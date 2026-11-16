package com.gmscreen.accessible.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.gmscreen.accessible.data.model.Channel
import com.gmscreen.accessible.protocol.StbClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class SatIpStreamPlayer(
    private val context: Context,
    private val playerView: PlayerView,
    private val listener: PlayerEventListener? = null
) : Player.Listener {

    interface PlayerEventListener {
        fun onPlaybackStarted()
        fun onPlaybackBuffering()
        fun onPlaybackEnded()
        fun onPlaybackError(errorMsg: String)
    }

    private var exoPlayer: ExoPlayer? = null
    private val playerScope = CoroutineScope(Dispatchers.IO)
    private var currentStbClient: StbClient? = null

    companion object {
        private const val TAG = "SatIpStreamPlayer"
    }

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(this@SatIpStreamPlayer)
                playWhenReady = true
            }
            playerView.player = exoPlayer
        }
    }

    fun playChannel(stbClient: StbClient, stbIp: String, channel: Channel, isAudioOnly: Boolean = false) {
        stop()
        currentStbClient = stbClient

        playerScope.launch {
            try {
                // 1. Wake STB RTSP server daemon on port 20000 (Request 1009)
                stbClient.sendCmd(1009, expectReply = false)
                delay(300)

                // 2. Build ALi SAT>IP query string
                val query = stbClient.buildSatIpQuery(channel)
                if (query.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        listener?.onPlaybackError("اطلاعات فرکانس و ترنسپوندر کانال یافت نشد")
                    }
                    return@launch
                }

                val rtspUrl = "rtsp://$stbIp:554/$query"
                Log.d(TAG, "Playing Native RTSP Stream: $rtspUrl")

                withContext(Dispatchers.Main) {
                    initializePlayer()

                    val mediaItem = MediaItem.fromUri(Uri.parse(rtspUrl))
                    exoPlayer?.apply {
                        setMediaItem(mediaItem)
                        prepare()
                        play()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    listener?.onPlaybackError("خطا در راه‌اندازی استریم: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        val client = currentStbClient
        if (client != null) {
            playerScope.launch {
                try {
                    // Send Request 1012 to deactivate STB RTSP daemon & free hardware tuner
                    client.sendCmd(1012, expectReply = true, timeoutMs = 2000)
                } catch (_: Exception) {}
            }
        }
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
        }
    }

    fun release() {
        stop()
        playerScope.cancel()
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            exoPlayer?.removeListener(this@SatIpStreamPlayer)
            exoPlayer?.release()
            exoPlayer = null
            playerView.player = null
        }
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> listener?.onPlaybackBuffering()
            Player.STATE_READY -> listener?.onPlaybackStarted()
            Player.STATE_ENDED -> listener?.onPlaybackEnded()
            Player.STATE_IDLE -> {}
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "ExoPlayer playback error", error)
        val msg = "خطا در پخش استریم زنده: ${error.localizedMessage ?: "عدم پاسخ از تیونر رسیور"}"
        listener?.onPlaybackError(msg)
    }
}
