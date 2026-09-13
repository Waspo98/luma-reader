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
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 20000
    }

    override suspend fun isAvailable(): Boolean {
        return getAuthToken() != null
    }

    override suspend fun getConnectedEmail(): String? {
        return account.name
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
        val query = "name = '$fileName' and trashed = false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$DRIVE_API_BASE/files?spaces=appDataFolder&q=$encodedQuery&fields=files(id,name)"
        val conn = openConnection(url, "GET", token)
        return try {
            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val root = JSONObject(jsonStr)
                val files = root.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    files.getJSONObject(0).optString("id")
                } else null
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun updateExistingFile(fileId: String, content: String, token: String): Boolean {
        val url = "$DRIVE_UPLOAD_BASE/files/$fileId?uploadType=media"
        val conn = openConnection(url, "PATCH", token).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        return try {
            val bytes = content.toByteArray(StandardCharsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { os ->
                os.write(bytes)
                os.flush()
            }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
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
            conn.responseCode in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            conn.disconnect()
        }
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
