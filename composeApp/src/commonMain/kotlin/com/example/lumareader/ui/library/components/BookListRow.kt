package com.example.lumareader.ui.library.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lumareader.data.model.Book
import com.example.lumareader.ui.components.BookCover
import com.example.lumareader.ui.components.LumaProgressBar

/**
 * Expressive list row item for displaying books in the library list view.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListRow(
    book: Book,
    onClick: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelectedInBatch: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onAuthorClick: () -> Unit = {},
    onSeriesClick: () -> Unit = {},
    showProgressBadges: Boolean = true,
    showSeriesBadges: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else if (isSelectedInBatch) 1.01f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "rowScale"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else if (isSelectedInBatch) 4.dp else 1.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "rowElevation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = {
                    if (isSelectionMode) onToggleSelection()
                    else onLongClick()
                }
            ),
        shape = MaterialTheme.shapes.medium,
        border = if (isSelectedInBatch) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCover(
                book = book,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .width(60.dp)
                    .aspectRatio(0.7f)
                    .clip(MaterialTheme.shapes.small)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = book.author,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onAuthorClick() }
                )
                if (showSeriesBadges && !book.series.isNullOrBlank()) {
                    val seriesText = if (book.seriesNumber != null) {
                        "${book.series} #${if (book.seriesNumber % 1.0f == 0f) book.seriesNumber.toInt() else book.seriesNumber}"
                    } else book.series
                    Text(
                        text = seriesText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onSeriesClick() }
                    )
                }
                
                if (showProgressBadges) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val progress = book.overallProgress()
                    LumaProgressBar(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isSelectionMode) {
                Checkbox(
                    checked = isSelectedInBatch,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.padding(end = 4.dp)
                )
            } else {
                val percent = (book.overallProgress() * 100).toInt()
                if (showProgressBadges && percent > 0) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
