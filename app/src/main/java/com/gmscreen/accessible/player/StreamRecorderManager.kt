package com.gmscreen.accessible.player

import android.content.Context
import android.util.Log
import com.gmscreen.accessible.data.model.Channel
import com.gmscreen.accessible.protocol.StbClient
import com.gmscreen.accessible.util.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

object StreamRecorderManager {
    private const val TAG = "StreamRecorderManager"
    private const val UDP_PORT = 5114

    private val managerScope = CoroutineScope(Dispatchers.IO)
    private var streamJob: Job? = null
    private var httpAcceptJob: Job? = null

    private var currentStbClient: StbClient? = null
    private var currentStbIp: String = ""
    private var satIpSession: SatIpSession? = null

    private var udpSocket: DatagramSocket? = null
    private var serverSocket: ServerSocket? = null
    private val httpClients = CopyOnWriteArrayList<Socket>()

    // Current State
    var isStreaming = false
        private set
    var isRecording = false
        private set
    var currentChannel: Channel? = null
        private set
    var currentRecordingFile: File? = null
        private set
    var recordedBytes: Long = 0L
        private set

    private var fileOutputStream: FileOutputStream? = null
    var localHttpPort: Int = 0
        private set

    interface StreamStateListener {
        fun onStreamingStarted(httpUrl: String)
        fun onStreamingStopped()
        fun onRecordingStarted(file: File)
        fun onRecordingProgress(bytesWritten: Long)
        fun onRecordingStopped(file: File, totalBytes: Long)
        fun onError(error: String)
    }

    private val listeners = CopyOnWriteArrayList<StreamStateListener>()

