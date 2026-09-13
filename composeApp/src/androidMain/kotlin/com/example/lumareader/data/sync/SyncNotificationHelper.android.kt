package com.example.lumareader.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat

object SyncNotificationHelper {
    private const val CHANNEL_ID = "luma_cloud_sync_channel"
    private const val CHANNEL_NAME = "Cloud Sync"
    private const val NOTIFICATION_ID = 42001

    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun updateProgress(context: Context, progress: SyncProgress?) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (progress == null) {
            notificationManager.cancel(NOTIFICATION_ID)
            releaseWakeLock()
            return
        }

        acquireWakeLockIfNeeded(context)
        createChannelIfNeeded(notificationManager)

        val title = if (progress.isUpload) {
            "Uploading book ${progress.currentItem} of ${progress.totalItems}"
        } else {
            "Downloading book ${progress.currentItem} of ${progress.totalItems}"
        }

        val overallPercentInt = (progress.overallPercent * 100).toInt().coerceIn(0, 100)
        val text = "${progress.currentItemName} • ${(progress.itemPercent * 100).toInt()}%"

        val iconRes = if (progress.isUpload) {
            android.R.drawable.stat_sys_upload
        } else {
            android.R.drawable.stat_sys_download
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("Total: $overallPercentInt%")
            .setProgress(100, overallPercentInt, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Safely ignored if POST_NOTIFICATIONS permission not yet granted on Android 13+
        }
    }

    private fun createChannelIfNeeded(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows upload and download progress for Google Drive book synchronization"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    @Synchronized
    fun cancel(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)
        releaseWakeLock()
    }

    private fun acquireWakeLockIfNeeded(context: Context) {
        if (wakeLock == null) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LumaReader:SyncWakeLock")?.apply {
                    setReferenceCounted(false)
                    acquire(30 * 60 * 1000L) // 30 minutes safe ceiling
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            wakeLock = null
        }
    }
}
