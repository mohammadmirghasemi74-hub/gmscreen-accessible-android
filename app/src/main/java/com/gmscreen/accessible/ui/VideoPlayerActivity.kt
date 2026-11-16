package com.gmscreen.accessible.ui

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
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
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class VideoPlayerActivity : AppCompatActivity(), Player.Listener {

    private lateinit var playerView: PlayerView
    private lateinit var btnExitPlayer: MaterialButton
    private lateinit var txtPlayerOverlay: TextView

    private var exoPlayer: ExoPlayer? = null
    private var channelName: String = "شبکه"
    private var serviceId: String = ""

    private val repository get() = GMScreenApplication.instance.repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_video_player)

        playerView = findViewById(R.id.playerViewFullscreen)
        btnExitPlayer = findViewById(R.id.btnExitPlayer)
        txtPlayerOverlay = findViewById(R.id.txtPlayerOverlay)

        channelName = intent.getStringExtra("EXTRA_CHANNEL_NAME") ?: "شبکه"
        serviceId = intent.getStringExtra("EXTRA_SERVICE_ID") ?: ""

        val initialMsg = "در حال بارگذاری پخش زنده $channelName..."
        txtPlayerOverlay.text = initialMsg
        AccessibilityHelper.announce(playerView, "صفحه پخش تمام‌صفحه باز شد. در حال اتصال به $channelName. برای خروج و توقف، کلید برگشت را بزنید.")

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
            txtPlayerOverlay.text = "خطا: اطلاعات شبکه یافت نشد"
            AccessibilityHelper.announce(playerView, "خطا در یافتن مشخصات شبکه")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            // First stop any previous stream cleanly
            StreamRecorderManager.stopStream(keepRecording = false)
            kotlinx.coroutines.delay(250)

            // Tune channel on STB if not already active
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
                    txtPlayerOverlay.text = "در حال پخش: $channelName"
                } else if (!isFinishing) {
                    txtPlayerOverlay.text = "خطا در برقراری ارتباط استریم با رسیور"
                    AccessibilityHelper.announce(playerView, "خطا در برقراری ارتباط استریم با رسیور")
                }
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                txtPlayerOverlay.text = "در حال بافر استریم: $channelName"
            }
            Player.STATE_READY -> {
                txtPlayerOverlay.text = "پخش زنده فعال: $channelName"
                AccessibilityHelper.announce(playerView, "پخش زنده $channelName آغاز شد")
            }
            Player.STATE_ENDED -> {
                txtPlayerOverlay.text = "پخش به پایان رسید"
            }
            Player.STATE_IDLE -> {}
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        txtPlayerOverlay.text = "خطا در دیکد تصویر یا صدا"
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
