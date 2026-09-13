package com.example.lumareader.ui.utils

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberFontImportLauncher(onFontImported: (fontFileName: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val contentResolver = context.contentResolver
                    var displayName = "font_${System.currentTimeMillis()}.ttf"
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

                    val fontsDir = File(context.filesDir, "fonts").apply { mkdirs() }
                    val destFile = File(fontsDir, displayName)
                    contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        onFontImported(displayName)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    return {
        launcher.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-opentype", "*/*"))
    }
}

@Composable
actual fun rememberFontDeleter(): (fontFileName: String) -> Boolean {
    val context = LocalContext.current
    return androidx.compose.runtime.remember(context) {
        { fontFileName ->
            try {
                val file = File(context.filesDir, "fonts/$fontFileName")
                if (file.exists()) file.delete() else false
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}

