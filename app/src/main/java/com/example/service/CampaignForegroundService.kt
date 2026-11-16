package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class CampaignForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            isRunningForeground = false
            releaseWakeLock()
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "CloakDeck Fleet Campaign"
        val body = intent?.getStringExtra(EXTRA_BODY) ?: "Running humanized browser profiles..."
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
        val max = intent?.getIntExtra(EXTRA_MAX, 100) ?: 100

        acquireWakeLock()
        val notification = buildNotification(this, title, body, progress, max)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isRunningForeground = true
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunningForeground = false
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CloakDeck::FleetRunner").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Mass Fleet & Automation",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress for batch profile browsing and CAPTCHA automation"
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "cloakdeck_fleet_channel"
        private const val NOTIFICATION_ID = 4401
        private const val ACTION_STOP = "com.example.service.ACTION_STOP_FLEET"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_BODY = "extra_body"
        private const val EXTRA_PROGRESS = "extra_progress"
        private const val EXTRA_MAX = "extra_max"

        @Volatile
        private var isRunningForeground = false

        @Volatile
        private var lastNotifyMs = 0L

        private fun buildNotification(
            context: Context,
            title: String,
            body: String,
            progress: Int,
            max: Int
        ): Notification {
            val appCtx = context.applicationContext
            val openAppIntent = Intent(appCtx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingOpen = PendingIntent.getActivity(
                appCtx,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val stopIntent = Intent(appCtx, CampaignForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            val pendingStop = PendingIntent.getService(
                appCtx,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(appCtx, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingOpen)
                .setProgress(max.coerceAtLeast(1), progress.coerceIn(0, max.coerceAtLeast(1)), max <= 0)
                .addAction(0, "Stop", pendingStop)
                .build()
        }

        fun updateProgress(context: Context, title: String, body: String, progress: Int, max: Int) {
            val appCtx = context.applicationContext
            val now = System.currentTimeMillis()
            if (isRunningForeground) {
                // Directly update active notification on a throttled cadence instead of spamming startForegroundService
                if (now - lastNotifyMs < 750L && progress < max) return
                lastNotifyMs = now
                runCatching {
                    val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.notify(NOTIFICATION_ID, buildNotification(appCtx, title, body, progress, max))
                }
                return
            }

            lastNotifyMs = now
            isRunningForeground = true
            val intent = Intent(appCtx, CampaignForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_MAX, max)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            }.onFailure {
                isRunningForeground = false
            }
        }

        fun stop(context: Context) {
            if (!isRunningForeground) return
            isRunningForeground = false
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, CampaignForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { appCtx.startService(intent) }
        }
    }
}
