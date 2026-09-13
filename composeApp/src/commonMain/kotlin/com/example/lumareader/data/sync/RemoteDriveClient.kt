package com.example.lumareader.data.sync

import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer

/**
 * Metadata descriptor for a file in cloud storage.
 */
@Serializable
data class RemoteFileInfo(
    val id: String,
    val name: String,
    val sizeBytes: Long = 0L
)

/**
 * Platform-independent abstraction for remote cloud storage (such as Google Drive appDataFolder).
 */
interface RemoteDriveClient {
    /**
     * Checks whether cloud sync is currently authenticated and ready to perform operations.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Returns the email of the authenticated cloud account, or null if not signed in.
     */
    suspend fun getConnectedEmail(): String?

    /**
     * Lists all files currently stored in the remote app data space.
     */
    suspend fun listFiles(): List<RemoteFileInfo>

    /**
     * Downloads the text content of a file by name from the app-data space in cloud storage.
     * Returns null if the file does not exist.
     */
    suspend fun downloadTextFile(fileName: String): String?

    /**
     * Creates or updates a text file by name in the app-data space in cloud storage.
     * Returns true if the upload was successful.
     */
    suspend fun uploadTextFile(fileName: String, content: String): Boolean

    /**
     * Uploads a binary file (such as an EPUB) from [localFilePath] into the cloud app data space as [remoteFileName].
     */
    suspend fun uploadBinaryFile(remoteFileName: String, localFilePath: String, mimeType: String = "application/epub+zip"): Boolean

    /**
     * Downloads a binary file named [remoteFileName] from cloud app data space to [destinationFilePath].
     */
    suspend fun downloadBinaryFile(remoteFileName: String, destinationFilePath: String): Boolean

    /**
     * Deletes a file by name from the app-data space, if it exists.
     */
    suspend fun deleteFile(fileName: String): Boolean

    /**
     * Signs out / disconnects the current account.
     */
    suspend fun disconnect()
}

/**
 * A file-system-backed implementation of [RemoteDriveClient] used for offline mocking and unit testing.
 */
class LocalFileDriveClient(
    private val syncDir: String,
    private val fs: FileSystem = FileSystem.SYSTEM,
    private var email: String? = null
) : RemoteDriveClient {

    private val folderPath = "$syncDir/google_drive_sync".toPath()

    override suspend fun isAvailable(): Boolean = email != null

    override suspend fun getConnectedEmail(): String? = email

    fun setConnectedEmail(newEmail: String?) {
        email = newEmail
    }

    override suspend fun listFiles(): List<RemoteFileInfo> {
        if (!fs.exists(folderPath)) return emptyList()
        return try {
            fs.list(folderPath).mapNotNull { path ->
                val meta = fs.metadataOrNull(path)
                if (meta?.isRegularFile == true) {
                    RemoteFileInfo(
                        id = path.name,
                        name = path.name,
                        sizeBytes = meta.size ?: 0L
                    )
                } else null
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun downloadTextFile(fileName: String): String? {
        val target = "$folderPath/$fileName".toPath()
        return if (fs.exists(target)) {
            try {
                fs.read(target) { readUtf8() }
            } catch (_: Exception) {
                null
            }
        } else null
    }

    override suspend fun uploadTextFile(fileName: String, content: String): Boolean {
        return try {
            fs.createDirectories(folderPath)
            val target = "$folderPath/$fileName".toPath()
            fs.write(target) {
                writeUtf8(content)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun uploadBinaryFile(remoteFileName: String, localFilePath: String, mimeType: String): Boolean {
        val src = localFilePath.toPath()
        if (!fs.exists(src)) return false
        val dest = "$folderPath/$remoteFileName".toPath()
        return try {
            fs.createDirectories(folderPath)
            fs.source(src).use { inSource ->
                fs.sink(dest).buffer().use { outSink ->
                    outSink.writeAll(inSource)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun downloadBinaryFile(remoteFileName: String, destinationFilePath: String): Boolean {
        val src = "$folderPath/$remoteFileName".toPath()
        if (!fs.exists(src)) return false
        val dest = destinationFilePath.toPath()
        return try {
            dest.parent?.let { fs.createDirectories(it) }
            fs.source(src).use { inSource ->
                fs.sink(dest).buffer().use { outSink ->
                    outSink.writeAll(inSource)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun deleteFile(fileName: String): Boolean {
        val target = "$folderPath/$fileName".toPath()
        return try {
            if (fs.exists(target)) {
                fs.delete(target)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun disconnect() {
        email = null
    }
}
