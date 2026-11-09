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
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import com.gmscreen.accessible.data.model.Channel

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
    private var currentChannel: Channel? = null

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

    fun playChannel(stbIp: String, channel: Channel) {
        currentChannel = channel
        val rtspUrl = channel.toSatIpUrl(stbIp)
        Log.d(TAG, "Playing SAT>IP RTSP URL: $rtspUrl for channel ${channel.serviceName}")

        initializePlayer()

        val mediaItem = MediaItem.fromUri(Uri.parse(rtspUrl))
        val mediaSource = RtspMediaSource.Factory()
            .setDebugLoggingEnabled(false)
            .setTimeoutMs(8000)
            .createMediaSource(mediaItem)

        exoPlayer?.apply {
            setMediaSource(mediaSource)
            prepare()
            play()
        }
    }

    fun stop() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
    }

    fun release() {
        exoPlayer?.removeListener(this)
        exoPlayer?.release()
        exoPlayer = null
        playerView.player = null
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
        Log.e(TAG, "ExoPlayer RTSP playback error", error)
        val msg = "خطا در پخش استریم زنده: ${error.localizedMessage ?: "عدم پاسخ از تیونر رسیور"}"
        listener?.onPlaybackError(msg)
    }
}
