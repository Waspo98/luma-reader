package com.example.lumareader.ui.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lumareader.data.model.Book
import com.example.lumareader.data.model.LumaThemeMode
import com.example.lumareader.data.model.ReadingPreferences
import com.example.lumareader.data.model.MarginLockMode
import com.example.lumareader.data.model.ImageHandlingMode
import com.example.lumareader.theme.GoogleSans
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.example.lumareader.ui.utils.BackHandler
import com.example.lumareader.ui.utils.LumaSlider

data class SpineItemInfo(
    val index: Int,
    val path: String,
    val title: String,
    val isRealChapter: Boolean,
    val realChapterNumber: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: Book,
    preferences: ReadingPreferences,
    onBackClick: () -> Unit,
    onPreferencesChanged: (ReadingPreferences) -> Unit,
    onProgressUpdated: (spineIndex: Int, progress: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val processedSpineItems = remember(book) {
        val nonChapterKeywords = listOf(
            "cover", "titlepage", "title page", "copyright", "dedication", "contents", 
            "table of contents", "acknowledgment", "acknowledgement", "acknowledgments",
            "about the author", "about the illustrator", "colophon", "author's note",
            "epigraph", "frontmatter", "backmatter"
        )
        
        var tempItems = book.spine.mapIndexed { idx, path ->
            val tocItem = book.toc.find { it.href == path || it.href.substringBefore("#") == path }
            val rawTitle = tocItem?.title ?: ""
            val titleLower = rawTitle.lowercase()
            val pathLower = path.lowercase()
            val filenameLower = path.substringAfterLast("/").substringBefore(".").lowercase()
            
            val isNonChapter = nonChapterKeywords.any { keyword ->
                titleLower.contains(keyword) || pathLower.contains(keyword) || filenameLower.contains(keyword)
            }
            
            val isReal = !isNonChapter
            
            val title = rawTitle.ifEmpty {
                when {
                    pathLower.contains("cover") -> "Cover"
                    pathLower.contains("title") -> "Title Page"
                    pathLower.contains("toc") || pathLower.contains("content") -> "Table of Contents"
                    pathLower.contains("copyright") -> "Copyright"
                    pathLower.contains("dedication") -> "Dedication"
                    pathLower.contains("acknowledg") -> "Acknowledgments"
                    else -> ""
                }
            }
            
            SpineItemInfo(idx, path, title, isReal)
        }
        
        val realCount = tempItems.count { it.isRealChapter }
        if (realCount == 0) {
            tempItems.mapIndexed { idx, item ->
                item.copy(
                    isRealChapter = true,
                    realChapterNumber = idx + 1,
                    title = item.title.ifEmpty { "Chapter ${idx + 1}" }
                )
            }
        } else {
            var counter = 0
            tempItems.map { item ->
                if (item.isRealChapter) {
                    counter++
                    item.copy(
                        realChapterNumber = counter,
                        title = item.title.ifEmpty { "Chapter $counter" }
                    )
                } else {
                    item.copy(
                        title = item.title.ifEmpty { "Section" }
                    )
                }
            }
        }
    }
    
    val totalRealChapters = remember(processedSpineItems) {
        processedSpineItems.count { it.isRealChapter }
    }
    
    // Back gesture closes Table of Contents drawer
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    
    var showFormatSheet by remember { mutableStateOf(false) }

    val accentColor = remember(preferences.accentColorHex) { preferences.accentColorHex.toComposeColor() }
    
    // Core state tracking what chapter we are in
    if (book.spine.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("This book has no readable content.", color = MaterialTheme.colorScheme.onSurface)
        }
        return
    }
    var currentSpineIndex by remember { mutableStateOf(book.currentSpineIndex.coerceIn(0, book.spine.size - 1)) }
    var activeHashAnchor by remember { mutableStateOf<String?>(null) }
    val chapterPath = book.spine[currentSpineIndex]
    
    // Save progression position
    var currentProgression by remember { mutableStateOf(book.currentProgression) }

    val pagerState = rememberPagerState(
        initialPage = currentSpineIndex,
        pageCount = { book.spine.size }
    )

    // Sync pagerState.currentPage to currentSpineIndex and save progress on complete page changes
    LaunchedEffect(pagerState.currentPage) {
        if (currentSpineIndex != pagerState.currentPage) {
            val movingForward = pagerState.currentPage > currentSpineIndex
            currentSpineIndex = pagerState.currentPage
            currentProgression = if (movingForward) 0f else 1.0f
            activeHashAnchor = null
            onProgressUpdated(pagerState.currentPage, currentProgression)
        }
    }

    // Sync currentSpineIndex updates from TOC or Progress Slider back to PagerState
    LaunchedEffect(currentSpineIndex) {
        if (pagerState.currentPage != currentSpineIndex) {
            pagerState.scrollToPage(currentSpineIndex)
        }
    }

    // Pages tracking for chapter remaining calculations
    var currentPageInChapter by remember(currentSpineIndex) { mutableStateOf(1) }
    var totalPagesInChapter by remember(currentSpineIndex) { mutableStateOf(1) }

    // Toggle overlay UI toolbars state
    var isUiVisible by remember { mutableStateOf(true) }
    var lastToggleTime by remember { mutableStateOf(0L) }

    // Screen-level immersive mode: hides/shows system bars based on UI visibility.
    // Placed here (NOT inside the pager page) so restore-on-dispose fires only when
    // the reader screen itself exits composition, not on every chapter swipe.
    ImmersiveModeEffect(
        isUiVisible = isUiVisible,
        extendBehindNotch = preferences.extendBehindNotch
    )

    // Haptics and scrubbing tracking
    val haptic = LocalHapticFeedback.current
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableStateOf(0f) }

    // Position reset states
    var initialMenuSpineIndex by remember { mutableStateOf<Int?>(null) }
    var initialMenuProgression by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(isUiVisible) {
        if (isUiVisible) {
            initialMenuSpineIndex = currentSpineIndex
            initialMenuProgression = currentProgression
        } else {
            initialMenuSpineIndex = null
            initialMenuProgression = null
        }
    }

    val entireBookProgress = remember(currentSpineIndex, currentProgression, book.spine.size) {
        (currentSpineIndex.toFloat() + currentProgression) / book.spine.size.toFloat()
    }

    val displayProgress = if (isScrubbing) scrubProgress else entireBookProgress
    val displaySpineIndex = remember(displayProgress, book.spine.size) {
        (displayProgress * book.spine.size).toInt().coerceIn(0, book.spine.size - 1)
    }
    val displayProgression = remember(displayProgress, book.spine.size, displaySpineIndex) {
        ((displayProgress * book.spine.size) - displaySpineIndex).coerceIn(0f, 0.99f)
    }
    val displayBookPercentage = (displayProgress * 100).toInt()

    // Resolve current chapter title for top bar in real-time
    val currentChapterTitle = remember(displaySpineIndex, processedSpineItems) {
        processedSpineItems[displaySpineIndex].title
    }
    
    val isSystemDark = isSystemInDarkTheme()
    val resolvedBgColor = remember(preferences.themeMode, isSystemDark) {
        when (preferences.themeMode) {
            LumaThemeMode.LIGHT -> Color(0xFFFFFFFF)
            LumaThemeMode.SLATE_GRAY -> Color(0xFF1C2025)
            LumaThemeMode.AMOLED_BLACK -> Color(0xFF000000)
            LumaThemeMode.SYSTEM -> if (isSystemDark) Color(0xFF1C2025) else Color(0xFFFFFFFF)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Table of Contents",
                    fontSize = 20.sp,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = accentColor
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    itemsIndexed(book.toc) { index, tocItem ->
                        val isCurrent = book.spine.indexOf(tocItem.href.substringBefore("#")) == currentSpineIndex || tocItem.href.substringBefore("#") == chapterPath
                        NavigationDrawerItem(
                            label = { 
                                Text(
                                    text = tocItem.title, 
                                    fontFamily = GoogleSans,
                                    maxLines = 1, 
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            selected = isCurrent,
                            onClick = {
                                val spinePos = book.spine.indexOfFirst { it == tocItem.href.substringBefore("#") || tocItem.href.startsWith(it) }
                                if (spinePos >= 0) {
                                    val hash = if (tocItem.href.contains("#")) tocItem.href.substringAfter("#") else null
                                    activeHashAnchor = hash
                                    currentSpineIndex = spinePos
                                    currentProgression = 0f
                                    onProgressUpdated(spinePos, 0f)
                                }
                                scope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = accentColor.copy(alpha = 0.15f),
                                selectedTextColor = accentColor,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(resolvedBgColor)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = false
            ) { pageIndex ->
                val targetChapterPath = book.spine[pageIndex]
                ReaderWebView(
                    book = book,
                    chapterPath = targetChapterPath,
                    preferences = preferences,
                    initialProgression = if (pageIndex == currentSpineIndex) currentProgression else {
                        if (pageIndex > currentSpineIndex) 0f else 1.0f
                    },
                    isUiVisible = isUiVisible,
                    onProgressChanged = { newProgress ->
                        if (pageIndex == currentSpineIndex) {
                            currentProgression = newProgress
                            onProgressUpdated(pageIndex, newProgress)
                        }
                    },
                    onPageInfoChanged = { currentPage, totalPages ->
                        if (pageIndex == currentSpineIndex) {
                            currentPageInChapter = currentPage
                            totalPagesInChapter = totalPages
                        }
                    },
                    onNextChapter = {
                        if (currentSpineIndex < book.spine.size - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(currentSpineIndex + 1)
                            }
                        }
                    },
                    onPrevChapter = {
                        if (currentSpineIndex > 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(currentSpineIndex - 1)
                            }
                        }
                    },
                    onToggleUI = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastToggleTime > 500L) {
                            isUiVisible = !isUiVisible
                            lastToggleTime = currentTime
                        }
                    },
                    onNavigateToChapter = { spineIndex, hash ->
                        activeHashAnchor = hash
                        currentSpineIndex = spineIndex
                        currentProgression = 0f
                        onProgressUpdated(spineIndex, 0f)
                    },
                    targetHash = if (pageIndex == currentSpineIndex) activeHashAnchor else null,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Touch shield overlay to block book interaction and close menus on tap
            if (isUiVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    isUiVisible = false
                                }
                            )
                        }
                )
            }

            // Absolute top overlay Top Bar
            AnimatedVisibility(
                visible = isUiVisible,
                enter = slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) { -it } + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = slideOutVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) { -it } + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentChapterTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = GoogleSans,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showFormatSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.FormatSize,
                                    contentDescription = "Format settings",
                                    tint = accentColor
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }

            // Absolute bottom overlay Bottom Bar
            AnimatedVisibility(
                visible = isUiVisible,
                enter = slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) { it } + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = slideOutVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) { it } + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 8.dp)
                    ) {
                        // Slider progress bar for entire book traversal
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentSpineIndex > 0) {
                                        currentSpineIndex--
                                        currentProgression = 0f
                                        onProgressUpdated(currentSpineIndex, 0f)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                                enabled = currentSpineIndex > 0
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Prev chapter",
                                    tint = if (currentSpineIndex > 0) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                            
                            var sliderProgress by remember(displayProgress) { mutableStateOf(displayProgress) }
                            
                            Slider(
                                value = sliderProgress,
                                onValueChange = { rawValue ->
                                    isScrubbing = true
                                    val magnetized = magnetizeProgress(rawValue, book.spine.size)
                                    if (magnetized != scrubProgress) {
                                        val oldMagnetizedSpine = (scrubProgress * book.spine.size).toInt().coerceIn(0, book.spine.size - 1)
                                        val newMagnetizedSpine = (magnetized * book.spine.size).toInt().coerceIn(0, book.spine.size - 1)
                                        
                                        // Trigger haptics on chapter boundary snap ticks
                                        if (newMagnetizedSpine != oldMagnetizedSpine) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        
                                        scrubProgress = magnetized
                                        sliderProgress = magnetized
                                    }
                                },
                                onValueChangeFinished = {
                                    isScrubbing = false
                                    val totalSpinePosition = sliderProgress * book.spine.size
                                    val newSpineIndex = totalSpinePosition.toInt().coerceIn(0, book.spine.size - 1)
                                    val newProgression = (totalSpinePosition - newSpineIndex).coerceIn(0f, 0.99f)
                                    currentSpineIndex = newSpineIndex
                                    currentProgression = newProgression
                                    onProgressUpdated(newSpineIndex, newProgression)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor,
                                    inactiveTrackColor = accentColor.copy(alpha = 0.24f)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            
                            IconButton(
                                onClick = {
                                    if (currentSpineIndex < book.spine.size - 1) {
                                        currentSpineIndex++
                                        currentProgression = 0f
                                        onProgressUpdated(currentSpineIndex, 0f)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                                enabled = currentSpineIndex < book.spine.size - 1
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next chapter",
                                    tint = if (currentSpineIndex < book.spine.size - 1) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                        }

                        // Table of Contents & details row
                        val pagesRemaining = (totalPagesInChapter - currentPageInChapter).coerceAtLeast(0)
                        val activeProcessedItem = processedSpineItems[displaySpineIndex]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val progressText = if (activeProcessedItem.isRealChapter) {
                                    "Book Progress: $displayBookPercentage% • Chapter ${activeProcessedItem.realChapterNumber} of $totalRealChapters"
                                } else {
                                    "Book Progress: $displayBookPercentage% • ${activeProcessedItem.title}"
                                }
                                Text(
                                    text = progressText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isScrubbing) {
                                    Text(
                                        text = "Release to jump to position",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = GoogleSans,
                                        color = accentColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    val pageText = if (totalPagesInChapter <= 1) {
                                        "Page $currentPageInChapter of $totalPagesInChapter"
                                    } else {
                                        "Page $currentPageInChapter of $totalPagesInChapter ($pagesRemaining page${if (pagesRemaining == 1) "" else "s"} left)"
                                    }
                                    Text(
                                        text = pageText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = GoogleSans,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = "Table of contents",
                                    tint = accentColor
                                )
                            }
                        }
                    }
                }
            }

            // Floating Capsule Reset Position button
            val hasMoved = initialMenuSpineIndex != null && 
                           (currentSpineIndex != initialMenuSpineIndex || 
                            kotlin.math.abs(currentProgression - (initialMenuProgression ?: 0f)) > 0.01f)
            
            AnimatedVisibility(
                visible = isUiVisible && hasMoved,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) { it / 2 },
                exit = fadeOut() + slideOutVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 148.dp) // Float elegantly above the bottom sheet / bar
            ) {
                Button(
                    onClick = {
                        val originalSpine = initialMenuSpineIndex
                        val originalProg = initialMenuProgression
                        if (originalSpine != null && originalProg != null) {
                            lastToggleTime = System.currentTimeMillis() // Capture time to block touch bleed-through
                            currentSpineIndex = originalSpine
                            currentProgression = originalProg
                            onProgressUpdated(originalSpine, originalProg)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "Reset position",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reset to original page",
                        fontFamily = GoogleSans,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    
    // Bottom Sheet for text formatting controls
    if (showFormatSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFormatSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Theming",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Theme Picker Row
                Text("Theme", fontFamily = GoogleSans, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bright Light
                    ThemeOptionButton(
                        label = "Bright",
                        bgColor = Color(0xFFFFFFFF),
                        textColor = Color(0xFF000000),
                        isSelected = preferences.themeMode == LumaThemeMode.LIGHT,
                        onClick = { onPreferencesChanged(preferences.copy(themeMode = LumaThemeMode.LIGHT)) },
                        modifier = Modifier.weight(1f)
                    )
                    // Slate Gray
                    ThemeOptionButton(
                        label = "Slate",
                        bgColor = Color(0xFF1C2025),
                        textColor = Color(0xFFE2E8F0),
                        isSelected = preferences.themeMode == LumaThemeMode.SLATE_GRAY,
                        onClick = { onPreferencesChanged(preferences.copy(themeMode = LumaThemeMode.SLATE_GRAY)) },
                        modifier = Modifier.weight(1f)
                    )
                    // AMOLED Black
                    ThemeOptionButton(
                        label = "Amoled",
                        bgColor = Color(0xFF000000),
                        textColor = Color(0xFFF3F4F6),
                        isSelected = preferences.themeMode == LumaThemeMode.AMOLED_BLACK,
                        onClick = { onPreferencesChanged(preferences.copy(themeMode = LumaThemeMode.AMOLED_BLACK)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Font Family Row
                Text("Font Style", fontFamily = GoogleSans, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FontOptionButton(
                        label = "Literata",
                        fontFamily = "Literata",
                        isSelected = preferences.fontFamily == "Literata",
                        onClick = { onPreferencesChanged(preferences.copy(fontFamily = "Literata")) },
                        modifier = Modifier.weight(1f)
                    )
                    FontOptionButton(
                        label = "Inter",
                        fontFamily = "Inter",
                        isSelected = preferences.fontFamily == "Inter",
                        onClick = { onPreferencesChanged(preferences.copy(fontFamily = "Inter")) },
                        modifier = Modifier.weight(1f)
                    )
                    FontOptionButton(
                        label = "System Serif",
                        fontFamily = "serif",
                        isSelected = preferences.fontFamily == "serif",
                        onClick = { onPreferencesChanged(preferences.copy(fontFamily = "serif")) },
                        modifier = Modifier.weight(1.2f)
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))

                // Font Size Slider
                LumaSlider(
                    label = "Font Size",
                    value = preferences.fontSizeSp,
                    onValueChangeFinished = { newVal ->
                        onPreferencesChanged(preferences.copy(fontSizeSp = newVal))
                    },
                    valueRange = 10f..30f,
                    steps = 19,
                    accentColor = accentColor,
                    valueFormatter = { "${it.toInt()} sp" }
                )

                // Line Spacing Slider
                Spacer(modifier = Modifier.height(12.dp))
                LumaSlider(
                    label = "Line Spacing",
                    value = preferences.lineSpacing,
                    onValueChangeFinished = { newVal ->
                        onPreferencesChanged(preferences.copy(lineSpacing = newVal))
                    },
                    valueRange = 1.0f..2.0f,
                    steps = 9,
                    accentColor = accentColor,
                    valueFormatter = { "${(it * 10).toInt() / 10.0}x" }
                )

                // Margins Lock Mode & Granular Margins Sliders
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Margin Lock Mode",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        MarginLockMode.LOCK_ALL to "Lock All",
                        MarginLockMode.LOCK_VH to "Lock H/V",
                        MarginLockMode.UNLOCKED to "Unlocked"
                    ).forEach { (mode, label) ->
                        val isSelected = preferences.marginLockMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    val newPrefs = when (mode) {
                                        MarginLockMode.LOCK_ALL -> {
                                            val newVal = preferences.marginTopDp
                                            preferences.copy(
                                                marginLockMode = MarginLockMode.LOCK_ALL,
                                                marginTopDp = newVal,
                                                marginBottomDp = newVal,
                                                marginLeftDp = newVal,
                                                marginRightDp = newVal,
                                                marginDp = newVal
                                            )
                                        }
                                        MarginLockMode.LOCK_VH -> {
                                            preferences.copy(
                                                marginLockMode = MarginLockMode.LOCK_VH,
                                                marginTopDp = preferences.marginTopDp,
                                                marginBottomDp = preferences.marginTopDp,
                                                marginLeftDp = preferences.marginLeftDp,
                                                marginRightDp = preferences.marginLeftDp
                                            )
                                        }
                                        MarginLockMode.UNLOCKED -> {
                                            preferences.copy(marginLockMode = MarginLockMode.UNLOCKED)
                                        }
                                    }
                                    onPreferencesChanged(newPrefs)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontFamily = GoogleSans,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                when (preferences.marginLockMode) {
                    MarginLockMode.LOCK_ALL -> {
                        LumaSlider(
                            label = "All Margins",
                            value = preferences.marginTopDp.toFloat(),
                            onValueChangeFinished = { newVal ->
                                val newValInt = newVal.toInt()
                                onPreferencesChanged(
                                    preferences.copy(
                                        marginTopDp = newValInt,
                                        marginBottomDp = newValInt,
                                        marginLeftDp = newValInt,
                                        marginRightDp = newValInt,
                                        marginDp = newValInt
                                    )
                                )
                            },
                            valueRange = 8f..48f,
                            steps = 19,
                            accentColor = accentColor,
                            valueFormatter = { "${it.toInt()} dp" }
                        )
                    }
                    MarginLockMode.LOCK_VH -> {
                        LumaSlider(
                            label = "Vertical Margins (Top/Bottom)",
                            value = preferences.marginTopDp.toFloat(),
                            onValueChangeFinished = { newVal ->
                                onPreferencesChanged(
                                    preferences.copy(
                                        marginTopDp = newVal.toInt(),
                                        marginBottomDp = newVal.toInt()
                                    )
                                )
                            },
                            valueRange = 8f..48f,
                            steps = 19,
                            accentColor = accentColor,
                            valueFormatter = { "${it.toInt()} dp" }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LumaSlider(
                            label = "Horizontal Margins (Left/Right)",
                            value = preferences.marginLeftDp.toFloat(),
                            onValueChangeFinished = { newVal ->
                                onPreferencesChanged(
                                    preferences.copy(
                                        marginLeftDp = newVal.toInt(),
                                        marginRightDp = newVal.toInt()
                                    )
                                )
                            },
                            valueRange = 8f..48f,
                            steps = 19,
                            accentColor = accentColor,
                            valueFormatter = { "${it.toInt()} dp" }
                        )
                    }
                    MarginLockMode.UNLOCKED -> {
                        LumaSlider(
                            label = "Top Margin",
                            value = preferences.marginTopDp.toFloat(),
                            onValueChangeFinished = { newVal ->
                                onPreferencesChanged(preferences.copy(marginTopDp = newVal.toInt()))
                            },
                            valueRange = 8f..48f,
                            steps = 19,
                            accentColor = accentColor,
                            valueFormatter = { "${it.toInt()} dp" }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LumaSlider(
                            label = "Bottom Margin",
                            value = preferences.marginBottomDp.toFloat(),
                            onValueChangeFinished = { newVal ->
                                onPreferencesChanged(preferences.copy(marginBottomDp = newVal.toInt()))
                            },
                            valueRange = 8f..48f,
                            steps = 19,
                            accentColor = accentColor,
                            valueFormatter = { "${it.toInt()} dp" }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LumaSlider(
                            label = "Left Margin",
                            value = preferences.marginLeftDp.toFloat(),
                            onValueChangeFinished = { newVal ->
                                onPreferencesChanged(preferences.copy(marginLeftDp = newVal.toInt()))
                            },
                            valueRange = 8f..48f,
                            steps = 19,
                            accentColor = accentColor,
                            valueFormatter = { "${it.toInt()} dp" }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LumaSlider(
                            label = "Right Margin",
                            value = preferences.marginRightDp.toFloat(),
                            onValueChangeFinished = { newVal ->
                                onPreferencesChanged(preferences.copy(marginRightDp = newVal.toInt()))
                            },
                            valueRange = 8f..48f,
                            steps = 19,
                            accentColor = accentColor,
                            valueFormatter = { "${it.toInt()} dp" }
                        )
                    }
                }

                // Layout Columns Mode Section
                Spacer(modifier = Modifier.height(20.dp))
                Text("Layout Columns", fontFamily = GoogleSans, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeOptionButton(
                        label = "Single Column",
                        bgColor = if (preferences.twoColumnLocked) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        textColor = if (preferences.twoColumnLocked) accentColor else MaterialTheme.colorScheme.onSurface,
                        isSelected = preferences.twoColumnLocked,
                        onClick = { onPreferencesChanged(preferences.copy(twoColumnLocked = true)) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionButton(
                        label = "Double Column",
                        bgColor = if (!preferences.twoColumnLocked) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        textColor = if (!preferences.twoColumnLocked) accentColor else MaterialTheme.colorScheme.onSurface,
                        isSelected = !preferences.twoColumnLocked,
                        onClick = { onPreferencesChanged(preferences.copy(twoColumnLocked = false)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Image Treatment Section
                Spacer(modifier = Modifier.height(20.dp))
                Text("Image Treatment", fontFamily = GoogleSans, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        ImageHandlingMode.ORIGINAL to "Original",
                        ImageHandlingMode.INVERT_BW to "Invert B/W",
                        ImageHandlingMode.INVERT_ALL to "Invert All"
                    ).forEach { (mode, label) ->
                        val isSelected = preferences.imageHandlingMode == mode
                        ThemeOptionButton(
                            label = label,
                            bgColor = if (isSelected) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            textColor = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                            isSelected = isSelected,
                            onClick = { onPreferencesChanged(preferences.copy(imageHandlingMode = mode)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Accent Colors Section
                Spacer(modifier = Modifier.height(20.dp))
                Text("Accent Color", fontFamily = GoogleSans, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Default 1: Terracotta
                    ColorDefaultCircle(
                        color = Color(0xFFD45D42),
                        isSelected = preferences.accentColorHex.equals("#D45D42", ignoreCase = true),
                        onClick = { onPreferencesChanged(preferences.copy(accentColorHex = "#D45D42")) }
                    )
                    // Default 2: Indigo
                    ColorDefaultCircle(
                        color = Color(0xFF6366F1),
                        isSelected = preferences.accentColorHex.equals("#6366F1", ignoreCase = true),
                        onClick = { onPreferencesChanged(preferences.copy(accentColorHex = "#6366F1")) }
                    )
                    // Default 3: Emerald
                    ColorDefaultCircle(
                        color = Color(0xFF0F766E),
                        isSelected = preferences.accentColorHex.equals("#0F766E", ignoreCase = true),
                        onClick = { onPreferencesChanged(preferences.copy(accentColorHex = "#0F766E")) }
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Horizontal Custom Color spectrum bar
                    HueSpectrumPicker(
                        selectedColorHex = preferences.accentColorHex,
                        onColorSelected = { hex ->
                            onPreferencesChanged(preferences.copy(accentColorHex = hex))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Notched display support toggle
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Extend behind notch",
                            fontFamily = GoogleSans,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Allow reading content to utilize the entire immersive display area under the camera cutout.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.extendBehindNotch,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(extendBehindNotch = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accentColor,
                            checkedTrackColor = accentColor.copy(alpha = 0.5f)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Drop Cap Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Drop Caps",
                            fontFamily = GoogleSans,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Display an elegant drop letter at the start of each chapter.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.dropCapEnabled,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(dropCapEnabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accentColor,
                            checkedTrackColor = accentColor.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeOptionButton(
    label: String,
    bgColor: Color,
    textColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun FontOptionButton(
    label: String,
    fontFamily: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = when (fontFamily) {
                "Literata" -> FontFamily.Serif
                "Inter" -> FontFamily.SansSerif
                "serif" -> FontFamily.Serif
                "Google Sans" -> GoogleSans
                else -> FontFamily.Default
            },
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
    }
}

fun String.toComposeColor(): Color {
    return try {
        val hex = this.removePrefix("#")
        val parsed = hex.toLong(16)
        if (hex.length == 6) {
            Color(0xFF000000 or parsed)
        } else {
            Color(parsed)
        }
    } catch (_: Exception) {
        com.example.lumareader.theme.LightPrimary // Fallback to default terracotta
    }
}

@Composable
fun ColorDefaultCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

@Composable
fun HueSpectrumPicker(
    selectedColorHex: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spectrumColors = remember {
        listOf(
            Color(0xFFFF0000), // Red
            Color(0xFFFFFF00), // Yellow
            Color(0xFF00FF00), // Green
            Color(0xFF00FFFF), // Cyan
            Color(0xFF0000FF), // Blue
            Color(0xFFFF00FF), // Magenta
            Color(0xFFFF0000)  // Red
        )
    }

    val parsedSelectedColor = remember(selectedColorHex) { selectedColorHex.toComposeColor() }

    val outlineColor = androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .pointerInput(Unit) {
                    fun updateColor(xOffset: Float) {
                        val fraction = (xOffset / size.width).coerceIn(0f, 1f)
                        val hue = fraction * 360f
                        val rgbColor = hueToColor(hue)
                        onColorSelected(rgbColor.toHex())
                    }
                    detectTapGestures { offset ->
                        updateColor(offset.x)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        val hue = fraction * 360f
                        val rgbColor = hueToColor(hue)
                        onColorSelected(rgbColor.toHex())
                    }
                }
        ) {
            drawRect(
                brush = Brush.horizontalGradient(spectrumColors),
                size = size
            )

            drawRect(
                color = outlineColor,
                size = size,
                style = Stroke(width = 2f)
            )

            val hue = parsedSelectedColor.toHue()
            val thumbX = (hue / 360f) * size.width

            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(thumbX, size.height / 2),
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = parsedSelectedColor,
                radius = 6.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(thumbX, size.height / 2)
            )
        }
    }
}

fun hueToColor(hue: Float): Color {
    val h = hue / 60.0f
    val x = 1.0f - kotlin.math.abs((h % 2.0f) - 1.0f)
    val r: Float
    val g: Float
    val b: Float
    when {
        h < 1.0f -> { r = 1.0f; g = x; b = 0.0f }
        h < 2.0f -> { r = x; g = 1.0f; b = 0.0f }
        h < 3.0f -> { r = 0.0f; g = 1.0f; b = x }
        h < 4.0f -> { r = 0.0f; g = x; b = 1.0f }
        h < 5.0f -> { r = x; g = 0.0f; b = 1.0f }
        else -> { r = 1.0f; g = 0.0f; b = x }
    }
    return Color(r, g, b)
}

fun Color.toHex(): String {
    val r = (this.red * 255).toInt().coerceIn(0, 255)
    val g = (this.green * 255).toInt().coerceIn(0, 255)
    val b = (this.blue * 255).toInt().coerceIn(0, 255)
    return "#" + r.toHexString() + g.toHexString() + b.toHexString()
}

private fun Int.toHexString(): String {
    val s = this.toString(16).uppercase()
    return if (s.length == 1) "0$s" else s
}

fun Color.toHue(): Float {
    val r = this.red
    val g = this.green
    val b = this.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    if (delta < 0.001f) return 0f
    
    var h = when (max) {
        r -> (g - b) / delta + (if (g < b) 6f else 0f)
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    }
    h *= 60f
    return h
}

// Deleted ReaderMarginSliderItem component

private fun magnetizeProgress(progress: Float, spineSize: Int): Float {
    if (spineSize <= 1) return progress
    val exactPosition = progress * spineSize
    val spineIndex = exactPosition.toInt().coerceIn(0, spineSize - 1)
    val fraction = exactPosition - spineIndex
    
    val snapThreshold = 0.08f 
    return when {
        fraction < snapThreshold -> {
            spineIndex.toFloat() / spineSize.toFloat()
        }
        fraction > (1f - snapThreshold) -> {
            (spineIndex + 1).toFloat() / spineSize.toFloat()
        }
        else -> progress
    }
}
