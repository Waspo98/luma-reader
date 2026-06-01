package com.example.lumareader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lumareader.data.model.Book
import com.example.lumareader.data.model.ReadingPreferences
import com.example.lumareader.data.model.LibrarySortOption
import com.example.lumareader.data.model.LibraryViewMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilteredLibraryScreen(
    filterType: String,
    filterValue: String,
    books: List<Book>,
    preferences: ReadingPreferences,
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onPreferencesChanged: (ReadingPreferences) -> Unit,
    onDeleteBook: (String) -> Unit,
    onUpdateMetadata: (String, String?, String?, String?, Float?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val libraryPrefs = preferences.libraryPrefs
    var showSortMenu by remember { mutableStateOf(false) }
    var showGridSlider by remember { mutableStateOf(false) }

    var showEditDialogForBook by remember { mutableStateOf<Book?>(null) }
    var showDeleteDialogForBook by remember { mutableStateOf<Book?>(null) }

    val filteredBooks = books.filter {
        when (filterType.lowercase()) {
            "author" -> it.author.equals(filterValue, ignoreCase = true)
            "series" -> it.series.equals(filterValue, ignoreCase = true)
            else -> false
        }
    }

    val statusFiltered = filteredBooks.filter { book ->
        libraryPrefs.activeFilter == "ALL" || book.readingStatus() == libraryPrefs.activeFilter
    }

    val sorted = when (libraryPrefs.sortOption) {
        LibrarySortOption.TITLE_ASC    -> statusFiltered.sortedBy { it.title.lowercase() }
        LibrarySortOption.TITLE_DESC   -> statusFiltered.sortedByDescending { it.title.lowercase() }
        LibrarySortOption.AUTHOR_ASC   -> statusFiltered.sortedBy { it.author.lowercase() }
        LibrarySortOption.AUTHOR_DESC  -> statusFiltered.sortedByDescending { it.author.lowercase() }
        LibrarySortOption.RECENT       -> statusFiltered.sortedByDescending { it.lastReadTimestamp }
        LibrarySortOption.PROGRESS     -> statusFiltered.sortedByDescending { it.overallProgress() }
    }
    
    val titleText = if (filterType.lowercase() == "author") "Books by $filterValue" else "$filterValue Series"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = titleText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val newMode = if (libraryPrefs.viewMode == LibraryViewMode.GRID) LibraryViewMode.LIST else LibraryViewMode.GRID
                        onPreferencesChanged(preferences.copy(libraryPrefs = libraryPrefs.copy(viewMode = newMode)))
                    }) {
                        Icon(
                            imageVector = if (libraryPrefs.viewMode == LibraryViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle view mode"
                        )
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            LibrarySortOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.name.replace("_", " ")) },
                                    onClick = {
                                        onPreferencesChanged(preferences.copy(libraryPrefs = libraryPrefs.copy(sortOption = option)))
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                    if (libraryPrefs.viewMode == LibraryViewMode.GRID) {
                        IconButton(onClick = { showGridSlider = !showGridSlider }) {
                            Icon(Icons.Default.Tune, contentDescription = "Resize grid")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            if (showGridSlider && libraryPrefs.viewMode == LibraryViewMode.GRID) {
                Slider(
                    value = libraryPrefs.gridItemWidthDp.toFloat(),
                    onValueChange = { newValue ->
                        onPreferencesChanged(preferences.copy(libraryPrefs = libraryPrefs.copy(gridItemWidthDp = newValue.toInt())))
                    },
                    valueRange = 80f..250f,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (libraryPrefs.viewMode == LibraryViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(libraryPrefs.gridItemWidthDp.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sorted, key = { it.id }) { book ->
                        BookGridCard(
                            book = book,
                            onClick = { onBookClick(book.id) },
                            onEdit = { showEditDialogForBook = book },
                            onDelete = { showDeleteDialogForBook = book },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sorted, key = { it.id }) { book ->
                        BookListRow(
                            book = book,
                            onClick = { onBookClick(book.id) },
                            onEdit = { showEditDialogForBook = book },
                            onDelete = { showDeleteDialogForBook = book },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialogForBook != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialogForBook = null },
            title = { Text("Delete '${showDeleteDialogForBook?.title}'?") },
            text = { Text("This will remove the book from your library and delete its cached data.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialogForBook?.id?.let { onDeleteBook(it) }
                        showDeleteDialogForBook = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogForBook = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditDialogForBook != null) {
        EditBookDialog(
            book = showEditDialogForBook!!,
            onDismiss = { showEditDialogForBook = null },
            onSave = { bookId, title, author, series, seriesNum, cover ->
                onUpdateMetadata(bookId, title, author, series, seriesNum, cover)
                showEditDialogForBook = null
            }
        )
    }
}
