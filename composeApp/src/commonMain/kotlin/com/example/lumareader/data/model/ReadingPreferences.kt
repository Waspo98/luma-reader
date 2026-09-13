package com.example.lumareader.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class LibraryViewMode { GRID, LIST }

@Serializable
enum class LibrarySortOption {
    TITLE_ASC, TITLE_DESC,
    AUTHOR_ASC, AUTHOR_DESC,
    RECENT,     // most recently read first
    PROGRESS    // highest progress first
}

@Serializable
data class LibraryPreferences(
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
    val gridItemWidthDp: Int = 150,          // slider range: 80–250
    val sortOption: LibrarySortOption = LibrarySortOption.TITLE_ASC,
    val activeFilter: String = "ALL",        // "ALL", "READING", "UNREAD", "FINISHED"
    val userShelves: List<String> = listOf("Favorites", "To Read"),
    val activeShelf: String? = null,         // null means no shelf filter, or specific shelf name
    val showProgressBadges: Boolean = true,
    val showSeriesBadges: Boolean = true,
    val autoShelveBySubject: Boolean = false
)

@Serializable
enum class LumaThemeMode {
    LIGHT,
    WARM_SEPIA,
    SLATE_GRAY,
    AMOLED_BLACK,
    SYSTEM
}

@Serializable
enum class MarginLockMode {
    LOCK_ALL,
    LOCK_VH,
    UNLOCKED
}

@Serializable
enum class ColumnLayoutMode {
    AUTO,
    SINGLE,
    DUAL
}

@Serializable
enum class ImageHandlingMode {
    ORIGINAL,
    INVERT_BW,
    INVERT_ALL
}

@Serializable
enum class TextJustification {
    LEFT,
    RIGHT,
    FULL
}

@Serializable
enum class PageNavigationStyle {
    HORIZONTAL_SLIDE,
    PAGE_TURN,
    CONTINUOUS_SCROLL,
    INSTANT
}

@Serializable
enum class SyncScope {
    FULL_LIBRARY,           // Sync library contents, metadata, custom shelves, and reading positions
    READING_POSITION_ONLY   // Only sync reading progress and bookmarks; book files stay local
}

@Serializable
data class SyncPreferences(
    val syncScope: SyncScope = SyncScope.FULL_LIBRARY,
    val lastSyncTimestamp: Long? = null,
    val autoSyncOnOpen: Boolean = true,
    val autoSyncOnClose: Boolean = true,
    val connectedEmail: String? = null
)

@Serializable
enum class BottomBarDisplayMode {
    PAGES,   // Page 142 of 384 (18 pages left)
    TIME,    // Page 142 of 384 (6 min left)
    COMPACT  // Ch. 4 • 45%
}

@Serializable
enum class AnnotationStyle {
    HIGHLIGHT,
    UNDERLINE
}

@Serializable
data class ReadingPreferences(
    val themeMode: LumaThemeMode = LumaThemeMode.SYSTEM,
    val fontFamily: String = "Literata", // Literata (serif), Inter (sans), System serif, System sans
    val fontSizeSp: Float = 20f,
    val lineSpacing: Float = 1.4f, // 1.0 to 2.0
    val textJustification: TextJustification = TextJustification.FULL,
    val letterSpacing: Float = 0.0f, // -0.05 to 0.30
    val wordSpacing: Float = 0.0f,   // 0.0 to 0.80
    val paragraphSpacing: Float = 0.5f, // 0.0 to 2.0
    val paragraphIndent: Float = 1.0f,  // 0.0 to 2.5
    val hyphens: Boolean = true,        // auto-hyphenation toggle
    val publisherStyles: Boolean = false, // honor EPUB publisher stylesheet
    val marginDp: Int = 24,       // margins (8 to 48 dp)
    val marginTopDp: Int = 24,
    val marginBottomDp: Int = 24,
    val marginLeftDp: Int = 24,
    val marginRightDp: Int = 24,
    val marginLockMode: MarginLockMode = MarginLockMode.LOCK_ALL,
    val columnLayoutMode: ColumnLayoutMode = ColumnLayoutMode.AUTO,
    val extendBehindNotch: Boolean = false, // immersive notch cutout support
    val accentColorHex: String = "#D45D42", // custom accent color (default terracotta rust)
    val imageHandlingMode: ImageHandlingMode = ImageHandlingMode.INVERT_BW,
    val dayThemeMode: LumaThemeMode = LumaThemeMode.LIGHT, // Day theme when themeMode == SYSTEM
    val nightThemeMode: LumaThemeMode = LumaThemeMode.SLATE_GRAY, // Night theme when themeMode == SYSTEM
    val customFonts: List<String> = emptyList(), // paths or filenames of imported fonts
    val navigationStyle: PageNavigationStyle = PageNavigationStyle.HORIZONTAL_SLIDE,
    val volumeKeyNavigation: Boolean = true,
    val bottomBarDisplayMode: BottomBarDisplayMode = BottomBarDisplayMode.PAGES,
    val annotationsEnabled: Boolean = true,
    val defaultHighlightColorHex: String = "#FFE082",
    val annotationStyle: AnnotationStyle = AnnotationStyle.HIGHLIGHT,
    val readingSpeedWpm: Int = 250,
    val hapticsEnabled: Boolean = true,
    val keepScreenOn: Boolean = false,
    val immersiveMode: Boolean = true,
    val libraryPrefs: LibraryPreferences = LibraryPreferences(),
    val syncPrefs: SyncPreferences = SyncPreferences()
)

@Serializable
data class LumaBackup(
    val version: Int = 1,
    val timestamp: Long = 0L,
    val books: List<Book> = emptyList(),
    val preferences: ReadingPreferences = ReadingPreferences()
)

@Serializable
data class StorageFootprint(
    val booksSizeBytes: Long = 0L,
    val cacheSizeBytes: Long = 0L,
    val bookCount: Int = 0
)

val LibrarySortOption.displayLabel: String
    get() = when (this) {
        LibrarySortOption.TITLE_ASC -> "Title (A → Z)"
        LibrarySortOption.TITLE_DESC -> "Title (Z → A)"
        LibrarySortOption.AUTHOR_ASC -> "Author (A → Z)"
        LibrarySortOption.AUTHOR_DESC -> "Author (Z → A)"
        LibrarySortOption.RECENT -> "Recently read"
        LibrarySortOption.PROGRESS -> "Most progress"
    }

/**
 * Resolves whether dual columns should be displayed given device posture / width.
 *
 * Invariants:
 * 1. CONTINUOUS_SCROLL is strictly single column across all form factors (including foldables).
 * 2. In pagination modes (HORIZONTAL_SLIDE, PAGE_TURN, INSTANT):
 *    - AUTO: dual column when unfolded / wide (isFoldableOrWide == true), single column when folded / phone.
 *    - SINGLE: forced single column.
 *    - DUAL: forced dual column.
 */
fun ReadingPreferences.resolveDualColumns(isFoldableOrWide: Boolean): Boolean {
    if (navigationStyle == PageNavigationStyle.CONTINUOUS_SCROLL) {
        return false
    }
    return when (columnLayoutMode) {
        ColumnLayoutMode.AUTO -> isFoldableOrWide
        ColumnLayoutMode.SINGLE -> false
        ColumnLayoutMode.DUAL -> true
    }
}

