package com.example.lumareader.ui.reader

/**
 * Singleton manager to route hardware volume keys to reader page flips or smooth scrolls.
 * Only non-null and active when the user is reading in full-screen mode (UI toolbars hidden).
 */
object VolumeKeyNavigationManager {
    /**
     * Listener callback invoked on volume key press.
     * @param isNext true for KEYCODE_VOLUME_DOWN (next page/scroll down), false for KEYCODE_VOLUME_UP (prev page/scroll up).
     * @return true if the event was handled and should be consumed; false otherwise.
     */
    var onVolumeKey: ((isNext: Boolean) -> Boolean)? = null
}
