package com.example.lumareader.data

import com.example.lumareader.data.epub.EpubParser
import com.example.lumareader.data.model.Book
import com.example.lumareader.data.model.BookAnnotation
import com.example.lumareader.data.model.ReadingPreferences
import com.example.lumareader.data.model.LumaBackup
import com.example.lumareader.data.model.StorageFootprint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.Closeable

class LocalBookRepository(
    private val filesDir: String,
    private val cacheDir: String,
    private val epubParser: EpubParser = EpubParser()
) : Closeable {
    private val fs = FileSystem.SYSTEM
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val libraryFile = "$filesDir/library.json".toPath()
    private val preferencesFile = "$filesDir/preferences.json".toPath()

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _preferences = MutableStateFlow(ReadingPreferences())
    val preferences: StateFlow<ReadingPreferences> = _preferences.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val saveMutex = Mutex()
    private var saveJob: Job? = null
    private var savePrefsJob: Job? = null

    init {
        loadLibrary()
        loadPreferences()
    }

    private fun atomicWrite(targetPath: okio.Path, content: String) {
        val parentDir = targetPath.parent ?: return
        fs.createDirectories(parentDir)
        val tempPath = "$targetPath.${System.nanoTime()}.tmp".toPath()
        val backupPath = "$targetPath.bak".toPath()

        // 1. Write completely to temp file
        fs.write(tempPath) { writeUtf8(content) }

        // 2. Backup previous version if it exists
        if (fs.exists(targetPath)) {
            try {
                if (fs.exists(backupPath)) fs.delete(backupPath)
                fs.copy(targetPath, backupPath)
            } catch (_: Exception) {}
        }

        // 3. Atomically replace target with temp file
        try {
            fs.atomicMove(tempPath, targetPath)
        } catch (_: Exception) {
            // Fallback for filesystems that do not support atomicMove
            if (fs.exists(targetPath)) fs.delete(targetPath)
            fs.copy(tempPath, targetPath)
            if (fs.exists(tempPath)) fs.delete(tempPath)
        }
    }

    private fun loadLibrary() {
        try {
            libraryFile.parent?.let { fs.createDirectories(it) }
            val fileToRead = when {
                fs.exists(libraryFile) -> libraryFile
                fs.exists("$libraryFile.bak".toPath()) -> "$libraryFile.bak".toPath()
                else -> null
            }
            if (fileToRead != null) {
                val jsonStr = fs.read(fileToRead) { readUtf8() }
                _books.value = json.decodeFromString(jsonStr)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val backup = "$libraryFile.bak".toPath()
            if (fs.exists(backup)) {
                try {
                    val jsonStr = fs.read(backup) { readUtf8() }
                    _books.value = json.decodeFromString(jsonStr)
                } catch (_: Exception) {}
            }
        }
    }

    private fun saveLibrary() {
        scope.launch(Dispatchers.IO) {
            saveMutex.withLock {
                try {
                    val jsonStr = json.encodeToString(_books.value)
                    atomicWrite(libraryFile, jsonStr)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun loadPreferences() {
        try {
            preferencesFile.parent?.let { fs.createDirectories(it) }
            val fileToRead = when {
                fs.exists(preferencesFile) -> preferencesFile
                fs.exists("$preferencesFile.bak".toPath()) -> "$preferencesFile.bak".toPath()
                else -> null
            }
            if (fileToRead != null) {
                val jsonStr = fs.read(fileToRead) { readUtf8() }
                _preferences.value = json.decodeFromString(jsonStr)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val backup = "$preferencesFile.bak".toPath()
            if (fs.exists(backup)) {
                try {
                    val jsonStr = fs.read(backup) { readUtf8() }
                    _preferences.value = json.decodeFromString(jsonStr)
                } catch (_: Exception) {}
            }
        }
    }

    fun savePreferences(prefs: ReadingPreferences) {
        _preferences.value = prefs
        savePrefsJob?.cancel()
        savePrefsJob = scope.launch(Dispatchers.IO) {
            delay(300)
            try {
                val jsonStr = json.encodeToString(prefs)
                atomicWrite(preferencesFile, jsonStr)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importBook(epubFilePath: String): Book {
        val epubsStorageDir = "$filesDir/epubs"
        val parsed = epubParser.parseEpub(epubFilePath, epubsStorageDir)
        val initialCollections = if (_preferences.value.libraryPrefs.autoShelveBySubject && parsed.subjects.isNotEmpty()) {
            parsed.subjects.distinct()
        } else {
            emptyList()
        }
        val book = parsed.copy(
            epubFilePath = epubFilePath,
            collections = initialCollections
        )
        val currentList = _books.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == book.id }
        if (index >= 0) {
            val existing = currentList[index]
            // Keep the user's progress, shelves, annotations, and customized metadata if importing an existing book
            val mergedCollections = if (_preferences.value.libraryPrefs.autoShelveBySubject && parsed.subjects.isNotEmpty()) {
                (existing.collections + parsed.subjects).distinct()
            } else {
                existing.collections
            }
            val updated = book.copy(
                currentSpineIndex = existing.currentSpineIndex,
                currentProgression = existing.currentProgression,
                lastLocatorJson = existing.lastLocatorJson,
                collections = mergedCollections,
                annotations = existing.annotations,
                customCoverPath = existing.customCoverPath,
                title = if (existing.title.isNotBlank()) existing.title else book.title,
                author = if (existing.author.isNotBlank()) existing.author else book.author,
                series = existing.series ?: book.series,
                seriesNumber = existing.seriesNumber ?: book.seriesNumber,
                lastReadTimestamp = existing.lastReadTimestamp
            )
            currentList[index] = updated
        } else {
            currentList.add(book)
        }
        _books.value = currentList
        saveLibrary()
        return book
    }

    fun updateBookProgress(bookId: String, spineIndex: Int, progression: Float) {
        val currentList = _books.value.map {
            if (it.id == bookId) {
                it.copy(
                    currentSpineIndex = spineIndex,
                    currentProgression = progression,
                    lastReadTimestamp = System.currentTimeMillis()
                )
            } else {
                it
            }
        }
        _books.value = currentList
        
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1000)
            saveLibrary()
        }
    }

    fun updateBookLocator(bookId: String, locatorJson: String?, spineIndex: Int, progression: Float) {
        val currentList = _books.value.map {
            if (it.id == bookId) {
                it.copy(
                    lastLocatorJson = locatorJson ?: it.lastLocatorJson,
                    currentSpineIndex = spineIndex,
                    currentProgression = progression,
                    lastReadTimestamp = System.currentTimeMillis()
                )
            } else {
                it
            }
        }
        _books.value = currentList
        
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1000)
            saveLibrary()
        }
    }

    fun addAnnotation(annotation: BookAnnotation) {
        val currentList = _books.value.map {
            if (it.id == annotation.bookId) {
                val updatedAnnos = it.annotations.filterNot { a -> a.id == annotation.id } + annotation
                it.copy(annotations = updatedAnnos)
            } else {
                it
            }
        }
        _books.value = currentList
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(500)
            saveLibrary()
        }
    }

    fun removeAnnotation(bookId: String, annotationId: String) {
        val currentList = _books.value.map {
            if (it.id == bookId) {
                it.copy(annotations = it.annotations.filterNot { a -> a.id == annotationId })
            } else {
                it
            }
        }
        _books.value = currentList
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(500)
            saveLibrary()
        }
    }

    fun updateAnnotation(annotation: BookAnnotation) {
        addAnnotation(annotation)
    }

    fun updateBookMetadata(
        bookId: String,
        title: String? = null,
        author: String? = null,
        series: String? = null,
        seriesNumber: Float? = null,
        customCoverPath: String? = null
    ) {
        val currentList = _books.value.map {
            if (it.id == bookId) {
                it.copy(
                    title = title ?: it.title,
                    author = author ?: it.author,
                    series = series,
                    seriesNumber = seriesNumber,
                    customCoverPath = customCoverPath ?: it.customCoverPath
                )
            } else it
        }
        _books.value = currentList
        scope.launch(Dispatchers.IO) { saveLibrary() }
    }

    fun updateBook(book: Book) {
        _books.value = _books.value.map { if (it.id == book.id) book else it }
        scope.launch(Dispatchers.IO) { saveLibrary() }
    }

    fun updateBooks(books: List<Book>) {
        _books.value = books
        scope.launch(Dispatchers.IO) { saveLibrary() }
    }

    fun toggleBookReadingStatus(bookId: String) {
        val book = _books.value.find { it.id == bookId } ?: return
        val currentStatus = book.readingStatus()
        val updated = when (currentStatus) {
            "FINISHED" -> book.copy(currentSpineIndex = 0, currentProgression = 0f, lastLocatorJson = null)
            else -> {
                val lastSpine = if (book.spine.isNotEmpty()) book.spine.size - 1 else 0
                book.copy(currentSpineIndex = lastSpine, currentProgression = 1.0f)
            }
        }
        _books.value = _books.value.map { if (it.id == bookId) updated else it }
        scope.launch(Dispatchers.IO) { saveLibrary() }
    }

    override fun close() {
        if (saveJob?.isActive == true) {
            saveJob?.cancel()
            try {
                saveLibrary()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            saveJob?.cancel()
        }
        if (savePrefsJob?.isActive == true) {
            savePrefsJob?.cancel()
            try {
                val jsonStr = json.encodeToString(_preferences.value)
                atomicWrite(preferencesFile, jsonStr)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            savePrefsJob?.cancel()
        }
        scope.coroutineContext[Job]?.cancel()
    }

    // --- Shelf & Collections Management ---

    fun createUserShelf(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val currentPrefs = _preferences.value
        val currentShelves = currentPrefs.libraryPrefs.userShelves
        if (!currentShelves.contains(trimmed)) {
            val updatedShelves = currentShelves + trimmed
            savePreferences(
                currentPrefs.copy(
                    libraryPrefs = currentPrefs.libraryPrefs.copy(userShelves = updatedShelves)
                )
            )
        }
    }

    fun deleteUserShelf(name: String) {
        val currentPrefs = _preferences.value
        val updatedShelves = currentPrefs.libraryPrefs.userShelves.filter { it != name }
        val updatedActiveShelf = if (currentPrefs.libraryPrefs.activeShelf == name) null else currentPrefs.libraryPrefs.activeShelf
        savePreferences(
            currentPrefs.copy(
                libraryPrefs = currentPrefs.libraryPrefs.copy(
                    userShelves = updatedShelves,
                    activeShelf = updatedActiveShelf
                )
            )
        )
        // Clean up books belonging to this shelf
        _books.value = _books.value.map { book ->
            if (book.collections.contains(name)) {
                book.copy(collections = book.collections.filter { it != name })
            } else book
        }
        scope.launch(Dispatchers.IO) { saveLibrary() }
    }

    fun renameUserShelf(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || oldName == trimmed) return
        val currentPrefs = _preferences.value
        val updatedShelves = currentPrefs.libraryPrefs.userShelves.map { if (it == oldName) trimmed else it }
        val updatedActiveShelf = if (currentPrefs.libraryPrefs.activeShelf == oldName) trimmed else currentPrefs.libraryPrefs.activeShelf
        savePreferences(
            currentPrefs.copy(
                libraryPrefs = currentPrefs.libraryPrefs.copy(
                    userShelves = updatedShelves,
                    activeShelf = updatedActiveShelf
                )
            )
        )
        _books.value = _books.value.map { book ->
            if (book.collections.contains(oldName)) {
                book.copy(collections = book.collections.map { if (it == oldName) trimmed else it })
            } else book
        }
        scope.launch(Dispatchers.IO) { saveLibrary() }
    }

    fun assignBookToShelf(bookId: String, shelf: String) {
        val trimmed = shelf.trim()
        if (trimmed.isEmpty()) return
        _books.value = _books.value.map { book ->
            if (book.id == bookId && !book.collections.contains(trimmed)) {
                book.copy(collections = book.collections + trimmed)
            } else book
        }
        scope.launch(Dispatchers.IO) { saveLibrary() }
    }

    fun removeBookFromShelf(bookId: String, shelf: String) {
        _books.value = _books.value.map { book ->
            if (book.id == bookId && book.collections.contains(shelf)) {
                book.copy(collections = book.collections.filter { it != shelf })
            } else book
        }
        scope.launch(Dispatchers.IO) { saveLibrary() }
    }

    // --- Batch Operations ---

    fun assignBooksToShelf(bookIds: Set<String>, shelf: String) {
        val trimmed = shelf.trim()
        if (trimmed.isEmpty() || bookIds.isEmpty()) return
        _books.value = _books.value.map { book ->
            if (bookIds.contains(book.id) && !book.collections.contains(trimmed)) {
                book.copy(collections = book.collections + trimmed)
            } else book
        }
        scope.launch(Dispatchers.IO) { saveLibrary() }
    }

    fun removeBooksFromShelf(bookIds: Set<String>, shelf: String) {
        if (bookIds.isEmpty()) return
        _books.value = _books.value.map { book ->
            if (bookIds.contains(book.id) && book.collections.contains(shelf)) {
                book.copy(collections = book.collections.filter { it != shelf })
            } else book
        }
        scope.launch(Dispatchers.IO) { saveLibrary() }
    }

    fun updateBooksReadingStatus(bookIds: Set<String>, targetStatus: String) {
        if (bookIds.isEmpty()) return
        _books.value = _books.value.map { book ->
            if (bookIds.contains(book.id)) {
                when (targetStatus.uppercase()) {
                    "FINISHED" -> {
                        val lastSpine = if (book.spine.isNotEmpty()) book.spine.size - 1 else 0
                        book.copy(currentSpineIndex = lastSpine, currentProgression = 1.0f)
                    }
                    "UNREAD" -> {
                        book.copy(currentSpineIndex = 0, currentProgression = 0f, lastLocatorJson = null)
                    }
                    "READING" -> {
                        if (book.overallProgress() > 0f && book.overallProgress() < 0.96f) book
                        else book.copy(currentSpineIndex = 0, currentProgression = 0.05f)
                    }
                    else -> book
                }
            } else book
        }
        scope.launch(Dispatchers.IO) { saveLibrary() }
    }

    fun deleteBooks(bookIds: Set<String>) {
        if (bookIds.isEmpty()) return
        saveJob?.cancel()
        val booksToDelete = _books.value.filter { bookIds.contains(it.id) }
        _books.value = _books.value.filterNot { bookIds.contains(it.id) }
        scope.launch(Dispatchers.IO) {
            for (book in booksToDelete) {
                try {
                    val targetDir = book.unzippedDir.toPath()
                    if (fs.exists(targetDir)) fs.deleteRecursively(targetDir)
                    val epubFilePath = book.epubFilePath
                    if (!epubFilePath.isNullOrBlank()) {
                        val epubPath = epubFilePath.toPath()
                        if (fs.exists(epubPath)) fs.delete(epubPath)
                    }
                    val customCoverPath = book.customCoverPath
                    if (!customCoverPath.isNullOrBlank()) {
                        val coverPath = customCoverPath.toPath()
                        if (fs.exists(coverPath)) fs.delete(coverPath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            saveLibrary()
        }
    }

    fun deleteBook(bookId: String) {
        deleteBooks(setOf(bookId))
    }

    // --- Backup, Storage & Maintenance ---

    fun exportBackupJson(): String {
        val backup = LumaBackup(
            version = 1,
            timestamp = System.currentTimeMillis(),
            books = _books.value,
            preferences = _preferences.value
        )
        return json.encodeToString(backup)
    }

    fun importBackupJson(jsonString: String): Boolean {
        return try {
            val backup = json.decodeFromString<LumaBackup>(jsonString)
            _preferences.value = backup.preferences
            _books.value = backup.books
            scope.launch(Dispatchers.IO) {
                saveLibrary()
                atomicWrite(preferencesFile, json.encodeToString(backup.preferences))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getBooksDir(): String = "$filesDir/books"

    fun getEpubStorageSizeBytes(): Long {
        var total = 0L
        val visitedPaths = mutableSetOf<String>()
        _books.value.forEach { book ->
            val path = book.epubFilePath
            if (!path.isNullOrBlank() && visitedPaths.add(path)) {
                try {
                    val p = path.toPath()
                    if (fs.exists(p)) {
                        total += fs.metadataOrNull(p)?.size ?: 0L
                    }
                } catch (_: Exception) {}
            }
        }
        return total
    }

    fun getStorageFootprint(): StorageFootprint {
        var booksSize = 0L
        val booksDir = "$filesDir/books".toPath()
        if (fs.exists(booksDir)) {
            try {
                fs.listRecursively(booksDir).forEach { file ->
                    val metadata = fs.metadataOrNull(file)
                    if (metadata?.isRegularFile == true) {
                        booksSize += metadata.size ?: 0L
                    }
                }
            } catch (_: Exception) {}
        }
        if (booksSize == 0L) {
            booksSize = getEpubStorageSizeBytes()
        }

        var cacheSize = 0L
        val cacheDirPath = cacheDir.toPath()
        if (fs.exists(cacheDirPath)) {
            try {
                fs.listRecursively(cacheDirPath).forEach { file ->
                    val metadata = fs.metadataOrNull(file)
                    if (metadata?.isRegularFile == true) {
                        cacheSize += metadata.size ?: 0L
                    }
                }
            } catch (_: Exception) {}
        }

        return StorageFootprint(
            booksSizeBytes = booksSize,
            cacheSizeBytes = cacheSize,
            bookCount = _books.value.size
        )
    }

    fun clearCoverCache(): Long {
        var freedBytes = 0L
        val cacheDirPath = cacheDir.toPath()
        if (fs.exists(cacheDirPath)) {
            try {
                fs.list(cacheDirPath).forEach { path ->
                    // Never delete extracted books in epubs directory
                    if (!path.name.equals("epubs", ignoreCase = true)) {
                        try {
                            fs.listRecursively(path).forEach { file ->
                                val metadata = fs.metadataOrNull(file)
                                if (metadata?.isRegularFile == true) {
                                    freedBytes += metadata.size ?: 0L
                                }
                            }
                            fs.deleteRecursively(path)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
        return freedBytes
    }

    fun reindexLibrary(): Int {
        val currentBooks = _books.value
        val validBooks = currentBooks.filter { book ->
            val path = book.epubFilePath
            if (path.isNullOrBlank()) false
            else fs.exists(path.toPath())
        }
        _books.value = validBooks
        scope.launch(Dispatchers.IO) { saveLibrary() }
        return validBooks.size
    }
}
