package com.example.lumareader.ui.reader.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lumareader.data.model.*
import com.example.lumareader.theme.GoogleSans
import com.example.lumareader.ui.components.*
import com.example.lumareader.ui.utils.LumaSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderFormatBottomSheet(
    preferences: ReadingPreferences,
    onPreferencesChanged: (ReadingPreferences) -> Unit,
    onLivePreferencesChanged: (ReadingPreferences) -> Unit,
    onDismissRequest: () -> Unit,
    accentColor: Color,
    book: Book? = null,
    onJumpToAnnotation: (BookAnnotation) -> Unit = {},
    onDeleteAnnotation: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var livePreferences by remember(preferences) { mutableStateOf(preferences) }
    LaunchedEffect(preferences) {
        livePreferences = preferences
    }
    var activeDraggingSliderId by remember { mutableStateOf<String?>(null) }
    val isAnySliderDragging = activeDraggingSliderId != null

    var selectedTabIndex by remember { mutableStateOf(0) }
    var showCustomFontSheet by remember { mutableStateOf(false) }
    var showPublisherOverrideNotice by remember { mutableStateOf(false) }

    val handleUpdateLivePrefs: (ReadingPreferences) -> Unit = { updated ->
        livePreferences = updated
        onLivePreferencesChanged(updated)
    }

    val handleCommitPrefs: (ReadingPreferences) -> Unit = { updated ->
        livePreferences = updated
        onLivePreferencesChanged(updated)
        onPreferencesChanged(updated)
    }

    val baseSheetColor = MaterialTheme.colorScheme.surfaceContainer
    val sheetContainerColor by animateColorAsState(
        targetValue = if (isAnySliderDragging) Color.Transparent else baseSheetColor,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "sheetContainerColor"
    )
    val sheetScrimColor by animateColorAsState(
        targetValue = if (isAnySliderDragging) Color.Transparent else Color.Black.copy(alpha = 0.32f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "sheetScrimColor"
    )
    val nonActiveControlsAlpha by animateFloatAsState(
        targetValue = if (isAnySliderDragging) 0f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "nonActiveControlsAlpha"
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetContainerColor,
        scrimColor = sheetScrimColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            val dragHandleAlpha by animateFloatAsState(
                targetValue = if (isAnySliderDragging) 0f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                label = "dragHandleAlpha"
            )
            BottomSheetDefaults.DragHandle(
                modifier = Modifier.graphicsLayer { alpha = dragHandleAlpha }
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
        ) {
            val themeScrollState = rememberScrollState()
            val layoutScrollState = rememberScrollState()
            val notesScrollState = rememberScrollState()
            val moreScrollState = rememberScrollState()

            // Publisher Style Override Notice Banner
            AnimatedVisibility(
                visible = livePreferences.publisherStyles && (selectedTabIndex == 0 || selectedTabIndex == 1) && showPublisherOverrideNotice,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Overridden by publisher style",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Turn off publisher styles to customize typography.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                        }
                        TextButton(
                            onClick = {
                                selectedTabIndex = 3
                                showPublisherOverrideNotice = false
                            }
                        ) {
                            Text("Go to Settings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(themeScrollState)
                            .padding(top = 4.dp, bottom = 12.dp)
                    ) {
                        ThemeTabContent(
                            livePreferences = livePreferences,
                            accentColor = accentColor,
                            isAnySliderDragging = isAnySliderDragging,
                            activeDraggingSliderId = activeDraggingSliderId,
                            nonActiveControlsAlpha = nonActiveControlsAlpha,
                            onUpdateLivePrefs = handleUpdateLivePrefs,
                            onCommitPrefs = handleCommitPrefs,
                            onActiveSliderChange = { activeDraggingSliderId = it },
                            onOpenCustomFonts = { showCustomFontSheet = true },
                            onPublisherOverrideClick = { showPublisherOverrideNotice = true }
                        )
                    }
                    1 -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(layoutScrollState)
                            .padding(top = 4.dp, bottom = 12.dp)
                    ) {
                        LayoutTabContent(
                            livePreferences = livePreferences,
                            accentColor = accentColor,
                            isAnySliderDragging = isAnySliderDragging,
                            activeDraggingSliderId = activeDraggingSliderId,
                            nonActiveControlsAlpha = nonActiveControlsAlpha,
                            onUpdateLivePrefs = handleUpdateLivePrefs,
                            onCommitPrefs = handleCommitPrefs,
                            onActiveSliderChange = { activeDraggingSliderId = it },
                            onPublisherOverrideClick = { showPublisherOverrideNotice = true }
                        )
                    }
                    2 -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(notesScrollState)
                            .padding(top = 4.dp, bottom = 12.dp)
                    ) {
                        NotesTabContent(
                            livePreferences = livePreferences,
                            accentColor = accentColor,
                            nonActiveControlsAlpha = nonActiveControlsAlpha,
                            book = book,
                            onJumpToAnnotation = onJumpToAnnotation,
                            onDeleteAnnotation = onDeleteAnnotation,
                            onCommitPrefs = handleCommitPrefs
                        )
                    }
                    3 -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(moreScrollState)
                            .padding(top = 4.dp, bottom = 12.dp)
                    ) {
                        MoreTabContent(
                            livePreferences = livePreferences,
                            accentColor = accentColor,
                            nonActiveControlsAlpha = nonActiveControlsAlpha,
                            onCommitPrefs = handleCommitPrefs
                        )
                    }
                }
            }

            // Pinned Bottom Navigation Bar (pinned at bottom above system navigation)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.graphicsLayer { alpha = nonActiveControlsAlpha }
            )
            SecondaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = accentColor,
                divider = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = nonActiveControlsAlpha }
                    .navigationBarsPadding()
            ) {
                val tabs = listOf(
                    Triple(0, "Theme", Icons.Default.Palette),
                    Triple(1, "Layout", Icons.Default.AspectRatio),
                    Triple(2, "Notes", Icons.Default.EditNote),
                    Triple(3, "More", Icons.Default.Tune)
                )
                tabs.forEach { (index, label, icon) ->
                    val isSelected = selectedTabIndex == index
                    Tab(
                        selected = isSelected,
                        onClick = {
                            selectedTabIndex = index
                            showPublisherOverrideNotice = false
                        },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        text = {
                            Text(
                                text = label,
                                fontFamily = GoogleSans,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        },
                        selectedContentColor = accentColor,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Custom Font Importer & Selector Sheet
    if (showCustomFontSheet) {
        CustomFontSheet(
            currentFont = livePreferences.fontFamily,
            customFonts = livePreferences.customFonts,
            accentColor = accentColor,
            onFontSelected = { fontKey ->
                val updated = livePreferences.copy(fontFamily = fontKey)
                livePreferences = updated
                onPreferencesChanged(updated)
            },
            onFontsListUpdated = { updatedList ->
                val updated = livePreferences.copy(customFonts = updatedList)
                livePreferences = updated
                onPreferencesChanged(updated)
            },
            onDismissRequest = { showCustomFontSheet = false }
        )
    }
}

@Composable
private fun ThemeTabContent(
    livePreferences: ReadingPreferences,
    accentColor: Color,
    isAnySliderDragging: Boolean,
    activeDraggingSliderId: String?,
    nonActiveControlsAlpha: Float,
    onUpdateLivePrefs: (ReadingPreferences) -> Unit,
    onCommitPrefs: (ReadingPreferences) -> Unit,
    onActiveSliderChange: (String?) -> Unit,
    onOpenCustomFonts: () -> Unit,
    onPublisherOverrideClick: () -> Unit
) {
    val isOverridden = livePreferences.publisherStyles

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Theme Palettes
        Column(modifier = Modifier.graphicsLayer { alpha = nonActiveControlsAlpha }) {
            Text(
                text = "Reading Canvas Theme",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Controls book page background and typography color palette.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            ThemePaletteSelector(
                currentTheme = livePreferences.themeMode,
                dayTheme = livePreferences.dayThemeMode,
                nightTheme = livePreferences.nightThemeMode,
                onThemeSelected = {
                    onCommitPrefs(livePreferences.copy(themeMode = it))
                },
                onDayThemeSelected = {
                    onCommitPrefs(livePreferences.copy(dayThemeMode = it))
                },
                onNightThemeSelected = {
                    onCommitPrefs(livePreferences.copy(nightThemeMode = it))
                }
            )
        }

        // Accent Color Swatches
        Column(modifier = Modifier.graphicsLayer { alpha = nonActiveControlsAlpha }) {
            Text(
                text = "Accent Color",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            AccentColorPicker(
                selectedColorHex = livePreferences.accentColorHex,
                onColorSelected = { hex ->
                    onCommitPrefs(livePreferences.copy(accentColorHex = hex))
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.graphicsLayer { alpha = nonActiveControlsAlpha },
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // Font Family Selector
        Column(
            modifier = Modifier
                .graphicsLayer { alpha = if (isOverridden) nonActiveControlsAlpha * 0.45f else nonActiveControlsAlpha }
                .then(
                    if (isOverridden) Modifier.clickable { onPublisherOverrideClick() } else Modifier
                )
        ) {
            Text(
                text = "Font",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            FontFamilySelector(
                currentFont = livePreferences.fontFamily,
                accentColor = accentColor,
                onFontSelected = { font ->
                    onCommitPrefs(livePreferences.copy(fontFamily = font))
                },
                onCustomFontClick = onOpenCustomFonts
            )
        }

        // Font Size Slider (With Peek-Through)
        Box(
            modifier = Modifier.then(
                if (isOverridden) Modifier.graphicsLayer { alpha = 0.45f }.clickable { onPublisherOverrideClick() } else Modifier
            )
        ) {
            LumaSlider(
                label = "Font Size",
                value = livePreferences.fontSizeSp,
                defaultValue = 20f,
                isActiveDragging = activeDraggingSliderId == "fontSize",
                isAnyDragging = isAnySliderDragging,
                onDragStateChange = { isDragging ->
                    if (!isOverridden) {
                        onActiveSliderChange(if (isDragging) "fontSize" else null)
                    }
                },
                onValueChange = { newVal ->
                    if (!isOverridden) {
                        onUpdateLivePrefs(livePreferences.copy(fontSizeSp = newVal))
                    }
                },
                onValueChangeFinished = { newVal ->
                    if (!isOverridden) {
                        onCommitPrefs(livePreferences.copy(fontSizeSp = newVal))
                    }
                },
                valueRange = 10f..30f,
                steps = 19,
                accentColor = accentColor,
                valueFormatter = { "${it.toInt()} sp" }
            )
        }

        // Text Justification
        Column(
            modifier = Modifier
                .graphicsLayer { alpha = if (isOverridden) nonActiveControlsAlpha * 0.45f else nonActiveControlsAlpha }
                .then(
                    if (isOverridden) Modifier.clickable { onPublisherOverrideClick() } else Modifier
                )
        ) {
            Text(
                text = "Text Justification",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextJustificationSelector(
                justification = livePreferences.textJustification,
                accentColor = accentColor,
                onJustificationSelected = {
                    if (!isOverridden) {
                        onCommitPrefs(livePreferences.copy(textJustification = it))
                    }
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.graphicsLayer { alpha = nonActiveControlsAlpha },
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // Image Treatment
        Column(modifier = Modifier.graphicsLayer { alpha = nonActiveControlsAlpha }) {
            Text(
                text = "Image Treatment",
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
                    ImageHandlingMode.ORIGINAL to "Original",
                    ImageHandlingMode.INVERT_BW to "Invert B/W",
                    ImageHandlingMode.INVERT_ALL to "Invert All"
                ).forEach { (mode, label) ->
                    val isSelected = livePreferences.imageHandlingMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) accentColor.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                onCommitPrefs(livePreferences.copy(imageHandlingMode = mode))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontFamily = GoogleSans,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutTabContent(
    livePreferences: ReadingPreferences,
    accentColor: Color,
    isAnySliderDragging: Boolean,
    activeDraggingSliderId: String?,
    nonActiveControlsAlpha: Float,
    onUpdateLivePrefs: (ReadingPreferences) -> Unit,
    onCommitPrefs: (ReadingPreferences) -> Unit,
    onActiveSliderChange: (String?) -> Unit,
    onPublisherOverrideClick: () -> Unit
) {
    val isOverridden = livePreferences.publisherStyles

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Margins Mode & Margins
        Column(
            modifier = Modifier
                .graphicsLayer { alpha = if (isOverridden) nonActiveControlsAlpha * 0.45f else nonActiveControlsAlpha }
                .then(if (isOverridden) Modifier.clickable { onPublisherOverrideClick() } else Modifier)
        ) {
            Text(
                text = "Margin Lock Mode",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            MarginLockModeSelector(
                lockMode = livePreferences.marginLockMode,
                accentColor = accentColor,
                onLockModeSelected = { mode ->
                    if (!isOverridden) {
                        val newPrefs = when (mode) {
                            MarginLockMode.LOCK_ALL -> {
                                val newVal = livePreferences.marginTopDp
                                livePreferences.copy(
                                    marginLockMode = MarginLockMode.LOCK_ALL,
                                    marginTopDp = newVal,
                                    marginBottomDp = newVal,
                                    marginLeftDp = newVal,
                                    marginRightDp = newVal,
                                    marginDp = newVal
                                )
                            }
                            MarginLockMode.LOCK_VH -> {
                                livePreferences.copy(
                                    marginLockMode = MarginLockMode.LOCK_VH,
                                    marginTopDp = livePreferences.marginTopDp,
                                    marginBottomDp = livePreferences.marginTopDp,
                                    marginLeftDp = livePreferences.marginLeftDp,
                                    marginRightDp = livePreferences.marginLeftDp
                                )
                            }
                            MarginLockMode.UNLOCKED -> {
                                livePreferences.copy(marginLockMode = MarginLockMode.UNLOCKED)
                            }
                        }
                        onCommitPrefs(newPrefs)
                    }
                }
            )
        }

        // Granular Margin Sliders (With Peek-Through)
        Box(
            modifier = Modifier.then(
                if (isOverridden) Modifier.graphicsLayer { alpha = 0.45f }.clickable { onPublisherOverrideClick() } else Modifier
            )
        ) {
            MarginSliders(
                preferences = livePreferences,
                accentColor = accentColor,
                activeDraggingId = activeDraggingSliderId,
                onActiveDraggingIdChange = { if (!isOverridden) onActiveSliderChange(it) },
                onLivePreferencesChange = { if (!isOverridden) onUpdateLivePrefs(it) },
                onPreferencesChanged = { if (!isOverridden) onCommitPrefs(it) }
            )
        }

        HorizontalDivider(
            modifier = Modifier.graphicsLayer { alpha = nonActiveControlsAlpha },
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // Foldable Columns Selector
        val isContinuousScroll = livePreferences.navigationStyle == PageNavigationStyle.CONTINUOUS_SCROLL
        Column(
            modifier = Modifier.graphicsLayer {
                alpha = if (isContinuousScroll) 0.5f else nonActiveControlsAlpha
            }
        ) {
            Text(
                text = "Layout Columns",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isContinuousScroll) {
                    "Continuous scroll uses 1 column for smooth vertical reading."
                } else {
                    "Auto chooses 1 column when folded and 2 columns when unfolded."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            ColumnLayoutSelector(
                currentMode = if (isContinuousScroll) ColumnLayoutMode.SINGLE else livePreferences.columnLayoutMode,
                accentColor = accentColor,
                enabled = !isContinuousScroll,
                onModeSelected = { mode ->
                    if (!isContinuousScroll) {
                        val updated = when (mode) {
                            ColumnLayoutMode.AUTO -> livePreferences.copy(columnLayoutMode = ColumnLayoutMode.AUTO)
                            ColumnLayoutMode.SINGLE -> livePreferences.copy(columnLayoutMode = ColumnLayoutMode.SINGLE)
                            ColumnLayoutMode.DUAL -> livePreferences.copy(columnLayoutMode = ColumnLayoutMode.DUAL)
                        }
                        onCommitPrefs(updated)
                    }
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.graphicsLayer { alpha = nonActiveControlsAlpha },
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // Advanced Typography Section
        Text(
            text = "Advanced Typography",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = accentColor,
            modifier = Modifier.graphicsLayer { alpha = nonActiveControlsAlpha }
        )

        // Line Spacing Slider
        Box(
            modifier = Modifier.then(
                if (isOverridden) Modifier.graphicsLayer { alpha = 0.45f }.clickable { onPublisherOverrideClick() } else Modifier
            )
        ) {
            LumaSlider(
                label = "Line Spacing",
                value = livePreferences.lineSpacing,
                defaultValue = 1.4f,
                isActiveDragging = activeDraggingSliderId == "lineSpacing",
                isAnyDragging = isAnySliderDragging,
                onDragStateChange = { isDragging ->
                    if (!isOverridden) onActiveSliderChange(if (isDragging) "lineSpacing" else null)
                },
                onValueChange = { newVal ->
                    if (!isOverridden) onUpdateLivePrefs(livePreferences.copy(lineSpacing = newVal))
                },
                onValueChangeFinished = { newVal ->
                    if (!isOverridden) onCommitPrefs(livePreferences.copy(lineSpacing = newVal))
                },
                valueRange = 1.0f..2.0f,
                steps = 9,
                accentColor = accentColor,
                valueFormatter = { "${(it * 10).toInt() / 10.0}x" }
            )
        }

        // Letter Spacing Slider
        Box(
            modifier = Modifier.then(
                if (isOverridden) Modifier.graphicsLayer { alpha = 0.45f }.clickable { onPublisherOverrideClick() } else Modifier
            )
        ) {
            LumaSlider(
                label = "Letter Spacing",
                value = livePreferences.letterSpacing,
                defaultValue = 0.0f,
                isActiveDragging = activeDraggingSliderId == "letterSpacing",
                isAnyDragging = isAnySliderDragging,
                onDragStateChange = { isDragging ->
                    if (!isOverridden) onActiveSliderChange(if (isDragging) "letterSpacing" else null)
                },
                onValueChange = { newVal ->
                    if (!isOverridden) onUpdateLivePrefs(livePreferences.copy(letterSpacing = newVal))
                },
                onValueChangeFinished = { newVal ->
                    if (!isOverridden) onCommitPrefs(livePreferences.copy(letterSpacing = newVal))
                },
                valueRange = -0.05f..0.30f,
                steps = 35,
                accentColor = accentColor,
                valueFormatter = { "${((it * 100).toInt()) / 100.0} em" }
            )
        }

        // Word Spacing Slider
        Box(
            modifier = Modifier.then(
                if (isOverridden) Modifier.graphicsLayer { alpha = 0.45f }.clickable { onPublisherOverrideClick() } else Modifier
            )
        ) {
            LumaSlider(
                label = "Word Spacing",
                value = livePreferences.wordSpacing,
                defaultValue = 0.0f,
                isActiveDragging = activeDraggingSliderId == "wordSpacing",
                isAnyDragging = isAnySliderDragging,
                onDragStateChange = { isDragging ->
                    if (!isOverridden) onActiveSliderChange(if (isDragging) "wordSpacing" else null)
                },
                onValueChange = { newVal ->
                    if (!isOverridden) onUpdateLivePrefs(livePreferences.copy(wordSpacing = newVal))
                },
                onValueChangeFinished = { newVal ->
                    if (!isOverridden) onCommitPrefs(livePreferences.copy(wordSpacing = newVal))
                },
                valueRange = 0.0f..0.80f,
                steps = 16,
                accentColor = accentColor,
                valueFormatter = { "${((it * 100).toInt()) / 100.0} em" }
            )
        }

        // Paragraph Spacing Slider
        Box(
            modifier = Modifier.then(
                if (isOverridden) Modifier.graphicsLayer { alpha = 0.45f }.clickable { onPublisherOverrideClick() } else Modifier
            )
        ) {
            LumaSlider(
                label = "Paragraph Spacing",
                value = livePreferences.paragraphSpacing,
                defaultValue = 0.5f,
                isActiveDragging = activeDraggingSliderId == "paragraphSpacing",
                isAnyDragging = isAnySliderDragging,
                onDragStateChange = { isDragging ->
                    if (!isOverridden) onActiveSliderChange(if (isDragging) "paragraphSpacing" else null)
                },
                onValueChange = { newVal ->
                    if (!isOverridden) onUpdateLivePrefs(livePreferences.copy(paragraphSpacing = newVal))
                },
                onValueChangeFinished = { newVal ->
                    if (!isOverridden) onCommitPrefs(livePreferences.copy(paragraphSpacing = newVal))
                },
                valueRange = 0.0f..2.0f,
                steps = 20,
                accentColor = accentColor,
                valueFormatter = { "${((it * 10).toInt()) / 10.0} em" }
            )
        }

        // Paragraph Indent Slider
        Box(
            modifier = Modifier.then(
                if (isOverridden) Modifier.graphicsLayer { alpha = 0.45f }.clickable { onPublisherOverrideClick() } else Modifier
            )
        ) {
            LumaSlider(
                label = "Paragraph Indent",
                value = livePreferences.paragraphIndent,
                defaultValue = 1.0f,
                isActiveDragging = activeDraggingSliderId == "paragraphIndent",
                isAnyDragging = isAnySliderDragging,
                onDragStateChange = { isDragging ->
                    if (!isOverridden) onActiveSliderChange(if (isDragging) "paragraphIndent" else null)
                },
                onValueChange = { newVal ->
                    if (!isOverridden) onUpdateLivePrefs(livePreferences.copy(paragraphIndent = newVal))
                },
                onValueChangeFinished = { newVal ->
                    if (!isOverridden) onCommitPrefs(livePreferences.copy(paragraphIndent = newVal))
                },
                valueRange = 0.0f..2.5f,
                steps = 10,
                accentColor = accentColor,
                valueFormatter = { "${((it * 10).toInt()) / 10.0} em" }
            )
        }
    }
}

@Composable
private fun MoreTabContent(
    livePreferences: ReadingPreferences,
    accentColor: Color,
    nonActiveControlsAlpha: Float,
    onCommitPrefs: (ReadingPreferences) -> Unit
) {
    Column(
        modifier = Modifier.graphicsLayer { alpha = nonActiveControlsAlpha },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Navigation Style Setting
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Navigation Style",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Choose between paginated transitions or continuous vertical scrolling.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PageNavigationStyleSelector(
                currentStyle = livePreferences.navigationStyle,
                accentColor = accentColor,
                onStyleSelected = { onCommitPrefs(livePreferences.copy(navigationStyle = it)) }
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 2. Volume Buttons Navigation Toggle (Item #2)
        SettingsToggleRow(
            title = "Volume buttons turn pages",
            description = "Volume Down is next, Volume Up is back. Active strictly during full-screen reading.",
            checked = livePreferences.volumeKeyNavigation,
            onCheckedChange = { onCommitPrefs(livePreferences.copy(volumeKeyNavigation = it)) },
            accentColor = accentColor
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 3. Bottom Bar Display Mode (Item #3)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Bottom Bar Display Mode",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Choose what metric displays at the bottom of the reader.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BottomBarDisplayModeSelector(
                selectedMode = livePreferences.bottomBarDisplayMode,
                onModeSelected = { onCommitPrefs(livePreferences.copy(bottomBarDisplayMode = it)) },
                accentColor = accentColor
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Publisher Styles Toggle
        SettingsToggleRow(
            title = "Publisher Styles",
            description = "Honor original publisher styling and font rules. When ON, manual font and layout controls are disabled.",
            checked = livePreferences.publisherStyles,
            onCheckedChange = { onCommitPrefs(livePreferences.copy(publisherStyles = it)) },
            accentColor = accentColor
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Extend Behind Notch Toggle
        SettingsToggleRow(
            title = "Extend behind notch",
            description = "Allow reading content to utilize the entire immersive display area under the camera cutout.",
            checked = livePreferences.extendBehindNotch,
            onCheckedChange = { onCommitPrefs(livePreferences.copy(extendBehindNotch = it)) },
            accentColor = accentColor
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Hyphenation Toggle
        SettingsToggleRow(
            title = "Hyphenation",
            description = "Automatically hyphenate words across line wraps for even margins.",
            checked = livePreferences.hyphens,
            onCheckedChange = { onCommitPrefs(livePreferences.copy(hyphens = it)) },
            accentColor = accentColor
        )
    }
}

@Composable
private fun NotesTabContent(
    livePreferences: ReadingPreferences,
    accentColor: Color,
    nonActiveControlsAlpha: Float,
    book: Book? = null,
    onJumpToAnnotation: (BookAnnotation) -> Unit = {},
    onDeleteAnnotation: (String) -> Unit = {},
    onCommitPrefs: (ReadingPreferences) -> Unit
) {
    Column(
        modifier = Modifier.graphicsLayer { alpha = nonActiveControlsAlpha },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master Toggle at top
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (livePreferences.annotationsEnabled) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Annotations & Highlighting",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Enable text selection menu, color highlights, and margin notes while reading.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = livePreferences.annotationsEnabled,
                    onCheckedChange = { onCommitPrefs(livePreferences.copy(annotationsEnabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = accentColor
                    )
                )
            }
        }

        val notesOptionsAlpha by animateFloatAsState(
            targetValue = if (livePreferences.annotationsEnabled) 1f else 0.38f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
            label = "notesOptionsAlpha"
        )

        Column(
            modifier = Modifier.graphicsLayer { alpha = notesOptionsAlpha },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!livePreferences.annotationsEnabled) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Enable annotations above to customize highlighting options.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Highlight Color Palette
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Default Highlight Color",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Preset color applied during one-tap highlighting.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HighlightColorSelector(
                    selectedColorHex = livePreferences.defaultHighlightColorHex,
                    onColorSelected = {
                        if (livePreferences.annotationsEnabled) {
                            onCommitPrefs(livePreferences.copy(defaultHighlightColorHex = it))
                        }
                    }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Annotation Visual Style
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Annotation Style",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Choose between translucent background fill or clean underline.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnnotationStyleSelector(
                    selectedStyle = livePreferences.annotationStyle,
                    onStyleSelected = {
                        if (livePreferences.annotationsEnabled) {
                            onCommitPrefs(livePreferences.copy(annotationStyle = it))
                        }
                    },
                    accentColor = accentColor
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Notes & Highlights List Section
            val annotations = book?.annotations ?: emptyList()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Notes & Highlights (${annotations.size})",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (annotations.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(32.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "No notes or highlights yet",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Select text while reading to highlight, write notes, copy to clipboard, or share.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        annotations.forEach { annotation ->
                            val annoColor = remember(annotation.colorHex) {
                                try {
                                    Color(
                                        red = annotation.colorHex.substring(1, 3).toInt(16) / 255f,
                                        green = annotation.colorHex.substring(3, 5).toInt(16) / 255f,
                                        blue = annotation.colorHex.substring(5, 7).toInt(16) / 255f
                                    )
                                } catch (_: Exception) {
                                    Color(0xFFFFE082)
                                }
                            }
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                onClick = { onJumpToAnnotation(annotation) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(annoColor)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "\"${annotation.text}\"",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontStyle = FontStyle.Italic,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (annotation.note.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.EditNote,
                                                    contentDescription = null,
                                                    tint = accentColor,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = annotation.note,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeleteAnnotation(annotation.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete annotation",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
