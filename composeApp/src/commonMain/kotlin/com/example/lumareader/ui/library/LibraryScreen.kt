package com.example.lumareader.ui.library

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lumareader.theme.GoogleSans
import com.example.lumareader.data.model.Book
import com.example.lumareader.data.model.ReadingPreferences
import com.example.lumareader.data.model.LibrarySortOption
import com.example.lumareader.data.model.LibraryViewMode
import com.example.lumareader.ui.components.BookCover
import com.example.lumareader.ui.components.BookDetailBottomSheet
import com.example.lumareader.ui.components.DeleteBookDialog
import com.example.lumareader.ui.components.LumaProgressBar
import com.example.lumareader.ui.components.SortBottomSheet
import com.example.lumareader.ui.components.SyncConfigBottomSheet
import com.example.lumareader.data.model.SyncScope
import com.example.lumareader.data.sync.SyncResult
import com.example.lumareader.ui.utils.LumaSlider
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.Dp
import com.example.lumareader.ui.utils.BackHandler
import com.example.lumareader.ui.components.FilterPill
import com.example.lumareader.ui.utils.LumaHapticFeedbackType
import com.example.lumareader.ui.utils.rememberLumaHaptics
import com.example.lumareader.ui.components.LumaConfirmationDialog
import com.example.lumareader.ui.library.components.BatchShelfDialog
import com.example.lumareader.ui.library.components.BatchStatusDialog
import com.example.lumareader.ui.library.components.BookGridCard
import com.example.lumareader.ui.library.components.BookListRow
import com.example.lumareader.ui.library.components.LibraryEmptyState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    books: List<Book>,
    preferences: ReadingPreferences,
    onBookClick: (String) -> Unit,
    onImportBookClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onSyncClick: () -> Unit = {},
    isSyncing: Boolean = false,
    syncEmail: String? = null,
    lastSyncResult: SyncResult? = null,
    onConnectSync: () -> Unit = {},
    onDisconnectSync: () -> Unit = {},
    onTriggerSync: (SyncScope) -> Unit = {},
    onPreferencesChanged: (ReadingPreferences) -> Unit,
    onDeleteBook: (String) -> Unit,
    onDeleteBooks: (Set<String>) -> Unit = { it.forEach(onDeleteBook) },
    onUpdateMetadata: (String, String?, String?, String?, Float?, String?) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateBook: (Book) -> Unit = {},
    onToggleBookStatus: (String) -> Unit = {},
    onUpdateBooksStatus: (Set<String>, String) -> Unit = { _, _ -> },
    onAuthorClick: (String) -> Unit = {},
    onSeriesClick: (String) -> Unit = {},
    onCreateShelf: (String) -> Unit = {},
    onDeleteShelf: (String) -> Unit = {},
    onRenameShelf: (String, String) -> Unit = { _, _ -> },
    onAssignBookToShelf: (String, String) -> Unit = { _, _ -> },
    onRemoveBookFromShelf: (String, String) -> Unit = { _, _ -> },
    onAssignBooksToShelf: (Set<String>, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showSortBottomSheet by remember { mutableStateOf(false) }
    var showSyncBottomSheet by remember { mutableStateOf(false) }
    var showGridSlider by remember { mutableStateOf(false) }
    
    var activeFilterType by remember { mutableStateOf<String?>(null) }
    var activeFilterValue by remember { mutableStateOf<String?>(null) }
    var selectedBookForDetail by remember { mutableStateOf<Book?>(null) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedBookIds by remember { mutableStateOf(emptySet<String>()) }
    val haptics = rememberLumaHaptics(preferences.hapticsEnabled)

    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var showManageShelvesDialog by remember { mutableStateOf(false) }
    var showBatchShelfDialog by remember { mutableStateOf(false) }
    var showBatchStatusDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    var showEditDialogForBook by remember { mutableStateOf<Book?>(null) }
    var showDeleteDialogForBook by remember { mutableStateOf<Book?>(null) }

    BackHandler(enabled = isSelectionMode) {
        haptics.perform(LumaHapticFeedbackType.TAP)
        isSelectionMode = false
        selectedBookIds = emptySet()
    }

    val libraryPrefs = preferences.libraryPrefs

    // 1. Filter by search query
    val searchFiltered = books.filter { book ->
        searchQuery.isBlank() ||
        book.title.contains(searchQuery, ignoreCase = true) ||
        book.author.contains(searchQuery, ignoreCase = true)
    }

    // 2. Filter by in-place metadata chip (author or series)
    val metadataFiltered = searchFiltered.filter { book ->
        when (activeFilterType) {
            "author" -> activeFilterValue.isNullOrBlank() || book.author.equals(activeFilterValue, ignoreCase = true)
            "series" -> activeFilterValue.isNullOrBlank() || book.series?.equals(activeFilterValue, ignoreCase = true) == true
            else -> true
        }
    }

    // 3. Filter by active custom shelf
    val shelfFiltered = metadataFiltered.filter { book ->
        libraryPrefs.activeShelf == null || book.collections.contains(libraryPrefs.activeShelf)
    }

    // 4. Filter by reading status chip
    val statusFiltered = shelfFiltered.filter { book ->
        libraryPrefs.activeFilter == "ALL" || book.readingStatus() == libraryPrefs.activeFilter
    }

    // 5. Sort: when filtering by series, automatically sort by # in series (seriesNumber);
    // when removing the series filter, automatically revert to the user's previously selected sortOption.
    val sorted = if (activeFilterType == "series" && !activeFilterValue.isNullOrBlank()) {
        statusFiltered.sortedWith(
            compareBy<Book> { it.seriesNumber ?: Float.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )
    } else {
        when (libraryPrefs.sortOption) {
            LibrarySortOption.TITLE_ASC    -> statusFiltered.sortedBy { it.title.lowercase() }
            LibrarySortOption.TITLE_DESC   -> statusFiltered.sortedByDescending { it.title.lowercase() }
            LibrarySortOption.AUTHOR_ASC   -> statusFiltered.sortedBy { it.author.lowercase() }
            LibrarySortOption.AUTHOR_DESC  -> statusFiltered.sortedByDescending { it.author.lowercase() }
            LibrarySortOption.RECENT       -> statusFiltered.sortedByDescending { it.lastReadTimestamp }
            LibrarySortOption.PROGRESS     -> statusFiltered.sortedByDescending { it.overallProgress() }
        }
    }


    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    val isAtTop by remember {
        derivedStateOf {
            if (libraryPrefs.viewMode == LibraryViewMode.GRID) {
                gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
            } else {
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            }
        }
    }

    var isHeaderExpanded by remember { mutableStateOf(true) }
    var scrollDeltaAccumulator by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy < -10f) {
                    // Scrolling down: collapse header into docked capsule
                    if (isHeaderExpanded) {
                        scrollDeltaAccumulator += dy
                        if (scrollDeltaAccumulator < -25f) {
                            isHeaderExpanded = false
                            scrollDeltaAccumulator = 0f
                        }
                    }
                } else if (dy > 10f) {
                    // Scrolling up (Quick return!): expand header
                    if (!isHeaderExpanded) {
                        scrollDeltaAccumulator += dy
                        if (scrollDeltaAccumulator > 15f) {
                            isHeaderExpanded = true
                            scrollDeltaAccumulator = 0f
                        }
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(isAtTop) {
        if (isAtTop) {
            isHeaderExpanded = true
            scrollDeltaAccumulator = 0f
        }
    }

    val isCollapsed = !isHeaderExpanded && !isSearchActive && searchQuery.isEmpty()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val screenWidth = maxWidth

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButtonPosition = FabPosition.End,
            floatingActionButton = {
                var fabVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    fabVisible = true
                }
                AnimatedVisibility(
                    visible = fabVisible && !isSelectionMode,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                    ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                    exit = fadeOut(),
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    ExtendedFloatingActionButton(
                        onClick = onImportBookClick,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        icon = { Icon(Icons.Default.ImportContacts, contentDescription = "Import book") },
                        text = { Text("Import ePub", fontWeight = FontWeight.SemiBold) },
                        expanded = isAtTop || isHeaderExpanded
                    )
                }
            }
        ) { _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .nestedScroll(nestedScrollConnection)
            ) {
                LibraryListPane(
                    books = books,
                    sortedBooks = sorted,
                    metadataFiltered = metadataFiltered,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = { isSearchActive = it },
                    isCollapsed = isCollapsed,
                    onExpandHeader = { isHeaderExpanded = true },
                    libraryPrefs = libraryPrefs,
                    preferences = preferences,
                    onPreferencesChanged = onPreferencesChanged,
                    onSettingsClick = onSettingsClick,
                    onShowSyncBottomSheet = { showSyncBottomSheet = true },
                    isSyncing = isSyncing,
                    syncEmail = syncEmail,
                    showGridSlider = showGridSlider,
                    onToggleGridSlider = { showGridSlider = !showGridSlider },
                    onShowSortBottomSheet = { showSortBottomSheet = true },
                    gridState = gridState,
                    listState = listState,
                    isSelectionMode = isSelectionMode,
                    selectedBookIds = selectedBookIds,
                    onToggleBookSelection = { id ->
                        haptics.perform(LumaHapticFeedbackType.TAP)
                        selectedBookIds = if (selectedBookIds.contains(id)) selectedBookIds - id else selectedBookIds + id
                    },
                    onEnterSelectionMode = { id ->
                        haptics.perform(LumaHapticFeedbackType.LONG_PRESS)
                        isSelectionMode = true
                        selectedBookIds = setOf(id)
                    },
                    onToggleSelectionMode = {
                        haptics.perform(LumaHapticFeedbackType.TAP)
                        isSelectionMode = !isSelectionMode
                        if (!isSelectionMode) selectedBookIds = emptySet()
                    },
                    onCreateShelfClick = { showCreateShelfDialog = true },
                    onManageShelvesClick = { showManageShelvesDialog = true },
                    onBookSelected = { book -> onBookClick(book.id) },
                    onBookLongClick = { book ->
                        if (isSelectionMode) {
                            haptics.perform(LumaHapticFeedbackType.TAP)
                            selectedBookIds = if (selectedBookIds.contains(book.id)) selectedBookIds - book.id else selectedBookIds + book.id
                        } else {
                            haptics.perform(LumaHapticFeedbackType.LONG_PRESS)
                            selectedBookForDetail = book
                        }
                    },
                    onEditBook = { showEditDialogForBook = it },
                    onDeleteBook = { showDeleteDialogForBook = it },
                    activeFilterType = activeFilterType,
                    activeFilterValue = activeFilterValue,
                    onClearMetadataFilter = {
                        activeFilterType = null
                        activeFilterValue = null
                    },
                    onAuthorSelected = { author ->
                        activeFilterType = "author"
                        activeFilterValue = author
                        onAuthorClick(author)
                    },
                    onSeriesSelected = { series ->
                        activeFilterType = "series"
                        activeFilterValue = series
                        onSeriesClick(series)
                    },
                    paneWidth = screenWidth,
                    onImportBookClick = onImportBookClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Batch Action Bar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            BatchActionBar(
                selectedCount = selectedBookIds.size,
                totalCount = sorted.size,
                onSelectAll = {
                    haptics.perform(LumaHapticFeedbackType.TAP)
                    selectedBookIds = sorted.map { it.id }.toSet()
                },
                onDeselectAll = {
                    haptics.perform(LumaHapticFeedbackType.TAP)
                    selectedBookIds = emptySet()
                },
                onAssignShelfClick = { showBatchShelfDialog = true },
                onMarkStatusClick = { showBatchStatusDialog = true },
                onDeleteClick = { showBatchDeleteDialog = true },
                onCloseSelectionMode = {
                    haptics.perform(LumaHapticFeedbackType.TAP)
                    isSelectionMode = false
                    selectedBookIds = emptySet()
                }
            )
        }
    }

    if (showSortBottomSheet) {
        SortBottomSheet(
            currentSort = libraryPrefs.sortOption,
            onSortSelected = { option ->
                onPreferencesChanged(preferences.copy(libraryPrefs = libraryPrefs.copy(sortOption = option)))
            },
            onDismiss = { showSortBottomSheet = false }
        )
    }

    if (showSyncBottomSheet) {
        SyncConfigBottomSheet(
            syncPrefs = preferences.syncPrefs,
            isSyncing = isSyncing,
            connectedEmail = syncEmail,
            lastSyncResult = lastSyncResult,
            onDismiss = { showSyncBottomSheet = false },
            onScopeSelected = { scope ->
                onPreferencesChanged(
                    preferences.copy(
                        syncPrefs = preferences.syncPrefs.copy(syncScope = scope)
                    )
                )
            },
            onConnectClick = onConnectSync,
            onDisconnectClick = onDisconnectSync,
            onSyncNowClick = { onTriggerSync(preferences.syncPrefs.syncScope) },
            onAutoSyncToggled = { enabled ->
                onPreferencesChanged(
                    preferences.copy(
                        syncPrefs = preferences.syncPrefs.copy(autoSyncOnOpen = enabled)
                    )
                )
            }
        )
    }

    selectedBookForDetail?.let { book ->
        BookDetailBottomSheet(
            book = book,
            onDismiss = { selectedBookForDetail = null },
            onOpenBook = { bookId ->
                selectedBookForDetail = null
                onBookClick(bookId)
            },
            onEditMetadata = { targetBook ->
                selectedBookForDetail = null
                showEditDialogForBook = targetBook
            },
            onAssignToShelf = { targetBook ->
                selectedBookForDetail = null
                selectedBookIds = setOf(targetBook.id)
                showBatchShelfDialog = true
            },
            onToggleStatus = { bookId ->
                onToggleBookStatus(bookId)
            },
            onDeleteBook = { targetBook ->
                selectedBookForDetail = null
                showDeleteDialogForBook = targetBook
            },
            onSelectInBatch = { bookId ->
                selectedBookForDetail = null
                isSelectionMode = true
                selectedBookIds = setOf(bookId)
            },
            onAuthorClick = { author ->
                selectedBookForDetail = null
                activeFilterType = "author"
                activeFilterValue = author
                onAuthorClick(author)
            },
            onSeriesClick = { series ->
                selectedBookForDetail = null
                activeFilterType = "series"
                activeFilterValue = series
                onSeriesClick(series)
            }
        )
    }

    showDeleteDialogForBook?.let { bookToDelete ->
        DeleteBookDialog(
            book = bookToDelete,
            onConfirm = {
                onDeleteBook(bookToDelete.id)
                showDeleteDialogForBook = null
            },
            onDismiss = { showDeleteDialogForBook = null }
        )
    }

    if (showEditDialogForBook != null) {
        EditBookDialog(
            book = showEditDialogForBook!!,
            availableShelves = libraryPrefs.userShelves,
            onDismiss = { showEditDialogForBook = null },
            onSaveBook = { updatedBook ->
                onUpdateBook(updatedBook)
                showEditDialogForBook = null
            }
        )
    }

    // Create Shelf Dialog
    if (showCreateShelfDialog) {
        CreateShelfDialog(
            existingShelves = libraryPrefs.userShelves,
            onDismiss = { showCreateShelfDialog = false },
            onCreateShelf = { newShelf ->
                onCreateShelf(newShelf)
                showCreateShelfDialog = false
            }
        )
    }

    // Manage Shelves Dialog
    if (showManageShelvesDialog) {
        ManageShelvesDialog(
            shelves = libraryPrefs.userShelves,
            onDismiss = { showManageShelvesDialog = false },
            onDeleteShelf = { onDeleteShelf(it) },
            onRenameShelf = { oldName, newName -> onRenameShelf(oldName, newName) },
            onCreateNewShelfClick = {
                showManageShelvesDialog = false
                showCreateShelfDialog = true
            }
        )
    }

    // Batch Assign Shelf Dialog
    if (showBatchShelfDialog) {
        BatchShelfDialog(
            selectedCount = selectedBookIds.size,
            userShelves = libraryPrefs.userShelves,
            onAssignShelf = { shelf ->
                onAssignBooksToShelf(selectedBookIds, shelf)
                showBatchShelfDialog = false
                isSelectionMode = false
                selectedBookIds = emptySet()
            },
            onCreateNewShelfClick = {
                showBatchShelfDialog = false
                showCreateShelfDialog = true
            },
            onDismiss = { showBatchShelfDialog = false }
        )
    }

    // Batch Status Dialog
    if (showBatchStatusDialog) {
        BatchStatusDialog(
            selectedCount = selectedBookIds.size,
            onStatusSelected = { statusKey ->
                onUpdateBooksStatus(selectedBookIds, statusKey)
                showBatchStatusDialog = false
                isSelectionMode = false
                selectedBookIds = emptySet()
            },
            onDismiss = { showBatchStatusDialog = false }
        )
    }

    // Batch Delete Confirmation Dialog
    if (showBatchDeleteDialog) {
        LumaConfirmationDialog(
            title = "Delete ${selectedBookIds.size} Books?",
            message = "This will permanently remove the selected books and all reading progress from your device.",
            confirmText = "Delete All",
            dismissText = "Cancel",
            isDestructive = true,
            onConfirm = {
                onDeleteBooks(selectedBookIds)
                showBatchDeleteDialog = false
                isSelectionMode = false
                selectedBookIds = emptySet()
            },
            onDismiss = { showBatchDeleteDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryListPane(
    books: List<Book>,
    sortedBooks: List<Book>,
    metadataFiltered: List<Book>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    isCollapsed: Boolean,
    onExpandHeader: () -> Unit = {},
    libraryPrefs: com.example.lumareader.data.model.LibraryPreferences,
    preferences: ReadingPreferences,
    onPreferencesChanged: (ReadingPreferences) -> Unit,
    onSettingsClick: () -> Unit = {},
    onShowSyncBottomSheet: () -> Unit = {},
    isSyncing: Boolean = false,
    syncEmail: String? = null,
    showGridSlider: Boolean,
    onToggleGridSlider: () -> Unit,
    onShowSortBottomSheet: () -> Unit,
    gridState: LazyGridState,
    listState: LazyListState,
    isSelectionMode: Boolean = false,
    selectedBookIds: Set<String> = emptySet(),
    onToggleBookSelection: (String) -> Unit = {},
    onEnterSelectionMode: (String) -> Unit = {},
    onToggleSelectionMode: () -> Unit = {},
    onCreateShelfClick: () -> Unit = {},
    onManageShelvesClick: () -> Unit = {},
    onBookSelected: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit = {},
    onEditBook: (Book) -> Unit,
    onDeleteBook: (Book) -> Unit,
    onImportBookClick: () -> Unit,
    activeFilterType: String?,
    activeFilterValue: String?,
    onClearMetadataFilter: () -> Unit,
    onAuthorSelected: (String) -> Unit,
    onSeriesSelected: (String) -> Unit,
    paneWidth: Dp,
    modifier: Modifier = Modifier
) {
    val maxCols = when {
        paneWidth < 500.dp -> 3
        paneWidth < 800.dp -> 5
        else -> 7
    }
    val minCols = 2
    
    val columnsCount = remember(libraryPrefs.gridItemWidthDp, paneWidth) {
        (paneWidth.value / libraryPrefs.gridItemWidthDp.toFloat()).toInt().coerceIn(minCols, maxCols)
    }

    // Filter counts and active filter states
    val allCount = metadataFiltered.size
    val readingCount = remember(metadataFiltered) { metadataFiltered.count { it.readingStatus() == "READING" } }
    val unreadCount = remember(metadataFiltered) { metadataFiltered.count { it.readingStatus() == "UNREAD" } }
    val finishedCount = remember(metadataFiltered) { metadataFiltered.count { it.readingStatus() == "FINISHED" } }

    val hasActiveStatus = libraryPrefs.activeFilter != "ALL" && libraryPrefs.activeShelf == null
    val hasActiveShelf = libraryPrefs.activeShelf != null
    val hasActiveMetadata = activeFilterType != null && activeFilterValue != null
    val hasAnyActiveFilter = hasActiveStatus || hasActiveShelf || hasActiveMetadata

    Column(modifier = modifier) {
        // Top Header Section
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .fillMaxWidth()
        ) {
            // Title & Actions Top Bar (Morphs when scrolled)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Brand/Logo (Morphs between full title and compact brand)
                AnimatedContent(
                    targetState = isCollapsed && !isSearchActive,
                    transitionSpec = {
                        (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                         scaleIn(initialScale = 0.9f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)))
                            .togetherWith(
                                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                scaleOut(targetScale = 0.9f, animationSpec = spring(stiffness = Spring.StiffnessLow))
                            )
                    },
                    label = "BrandTransition"
                ) { collapsed ->
                    if (collapsed) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onExpandHeader() }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = "Luma Reader",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Luma",
                                fontFamily = GoogleSans,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = "Luma Reader",
                            fontFamily = GoogleSans,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // Center: Docked Search Capsule (Morphs into top bar when collapsed)
                AnimatedVisibility(
                    visible = isCollapsed && !isSearchActive,
                    enter = expandHorizontally(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                        expandFrom = Alignment.End
                    ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                    exit = shrinkHorizontally(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                        shrinkTowards = Alignment.End
                    ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .height(38.dp)
                            .clickable {
                                onExpandHeader()
                                onSearchActiveChange(true)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotEmpty()) searchQuery else "Search...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (searchQuery.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchQueryChange("") },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isCollapsed || isSearchActive) {
                    Spacer(Modifier.weight(1f))
                }

                var isMenuOpen by remember { mutableStateOf(false) }
                val isNonDefaultSort = libraryPrefs.sortOption != LibrarySortOption.TITLE_ASC

                // Playful rotation & scale for the More button
                val buttonRotation by animateFloatAsState(
                    targetValue = if (isMenuOpen) 90f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "TopMenuButtonRotation"
                )
                val buttonScale by animateFloatAsState(
                    targetValue = if (isMenuOpen) 1.08f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "TopMenuButtonScale"
                )

                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        shape = CircleShape,
                        color = if (isMenuOpen) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        modifier = Modifier.scale(buttonScale)
                    ) {
                        IconButton(
                            onClick = {
                                isMenuOpen = !isMenuOpen
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = if (isMenuOpen || isSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.graphicsLayer {
                                        rotationZ = buttonRotation
                                    }
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = isMenuOpen,
                        onDismissRequest = { isMenuOpen = false },
                        shape = MaterialTheme.shapes.large,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        ),
                        modifier = Modifier.widthIn(min = 220.dp, max = 270.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isSelectionMode) "Done selecting" else "Select books",
                                    fontWeight = if (isSelectionMode) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isSelectionMode) Icons.Default.CheckCircle else Icons.Default.Checklist,
                                    contentDescription = null,
                                    tint = if (isSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                isMenuOpen = false
                                onToggleSelectionMode()
                            },
                            modifier = Modifier.clip(MaterialTheme.shapes.medium)
                        )

                        if (libraryPrefs.viewMode == LibraryViewMode.GRID) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (showGridSlider) "Hide grid slider" else "Resize grid",
                                        fontWeight = if (showGridSlider) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = if (showGridSlider) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    isMenuOpen = false
                                    onToggleGridSlider()
                                },
                                modifier = Modifier.clip(MaterialTheme.shapes.medium)
                            )
                        }

                        DropdownMenuItem(
                            text = {
                                Text(if (libraryPrefs.viewMode == LibraryViewMode.GRID) "List view" else "Grid view")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (libraryPrefs.viewMode == LibraryViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                isMenuOpen = false
                                val newMode = if (libraryPrefs.viewMode == LibraryViewMode.GRID) LibraryViewMode.LIST else LibraryViewMode.GRID
                                onPreferencesChanged(preferences.copy(libraryPrefs = libraryPrefs.copy(viewMode = newMode)))
                            },
                            modifier = Modifier.clip(MaterialTheme.shapes.medium)
                        )

                        DropdownMenuItem(
                            text = { Text("Sort") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                isMenuOpen = false
                                onShowSortBottomSheet()
                            },
                            modifier = Modifier.clip(MaterialTheme.shapes.medium)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isSyncing) "Syncing..."
                                    else "Google Drive sync"
                                )
                            },
                            leadingIcon = {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                isMenuOpen = false
                                onShowSyncBottomSheet()
                            },
                            modifier = Modifier.clip(MaterialTheme.shapes.medium)
                        )

                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                isMenuOpen = false
                                onSettingsClick()
                            },
                            modifier = Modifier.clip(MaterialTheme.shapes.medium)
                        )
                    }
                }
            }

            // SearchBar spanning cleanly across the width (Collapses into docked capsule when scrolled)
            AnimatedVisibility(
                visible = !isCollapsed || isSearchActive,
                enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    SearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = searchQuery,
                                onQueryChange = onSearchQueryChange,
                                onSearch = { onSearchActiveChange(false) },
                                expanded = isSearchActive,
                                onExpandedChange = onSearchActiveChange,
                                placeholder = { Text("Search your library") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (isSearchActive) {
                                        IconButton(onClick = {
                                            onSearchQueryChange("")
                                            onSearchActiveChange(false)
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close search")
                                        }
                                    }
                                }
                            )
                        },
                        expanded = isSearchActive,
                        onExpandedChange = onSearchActiveChange,
                        colors = SearchBarDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(sortedBooks, key = { it.id }) { book ->
                                BookListRow(
                                    book = book,
                                    isSelectionMode = isSelectionMode,
                                    isSelectedInBatch = selectedBookIds.contains(book.id),
                                    onToggleSelection = { onToggleBookSelection(book.id) },
                                    onLongClick = {
                                        onSearchActiveChange(false)
                                        onBookLongClick(book)
                                    },
                                    onClick = {
                                        onSearchActiveChange(false)
                                        onBookSelected(book)
                                    },
                                    onAuthorClick = { onAuthorSelected(book.author); onSearchActiveChange(false) },
                                    onSeriesClick = { book.series?.let { onSeriesSelected(it) }; onSearchActiveChange(false) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dedicated Filter & Shelves Row (Single, smooth horizontal scroll - hides when collapsed)
        AnimatedVisibility(
            visible = !isCollapsed && !isSearchActive,
            enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val filters = listOf(
                    Triple("ALL", "All", Icons.AutoMirrored.Filled.LibraryBooks to allCount),
                    Triple("READING", "Reading", Icons.AutoMirrored.Filled.MenuBook to readingCount),
                    Triple("UNREAD", "New", Icons.Default.FiberNew to unreadCount),
                    Triple("FINISHED", "Finished", Icons.Default.CheckCircle to finishedCount)
                )
                
                filters.forEach { (filterKey, label, pair) ->
                    val (icon, count) = pair
                    FilterPill(
                        selected = libraryPrefs.activeFilter == filterKey && libraryPrefs.activeShelf == null,
                        label = label,
                        count = count,
                        icon = icon,
                        onClick = {
                            onPreferencesChanged(
                                preferences.copy(
                                    libraryPrefs = libraryPrefs.copy(
                                        activeFilter = filterKey,
                                        activeShelf = null
                                    )
                                )
                            )
                        }
                    )
                }

                // Divider before shelves
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(20.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )

                // User Shelves Pills
                libraryPrefs.userShelves.forEach { shelf ->
                    val shelfCount = metadataFiltered.count { it.collections.contains(shelf) }
                    FilterPill(
                        selected = libraryPrefs.activeShelf == shelf,
                        label = shelf,
                        count = shelfCount,
                        icon = Icons.Default.Bookmark,
                        onClick = {
                            val nextShelf = if (libraryPrefs.activeShelf == shelf) null else shelf
                            onPreferencesChanged(
                                preferences.copy(
                                    libraryPrefs = libraryPrefs.copy(activeShelf = nextShelf)
                                )
                            )
                        }
                    )
                }

                // "+ Shelf" Pill
                Surface(
                    onClick = onCreateShelfClick,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Shelf",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Shelf",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Active Filters Micro-Bar (Shown when header is collapsed and any filter is active)
        AnimatedVisibility(
            visible = isCollapsed && !isSearchActive && hasAnyActiveFilter,
            enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
            exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasActiveStatus) {
                    val statusLabel = when (libraryPrefs.activeFilter) {
                        "READING" -> "Reading"
                        "UNREAD" -> "New"
                        "FINISHED" -> "Finished"
                        else -> libraryPrefs.activeFilter
                    }
                    val statusCount = when (libraryPrefs.activeFilter) {
                        "READING" -> readingCount
                        "UNREAD" -> unreadCount
                        "FINISHED" -> finishedCount
                        else -> null
                    }
                    val statusIcon = when (libraryPrefs.activeFilter) {
                        "READING" -> Icons.AutoMirrored.Filled.MenuBook
                        "UNREAD" -> Icons.Default.FiberNew
                        "FINISHED" -> Icons.Default.CheckCircle
                        else -> Icons.AutoMirrored.Filled.LibraryBooks
                    }
                    FilterPill(
                        selected = true,
                        label = statusLabel,
                        count = statusCount,
                        icon = statusIcon,
                        onClear = {
                            onPreferencesChanged(preferences.copy(libraryPrefs = libraryPrefs.copy(activeFilter = "ALL")))
                        },
                        onClick = onExpandHeader
                    )
                }

                if (hasActiveShelf && libraryPrefs.activeShelf != null) {
                    val shelfName = libraryPrefs.activeShelf
                    val shelfCount = metadataFiltered.count { it.collections.contains(shelfName) }
                    FilterPill(
                        selected = true,
                        label = shelfName,
                        count = shelfCount,
                        icon = Icons.Default.Bookmark,
                        onClear = {
                            onPreferencesChanged(preferences.copy(libraryPrefs = libraryPrefs.copy(activeShelf = null)))
                        },
                        onClick = onExpandHeader
                    )
                }

                if (hasActiveMetadata) {
                    FilterPill(
                        selected = true,
                        label = "${if (activeFilterType == "author") "Author: " else "Series: "}$activeFilterValue",
                        icon = if (activeFilterType == "author") Icons.Default.Person else Icons.Default.CollectionsBookmark,
                        onClear = onClearMetadataFilter,
                        onClick = onExpandHeader
                    )
                }
            }
        }

        // Active Metadata Filter Chip (Author / Series) (Shown when expanded)
        AnimatedVisibility(
            visible = !isCollapsed && activeFilterType != null && activeFilterValue != null && !isSearchActive,
            enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InputChip(
                    selected = true,
                    onClick = onClearMetadataFilter,
                    label = {
                        Text(
                            text = "${if (activeFilterType == "author") "Author: " else "Series: "}${activeFilterValue.orEmpty()}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear filter",
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (activeFilterType == "author") Icons.Default.Person else Icons.Default.CollectionsBookmark,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // Grid size slider
        AnimatedVisibility(
            visible = !isCollapsed && showGridSlider && libraryPrefs.viewMode == LibraryViewMode.GRID && !isSearchActive,
            enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val defaultCols = remember(paneWidth.value, minCols, maxCols) {
                    ((paneWidth.value / 150).toInt().coerceIn(minCols, maxCols)).toFloat()
                }
                LumaSlider(
                    label = "Grid Columns",
                    value = columnsCount.toFloat(),
                    defaultValue = defaultCols,
                    onValueChangeFinished = { newColsFloat ->
                        val newCols = newColsFloat.toInt().coerceIn(minCols, maxCols)
                        val newWidth = (paneWidth.value / newCols).toInt().coerceIn(80, 250)
                        onPreferencesChanged(preferences.copy(libraryPrefs = libraryPrefs.copy(gridItemWidthDp = newWidth)))
                    },
                    valueRange = minCols.toFloat()..maxCols.toFloat(),
                    steps = (maxCols - minCols - 1).coerceAtLeast(0),
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                    valueFormatter = { "${it.toInt()} Columns" }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Content Area
        if (books.isEmpty()) {
            LibraryEmptyState(onImportBookClick)
        } else {
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val listBottomPadding = 100.dp + navBarBottom

            AnimatedContent(
                targetState = libraryPrefs.viewMode,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                     scaleIn(initialScale = 0.95f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)))
                        .togetherWith(
                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                            scaleOut(targetScale = 0.95f, animationSpec = spring(stiffness = Spring.StiffnessLow))
                        )
                },
                modifier = Modifier.fillMaxSize(),
                label = "libraryContentTransition"
            ) { viewMode ->
                if (viewMode == LibraryViewMode.GRID) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columnsCount),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = listBottomPadding),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(sortedBooks, key = { it.id }) { book ->
                            BookGridCard(
                                book = book,
                                isSelectionMode = isSelectionMode,
                                isSelectedInBatch = selectedBookIds.contains(book.id),
                                onToggleSelection = { onToggleBookSelection(book.id) },
                                onLongClick = { onBookLongClick(book) },
                                onClick = {
                                    if (isSelectionMode) onToggleBookSelection(book.id)
                                    else onBookSelected(book)
                                },
                                onAuthorClick = { onAuthorSelected(book.author) },
                                onSeriesClick = { book.series?.let { onSeriesSelected(it) } },
                                showProgressBadges = libraryPrefs.showProgressBadges,
                                showSeriesBadges = libraryPrefs.showSeriesBadges,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = listBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(sortedBooks, key = { it.id }) { book ->
                            BookListRow(
                                book = book,
                                isSelectionMode = isSelectionMode,
                                isSelectedInBatch = selectedBookIds.contains(book.id),
                                onToggleSelection = { onToggleBookSelection(book.id) },
                                onLongClick = { onBookLongClick(book) },
                                onClick = {
                                    if (isSelectionMode) onToggleBookSelection(book.id)
                                    else onBookSelected(book)
                                },
                                onAuthorClick = { onAuthorSelected(book.author) },
                                onSeriesClick = { book.series?.let { onSeriesSelected(it) } },
                                showProgressBadges = libraryPrefs.showProgressBadges,
                                showSeriesBadges = libraryPrefs.showSeriesBadges,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}
