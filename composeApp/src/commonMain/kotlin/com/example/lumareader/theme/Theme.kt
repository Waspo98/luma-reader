package com.example.lumareader.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.lumareader.data.model.LumaThemeMode

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    outline = LightOutline,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainerHigh = LightSurfaceContainerHigh
)

private val SlateColorScheme = darkColorScheme(
    primary = SlatePrimary,
    onPrimary = SlateOnPrimary,
    primaryContainer = SlatePrimaryContainer,
    onPrimaryContainer = SlateOnPrimaryContainer,
    secondary = SlateSecondary,
    onSecondary = SlateOnSecondary,
    background = SlateBackground,
    onBackground = SlateOnBackground,
    surface = SlateSurface,
    onSurface = SlateOnSurface,
    outline = SlateOutline,
    surfaceContainer = SlateSurfaceContainer,
    surfaceContainerLow = SlateSurfaceContainerLow,
    surfaceContainerHigh = SlateSurfaceContainerHigh
)

private val AmoledColorScheme = darkColorScheme(
    primary = AmoledPrimary,
    onPrimary = AmoledOnPrimary,
    primaryContainer = AmoledPrimaryContainer,
    onPrimaryContainer = AmoledOnPrimaryContainer,
    secondary = AmoledSecondary,
    onSecondary = AmoledOnSecondary,
    background = AmoledBackground,
    onBackground = AmoledOnBackground,
    surface = AmoledSurface,
    onSurface = AmoledOnSurface,
    outline = AmoledOutline,
    surfaceContainer = AmoledSurfaceContainer,
    surfaceContainerLow = AmoledSurfaceContainerLow,
    surfaceContainerHigh = AmoledSurfaceContainerHigh
)

private val SepiaColorScheme = lightColorScheme(
    primary = SepiaPrimary,
    onPrimary = SepiaOnPrimary,
    primaryContainer = SepiaPrimaryContainer,
    onPrimaryContainer = SepiaOnPrimaryContainer,
    secondary = SepiaSecondary,
    onSecondary = SepiaOnSecondary,
    background = SepiaBackground,
    onBackground = SepiaOnBackground,
    surface = SepiaSurface,
    onSurface = SepiaOnSurface,
    outline = SepiaOutline,
    surfaceContainer = SepiaSurfaceContainer,
    surfaceContainerLow = SepiaSurfaceContainerLow,
    surfaceContainerHigh = SepiaSurfaceContainerHigh
)

@Composable
fun LumaReaderTheme(
    themeMode: LumaThemeMode = LumaThemeMode.SYSTEM,
    dayTheme: LumaThemeMode = LumaThemeMode.LIGHT,
    nightTheme: LumaThemeMode = LumaThemeMode.SLATE_GRAY,
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val activeMode = when (themeMode) {
        LumaThemeMode.SYSTEM -> if (darkTheme) nightTheme else dayTheme
        else -> themeMode
    }
    val colorScheme = when (activeMode) {
        LumaThemeMode.LIGHT -> LightColorScheme
        LumaThemeMode.WARM_SEPIA -> SepiaColorScheme
        LumaThemeMode.SLATE_GRAY -> SlateColorScheme
        LumaThemeMode.AMOLED_BLACK -> AmoledColorScheme
        LumaThemeMode.SYSTEM -> if (darkTheme) SlateColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
