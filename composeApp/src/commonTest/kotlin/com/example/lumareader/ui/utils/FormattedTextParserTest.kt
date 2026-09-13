package com.example.lumareader.ui.utils

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormattedTextParserTest {

    @Test
    fun testEmptyAndNull() {
        val emptyResult = parseFormattedText("")
        assertEquals("", emptyResult.text)
        val nullResult = parseFormattedText(null)
        assertEquals("", nullResult.text)
    }

    @Test
    fun testHtmlEntitiesDecoding() {
        val raw = "&quot;Hello &amp; Welcome&quot;&mdash;Enjoy&nbsp;&lt;Book&gt;"
        val result = parseFormattedText(raw)
        assertEquals("\"Hello & Welcome\"—Enjoy <Book>", result.text)
    }

    @Test
    fun testHtmlFormattingTags() {
        val raw = "<p><b>Bold Title</b></p><p>Normal text and <i>italic phrase</i>.</p>"
        val result = parseFormattedText(raw)
        assertEquals("Bold Title\n\nNormal text and italic phrase.", result.text)

        // Check bold span on "Bold Title"
        val boldSpan = result.spanStyles.find { it.item.fontWeight == FontWeight.Bold }
        assertTrue(boldSpan != null, "Should contain bold span")
        assertEquals(0, boldSpan.start)
        assertEquals(10, boldSpan.end)

        // Check italic span on "italic phrase"
        val italicSpan = result.spanStyles.find { it.item.fontStyle == FontStyle.Italic }
        assertTrue(italicSpan != null, "Should contain italic span")
    }

    @Test
    fun testMarkdownFormatting() {
        val raw = "**Bold Header** and *italic accent* with ***both***."
        val result = parseFormattedText(raw)
        assertEquals("Bold Header and italic accent with both.", result.text)

        val boldSpans = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertTrue(boldSpans.isNotEmpty())

        val italicSpans = result.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertTrue(italicSpans.isNotEmpty())
    }

    @Test
    fun testMalformedHtmlFromScreenshot() {
        val raw = "<p><b>\"Ender, Katniss, and now Darrow.\"<b>—Scott Sigler</b></b><br><b><i> </i></b><br><b>Pierce Brown"
        val result = parseFormattedText(raw)

        // Verifies tags are completely stripped and text is clean
        assertTrue(!result.text.contains("<p>"))
        assertTrue(!result.text.contains("<b>"))
        assertTrue(!result.text.contains("<br>"))
        assertTrue(result.text.contains("\"Ender, Katniss, and now Darrow.\"—Scott Sigler"))
        assertTrue(result.text.contains("Pierce Brown"))
    }
}
