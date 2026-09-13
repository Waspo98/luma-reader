package com.example.lumareader.data

import com.example.lumareader.data.model.Book
import com.example.lumareader.data.model.LibrarySortOption
import com.example.lumareader.data.model.ReadingPreferences
import com.example.lumareader.data.model.displayLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookModelTest {

    private fun createTestBook(
        spineSize: Int = 10,
        currentSpineIndex: Int = 0,
        currentProgression: Float = 0f
    ): Book {
        val spineList = (1..spineSize).map { "chapter_$it.html" }
        return Book(
            id = "test-book-1",
            title = "Test Book",
            author = "Test Author",
            coverPath = null,
            unzippedDir = "/dummy/dir",
            spine = spineList,
            toc = emptyList(),
            currentSpineIndex = currentSpineIndex,
            currentProgression = currentProgression
        )
    }

    @Test
    fun testOverallProgress_emptySpine() {
        val book = createTestBook(spineSize = 0)
        assertEquals(0f, book.overallProgress())
    }

    @Test
    fun testOverallProgress_calculation() {
        val book = createTestBook(spineSize = 10, currentSpineIndex = 5, currentProgression = 0.5f)
        assertEquals(0.55f, book.overallProgress(), 0.001f)
    }

    @Test
    fun testReadingStatus_unread() {
        val book = createTestBook(spineSize = 10, currentSpineIndex = 0, currentProgression = 0f)
        assertEquals("UNREAD", book.readingStatus())
    }

    @Test
    fun testReadingStatus_reading() {
        val book = createTestBook(spineSize = 10, currentSpineIndex = 2, currentProgression = 0.3f)
        assertEquals("READING", book.readingStatus())
    }

    @Test
    fun testReadingStatus_finished() {
        val book = createTestBook(spineSize = 10, currentSpineIndex = 9, currentProgression = 0.9f)
        // 9.9 / 10 = 0.99 >= 0.96 -> FINISHED
        assertEquals("FINISHED", book.readingStatus())
    }

    @Test
    fun testLibrarySortOption_displayLabels() {
        assertEquals("Title (A → Z)", LibrarySortOption.TITLE_ASC.displayLabel)
        assertEquals("Title (Z → A)", LibrarySortOption.TITLE_DESC.displayLabel)
        assertEquals("Author (A → Z)", LibrarySortOption.AUTHOR_ASC.displayLabel)
        assertEquals("Author (Z → A)", LibrarySortOption.AUTHOR_DESC.displayLabel)
        assertEquals("Recently read", LibrarySortOption.RECENT.displayLabel)
        assertEquals("Most progress", LibrarySortOption.PROGRESS.displayLabel)
    }

    @Test
    fun testReadingPreferences_defaults() {
        val prefs = ReadingPreferences()
        assertEquals(20f, prefs.fontSizeSp)
        assertEquals(1.4f, prefs.lineSpacing)
        assertEquals("#D45D42", prefs.accentColorHex)
    }

    @Test
    fun testWarmSepiaThemeMode_exists() {
        val prefs = ReadingPreferences(themeMode = com.example.lumareader.data.model.LumaThemeMode.WARM_SEPIA)
        assertEquals(com.example.lumareader.data.model.LumaThemeMode.WARM_SEPIA, prefs.themeMode)
    }

    @Test
    fun testReadingStatus_toggleCycle() {
        val unreadBook = createTestBook(spineSize = 5, currentSpineIndex = 0, currentProgression = 0f)
        assertEquals("UNREAD", unreadBook.readingStatus())

        // Toggle from UNREAD -> FINISHED (spineIndex = last, progression = 1.0f)
        val finishedBook = unreadBook.copy(
            currentSpineIndex = unreadBook.spine.size - 1,
            currentProgression = 1.0f
        )
        assertEquals("FINISHED", finishedBook.readingStatus())

        // Toggle from FINISHED -> UNREAD (spineIndex = 0, progression = 0f)
        val resetBook = finishedBook.copy(
            currentSpineIndex = 0,
            currentProgression = 0f
        )
        assertEquals("UNREAD", resetBook.readingStatus())
    }

    @Test
    fun testReadingPreferences_advancedTypographyDefaults() {
        val prefs = ReadingPreferences()
        assertEquals(0.0f, prefs.letterSpacing)
        assertEquals(0.0f, prefs.wordSpacing)
        assertEquals(0.5f, prefs.paragraphSpacing)
        assertEquals(1.0f, prefs.paragraphIndent)
        assertEquals(true, prefs.hyphens)
        assertEquals(false, prefs.publisherStyles)
    }

    @Test
    fun testLibraryPreferences_userShelvesDefaults() {
        val prefs = ReadingPreferences()
        assertEquals(listOf("Favorites", "To Read"), prefs.libraryPrefs.userShelves)
        assertEquals(null, prefs.libraryPrefs.activeShelf)
    }

    @Test
    fun testBook_collectionsDefault() {
        val book = createTestBook()
        assertTrue(book.collections.isEmpty())
    }

    @Test
    fun testBook_backwardCompatibleJsonDeserialization() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        // JSON without 'collections' field simulating older saved library data
        val oldJson = """
            {
                "id": "book-compat",
                "title": "Old Format Book",
                "author": "Author A",
                "unzippedDir": "/data/book",
                "spine": ["ch1.html"],
                "toc": []
            }
        """.trimIndent()
        val deserialized = json.decodeFromString<Book>(oldJson)
        assertEquals("book-compat", deserialized.id)
        assertTrue(deserialized.collections.isEmpty())
    }

    @Test
    fun testBook_collectionsJsonSerialization() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val book = createTestBook().copy(collections = listOf("Favorites", "Sci-Fi"))
        val encoded = json.encodeToString(kotlinx.serialization.serializer(), book)
        val decoded = json.decodeFromString<Book>(encoded)
        assertEquals(listOf("Favorites", "Sci-Fi"), decoded.collections)
    }

    @Test
    fun testReadingPreferences_backwardCompatibleJsonDeserialization() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        // Old preferences JSON without advanced typography or userShelves
        val oldPrefsJson = """
            {
                "fontSizeSp": 18.0,
                "lineSpacing": 1.5,
                "themeMode": "AMOLED_BLACK"
            }
        """.trimIndent()
        val decoded = json.decodeFromString<ReadingPreferences>(oldPrefsJson)
        assertEquals(18.0f, decoded.fontSizeSp)
        assertEquals(0.0f, decoded.letterSpacing)
        assertEquals(0.0f, decoded.wordSpacing)
        assertEquals(0.5f, decoded.paragraphSpacing)
        assertEquals(true, decoded.hyphens)
        assertEquals(listOf("Favorites", "To Read"), decoded.libraryPrefs.userShelves)
        assertEquals(com.example.lumareader.data.model.PageNavigationStyle.HORIZONTAL_SLIDE, decoded.navigationStyle)
    }

    @Test
    fun testReadingPreferences_navigationStyleSerialization() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        for (style in com.example.lumareader.data.model.PageNavigationStyle.values()) {
            val prefs = ReadingPreferences(navigationStyle = style)
            val encoded = json.encodeToString(kotlinx.serialization.serializer(), prefs)
            val decoded = json.decodeFromString<ReadingPreferences>(encoded)
            assertEquals(style, decoded.navigationStyle)
        }
    }

    @Test
    fun testShelfAssignmentAndFiltering_logic() {
        val book1 = createTestBook().copy(id = "b1", collections = listOf("Favorites"))
        val book2 = createTestBook().copy(id = "b2", collections = listOf("To Read"))
        val book3 = createTestBook().copy(id = "b3", collections = listOf("Favorites", "To Read"))
        val allBooks = listOf(book1, book2, book3)

        val favoritesOnly = allBooks.filter { it.collections.contains("Favorites") }
        assertEquals(2, favoritesOnly.size)
        assertTrue(favoritesOnly.any { it.id == "b1" })
        assertTrue(favoritesOnly.any { it.id == "b3" })

        val toReadOnly = allBooks.filter { it.collections.contains("To Read") }
        assertEquals(2, toReadOnly.size)
        assertTrue(toReadOnly.any { it.id == "b2" })
        assertTrue(toReadOnly.any { it.id == "b3" })
    }

    @Test
    fun testBatchStatusUpdate_logic() {
        val book1 = createTestBook(spineSize = 5, currentSpineIndex = 0, currentProgression = 0f).copy(id = "b1")
        val book2 = createTestBook(spineSize = 5, currentSpineIndex = 2, currentProgression = 0.5f).copy(id = "b2")
        val books = listOf(book1, book2)
        val selectedIds = setOf("b1", "b2")

        // Batch mark as FINISHED
        val finishedList = books.map { b ->
            if (selectedIds.contains(b.id)) {
                val lastSpine = if (b.spine.isNotEmpty()) b.spine.size - 1 else 0
                b.copy(currentSpineIndex = lastSpine, currentProgression = 1.0f)
            } else b
        }
        assertTrue(finishedList.all { it.readingStatus() == "FINISHED" })

        // Batch mark as UNREAD
        val unreadList = finishedList.map { b ->
            if (selectedIds.contains(b.id)) {
                b.copy(currentSpineIndex = 0, currentProgression = 0f)
            } else b
        }
        assertTrue(unreadList.all { it.readingStatus() == "UNREAD" })
    }

    @Test
    fun testSeriesFilter_sortingBySeriesNumber() {
        val book1 = createTestBook().copy(id = "b1", title = "Morning Star", series = "Red Rising", seriesNumber = 3f)
        val book2 = createTestBook().copy(id = "b2", title = "Red Rising", series = "Red Rising", seriesNumber = 1f)
        val book3 = createTestBook().copy(id = "b3", title = "Golden Son", series = "Red Rising", seriesNumber = 2f)
        val book4 = createTestBook().copy(id = "b4", title = "Iron Gold", series = "Red Rising", seriesNumber = 4f)
        val bookNovella = createTestBook().copy(id = "b5", title = "Bonus Prequel", series = "Red Rising", seriesNumber = null)

        val seriesBooks = listOf(book1, book2, book3, book4, bookNovella)

        // When filtering by series, sort by seriesNumber (ascending), with nulls at the end
        val sortedBySeries = seriesBooks.sortedWith(
            compareBy<Book> { it.seriesNumber ?: Float.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )

        assertEquals("Red Rising", sortedBySeries[0].title)
        assertEquals("Golden Son", sortedBySeries[1].title)
        assertEquals("Morning Star", sortedBySeries[2].title)
        assertEquals("Iron Gold", sortedBySeries[3].title)
        assertEquals("Bonus Prequel", sortedBySeries[4].title)
    }
}
