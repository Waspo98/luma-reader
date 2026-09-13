package com.example.lumareader.ui.utils

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lumareader.theme.GoogleSans
import kotlin.math.abs

@Composable
fun LumaSlider(
    label: String,
    value: Float,
    onValueChangeFinished: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    defaultValue: Float? = null,
    onValueChange: ((Float) -> Unit)? = null,
    onDragStateChange: ((Boolean) -> Unit)? = null,
    isActiveDragging: Boolean = false,
    isAnyDragging: Boolean = false,
    valueFormatter: (Float) -> String = { it.toInt().toString() }
) {
    val haptic = rememberLumaHaptics()
    var localValue by remember(value) { mutableStateOf(value) }
    var isDragging by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (isAnyDragging && !isActiveDragging) 0f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "LumaSliderAlpha"
    )

    val containerModifier = if (isActiveDragging) {
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .then(containerModifier)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontFamily = GoogleSans,
                fontSize = 13.sp,
                fontWeight = if (isActiveDragging) FontWeight.Bold else FontWeight.Medium,
                color = if (isActiveDragging) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = valueFormatter(localValue),
                    fontFamily = GoogleSans,
                    fontSize = 13.sp,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )

                if (defaultValue != null) {
                    val isModified = abs(localValue - defaultValue) > 0.0001f
                    IconButton(
                        onClick = {
                            localValue = defaultValue
                            haptic.perform(LumaHapticFeedbackType.TAP)
                            onValueChange?.invoke(defaultValue)
                            onValueChangeFinished(defaultValue)
                        },
                        enabled = isModified,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Reset $label to default",
                            tint = if (isModified) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
        Slider(
            value = localValue.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { newVal ->
                if (!isDragging) {
                    isDragging = true
                    onDragStateChange?.invoke(true)
                }
                val prevStep = getStepIndex(localValue, valueRange, steps)
                val currStep = getStepIndex(newVal, valueRange, steps)
                if (currStep != prevStep) {
                    haptic.perform(LumaHapticFeedbackType.SEGMENT_TICK)
                }
                localValue = newVal
                onValueChange?.invoke(newVal)
            },
            onValueChangeFinished = {
                isDragging = false
                onDragStateChange?.invoke(false)
                onValueChangeFinished(localValue)
            },
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.24f)
            )
        )
    }
}

private fun getStepIndex(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Int {
    if (steps <= 0) return (value * 100).toInt() // continuous fallback
    val span = range.endInclusive - range.start
    val stepSize = span / (steps + 1)
    val relativeValue = value - range.start
    return (relativeValue / stepSize + 0.5f).toInt()
}
