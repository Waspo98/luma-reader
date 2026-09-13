package com.example.lumareader.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.lumareader.data.model.Book
import com.example.lumareader.data.model.BookAnnotation
import com.example.lumareader.data.model.ReadingPreferences

class ReadiumReaderController {
    var goForward: () -> Unit = {}
    var goBackward: () -> Unit = {}
    var goToChapter: (spineIndex: Int, href: String) -> Unit = { _, _ -> }
    var goToProgression: (progression: Float) -> Unit = {}
    var goToLocator: (locatorJson: String) -> Unit = {}
    var isUiVisible: Boolean = false
}

@Composable
expect fun ReadiumEpubReader(
    book: Book,
    preferences: ReadingPreferences,
    controller: ReadiumReaderController,
    onProgressChanged: (totalProgression: Float, chapterIndex: Int, chapterTitle: String?, locatorJson: String) -> Unit,
    onPageInfoChanged: (currentPage: Int, totalPages: Int, chapterPagesLeft: Int) -> Unit,
    onToggleUI: () -> Unit,
    onAddAnnotation: (BookAnnotation) -> Unit = {},
    onDeleteAnnotation: (String) -> Unit = {},
    onUpdateAnnotation: (BookAnnotation) -> Unit = {},
    modifier: Modifier = Modifier
)
