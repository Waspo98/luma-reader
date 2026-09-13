package com.example.lumareader

import androidx.compose.runtime.*

import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.lumareader.data.LocalBookRepository
import com.example.lumareader.ui.library.LibraryScreen
import com.example.lumareader.ui.reader.ReaderScreen
import com.example.lumareader.ui.settings.SettingsScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.*
import androidx.compose.animation.core.*

import com.example.lumareader.theme.toComposeColor

@Composable
fun MainNavigation(
    repository: LocalBookRepository,
    onImportBookClick: () -> Unit,
    isSyncing: Boolean = false,
    syncEmail: String? = null,
    lastSyncResult: com.example.lumareader.data.sync.SyncResult? = null,
    onConnectSync: () -> Unit = {},
    onDisconnectSync: () -> Unit = {},
    onTriggerSync: (com.example.lumareader.data.model.SyncScope) -> Unit = {},
    onSyncClick: () -> Unit = {},
    onExportBackupToFile: () -> Unit = {},
    onRestoreBackupFromFile: () -> Unit = {}
) {
    val backStack = rememberNavBackStack(Main)
    
    val books by repository.books.collectAsState()
    val preferences by repository.preferences.collectAsState()

    val accentColor = remember(preferences.accentColorHex) {
        preferences.accentColorHex.toComposeColor()
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
                    (scaleIn(
                        initialScale = 0.72f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                    )).togetherWith(
                        scaleOut(
                            targetScale = 0.90f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeOut(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
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
                    (scaleIn(
                        initialScale = 0.90f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                    )).togetherWith(
                        scaleOut(
                            targetScale = 0.72f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeOut(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
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
                        isSyncing = isSyncing,
                        syncEmail = syncEmail,
                        lastSyncResult = lastSyncResult,
                        onConnectSync = onConnectSync,
                        onDisconnectSync = onDisconnectSync,
                        onTriggerSync = onTriggerSync,
                        onPreferencesChanged = { repository.savePreferences(it) },
                        onDeleteBook = { repository.deleteBook(it) },
                        onDeleteBooks = { repository.deleteBooks(it) },
                        onUpdateBook = { repository.updateBook(it) },
                        onUpdateMetadata = { bookId, title, author, series, seriesNum, cover ->
                            repository.updateBookMetadata(bookId, title, author, series, seriesNum, cover)
                        },
                        onToggleBookStatus = { repository.toggleBookReadingStatus(it) },
                        onUpdateBooksStatus = { ids, status -> repository.updateBooksReadingStatus(ids, status) },
                        onCreateShelf = { repository.createUserShelf(it) },
                        onDeleteShelf = { repository.deleteUserShelf(it) },
                        onRenameShelf = { oldName, newName -> repository.renameUserShelf(oldName, newName) },
                        onAssignBookToShelf = { bookId, shelf -> repository.assignBookToShelf(bookId, shelf) },
                        onRemoveBookFromShelf = { bookId, shelf -> repository.removeBookFromShelf(bookId, shelf) },
                        onAssignBooksToShelf = { ids, shelf -> repository.assignBooksToShelf(ids, shelf) },
                        totalEpubSizeBytes = remember(books) { repository.getEpubStorageSizeBytes() }
                    )
                }
                
                entry<Reader> { key ->
                    DisposableEffect(key.bookId) {
                        onDispose {
                            if (syncEmail != null && preferences.syncPrefs.autoSyncOnClose) {
                                onTriggerSync(com.example.lumareader.data.model.SyncScope.READING_POSITION_ONLY)
                            }
                        }
                    }

                    val book = books.find { it.id == key.bookId }
                    if (book != null) {
                        ReaderScreen(
                            book = book,
                            preferences = preferences,
                            onBackClick = { backStack.removeLastOrNull() },
                            onPreferencesChanged = { repository.savePreferences(it) },
                            onProgressUpdated = { spineIndex, progress, locatorJson ->
                                if (locatorJson != null) {
                                    repository.updateBookLocator(book.id, locatorJson, spineIndex, progress)
                                } else {
                                    repository.updateBookProgress(book.id, spineIndex, progress)
                                }
                            },
                            onAddAnnotation = { repository.addAnnotation(it) },
                            onDeleteAnnotation = { repository.removeAnnotation(book.id, it) },
                            onUpdateAnnotation = { repository.updateAnnotation(it) }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = "Book not found",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Button(
                                    onClick = { backStack.removeLastOrNull() },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Return to Library")
                                }
                            }
                        }
                    }
                }
                
                entry<Settings> {
                    var footprint by remember { mutableStateOf(repository.getStorageFootprint()) }
                    SettingsScreen(
                        preferences = preferences,
                        onPreferencesChanged = { repository.savePreferences(it) },
                        onBackClick = { backStack.removeLastOrNull() },
                        onSyncClick = onSyncClick,
                        isSyncing = isSyncing,
                        syncEmail = syncEmail,
                        onDisconnectSync = onDisconnectSync,
                        storageFootprint = footprint,
                        onExportBackupToFile = onExportBackupToFile,
                        onRestoreBackupFromFile = onRestoreBackupFromFile,
                        onExportBackup = { repository.exportBackupJson() },
                        onImportBackup = { json ->
                            val success = repository.importBackupJson(json)
                            if (success) {
                                footprint = repository.getStorageFootprint()
                            }
                            success
                        },
                        onClearCoverCache = {
                            val freed = repository.clearCoverCache()
                            footprint = repository.getStorageFootprint()
                            freed
                        },
                        onReindexLibrary = {
                            val count = repository.reindexLibrary()
                            footprint = repository.getStorageFootprint()
                            count
                        }
                    )
                }
            }
        )
    }
}
