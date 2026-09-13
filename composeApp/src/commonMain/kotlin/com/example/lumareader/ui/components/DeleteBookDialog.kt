package com.example.lumareader.ui.components

import androidx.compose.runtime.Composable
import com.example.lumareader.data.model.Book

/**
 * Dialog for confirming book deletion using the standardized [LumaConfirmationDialog] primitive.
 */
@Composable
fun DeleteBookDialog(
    book: Book,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    LumaConfirmationDialog(
        title = "Delete '${book.title}'?",
        message = "This will remove the book from your library and delete its cached and downloaded data.",
        confirmText = "Delete",
        dismissText = "Cancel",
        isDestructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
