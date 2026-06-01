package com.example.lumareader.data.epub

import com.example.lumareader.data.model.Book

expect class EpubParser() {
    /**
     * Unzips the epub file to a local subdirectory in cacheDir,
     * parses its metadata (container, opf manifest, spine, TOC),
     * and returns a complete Book model representing the book.
     */
    fun parseEpub(epubFilePath: String, cacheDir: String): Book
}
