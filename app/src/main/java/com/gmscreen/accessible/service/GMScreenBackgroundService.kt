package com.gmscreen.accessible.service

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
import androidx.core.app.NotificationCompat
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.ui.MainActivity
import com.gmscreen.accessible.util.AppLogger
import com.gmscreen.accessible.util.TtsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GMScreenBackgroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastObservedPid: String? = null

    companion object {
        const val CHANNEL_ID = "gmscreen_bg_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.gmscreen.accessible.action.START_BG_SERVICE"
        const val ACTION_STOP = "com.gmscreen.accessible.action.STOP_BG_SERVICE"

        @Volatile
        var isServiceRunning = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, GMScreenBackgroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, GMScreenBackgroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        TtsHelper.init(applicationContext)
        createNotificationChannel()
        startForegroundWithNotification("پایش کانال‌ها و زمان‌بندی‌های ضبط در پس‌زمینه فعال است")
        observeChannelChanges()
        AppLogger.i("GMScreenBackgroundService", "Background foreground service created and started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            try {
                GMScreenApplication.instance.repository.disconnect()
            } catch (_: Exception) {}
            MainActivity.activeInstance?.finishAffinity()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "سرویس پس‌زمینه GMScreen",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "فعال ماندن ارتباط با رسیور و اعلام صوتی تغییر کانال‌ها در پس‌زمینه"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, GMScreenBackgroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GMScreen در پس‌زمینه فعال است")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pOpenIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "خروج کامل", pStopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startForegroundWithNotification(statusText: String) {
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(statusText: String) {
        val notification = buildNotification(statusText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun observeChannelChanges() {
        val repository = GMScreenApplication.instance.repository
        serviceScope.launch {
            repository.currentChannel.collectLatest { ch ->
                if (ch != null && ch.serviceId.isNotBlank()) {
                    if (ch.serviceId != lastObservedPid) {
                        val isInitial = (lastObservedPid == null)
                        lastObservedPid = ch.serviceId
                        
                        val type = if (ch.isRadio) "رادیو" else "شبکه"
                        val channelStr = "$type ${ch.serviceName}"
                        updateNotification("کانال فعلی: $channelStr")

                        // Speak channel aloud via TTS if this change happened after initial start
                        if (!isInitial) {
                            AppLogger.i("GMScreenBackgroundService", "Announcing channel in background: $channelStr")
                            TtsHelper.speak(channelStr, interrupt = true)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceScope.cancel()
        AppLogger.i("GMScreenBackgroundService", "Background foreground service destroyed")
    }
}
