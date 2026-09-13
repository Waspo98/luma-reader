package com.example.lumareader.ui.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.runtime.*
import com.example.lumareader.ui.components.LumaTextInputDialog

@Composable
fun CreateShelfDialog(
    existingShelves: List<String>,
    onDismiss: () -> Unit,
    onCreateShelf: (String) -> Unit
) {
    var shelfName by remember { mutableStateOf("") }
    val isDuplicate = remember(shelfName, existingShelves) {
        existingShelves.any { it.equals(shelfName.trim(), ignoreCase = true) }
    }
    val isValid = shelfName.trim().isNotBlank() && !isDuplicate

    LumaTextInputDialog(
        title = "New Bookshelf",
        description = "Create a custom collection to organize your books.",
        initialValue = shelfName,
        label = "Shelf Name",
        placeholder = "e.g. Sci-Fi, Favorites, Summer 2026",
        icon = Icons.Default.BookmarkAdd,
        confirmText = "Create Shelf",
        dismissText = "Cancel",
        errorMessage = if (isDuplicate) "A shelf with this name already exists" else null,
        isValid = isValid,
        onValueChange = { shelfName = it },
        onConfirm = {
            onCreateShelf(it)
            onDismiss()
        },
        onDismiss = onDismiss
    )
}
