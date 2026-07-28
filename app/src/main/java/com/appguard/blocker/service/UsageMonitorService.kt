package com.appguard.blocker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.ui.MainActivity
import com.appguard.blocker.util.PermissionHelper

/**
 * Foreground service that polls UsageStats for the current foreground app.
 * Works together with Accessibility for Kaspersky-like coverage without Device Owner.
 */
class UsageMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: PrefsRepository
    private var lastFg: String? = null

    private val tick = object : Runnable {
        override fun run() {
            try {
                checkForeground()
            } catch (_: Exception) {
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsRepository(this)
        createChannel()
        startAsForeground()
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::prefs.isInitialized) prefs = PrefsRepository(this)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun checkForeground() {
        if (!prefs.allowlistEnabled && !prefs.installBlockEnabled) return
        if (!PermissionHelper.usageAccessGranted(this)) return

        val fg = queryForegroundPackage() ?: return
        if (fg == lastFg) return
        lastFg = fg

        if (BlockCoordinator.shouldBlockApp(this, fg)) {
            BlockCoordinator.blockApp(this, fg)
        }
    }

    private fun queryForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 15_000
        val events = usm.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = event.eventType
            val isFg = type == UsageEvents.Event.ACTIVITY_RESUMED ||
                type == 1 /* MOVE_TO_FOREGROUND legacy */
            if (isFg) {
                last = event.packageName
            }
        }
        return last
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.monitor_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.monitor_channel_desc)
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun startAsForeground() {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "app_guard_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_MS = 700L

        fun start(context: Context) {
            val intent = Intent(context, UsageMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UsageMonitorService::class.java))
        }
    }
}
