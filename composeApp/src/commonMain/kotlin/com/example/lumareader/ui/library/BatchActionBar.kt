package com.example.lumareader.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lumareader.theme.GoogleSans

@Composable
fun BatchActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onAssignShelfClick: () -> Unit,
    onMarkStatusClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCloseSelectionMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allSelected = selectedCount > 0 && selectedCount == totalCount

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Close button + count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onCloseSelectionMode,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel selection",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "$selectedCount selected",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Right: Batch Action buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Select All / Deselect All
                IconButton(
                    onClick = { if (allSelected) onDeselectAll() else onSelectAll() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                        contentDescription = if (allSelected) "Deselect all" else "Select all",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Add to Shelf
                IconButton(
                    onClick = onAssignShelfClick,
                    enabled = selectedCount > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = "Assign to shelf",
                        tint = if (selectedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                // Change Reading Status
                IconButton(
                    onClick = onMarkStatusClick,
                    enabled = selectedCount > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Mark reading status",
                        tint = if (selectedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                // Delete selected
                IconButton(
                    onClick = onDeleteClick,
                    enabled = selectedCount > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete selected books",
                        tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }
}
