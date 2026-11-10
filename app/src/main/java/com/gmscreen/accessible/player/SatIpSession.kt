package com.gmscreen.accessible.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.util.regex.Pattern

class SatIpSession(
    private val stbIp: String,
    private val clientPort: Int = 5114
) {
    private var socket: Socket? = null
    private var cseq = 1
    private var sessionId: String? = null
    private var streamId: String? = null
    var isSessionActive = false
        private set
    private var keepaliveJob: Job? = null

    suspend fun start(query: String): Boolean = withContext(Dispatchers.IO) {
        stop()
        try {
            val sock = Socket(stbIp, 554).apply {
                soTimeout = 5000
                tcpNoDelay = true
            }
            socket = sock
            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            val writer = sock.getOutputStream()

            // 1. SETUP
            val setupReq = "SETUP rtsp://$stbIp:554/$query RTSP/1.0\r\n" +
                    "CSeq: $cseq\r\n" +
                    "Transport: RTP/AVP/UDP;unicast;client_port=$clientPort-${clientPort + 1}\r\n" +
                    "User-Agent: Lavf57.52.100\r\n\r\n"
            cseq++
            writer.write(setupReq.toByteArray(Charsets.UTF_8))
            writer.flush()

            val setupResp = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                setupResp.append(line).append("\n")
                if (line!!.isBlank()) break
            }
            val respStr = setupResp.toString()

            val sessMatcher = Pattern.compile("Session:\\s*([^\\r\\n;]+)", Pattern.CASE_INSENSITIVE).matcher(respStr)
            val streamMatcher = Pattern.compile("streamID:\\s*([^\\r\\n;]+)", Pattern.CASE_INSENSITIVE).matcher(respStr)

            if (!sessMatcher.find() || !streamMatcher.find()) {
                stop()
                return@withContext false
            }

            sessionId = sessMatcher.group(1)?.trim()
            streamId = streamMatcher.group(1)?.trim()

            // 2. PLAY
            val playReq = "PLAY rtsp://$stbIp:554/stream=$streamId RTSP/1.0\r\n" +
                    "CSeq: $cseq\r\n" +
                    "Session: $sessionId\r\n" +
                    "User-Agent: Lavf57.52.100\r\n\r\n"
            cseq++
            writer.write(playReq.toByteArray(Charsets.UTF_8))
            writer.flush()

            val playResp = StringBuilder()
            while (reader.readLine().also { line = it } != null) {
                playResp.append(line).append("\n")
                if (line!!.isBlank()) break
            }

            if (!playResp.contains("200 OK")) {
                stop()
                return@withContext false
            }

            isSessionActive = true
            keepaliveJob = CoroutineScope(Dispatchers.IO).launch {
                while (isActive && isSessionActive) {
                    delay(8000)
                    if (!isSessionActive || socket == null || streamId == null || sessionId == null) break
                    try {
                        val optReq = "OPTIONS rtsp://$stbIp:554/stream=$streamId RTSP/1.0\r\n" +
                                "CSeq: $cseq\r\n" +
                                "Session: $sessionId\r\n" +
                                "User-Agent: Lavf57.52.100\r\n\r\n"
                        cseq++
                        writer.write(optReq.toByteArray(Charsets.UTF_8))
                        writer.flush()
                        while (reader.readLine().also { line = it } != null) {
                            if (line!!.isBlank()) break
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
            false
        }
    }

    fun stop() {
        isSessionActive = false
        keepaliveJob?.cancel()
        keepaliveJob = null
        val sock = socket
        val sess = sessionId
        val strm = streamId
        if (sock != null && sess != null && strm != null) {
            try {
                val tdReq = "TEARDOWN rtsp://$stbIp:554/stream=$strm RTSP/1.0\r\n" +
                        "CSeq: $cseq\r\n" +
                        "Session: $sess\r\n" +
                        "User-Agent: Lavf57.52.100\r\n\r\n"
                sock.getOutputStream().write(tdReq.toByteArray(Charsets.UTF_8))
                sock.getOutputStream().flush()
            } catch (_: Exception) {}
        }
        try {
            sock?.close()
        } catch (_: Exception) {}
        socket = null
        sessionId = null
        streamId = null
    }
}
