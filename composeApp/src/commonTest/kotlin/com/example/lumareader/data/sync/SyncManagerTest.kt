package com.example.lumareader.data.sync

import com.example.lumareader.data.LocalBookRepository
import com.example.lumareader.data.model.Book
import com.example.lumareader.data.model.ReadingPreferences
import com.example.lumareader.data.model.SyncPreferences
import com.example.lumareader.data.model.SyncScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.*

class SyncManagerTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Test
    fun testSyncPreferences_defaults() {
        val prefs = SyncPreferences()
        assertEquals(SyncScope.FULL_LIBRARY, prefs.syncScope)
        assertNull(prefs.lastSyncTimestamp)
        assertTrue(prefs.autoSyncOnOpen)
        assertTrue(prefs.autoSyncOnClose)
    }

    @Test
    fun testSyncPreferences_serialization() {
        val original = SyncPreferences(
            syncScope = SyncScope.READING_POSITION_ONLY,
            lastSyncTimestamp = 1725890000000L,
            autoSyncOnOpen = false,
            autoSyncOnClose = true
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SyncPreferences>(encoded)

        assertEquals(SyncScope.READING_POSITION_ONLY, decoded.syncScope)
        assertEquals(1725890000000L, decoded.lastSyncTimestamp)
        assertFalse(decoded.autoSyncOnOpen)
        assertTrue(decoded.autoSyncOnClose)
    }

    @Test
    fun testReadingPreferences_includesSyncPreferences() {
        val readingPrefs = ReadingPreferences(
            syncPrefs = SyncPreferences(syncScope = SyncScope.READING_POSITION_ONLY)
        )
        val encoded = json.encodeToString(readingPrefs)
        val decoded = json.decodeFromString<ReadingPreferences>(encoded)

        assertEquals(SyncScope.READING_POSITION_ONLY, decoded.syncPrefs.syncScope)
    }

    @Test
    fun testCloudSyncManager_connectAndDisconnect() = runBlocking {
        val syncManager = CloudSyncManager()

        // Initial state is disconnected default
        assertNull(syncManager.connectedEmail.value)

        // Connect
        syncManager.connect("reader.user@gmail.com")
        assertEquals("reader.user@gmail.com", syncManager.connectedEmail.value)
        assertFalse(syncManager.isSyncing.value)

        // Disconnect
        syncManager.disconnect()
        assertNull(syncManager.connectedEmail.value)
        assertFalse(syncManager.isSyncing.value)
    }

    @Test
    fun testCloudSyncManager_notConnectedReturnsError() = runBlocking {
        val syncManager = CloudSyncManager(fs = FileSystem.SYSTEM)

        val tempDir = Files.createTempDirectory("sync_test_not_connected").toFile().apply { deleteOnExit() }
        val tempDirPath = tempDir.absolutePath
        val repo = LocalBookRepository(tempDirPath, "$tempDirPath/cache")

        val result = syncManager.triggerSync(tempDirPath, repo, SyncScope.FULL_LIBRARY)
        assertFalse(result.success)
        assertEquals("Not connected to Google Drive", result.message)
        assertEquals(0, result.itemsSynced)
    }

    @Test
    fun testCloudSyncManager_readingPositionOnlySync() = runBlocking {
        val fs = FileSystem.SYSTEM
        val syncManager = CloudSyncManager(fs = fs)
        syncManager.connect("test.user@example.com")

        val tempDir = Files.createTempDirectory("sync_test_pos").toFile().apply { deleteOnExit() }
        val tempDirPath = tempDir.absolutePath
        val repo = LocalBookRepository(tempDirPath, "$tempDirPath/cache")

        val result = syncManager.triggerSync(tempDirPath, repo, SyncScope.READING_POSITION_ONLY)
        assertTrue(result.success)
        assertEquals(SyncScope.READING_POSITION_ONLY, result.scope)
        assertTrue(result.message.contains("reading positions"))

        // Check that reading_positions.json was written in the google_drive_sync dir
        val positionsFile = "$tempDirPath/google_drive_sync/reading_positions.json".toPath()
        assertTrue(fs.exists(positionsFile))

        // Catalog file should NOT be written for reading position only sync
        val catalogFile = "$tempDirPath/google_drive_sync/library_catalog.json".toPath()
        assertFalse(fs.exists(catalogFile))
    }

    @Test
    fun testCloudSyncManager_fullLibrarySync() = runBlocking {
        val fs = FileSystem.SYSTEM
        val syncManager = CloudSyncManager(fs = fs)
        syncManager.connect("test.user@example.com")

        val tempDir = Files.createTempDirectory("sync_test_full").toFile().apply { deleteOnExit() }
        val tempDirPath = tempDir.absolutePath
        val repo = LocalBookRepository(tempDirPath, "$tempDirPath/cache")

        val result = syncManager.triggerSync(tempDirPath, repo, SyncScope.FULL_LIBRARY)
        assertTrue(result.success)
        assertEquals(SyncScope.FULL_LIBRARY, result.scope)
        assertTrue(result.message.contains("Full library"))

        // Catalog file MUST be written for full library sync
        val catalogFile = "$tempDirPath/google_drive_sync/library_catalog.json".toPath()
        assertTrue(fs.exists(catalogFile))

        // Reading positions file is also updated
        val positionsFile = "$tempDirPath/google_drive_sync/reading_positions.json".toPath()
        assertTrue(fs.exists(positionsFile))
    }

    @Test
    fun testCloudSyncManager_remoteReadingPositionMerge() = runBlocking {
        val fs = FileSystem.SYSTEM
        val tempDir = Files.createTempDirectory("sync_test_merge").toFile().apply { deleteOnExit() }
        val tempDirPath = tempDir.absolutePath
        val repo = LocalBookRepository(tempDirPath, "$tempDirPath/cache")

        val syncClient = LocalFileDriveClient(tempDirPath, fs, "merge.user@example.com")
        val syncManager = CloudSyncManager(fs = fs, remoteClient = syncClient)
        syncManager.connect("merge.user@example.com")

        // Prepopulate remote with newer position for book-1
        val remoteMap = mapOf(
            "book-1" to RemoteReadingPosition(
                bookId = "book-1",
                currentSpineIndex = 5,
                currentProgression = 0.8f,
                lastLocatorJson = null,
                lastReadTimestamp = 2000000L
            )
        )
        syncClient.uploadTextFile("reading_positions.json", json.encodeToString(remoteMap))

        val result = syncManager.triggerSync(tempDirPath, repo, SyncScope.READING_POSITION_ONLY)
        assertTrue(result.success)

        val downloaded = syncClient.downloadTextFile("reading_positions.json")
        assertNotNull(downloaded)
        assertTrue(downloaded.contains("book-1"))
    }

    @Test
    fun testSyncPreferences_syncEpubFilesDefault() {
        val prefs = SyncPreferences()
        assertTrue(prefs.syncEpubFiles)

        val updated = prefs.copy(syncEpubFiles = false)
        assertFalse(updated.syncEpubFiles)

        val encoded = json.encodeToString(updated)
        val decoded = json.decodeFromString<SyncPreferences>(encoded)
        assertFalse(decoded.syncEpubFiles)
    }

    @Test
    fun testLocalFileDriveClient_binaryUploadDownloadAndList() = runBlocking {
        val fs = FileSystem.SYSTEM
        val tempDir = Files.createTempDirectory("drive_binary_test").toFile().apply { deleteOnExit() }
        val tempDirPath = tempDir.absolutePath

        val client = LocalFileDriveClient(tempDirPath, fs, "test@example.com")

        // Create a dummy EPUB file
        val localEpub = java.io.File(tempDir, "sample.epub").apply {
            writeBytes("PK\u0003\u0004DummyEpubContentForTesting12345".toByteArray())
        }

        // Upload
        val uploadOk = client.uploadBinaryFile("epub_sample.epub", localEpub.absolutePath)
        assertTrue(uploadOk)

        // List
        val list = client.listFiles()
        assertEquals(1, list.size)
        assertEquals("epub_sample.epub", list[0].name)
        assertTrue(list[0].sizeBytes > 0)

        // Download to new destination
        val downloadedFile = java.io.File(tempDir, "downloaded.epub")
        val downloadOk = client.downloadBinaryFile("epub_sample.epub", downloadedFile.absolutePath)
        assertTrue(downloadOk)
        assertTrue(downloadedFile.exists())
        assertEquals(localEpub.length(), downloadedFile.length())
        assertEquals("PK\u0003\u0004DummyEpubContentForTesting12345", downloadedFile.readText())
    }

    @Test
    fun testCloudSyncManager_fullLibrarySyncWithEpubUpload() = runBlocking {
        val fs = FileSystem.SYSTEM
        val tempDir = Files.createTempDirectory("sync_epub_full").toFile().apply { deleteOnExit() }
        val tempDirPath = tempDir.absolutePath
        val repo = LocalBookRepository(tempDirPath, "$tempDirPath/cache")

        val syncClient = LocalFileDriveClient(tempDirPath, fs, "epub.user@example.com")
        val syncManager = CloudSyncManager(fs = fs, remoteClient = syncClient)
        syncManager.connect("epub.user@example.com")

        // Create a local book with an EPUB file on disk
        val booksDir = java.io.File(tempDirPath, "books").apply { mkdirs() }
        val dummyEpub = java.io.File(booksDir, "book_101.epub").apply {
            writeBytes("DUMMY_EPUB_BINARY_CONTENT_101".toByteArray())
        }

        val testBook = Book(
            id = "book_101",
            title = "Test EPUB Book",
            author = "Test Author",
            unzippedDir = "$tempDirPath/cache/extracted_101",
            spine = listOf("ch1.html"),
            toc = emptyList(),
            epubFilePath = dummyEpub.absolutePath
        )
        repo.updateBooks(listOf(testBook))

        val result = syncManager.triggerSync(tempDirPath, repo, SyncScope.FULL_LIBRARY)
        assertTrue(result.success)
        assertTrue(result.message.contains("EPUB(s) uploaded") || result.message.contains("Full library"))

        // Verify that epub_book_101.epub exists in the remote storage folder
        val remoteEpubPath = "$tempDirPath/google_drive_sync/epub_book_101.epub".toPath()
        assertTrue(fs.exists(remoteEpubPath))
    }

    @Test
    fun testLocalFileDriveClient_onProgressInvokedDuringUpload() = runBlocking {
        val fs = FileSystem.SYSTEM
        val tempDir = Files.createTempDirectory("drive_prog_test").toFile().apply { deleteOnExit() }
        val tempDirPath = tempDir.absolutePath
        val client = LocalFileDriveClient(tempDirPath, fs, "test@example.com")

        val dummyData = ByteArray(40000) { (it % 128).toByte() }
        val localFile = java.io.File(tempDir, "chunk_test.epub").apply {
            writeBytes(dummyData)
        }

        val progressReports = mutableListOf<Pair<Long, Long>>()
        val ok = client.uploadBinaryFile("remote_chunk.epub", localFile.absolutePath) { sent, total ->
            progressReports.add(sent to total)
        }

        assertTrue(ok)
        assertTrue(progressReports.isNotEmpty())
        val lastReport = progressReports.last()
        assertEquals(40000L, lastReport.first)
        assertEquals(40000L, lastReport.second)
    }

    @Test
    fun testCloudSyncManager_syncProgressEmittedDuringEpubSyncAndReset() = runBlocking {
        val fs = FileSystem.SYSTEM
        val tempDir = Files.createTempDirectory("sync_prog_full").toFile().apply { deleteOnExit() }
        val tempDirPath = tempDir.absolutePath
        val repo = LocalBookRepository(tempDirPath, "$tempDirPath/cache")

        val syncClient = LocalFileDriveClient(tempDirPath, fs, "epub.prog@example.com")
        lateinit var syncManager: CloudSyncManager
        var capturedDuringUpload: SyncProgress? = null

        val testClient = object : RemoteDriveClient by syncClient {
            override suspend fun uploadBinaryFile(
                remoteFileName: String,
                localFilePath: String,
                mimeType: String,
                onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)?
            ): Boolean {
                return syncClient.uploadBinaryFile(remoteFileName, localFilePath, mimeType) { sent, total ->
                    onProgress?.invoke(sent, total)
                    capturedDuringUpload = syncManager.syncProgress.value
                }
            }
        }

        syncManager = CloudSyncManager(fs = fs, remoteClient = testClient)
        syncManager.connect("epub.prog@example.com")

        val booksDir = java.io.File(tempDirPath, "books").apply { mkdirs() }
        val dummyEpub = java.io.File(booksDir, "book_progress.epub").apply {
            writeBytes(ByteArray(32768) { 42.toByte() })
        }

        val testBook = Book(
            id = "book_prog_1",
            title = "Progress Tracking Book",
            author = "Author",
            unzippedDir = "$tempDirPath/cache/extracted_prog",
            spine = listOf("ch1.html"),
            toc = emptyList(),
            epubFilePath = dummyEpub.absolutePath
        )
        repo.updateBooks(listOf(testBook))

        val result = syncManager.triggerSync(tempDirPath, repo, SyncScope.FULL_LIBRARY)

        assertTrue(result.success)
        assertNull(syncManager.syncProgress.value, "Progress must reset to null after sync finishes")
        assertNotNull(capturedDuringUpload, "Progress should have been emitted during upload")
        assertEquals("Progress Tracking Book", capturedDuringUpload?.currentItemName)
        assertEquals(1, capturedDuringUpload?.totalItems)
        assertTrue(capturedDuringUpload?.isUpload == true)
        assertTrue((capturedDuringUpload?.itemBytesTransferred ?: 0L) > 0L)
    }

    @Test
    fun testCloudSyncManager_readingPositionSyncNeverEmitsSyncProgress() = runBlocking {
        val fs = FileSystem.SYSTEM
        val tempDir = Files.createTempDirectory("sync_pos_silent").toFile().apply { deleteOnExit() }
        val tempDirPath = tempDir.absolutePath
        val repo = LocalBookRepository(tempDirPath, "$tempDirPath/cache")

        val syncClient = LocalFileDriveClient(tempDirPath, fs, "silent@example.com")
        lateinit var syncManager: CloudSyncManager
        var capturedDuringUploadText: SyncProgress? = null

        val testClient = object : RemoteDriveClient by syncClient {
            override suspend fun uploadTextFile(fileName: String, content: String): Boolean {
                capturedDuringUploadText = syncManager.syncProgress.value
                return syncClient.uploadTextFile(fileName, content)
            }
        }

        syncManager = CloudSyncManager(fs = fs, remoteClient = testClient)
        syncManager.connect("silent@example.com")

        val result = syncManager.triggerSync(tempDirPath, repo, SyncScope.READING_POSITION_ONLY)

        assertTrue(result.success)
        assertNull(syncManager.syncProgress.value)
        assertNull(capturedDuringUploadText, "Reading position sync must never set syncProgress (stays completely silent)")
    }
}
