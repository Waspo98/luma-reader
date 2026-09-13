package com.example.lumareader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lumareader.data.LocalBookRepository
import com.example.lumareader.data.import.LocalFolderScanner
import com.example.lumareader.data.model.DiscoveredEpub
import com.example.lumareader.data.sync.AndroidGoogleDriveClient
import com.example.lumareader.data.sync.CloudSyncManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.example.lumareader.theme.LumaReaderTheme
import com.example.lumareader.ui.components.ImportActionBottomSheet
import com.example.lumareader.ui.components.MassImportBottomSheet
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import java.io.File

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val nonFragmentBundle = savedInstanceState?.let { bundle ->
            Bundle(bundle).apply {
                remove("android:support:fragments")
            }
        }
        super.onCreate(nonFragmentBundle)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            @Suppress("DEPRECATION")
            window.isStatusBarContrastEnforced = false
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }

        // Request 120Hz / highest available display refresh rate for fluid 120fps page transitions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val modes = display?.supportedModes
            val maxRefreshMode = modes?.maxByOrNull { it.refreshRate }
            if (maxRefreshMode != null && maxRefreshMode.refreshRate >= 90f) {
                val params = window.attributes
                params.preferredDisplayModeId = maxRefreshMode.modeId
                window.attributes = params
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val modes = window.windowManager.defaultDisplay?.supportedModes
            val maxRefreshMode = modes?.maxByOrNull { it.refreshRate }
            if (maxRefreshMode != null && maxRefreshMode.refreshRate >= 90f) {
                val params = window.attributes
                params.preferredDisplayModeId = maxRefreshMode.modeId
                window.attributes = params
            }
        }
        
        setContent {
            val repository = remember { LocalBookRepository(filesDir.absolutePath, cacheDir.absolutePath) }
            DisposableEffect(repository) {
                onDispose { repository.close() }
            }
            
            val syncManager = remember { CloudSyncManager() }
            val isSyncing by syncManager.isSyncing.collectAsState()
            val syncEmail by syncManager.connectedEmail.collectAsState()
            val lastSyncResult by syncManager.lastSyncResult.collectAsState()
            val syncProgress by syncManager.syncProgress.collectAsState()
            val scope = rememberCoroutineScope()

            LaunchedEffect(syncProgress) {
                com.example.lumareader.data.sync.SyncNotificationHelper.updateProgress(this@MainActivity, syncProgress)
            }
            DisposableEffect(Unit) {
                onDispose {
                    com.example.lumareader.data.sync.SyncNotificationHelper.cancel(this@MainActivity)
                }
            }

            val gso = remember {
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(
                        Scope("https://www.googleapis.com/auth/drive.appdata"),
                        Scope("https://www.googleapis.com/auth/drive.file")
                    )
                    .build()
            }
            val googleSignInClient = remember { GoogleSignIn.getClient(this@MainActivity, gso) }
            var showSyncDiagnosticDialog by remember { mutableStateOf<String?>(null) }

            // Restore account on launch if previously signed in
            LaunchedEffect(Unit) {
                val lastAccount = GoogleSignIn.getLastSignedInAccount(this@MainActivity)
                if (lastAccount != null && lastAccount.account != null) {
                    val driveClient = AndroidGoogleDriveClient(this@MainActivity, lastAccount.account!!)
                    syncManager.setRemoteClient(driveClient)
                    syncManager.setConnectedEmail(lastAccount.email)
                    if (repository.preferences.value.syncPrefs.autoSyncOnOpen) {
                        syncManager.triggerSync(filesDir.absolutePath, repository, repository.preferences.value.syncPrefs.syncScope)
                    }
                }
            }

            val googleSignInLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    val email = account.email
                    if (account.account != null) {
                        val driveClient = AndroidGoogleDriveClient(this@MainActivity, account.account!!)
                        syncManager.setRemoteClient(driveClient)
                        scope.launch {
                            syncManager.connect(email ?: "Google Drive User")
                            val currentPrefs = repository.preferences.value
                            repository.savePreferences(
                                currentPrefs.copy(
                                    syncPrefs = currentPrefs.syncPrefs.copy(connectedEmail = email)
                                )
                            )
                            syncManager.triggerSync(filesDir.absolutePath, repository, currentPrefs.syncPrefs.syncScope)
                        }
                    }
                } catch (e: ApiException) {
                    val statusCode = e.statusCode
                    if (statusCode == CommonStatusCodes.DEVELOPER_ERROR) {
                        showSyncDiagnosticDialog = "Developer Error (code 10): Google Cloud Console needs your OAuth 2.0 Client ID configured with package 'com.example.lumareader' and your certificate SHA-1 fingerprint."
                    } else if (statusCode != CommonStatusCodes.SIGN_IN_REQUIRED && statusCode != CommonStatusCodes.CANCELED) {
                        Toast.makeText(this@MainActivity, "Google Sign-In failed ($statusCode): ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            var showImportActionSheet by remember { mutableStateOf(false) }
            var isScanningFolder by remember { mutableStateOf(false) }
            var scanJob by remember { mutableStateOf<Job?>(null) }
            var scanProgressText by remember { mutableStateOf("") }
            var discoveredEpubs by remember { mutableStateOf<List<DiscoveredEpub>?>(null) }
            var isImportingBatch by remember { mutableStateOf(false) }
            var batchImportProgress by remember { mutableStateOf(0f) }
            var batchImportCurrentBook by remember { mutableStateOf("") }

            // Launcher for Moon+ Reader style folder scanning (SAF OpenDocumentTree)
            val folderPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { treeUri ->
                if (treeUri != null) {
                    try {
                        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                    } catch (_: Exception) {}

                    isScanningFolder = true
                    discoveredEpubs = null
                    scanProgressText = "Preparing scanner..."
                    scanJob?.cancel()
                    scanJob = scope.launch {
                        try {
                            val scanner = LocalFolderScanner(this@MainActivity)
                            val results = scanner.scanTreeUri(treeUri, repository.books.value) { count, path ->
                                scanProgressText = if (path.isBlank()) "Found $count EPUBs..." else "Found $count EPUBs in $path..."
                            }
                            if (isActive) {
                                discoveredEpubs = results
                            }
                        } catch (e: Exception) {
                            if (e !is kotlinx.coroutines.CancellationException) {
                                e.printStackTrace()
                                discoveredEpubs = emptyList()
                            }
                        } finally {
                            isScanningFolder = false
                            scanJob = null
                        }
                    }
                }
            }

            // Standard launcher for system file picker to select multiple EPUB files
            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetMultipleContents()
            ) { uris ->
                if (!uris.isNullOrEmpty()) {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val booksDir = File(filesDir, "books").apply { mkdirs() }
                                uris.forEach { uri ->
                                    contentResolver.openInputStream(uri)?.use { inputStream ->
                                        var displayName = "import_${java.util.UUID.randomUUID().toString().take(8)}.epub"
                                        val cursor = contentResolver.query(uri, null, null, null, null)
                                        cursor?.use { c ->
                                            if (c.moveToFirst()) {
                                                val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                                if (nameIndex != -1) {
                                                    val name = c.getString(nameIndex)
                                                    if (!name.isNullOrBlank()) {
                                                        displayName = name
                                                    }
                                                }
                                            }
                                        }
                                        if (!displayName.endsWith(".epub", ignoreCase = true)) {
                                            displayName = "$displayName.epub"
                                        }
                                        val destFile = getNonCollidingFile(booksDir, displayName)
                                        destFile.outputStream().use { outputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                        repository.importBook(destFile.absolutePath)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            
            // Launcher to export library backup JSON via SAF CreateDocument
            val exportBackupLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                if (uri != null) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            contentResolver.openOutputStream(uri)?.use { outputStream ->
                                val backupJson = repository.exportBackupJson()
                                outputStream.write(backupJson.toByteArray())
                            }
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(this@MainActivity, "Backup saved successfully", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(this@MainActivity, "Failed to save backup: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }

            // Launcher to restore library backup JSON via SAF OpenDocument
            val restoreBackupLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val jsonString = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            if (!jsonString.isNullOrBlank()) {
                                val success = repository.importBackupJson(jsonString)
                                withContext(Dispatchers.Main) {
                                    if (success) {
                                        android.widget.Toast.makeText(this@MainActivity, "Library restored successfully", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        android.widget.Toast.makeText(this@MainActivity, "Invalid backup file", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(this@MainActivity, "Error reading backup file", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            
            val prefs by repository.preferences.collectAsState()

            LumaReaderTheme(
                themeMode = prefs.themeMode,
                dayTheme = prefs.dayThemeMode,
                nightTheme = prefs.nightThemeMode
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        repository = repository,
                        onImportBookClick = {
                            showImportActionSheet = true
                        },
                        isSyncing = isSyncing,
                        syncEmail = syncEmail,
                        lastSyncResult = lastSyncResult,
                        syncProgress = syncProgress,
                        onExportBackupToFile = {
                            exportBackupLauncher.launch("luma-reader-backup-${System.currentTimeMillis()}.json")
                        },
                        onRestoreBackupFromFile = {
                            restoreBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        },
                        onConnectSync = {
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        },
                        onDisconnectSync = {
                            googleSignInClient.signOut().addOnCompleteListener {
                                scope.launch {
                                    syncManager.disconnect()
                                    val currentPrefs = repository.preferences.value
                                    repository.savePreferences(
                                        currentPrefs.copy(
                                            syncPrefs = currentPrefs.syncPrefs.copy(connectedEmail = null)
                                        )
                                    )
                                }
                            }
                        },
                        onTriggerSync = { scopeOption ->
                            scope.launch {
                                syncManager.triggerSync(filesDir.absolutePath, repository, scopeOption)
                            }
                        },
                        onSyncClick = {
                            if (syncEmail != null) {
                                scope.launch {
                                    syncManager.triggerSync(filesDir.absolutePath, repository, prefs.syncPrefs.syncScope)
                                }
                            } else {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            }
                        }
                    )

                    if (showImportActionSheet) {
                        ImportActionBottomSheet(
                            onDismiss = { showImportActionSheet = false },
                            onScanFolderClick = {
                                folderPickerLauncher.launch(null)
                            },
                            onPickFilesClick = {
                                importLauncher.launch("application/epub+zip")
                            }
                        )
                    }

                    if (isScanningFolder) {
                        ScanningFolderDialog(
                            scanProgressText = scanProgressText,
                            onCancel = {
                                scanJob?.cancel()
                                scanJob = null
                                isScanningFolder = false
                            }
                        )
                    }

                    discoveredEpubs?.let { epubs ->
                        MassImportBottomSheet(
                            discoveredBooks = epubs,
                            isImporting = isImportingBatch,
                            importProgress = batchImportProgress,
                            currentImportingTitle = batchImportCurrentBook,
                            onDismiss = {
                                if (!isImportingBatch) {
                                    discoveredEpubs = null
                                }
                            },
                            onStartImport = { selectedItems ->
                                isImportingBatch = true
                                scope.launch {
                                    try {
                                        val total = selectedItems.size
                                        val booksDir = File(filesDir, "books").apply { mkdirs() }
                                        selectedItems.forEachIndexed { index, item ->
                                            batchImportCurrentBook = item.fileName
                                            batchImportProgress = index / total.toFloat()
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    val docUri = Uri.parse(item.uriString)
                                                    contentResolver.openInputStream(docUri)?.use { inputStream ->
                                                        val destFile = getNonCollidingFile(booksDir, item.fileName)
                                                        destFile.outputStream().use { outputStream ->
                                                            inputStream.copyTo(outputStream)
                                                        }
                                                        repository.importBook(destFile.absolutePath)
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                            batchImportProgress = (index + 1) / total.toFloat()
                                        }
                                    } finally {
                                        isImportingBatch = false
                                        discoveredEpubs = null
                                    }
                                }
                            }
                        )
                    }

                    if (showSyncDiagnosticDialog != null) {
                        AlertDialog(
                            onDismissRequest = { showSyncDiagnosticDialog = null },
                            title = {
                                Text("Google Cloud Setup Required", fontWeight = FontWeight.Bold)
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        "Google Sign-In returned Developer Error (Code 10). To allow Google Drive access, register an Android OAuth 2.0 Client ID in your Google Cloud Console project.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Package: com.example.lumareader", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                            Text("Debug SHA-1:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            Text("13:70:78:CB:F8:9F:D8:47:FF:53:52:0D:3F:DD:3B:55:55:17:2B:45", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("SHA-1 Fingerprint", "13:70:78:CB:F8:9F:D8:47:FF:53:52:0D:3F:DD:3B:55:55:17:2B:45")
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(this@MainActivity, "SHA-1 copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Copy SHA-1")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSyncDiagnosticDialog = null }) {
                                    Text("Close")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val listener = com.example.lumareader.ui.reader.VolumeKeyNavigationManager.onVolumeKey
        if (listener != null) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (listener(true)) return true
                }
                android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (listener(false)) return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun ScanningFolderDialog(
    scanProgressText: String,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Scanning Local Folder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Text(
                text = if (scanProgressText.isNotBlank()) scanProgressText else "Searching recursively for .epub files across subfolders...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge
    )
}

private fun getNonCollidingFile(parentDir: File, fileName: String): File {
    val file = File(parentDir, fileName)
    if (!file.exists()) return file
    val dotIndex = fileName.lastIndexOf('.')
    val base = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
    val ext = if (dotIndex > 0) fileName.substring(dotIndex) else ""
    var counter = 1
    var candidate = File(parentDir, "${base}_$counter$ext")
    while (candidate.exists()) {
        counter++
        candidate = File(parentDir, "${base}_$counter$ext")
    }
    return candidate
}
