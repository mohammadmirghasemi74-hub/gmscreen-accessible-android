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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

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
    private var satIpSession: SatIpSession? = null
    private var relayJob: Job? = null
    private var inSocket: DatagramSocket? = null
    private var outSocket: DatagramSocket? = null
    private val playerScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "SatIpStreamPlayer"
        private const val STB_STREAM_PORT = 5114
        private const val LOCAL_RELAY_PORT = 5118
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

    fun playChannel(stbClient: StbClient, stbIp: String, channel: Channel) {
        stop()

        playerScope.launch {
            try {
                // 1. Wake STB SAT>IP server daemon
                stbClient.sendCmd(1009, expectReply = false)
                delay(300)

                // 2. Build ALi SAT>IP query
                val query = stbClient.buildSatIpQuery(channel)
                if (query.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        listener?.onPlaybackError("اطلاعات فرکانس و ترنسپوندر کانال یافت نشد")
                    }
                    return@launch
                }

                // 3. Start local UDP relay (strips 12-byte RTP header and forwards pure MPEG-TS)
                startRelay()

                // 4. Start RTSP Session (SETUP + PLAY)
                val session = SatIpSession(stbIp, clientPort = STB_STREAM_PORT)
                val ok = session.start(query)
                if (!ok) {
                    stop()
                    withContext(Dispatchers.Main) {
                        listener?.onPlaybackError("عدم دریافت سیگنال استریم از رسیور")
                    }
                    return@launch
                }
                satIpSession = session

                withContext(Dispatchers.Main) {
                    initializePlayer()

                    val mediaItem = MediaItem.fromUri(Uri.parse("udp://127.0.0.1:$LOCAL_RELAY_PORT"))
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

    private fun startRelay() {
        stopRelay()
        try {
            val iSock = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(STB_STREAM_PORT))
                soTimeout = 2000
                receiveBufferSize = 2 * 1024 * 1024
            }
            val oSock = DatagramSocket()
            inSocket = iSock
            outSocket = oSock

            val dest = InetSocketAddress("127.0.0.1", LOCAL_RELAY_PORT)

            relayJob = playerScope.launch {
                val buf = ByteArray(4096)
                val packet = DatagramPacket(buf, buf.size)
                while (isActive) {
                    try {
                        iSock.receive(packet)
                        val len = packet.length
                        if (len <= 0) continue

                        // Check if RTP packet (12-byte header, sync byte 0x47 at index 12)
                        val offset = if (len > 12 && (buf[0].toInt() and 0xC0) == 0x80 && buf[12] == 0x47.toByte()) {
                            12
                        } else {
                            0
                        }
                        val tsLen = len - offset
                        if (tsLen > 0) {
                            val forwardPkt = DatagramPacket(buf, offset, tsLen, dest)
                            oSock.send(forwardPkt)
                        }
                    } catch (_: Exception) {
                        if (!isActive) break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRelay() {
        relayJob?.cancel()
        relayJob = null
        try { inSocket?.close() } catch (_: Exception) {}
        try { outSocket?.close() } catch (_: Exception) {}
        inSocket = null
        outSocket = null
    }

    fun stop() {
        stopRelay()
        satIpSession?.stop()
        satIpSession = null
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
