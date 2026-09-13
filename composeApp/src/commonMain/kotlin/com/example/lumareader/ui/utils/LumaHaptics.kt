package com.example.lumareader.ui.utils

import androidx.compose.runtime.Composable

enum class LumaHapticFeedbackType {
    PAGE_TURN,
    SEGMENT_TICK,
    LONG_PRESS,
    TAP
}

interface LumaHaptics {
    fun perform(type: LumaHapticFeedbackType)
}

@Composable
expect fun rememberLumaHaptics(enabled: Boolean = true): LumaHaptics
