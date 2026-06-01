package com.example.lumareader.ui.utils

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lumareader.theme.GoogleSans

@Composable
fun LumaSlider(
    label: String,
    value: Float,
    onValueChangeFinished: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    valueFormatter: (Float) -> String = { it.toInt().toString() }
) {
    val haptic = LocalHapticFeedback.current
    var localValue by remember(value) { mutableStateOf(value) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontFamily = GoogleSans,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueFormatter(localValue),
                fontFamily = GoogleSans,
                fontSize = 13.sp,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = localValue.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { newVal ->
                val prevStep = getStepIndex(localValue, valueRange, steps)
                val currStep = getStepIndex(newVal, valueRange, steps)
                if (currStep != prevStep) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                localValue = newVal
            },
            onValueChangeFinished = {
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
