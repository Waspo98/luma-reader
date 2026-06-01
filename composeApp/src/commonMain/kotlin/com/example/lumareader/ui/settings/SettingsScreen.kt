package com.example.lumareader.ui.settings


import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lumareader.data.model.LumaThemeMode
import com.example.lumareader.data.model.ReadingPreferences
import com.example.lumareader.data.model.MarginLockMode
import com.example.lumareader.theme.GoogleSans
import com.example.lumareader.ui.utils.LumaSlider
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: ReadingPreferences,
    onPreferencesChanged: (ReadingPreferences) -> Unit,
    onBackClick: () -> Unit,
    onSyncClick: () -> Unit,
    isSyncing: Boolean,
    syncEmail: String?,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontFamily = GoogleSans,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Theme Mode Section
            SettingsSection(title = "Appearance") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Theme Palette",
                        fontFamily = GoogleSans,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeOptionCard(
                            title = "System",
                            isSelected = preferences.themeMode == LumaThemeMode.SYSTEM,
                            onClick = { onPreferencesChanged(preferences.copy(themeMode = LumaThemeMode.SYSTEM)) },
                            colors = listOf(Color(0xFFFAF7F0), Color(0xFF1C2025)),
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionCard(
                            title = "Cream Light",
                            isSelected = preferences.themeMode == LumaThemeMode.LIGHT,
                            onClick = { onPreferencesChanged(preferences.copy(themeMode = LumaThemeMode.LIGHT)) },
                            colors = listOf(Color(0xFFFAF7F0), Color(0xFFFAF7F0)),
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionCard(
                            title = "Slate Gray",
                            isSelected = preferences.themeMode == LumaThemeMode.SLATE_GRAY,
                            onClick = { onPreferencesChanged(preferences.copy(themeMode = LumaThemeMode.SLATE_GRAY)) },
                            colors = listOf(Color(0xFF1C2025), Color(0xFF1C2025)),
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionCard(
                            title = "AMOLED",
                            isSelected = preferences.themeMode == LumaThemeMode.AMOLED_BLACK,
                            onClick = { onPreferencesChanged(preferences.copy(themeMode = LumaThemeMode.AMOLED_BLACK)) },
                            colors = listOf(Color(0xFF000000), Color(0xFF000000)),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    
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
                            onCheckedChange = { onPreferencesChanged(preferences.copy(extendBehindNotch = it)) }
                        )
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    
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
                            onCheckedChange = { onPreferencesChanged(preferences.copy(dropCapEnabled = it)) }
                        )
                    }
                }
            }

            // Typography Section
            SettingsSection(title = "Typography & Layout") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Font Family Selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Font Family",
                            fontFamily = GoogleSans,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Literata", "Inter", "serif").forEach { fontName ->
                                val isSelected = preferences.fontFamily == fontName
                                val displayLabel = if (fontName == "serif") "System Serif" else fontName
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onPreferencesChanged(preferences.copy(fontFamily = fontName)) },
                                    label = {
                                        Text(
                                            text = displayLabel,
                                            fontFamily = when (fontName) {
                                                "Literata" -> FontFamily.Serif
                                                "Inter" -> FontFamily.SansSerif
                                                "serif" -> FontFamily.Serif
                                                else -> FontFamily.Default
                                            }
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Font Size Slider
                    LumaSlider(
                        label = "Font Size",
                        value = preferences.fontSizeSp,
                        onValueChangeFinished = { newVal ->
                            onPreferencesChanged(preferences.copy(fontSizeSp = newVal))
                        },
                        valueRange = 10f..30f,
                        steps = 19,
                        accentColor = MaterialTheme.colorScheme.primary,
                        valueFormatter = { "${it.toInt()} sp" }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Line Spacing Slider
                    LumaSlider(
                        label = "Line Spacing",
                        value = preferences.lineSpacing,
                        onValueChangeFinished = { newVal ->
                            onPreferencesChanged(preferences.copy(lineSpacing = newVal))
                        },
                        valueRange = 1.0f..2.0f,
                        steps = 9,
                        accentColor = MaterialTheme.colorScheme.primary,
                        valueFormatter = { "${(it * 10).toInt() / 10.0}x" }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Margins Lock Mode & Granular Margins Sliders
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "Margin Lock Mode",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                val accentColor = MaterialTheme.colorScheme.primary
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
                        
                        val accentColor = MaterialTheme.colorScheme.primary
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
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Foldable Columns Settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Force Single Column",
                                fontFamily = GoogleSans,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Lock to one column even when device is unfolded or in landscape.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.twoColumnLocked,
                            onCheckedChange = { onPreferencesChanged(preferences.copy(twoColumnLocked = it)) }
                        )
                    }
                }
            }

            // Sync Settings Section
            SettingsSection(title = "Cloud Synchronization") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Google Drive Sync",
                                fontFamily = GoogleSans,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (syncEmail != null) "Connected as $syncEmail" else "Sync library and reading progress across devices.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = onSyncClick,
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (syncEmail != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (syncEmail != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = if (syncEmail != null) "Disconnect Sync" else "Connect Google Drive",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = GoogleSans
            )
            content()
        }
    }
}

@Composable
fun ThemeOptionCard(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessLow)
    )
    val borderWidth by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessLow)
    )
    
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Mock Page Representation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(colors))
                .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Mock lines of text
                val textColor = if (colors.first() == Color.White || colors.first() == Color(0xFFFAF7F0)) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.4f)
                Box(modifier = Modifier.fillMaxWidth(0.8f).height(4.dp).background(textColor))
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(4.dp).background(textColor))
            }
        }
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

// Deleted SettingsMarginSliderItem component
