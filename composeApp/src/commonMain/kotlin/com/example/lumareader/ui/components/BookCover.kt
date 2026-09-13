package com.example.lumareader.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lumareader.data.model.Book
import com.example.lumareader.ui.utils.loadCoverImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reusable primitive for rendering book covers across the library, search, and edit dialogs.
 * Handles downsampled image decoding, fallback art with serif typography, and progress overlay.
 */
@Composable
fun BookCover(
    book: Book,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    BookCover(
        bookId = book.id,
        title = book.title,
        coverPath = book.coverPath,
        customCoverPath = book.customCoverPath,
        unzippedDir = book.unzippedDir,
        progress = if (showProgress && book.spine.isNotEmpty()) {
            (book.currentSpineIndex.toFloat() + book.currentProgression) / book.spine.size.toFloat()
        } else 0f,
        modifier = modifier,
        showProgress = showProgress,
        shape = shape
    )
}

@Composable
fun BookCover(
    bookId: String,
    title: String,
    coverPath: String?,
    customCoverPath: String?,
    unzippedDir: String,
    progress: Float = 0f,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    var coverImage by remember(bookId, coverPath, customCoverPath) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(bookId, coverPath, customCoverPath) {
        val path = customCoverPath ?: coverPath
        coverImage = if (path != null) {
            withContext(Dispatchers.IO) {
                if (customCoverPath != null) {
                    loadCoverImage(path)
                } else {
                    loadCoverImage("$unzippedDir/$path")
                }
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        val currentCover = coverImage
        if (currentCover != null) {
            Image(
                bitmap = currentCover,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    if (title.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = MaterialTheme.colorScheme.onPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }

        if (showProgress && progress > 0f) {
            LumaProgressBar(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .align(Alignment.BottomCenter)
                    .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            )
        }
    }
}
