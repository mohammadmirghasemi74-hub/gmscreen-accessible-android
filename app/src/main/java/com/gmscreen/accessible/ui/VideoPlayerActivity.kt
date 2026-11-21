package com.gmscreen.accessible.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.button.MaterialButton
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.player.StreamRecorderManager
import com.gmscreen.accessible.util.AccessibilityHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class VideoPlayerActivity : AppCompatActivity(), Player.Listener {

    private lateinit var playerView: PlayerView
    private lateinit var btnClosePlayer: MaterialButton
    private lateinit var txtChannelBadge: TextView
    private lateinit var btnToggleRecordInPlayer: MaterialButton

    private var exoPlayer: ExoPlayer? = null
    private var channelName: String = "شبکه"
    private var serviceId: String = ""

    private val repository get() = GMScreenApplication.instance.repository
    private val appConfig get() = GMScreenApplication.instance.appConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Make true immersive edge-to-edge fullscreen
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Set orientation based on user configuration
        requestedOrientation = if (appConfig.videoLandscapeOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        setContentView(R.layout.activity_video_player)

        playerView = findViewById(R.id.playerViewFullscreen)
        btnClosePlayer = findViewById(R.id.btnClosePlayer)
        txtChannelBadge = findViewById(R.id.txtChannelBadge)
        btnToggleRecordInPlayer = findViewById(R.id.btnToggleRecordInPlayer)

        channelName = intent.getStringExtra("EXTRA_CHANNEL_NAME") ?: "شبکه"
        serviceId = intent.getStringExtra("EXTRA_SERVICE_ID") ?: ""

        txtChannelBadge.text = "🔴 $channelName"

        btnClosePlayer.setOnClickListener {
            AccessibilityHelper.announce(playerView, "بستن پخش‌کننده")
            finish()
        }

        val welcomeMsg = "پخش ویدیویی تمام‌صفحه شبکه $channelName فعال شد. دکمه بستن و دکمه ضبط در بالای صفحه در دسترس است."
        AccessibilityHelper.announce(playerView, welcomeMsg)

        // Accessibility actions for TalkBack
        androidx.core.view.ViewCompat.setAccessibilityDelegate(playerView, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: android.view.View, info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(
                    androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_CLICK,
                        "بستن پخش‌کننده و بازگشت"
                    )
                )
                info.addAction(
                    androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        R.id.action_toggle_record,
                        "شروع یا توقف ضبط گوشی"
                    )
                )
            }

            override fun performAccessibilityAction(host: android.view.View, action: Int, args: Bundle?): Boolean {
                if (action == androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_CLICK) {
                    finish()
                    return true
                }
                if (action == R.id.action_toggle_record) {
                    toggleRecording()
                    return true
                }
                return super.performAccessibilityAction(host, action, args)
            }
        })

        setupRecordButton()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        initExoPlayer()
        startPlayback()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setupRecordButton() {
        val isCurrentlyRec = StreamRecorderManager.isRecording && StreamRecorderManager.currentChannel?.serviceId == serviceId
        updateRecordButtonUi(isCurrentlyRec)

        btnToggleRecordInPlayer.setOnClickListener {
            toggleRecording()
        }
    }

    private fun toggleRecording() {
        val channel = repository.channels.value.find { it.serviceId == serviceId }
        val ip = repository.connectedIp.value
        if (channel == null || ip.isBlank()) {
            Toast.makeText(this, "مشخصات شبکه جهت ضبط یافت نشد", Toast.LENGTH_SHORT).show()
            return
        }

        if (StreamRecorderManager.isRecording) {
            lifecycleScope.launch(Dispatchers.IO) {
                val file = StreamRecorderManager.stopRecording()
                withContext(Dispatchers.Main) {
                    updateRecordButtonUi(false)
                    val msg = "ضبط ویدیو متوقف و با موفقیت ذخیره شد: ${file?.name ?: ""}"
                    AccessibilityHelper.announce(playerView, msg)
                    Toast.makeText(this@VideoPlayerActivity, msg, Toast.LENGTH_LONG).show()
                }
            }
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                val file = StreamRecorderManager.startRecording(
                    this@VideoPlayerActivity,
                    repository.client,
                    ip,
                    channel,
                    isAudioOnly = false
                )
                withContext(Dispatchers.Main) {
                    if (file != null) {
                        updateRecordButtonUi(true)
                        val msg = "ضبط همزمان ویدیویی شبکه ${channel.serviceName} روی گوشی آغاز شد"
                        AccessibilityHelper.announce(playerView, msg)
                        Toast.makeText(this@VideoPlayerActivity, msg, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@VideoPlayerActivity, "خطا در شروع ضبط همزمان", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun updateRecordButtonUi(isRecording: Boolean) {
        if (isRecording) {
            btnToggleRecordInPlayer.text = "■ توقف ضبط"
            btnToggleRecordInPlayer.contentDescription = "در حال ضبط مستقیم روی گوشی. کلیک برای توقف و ذخیره"
            btnToggleRecordInPlayer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#CCB71C1C"))
            btnToggleRecordInPlayer.alpha = 0.9f
        } else {
            val vidFmt = appConfig.videoRecFormat.uppercase()
            btnToggleRecordInPlayer.text = "● ضبط ($vidFmt)"
            btnToggleRecordInPlayer.contentDescription = "شروع ضبط ویدیویی با فرمت $vidFmt بر روی حافظه گوشی"
            btnToggleRecordInPlayer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#66000000"))
            btnToggleRecordInPlayer.alpha = 0.65f
        }
    }

    private fun initExoPlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .build()
            .apply {
                addListener(this@VideoPlayerActivity)
                playWhenReady = true
            }
        playerView.player = exoPlayer
    }

    private fun startPlayback() {
        val ip = repository.connectedIp.value
        val channel = repository.channels.value.find { it.serviceId == serviceId }
        if (ip.isBlank() || channel == null) {
            AccessibilityHelper.announce(playerView, "خطا در یافتن مشخصات شبکه")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val isAlreadyStreamingSameChannel = StreamRecorderManager.isStreaming && StreamRecorderManager.currentChannel?.serviceId == channel.serviceId
            if (!isAlreadyStreamingSameChannel) {
                StreamRecorderManager.stopStream(keepRecording = false)
                kotlinx.coroutines.delay(250)
                repository.switchChannel(channel)
                kotlinx.coroutines.delay(300)
            }

            val httpUrl = StreamRecorderManager.startStream(
                this@VideoPlayerActivity,
                repository.client,
                ip,
                channel
            )

            withContext(Dispatchers.Main) {
                if (!isFinishing && httpUrl != null) {
                    val mediaItem = MediaItem.fromUri(Uri.parse(httpUrl))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && appConfig.separateAudioToBluetooth) {
                        try {
                            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                            val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                            val btDevice = devices?.firstOrNull {
                                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                            }
                            if (btDevice != null) {
                                exoPlayer?.setPreferredAudioDevice(btDevice)
                            }
                        } catch (_: Exception) {}
                    }
                    exoPlayer?.apply {
                        setMediaItem(mediaItem)
                        prepare()
                        play()
                    }
                } else if (!isFinishing) {
                    AccessibilityHelper.announce(playerView, "خطا در برقراری ارتباط استریم با رسیور")
                }
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {}
            Player.STATE_READY -> {
                AccessibilityHelper.announce(playerView, "پخش زنده $channelName فعال است")
            }
            Player.STATE_ENDED -> {
                finish()
            }
            Player.STATE_IDLE -> {}
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        AccessibilityHelper.announce(playerView, "خطا در پخش تصویر یا صدای شبکه")
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.removeListener(this)
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        playerView.player = null
        StreamRecorderManager.stopPlayback()
    }
}
