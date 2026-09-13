package com.example.lumareader.data.sync

import com.example.lumareader.data.LocalBookRepository
import com.example.lumareader.data.model.Book
import com.example.lumareader.data.model.ReadingPreferences
import com.example.lumareader.data.model.SyncPreferences
import com.example.lumareader.data.model.SyncScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.*

class SyncManagerTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Test
    fun testSyncPreferences_defaults() {
        val prefs = SyncPreferences()
        assertEquals(SyncScope.FULL_LIBRARY, prefs.syncScope)
        assertNull(prefs.lastSyncTimestamp)
        assertTrue(prefs.autoSyncOnOpen)
        assertTrue(prefs.autoSyncOnClose)
    }

    @Test
    fun testSyncPreferences_serialization() {
        val original = SyncPreferences(
            syncScope = SyncScope.READING_POSITION_ONLY,
            lastSyncTimestamp = 1725890000000L,
            autoSyncOnOpen = false,
            autoSyncOnClose = true
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SyncPreferences>(encoded)

        assertEquals(SyncScope.READING_POSITION_ONLY, decoded.syncScope)
        assertEquals(1725890000000L, decoded.lastSyncTimestamp)
        assertFalse(decoded.autoSyncOnOpen)
        assertTrue(decoded.autoSyncOnClose)
    }

    @Test
    fun testReadingPreferences_includesSyncPreferences() {
        val readingPrefs = ReadingPreferences(
            syncPrefs = SyncPreferences(syncScope = SyncScope.READING_POSITION_ONLY)
        )
        val encoded = json.encodeToString(readingPrefs)
        val decoded = json.decodeFromString<ReadingPreferences>(encoded)

        assertEquals(SyncScope.READING_POSITION_ONLY, decoded.syncPrefs.syncScope)
    }

    @Test
    fun testCloudSyncManager_connectAndDisconnect() = runBlocking {
        val syncManager = CloudSyncManager()

        // Initial state is disconnected default
        assertNull(syncManager.connectedEmail.value)

        // Connect
        syncManager.connect("reader.user@gmail.com")
        assertEquals("reader.user@gmail.com", syncManager.connectedEmail.value)
        assertFalse(syncManager.isSyncing.value)

        // Disconnect
        syncManager.disconnect()
        assertNull(syncManager.connectedEmail.value)
        assertFalse(syncManager.isSyncing.value)
    }

    @Test
    fun testCloudSyncManager_notConnectedReturnsError() = runBlocking {
        val syncManager = CloudSyncManager(fs = FileSystem.SYSTEM)

        val tempDir = Files.createTempDirectory("sync_test_not_connected").toFile().apply { deleteOnExit() }
        val tempDirPath = tempDir.absolutePath
        val repo = LocalBookRepository(tempDirPath, "$tempDirPath/cache")

        val result = syncManager.triggerSync(tempDirPath, repo, SyncScope.FULL_LIBRARY)
        assertFalse(result.success)
        assertEquals("Not connected to Google Drive", result.message)
        assertEquals(0, result.itemsSynced)
    }

    @Test
    fun testCloudSyncManager_readingPositionOnlySync() = runBlocking {
        val fs = FileSystem.SYSTEM
        val syncManager = CloudSyncManager(fs = fs)
        syncManager.connect("test.user@example.com")

        val tempDir = Files.createTempDirectory("sync_test_pos").toFile().apply { deleteOnExit() }
        val tempDirPath = tempDir.absolutePath
        val repo = LocalBookRepository(tempDirPath, "$tempDirPath/cache")

        val result = syncManager.triggerSync(tempDirPath, repo, SyncScope.READING_POSITION_ONLY)
        assertTrue(result.success)
        assertEquals(SyncScope.READING_POSITION_ONLY, result.scope)
        assertTrue(result.message.contains("reading positions"))

        // Check that reading_positions.json was written in the google_drive_sync dir
        val positionsFile = "$tempDirPath/google_drive_sync/reading_positions.json".toPath()
        assertTrue(fs.exists(positionsFile))

        // Catalog file should NOT be written for reading position only sync
        val catalogFile = "$tempDirPath/google_drive_sync/library_catalog.json".toPath()
        assertFalse(fs.exists(catalogFile))
    }

    @Test
    fun testCloudSyncManager_fullLibrarySync() = runBlocking {
        val fs = FileSystem.SYSTEM
        val syncManager = CloudSyncManager(fs = fs)
        syncManager.connect("test.user@example.com")

        val tempDir = Files.createTempDirectory("sync_test_full").toFile().apply { deleteOnExit() }
        val tempDirPath = tempDir.absolutePath
        val repo = LocalBookRepository(tempDirPath, "$tempDirPath/cache")

        val result = syncManager.triggerSync(tempDirPath, repo, SyncScope.FULL_LIBRARY)
        assertTrue(result.success)
        assertEquals(SyncScope.FULL_LIBRARY, result.scope)
        assertTrue(result.message.contains("Full library"))

        // Catalog file MUST be written for full library sync
        val catalogFile = "$tempDirPath/google_drive_sync/library_catalog.json".toPath()
        assertTrue(fs.exists(catalogFile))

        // Reading positions file is also updated
        val positionsFile = "$tempDirPath/google_drive_sync/reading_positions.json".toPath()
        assertTrue(fs.exists(positionsFile))
    }

    @Test
    fun testCloudSyncManager_remoteReadingPositionMerge() = runBlocking {
        val fs = FileSystem.SYSTEM
        val tempDir = Files.createTempDirectory("sync_test_merge").toFile().apply { deleteOnExit() }
        val tempDirPath = tempDir.absolutePath
        val repo = LocalBookRepository(tempDirPath, "$tempDirPath/cache")

        val syncClient = LocalFileDriveClient(tempDirPath, fs, "merge.user@example.com")
        val syncManager = CloudSyncManager(fs = fs, remoteClient = syncClient)
        syncManager.connect("merge.user@example.com")

        // Prepopulate remote with newer position for book-1
        val remoteMap = mapOf(
            "book-1" to RemoteReadingPosition(
                bookId = "book-1",
                currentSpineIndex = 5,
                currentProgression = 0.8f,
                lastLocatorJson = null,
                lastReadTimestamp = 2000000L
            )
        )
        syncClient.uploadTextFile("reading_positions.json", json.encodeToString(remoteMap))

        val result = syncManager.triggerSync(tempDirPath, repo, SyncScope.READING_POSITION_ONLY)
        assertTrue(result.success)

        val downloaded = syncClient.downloadTextFile("reading_positions.json")
        assertNotNull(downloaded)
        assertTrue(downloaded.contains("book-1"))
    }
}
