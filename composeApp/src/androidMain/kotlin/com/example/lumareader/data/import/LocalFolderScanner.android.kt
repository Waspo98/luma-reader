package com.example.lumareader.data.import

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.lumareader.data.model.Book
import com.example.lumareader.data.model.DiscoveredEpub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalFolderScanner(private val context: Context) {

    suspend fun scanTreeUri(
        treeUri: Uri,
        existingBooks: List<Book>,
        onProgress: ((foundCount: Int, currentPath: String) -> Unit)? = null
    ): List<DiscoveredEpub> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<DiscoveredEpub>()
        val existingTitles = existingBooks.map { it.title.trim().lowercase() }.toSet()
        val existingFiles = existingBooks.mapNotNull { 
            it.epubFilePath?.substringAfterLast('/')?.trim()?.lowercase() 
        }.toSet()

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)

        fun scanDirectory(docId: String, currentPath: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )

            try {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                    while (cursor.moveToNext()) {
                        val childId = cursor.getString(idIndex)
                        val name = cursor.getString(nameIndex) ?: continue
                        if (name.startsWith(".") || name.isBlank()) continue
                        val mime = cursor.getString(mimeIndex) ?: ""
                        val size = if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            val nextPath = if (currentPath.isEmpty()) name else "$currentPath/$name"
                            onProgress?.invoke(discovered.size, nextPath)
                            scanDirectory(childId, nextPath)
                        } else if (name.endsWith(".epub", ignoreCase = true) || mime == "application/epub+zip") {
                            val cleanTitle = name.removeSuffix(".epub").removeSuffix(".EPUB").trim().lowercase()
                            if (cleanTitle.isBlank()) continue
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            val cleanFileName = name.trim().lowercase()
                            val isAlreadyInLibrary = cleanTitle in existingTitles || cleanFileName in existingFiles

                            val item = DiscoveredEpub(
                                uriString = docUri.toString(),
                                fileName = name,
                                relativePath = if (currentPath.isEmpty()) "/" else currentPath,
                                sizeBytes = size,
                                isAlreadyInLibrary = isAlreadyInLibrary,
                                isSelected = !isAlreadyInLibrary
                            )
                            discovered.add(item)
                            onProgress?.invoke(discovered.size, currentPath)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        onProgress?.invoke(0, "")
        scanDirectory(rootDocId, "")
        discovered.sortedWith(compareBy({ it.isAlreadyInLibrary }, { it.fileName.lowercase() }))
    }
}
