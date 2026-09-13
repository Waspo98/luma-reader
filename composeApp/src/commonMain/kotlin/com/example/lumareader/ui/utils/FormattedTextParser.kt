package com.example.lumareader.ui.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Parses raw text that may contain HTML markup (<p>, <b>, <i>, <br>, entities)
 * or Markdown syntax (**, *, __, _) into a styled Jetpack Compose AnnotatedString.
 */
fun parseFormattedText(raw: String?): AnnotatedString {
    if (raw.isNullOrBlank()) return AnnotatedString("")

    var text = raw

    // Step 1: Normalize Markdown formatting to uniform HTML tags for unified processing
    // ***bold italic*** or ___bold italic___
    text = text.replace(Regex("""\*\*\*(.+?)\*\*\*"""), "<b><i>$1</i></b>")
    text = text.replace(Regex("""___(.+?)___"""), "<b><i>$1</i></b>")
    // **bold** or __bold__
    text = text.replace(Regex("""\*\*(.+?)\*\*"""), "<b>$1</b>")
    text = text.replace(Regex("""__(.+?)__"""), "<b>$1</b>")
    // *italic* or _italic_
    text = text.replace(Regex("""\*(.+?)\*"""), "<i>$1</i>")
    text = text.replace(Regex("""_(.+?)_"""), "<i>$1</i>")

    // Step 2: Normalize HTML structural breaks
    text = text.replace(Regex("""(?i)<br\s*/?>"""), "\n")
    text = text.replace(Regex("""(?i)</p>\s*<p[^>]*>"""), "\n\n")
    text = text.replace(Regex("""(?i)</p>"""), "\n\n")
    text = text.replace(Regex("""(?i)<p[^>]*>"""), "")
    text = text.replace(Regex("""(?i)</div>\s*<div[^>]*>"""), "\n")
    text = text.replace(Regex("""(?i)</?(div|span|blockquote|section|article)[^>]*>"""), "")

    // Step 3: Tokenize into text chunks and formatting tags
    return buildAnnotatedString {
        var boldDepth = 0
        var italicDepth = 0

        val tagRegex = Regex("""<(/?[a-zA-Z0-9]+)[^>]*>""")
        var lastIndex = 0

        for (match in tagRegex.findAll(text)) {
            val startIndex = match.range.first
            if (startIndex > lastIndex) {
                val chunk = text.substring(lastIndex, startIndex)
                appendChunk(chunk, boldDepth > 0, italicDepth > 0)
            }

            val tagName = match.groupValues[1].lowercase()
            when (tagName) {
                "b", "strong" -> boldDepth++
                "/b", "/strong" -> boldDepth = (boldDepth - 1).coerceAtLeast(0)
                "i", "em" -> italicDepth++
                "/i", "/em" -> italicDepth = (italicDepth - 1).coerceAtLeast(0)
            }

            lastIndex = match.range.last + 1
        }

        if (lastIndex < text.length) {
            val remaining = text.substring(lastIndex)
            appendChunk(remaining, boldDepth > 0, italicDepth > 0)
        }
    }.trimAnnotatedString()
}

private fun AnnotatedString.Builder.appendChunk(rawChunk: String, isBold: Boolean, isItalic: Boolean) {
    val chunk = decodeHtmlEntities(rawChunk)
    if (chunk.isEmpty()) return
    when {
        isBold && isItalic -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                append(chunk)
            }
        }
        isBold -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(chunk)
            }
        }
        isItalic -> {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(chunk)
            }
        }
        else -> {
            append(chunk)
        }
    }
}

private fun decodeHtmlEntities(input: String): String {
    var result = input
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&hellip;", "…")
        .replace("&copy;", "©")
        .replace("&reg;", "®")
        .replace("&trade;", "™")

    // Numeric decimal entities: &#123;
    result = Regex("""&#(\d+);""").replace(result) { match ->
        val code = match.groupValues[1].toIntOrNull()
        if (code != null && code in 32..65535) {
            code.toChar().toString()
        } else {
            match.value
        }
    }

    // Numeric hex entities: &#x1F;
    result = Regex("""&#x([0-9a-fA-F]+);""").replace(result) { match ->
        val code = match.groupValues[1].toIntOrNull(16)
        if (code != null && code in 32..65535) {
            code.toChar().toString()
        } else {
            match.value
        }
    }

    return result
}

private fun AnnotatedString.trimAnnotatedString(): AnnotatedString {
    var start = 0
    while (start < length && this[start].isWhitespace()) {
        start++
    }
    var end = length
    while (end > start && this[end - 1].isWhitespace()) {
        end--
    }
    return if (start == 0 && end == length) this else subSequence(start, end)
}
