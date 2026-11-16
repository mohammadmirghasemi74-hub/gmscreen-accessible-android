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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(UnstableApi::class)
class SatIpStreamPlayer(
    private val context: Context,
    private val playerView: PlayerView,
    private val listener: PlayerEventListener? = null
) : Player.Listener, StreamRecorderManager.StreamStateListener {

    interface PlayerEventListener {
        fun onPlaybackStarted()
        fun onPlaybackBuffering()
        fun onPlaybackEnded()
        fun onPlaybackError(errorMsg: String)
    }

    private var exoPlayer: ExoPlayer? = null
    private val playerScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "SatIpStreamPlayer"
    }

    init {
        StreamRecorderManager.addListener(this)
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

        playerScope.launch {
            try {
                listener?.onPlaybackBuffering()
                val httpUrl = StreamRecorderManager.startStream(context, stbClient, stbIp, channel)
                if (httpUrl.isNullOrBlank()) {
                    listener?.onPlaybackError("خطا در برقراری اتصال استریم زنده")
                    return@launch
                }

                initializePlayer()
                val mediaItem = MediaItem.fromUri(Uri.parse(httpUrl))
                exoPlayer?.apply {
                    setMediaItem(mediaItem)
                    prepare()
                    play()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing stream", e)
                listener?.onPlaybackError("خطا در پخش استریم: ${e.message}")
            }
        }
    }

    fun stop() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        StreamRecorderManager.stopStream(keepRecording = StreamRecorderManager.isRecording)
    }

    fun release() {
        StreamRecorderManager.removeListener(this)
        stop()
        playerScope.cancel()
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
        Log.e(TAG, "ExoPlayer playback error", error)
        val msg = "خطا در دیکد استریم: ${error.localizedMessage ?: "فرمت تصویر یا صدای کانال پشتیبانی نشد"}"
        listener?.onPlaybackError(msg)
    }

    // StreamRecorderManager Callbacks
    override fun onStreamingStarted(httpUrl: String) {}
    override fun onStreamingStopped() {
        playerScope.launch {
            exoPlayer?.stop()
            listener?.onPlaybackEnded()
        }
    }
    override fun onRecordingStarted(file: File) {}
    override fun onRecordingProgress(bytesWritten: Long) {}
    override fun onRecordingStopped(file: File, totalBytes: Long) {}
    override fun onError(error: String) {
        playerScope.launch {
            listener?.onPlaybackError(error)
        }
    }
}
