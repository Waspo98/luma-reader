package com.example.lumareader.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Expressive filter chip primitive adhering to Material Design 3 (M3E).
 * Used for library category tabs, status filters, and shelf selectors.
 */
@Composable
fun FilterPill(
    selected: Boolean,
    label: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    count: Int? = null,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val displayText = if (count != null && count >= 0) "$label ($count)" else label

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        },
        trailingIcon = onClear?.let {
            {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear $label filter",
                    modifier = Modifier
                        .size(FilterChipDefaults.IconSize)
                        .clickable { it() }
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            selectedBorderColor = MaterialTheme.colorScheme.primary
        ),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.padding(vertical = 2.dp)
    )
}
