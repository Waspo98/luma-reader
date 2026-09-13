package com.example.lumareader.ui.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import com.example.lumareader.data.model.BookAnnotation
import com.example.lumareader.data.model.LumaThemeMode
import com.example.lumareader.data.model.ReadingPreferences
import com.example.lumareader.data.model.PageNavigationStyle
import com.example.lumareader.data.model.MarginLockMode
import com.example.lumareader.data.model.ImageHandlingMode
import com.example.lumareader.data.model.ColumnLayoutMode
import com.example.lumareader.data.model.BottomBarDisplayMode
import com.example.lumareader.data.model.TextJustification
import com.example.lumareader.theme.GoogleSans
import com.example.lumareader.theme.LiterataFont
import com.example.lumareader.theme.InterFont
import com.example.lumareader.theme.LightBackground
import com.example.lumareader.theme.SepiaBackground
import com.example.lumareader.theme.SlateBackground
import com.example.lumareader.theme.AmoledBackground
import com.example.lumareader.theme.toComposeColor
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.example.lumareader.ui.utils.LumaHapticFeedbackType
import com.example.lumareader.ui.utils.rememberLumaHaptics
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.example.lumareader.ui.utils.BackHandler
import com.example.lumareader.ui.utils.LumaSlider
import com.example.lumareader.ui.components.*
import com.example.lumareader.ui.reader.components.ReaderFormatBottomSheet

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
    onProgressUpdated: (spineIndex: Int, progress: Float, locatorJson: String?) -> Unit,
    onAddAnnotation: (BookAnnotation) -> Unit = {},
    onDeleteAnnotation: (String) -> Unit = {},
    onUpdateAnnotation: (BookAnnotation) -> Unit = {},
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

    var livePreferences by remember(preferences) { mutableStateOf(preferences) }
    LaunchedEffect(preferences) { livePreferences = preferences }

    val accentColor = remember(livePreferences.accentColorHex) { livePreferences.accentColorHex.toComposeColor() }
    
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

    val readerController = remember { ReadiumReaderController() }

    // Pages tracking for chapter remaining calculations
    var currentBookPage by remember(currentSpineIndex) { mutableStateOf(1) }
    var totalBookPages by remember(currentSpineIndex) { mutableStateOf(1) }
    var chapterPagesLeft by remember(currentSpineIndex) { mutableStateOf(0) }

    // Toggle overlay UI toolbars state
    var isUiVisible by remember { mutableStateOf(true) }
    readerController.isUiVisible = isUiVisible
    var lastToggleTime by remember { mutableStateOf(0L) }

    // Screen-level immersive mode: hides/shows system bars based on UI visibility.
    // Placed here (NOT inside the pager page) so restore-on-dispose fires only when
    // the reader screen itself exits composition, not on every chapter swipe.
    ImmersiveModeEffect(
        isUiVisible = isUiVisible,
        extendBehindNotch = livePreferences.extendBehindNotch,
        keepScreenOn = livePreferences.keepScreenOn,
        immersiveMode = livePreferences.immersiveMode
    )

    // Volume button page turning - active ONLY when reader is full screen (UI toolbars hidden)
    DisposableEffect(isUiVisible, livePreferences.volumeKeyNavigation) {
        if (!isUiVisible && livePreferences.volumeKeyNavigation) {
            VolumeKeyNavigationManager.onVolumeKey = { isNext ->
                if (isNext) {
                    readerController.goForward()
                } else {
                    readerController.goBackward()
                }
                true
            }
        } else {
            VolumeKeyNavigationManager.onVolumeKey = null
        }
        onDispose {
            VolumeKeyNavigationManager.onVolumeKey = null
        }
    }

    // Haptics and scrubbing tracking
    val haptic = rememberLumaHaptics(livePreferences.hapticsEnabled)
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableStateOf(0f) }

    // Pull-down to dismiss gesture state
    val pullDownOffset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val dismissThresholdPx = remember(density) { with(density) { 120.dp.toPx() } }

    // Position reset states
    var initialMenuSpineIndex by remember { mutableStateOf<Int?>(null) }
    var initialMenuProgression by remember { mutableStateOf<Float?>(null) }
    var initialMenuLocatorJson by remember { mutableStateOf<String?>(null) }
    var currentLocatorJson by remember { mutableStateOf<String?>(book.lastLocatorJson) }
    var bottomBarHeightDp by remember { mutableStateOf(0.dp) }

    LaunchedEffect(isUiVisible) {
        if (isUiVisible) {
            initialMenuSpineIndex = currentSpineIndex
            initialMenuProgression = currentProgression
            initialMenuLocatorJson = currentLocatorJson
        } else {
            initialMenuSpineIndex = null
            initialMenuProgression = null
            initialMenuLocatorJson = null
        }
    }

    val entireBookProgress = currentProgression.coerceIn(0f, 1f)

    val displayProgress = if (isScrubbing) scrubProgress else entireBookProgress
    val displaySpineIndex = if (isScrubbing) {
        (displayProgress * book.spine.size).toInt().coerceIn(0, book.spine.size - 1)
    } else {
        currentSpineIndex
    }
    val displayBookPercentage = (displayProgress * 100).toInt()

    // Resolve current chapter title for top bar in real-time
    val currentChapterTitle = remember(displaySpineIndex, processedSpineItems) {
        processedSpineItems[displaySpineIndex].title
    }
    
    val isSystemDark = isSystemInDarkTheme()
    val resolvedBgColor = remember(livePreferences.themeMode, livePreferences.dayThemeMode, livePreferences.nightThemeMode, isSystemDark) {
        val effectiveTheme = when (livePreferences.themeMode) {
            LumaThemeMode.SYSTEM -> if (isSystemDark) livePreferences.nightThemeMode else livePreferences.dayThemeMode
            else -> livePreferences.themeMode
        }
        when (effectiveTheme) {
            LumaThemeMode.LIGHT -> LightBackground
            LumaThemeMode.WARM_SEPIA -> SepiaBackground
            LumaThemeMode.SLATE_GRAY -> SlateBackground
            LumaThemeMode.AMOLED_BLACK -> AmoledBackground
            LumaThemeMode.SYSTEM -> if (isSystemDark) SlateBackground else LightBackground
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
                                    readerController.goToChapter(spinePos, tocItem.href)
                                    onProgressUpdated(spinePos, 0f, null)
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, pullDownOffset.value.roundToInt()) }
                .background(resolvedBgColor)
        ) {
            val isNarrowWindow = maxWidth < 380.dp
            val isVeryNarrowWindow = maxWidth < 300.dp

            ReadiumEpubReader(
                book = book,
                preferences = livePreferences,
                controller = readerController,
                onProgressChanged = { chapterProg, spineIdx, _, locatorJson ->
                    currentProgression = chapterProg
                    currentSpineIndex = spineIdx
                    currentLocatorJson = locatorJson
                    onProgressUpdated(spineIdx, chapterProg, locatorJson)
                },
                onPageInfoChanged = { curPage, totalPages, pagesLeftInChapter ->
                    currentBookPage = curPage
                    totalBookPages = totalPages
                    chapterPagesLeft = pagesLeftInChapter
                },
                onToggleUI = {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastToggleTime > 300L) {
                        isUiVisible = !isUiVisible
                        lastToggleTime = currentTime
                    }
                },
                onAddAnnotation = onAddAnnotation,
                onDeleteAnnotation = onDeleteAnnotation,
                onUpdateAnnotation = onUpdateAnnotation,
                modifier = Modifier.fillMaxSize()
            )

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

            // Absolute top overlay Top Bar with pull-down to dismiss gesture
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
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = {},
                                onVerticalDrag = { change, dragAmount ->
                                    if (dragAmount > 0f || pullDownOffset.value > 0f) {
                                        change.consume()
                                        scope.launch {
                                            val newOffset = (pullDownOffset.value + dragAmount * 0.75f).coerceAtLeast(0f)
                                            pullDownOffset.snapTo(newOffset)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (pullDownOffset.value > dismissThresholdPx) {
                                        scope.launch {
                                            haptic.perform(LumaHapticFeedbackType.PAGE_TURN)
                                            pullDownOffset.animateTo(
                                                targetValue = dismissThresholdPx * 3.5f,
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                                            )
                                            onBackClick()
                                        }
                                    } else {
                                        scope.launch {
                                            pullDownOffset.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        pullDownOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                                        )
                                    }
                                }
                            )
                        }
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = book.title,
                                        style = if (isNarrowWindow) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
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
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                IconButton(onClick = { showFormatSheet = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Reader options",
                                        tint = accentColor
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent
                            )
                        )
                        // Pill drag handle indicating swipe down to dismiss
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                            )
                        }
                    }
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
                    .onGloballyPositioned { coordinates ->
                        val h = with(density) { coordinates.size.height.toDp() }
                        if (h > 0.dp) {
                            bottomBarHeightDp = h
                        }
                    }
            ) {
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            var totalDragY = 0f
                            detectVerticalDragGestures(
                                onDragStart = { totalDragY = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    totalDragY += dragAmount
                                    if (totalDragY < -25f) {
                                        change.consume()
                                        if (!showFormatSheet) {
                                            showFormatSheet = true
                                            haptic.perform(LumaHapticFeedbackType.TAP)
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 8.dp)
                    ) {
                        // Drag handle visual cue & quick open tap target
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 2.dp)
                                .clickable {
                                    showFormatSheet = true
                                    haptic.perform(LumaHapticFeedbackType.TAP)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                        shape = CircleShape
                                    )
                            )
                        }
                        // Slider progress bar for entire book traversal
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (isNarrowWindow) 8.dp else 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentSpineIndex > 0) {
                                        currentSpineIndex--
                                        currentProgression = 0f
                                        val prevChapterHref = book.spine[currentSpineIndex]
                                        readerController.goToChapter(currentSpineIndex, prevChapterHref)
                                        onProgressUpdated(currentSpineIndex, 0f, null)
                                        haptic.perform(LumaHapticFeedbackType.PAGE_TURN)
                                    }
                                },
                                enabled = currentSpineIndex > 0,
                                modifier = if (isVeryNarrowWindow) Modifier.size(36.dp) else Modifier
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
                                        val wasSnapped = scrubProgress != rawValue
                                        val isNowSnapped = magnetized != rawValue
                                        
                                        // Trigger tactile haptic ticks on chapter boundary snap or chapter crossing
                                        if (newMagnetizedSpine != oldMagnetizedSpine || (!wasSnapped && isNowSnapped)) {
                                            haptic.perform(LumaHapticFeedbackType.SEGMENT_TICK)
                                        }
                                        
                                        scrubProgress = magnetized
                                        sliderProgress = magnetized
                                    }
                                },
                                onValueChangeFinished = {
                                    isScrubbing = false
                                    val totalSpinePosition = sliderProgress * book.spine.size
                                    val newSpineIndex = totalSpinePosition.toInt().coerceIn(0, book.spine.size - 1)
                                    val chapterProg = (totalSpinePosition - newSpineIndex).coerceIn(0f, 1f)
                                    currentSpineIndex = newSpineIndex
                                    currentProgression = chapterProg
                                    readerController.goToProgression(sliderProgress)
                                    onProgressUpdated(newSpineIndex, chapterProg, null)
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
                                        val nextChapterHref = book.spine[currentSpineIndex]
                                        readerController.goToChapter(currentSpineIndex, nextChapterHref)
                                        onProgressUpdated(currentSpineIndex, 0f, null)
                                        haptic.perform(LumaHapticFeedbackType.PAGE_TURN)
                                    }
                                },
                                enabled = currentSpineIndex < book.spine.size - 1,
                                modifier = if (isVeryNarrowWindow) Modifier.size(36.dp) else Modifier
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next chapter",
                                    tint = if (currentSpineIndex < book.spine.size - 1) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                        }

                        // Table of Contents & details row
                        val activeProcessedItem = processedSpineItems[displaySpineIndex]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (isNarrowWindow) 12.dp else 24.dp)
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val progressText = if (isNarrowWindow) {
                                    if (activeProcessedItem.isRealChapter) {
                                        "$displayBookPercentage% • Ch. ${activeProcessedItem.realChapterNumber}/${totalRealChapters}"
                                    } else {
                                        "$displayBookPercentage% • ${activeProcessedItem.title}"
                                    }
                                } else {
                                    if (activeProcessedItem.isRealChapter) {
                                        "Book Progress: $displayBookPercentage% • Chapter ${activeProcessedItem.realChapterNumber} of $totalRealChapters"
                                    } else {
                                        "Book Progress: $displayBookPercentage% • ${activeProcessedItem.title}"
                                    }
                                }
                                Text(
                                    text = progressText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isScrubbing) {
                                    Text(
                                        text = "Release to jump to position",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = GoogleSans,
                                        color = accentColor,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    val pageText = when (livePreferences.bottomBarDisplayMode) {
                                        BottomBarDisplayMode.PAGES -> {
                                            if (livePreferences.navigationStyle == PageNavigationStyle.CONTINUOUS_SCROLL) {
                                                "$currentChapterTitle • $displayBookPercentage%"
                                            } else if (totalBookPages <= 1) {
                                                if (isNarrowWindow) "p. $currentBookPage / $totalBookPages" else "Page $currentBookPage of $totalBookPages"
                                            } else {
                                                if (isNarrowWindow) "p. $currentBookPage / $totalBookPages • ${chapterPagesLeft} left"
                                                else "Page $currentBookPage of $totalBookPages ($chapterPagesLeft page${if (chapterPagesLeft == 1) "" else "s"} left in chapter)"
                                            }
                                        }
                                        BottomBarDisplayMode.TIME -> {
                                            val estimatedMinsLeft = (chapterPagesLeft * 1024 / (livePreferences.readingSpeedWpm * 5)).coerceAtLeast(1)
                                            if (livePreferences.navigationStyle == PageNavigationStyle.CONTINUOUS_SCROLL) {
                                                "$currentChapterTitle • ~${estimatedMinsLeft}m left"
                                            } else {
                                                if (isNarrowWindow) "p. $currentBookPage / $totalBookPages • ~${estimatedMinsLeft}m left"
                                                else "Page $currentBookPage of $totalBookPages (~$estimatedMinsLeft min${if (estimatedMinsLeft == 1) "" else "s"} left in chapter)"
                                            }
                                        }
                                        BottomBarDisplayMode.COMPACT -> {
                                            "$currentChapterTitle • $displayBookPercentage%"
                                        }
                                    }
                                    Text(
                                        text = pageText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = GoogleSans,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable {
                                            val nextMode = when (livePreferences.bottomBarDisplayMode) {
                                                BottomBarDisplayMode.PAGES -> BottomBarDisplayMode.TIME
                                                BottomBarDisplayMode.TIME -> BottomBarDisplayMode.COMPACT
                                                BottomBarDisplayMode.COMPACT -> BottomBarDisplayMode.PAGES
                                            }
                                            val updated = livePreferences.copy(bottomBarDisplayMode = nextMode)
                                            livePreferences = updated
                                            onPreferencesChanged(updated)
                                            haptic.perform(LumaHapticFeedbackType.TAP)
                                        }
                                    )
                                }
                            }
                            
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = if (isVeryNarrowWindow) Modifier.size(36.dp) else Modifier
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
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
                            kotlin.math.abs(currentProgression - (initialMenuProgression ?: 0f)) > 0.005f ||
                            (isScrubbing && kotlin.math.abs(scrubProgress - entireBookProgress) > 0.005f))

            val effectiveBottomPadding = if (bottomBarHeightDp > 0.dp) bottomBarHeightDp + 16.dp else 192.dp
            
            AnimatedVisibility(
                visible = isUiVisible && hasMoved,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                ) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = effectiveBottomPadding) // Float elegantly above the bottom card
            ) {
                Button(
                    onClick = {
                        val originalLocator = initialMenuLocatorJson
                        val originalSpine = initialMenuSpineIndex
                        val originalProg = initialMenuProgression
                        if (!originalLocator.isNullOrBlank()) {
                            readerController.goToLocator(originalLocator)
                            if (originalSpine != null && originalProg != null) {
                                currentSpineIndex = originalSpine
                                currentProgression = originalProg
                                currentLocatorJson = originalLocator
                                onProgressUpdated(originalSpine, originalProg, originalLocator)
                            }
                            haptic.perform(LumaHapticFeedbackType.LONG_PRESS)
                        } else if (originalSpine != null && originalProg != null) {
                            currentSpineIndex = originalSpine
                            currentProgression = originalProg
                            val originalHref = book.spine[originalSpine]
                            readerController.goToChapter(originalSpine, originalHref)
                            onProgressUpdated(originalSpine, originalProg, null)
                            haptic.perform(LumaHapticFeedbackType.LONG_PRESS)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                    contentPadding = PaddingValues(horizontal = if (isNarrowWindow) 12.dp else 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Reset position",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(if (isNarrowWindow) 4.dp else 8.dp))
                    Text(
                        text = if (isNarrowWindow) "Reset" else "Reset to original page",
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
        ReaderFormatBottomSheet(
            preferences = livePreferences,
            onLivePreferencesChanged = { livePreferences = it },
            onPreferencesChanged = {
                livePreferences = it
                onPreferencesChanged(it)
            },
            onDismissRequest = { showFormatSheet = false },
            accentColor = accentColor,
            book = book,
            onJumpToAnnotation = { anno ->
                readerController.goToLocator(anno.locatorJson)
                showFormatSheet = false
            },
            onDeleteAnnotation = onDeleteAnnotation
        )
    }
}

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
