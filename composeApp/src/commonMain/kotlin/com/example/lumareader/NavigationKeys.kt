package com.example.lumareader

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data class Reader(val bookId: String) : NavKey
@Serializable data object Settings : NavKey
@Serializable data class FilteredLibrary(val filterType: String, val filterValue: String) : NavKey
