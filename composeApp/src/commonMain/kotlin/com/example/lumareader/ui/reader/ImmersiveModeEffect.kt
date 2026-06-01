package com.example.lumareader.ui.reader

import androidx.compose.runtime.Composable

/**
 * Screen-level effect that manages immersive mode (hiding/showing system bars).
 * Called once at the ReaderScreen level, NOT inside individual pager pages.
 *
 * Hiding inside each pager page was incorrect: HorizontalPager disposes pages as they scroll
 * off-screen, which triggered the onDispose "show bars" callback on every chapter change.
 *
 * @param isUiVisible When true, system bars are shown; when false, they are hidden.
 * @param extendBehindNotch When true, content is allowed to draw behind the display cutout.
 */
@Composable
expect fun ImmersiveModeEffect(isUiVisible: Boolean, extendBehindNotch: Boolean)
