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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.data.StbRepository
import com.gmscreen.accessible.data.model.Channel
import com.gmscreen.accessible.player.AudioStreamPlayer
import com.gmscreen.accessible.player.StreamRecorderManager
import com.gmscreen.accessible.protocol.RcuKey
import com.gmscreen.accessible.ui.MainActivity
import com.gmscreen.accessible.util.AccessibilityHelper
import com.gmscreen.accessible.util.AppLogger
import com.gmscreen.accessible.util.TtsHelper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class GMScreenBackgroundService : Service(), StreamRecorderManager.StreamStateListener {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastObservedPid: String? = null

    companion object {
        const val CHANNEL_ID = "gmscreen_bg_channel"
        const val REC_CHANNEL_ID = "gmscreen_rec_channel"
        const val NOTIFICATION_ID = 1001
        const val REC_NOTIFICATION_ID = 2001
        const val STB_REC_NOTIFICATION_ID = 2002

        const val ACTION_START = "com.gmscreen.accessible.action.START_BG_SERVICE"
        const val ACTION_STOP = "com.gmscreen.accessible.action.STOP_BG_SERVICE"
        const val ACTION_AUDIO_TOGGLE = "com.gmscreen.accessible.action.AUDIO_TOGGLE"
        const val ACTION_AUDIO_STOP = "com.gmscreen.accessible.action.AUDIO_STOP"

        @Volatile
        var isServiceRunning = false
            private set

        @Volatile
        private var isAudioStreaming = false
        @Volatile
        private var audioPlayingChannel: Channel? = null
        @Volatile
        private var isAudioPlaying = false

        private var activeServiceInstance: GMScreenBackgroundService? = null

        fun startService(context: Context) {
            val intent = Intent(context, GMScreenBackgroundService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                AppLogger.e("GMScreenBackgroundService", "Failed to start service: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, GMScreenBackgroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.stopService(intent)
            } catch (_: Exception) {}
        }

        fun updateAudioPlaybackState(isPlaying: Boolean, channel: Channel?) {
            isAudioPlaying = isPlaying
            isAudioStreaming = (channel != null)
            audioPlayingChannel = channel
            activeServiceInstance?.updateServiceNotification()
        }

        fun notifyStbRecordingState(isRecording: Boolean, channelName: String) {
            activeServiceInstance?.handleStbRecordingState(isRecording, channelName)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        activeServiceInstance = this

        TtsHelper.init(applicationContext)
        createNotificationChannels()
        startForegroundWithNotification()

        StreamRecorderManager.addListener(this)
        observeChannelChanges()
        observeReconnectEvents()

        AppLogger.i("GMScreenBackgroundService", "Background foreground service created and active")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                try {
                    GMScreenApplication.instance.repository.disconnect()
                } catch (_: Exception) {}
                MainActivity.activeInstance?.finishAffinity()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_AUDIO_TOGGLE -> {
                AudioStreamPlayer.togglePlayPause()
                updateServiceNotification()
            }
            ACTION_AUDIO_STOP -> {
                AudioStreamPlayer.stop()
                updateServiceNotification()
            }
            ACTION_START -> {
                updateServiceNotification()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            // 1. Ongoing background service channel (Low importance, silent)
            val bgChannel = NotificationChannel(
                CHANNEL_ID,
                "سرویس پس‌زمینه GMScreen",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "پایش ارتباط با رسیور، اجرای زمان‌بندی‌ها و اعلام صوتی کانال‌ها"
                setShowBadge(false)
            }
            manager.createNotificationChannel(bgChannel)

            // 2. High priority notification channel for recording alerts (Heads-up)
            val recChannel = NotificationChannel(
                REC_CHANNEL_ID,
                "اعلان‌های مهم ضبط GMScreen",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "نمایش شروع و پایان ضبط‌های زنده در بالای گوشی"
                setShowBadge(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(recChannel)
        }
    }

    private fun buildServiceNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopAppIntent = Intent(this, GMScreenBackgroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pStopAppIntent = PendingIntent.getService(
            this,
            1,
            stopAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pOpenIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // If background audio is active, show media actions
        if (isAudioStreaming && audioPlayingChannel != null) {
            val ch = audioPlayingChannel!!
            builder.setContentTitle("در حال پخش صوتی: ${ch.serviceName}")
            builder.setContentText(if (isAudioPlaying) "در حال پخش زنده صوتی" else "پخش متوقف موقت (Pause)")

            val toggleIntent = Intent(this, GMScreenBackgroundService::class.java).apply {
                action = ACTION_AUDIO_TOGGLE
            }
            val pToggleIntent = PendingIntent.getService(
                this,
                2,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val stopAudioIntent = Intent(this, GMScreenBackgroundService::class.java).apply {
                action = ACTION_AUDIO_STOP
            }
            val pStopAudioIntent = PendingIntent.getService(
                this,
                3,
                stopAudioIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val playPauseTitle = if (isAudioPlaying) "توقف موقت" else "ادامه پخش"
            val playPauseIcon = if (isAudioPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

            builder.addAction(playPauseIcon, playPauseTitle, pToggleIntent)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "بستن پخش صوتی", pStopAudioIntent)
        } else {
            val currCh = GMScreenApplication.instance.repository.currentChannel.value
            val statusText = if (currCh != null) {
                val type = if (currCh.isRadio) "رادیو" else "شبکه"
                "کانال فعلی: $type ${currCh.serviceName}"
            } else {
                "پایش کانال‌ها و زمان‌بندی‌های ضبط در پس‌زمینه فعال است"
            }
            builder.setContentTitle("GMScreen در پس‌زمینه فعال است")
            builder.setContentText(statusText)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "خروج کامل", pStopAppIntent)
        }

        return builder.build()
    }

    private fun startForegroundWithNotification() {
        val notification = buildServiceNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            }
            startForeground(NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun updateServiceNotification() {
        val notification = buildServiceNotification()
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
                        val numStr = if (ch.realChannelNum > 0) "شماره ${ch.realChannelNum}" else ""
                        val channelStr = "$type ${ch.serviceName} $numStr".trim()
                        updateServiceNotification()

                        // Only announce via TTS if the app is in the background.
                        // When in foreground, ChannelsFragment handles the TalkBack screen reader announcement!
                        if (!isInitial && !GMScreenApplication.instance.isAppInForeground) {
                            AppLogger.i("GMScreenBackgroundService", "Announcing channel in background: $channelStr")
                            TtsHelper.speak(channelStr, interrupt = true)

                            if (GMScreenApplication.instance.appConfig.backgroundAnnounceEpg) {
                                serviceScope.launch(Dispatchers.IO) {
                                    try {
                                        // Wait 1200ms for STB tuner transponder lock & EIT table parsing
                                        delay(1200)
                                        var epgData = repository.getEpg(ch.serviceId)
                                        var (cur, nxt) = com.gmscreen.accessible.util.EpgHelper.extractCurrentAndNextEvent(epgData)
                                        if (cur == null && nxt == null) {
                                            // Retry once after 1000ms if STB hadn't populated EPG yet
                                            delay(1000)
                                            epgData = repository.getEpg(ch.serviceId)
                                            val pair = com.gmscreen.accessible.util.EpgHelper.extractCurrentAndNextEvent(epgData)
                                            cur = pair.first
                                            nxt = pair.second
                                        }
                                        val epgMsg = com.gmscreen.accessible.util.EpgHelper.buildEpgAnnouncement(
                                            cur,
                                            nxt,
                                            GMScreenApplication.instance.appConfig.epgAnnounceDetail
                                        )
                                        if (!epgMsg.isNullOrBlank() && !GMScreenApplication.instance.isAppInForeground) {
                                            TtsHelper.speak(epgMsg, interrupt = false)
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeReconnectEvents() {
        val repository = GMScreenApplication.instance.repository
        serviceScope.launch {
            repository.reconnectEvent.collect { event ->
                val inBg = !GMScreenApplication.instance.isAppInForeground
                when (event) {
                    is StbRepository.ReconnectEvent.Attempting -> {
                        if (inBg) {
                            TtsHelper.speak("ارتباط با رسیور قطع شد. در حال تلاش برای اتصال هوشمند مجدد...", interrupt = true)
                            if (GMScreenApplication.instance.appConfig.soundCuesEnabled) {
                                AccessibilityHelper.playDisconnectSoundCue()
                            }
                        }
                    }
                    is StbRepository.ReconnectEvent.Reconnected -> {
                        if (inBg) {
                            TtsHelper.speak("اتصال به رسیور مجدداً برقرار شد", interrupt = true)
                            if (GMScreenApplication.instance.appConfig.soundCuesEnabled) {
                                AccessibilityHelper.playSoundCue()
                            }
                        }
                        updateServiceNotification()
                    }
                    is StbRepository.ReconnectEvent.Failed -> {
                        if (inBg) {
                            TtsHelper.speak("برنامه نتوانست مجدداً به رسیور متصل شود", interrupt = true)
                            if (GMScreenApplication.instance.appConfig.soundCuesEnabled) {
                                AccessibilityHelper.playDisconnectSoundCue()
                            }
                        }
                    }
                }
            }
        }
    }

    // --- High Priority Recording Notifications & Announcements ---

    override fun onRecordingStarted(file: File) {
        val chName = StreamRecorderManager.currentChannel?.serviceName ?: "شبکه"
        val msg = "ضبط شبکه $chName روی گوشی آغاز شد"
        TtsHelper.speak(msg, interrupt = true)
        showRecordingTopNotification(
            REC_NOTIFICATION_ID,
            "در حال ضبط روی گوشی",
            "شبکه $chName (ذخیره در حافظه)"
        )
    }

    override fun onRecordingStopped(file: File, totalBytes: Long) {
        val chName = StreamRecorderManager.currentChannel?.serviceName ?: "شبکه"
        val mb = String.format(java.util.Locale.US, "%.1f", totalBytes / (1024.0 * 1024.0))
        val msg = "ضبط شبکه $chName روی گوشی پایان یافت و با حجم $mb مگابایت ذخیره شد"
        TtsHelper.speak(msg, interrupt = true)
        cancelNotification(REC_NOTIFICATION_ID)
    }

    override fun onStreamingStarted(httpUrl: String) {}
    override fun onStreamingStopped() {}
    override fun onRecordingProgress(bytesWritten: Long) {}
    override fun onError(error: String) {}

    fun handleStbRecordingState(isRecording: Boolean, channelName: String) {
        GMScreenApplication.instance.repository.setStbRecording(isRecording, channelName)
        if (isRecording) {
            val msg = "ضبط شبکه $channelName روی فلش رسیور آغاز شد"
            TtsHelper.speak(msg, interrupt = true)
            showRecordingTopNotification(
                STB_REC_NOTIFICATION_ID,
                "در حال ضبط روی فلش رسیور",
                "شبکه $channelName"
            )
        } else {
            val msg = "ضبط روی فلش رسیور متوقف شد"
            TtsHelper.speak(msg, interrupt = true)
            cancelNotification(STB_REC_NOTIFICATION_ID)
        }
    }

    private fun showRecordingTopNotification(notifId: Int, title: String, content: String) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pOpenIntent = PendingIntent.getActivity(
            this,
            notifId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notif = NotificationCompat.Builder(this, REC_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pOpenIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(notifId, notif)
    }

    private fun cancelNotification(notifId: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(notifId)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        activeServiceInstance = null

        StreamRecorderManager.removeListener(this)
        serviceScope.cancel()
        AppLogger.i("GMScreenBackgroundService", "Background foreground service destroyed")
    }
}
