package com.example.lumareader.data

import com.example.lumareader.data.model.ColumnLayoutMode
import com.example.lumareader.data.model.PageNavigationStyle
import com.example.lumareader.data.model.ReadingPreferences
import com.example.lumareader.data.model.resolveDualColumns
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadingPreferencesTest {

    @Test
    fun testContinuousScroll_alwaysSingleColumn_evenWhenFoldableUnfolded() {
        val prefsAuto = ReadingPreferences(
            navigationStyle = PageNavigationStyle.CONTINUOUS_SCROLL,
            columnLayoutMode = ColumnLayoutMode.AUTO
        )
        assertFalse(prefsAuto.resolveDualColumns(isFoldableOrWide = true))
        assertFalse(prefsAuto.resolveDualColumns(isFoldableOrWide = false))

        val prefsDual = ReadingPreferences(
            navigationStyle = PageNavigationStyle.CONTINUOUS_SCROLL,
            columnLayoutMode = ColumnLayoutMode.DUAL
        )
        assertFalse(prefsDual.resolveDualColumns(isFoldableOrWide = true))
        assertFalse(prefsDual.resolveDualColumns(isFoldableOrWide = false))

        val prefsSingle = ReadingPreferences(
            navigationStyle = PageNavigationStyle.CONTINUOUS_SCROLL,
            columnLayoutMode = ColumnLayoutMode.SINGLE
        )
        assertFalse(prefsSingle.resolveDualColumns(isFoldableOrWide = true))
        assertFalse(prefsSingle.resolveDualColumns(isFoldableOrWide = false))
    }

    @Test
    fun testHorizontalSlide_autoMode_foldableUnfoldedVsFolded() {
        val prefs = ReadingPreferences(
            navigationStyle = PageNavigationStyle.HORIZONTAL_SLIDE,
            columnLayoutMode = ColumnLayoutMode.AUTO
        )
        assertTrue(prefs.resolveDualColumns(isFoldableOrWide = true))
        assertFalse(prefs.resolveDualColumns(isFoldableOrWide = false))
    }

    @Test
    fun testPageTurn_autoMode_foldableUnfoldedVsFolded() {
        val prefs = ReadingPreferences(
            navigationStyle = PageNavigationStyle.PAGE_TURN,
            columnLayoutMode = ColumnLayoutMode.AUTO
        )
        assertTrue(prefs.resolveDualColumns(isFoldableOrWide = true))
        assertFalse(prefs.resolveDualColumns(isFoldableOrWide = false))
    }

    @Test
    fun testInstant_autoMode_foldableUnfoldedVsFolded() {
        val prefs = ReadingPreferences(
            navigationStyle = PageNavigationStyle.INSTANT,
            columnLayoutMode = ColumnLayoutMode.AUTO
        )
        assertTrue(prefs.resolveDualColumns(isFoldableOrWide = true))
        assertFalse(prefs.resolveDualColumns(isFoldableOrWide = false))
    }

    @Test
    fun testPaginatedStyles_forcedSingleMode() {
        val paginatedStyles = listOf(
            PageNavigationStyle.HORIZONTAL_SLIDE,
            PageNavigationStyle.PAGE_TURN,
            PageNavigationStyle.INSTANT
        )
        for (style in paginatedStyles) {
            val prefs = ReadingPreferences(
                navigationStyle = style,
                columnLayoutMode = ColumnLayoutMode.SINGLE
            )
            assertFalse(prefs.resolveDualColumns(isFoldableOrWide = true), "Style $style should be single column in SINGLE mode")
            assertFalse(prefs.resolveDualColumns(isFoldableOrWide = false), "Style $style should be single column in SINGLE mode")
        }
    }

    @Test
    fun testPaginatedStyles_forcedDualMode() {
        val paginatedStyles = listOf(
            PageNavigationStyle.HORIZONTAL_SLIDE,
            PageNavigationStyle.PAGE_TURN,
            PageNavigationStyle.INSTANT
        )
        for (style in paginatedStyles) {
            val prefs = ReadingPreferences(
                navigationStyle = style,
                columnLayoutMode = ColumnLayoutMode.DUAL
            )
            assertTrue(prefs.resolveDualColumns(isFoldableOrWide = true), "Style $style should be dual column in DUAL mode")
            assertTrue(prefs.resolveDualColumns(isFoldableOrWide = false), "Style $style should be dual column in DUAL mode")
        }
    }
}
