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

@Composable
fun LumaReaderTheme(
    themeMode: LumaThemeMode = LumaThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        LumaThemeMode.LIGHT -> LightColorScheme
        LumaThemeMode.SLATE_GRAY -> SlateColorScheme
        LumaThemeMode.AMOLED_BLACK -> AmoledColorScheme
        LumaThemeMode.SYSTEM -> {
            if (darkTheme) SlateColorScheme else LightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
