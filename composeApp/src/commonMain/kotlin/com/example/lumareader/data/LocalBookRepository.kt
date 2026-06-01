package com.example.lumareader.data

import com.example.lumareader.data.epub.EpubParser
import com.example.lumareader.data.model.Book
import com.example.lumareader.data.model.ReadingPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    init {
        loadLibrary()
        loadPreferences()
    }

    private fun loadLibrary() {
        try {
            // Ensure directories exist
            libraryFile.parent?.let { fs.createDirectories(it) }
            
            if (fs.exists(libraryFile)) {
                val jsonStr = fs.read(libraryFile) { readUtf8() }
                _books.value = json.decodeFromString(jsonStr)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveLibrary() {
        try {
            libraryFile.parent?.let { fs.createDirectories(it) }
            val jsonStr = json.encodeToString(_books.value)
            fs.write(libraryFile) { writeUtf8(jsonStr) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadPreferences() {
        try {
            preferencesFile.parent?.let { fs.createDirectories(it) }
            
            if (fs.exists(preferencesFile)) {
                val jsonStr = fs.read(preferencesFile) { readUtf8() }
                _preferences.value = json.decodeFromString(jsonStr)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun savePreferences(prefs: ReadingPreferences) {
        _preferences.value = prefs
        scope.launch(Dispatchers.IO) {
            try {
                preferencesFile.parent?.let { fs.createDirectories(it) }
                val jsonStr = json.encodeToString(prefs)
                fs.write(preferencesFile) { writeUtf8(jsonStr) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importBook(epubFilePath: String): Book {
        val book = epubParser.parseEpub(epubFilePath, cacheDir)
        val currentList = _books.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == book.id }
        if (index >= 0) {
            val existing = currentList[index]
            // Keep the user's progress if importing an existing book
            val updated = book.copy(
                currentSpineIndex = existing.currentSpineIndex,
                currentProgression = existing.currentProgression
            )
            currentList[index] = updated
        } else {
            currentList.add(book)
        }
        _books.value = currentList
        saveLibrary()
        return book
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var saveJob: Job? = null

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
        scope.coroutineContext[Job]?.cancel()
    }

    fun deleteBook(bookId: String) {
        val book = _books.value.find { it.id == bookId }
        if (book != null) {
            _books.value = _books.value.filter { it.id != bookId }
            scope.launch(Dispatchers.IO) {
                try {
                    val targetDir = book.unzippedDir.toPath()
                    if (fs.exists(targetDir)) {
                        fs.deleteRecursively(targetDir)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                saveLibrary()
            }
        }
    }
}
