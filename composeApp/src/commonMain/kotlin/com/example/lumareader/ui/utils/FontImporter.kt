package com.example.lumareader.ui.utils

import androidx.compose.runtime.Composable

/**
 * Platform launcher hook to import TTF/OTF custom fonts from device storage.
 * Returns a lambda that triggers the system file picker.
 */
@Composable
expect fun rememberFontImportLauncher(onFontImported: (fontFileName: String) -> Unit): () -> Unit

/**
 * Platform hook to physically delete an imported font file from internal storage.
 */
@Composable
expect fun rememberFontDeleter(): (fontFileName: String) -> Boolean

