package com.example.lumareader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lumareader.data.model.LibrarySortOption
import com.example.lumareader.data.model.displayLabel
import com.example.lumareader.theme.GoogleSans

/**
 * Reusable modal bottom sheet primitive for selecting library sorting options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    currentSort: LibrarySortOption,
    onSortSelected: (LibrarySortOption) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Sort Library By",
                fontFamily = GoogleSans,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LibrarySortOption.entries.forEach { option ->
                val isSelected = currentSort == option
                SortOptionItem(
                    option = option,
                    selected = isSelected,
                    onClick = {
                        onSortSelected(option)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
fun SortOptionItem(
    option: LibrarySortOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val icon = when (option) {
        LibrarySortOption.TITLE_ASC -> Icons.Default.SortByAlpha
        LibrarySortOption.TITLE_DESC -> Icons.Default.SortByAlpha
        LibrarySortOption.AUTHOR_ASC -> Icons.Default.Person
        LibrarySortOption.AUTHOR_DESC -> Icons.Default.Person
        LibrarySortOption.RECENT -> Icons.Default.Schedule
        LibrarySortOption.PROGRESS -> Icons.AutoMirrored.Filled.TrendingUp
    }

    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "sortItemContainerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "sortItemContentColor"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        if (option == LibrarySortOption.TITLE_DESC || option == LibrarySortOption.AUTHOR_DESC) {
                            scaleY = -1f
                        }
                    }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = option.displayLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
