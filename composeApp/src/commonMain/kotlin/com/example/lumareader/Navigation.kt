package com.example.lumareader

import androidx.compose.runtime.*

import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.lumareader.data.LocalBookRepository
import com.example.lumareader.ui.library.LibraryScreen
import com.example.lumareader.ui.library.FilteredLibraryScreen
import com.example.lumareader.ui.reader.ReaderScreen
import com.example.lumareader.ui.settings.SettingsScreen

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.*
import androidx.compose.animation.core.*

@Composable
fun MainNavigation(
    repository: LocalBookRepository,
    onImportBookClick: () -> Unit,
    isSyncing: Boolean = false,
    syncEmail: String? = null,
    onSyncClick: () -> Unit = {}
) {
    val backStack = rememberNavBackStack(Main)
    
    val books by repository.books.collectAsState()
    val preferences by repository.preferences.collectAsState()

    val accentColor = remember(preferences.accentColorHex) {
        try {
            val hex = preferences.accentColorHex.removePrefix("#")
            val parsed = hex.toLong(16)
            if (hex.length == 6) Color(0xFF000000 or parsed) else Color(parsed)
        } catch (_: Exception) {
            Color(0xFFD45D42) // Fallback to default terracotta
        }
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = accentColor,
            primaryContainer = accentColor.copy(alpha = 0.15f),
            onPrimaryContainer = accentColor
        )
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            transitionSpec = {
                if (targetState.key is Reader) {
                    (slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                    ) + fadeIn(
                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                    ) + scaleIn(
                        initialScale = 0.9f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                    )).togetherWith(
                        fadeOut(
                            animationSpec = spring(stiffness = Spring.StiffnessLow)
                        ) + scaleOut(
                            targetScale = 0.96f,
                            animationSpec = spring(stiffness = Spring.StiffnessLow)
                        )
                    )
                } else {
                    (slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                    ) + fadeIn(
                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                    )).togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { -it / 3 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                        ) + fadeOut(
                            animationSpec = spring(stiffness = Spring.StiffnessLow)
                        )
                    )
                }
            },
            popTransitionSpec = {
                if (initialState.key is Reader) {
                    (fadeIn(
                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                    ) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                    )).togetherWith(
                        slideOutVertically(
                            targetOffsetY = { it / 3 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                        ) + fadeOut(
                            animationSpec = spring(stiffness = Spring.StiffnessLow)
                        ) + scaleOut(
                            targetScale = 0.9f,
                            animationSpec = spring(stiffness = Spring.StiffnessLow)
                        )
                    )
                } else {
                    (slideInHorizontally(
                        initialOffsetX = { -it / 3 },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                    ) + fadeIn(
                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                    )).togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                        ) + fadeOut(
                            animationSpec = spring(stiffness = Spring.StiffnessLow)
                        )
                    )
                }
            },
            entryProvider = entryProvider {
                entry<Main> {
                    LibraryScreen(
                        books = books,
                        preferences = preferences,
                        onBookClick = { bookId -> backStack.add(Reader(bookId)) },
                        onImportBookClick = onImportBookClick,
                        onSettingsClick = { backStack.add(Settings) },
                        onSyncClick = onSyncClick,
                        onPreferencesChanged = { repository.savePreferences(it) },
                        onDeleteBook = { repository.deleteBook(it) },
                        onUpdateMetadata = { bookId, title, author, series, seriesNum, cover ->
                            repository.updateBookMetadata(bookId, title, author, series, seriesNum, cover)
                        },
                        onAuthorClick = { author -> backStack.add(FilteredLibrary("author", author)) },
                        onSeriesClick = { series -> backStack.add(FilteredLibrary("series", series)) }
                    )
                }
                
                entry<Reader> { key ->
                    val book = books.find { it.id == key.bookId }
                    if (book != null) {
                        ReaderScreen(
                            book = book,
                            preferences = preferences,
                            onBackClick = { backStack.removeLastOrNull() },
                            onPreferencesChanged = { repository.savePreferences(it) },
                            onProgressUpdated = { spineIndex, progress ->
                                repository.updateBookProgress(book.id, spineIndex, progress)
                            }
                        )
                    }
                }
                
                entry<Settings> {
                    SettingsScreen(
                        preferences = preferences,
                        onPreferencesChanged = { repository.savePreferences(it) },
                        onBackClick = { backStack.removeLastOrNull() },
                        onSyncClick = onSyncClick,
                        isSyncing = isSyncing,
                        syncEmail = syncEmail
                    )
                }
                
                entry<FilteredLibrary> { key ->
                    FilteredLibraryScreen(
                        filterType = key.filterType,
                        filterValue = key.filterValue,
                        books = books,
                        preferences = preferences,
                        onBackClick = { backStack.removeLastOrNull() },
                        onBookClick = { bookId -> backStack.add(Reader(bookId)) },
                        onPreferencesChanged = { repository.savePreferences(it) },
                        onDeleteBook = { repository.deleteBook(it) },
                        onUpdateMetadata = { bookId, title, author, series, seriesNum, cover ->
                            repository.updateBookMetadata(bookId, title, author, series, seriesNum, cover)
                        }
                    )
                }
            }
        )
    }
}
