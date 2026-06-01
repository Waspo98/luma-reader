package com.example.lumareader.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.lumareader.data.model.Book
import com.example.lumareader.ui.utils.loadCoverImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun EditBookDialog(
    book: Book,
    onDismiss: () -> Unit,
    onSave: (bookId: String, title: String?, author: String?, series: String?, seriesNumber: Float?, customCoverPath: String?) -> Unit
) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var series by remember { mutableStateOf(book.series ?: "") }
    var seriesNumber by remember { mutableStateOf(book.seriesNumber?.toString() ?: "") }
    
    // In a real app, clicking the cover would launch a file picker.
    // We are leaving the parameter `customCoverPath` as null for now unless implemented via an actual picker callback.
    val customCoverPath by remember { mutableStateOf(book.customCoverPath) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Edit Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Cover Image section
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        var coverImage by remember(book.id, book.coverPath, customCoverPath) {
                            mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
                        }
                        LaunchedEffect(book.id, book.coverPath, customCoverPath) {
                            val path = customCoverPath ?: book.coverPath
                            coverImage = if (path != null) {
                                withContext(Dispatchers.IO) {
                                    if (customCoverPath != null) loadCoverImage(path)
                                    else loadCoverImage("${book.unzippedDir}/$path")
                                }
                            } else null
                        }

                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .aspectRatio(0.7f)
                                .shadow(4.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable {
                                    // TODO: Launch image picker
                                }
                        ) {
                            if (coverImage != null) {
                                Image(
                                    bitmap = coverImage!!,
                                    contentDescription = "Cover",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    Icons.Default.Book,
                                    contentDescription = null,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("Author") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = series,
                        onValueChange = { series = it },
                        label = { Text("Series (Optional)") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = seriesNumber,
                        onValueChange = { seriesNumber = it },
                        label = { Text("Series Number (Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Cancel")
                    }
                    Button(onClick = {
                        val parsedSeriesNum = seriesNumber.toFloatOrNull()
                        onSave(
                            book.id,
                            title.takeIf { it.isNotBlank() },
                            author.takeIf { it.isNotBlank() },
                            series.takeIf { it.isNotBlank() },
                            parsedSeriesNum,
                            customCoverPath
                        )
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
