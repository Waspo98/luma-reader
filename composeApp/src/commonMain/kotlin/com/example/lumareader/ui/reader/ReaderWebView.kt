package com.example.lumareader.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.lumareader.data.model.Book
import com.example.lumareader.data.model.ReadingPreferences

@Composable
expect fun ReaderWebView(
    book: Book,
    chapterPath: String,
    preferences: ReadingPreferences,
    initialProgression: Float,
    isUiVisible: Boolean,
    onProgressChanged: (Float) -> Unit,
    onPageInfoChanged: (currentPage: Int, totalPages: Int) -> Unit,
    onNextChapter: () -> Unit,
    onPrevChapter: () -> Unit,
    onToggleUI: () -> Unit,
    onNavigateToChapter: (Int, String?) -> Unit,
    targetHash: String?,
    modifier: Modifier = Modifier
)

