package com.example.lumareader.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val series: String? = null,
    val seriesNumber: Float? = null,
    val coverPath: String? = null,
    val customCoverPath: String? = null,
    val unzippedDir: String,
    val spine: List<String>, // List of relative HTML file paths in order of reading
    val toc: List<TocItem>,  // Table of contents mapping titles to HTML hrefs
    val currentSpineIndex: Int = 0,
    val currentProgression: Float = 0f, // scroll position / progress (0.0 to 1.0)
    val dateAdded: Long = System.currentTimeMillis(),
    val lastReadTimestamp: Long = 0L,
    // --- New metadata fields ---
    val publisher: String? = null,
    val publishDate: String? = null,      // ISO date string from dc:date
    val language: String? = null,
    val description: String? = null,      // Synopsis / blurb
    val subjects: List<String> = emptyList(), // Genres / tags
    val collections: List<String> = emptyList(), // Custom user shelves / collections
    val isbn: String? = null,             // Primary ISBN identifier
    val subtitle: String? = null,         // booklore:subtitle or calibre:subtitle
    val pageCount: Int? = null,           // booklore:page_count
    val epubFilePath: String? = null,     // Path to the persistent .epub archive
    val lastLocatorJson: String? = null,  // Serialized Readium Locator JSON for exact resume
    val annotations: List<BookAnnotation> = emptyList() // Saved highlights & notes
) {
    fun overallProgress(): Float {
        return if (spine.isNotEmpty()) {
            (currentSpineIndex.toFloat() + currentProgression) / spine.size.toFloat()
        } else 0f
    }

    fun readingStatus(): String {
        val progress = overallProgress()
        return when {
            progress >= 0.96f -> "FINISHED"
            progress > 0f -> "READING"
            else -> "UNREAD"
        }
    }
}

@Serializable
data class TocItem(
    val title: String,
    val href: String // Relative reference inside the unzipped structure
)

@Serializable
data class BookAnnotation(
    val id: String,
    val bookId: String,
    val text: String,
    val note: String = "",
    val colorHex: String = "#FFE082",
    val style: AnnotationStyle = AnnotationStyle.HIGHLIGHT,
    val locatorJson: String = "",
    val spineIndex: Int = 0,
    val progression: Float = 0f,
    val timestamp: Long = 0L
)
