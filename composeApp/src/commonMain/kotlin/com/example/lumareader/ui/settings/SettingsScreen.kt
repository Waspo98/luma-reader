package com.example.lumareader.ui.settings


import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lumareader.data.model.LibraryViewMode
import com.example.lumareader.data.model.LumaThemeMode
import com.example.lumareader.data.model.ReadingPreferences
import com.example.lumareader.data.model.StorageFootprint
import com.example.lumareader.theme.GoogleSans
import com.example.lumareader.ui.components.*
import com.example.lumareader.ui.reader.components.ReaderFormatBottomSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: ReadingPreferences,
    onPreferencesChanged: (ReadingPreferences) -> Unit,
    onBackClick: () -> Unit,
    onSyncClick: () -> Unit,
    isSyncing: Boolean,
    syncEmail: String?,
    onDisconnectSync: () -> Unit = {},
    storageFootprint: StorageFootprint = StorageFootprint(),
    onExportBackupToFile: () -> Unit = {},
    onRestoreBackupFromFile: () -> Unit = {},
    onExportBackup: () -> String = { "" },
    onImportBackup: (String) -> Boolean = { false },
    onClearCoverCache: () -> Long = { 0L },
    onReindexLibrary: () -> Int = { 0 },
    modifier: Modifier = Modifier
) {
    var showReaderFormatBottomSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showReindexDialog by remember { mutableStateOf(false) }

    var backupJsonToExport by remember { mutableStateOf("") }
    var importJsonInput by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            // 1. App Appearance
            LumaSectionCard(title = "App Appearance") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Theme Palette",
                            fontFamily = GoogleSans,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Controls the theme palette across the entire app and reading canvas.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ThemePaletteSelector(
                            currentTheme = preferences.themeMode,
                            dayTheme = preferences.dayThemeMode,
                            nightTheme = preferences.nightThemeMode,
                            onThemeSelected = { onPreferencesChanged(preferences.copy(themeMode = it)) },
                            onDayThemeSelected = { onPreferencesChanged(preferences.copy(dayThemeMode = it)) },
                            onNightThemeSelected = { onPreferencesChanged(preferences.copy(nightThemeMode = it)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Accent Color",
                        fontFamily = GoogleSans,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AccentColorPicker(
                        selectedColorHex = preferences.accentColorHex,
                        onColorSelected = { onPreferencesChanged(preferences.copy(accentColorHex = it)) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    SettingsToggleRow(
                        title = "Haptic Feedback",
                        description = "Vibrate lightly on gestures, page flips, and toolbar actions.",
                        checked = preferences.hapticsEnabled,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(hapticsEnabled = it)) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Prominent Reader Appearance & Formatting Card
                    Card(
                        onClick = { showReaderFormatBottomSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Reader Appearance & Layout",
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Fine-tune book typography, margins, columns, and line spacing",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Reading Behavior
            LumaSectionCard(title = "Reading Behavior") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsToggleRow(
                        title = "Keep screen awake",
                        description = "Prevent the screen from turning off while reading a book.",
                        checked = preferences.keepScreenOn,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(keepScreenOn = it)) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    SettingsToggleRow(
                        title = "Immersive fullscreen",
                        description = "Hide system status and navigation bars during reading sessions.",
                        checked = preferences.immersiveMode,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(immersiveMode = it)) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    SettingsToggleRow(
                        title = "Extend behind notch",
                        description = "Allow reading content to utilize the entire immersive display area under the camera cutout.",
                        checked = preferences.extendBehindNotch,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(extendBehindNotch = it)) }
                    )
                }
            }

            // 3. Library Management
            LumaSectionCard(title = "Library Management") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Default View Mode",
                            fontFamily = GoogleSans,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LumaSegmentedOption(
                                selected = preferences.libraryPrefs.viewMode == LibraryViewMode.GRID,
                                onClick = {
                                    onPreferencesChanged(
                                        preferences.copy(
                                            libraryPrefs = preferences.libraryPrefs.copy(viewMode = LibraryViewMode.GRID)
                                        )
                                    )
                                },
                                accentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (preferences.libraryPrefs.viewMode == LibraryViewMode.GRID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Grid",
                                        fontFamily = GoogleSans,
                                        fontWeight = if (preferences.libraryPrefs.viewMode == LibraryViewMode.GRID) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = if (preferences.libraryPrefs.viewMode == LibraryViewMode.GRID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            LumaSegmentedOption(
                                selected = preferences.libraryPrefs.viewMode == LibraryViewMode.LIST,
                                onClick = {
                                    onPreferencesChanged(
                                        preferences.copy(
                                            libraryPrefs = preferences.libraryPrefs.copy(viewMode = LibraryViewMode.LIST)
                                        )
                                    )
                                },
                                accentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ViewList,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (preferences.libraryPrefs.viewMode == LibraryViewMode.LIST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "List",
                                        fontFamily = GoogleSans,
                                        fontWeight = if (preferences.libraryPrefs.viewMode == LibraryViewMode.LIST) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = if (preferences.libraryPrefs.viewMode == LibraryViewMode.LIST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    SettingsToggleRow(
                        title = "Reading progress badges",
                        description = "Display completion percentage badges on book covers.",
                        checked = preferences.libraryPrefs.showProgressBadges,
                        onCheckedChange = {
                            onPreferencesChanged(
                                preferences.copy(
                                    libraryPrefs = preferences.libraryPrefs.copy(showProgressBadges = it)
                                )
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    SettingsToggleRow(
                        title = "Series badges",
                        description = "Show series title and volume index on library cards.",
                        checked = preferences.libraryPrefs.showSeriesBadges,
                        onCheckedChange = {
                            onPreferencesChanged(
                                preferences.copy(
                                    libraryPrefs = preferences.libraryPrefs.copy(showSeriesBadges = it)
                                )
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    SettingsToggleRow(
                        title = "Auto-shelve on import",
                        description = "Automatically create and assign custom shelves based on EPUB genre tags.",
                        checked = preferences.libraryPrefs.autoShelveBySubject,
                        onCheckedChange = {
                            onPreferencesChanged(
                                preferences.copy(
                                    libraryPrefs = preferences.libraryPrefs.copy(autoShelveBySubject = it)
                                )
                            )
                        }
                    )
                }
            }

            // 4. Cloud & Backup
            LumaSectionCard(title = "Cloud & Backup") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Google Drive Sync
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
                                text = if (syncEmail != null) "Connected as $syncEmail" else "Sync library catalog and reading positions across devices.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (syncEmail != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onSyncClick,
                                enabled = !isSyncing,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Syncing...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sync Now", fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = onDisconnectSync,
                                enabled = !isSyncing,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Disconnect", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Auto-sync Toggle Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Auto-sync on app open",
                                    fontFamily = GoogleSans,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Automatically synchronizes reading progress upon launching Luma Reader.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = preferences.syncPrefs.autoSyncOnOpen,
                                onCheckedChange = { checked ->
                                    onPreferencesChanged(
                                        preferences.copy(
                                            syncPrefs = preferences.syncPrefs.copy(autoSyncOnOpen = checked)
                                        )
                                    )
                                }
                            )
                        }

                        // Sync on Reader Exit Toggle Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Sync on reader exit",
                                    fontFamily = GoogleSans,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Automatically uploads reading progress when closing a book or returning to library.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = preferences.syncPrefs.autoSyncOnClose,
                                onCheckedChange = { checked ->
                                    onPreferencesChanged(
                                        preferences.copy(
                                            syncPrefs = preferences.syncPrefs.copy(autoSyncOnClose = checked)
                                        )
                                    )
                                }
                            )
                        }
                    } else {
                        Button(
                            onClick = onSyncClick,
                            enabled = !isSyncing,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                                    text = "Connect Google Drive",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Export Backup
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Export Library Backup",
                                fontFamily = GoogleSans,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Export all book metadata, custom shelves, and app preferences to JSON.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onExportBackupToFile,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Restore Backup
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Restore Library Backup",
                                fontFamily = GoogleSans,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Restore library database and preferences from a previously exported JSON backup.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onRestoreBackupFromFile,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore")
                        }
                    }
                }
            }

            // 5. Storage & Diagnostics
            LumaSectionCard(title = "Storage & Diagnostics") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Storage footprint stats
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Book Storage",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${storageFootprint.bookCount} books (${formatBytes(storageFootprint.booksSizeBytes)})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Cover Image Cache",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatBytes(storageFootprint.cacheSizeBytes),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total Footprint",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = formatBytes(storageFootprint.booksSizeBytes + storageFootprint.cacheSizeBytes),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Maintenance action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showClearCacheDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Cache", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { showReindexDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-index", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // About Luma Reader Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Luma Reader v1.0.0",
                                fontFamily = GoogleSans,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Powered by Readium 3.3.0 & Jetpack Compose Multiplatform. 100% Offline & Private.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showReaderFormatBottomSheet) {
        ReaderFormatBottomSheet(
            preferences = preferences,
            onPreferencesChanged = onPreferencesChanged,
            onLivePreferencesChanged = onPreferencesChanged,
            onDismissRequest = { showReaderFormatBottomSheet = false },
            accentColor = MaterialTheme.colorScheme.primary,
            book = null
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Text(
                    text = "Library Backup",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Backup contains your complete library catalog, custom shelves, and preferences.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = backupJsonToExport,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(backupJsonToExport))
                        showExportDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Backup copied to clipboard")
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExportDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Text(
                    text = "Restore Library",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Paste your exported JSON backup below to restore your library catalog and preferences.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = importJsonInput,
                        onValueChange = {
                            importJsonInput = it
                            importError = null
                        },
                        placeholder = { Text("Paste JSON backup here...", fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        isError = importError != null,
                        supportingText = importError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                    )
                    OutlinedButton(
                        onClick = {
                            clipboardManager.getText()?.text?.let { clipText ->
                                importJsonInput = clipText
                                importError = null
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste Clipboard", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importJsonInput.isBlank()) {
                            importError = "Please enter backup JSON"
                            return@Button
                        }
                        val success = onImportBackup(importJsonInput)
                        if (success) {
                            showImportDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Library restored successfully")
                            }
                        } else {
                            importError = "Invalid backup JSON structure"
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showImportDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    }

    if (showClearCacheDialog) {
        LumaConfirmationDialog(
            title = "Clear Cover Cache?",
            message = "This will delete all cached book covers (${formatBytes(storageFootprint.cacheSizeBytes)}). Cover images will be re-extracted automatically as you browse your library.",
            confirmText = "Clear Cache",
            isDestructive = true,
            onConfirm = {
                showClearCacheDialog = false
                val freed = onClearCoverCache()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Cover cache cleared (${formatBytes(freed)} freed)")
                }
            },
            onDismiss = { showClearCacheDialog = false }
        )
    }

    if (showReindexDialog) {
        LumaConfirmationDialog(
            title = "Re-index Library?",
            message = "This will scan your library storage, verify that all book files exist on disk, and update the catalog.",
            confirmText = "Re-index",
            onConfirm = {
                showReindexDialog = false
                val verifiedCount = onReindexLibrary()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Library re-indexed ($verifiedCount books verified)")
                }
            },
            onDismiss = { showReindexDialog = false }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var digitGroups = 0
    var b = bytes.toDouble()
    while (b >= 1024.0 && digitGroups < units.size - 1) {
        b /= 1024.0
        digitGroups++
    }
    return if (digitGroups == 0) {
        "${b.toLong()} ${units[digitGroups]}"
    } else {
        val rounded = ((b * 10).toLong()) / 10.0
        "$rounded ${units[digitGroups]}"
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    LumaSectionCard(
        title = title,
        content = content
    )
}

