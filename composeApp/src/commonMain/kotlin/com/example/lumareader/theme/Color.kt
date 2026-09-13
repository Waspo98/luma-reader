package com.example.lumareader.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Bright White Light Theme (Crisp, modern, high-contrast)
// ==========================================
val LightPrimary = Color(0xFFD45D42)       // Rich Warm Terracotta
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFFDECE9)
val LightOnPrimaryContainer = Color(0xFF3F140A)
val LightSecondary = Color(0xFFE2E7EC)
val LightOnSecondary = Color(0xFF1E252B)
val LightBackground = Color(0xFFFFFFFF)    // Crisp clean pure white
val LightOnBackground = Color(0xFF121212)  // High-contrast deep charcoal text
val LightSurface = Color(0xFFF8F9FA)       // Soft clean white surface
val LightOnSurface = Color(0xFF121212)
val LightOutline = Color(0xFFE5E7EB)       // Soft modern divider

val LightSurfaceContainer = Color(0xFFF3F4F6)       // Clean neutral container
val LightSurfaceContainerLow = Color(0xFFF9FAFB)
val LightSurfaceContainerHigh = Color(0xFFE5E7EB)

// ==========================================
// Cool Slate Gray Theme (Extremely restful dark mode)
// ==========================================
val SlatePrimary = Color(0xFF00A896)       // Rich Mint Teal
val SlateOnPrimary = Color(0xFF0C1D1A)
val SlatePrimaryContainer = Color(0xFF133632)
val SlateOnPrimaryContainer = Color(0xFF86F2E5)
val SlateSecondary = Color(0xFF2A313C)     // Restful blue-gray
val SlateOnSecondary = Color(0xFFE2E8F0)
val SlateBackground = Color(0xFF1C2025)    // Smooth dark slate-charcoal
val SlateOnBackground = Color(0xFFE2E8F0)
val SlateSurface = Color(0xFF242A31)       // Slightly lighter slate surface
val SlateOnSurface = Color(0xFFE2E8F0)
val SlateOutline = Color(0xFF3A4556)       // Dark divider

val SlateSurfaceContainer = Color(0xFF2C333C)
val SlateSurfaceContainerLow = Color(0xFF262D35)
val SlateSurfaceContainerHigh = Color(0xFF333B45)

// ==========================================
// AMOLED Black Theme (Pitch Black power-saver)
// ==========================================
val AmoledPrimary = Color(0xFFBB86FC)      // Electric Lavender Violet
val AmoledOnPrimary = Color(0xFF1F003C)
val AmoledPrimaryContainer = Color(0xFF3C0075)
val AmoledOnPrimaryContainer = Color(0xFFF2E6FF)
val AmoledSecondary = Color(0xFF1A1A2E)    // Deep Indigo-Gray
val AmoledOnSecondary = Color(0xFFF3F4F6)
val AmoledBackground = Color(0xFF000000)   // Absolute Black
val AmoledOnBackground = Color(0xFFF3F4F6)
val AmoledSurface = Color(0xFF0E0E12)      // Deep Onyx Black surface
val AmoledOnSurface = Color(0xFFF3F4F6)
val AmoledOutline = Color(0xFF2A2A38)      // Dark graphite divider

val AmoledSurfaceContainer = Color(0xFF141418)
val AmoledSurfaceContainerLow = Color(0xFF0A0A0E)
val AmoledSurfaceContainerHigh = Color(0xFF1E1E24)

// ==========================================
// Warm Sepia / Paper Theme (Classic rich amber/warm parchment)
// ==========================================
val SepiaPrimary = Color(0xFF9E4812)       // Rich Warm Amber Ochre
val SepiaOnPrimary = Color(0xFFFFFFFF)
val SepiaPrimaryContainer = Color(0xFFF6E2CD)
val SepiaOnPrimaryContainer = Color(0xFF381A05)
val SepiaSecondary = Color(0xFFDCC8AA)     // Antique book cloth
val SepiaOnSecondary = Color(0xFF352617)
val SepiaBackground = Color(0xFFF4ECD8)    // Authentic warm sepia paper (distinctly warm amber)
val SepiaOnBackground = Color(0xFF38291B)  // Rich deep espresso ink
val SepiaSurface = Color(0xFFEFE5CF)       // Deepened parchment surface
val SepiaOnSurface = Color(0xFF38291B)
val SepiaOutline = Color(0xFFD8CAA7)       // Warm parchment divider

val SepiaSurfaceContainer = Color(0xFFEADBC0)
val SepiaSurfaceContainerLow = Color(0xFFEFE5CF)
val SepiaSurfaceContainerHigh = Color(0xFFE2D1B2)

/**
 * Safely parses a hex color string (e.g. "#RRGGBB", "RRGGBB", "#AARRGGBB", or "AARRGGBB")
 * into a Compose [Color], returning [fallback] if parsing fails.
 */
fun String?.toComposeColor(fallback: Color = LightPrimary): Color {
    if (this.isNullOrBlank()) return fallback
    return try {
        val clean = this.removePrefix("#").trim()
        val parsed = clean.toLong(16)
        when (clean.length) {
            6 -> Color(0xFF000000 or parsed)
            8 -> Color(parsed)
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}
