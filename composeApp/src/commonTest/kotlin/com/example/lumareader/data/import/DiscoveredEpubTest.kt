package com.example.lumareader.data.import

import com.example.lumareader.data.model.DiscoveredEpub
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscoveredEpubTest {

    @Test
    fun testDefaultSelection_newBook_isSelectedTrue() {
        val epub = DiscoveredEpub(
            uriString = "content://sample/1",
            fileName = "Dune.epub",
            relativePath = "Sci-Fi",
            sizeBytes = 2 * 1024 * 1024,
            isAlreadyInLibrary = false
        )
        assertTrue(epub.isSelected)
        assertFalse(epub.isAlreadyInLibrary)
    }

    @Test
    fun testDefaultSelection_duplicateBook_isSelectedFalse() {
        val epub = DiscoveredEpub(
            uriString = "content://sample/2",
            fileName = "Dune.epub",
            relativePath = "Sci-Fi",
            sizeBytes = 2 * 1024 * 1024,
            isAlreadyInLibrary = true
        )
        assertFalse(epub.isSelected)
        assertTrue(epub.isAlreadyInLibrary)
    }

    @Test
    fun testFormattedSize_unknown() {
        val epub = DiscoveredEpub(
            uriString = "content://sample/3",
            fileName = "Unknown.epub",
            relativePath = "/",
            sizeBytes = 0L
        )
        assertEquals("Unknown size", epub.formattedSize)
    }

    @Test
    fun testFormattedSize_bytes() {
        val epub = DiscoveredEpub(
            uriString = "content://sample/4",
            fileName = "Tiny.epub",
            relativePath = "/",
            sizeBytes = 512L
        )
        assertEquals("512 B", epub.formattedSize)
    }

    @Test
    fun testFormattedSize_kilobytes() {
        val epub = DiscoveredEpub(
            uriString = "content://sample/5",
            fileName = "Small.epub",
            relativePath = "/",
            sizeBytes = 1536L // 1.5 KB
        )
        assertEquals("1.5 KB", epub.formattedSize)
    }

    @Test
    fun testFormattedSize_megabytes() {
        val epub = DiscoveredEpub(
            uriString = "content://sample/6",
            fileName = "Book.epub",
            relativePath = "Fantasy",
            sizeBytes = (2.5 * 1024 * 1024).toLong()
        )
        assertEquals("2.5 MB", epub.formattedSize)
    }

    @Test
    fun testFormattedSize_gigabytes() {
        val epub = DiscoveredEpub(
            uriString = "content://sample/7",
            fileName = "Omnibus.epub",
            relativePath = "Archive",
            sizeBytes = (1.2 * 1024 * 1024 * 1024).toLong()
        )
        assertEquals("1.2 GB", epub.formattedSize)
    }
}
