package com.example.lumareader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.lumareader.data.model.Book
import com.example.lumareader.theme.GoogleSans
import com.example.lumareader.ui.components.BookCover
import com.example.lumareader.ui.components.LumaSectionCard

@Composable
fun EditBookDialog(
    book: Book,
    availableShelves: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSaveBook: (Book) -> Unit
) {
    var title by remember { mutableStateOf(book.title) }
    var subtitle by remember { mutableStateOf(book.subtitle ?: "") }
    var author by remember { mutableStateOf(book.author) }
    var series by remember { mutableStateOf(book.series ?: "") }
    var seriesNumber by remember { mutableStateOf(book.seriesNumber?.let { if (it % 1.0f == 0f) it.toInt().toString() else it.toString() } ?: "") }
    var publisher by remember { mutableStateOf(book.publisher ?: "") }
    var publishDate by remember { mutableStateOf(book.publishDate ?: "") }
    var language by remember { mutableStateOf(book.language ?: "") }
    var isbn by remember { mutableStateOf(book.isbn ?: "") }
    var pageCount by remember { mutableStateOf(book.pageCount?.toString() ?: "") }
    var description by remember { mutableStateOf(book.description ?: "") }
    var subjectsStr by remember { mutableStateOf(book.subjects.joinToString(", ")) }
    var selectedCollections by remember { mutableStateOf(book.collections.toSet()) }
    val customCoverPath by remember { mutableStateOf(book.customCoverPath) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Edit Book Metadata",
                            fontFamily = GoogleSans,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Update details, series, publication info, and synopsis",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Scrollable Form
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cover Preview & Core Identification
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BookCover(
                            book = book,
                            showProgress = false,
                            modifier = Modifier
                                .width(90.dp)
                                .aspectRatio(0.7f)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Title *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = subtitle,
                                onValueChange = { subtitle = it },
                                label = { Text("Subtitle") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("Author *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Series Section Card
                    LumaSectionCard(
                        title = "Series Information",
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = series,
                                onValueChange = { series = it },
                                label = { Text("Series Name") },
                                singleLine = true,
                                modifier = Modifier.weight(0.7f)
                            )
                            OutlinedTextField(
                                value = seriesNumber,
                                onValueChange = { seriesNumber = it },
                                label = { Text("Book #") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(0.3f)
                            )
                        }
                    }

                    // Publication & Identification Section Card
                    LumaSectionCard(
                        title = "Publication & Identifiers",
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = publisher,
                                onValueChange = { publisher = it },
                                label = { Text("Publisher") },
                                singleLine = true,
                                modifier = Modifier.weight(0.5f)
                            )
                            OutlinedTextField(
                                value = publishDate,
                                onValueChange = { publishDate = it },
                                label = { Text("Publish Date (e.g. 2026)") },
                                singleLine = true,
                                modifier = Modifier.weight(0.5f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = language,
                                onValueChange = { language = it },
                                label = { Text("Language (e.g. EN-US)") },
                                singleLine = true,
                                modifier = Modifier.weight(0.35f)
                            )
                            OutlinedTextField(
                                value = isbn,
                                onValueChange = { isbn = it },
                                label = { Text("ISBN") },
                                singleLine = true,
                                modifier = Modifier.weight(0.4f)
                            )
                            OutlinedTextField(
                                value = pageCount,
                                onValueChange = { pageCount = it },
                                label = { Text("Pages") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(0.25f)
                            )
                        }
                    }

                    // Bookshelves / Collections
                    val allKnownShelves = remember(availableShelves, selectedCollections) {
                        (availableShelves + selectedCollections).distinct()
                    }
                    if (allKnownShelves.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Bookshelves & Collections",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                allKnownShelves.forEach { shelf ->
                                    val isSelected = selectedCollections.contains(shelf)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedCollections = if (isSelected) {
                                                selectedCollections - shelf
                                            } else {
                                                selectedCollections + shelf
                                            }
                                        },
                                        label = { Text(shelf) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Bookmark,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Genres / Tags
                    OutlinedTextField(
                        value = subjectsStr,
                        onValueChange = { subjectsStr = it },
                        label = { Text("Genres & Tags (comma-separated)") },
                        singleLine = true,
                        placeholder = { Text("Fantasy, Epic, Science Fiction") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Synopsis / Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Synopsis / Blurb") },
                        minLines = 4,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val parsedSeriesNum = seriesNumber.toFloatOrNull()
                            val parsedPageCount = pageCount.toIntOrNull()
                            val parsedSubjects = subjectsStr
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }

                            val updatedBook = book.copy(
                                title = title.ifBlank { book.title },
                                subtitle = subtitle.takeIf { it.isNotBlank() },
                                author = author.ifBlank { book.author },
                                series = series.takeIf { it.isNotBlank() },
                                seriesNumber = parsedSeriesNum,
                                publisher = publisher.takeIf { it.isNotBlank() },
                                publishDate = publishDate.takeIf { it.isNotBlank() },
                                language = language.takeIf { it.isNotBlank() },
                                isbn = isbn.takeIf { it.isNotBlank() },
                                pageCount = parsedPageCount,
                                description = description.takeIf { it.isNotBlank() },
                                subjects = parsedSubjects,
                                collections = selectedCollections.toList(),
                                customCoverPath = customCoverPath
                            )
                            onSaveBook(updatedBook)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

