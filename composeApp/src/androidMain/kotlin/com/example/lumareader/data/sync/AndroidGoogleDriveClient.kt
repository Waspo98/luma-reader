package com.example.lumareader.data.sync

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Production-ready Android implementation of [RemoteDriveClient] communicating with
 * Google Drive REST v3 API inside the private application data container (appDataFolder).
 */
class AndroidGoogleDriveClient(
    private val context: Context,
    private val account: Account
) : RemoteDriveClient {

    companion object {
        private const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        private const val SCOPE_FILE = "https://www.googleapis.com/auth/drive.file"
        private const val OAUTH_SCOPE = "oauth2:$SCOPE_APPDATA $SCOPE_FILE"
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        private const val CONNECT_TIMEOUT_MS = 30000
        private const val READ_TIMEOUT_MS = 120000
    }

    override suspend fun isAvailable(): Boolean {
        return getAuthToken() != null
    }

    override suspend fun getConnectedEmail(): String? {
        return account.name
    }

    override suspend fun listFiles(): List<RemoteFileInfo> = withContext(Dispatchers.IO) {
        var token = getAuthToken() ?: return@withContext emptyList()
        val allFiles = mutableListOf<RemoteFileInfo>()
        var pageToken: String? = null

        do {
            val pageParam = if (pageToken != null) "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}" else ""
            val url = "$DRIVE_API_BASE/files?spaces=appDataFolder&fields=files(id,name,size),nextPageToken&pageSize=1000$pageParam"
            var conn = openConnection(url, "GET", token)
            var responseCode = conn.responseCode

            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                invalidateToken(token)
                token = getAuthToken() ?: return@withContext allFiles
                conn.disconnect()
                conn = openConnection(url, "GET", token)
                responseCode = conn.responseCode
            }

            if (responseCode in 200..299) {
                try {
                    val jsonStr = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    val root = JSONObject(jsonStr)
                    val filesArr = root.optJSONArray("files")
                    if (filesArr != null) {
                        for (i in 0 until filesArr.length()) {
                            val item = filesArr.getJSONObject(i)
                            allFiles.add(
                                RemoteFileInfo(
                                    id = item.optString("id"),
                                    name = item.optString("name"),
                                    sizeBytes = item.optLong("size", 0L)
                                )
                            )
                        }
                    }
                    pageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    android.util.Log.e("LumaDriveClient", "Error parsing listFiles response", e)
                    pageToken = null
                } finally {
                    conn.disconnect()
                }
            } else {
                logError(conn, "GET", url, responseCode)
                conn.disconnect()
                pageToken = null
            }
        } while (pageToken != null)

        allFiles
    }

    override suspend fun downloadBinaryFile(
        remoteFileName: String,
        destinationFilePath: String,
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)?
    ): Boolean = withContext(Dispatchers.IO) {
        var token = getAuthToken() ?: return@withContext false
        var fileId = findFileId(remoteFileName, token) ?: return@withContext false

        var conn = openConnection("$DRIVE_API_BASE/files/$fileId?alt=media", "GET", token)
        var responseCode = conn.responseCode

        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            invalidateToken(token)
            token = getAuthToken() ?: return@withContext false
            conn.disconnect()
            conn = openConnection("$DRIVE_API_BASE/files/$fileId?alt=media", "GET", token)
            responseCode = conn.responseCode
        }

        if (responseCode in 200..299) {
            val contentLength = conn.contentLengthLong.takeIf { it > 0 } ?: 0L
            val destFile = java.io.File(destinationFilePath)
            destFile.parentFile?.mkdirs()
            val tempFile = java.io.File(destFile.parentFile, "${destFile.name}.tmp")
            try {
                conn.inputStream.use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16384)
                        var bytesRead: Int
                        var totalTransferred = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalTransferred += bytesRead
                            onProgress?.invoke(totalTransferred, contentLength)
                        }
                        output.flush()
                    }
                }
                if (destFile.exists()) {
                    destFile.delete()
                }
                tempFile.renameTo(destFile)
            } catch (e: Exception) {
                e.printStackTrace()
                if (tempFile.exists()) tempFile.delete()
                false
            } finally {
                conn.disconnect()
            }
        } else {
            conn.disconnect()
            false
        }
    }

    override suspend fun uploadBinaryFile(
        remoteFileName: String,
        localFilePath: String,
        mimeType: String,
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)?
    ): Boolean = withContext(Dispatchers.IO) {
        val localFile = java.io.File(localFilePath)
        if (!localFile.exists() || localFile.length() == 0L) {
            android.util.Log.e("LumaDriveClient", "Upload skipped: file missing or empty: $localFilePath")
            return@withContext false
        }

        var token = getAuthToken() ?: run {
            android.util.Log.e("LumaDriveClient", "Cannot upload $remoteFileName: failed to get OAuth token")
            return@withContext false
        }
        var fileId = findFileId(remoteFileName, token)

        val success = performResumableUpload(fileId, remoteFileName, localFile, mimeType, token, onProgress)

        if (!success) {
            android.util.Log.w("LumaDriveClient", "Resumable upload failed for $remoteFileName, refreshing token and retrying...")
            invalidateToken(token)
            val freshToken = getAuthToken() ?: return@withContext false
            fileId = findFileId(remoteFileName, freshToken)
            performResumableUpload(fileId, remoteFileName, localFile, mimeType, freshToken, onProgress)
        } else {
            true
        }
    }

    override suspend fun downloadTextFile(fileName: String): String? = withContext(Dispatchers.IO) {
        var token = getAuthToken() ?: return@withContext null
        var fileId = findFileId(fileName, token) ?: return@withContext null

        var conn = openConnection("$DRIVE_API_BASE/files/$fileId?alt=media", "GET", token)
        var responseCode = conn.responseCode

        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            invalidateToken(token)
            token = getAuthToken() ?: return@withContext null
            conn.disconnect()
            conn = openConnection("$DRIVE_API_BASE/files/$fileId?alt=media", "GET", token)
            responseCode = conn.responseCode
        }

        if (responseCode in 200..299) {
            try {
                conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        } else {
            conn.disconnect()
            null
        }
    }

    override suspend fun uploadTextFile(fileName: String, content: String): Boolean = withContext(Dispatchers.IO) {
        var token = getAuthToken() ?: return@withContext false
        var fileId = findFileId(fileName, token)

        val success = if (fileId != null) {
            updateExistingFile(fileId, content, token)
        } else {
            createNewFile(fileName, content, token)
        }

        if (!success) {
            // Invalidate token in case of token expiration and retry once
            invalidateToken(token)
            val freshToken = getAuthToken() ?: return@withContext false
            fileId = findFileId(fileName, freshToken)
            if (fileId != null) {
                updateExistingFile(fileId, content, freshToken)
            } else {
                createNewFile(fileName, content, freshToken)
            }
        } else {
            true
        }
    }

    override suspend fun deleteFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        val token = getAuthToken() ?: return@withContext false
        val fileId = findFileId(fileName, token) ?: return@withContext true
        val conn = openConnection("$DRIVE_API_BASE/files/$fileId", "DELETE", token)
        try {
            conn.responseCode in 200..299 || conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        val token = try {
            GoogleAuthUtil.getToken(context, account, OAUTH_SCOPE)
        } catch (_: Exception) {
            null
        }
        if (token != null) {
            invalidateToken(token)
        }
    }

    private suspend fun getAuthToken(): String? = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.getToken(context, account, OAUTH_SCOPE)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun invalidateToken(token: String) = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (_: Exception) {}
    }

    private fun findFileId(fileName: String, token: String): String? {
        val safeName = fileName.replace("'", "\\'")
        val query = "name = '$safeName' and trashed = false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$DRIVE_API_BASE/files?spaces=appDataFolder&q=$encodedQuery&fields=files(id,name)"
        val conn = openConnection(url, "GET", token)
        return try {
            val code = conn.responseCode
            if (code in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val root = JSONObject(jsonStr)
                val files = root.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    files.getJSONObject(0).optString("id")
                } else null
            } else {
                logError(conn, "GET", url, code)
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("LumaDriveClient", "Error finding file $fileName", e)
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun updateExistingFile(fileId: String, content: String, token: String): Boolean {
        val url = "$DRIVE_UPLOAD_BASE/files/$fileId?uploadType=media"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("X-HTTP-Method-Override", "PATCH")
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", "LumaReader-Android")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            doOutput = true
        }
        return try {
            val bytes = content.toByteArray(StandardCharsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { os ->
                os.write(bytes)
                os.flush()
            }
            val code = conn.responseCode
            if (code in 200..299) {
                true
            } else {
                logError(conn, "PATCH", url, code)
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("LumaDriveClient", "Exception in updateExistingFile", e)
            false
        } finally {
            conn.disconnect()
        }
    }

    private fun createNewFile(fileName: String, content: String, token: String): Boolean {
        val boundary = "====LumaReaderBoundary${System.currentTimeMillis()}===="
        val url = "$DRIVE_UPLOAD_BASE/files?uploadType=multipart"
        val conn = openConnection(url, "POST", token).apply {
            doOutput = true
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        }

        return try {
            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("parents", org.json.JSONArray().put("appDataFolder"))
            }.toString()

            val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
            val metadataBytes = metadataJson.toByteArray(StandardCharsets.UTF_8)

            val part1Header = ("--$boundary\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n\r\n").toByteArray(StandardCharsets.UTF_8)
            val part2Header = ("\r\n--$boundary\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n\r\n").toByteArray(StandardCharsets.UTF_8)
            val closing = ("\r\n--$boundary--\r\n").toByteArray(StandardCharsets.UTF_8)

            val totalLength = part1Header.size + metadataBytes.size + part2Header.size + contentBytes.size + closing.size
            conn.setFixedLengthStreamingMode(totalLength)

            conn.outputStream.use { os ->
                os.write(part1Header)
                os.write(metadataBytes)
                os.write(part2Header)
                os.write(contentBytes)
                os.write(closing)
                os.flush()
            }
            val code = conn.responseCode
            if (code in 200..299) {
                true
            } else {
                logError(conn, "POST", url, code)
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("LumaDriveClient", "Exception in createNewFile", e)
            false
        } finally {
            conn.disconnect()
        }
    }

    private fun performResumableUpload(
        fileId: String?,
        fileName: String,
        file: java.io.File,
        mimeType: String,
        token: String,
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        val fileLength = file.length()

        // Step 1: Initiate Resumable Upload Session
        val initUrl = if (fileId != null) {
            "$DRIVE_UPLOAD_BASE/files/$fileId?uploadType=resumable"
        } else {
            "$DRIVE_UPLOAD_BASE/files?uploadType=resumable"
        }

        val initConn = (URL(initUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            if (fileId != null) {
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
            }
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", "LumaReader-Android")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-Upload-Content-Type", mimeType)
            setRequestProperty("X-Upload-Content-Length", fileLength.toString())
            doOutput = true
        }

        val sessionUrl = try {
            val metadataJson = if (fileId == null) {
                JSONObject().apply {
                    put("name", fileName)
                    put("parents", org.json.JSONArray().put("appDataFolder"))
                }.toString()
            } else {
                "{}"
            }
            val metaBytes = metadataJson.toByteArray(StandardCharsets.UTF_8)
            initConn.setFixedLengthStreamingMode(metaBytes.size)
            initConn.outputStream.use { os ->
                os.write(metaBytes)
                os.flush()
            }

            val code = initConn.responseCode
            if (code in 200..299) {
                initConn.getHeaderField("Location")
            } else {
                logError(initConn, "POST", initUrl, code)
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("LumaDriveClient", "Exception initiating resumable upload for $fileName", e)
            null
        } finally {
            initConn.disconnect()
        }

        if (sessionUrl.isNullOrBlank()) {
            android.util.Log.e("LumaDriveClient", "No session Location returned for $fileName upload")
            return false
        }

        // Step 2: Stream binary content directly via PUT to the session Location
        val uploadConn = (URL(sessionUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", mimeType)
            setRequestProperty("User-Agent", "LumaReader-Android")
            setFixedLengthStreamingMode(fileLength)
            doOutput = true
        }

        return try {
            java.io.FileInputStream(file).use { input ->
                uploadConn.outputStream.use { output ->
                    val buffer = ByteArray(32768)
                    var bytesRead: Int
                    var bytesSent = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesSent += bytesRead
                        onProgress?.invoke(bytesSent, fileLength)
                    }
                    output.flush()
                }
            }

            val code = uploadConn.responseCode
            if (code in 200..299) {
                true
            } else {
                logError(uploadConn, "PUT", sessionUrl, code)
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("LumaDriveClient", "Exception streaming binary upload for $fileName", e)
            false
        } finally {
            uploadConn.disconnect()
        }
    }

    private fun logError(conn: HttpURLConnection, method: String, url: String, code: Int) {
        val errorBody = try {
            conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
        } catch (_: Exception) { null }
        android.util.Log.e("LumaDriveClient", "[$method] $url failed (HTTP $code): $errorBody")
    }

    private fun openConnection(urlString: String, method: String, token: String): HttpURLConnection {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("User-Agent", "LumaReader-Android")
        return conn
    }
}
