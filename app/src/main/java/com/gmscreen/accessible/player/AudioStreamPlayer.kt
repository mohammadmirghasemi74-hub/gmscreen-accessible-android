package com.gmscreen.accessible.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.gmscreen.accessible.data.model.Channel
import com.gmscreen.accessible.service.GMScreenBackgroundService
import com.gmscreen.accessible.util.AppLogger
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(UnstableApi::class)
object AudioStreamPlayer {
    private const val TAG = "AudioStreamPlayer"

    private var exoPlayer: ExoPlayer? = null
    var currentChannel: Channel? = null
        private set
    var isPlaying: Boolean = false
        private set
    var isStreaming: Boolean = false
        private set

    interface AudioPlayerListener {
        fun onPlaybackStateChanged(isPlaying: Boolean, channel: Channel?)
    }

    private val listeners = CopyOnWriteArrayList<AudioPlayerListener>()

    fun addListener(listener: AudioPlayerListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: AudioPlayerListener) {
        listeners.remove(listener)
    }

    fun play(context: Context, httpUrl: String, channel: Channel) {
        currentChannel = channel
        isStreaming = true

        if (exoPlayer == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            exoPlayer = ExoPlayer.Builder(context.applicationContext)
                .setAudioAttributes(audioAttributes, true)
                .build().apply {
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                            AppLogger.i(TAG, "Audio player isPlaying changed to $playing for ${currentChannel?.serviceName}")
                            notifyListeners(playing, currentChannel)
                            GMScreenBackgroundService.updateAudioPlaybackState(playing, currentChannel)
                        }
                    })
                }
        }

        try {
            val mediaItem = MediaItem.fromUri(Uri.parse(httpUrl))
            exoPlayer?.apply {
                setMediaItem(mediaItem)
                prepare()
                play()
            }
            isPlaying = true
            notifyListeners(true, channel)
            GMScreenBackgroundService.updateAudioPlaybackState(true, channel)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error starting AudioStreamPlayer: ${e.message}", e)
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun stop() {
        try {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            exoPlayer?.release()
        } catch (_: Exception) {}
        exoPlayer = null
        isPlaying = false
        isStreaming = false
        val prev = currentChannel
        currentChannel = null
        notifyListeners(false, null)
        StreamRecorderManager.stopPlayback()
        GMScreenBackgroundService.updateAudioPlaybackState(false, null)
    }

    private fun notifyListeners(playing: Boolean, channel: Channel?) {
        for (l in listeners) {
            try {
                l.onPlaybackStateChanged(playing, channel)
            } catch (_: Exception) {}
        }
    }
}
