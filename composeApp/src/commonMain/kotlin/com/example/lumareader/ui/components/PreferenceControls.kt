package com.example.lumareader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lumareader.data.model.ColumnLayoutMode
import com.example.lumareader.data.model.LumaThemeMode
import com.example.lumareader.data.model.MarginLockMode
import com.example.lumareader.data.model.PageNavigationStyle
import com.example.lumareader.data.model.ReadingPreferences
import com.example.lumareader.data.model.TextJustification
import com.example.lumareader.data.model.BottomBarDisplayMode
import com.example.lumareader.data.model.AnnotationStyle
import com.example.lumareader.theme.*
import com.example.lumareader.ui.utils.LumaSlider

/**
 * Expressive segmented option button primitive with animated selection border and background.
 * Used for text justification, margin locking, column layouts, and animation mode selectors.
 */
@Composable
fun LumaSegmentedOption(
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            selected -> accentColor
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "optionBorderColor"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected && enabled) 1.5.dp else 1.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "optionBorderWidth"
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.38f)
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "optionContainerColor"
    )

    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier.height(44.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

/**
 * Standard toggle row primitive with title, description, and Switch.
 * Used consistently across settings and reader format sheets.
 */
@Composable
fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = GoogleSans,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accentColor,
                checkedTrackColor = accentColor.copy(alpha = 0.5f)
            )
        )
    }
}

/**
 * Text justification segmented selector primitive.
 */
@Composable
fun TextJustificationSelector(
    justification: TextJustification,
    accentColor: Color,
    onJustificationSelected: (TextJustification) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        TextJustification.LEFT to ("Left" to Icons.AutoMirrored.Filled.FormatAlignLeft),
        TextJustification.RIGHT to ("Right" to Icons.AutoMirrored.Filled.FormatAlignRight),
        TextJustification.FULL to ("Full" to Icons.Default.FormatAlignJustify)
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (option, info) ->
            val (label, icon) = info
            val isSelected = justification == option
            LumaSegmentedOption(
                selected = isSelected,
                onClick = { onJustificationSelected(option) },
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                    )
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
    }
}

/**
 * Margin lock mode segmented selector primitive.
 */