    fun addListener(l: StreamStateListener) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: StreamStateListener) {
        listeners.remove(l)
    }

    suspend fun startStream(
        context: Context,
        stbClient: StbClient,
        stbIp: String,
        channel: Channel
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (isStreaming && currentChannel?.serviceId == channel.serviceId && localHttpPort > 0) {
                return@withContext "http://127.0.0.1:$localHttpPort/live.ts"
            }

            stopStream(keepRecording = false)

            currentStbClient = stbClient
            currentStbIp = stbIp
            currentChannel = channel

            // 1. Wake STB RTSP daemon (Command 1009)
            stbClient.sendCmd(1009, expectReply = false)
            delay(250)

            // 2. Build SAT>IP Query
            val query = stbClient.buildSatIpQuery(channel)
            if (query.isNullOrBlank()) {
                notifyError("اطلاعات فرکانس و ترنسپوندر کانال یافت نشد")
                return@withContext null
            }

            // 3. Bind UDP receiver socket
            try {
                udpSocket?.close()
                udpSocket = DatagramSocket(UDP_PORT).apply {
                    soTimeout = 4000
                    receiveBufferSize = 1024 * 1024 // 1MB buffer
                }
            } catch (e: Exception) {
                Log.w(TAG, "Port $UDP_PORT busy, binding to dynamic port", e)
                udpSocket = DatagramSocket(0).apply {
                    soTimeout = 4000
                    receiveBufferSize = 1024 * 1024
                }
            }
            val activeUdpPort = udpSocket?.localPort ?: UDP_PORT

            // 4. Start local HTTP server on dynamic port
            serverSocket?.close()
            val server = ServerSocket(0)
            serverSocket = server
            localHttpPort = server.localPort

            // 5. Start SAT>IP RTSP Session with STB
            val session = SatIpSession(stbIp, clientPort = activeUdpPort)
            val started = session.start(query)
            if (!started) {
                session.stop()
                server.close()
                udpSocket?.close()
                notifyError("خطا در برقراری ارتباط استریم SAT>IP با رسیور")
                return@withContext null
            }
            satIpSession = session
            isStreaming = true

            // Launch HTTP accept loop
            startHttpServerLoop(server)

            // Launch UDP TS receiver & relay loop
            startUdpReceiverLoop()

            val httpUrl = "http://127.0.0.1:$localHttpPort/live.ts"
            notifyStreamingStarted(httpUrl)
            return@withContext httpUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error starting stream", e)
            notifyError("خطا در راه‌اندازی استریم: ${e.message}")
            stopStream(keepRecording = false)
            return@withContext null
        }
    }

    private fun startHttpServerLoop(server: ServerSocket) {
        httpAcceptJob = managerScope.launch {
            while (isActive && isStreaming && !server.isClosed) {
                try {
                    val clientSocket = server.accept()
                    clientSocket.tcpNoDelay = true
                    clientSocket.sendBufferSize = 512 * 1024

                    // Handle HTTP request header in coroutine
                    launch(Dispatchers.IO) {
                        try {
                            val input = clientSocket.getInputStream()
                            val buffer = ByteArray(1024)
                            val read = input.read(buffer)
                            if (read > 0) {
                                val header = "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: video/mp2t\r\n" +
                                        "Connection: close\r\n" +
                                        "Access-Control-Allow-Origin: *\r\n\r\n"
                                val out = clientSocket.getOutputStream()
                                out.write(header.toByteArray(Charsets.UTF_8))
                                out.flush()
                                httpClients.add(clientSocket)
                                Log.d(TAG, "HTTP client connected for TS stream: ${clientSocket.inetAddress}")
                            } else {
                                clientSocket.close()
                            }
                        } catch (e: Exception) {
                            try { clientSocket.close() } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    if (!isActive || !isStreaming) break
                }
            }
        }
    }

    private fun startUdpReceiverLoop() {
        streamJob = managerScope.launch {
            val buf = ByteArray(2048)
            val packet = DatagramPacket(buf, buf.size)
            var packetCount = 0L

            while (isActive && isStreaming) {
                val sock = udpSocket ?: break
                try {
                    sock.receive(packet)
                    val len = packet.length

                    // Validate RTP packet and MPEG-TS sync byte 0x47
                    if (len > 12 && (buf[0].toInt() and 0xC0) == 0x80 && buf[12] == 0x47.toByte()) {
                        val tsOffset = 12
                        val tsLen = len - 12
                        packetCount++

                        // 1. Write to recording file if recording
                        if (isRecording) {
                            val fos = fileOutputStream
                            if (fos != null) {
                                try {
                                    fos.write(buf, tsOffset, tsLen)
                                    recordedBytes += tsLen
                                    if (packetCount % 200 == 0L) {
                                        notifyRecordingProgress(recordedBytes)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error writing to recording file", e)
                                }
                            }
                        }

                        // 2. Broadcast to connected HTTP clients
                        if (httpClients.isNotEmpty()) {
                            val iter = httpClients.iterator()
                            while (iter.hasNext()) {
                                val client = iter.next()
                                try {
                                    client.getOutputStream().write(buf, tsOffset, tsLen)
                                } catch (e: Exception) {
                                    httpClients.remove(client)
                                    try { client.close() } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Socket timeout or closed
                    if (!isActive || !isStreaming) break
                }
            }
        }
    }

    suspend fun startRecording(
        context: Context,
        stbClient: StbClient,
        stbIp: String,
        channel: Channel,
        isAudioOnly: Boolean
    ): File? = withContext(Dispatchers.IO) {
        try {
            if (isRecording) {
                stopRecording()
            }

            // Ensure stream is running for this channel
            if (!isStreaming || currentChannel?.serviceId != channel.serviceId) {
                val url = startStream(context, stbClient, stbIp, channel)
                if (url == null) return@withContext null
            }

            val file = StorageManager.generateRecordingFile(context, channel.serviceName, isAudioOnly)
            val fos = FileOutputStream(file, false)
            fileOutputStream = fos
            currentRecordingFile = file
            recordedBytes = 0L
            isRecording = true

            notifyRecordingStarted(file)
            return@withContext file
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            notifyError("خطا در ایجاد فایل ضبط: ${e.message}")
            return@withContext null
        }
    }

    suspend fun stopRecording(): File? = withContext(Dispatchers.IO) {
        if (!isRecording) return@withContext null
        isRecording = false
        val file = currentRecordingFile
        val bytes = recordedBytes

        try {
            fileOutputStream?.flush()
            fileOutputStream?.close()
        } catch (_: Exception) {}
        fileOutputStream = null

        // If no HTTP clients are watching, stop stream completely
        if (httpClients.isEmpty()) {
            stopStream(keepRecording = false)
        }

        if (file != null) {
            notifyRecordingStopped(file, bytes)
        }
        return@withContext file
    }

    fun stopStream(keepRecording: Boolean = false) {
        if (!keepRecording && isRecording) {
            managerScope.launch { stopRecording() }
        }

        isStreaming = false
        streamJob?.cancel()
        streamJob = null

        httpAcceptJob?.cancel()
        httpAcceptJob = null

        // Close all HTTP clients
        for (client in httpClients) {
            try { client.close() } catch (_: Exception) {}
        }
        httpClients.clear()

        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null

        try { udpSocket?.close() } catch (_: Exception) {}
        udpSocket = null

        satIpSession?.stop()
        satIpSession = null

        val client = currentStbClient
        if (client != null) {
            managerScope.launch {
                try {
                    client.sendCmd(1012, expectReply = false)
                } catch (_: Exception) {}
            }
        }

        notifyStreamingStopped()
    }

    private fun notifyStreamingStarted(url: String) {
        for (l in listeners) {
            try { l.onStreamingStarted(url) } catch (_: Exception) {}
        }
    }

    private fun notifyStreamingStopped() {
        for (l in listeners) {
            try { l.onStreamingStopped() } catch (_: Exception) {}
        }
    }

    private fun notifyRecordingStarted(file: File) {
        for (l in listeners) {
            try { l.onRecordingStarted(file) } catch (_: Exception) {}
        }
    }

    private fun notifyRecordingProgress(bytes: Long) {
        for (l in listeners) {
            try { l.onRecordingProgress(bytes) } catch (_: Exception) {}
        }
    }

    private fun notifyRecordingStopped(file: File, bytes: Long) {
        for (l in listeners) {
            try { l.onRecordingStopped(file, bytes) } catch (_: Exception) {}
        }
    }

    private fun notifyError(msg: String) {
        for (l in listeners) {
            try { l.onError(msg) } catch (_: Exception) {}
        }
    }
}
