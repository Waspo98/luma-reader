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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.lumareader.data.model.displayLabel
import com.example.lumareader.ui.utils.loadCoverImage
import com.example.lumareader.ui.utils.LumaSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Book.readingStatus(): String {
    val progress = if (spine.isNotEmpty()) {
        (currentSpineIndex.toFloat() + currentProgression) / spine.size.toFloat()
    } else 0f
    return when {
        progress >= 0.96f -> "FINISHED"
        progress > 0f -> "READING"
        else -> "UNREAD"
    }
}

fun Book.overallProgress(): Float {
    return if (spine.isNotEmpty()) {
        (currentSpineIndex.toFloat() + currentProgression) / spine.size.toFloat()
    } else 0f
}

@Composable
fun LumaProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "progressBar"
    )
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
    ) {
        val width = size.width
        val height = size.height
        val strokeWidth = height
        val radius = strokeWidth / 2f
        
        // 1. Draw track
        drawLine(
            color = trackColor,
            start = androidx.compose.ui.geometry.Offset(radius, radius),
            end = androidx.compose.ui.geometry.Offset(width - radius, radius),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        
        if (animatedProgress > 0f) {
            val progressWidth = radius + (width - 2 * radius) * animatedProgress
            
            // 2. Draw glow (wider, semi-translucent line)
            drawLine(
                color = accentColor.copy(alpha = 0.3f),
                start = androidx.compose.ui.geometry.Offset(radius, radius),
                end = androidx.compose.ui.geometry.Offset(progressWidth, radius),
                strokeWidth = strokeWidth * 1.6f,
                cap = StrokeCap.Round
            )
            
            // 3. Draw actual filled progress bar
            drawLine(
                color = accentColor,
                start = androidx.compose.ui.geometry.Offset(radius, radius),
                end = androidx.compose.ui.geometry.Offset(progressWidth, radius),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun FilterPill(
    selected: Boolean,
    label: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val transition = updateTransition(selected, label = "FilterPillTransition")
    
    val containerColor by transition.animateColor(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow) },
        label = "containerColor"
    ) { isSelected ->
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    }
    
    val contentColor by transition.animateColor(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow) },
        label = "contentColor"
    ) { isSelected ->
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    val scale by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow) },
        label = "scale"
    ) { isSelected ->
        if (isSelected) 1.04f else 1.0f
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier
            .scale(scale)
            .padding(vertical = 4.dp),
        tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$label ($count)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SortOptionItem(
    option: LibrarySortOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val icon = when (option) {
        LibrarySortOption.TITLE_ASC -> Icons.Default.SortByAlpha
        LibrarySortOption.TITLE_DESC -> Icons.Default.SortByAlpha
        LibrarySortOption.AUTHOR_ASC -> Icons.Default.Person
        LibrarySortOption.AUTHOR_DESC -> Icons.Default.Person
        LibrarySortOption.RECENT -> Icons.Default.Schedule
        LibrarySortOption.PROGRESS -> Icons.Default.TrendingUp
    }
    
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        if (option == LibrarySortOption.TITLE_DESC || option == LibrarySortOption.AUTHOR_DESC) {
                            scaleY = -1f
                        }
                    }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = option.displayLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    books: List<Book>,
    preferences: ReadingPreferences,
    onBookClick: (String) -> Unit,
    onImportBookClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSyncClick: () -> Unit,
    onPreferencesChanged: (ReadingPreferences) -> Unit,
    onDeleteBook: (String) -> Unit,
    onUpdateMetadata: (String, String?, String?, String?, Float?, String?) -> Unit,
    onAuthorClick: (String) -> Unit = {},
    onSeriesClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showSortBottomSheet by remember { mutableStateOf(false) }
    var showGridSlider by remember { mutableStateOf(false) }
    
    var showEditDialogForBook by remember { mutableStateOf<Book?>(null) }
    var showDeleteDialogForBook by remember { mutableStateOf<Book?>(null) }

    val libraryPrefs = preferences.libraryPrefs

    // 1. Filter by search query
    val searchFiltered = books.filter { book ->
        searchQuery.isBlank() ||
        book.title.contains(searchQuery, ignoreCase = true) ||
        book.author.contains(searchQuery, ignoreCase = true)
    }

    // 2. Filter by reading status chip
    val statusFiltered = searchFiltered.filter { book ->
        libraryPrefs.activeFilter == "ALL" || book.readingStatus() == libraryPrefs.activeFilter
    }

    // 3. Sort
    val sorted = when (libraryPrefs.sortOption) {
        LibrarySortOption.TITLE_ASC    -> statusFiltered.sortedBy { it.title.lowercase() }
        LibrarySortOption.TITLE_DESC   -> statusFiltered.sortedByDescending { it.title.lowercase() }
        LibrarySortOption.AUTHOR_ASC   -> statusFiltered.sortedBy { it.author.lowercase() }
        LibrarySortOption.AUTHOR_DESC  -> statusFiltered.sortedByDescending { it.author.lowercase() }
        LibrarySortOption.RECENT       -> statusFiltered.sortedByDescending { it.lastReadTimestamp }
        LibrarySortOption.PROGRESS     -> statusFiltered.sortedByDescending { it.overallProgress() }
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

    val isCollapsed by remember {
        derivedStateOf {
            val offset = if (libraryPrefs.viewMode == LibraryViewMode.GRID) {
                gridState.firstVisibleItemScrollOffset
            } else {
                listState.firstVisibleItemScrollOffset
            }
            val index = if (libraryPrefs.viewMode == LibraryViewMode.GRID) {
                gridState.firstVisibleItemIndex
            } else {
                listState.firstVisibleItemIndex
            }
            index > 0 || offset > 40
        }
    }

    Scaffold(
        floatingActionButton = {
            var fabVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                fabVisible = true
            }
            AnimatedVisibility(
                visible = fabVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = onImportBookClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    icon = { Icon(Icons.Default.ImportContacts, contentDescription = "Import book") },
                    text = { Text("Import ePub", fontWeight = FontWeight.SemiBold) },
                    expanded = isAtTop
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            val screenWidth = maxWidth
            val maxCols = when {
                screenWidth < 600.dp -> 4 // phone
                screenWidth < 900.dp -> 6 // foldable
                else -> 8 // tablet
            }
            val minCols = 2
            
            val columnsCount = remember(libraryPrefs.gridItemWidthDp, screenWidth) {
                (screenWidth.value / libraryPrefs.gridItemWidthDp.toFloat()).toInt().coerceIn(minCols, maxCols)
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Collapsing Top Section
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .fillMaxWidth()
                ) {
                    // Branding (collapses on scroll)
                    AnimatedVisibility(
                        visible = !isCollapsed && !isSearchActive,
                        enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
                        exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Luma Reader",
                                fontFamily = GoogleSans,
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Your personal reading sanctuary",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // SearchBar + Top Level Settings Actions Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = { isSearchActive = false },
                            active = isSearchActive,
                            onActiveChange = { isSearchActive = it },
                            placeholder = { Text("Search your library") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (isSearchActive) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        isSearchActive = false
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close search")
                                    }
                                }
                            },
                            colors = SearchBarDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            // inline search results
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(sorted, key = { it.id }) { book ->
                                    BookListRow(
                                        book = book,
                                        onClick = {
                                            isSearchActive = false
                                            onBookClick(book.id)
                                        },
                                        onEdit = { showEditDialogForBook = book; isSearchActive = false },
                                        onDelete = { showDeleteDialogForBook = book; isSearchActive = false },
                                        onAuthorClick = { onAuthorClick(book.author); isSearchActive = false },
                                        onSeriesClick = { book.series?.let { onSeriesClick(it) }; isSearchActive = false }
                                    )
                                }
                            }
                        }
                        
                        if (!isSearchActive) {
                            IconButton(
                                onClick = onSettingsClick,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Staggered filter row & controls (collapses if search is active)
                AnimatedVisibility(
                    visible = !isSearchActive,
                    enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
                    exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side: Filter pills
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val allCount = searchFiltered.size
                            val readingCount = searchFiltered.count { it.readingStatus() == "READING" }
                            val unreadCount = searchFiltered.count { it.readingStatus() == "UNREAD" }
                            val finishedCount = searchFiltered.count { it.readingStatus() == "FINISHED" }
                            
                            val filters = listOf(
                                Triple("ALL", "All", Icons.Default.LibraryBooks to allCount),
                                Triple("READING", "Reading", Icons.Default.MenuBook to readingCount),
                                Triple("UNREAD", "New", Icons.Default.FiberNew to unreadCount),
                                Triple("FINISHED", "Finished", Icons.Default.CheckCircle to finishedCount)
                            )
                            
                            filters.forEach { (filterKey, label, pair) ->
                                val (icon, count) = pair
                                Box(modifier = Modifier.weight(1f)) {
                                    FilterPill(
                                        selected = libraryPrefs.activeFilter == filterKey,
                                        label = label,
                                        count = count,
                                        icon = icon,
                                        onClick = {
                                            onPreferencesChanged(preferences.copy(libraryPrefs = libraryPrefs.copy(activeFilter = filterKey)))
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Right side: Layout and Sort actions grouped together
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (libraryPrefs.viewMode == LibraryViewMode.GRID) {
                                IconButton(
                                    onClick = { showGridSlider = !showGridSlider },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Resize grid",
                                        modifier = Modifier.size(20.dp),
                                        tint = if (showGridSlider) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    val newMode = if (libraryPrefs.viewMode == LibraryViewMode.GRID) LibraryViewMode.LIST else LibraryViewMode.GRID
                                    onPreferencesChanged(preferences.copy(libraryPrefs = libraryPrefs.copy(viewMode = newMode)))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (libraryPrefs.viewMode == LibraryViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = "Toggle view mode",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            val isNonDefaultSort = libraryPrefs.sortOption != LibrarySortOption.TITLE_ASC
                            Box(contentAlignment = Alignment.Center) {
                                IconButton(
                                    onClick = { showSortBottomSheet = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isNonDefaultSort) {
                                    Surface(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .align(Alignment.TopEnd)
                                            .padding(top = 2.dp, end = 2.dp),
                                        shape = RoundedCornerShape(3.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {}
                                }
                            }
                        }
                    }
                }

                // Grid size slider animation
                AnimatedVisibility(
                    visible = showGridSlider && libraryPrefs.viewMode == LibraryViewMode.GRID && !isSearchActive,
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
                        LumaSlider(
                            label = "Grid Columns",
                            value = columnsCount.toFloat(),
                            onValueChangeFinished = { newColsFloat ->
                                val newCols = newColsFloat.toInt().coerceIn(minCols, maxCols)
                                val newWidth = (screenWidth.value / newCols).toInt().coerceIn(80, 250)
                                onPreferencesChanged(preferences.copy(libraryPrefs = libraryPrefs.copy(gridItemWidthDp = newWidth)))
                            },
                            valueRange = minCols.toFloat()..maxCols.toFloat(),
                            steps = maxCols - minCols - 1,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(16.dp),
                            valueFormatter = { "${it.toInt()} Columns" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Main Content Area with AnimatedContent Crossfade
                if (books.isEmpty()) {
                    LibraryEmptyState(onImportBookClick)
                } else {
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
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
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
                                        onAuthorClick = { onAuthorClick(book.author) },
                                        onSeriesClick = { book.series?.let { onSeriesClick(it) } },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(sorted, key = { it.id }) { book ->
                                    BookListRow(
                                        book = book,
                                        onClick = { onBookClick(book.id) },
                                        onEdit = { showEditDialogForBook = book },
                                        onDelete = { showDeleteDialogForBook = book },
                                        onAuthorClick = { onAuthorClick(book.author) },
                                        onSeriesClick = { book.series?.let { onSeriesClick(it) } },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSortBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Sort Library By",
                    fontFamily = GoogleSans,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                LibrarySortOption.values().forEach { option ->
                    val isSelected = libraryPrefs.sortOption == option
                    SortOptionItem(
                        option = option,
                        selected = isSelected,
                        onClick = {
                            onPreferencesChanged(preferences.copy(libraryPrefs = libraryPrefs.copy(sortOption = option)))
                            showSortBottomSheet = false
                        }
                    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookGridCard(
    book: Book,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onAuthorClick: () -> Unit = {},
    onSeriesClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var coverImage by remember(book.id, book.coverPath, book.customCoverPath) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(book.id, book.coverPath, book.customCoverPath) {
        val path = book.customCoverPath ?: book.coverPath
        coverImage = if (path != null) {
            withContext(Dispatchers.IO) {
                if (book.customCoverPath != null) {
                    loadCoverImage(path)
                } else {
                    loadCoverImage("${book.unzippedDir}/$path")
                }
            }
        } else {
            null
        }
    }

    var showMenu by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "cardScale"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 4.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "cardElevation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                val currentCover = coverImage
                if (currentCover != null) {
                    Image(
                        bitmap = currentCover,
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = book.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif,
                                color = MaterialTheme.colorScheme.onPrimary,
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
                
                val progress = book.overallProgress()
                if (progress > 0f) {
                    LumaProgressBar(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .align(Alignment.BottomCenter)
                            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = book.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )
            Text(
                text = book.author,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable { onAuthorClick() }
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(text = { Text("Edit Details") }, onClick = { showMenu = false; onEdit() })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListRow(
    book: Book,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onAuthorClick: () -> Unit = {},
    onSeriesClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var coverImage by remember(book.id, book.coverPath, book.customCoverPath) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(book.id, book.coverPath, book.customCoverPath) {
        val path = book.customCoverPath ?: book.coverPath
        coverImage = if (path != null) {
            withContext(Dispatchers.IO) {
                if (book.customCoverPath != null) {
                    loadCoverImage(path)
                } else {
                    loadCoverImage("${book.unzippedDir}/$path")
                }
            }
        } else {
            null
        }
    }

    var showMenu by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "rowScale"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 2.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "rowElevation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                val currentCover = coverImage
                if (currentCover != null) {
                    Image(
                        bitmap = currentCover,
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onAuthorClick() }
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                val progress = book.overallProgress()
                LumaProgressBar(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(text = { Text("Edit Details") }, onClick = { showMenu = false; onEdit() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
            }
        }
    }
}

@Composable
fun LibraryEmptyState(
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = scaleIn(
                initialScale = 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(pulseScale)
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = pulseAlpha))
                )
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LibraryBooks,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        var showTitle by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(150)
            showTitle = true
        }
        AnimatedVisibility(
            visible = showTitle,
            enter = slideInVertically(
                initialOffsetY = { 40 },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow))
        ) {
            Text(
                text = "Welcome to Luma",
                fontFamily = GoogleSans,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        var showBody by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(300)
            showBody = true
        }
        AnimatedVisibility(
            visible = showBody,
            enter = slideInVertically(
                initialOffsetY = { 40 },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow))
        ) {
            Text(
                text = "Import your EPUB digital files to begin cultivating your beautiful personal reading sanctuary.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp),
                lineHeight = 20.sp
            )
        }
        
        Spacer(modifier = Modifier.height(28.dp))
        
        var showButton by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(450)
            showButton = true
        }
        AnimatedVisibility(
            visible = showButton,
            enter = slideInVertically(
                initialOffsetY = { 40 },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow))
        ) {
            Button(
                onClick = onImportClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("Select an ePub File", fontWeight = FontWeight.Bold)
            }
        }
    }
}
