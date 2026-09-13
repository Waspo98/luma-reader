package com.example.lumareader.data.sync

import com.example.lumareader.data.LocalBookRepository
import com.example.lumareader.data.model.Book
import com.example.lumareader.data.model.SyncScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath

@Serializable
data class RemoteReadingPosition(
    val bookId: String,
    val currentSpineIndex: Int,
    val currentProgression: Float,
    val lastLocatorJson: String?,
    val lastReadTimestamp: Long
)

@Serializable
data class RemoteLibraryCatalog(
    val version: Int = 1,
    val lastUpdatedTimestamp: Long = 0L,
    val books: List<Book> = emptyList(),
    val userShelves: List<String> = emptyList()
)

@Serializable
data class SyncResult(
    val success: Boolean,
    val scope: SyncScope,
    val itemsSynced: Int,
    val message: String,
    val timestamp: Long
)

class CloudSyncManager(
    private val fs: FileSystem = FileSystem.SYSTEM,
    private var remoteClient: RemoteDriveClient? = null,
    initialEmail: String? = null
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _connectedEmail = MutableStateFlow<String?>(initialEmail)
    val connectedEmail: StateFlow<String?> = _connectedEmail.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<SyncResult?>(null)
    val lastSyncResult: StateFlow<SyncResult?> = _lastSyncResult.asStateFlow()

    fun setRemoteClient(client: RemoteDriveClient?) {
        this.remoteClient = client
    }

    fun setConnectedEmail(email: String?) {
        _connectedEmail.value = email
    }

    suspend fun connect(email: String = "user@example.com") {
        _isSyncing.value = true
        try {
            delay(300)
            _connectedEmail.value = email
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun disconnect() {
        _isSyncing.value = true
        try {
            remoteClient?.disconnect()
            _connectedEmail.value = null
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun triggerSync(
        syncDir: String,
        repository: LocalBookRepository,
        scope: SyncScope
    ): SyncResult {
        val currentEmail = _connectedEmail.value ?: remoteClient?.getConnectedEmail()
        if (currentEmail == null) {
            val failure = SyncResult(
                success = false,
                scope = scope,
                itemsSynced = 0,
                message = "Not connected to Google Drive",
                timestamp = System.currentTimeMillis()
            )
            _lastSyncResult.value = failure
            return failure
        }
        _connectedEmail.value = currentEmail

        _isSyncing.value = true
        try {
            val client = remoteClient ?: LocalFileDriveClient(syncDir, fs, currentEmail)
            val now = System.currentTimeMillis()
            val result: SyncResult

            when (scope) {
                SyncScope.READING_POSITION_ONLY -> {
                    val progressContent = client.downloadTextFile("reading_positions.json")
                    val localBooks = repository.books.value

                    val remotePositions = if (!progressContent.isNullOrBlank()) {
                        try {
                            json.decodeFromString<Map<String, RemoteReadingPosition>>(progressContent)
                        } catch (_: Exception) {
                            emptyMap()
                        }
                    } else {
                        emptyMap()
                    }

                    val mergedPositions = remotePositions.toMutableMap()

                    localBooks.forEach { book ->
                        val remote = remotePositions[book.id]
                        if (remote == null || book.lastReadTimestamp >= remote.lastReadTimestamp) {
                            mergedPositions[book.id] = RemoteReadingPosition(
                                bookId = book.id,
                                currentSpineIndex = book.currentSpineIndex,
                                currentProgression = book.currentProgression,
                                lastLocatorJson = book.lastLocatorJson,
                                lastReadTimestamp = book.lastReadTimestamp
                            )
                        } else {
                            if (remote.lastLocatorJson != null) {
                                repository.updateBookLocator(
                                    bookId = book.id,
                                    locatorJson = remote.lastLocatorJson,
                                    spineIndex = remote.currentSpineIndex,
                                    progression = remote.currentProgression
                                )
                            } else {
                                repository.updateBookProgress(
                                    bookId = book.id,
                                    spineIndex = remote.currentSpineIndex,
                                    progression = remote.currentProgression
                                )
                            }
                        }
                    }

                    val writeSuccess = client.uploadTextFile("reading_positions.json", json.encodeToString(mergedPositions))
                    if (!writeSuccess) {
                        throw RuntimeException("Failed to upload reading positions to Google Drive")
                    }

                    result = SyncResult(
                        success = true,
                        scope = scope,
                        itemsSynced = localBooks.size,
                        message = "Synced reading positions for ${localBooks.size} book(s)",
                        timestamp = now
                    )
                }

                SyncScope.FULL_LIBRARY -> {
                    val catalogContent = client.downloadTextFile("library_catalog.json")
                    val localBooks = repository.books.value
                    val localPrefs = repository.preferences.value
                    val syncEpubs = localPrefs.syncPrefs.syncEpubFiles

                    val remoteCatalog = if (!catalogContent.isNullOrBlank()) {
                        try {
                            json.decodeFromString<RemoteLibraryCatalog>(catalogContent)
                        } catch (_: Exception) {
                            null
                        }
                    } else null

                    val remoteBooks = remoteCatalog?.books ?: emptyList()
                    val mergedBooksMap = localBooks.associateBy { it.id }.toMutableMap()
                    for (rb in remoteBooks) {
                        val lb = mergedBooksMap[rb.id]
                        if (lb == null) {
                            mergedBooksMap[rb.id] = rb
                        } else if (rb.lastReadTimestamp > lb.lastReadTimestamp) {
                            mergedBooksMap[rb.id] = lb.copy(
                                currentSpineIndex = rb.currentSpineIndex,
                                currentProgression = rb.currentProgression,
                                lastLocatorJson = rb.lastLocatorJson ?: lb.lastLocatorJson,
                                lastReadTimestamp = rb.lastReadTimestamp,
                                collections = (lb.collections + rb.collections).distinct(),
                                annotations = (lb.annotations + rb.annotations).distinctBy { it.id }
                            )
                        } else {
                            mergedBooksMap[rb.id] = lb.copy(
                                collections = (lb.collections + rb.collections).distinct(),
                                annotations = (lb.annotations + rb.annotations).distinctBy { it.id }
                            )
                        }
                    }

                    var epubsUploaded = 0
                    var epubsDownloaded = 0

                    if (syncEpubs) {
                        val remoteFiles = try {
                            client.listFiles()
                        } catch (_: Exception) {
                            emptyList()
                        }
                        val remoteFileMap = remoteFiles.associateBy { it.name }

                        // 1. Upload local EPUBs that are missing on remote
                        for (lb in localBooks) {
                            val epubPath = lb.epubFilePath
                            if (!epubPath.isNullOrBlank() && fs.exists(epubPath.toPath())) {
                                val remoteName = "epub_${lb.id}.epub"
                                if (!remoteFileMap.containsKey(remoteName)) {
                                    val ok = client.uploadBinaryFile(remoteName, epubPath)
                                    if (ok) epubsUploaded++
                                }
                            }
                        }

                        // 2. Download remote EPUBs that are missing locally
                        val booksDir = repository.getBooksDir()
                        try {
                            fs.createDirectories(booksDir.toPath())
                        } catch (_: Exception) {}

                        for (rb in remoteBooks) {
                            val localMatch = localBooks.firstOrNull { it.id == rb.id }
                            val localEpubMissing = localMatch?.epubFilePath == null || !fs.exists(localMatch.epubFilePath.toPath())
                            if (localEpubMissing) {
                                val remoteName = "epub_${rb.id}.epub"
                                if (remoteFileMap.containsKey(remoteName)) {
                                    val safeTitle = rb.title.replace(Regex("[^a-zA-Z0-9_.-]"), "_").take(40)
                                    val targetFile = "$booksDir/${safeTitle}_${rb.id}.epub"
                                    val ok = client.downloadBinaryFile(remoteName, targetFile)
                                    if (ok && fs.exists(targetFile.toPath())) {
                                        try {
                                            val imported = repository.importBook(targetFile)
                                            val updatedImported = imported.copy(
                                                currentSpineIndex = rb.currentSpineIndex,
                                                currentProgression = rb.currentProgression,
                                                lastLocatorJson = rb.lastLocatorJson,
                                                lastReadTimestamp = rb.lastReadTimestamp,
                                                collections = (imported.collections + rb.collections).distinct(),
                                                annotations = (imported.annotations + rb.annotations).distinctBy { it.id }
                                            )
                                            repository.updateBook(updatedImported)
                                            mergedBooksMap[rb.id] = updatedImported
                                            epubsDownloaded++
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val finalBooks = mergedBooksMap.values.toList()
                    if (finalBooks != localBooks) {
                        repository.updateBooks(finalBooks)
                    }

                    val remoteShelves = remoteCatalog?.userShelves ?: emptyList()
                    val mergedShelves = (localPrefs.libraryPrefs.userShelves + remoteShelves).distinct()
                    if (mergedShelves != localPrefs.libraryPrefs.userShelves) {
                        repository.savePreferences(
                            localPrefs.copy(
                                libraryPrefs = localPrefs.libraryPrefs.copy(userShelves = mergedShelves)
                            )
                        )
                    }

                    val updatedCatalog = RemoteLibraryCatalog(
                        version = 1,
                        lastUpdatedTimestamp = now,
                        books = finalBooks,
                        userShelves = mergedShelves
                    )
                    val uploadCatalogOk = client.uploadTextFile("library_catalog.json", json.encodeToString(updatedCatalog))
                    if (!uploadCatalogOk) {
                        throw RuntimeException("Failed to upload library catalog to Google Drive")
                    }

                    // Keep reading positions in sync as well
                    val positionsMap = finalBooks.associate { book ->
                        book.id to RemoteReadingPosition(
                            bookId = book.id,
                            currentSpineIndex = book.currentSpineIndex,
                            currentProgression = book.currentProgression,
                            lastLocatorJson = book.lastLocatorJson,
                            lastReadTimestamp = book.lastReadTimestamp
                        )
                    }
                    client.uploadTextFile("reading_positions.json", json.encodeToString(positionsMap))

                    val epubSummary = if (syncEpubs) {
                        when {
                            epubsUploaded > 0 && epubsDownloaded > 0 -> ", $epubsUploaded EPUB(s) uploaded, $epubsDownloaded downloaded"
                            epubsUploaded > 0 -> ", $epubsUploaded EPUB(s) uploaded"
                            epubsDownloaded > 0 -> ", $epubsDownloaded EPUB(s) downloaded"
                            else -> ", EPUB files up to date"
                        }
                    } else {
                        ", metadata only"
                    }

                    result = SyncResult(
                        success = true,
                        scope = scope,
                        itemsSynced = finalBooks.size,
                        message = "Full library synchronized (${finalBooks.size} books$epubSummary)",
                        timestamp = now
                    )
                }
            }

            val currentPrefs = repository.preferences.value
            repository.savePreferences(
                currentPrefs.copy(
                    syncPrefs = currentPrefs.syncPrefs.copy(
                        lastSyncTimestamp = now,
                        syncScope = scope,
                        connectedEmail = currentEmail
                    )
                )
            )

            _lastSyncResult.value = result
            return result
        } catch (e: Exception) {
            val errorResult = SyncResult(
                success = false,
                scope = scope,
                itemsSynced = 0,
                message = "Sync failed: ${e.message ?: "Unknown error"}",
                timestamp = System.currentTimeMillis()
            )
            _lastSyncResult.value = errorResult
            return errorResult
        } finally {
            _isSyncing.value = false
        }
    }
}
