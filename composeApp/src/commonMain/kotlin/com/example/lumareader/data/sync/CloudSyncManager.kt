package com.example.lumareader.data.sync

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CloudSyncManager {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _connectedEmail = MutableStateFlow<String?>(null)
    val connectedEmail: StateFlow<String?> = _connectedEmail.asStateFlow()

    suspend fun connect() {
        _isSyncing.value = true
        try {
            delay(1500) // Simulated network delay for OAuth flow
            _connectedEmail.value = "neal@overbay.app"
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun disconnect() {
        _isSyncing.value = true
        try {
            delay(800)
            _connectedEmail.value = null
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun triggerSync(filesDir: String) {
        if (_connectedEmail.value == null) return
        _isSyncing.value = true
        delay(2000) // Simulated sync (scanning library.json, checking remote versions)
        _isSyncing.value = false
    }
}
