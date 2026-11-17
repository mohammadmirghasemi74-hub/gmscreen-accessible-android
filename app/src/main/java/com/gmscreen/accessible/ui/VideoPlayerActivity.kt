package com.gmscreen.accessible.ui

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
    private lateinit var btnExitPlayer: ImageButton

    private var exoPlayer: ExoPlayer? = null
    private var channelName: String = "شبکه"
    private var serviceId: String = ""

    private val repository get() = GMScreenApplication.instance.repository
    private val appConfig get() = GMScreenApplication.instance.appConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Set orientation based on user configuration
        requestedOrientation = if (appConfig.videoLandscapeOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        setContentView(R.layout.activity_video_player)

        playerView = findViewById(R.id.playerViewFullscreen)
        btnExitPlayer = findViewById(R.id.btnExitPlayer)

        channelName = intent.getStringExtra("EXTRA_CHANNEL_NAME") ?: "شبکه"
        serviceId = intent.getStringExtra("EXTRA_SERVICE_ID") ?: ""

        AccessibilityHelper.announce(playerView, "پخش ویدیویی تمام‌صفحه $channelName آغاز شد. برای بازگشت کلید برگشت را بزنید.")

        btnExitPlayer.setOnClickListener {
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        initExoPlayer()
        startPlayback()
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
            StreamRecorderManager.stopStream(keepRecording = false)
            kotlinx.coroutines.delay(250)

            repository.switchChannel(channel)
            kotlinx.coroutines.delay(300)

            val httpUrl = StreamRecorderManager.startStream(
                this@VideoPlayerActivity,
                repository.client,
                ip,
                channel
            )

            withContext(Dispatchers.Main) {
                if (!isFinishing && httpUrl != null) {
                    val mediaItem = MediaItem.fromUri(Uri.parse(httpUrl))
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
        StreamRecorderManager.stopStream(keepRecording = false)
    }
}
