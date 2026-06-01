package com.example.lumareader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.lumareader.data.LocalBookRepository
import com.example.lumareader.data.sync.CloudSyncManager
import com.example.lumareader.theme.LumaReaderTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            val repository = remember { LocalBookRepository(filesDir.absolutePath, cacheDir.absolutePath) }
            DisposableEffect(repository) {
                onDispose { repository.close() }
            }
            
            LaunchedEffect(Unit) {
                val autoFile = File(getExternalFilesDir(null), "import_test.epub")
                if (autoFile.exists()) {
                    try {
                        repository.importBook(autoFile.absolutePath)
                        autoFile.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            val syncManager = remember { CloudSyncManager() }
            val isSyncing by syncManager.isSyncing.collectAsState()
            val syncEmail by syncManager.connectedEmail.collectAsState()
            val scope = rememberCoroutineScope()
            
            // Standard launcher for system file picker to select multiple EPUB files
            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetMultipleContents()
            ) { uris ->
                if (!uris.isNullOrEmpty()) {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                uris.forEach { uri ->
                                    contentResolver.openInputStream(uri)?.use { inputStream ->
                                        var displayName = "import_${System.currentTimeMillis()}.epub"
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
                                        val tempFile = File(cacheDir, displayName)
                                        try {
                                            tempFile.outputStream().use { outputStream ->
                                                inputStream.copyTo(outputStream)
                                            }
                                            repository.importBook(tempFile.absolutePath)
                                        } finally {
                                            if (tempFile.exists()) {
                                                tempFile.delete()
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            
            val prefs by repository.preferences.collectAsState()

            LumaReaderTheme(themeMode = prefs.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        repository = repository,
                        onImportBookClick = {
                            // Request EPUB content type
                            importLauncher.launch("application/epub+zip")
                        },
                        isSyncing = isSyncing,
                        syncEmail = syncEmail,
                        onSyncClick = {
                            scope.launch {
                                if (syncEmail != null) {
                                    syncManager.disconnect()
                                } else {
                                    syncManager.connect()
                                    syncManager.triggerSync(filesDir.absolutePath)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
