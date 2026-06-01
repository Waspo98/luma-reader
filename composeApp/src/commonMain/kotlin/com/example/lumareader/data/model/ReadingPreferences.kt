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
    val activeFilter: String = "ALL"         // "ALL", "READING", "UNREAD", "FINISHED"
)

@Serializable
enum class LumaThemeMode {
    LIGHT,
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
enum class ImageHandlingMode {
    ORIGINAL,
    INVERT_BW,
    INVERT_ALL
}

@Serializable
data class ReadingPreferences(
    val themeMode: LumaThemeMode = LumaThemeMode.SYSTEM,
    val fontFamily: String = "Literata", // Literata (serif), Inter (sans), System serif, System sans
    val fontSizeSp: Float = 20f,
    val lineSpacing: Float = 1.4f, // 1.0 to 2.0
    val marginDp: Int = 24,       // margins (8 to 48 dp)
    val marginTopDp: Int = 24,
    val marginBottomDp: Int = 24,
    val marginLeftDp: Int = 24,
    val marginRightDp: Int = 24,
    val marginLockMode: MarginLockMode = MarginLockMode.LOCK_ALL,
    val twoColumnLocked: Boolean = false, // lock to single column even if device is unfolded
    val extendBehindNotch: Boolean = false, // immersive notch cutout support
    val dropCapEnabled: Boolean = true, // drop cap support for the first paragraph
    val accentColorHex: String = "#D45D42", // custom accent color (default terracotta rust)
    val imageHandlingMode: ImageHandlingMode = ImageHandlingMode.INVERT_BW,
    val libraryPrefs: LibraryPreferences = LibraryPreferences()
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

