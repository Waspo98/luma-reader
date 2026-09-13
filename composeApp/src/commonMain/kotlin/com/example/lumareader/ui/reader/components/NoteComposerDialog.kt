package com.example.lumareader.ui.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lumareader.data.model.AnnotationStyle
import com.example.lumareader.theme.GoogleSans
import com.example.lumareader.theme.toComposeColor
import com.example.lumareader.ui.components.AnnotationStyleSelector
import com.example.lumareader.ui.components.HighlightColorSelector

@Composable
fun NoteComposerDialog(
    quoteText: String,
    initialNote: String,
    initialColorHex: String,
    initialStyle: AnnotationStyle,
    isExisting: Boolean,
    onSave: (note: String, colorHex: String, style: AnnotationStyle) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var noteText by remember { mutableStateOf(initialNote) }
    var selectedColorHex by remember { mutableStateOf(initialColorHex) }
    var selectedStyle by remember { mutableStateOf(initialStyle) }

    val currentColor = remember(selectedColorHex) {
        selectedColorHex.toComposeColor(fallback = Color(0xFFFFE082))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxWidth(0.92f),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = if (isExisting) "Edit Note & Highlight" else "Add Note",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Quoted Passage Preview
                if (quoteText.isNotBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.5.dp)
                                    .height(44.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(currentColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "\"$quoteText\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Note Input
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = {
                        Text(
                            "Write your thoughts, annotations, or reflections...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                // Highlight Color Palette
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Highlight Color",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HighlightColorSelector(
                        selectedColorHex = selectedColorHex,
                        onColorSelected = { selectedColorHex = it }
                    )
                }

                // Annotation Style Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Style",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AnnotationStyleSelector(
                        selectedStyle = selectedStyle,
                        onStyleSelected = { selectedStyle = it },
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(noteText.trim(), selectedColorHex, selectedStyle) },
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Save",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isExisting && onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Annotation",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                TextButton(
                    onClick = onDismiss,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Cancel", fontFamily = GoogleSans)
                }
            }
        }
    )
}
