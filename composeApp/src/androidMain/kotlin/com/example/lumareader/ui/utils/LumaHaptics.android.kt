package com.example.lumareader.ui.utils

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

fun performLumaHaptic(view: View?, type: LumaHapticFeedbackType) {
    if (view == null) return
    try {
        val constant = when (type) {
            LumaHapticFeedbackType.PAGE_TURN -> HapticFeedbackConstants.KEYBOARD_TAP
            LumaHapticFeedbackType.SEGMENT_TICK -> HapticFeedbackConstants.CLOCK_TICK
            LumaHapticFeedbackType.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
            LumaHapticFeedbackType.TAP -> HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(
            constant,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    } catch (_: Exception) {}
}

@Composable
actual fun rememberLumaHaptics(enabled: Boolean): LumaHaptics {
    val view = LocalView.current
    return remember(view, enabled) {
        object : LumaHaptics {
            override fun perform(type: LumaHapticFeedbackType) {
                if (!enabled) return
                performLumaHaptic(view, type)
            }
        }
    }
}
