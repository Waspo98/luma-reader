package com.example.lumareader.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

/**
 * Expressive progress bar primitive with physics-based animation and ambient glow.
 */
@Composable
fun LumaProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "progressBar"
    )

    Canvas(
        modifier = modifier.fillMaxWidth()
    ) {
        val width = size.width
        val height = size.height
        val strokeWidth = height
        val radius = strokeWidth / 2f

        // 1. Draw track
        drawLine(
            color = trackColor,
            start = Offset(radius, radius),
            end = Offset(width - radius, radius),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        if (animatedProgress > 0f) {
            val progressWidth = radius + (width - 2 * radius) * animatedProgress

            // 2. Draw glow (wider, semi-translucent line)
            drawLine(
                color = accentColor.copy(alpha = 0.3f),
                start = Offset(radius, radius),
                end = Offset(progressWidth, radius),
                strokeWidth = strokeWidth * 1.6f,
                cap = StrokeCap.Round
            )

            // 3. Draw actual filled progress bar
            drawLine(
                color = accentColor,
                start = Offset(radius, radius),
                end = Offset(progressWidth, radius),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
