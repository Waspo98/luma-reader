package com.example.lumareader.data.epub

import com.example.lumareader.data.model.Book
import com.example.lumareader.data.model.TocItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

actual class EpubParser {
    actual fun parseEpub(epubFilePath: String, cacheDir: String): Book {
        val epubFile = File(epubFilePath)
        if (!epubFile.exists()) {
            throw IllegalArgumentException("EPUB file does not exist: $epubFilePath")
        }

        // Generate unique MD5 hash for the file content to serve as deterministic bookId
        val bookId = getFileMd5(epubFile)
        val targetDir = File(cacheDir, "epubs/$bookId")
        
        // Unzip EPUB if it hasn't been unzipped yet or if directories are missing
        if (!targetDir.exists() || targetDir.listFiles().isNullOrEmpty()) {
            unzip(epubFile, targetDir)
        }

        // Parse container.xml to locate the OPF file
        val containerXml = File(targetDir, "META-INF/container.xml")
        if (!containerXml.exists()) {
            throw IllegalArgumentException("Invalid EPUB: META-INF/container.xml not found")
        }
        val containerContent = containerXml.readText()
        val opfRelativePath = extractOpfPath(containerContent)
        
        val opfFile = File(targetDir, opfRelativePath)
        if (!opfFile.exists()) {
            throw IllegalArgumentException("Invalid EPUB: OPF manifest not found at $opfRelativePath")
        }

        // Define the base directory of the OPF file (needed to locate resources relative to OPF)
        val opfDir = opfFile.parentFile?.relativeTo(targetDir)?.path?.replace("\\", "/") ?: ""

        // Parse OPF content using XML DOM builder
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // Protect against XXE attacks safely on various Android system XML parsers
            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (_: Exception) {}
            try {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            } catch (_: Exception) {}
            try {
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            } catch (_: Exception) {}
        }
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(opfFile)

        // Extract Title
        var title = ""
        val titleNodes = doc.getElementsByTagName("dc:title")
        if (titleNodes.length > 0) {
            title = titleNodes.item(0).textContent ?: ""
        } else {
            val altTitle = doc.getElementsByTagName("title")
            if (altTitle.length > 0) title = altTitle.item(0).textContent ?: ""
        }
        if (title.isBlank()) title = epubFile.nameWithoutExtension.replace("_", " ").replace("-", " ")

        // Extract Author
        var author = ""
        val creatorNodes = doc.getElementsByTagName("dc:creator")
        
        val metaNodesForAuthor = getAllMetaElements(doc)
        val creatorRoles = mutableMapOf<String, String>()
        for (meta in metaNodesForAuthor) {
            val refines = meta.getAttribute("refines").removePrefix("#")
            val property = meta.getAttribute("property")
            val name = meta.getAttribute("name")
            if (property == "role" || name == "role") {
                val role = meta.textContent?.takeIf { it.isNotBlank() } ?: meta.getAttribute("content")
                if (refines.isNotEmpty() && role.isNotEmpty()) {
                    creatorRoles[refines] = role
                }
            }
        }
        
        for (i in 0 until creatorNodes.length) {
            val node = creatorNodes.item(i) as Element
            val id = node.getAttribute("id")
            if (creatorRoles[id] == "aut") {
                author = node.textContent ?: ""
                break
            }
        }
        
        if (author.isBlank() && creatorNodes.length > 0) {
            author = creatorNodes.item(0).textContent ?: ""
        } else if (author.isBlank()) {
            val altCreator = doc.getElementsByTagName("creator")
            if (altCreator.length > 0) author = altCreator.item(0).textContent ?: ""
        }
        if (author.isBlank()) author = "Unknown Author"

        // Extract Publisher
        var publisher: String? = null
        val publisherNodes = doc.getElementsByTagName("dc:publisher")
        if (publisherNodes.length > 0) {
            publisher = publisherNodes.item(0).textContent?.takeIf { it.isNotBlank() }
        }

        // Extract Publication Date
        var publishDate: String? = null
        val dateNodes = doc.getElementsByTagName("dc:date")
        if (dateNodes.length > 0) {
            publishDate = dateNodes.item(0).textContent?.takeIf { it.isNotBlank() }
        }

        // Extract Language
        var language: String? = null
        val langNodes = doc.getElementsByTagName("dc:language")
        if (langNodes.length > 0) {
            language = langNodes.item(0).textContent?.takeIf { it.isNotBlank() }
        }

        // Extract Description
        var description: String? = null
        val descNodes = doc.getElementsByTagName("dc:description")
        if (descNodes.length > 0) {
            description = descNodes.item(0).textContent?.takeIf { it.isNotBlank() }
        }

        // Extract Subjects (genres/tags)
        val subjects = mutableListOf<String>()
        val subjectNodes = doc.getElementsByTagName("dc:subject")
        for (j in 0 until subjectNodes.length) {
            subjectNodes.item(j).textContent?.takeIf { it.isNotBlank() }?.let { subjects.add(it) }
        }

        // Extract ISBN from dc:identifier
        var isbn: String? = null
        val identifierNodes = doc.getElementsByTagName("dc:identifier")
        for (j in 0 until identifierNodes.length) {
            val text = identifierNodes.item(j).textContent ?: continue
            if (text.startsWith("urn:isbn:")) {
                isbn = text.removePrefix("urn:isbn:")
                break
            } else if (text.matches(Regex("""^(?:\d[-xX\s]?){9,13}\d$"""))) {
                isbn = text.replace(Regex("""[-\s]"""), "")
                break
            }
        }

        // Extract Series (calibre metadata convention & EPUB 3 fallback) & Subtitle & Page Count
        var series: String? = null
        var seriesNumber: Float? = null
        var subtitle: String? = null
        var pageCount: Int? = null

        val metaNodes = getAllMetaElements(doc)
        for (meta in metaNodes) {
            when (meta.getAttribute("name")) {
                "calibre:series" -> series = meta.getAttribute("content").takeIf { it.isNotBlank() }
                "calibre:series_index" -> seriesNumber = meta.getAttribute("content").toFloatOrNull()
                "booklore:subtitle", "calibre:title_sort" -> {
                    if (subtitle == null) subtitle = meta.getAttribute("content").takeIf { it.isNotBlank() }
                }
                "booklore:page_count" -> pageCount = meta.getAttribute("content").toIntOrNull()
            }
            if (series == null && meta.getAttribute("property") == "belongs-to-collection") {
                series = meta.textContent?.takeIf { it.isNotBlank() }
            }
            if (seriesNumber == null && meta.getAttribute("property") == "group-position") {
                seriesNumber = meta.textContent?.toFloatOrNull()
            }
            when (meta.getAttribute("property")) {
                "booklore:page_count" -> if (pageCount == null) pageCount = meta.textContent?.toIntOrNull()
                "booklore:subtitle" -> if (subtitle == null) subtitle = meta.textContent?.takeIf { it.isNotBlank() }
            }
        }

        // Build Manifest Map (ID -> Href)
        val manifestMap = mutableMapOf<String, String>()
        val items = doc.getElementsByTagName("item")
        var coverId = ""
        
        // Check metadata block for cover meta references
        val metas = getAllMetaElements(doc)
        for (meta in metas) {
            if (meta.getAttribute("name") == "cover") {
                coverId = meta.getAttribute("content")
            }
        }

        for (i in 0 until items.length) {
            val item = items.item(i) as Element
            val id = item.getAttribute("id")
            val href = item.getAttribute("href")
            if (id.isNotEmpty() && href.isNotEmpty()) {
                manifestMap[id] = href
            }
        }

        // Compile Spine (ordered content path list)
        val spine = mutableListOf<String>()
        val itemrefs = doc.getElementsByTagName("itemref")
        for (i in 0 until itemrefs.length) {
            val itemref = itemrefs.item(i) as Element
            val idref = itemref.getAttribute("idref")
            val href = manifestMap[idref]
            if (href != null) {
                val relativePath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
                spine.add(normalizePath(relativePath))
            }
        }

        // Resolve cover image path using priority-based matching
        var coverPath: String? = null
        var coverPriority = -1  // higher = better match

        for (i in 0 until items.length) {
            val item = items.item(i) as Element
            val id = item.getAttribute("id")
            val href = item.getAttribute("href")
            val mediaType = item.getAttribute("media-type")
            val properties = item.getAttribute("properties")

            // Only consider image media types for cover
            if (!mediaType.startsWith("image/")) continue

            val priority = when {
                properties == "cover-image" -> 4           // EPUB 3 standard — highest
                id == coverId && coverId.isNotEmpty() -> 3  // <meta name="cover"> reference
                id == "cover-image" -> 2                    // Common convention
                id == "cover" -> 1                          // Ambiguous but possible
                else -> -1
            }

            if (priority > coverPriority) {
                coverPriority = priority
                coverPath = normalizePath(if (opfDir.isNotEmpty()) "$opfDir/$href" else href)
            }
        }

        // Extract Table of Contents (TOC) from NCX or spine fallbacks
        val toc = mutableListOf<TocItem>()
        val spineElement = doc.getElementsByTagName("spine").item(0) as? Element
        val tocId = spineElement?.getAttribute("toc") ?: "ncx"
        val tocHref = manifestMap[tocId] ?: manifestMap.values.find { it.endsWith(".ncx") }

        if (tocHref != null) {
            val ncxFile = File(targetDir, if (opfDir.isNotEmpty()) "$opfDir/$tocHref" else tocHref)
            if (ncxFile.exists()) {
                try {
                    val ncxDoc = builder.parse(ncxFile)
                    val navPoints = ncxDoc.getElementsByTagName("navPoint")
                    for (i in 0 until navPoints.length) {
                        val navPoint = navPoints.item(i) as Element
                        val labelElement = navPoint.getElementsByTagName("navLabel").item(0) as? Element
                        val textElement = labelElement?.getElementsByTagName("text")?.item(0) as? Element
                        val text = textElement?.textContent ?: "Chapter ${i + 1}"
                        
                        val contentElement = navPoint.getElementsByTagName("content").item(0) as? Element
                        val src = contentElement?.getAttribute("src") ?: ""
                        if (src.isNotEmpty()) {
                            val ncxFilePath = if (opfDir.isNotEmpty()) "$opfDir/$tocHref" else tocHref
                            val ncxDir = ncxFilePath.substringBeforeLast("/", "")
                            val rawHref = if (ncxDir.isNotEmpty()) "$ncxDir/$src" else src
                            toc.add(TocItem(text.trim(), normalizePath(rawHref)))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Standard fallback for TOC if empty
        if (toc.isEmpty()) {
            for (i in spine.indices) {
                val path = spine[i]
                val name = path.substringAfterLast("/").substringBeforeLast(".")
                    .replace("-", " ")
                    .replace("_", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                toc.add(TocItem(name, path))
            }
        }

        return Book(
            id = bookId,
            title = title,
            author = author,
            series = series,
            seriesNumber = seriesNumber,
            coverPath = coverPath,
            unzippedDir = targetDir.absolutePath.replace("\\", "/"),
            spine = spine,
            toc = toc,
            publisher = publisher,
            publishDate = publishDate,
            language = language,
            description = description,
            subjects = subjects,
            isbn = isbn,
            subtitle = subtitle,
            pageCount = pageCount
        )
    }

    private fun getAllMetaElements(doc: org.w3c.dom.Document): List<Element> {
        val result = mutableListOf<Element>()
        val plain = doc.getElementsByTagName("meta")
        for (i in 0 until plain.length) {
            result.add(plain.item(i) as Element)
        }
        val prefixed = doc.getElementsByTagName("opf:meta")
        for (i in 0 until prefixed.length) {
            result.add(prefixed.item(i) as Element)
        }
        return result
    }

    private fun getFileMd5(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(16384)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun getMd5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun unzip(zipFile: File, targetDir: File) {
        targetDir.mkdirs()
        ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                // Guard against Zip Slip directory traversal
                val targetCanonicalPath = targetDir.canonicalPath
                val fileCanonicalPath = file.canonicalPath
                val safePrefix = if (targetCanonicalPath.endsWith(File.separator)) targetCanonicalPath else targetCanonicalPath + File.separator
                if (!fileCanonicalPath.startsWith(safePrefix)) {
                    throw SecurityException("Zip Slip directory traversal attempt: ${entry.name}")
                }
                
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { out ->
                        zipIn.copyTo(out)
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }

    private fun extractOpfPath(containerContent: String): String {
        val regex = """full-path="([^"]+)"""".toRegex()
        val match = regex.find(containerContent)
        return match?.groups?.get(1)?.value ?: "OEBPS/content.opf"
    }

    private fun normalizePath(path: String): String {
        val cleanPath = path.replace("\\", "/")
        val hash = if (cleanPath.contains("#")) "#" + cleanPath.substringAfter("#") else ""
        val pathWithoutHash = cleanPath.substringBefore("#")
        
        val parts = pathWithoutHash.split("/")
        val resolved = mutableListOf<String>()
        for (part in parts) {
            if (part == "..") {
                if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
            } else if (part != "." && part.isNotEmpty()) {
                resolved.add(part)
            }
        }
        return resolved.joinToString("/") + hash
    }
}
