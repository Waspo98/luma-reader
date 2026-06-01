package com.example.lumareader.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Android actual for ImmersiveModeEffect.
 *
 * Manages the full lifecycle of system bar visibility for the reader:
 * - Hides bars when isUiVisible == false (reading mode).
 * - Shows bars when isUiVisible == true (UI visible).
 * - Restores bars unconditionally on disposal (i.e. when the reader is closed).
 *
 * This is intentionally placed at the ReaderScreen level so that the restore-on-dispose
 * only fires once when the screen leaves composition, NOT on every chapter swipe.
 */
@Composable
actual fun ImmersiveModeEffect(isUiVisible: Boolean, extendBehindNotch: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    LaunchedEffect(activity, isUiVisible, extendBehindNotch) {
        val window = activity?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Dynamically toggle cutout layout based on preference
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = if (extendBehindNotch) {
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
            window.attributes = lp
        }

        if (isUiVisible) {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Restore system bars when the reader screen exits composition.
    // This fires exactly once per reader session, not once per chapter.
    DisposableEffect(activity) {
        onDispose {
            val window = activity?.window ?: return@onDispose
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            // Also reset cutout mode back to the safe default
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val lp = window.attributes
                lp.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                window.attributes = lp
            }
        }
    }
}
