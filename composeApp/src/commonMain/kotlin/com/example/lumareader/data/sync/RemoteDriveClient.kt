package com.example.lumareader.data.sync

import okio.FileSystem
import okio.Path.Companion.toPath

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
