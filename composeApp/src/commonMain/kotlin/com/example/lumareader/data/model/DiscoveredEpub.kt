package com.example.lumareader.data.model

import kotlin.math.round
import kotlinx.serialization.Serializable

@Serializable
data class DiscoveredEpub(
    val uriString: String,
    val fileName: String,
    val relativePath: String,
    val sizeBytes: Long,
    val isAlreadyInLibrary: Boolean = false,
    val isSelected: Boolean = !isAlreadyInLibrary
) {
    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "Unknown size"
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> "${round(gb * 10) / 10.0} GB"
                mb >= 1.0 -> "${round(mb * 10) / 10.0} MB"
                kb >= 1.0 -> "${round(kb * 10) / 10.0} KB"
                else -> "$sizeBytes B"
            }
        }
}