@Composable
fun MarginLockModeSelector(
    lockMode: MarginLockMode,
    accentColor: Color,
    onLockModeSelected: (MarginLockMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf(
        MarginLockMode.LOCK_ALL to "Lock All",
        MarginLockMode.LOCK_VH to "Lock H/V",
        MarginLockMode.UNLOCKED to "Unlocked"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        modes.forEach { (mode, label) ->
            val isSelected = lockMode == mode
            LumaSegmentedOption(
                selected = isSelected,
                onClick = { onLockModeSelected(mode) },
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
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
}

/**
 * Dynamic margin sliders group primitive responding to MarginLockMode.
 */
@Composable
fun MarginSliders(
    preferences: ReadingPreferences,
    accentColor: Color,
    onPreferencesChanged: (ReadingPreferences) -> Unit,
    modifier: Modifier = Modifier,
    activeDraggingId: String? = null,
    onActiveDraggingIdChange: ((String?) -> Unit)? = null,
    onLivePreferencesChange: ((ReadingPreferences) -> Unit)? = null
) {
    val isAnyDragging = activeDraggingId != null

    Column(modifier = modifier.fillMaxWidth()) {
        when (preferences.marginLockMode) {
            MarginLockMode.LOCK_ALL -> {
                val sliderId = "margin_all"
                LumaSlider(
                    label = "All Margins",
                    value = preferences.marginTopDp.toFloat(),
                    defaultValue = 24f,
                    isActiveDragging = activeDraggingId == sliderId,
                    isAnyDragging = isAnyDragging,
                    onDragStateChange = { isDragging ->
                        onActiveDraggingIdChange?.invoke(if (isDragging) sliderId else null)
                    },
                    onValueChange = { newVal ->
                        val newValInt = newVal.toInt()
                        onLivePreferencesChange?.invoke(
                            preferences.copy(
                                marginTopDp = newValInt,
                                marginBottomDp = newValInt,
                                marginLeftDp = newValInt,
                                marginRightDp = newValInt,
                                marginDp = newValInt
                            )
                        )
                    },
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
                val sliderIdV = "margin_v"
                val sliderIdH = "margin_h"
                LumaSlider(
                    label = "Vertical Margins (Top/Bottom)",
                    value = preferences.marginTopDp.toFloat(),
                    defaultValue = 24f,
                    isActiveDragging = activeDraggingId == sliderIdV,
                    isAnyDragging = isAnyDragging,
                    onDragStateChange = { isDragging ->
                        onActiveDraggingIdChange?.invoke(if (isDragging) sliderIdV else null)
                    },
                    onValueChange = { newVal ->
                        val newValInt = newVal.toInt()
                        onLivePreferencesChange?.invoke(
                            preferences.copy(
                                marginTopDp = newValInt,
                                marginBottomDp = newValInt
                            )
                        )
                    },
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
                    defaultValue = 24f,
                    isActiveDragging = activeDraggingId == sliderIdH,
                    isAnyDragging = isAnyDragging,
                    onDragStateChange = { isDragging ->
                        onActiveDraggingIdChange?.invoke(if (isDragging) sliderIdH else null)
                    },
                    onValueChange = { newVal ->
                        val newValInt = newVal.toInt()
                        onLivePreferencesChange?.invoke(
                            preferences.copy(
                                marginLeftDp = newValInt,
                                marginRightDp = newValInt
                            )
                        )
                    },
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
                val sliderIdTop = "margin_top"
                val sliderIdBottom = "margin_bottom"
                val sliderIdLeft = "margin_left"
                val sliderIdRight = "margin_right"

                LumaSlider(
                    label = "Top Margin",
                    value = preferences.marginTopDp.toFloat(),
                    defaultValue = 24f,
                    isActiveDragging = activeDraggingId == sliderIdTop,
                    isAnyDragging = isAnyDragging,
                    onDragStateChange = { isDragging ->
                        onActiveDraggingIdChange?.invoke(if (isDragging) sliderIdTop else null)
                    },
                    onValueChange = { newVal ->
                        onLivePreferencesChange?.invoke(preferences.copy(marginTopDp = newVal.toInt()))
                    },
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
                    defaultValue = 24f,
                    isActiveDragging = activeDraggingId == sliderIdBottom,
                    isAnyDragging = isAnyDragging,
                    onDragStateChange = { isDragging ->
                        onActiveDraggingIdChange?.invoke(if (isDragging) sliderIdBottom else null)
                    },
                    onValueChange = { newVal ->
                        onLivePreferencesChange?.invoke(preferences.copy(marginBottomDp = newVal.toInt()))
                    },
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
                    defaultValue = 24f,
                    isActiveDragging = activeDraggingId == sliderIdLeft,
                    isAnyDragging = isAnyDragging,
                    onDragStateChange = { isDragging ->
                        onActiveDraggingIdChange?.invoke(if (isDragging) sliderIdLeft else null)
                    },
                    onValueChange = { newVal ->
                        onLivePreferencesChange?.invoke(preferences.copy(marginLeftDp = newVal.toInt()))
                    },
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
                    defaultValue = 24f,
                    isActiveDragging = activeDraggingId == sliderIdRight,
                    isAnyDragging = isAnyDragging,
                    onDragStateChange = { isDragging ->
                        onActiveDraggingIdChange?.invoke(if (isDragging) sliderIdRight else null)
                    },
                    onValueChange = { newVal ->
                        onLivePreferencesChange?.invoke(preferences.copy(marginRightDp = newVal.toInt()))
                    },
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
    }
}

/**
 * Column layout selector primitive.
 */
@Composable
fun ColumnLayoutSelector(
    currentMode: ColumnLayoutMode,
    accentColor: Color,
    onModeSelected: (ColumnLayoutMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val modes = listOf(
        ColumnLayoutMode.AUTO to "Auto (Foldable)",
        ColumnLayoutMode.SINGLE to "Single",
        ColumnLayoutMode.DUAL to "Double"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        modes.forEach { (mode, label) ->
            val isSelected = currentMode == mode
            LumaSegmentedOption(
                selected = isSelected,
                onClick = { onModeSelected(mode) },
                accentColor = accentColor,
                enabled = enabled,
                modifier = Modifier.weight(if (mode == ColumnLayoutMode.AUTO) 1.3f else 1f)
            ) {
                Text(
                    text = label,
                    fontFamily = GoogleSans,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        isSelected -> accentColor
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

/**
 * Page navigation style selector primitive with M3 Expressive card grid.
 */
@Composable
fun PageNavigationStyleSelector(
    currentStyle: PageNavigationStyle,
    accentColor: Color,
    onStyleSelected: (PageNavigationStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        PageNavigationStyle.HORIZONTAL_SLIDE to "Horizontal Slide",
        PageNavigationStyle.PAGE_TURN to "Page Turn",
        PageNavigationStyle.CONTINUOUS_SCROLL to "Continuous Scroll",
        PageNavigationStyle.INSTANT to "Instant"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.take(2).forEach { (style, label) ->
                val isSelected = currentStyle == style
                LumaSegmentedOption(
                    selected = isSelected,
                    onClick = { onStyleSelected(style) },
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = label,
                        fontFamily = GoogleSans,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.drop(2).forEach { (style, label) ->
                val isSelected = currentStyle == style
                LumaSegmentedOption(
                    selected = isSelected,
                    onClick = { onStyleSelected(style) },
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = label,
                        fontFamily = GoogleSans,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Font family selector primitive.
 */
@Composable
fun FontFamilySelector(
    currentFont: String,
    accentColor: Color,
    onFontSelected: (String) -> Unit,
    onCustomFontClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val fonts = listOf(
        "Literata" to "Literata",
        "Inter" to "Inter",
        "Roboto" to "Roboto",
        "custom" to if (currentFont.startsWith("custom:")) {
            currentFont.removePrefix("custom:").substringBeforeLast(".")
        } else "Custom"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        fonts.forEach { (fontKey, label) ->
            val isSelected = if (fontKey == "custom") {
                currentFont.startsWith("custom:")
            } else {
                currentFont == fontKey
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable {
                        if (fontKey == "custom") {
                            onCustomFontClick?.invoke()
                        } else {
                            onFontSelected(fontKey)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontFamily = when (fontKey) {
                        "Literata" -> LiterataFont
                        "Inter" -> InterFont
                        "Roboto" -> FontFamily.SansSerif
                        else -> FontFamily.Default
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Theme palette card primitive with miniature page preview and Day/Night system selection.
 */
@Composable
fun ThemePaletteSelector(
    currentTheme: LumaThemeMode,
    onThemeSelected: (LumaThemeMode) -> Unit,
    dayTheme: LumaThemeMode = LumaThemeMode.LIGHT,
    onDayThemeSelected: ((LumaThemeMode) -> Unit)? = null,
    nightTheme: LumaThemeMode = LumaThemeMode.SLATE_GRAY,
    onNightThemeSelected: ((LumaThemeMode) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val themes = listOf(
        Triple(LumaThemeMode.SYSTEM, "System", listOf(LightBackground, SlateBackground)),
        Triple(LumaThemeMode.LIGHT, "Bright", listOf(LightBackground, LightBackground)),
        Triple(LumaThemeMode.WARM_SEPIA, "Sepia", listOf(SepiaBackground, SepiaBackground)),
        Triple(LumaThemeMode.SLATE_GRAY, "Slate", listOf(SlateBackground, SlateBackground)),
        Triple(LumaThemeMode.AMOLED_BLACK, "AMOLED", listOf(AmoledBackground, AmoledBackground))
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            themes.forEach { (themeMode, title, colors) ->
                val isSelected = currentTheme == themeMode
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                    label = "themeBorderColor"
                )
                val borderWidth by animateDpAsState(
                    targetValue = if (isSelected) 2.dp else 1.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                    label = "themeBorderWidth"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
                        .clickable { onThemeSelected(themeMode) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Miniature Page Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(colors))
                            .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val textColor = if (colors.first() == LightBackground || colors.first() == SepiaBackground) {
                                Color.Black.copy(alpha = 0.4f)
                            } else {
                                Color.White.copy(alpha = 0.4f)
                            }
                            Box(modifier = Modifier.fillMaxWidth(0.8f).height(4.dp).background(textColor))
                            Box(modifier = Modifier.fillMaxWidth(0.6f).height(4.dp).background(textColor))
                        }
                    }
                    Text(
                        text = title,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // When System is active, present Day & Night theme selectors
        if (currentTheme == LumaThemeMode.SYSTEM && onDayThemeSelected != null && onNightThemeSelected != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Day Theme Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Day Theme",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(LumaThemeMode.LIGHT to "Bright", LumaThemeMode.WARM_SEPIA to "Sepia").forEach { (mode, label) ->
                            val active = dayTheme == mode
                            FilterChip(
                                selected = active,
                                onClick = { onDayThemeSelected(mode) },
                                label = { Text(label, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // Night Theme Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Night Theme",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(LumaThemeMode.SLATE_GRAY to "Slate", LumaThemeMode.AMOLED_BLACK to "AMOLED").forEach { (mode, label) ->
                            val active = nightTheme == mode
                            FilterChip(
                                selected = active,
                                onClick = { onNightThemeSelected(mode) },
                                label = { Text(label, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Accent color palette picker primitive with curated swatches.
 */
@Composable
fun AccentColorPicker(
    selectedColorHex: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(
        "#D45D42" to "Terracotta",
        "#D97706" to "Amber",
        "#059669" to "Emerald",
        "#00A896" to "Teal",
        "#2563EB" to "Azure",
        "#7C3AED" to "Violet",
        "#DC2626" to "Crimson",
        "#E11D48" to "Rose"
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { (hex, name) ->
            val color = hex.toComposeColor()
            val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
            val checkmarkTint = if (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue > 0.5f) {
                Color.Black
            } else {
                Color.White
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                    .clickable { onColorSelected(hex) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = name,
                        tint = checkmarkTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Selector for reader bottom bar display mode (Pages, Time Left, Compact).
 */
@Composable
fun BottomBarDisplayModeSelector(
    selectedMode: BottomBarDisplayMode,
    onModeSelected: (BottomBarDisplayMode) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        Triple(BottomBarDisplayMode.PAGES, "Pages", Icons.AutoMirrored.Filled.MenuBook),
        Triple(BottomBarDisplayMode.TIME, "Time Left", Icons.Default.Schedule),
        Triple(BottomBarDisplayMode.COMPACT, "Compact", Icons.AutoMirrored.Filled.ShortText)
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (mode, label, icon) ->
            val isSelected = selectedMode == mode
            LumaSegmentedOption(
                selected = isSelected,
                onClick = { onModeSelected(mode) },
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = label,
                        fontFamily = GoogleSans,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Curated pastel highlight color picker.
 */
@Composable
fun HighlightColorSelector(
    selectedColorHex: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(
        "#FFE082" to "Canary Yellow",
        "#A7F3D0" to "Mint Green",
        "#FDBA74" to "Peach Coral",
        "#DDD6FE" to "Lavender",
        "#BAE6FD" to "Sky Blue"
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { (hex, name) ->
            val color = hex.toComposeColor()
            val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
            val checkmarkTint = if (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue > 0.5f) {
                Color.Black.copy(alpha = 0.8f)
            } else {
                Color.White.copy(alpha = 0.9f)
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                    .clickable { onColorSelected(hex) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = name,
                        tint = checkmarkTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Selector for annotation visual style (Highlight vs Underline).
 */
@Composable
fun AnnotationStyleSelector(
    selectedStyle: AnnotationStyle,
    onStyleSelected: (AnnotationStyle) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        Triple(AnnotationStyle.HIGHLIGHT, "Highlight", Icons.Default.Brush),
        Triple(AnnotationStyle.UNDERLINE, "Underline", Icons.Default.FormatUnderlined)
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (style, label, icon) ->
            val isSelected = selectedStyle == style
            LumaSegmentedOption(
                selected = isSelected,
                onClick = { onStyleSelected(style) },
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = label,
                        fontFamily = GoogleSans,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

