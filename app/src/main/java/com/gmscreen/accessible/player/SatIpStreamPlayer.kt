package com.gmscreen.accessible.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

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
    private var inSocket: DatagramSocket? = null
    private var httpServer: ServerSocket? = null
    private var serverJob: Job? = null
    private var streamJob: Job? = null
    private val playerScope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var activeClientOut: OutputStream? = null

    companion object {
        private const val TAG = "SatIpStreamPlayer"
        private const val STB_STREAM_PORT = 5114
        private const val LOCAL_HTTP_PORT = 8899
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
                delay(200)

                // 2. Build ALi SAT>IP query
                val query = stbClient.buildSatIpQuery(channel)
                if (query.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        listener?.onPlaybackError("اطلاعات فرکانس و ترنسپوندر کانال یافت نشد")
                    }
                    return@launch
                }

                // 3. Start local HTTP TS streaming server & UDP receiver
                startHttpServerAndUdp()

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

                // Wait 200ms for STB stream packets to arrive
                delay(200)

                withContext(Dispatchers.Main) {
                    initializePlayer()

                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.parse("http://127.0.0.1:$LOCAL_HTTP_PORT/live.ts"))
                        .setMimeType(MimeTypes.VIDEO_MP2T)
                        .build()

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

    private fun startHttpServerAndUdp() {
        stopHttpServerAndUdp()
        try {
            // Setup UDP socket listening for STB RTP packets
            val iSock = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(STB_STREAM_PORT))
                soTimeout = 2000
                receiveBufferSize = 4 * 1024 * 1024
            }
            inSocket = iSock

            // Start UDP reader loop
            streamJob = playerScope.launch {
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
                            val client = activeClientOut
                            if (client != null) {
                                try {
                                    client.write(buf, offset, tsLen)
                                } catch (_: Exception) {
                                    activeClientOut = null
                                }
                            }
                        }
                    } catch (_: Exception) {
                        if (!isActive) break
                    }
                }
            }

            // Setup lightweight local HTTP server
            val sServer = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress("127.0.0.1", LOCAL_HTTP_PORT))
            }
            httpServer = sServer

            serverJob = playerScope.launch {
                while (isActive) {
                    try {
                        val clientSocket: Socket = sServer.accept()
                        handleHttpClient(clientSocket)
                    } catch (_: Exception) {
                        if (!isActive) break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleHttpClient(socket: Socket) {
        playerScope.launch {
            try {
                val reader = socket.getInputStream().bufferedReader()
                // Read HTTP request line
                var line: String? = reader.readLine()
                while (!line.isNullOrBlank()) {
                    line = reader.readLine()
                }

                val out = socket.getOutputStream()
                val responseHeaders = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: video/mp2t\r\n" +
                        "Connection: close\r\n" +
                        "Access-Control-Allow-Origin: *\r\n\r\n"
                out.write(responseHeaders.toByteArray(Charsets.UTF_8))
                out.flush()

                activeClientOut = out
            } catch (e: Exception) {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    private fun stopHttpServerAndUdp() {
        activeClientOut = null
        streamJob?.cancel()
        streamJob = null
        serverJob?.cancel()
        serverJob = null
        try { inSocket?.close() } catch (_: Exception) {}
        try { httpServer?.close() } catch (_: Exception) {}
        inSocket = null
        httpServer = null
    }

    fun stop() {
        stopHttpServerAndUdp()
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
