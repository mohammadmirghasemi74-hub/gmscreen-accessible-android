package com.gmscreen.accessible.protocol

import com.gmscreen.accessible.data.model.Channel
import com.gmscreen.accessible.data.model.Satellite
import com.gmscreen.accessible.data.model.StbState
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.Inflater

class StbClient {

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val clientLock = Any()

    var isConnected: Boolean = false
        private set

    var currentIp: String = ""
        private set

    var rawModel: String = ""
        private set

    var formattedModel: String = ""
        private set

    private val transponders = mutableMapOf<Int, Map<String, Any>>()

    private val _notifications = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val notifications = _notifications.asSharedFlow()

    suspend fun connect(ip: String, port: Int = ProtocolConstants.DEFAULT_PORT, timeoutMs: Int = 5000): Boolean = withContext(Dispatchers.IO) {
        synchronized(clientLock) {
            disconnectLocked()
            try {
                val sock = Socket().apply {
                    tcpNoDelay = true
                    soTimeout = timeoutMs
                    connect(InetSocketAddress(ip, port), timeoutMs)
                }
                socket = sock
                inputStream = sock.getInputStream()
                outputStream = sock.getOutputStream()
                currentIp = ip

                // XML Handshake 998 with exact Windows payload
                val login = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>" +
                        "<Command request=\"998\"><data>G-MScreen-Win</data><uuid>gm-screen-pc</uuid></Command>"
                val loginBytes = login.toByteArray(Charsets.UTF_8)
                val lenStr = String.format(Locale.US, "%07d", loginBytes.size)
                val pkt = "Start${lenStr}End".toByteArray(Charsets.US_ASCII) + loginBytes

                outputStream?.write(pkt)
                outputStream?.flush()

                // Read 108-byte Login Response
                val loginResp = ByteArray(108)
                var readTotal = 0
                val readStartTime = System.currentTimeMillis()
                while (readTotal < 108 && System.currentTimeMillis() - readStartTime < timeoutMs) {
                    val r = inputStream?.read(loginResp, readTotal, 108 - readTotal) ?: -1
                    if (r == -1) break
                    readTotal += r
                }

                if (readTotal == 108) {
                    val dec = ByteArray(108) { i ->
                        (loginResp[107 - i].toInt() xor 0x5B).toByte()
                    }
                    val magic = String(dec, 0, 12, Charsets.US_ASCII)
                    if (magic.startsWith("39WwijOog")) {
                        val modelBytes = dec.copyOfRange(20, 52)
                        val endIdx = modelBytes.indexOf(0).let { if (it >= 0) it else modelBytes.size }
                        rawModel = String(modelBytes, 0, endIdx, Charsets.US_ASCII).trim()
                        formattedModel = ProtocolConstants.formatStbProductName(rawModel)
                    }
                }

                if (formattedModel.isBlank()) {
                    formattedModel = "رسیور مدیااستار ($ip)"
                }

                isConnected = true
                true
            } catch (e: Exception) {
                e.printStackTrace()
                disconnectLocked()
                false
            }
        }
    }

    fun disconnect() {
        synchronized(clientLock) {
            disconnectLocked()
        }
    }

    private fun disconnectLocked() {
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
        isConnected = false
    }

    private fun drainSocket() {
        val s = socket ?: return
        try {
            val oldTimeout = s.soTimeout
            s.soTimeout = 20
            val tmp = ByteArray(2048)
            while ((inputStream?.available() ?: 0) > 0) {
                val r = inputStream?.read(tmp) ?: -1
                if (r <= 0) break
            }
            s.soTimeout = oldTimeout
        } catch (_: Exception) {}
    }

    private fun readExact(n: Int, timeoutMs: Int = 6000): ByteArray? {
        val stream = inputStream ?: return null
        val sock = socket ?: return null
        sock.soTimeout = timeoutMs
        val buf = ByteArray(n)
        var total = 0
        val start = System.currentTimeMillis()
        while (total < n) {
            if (System.currentTimeMillis() - start > timeoutMs) return null
            val r = stream.read(buf, total, n - total)
            if (r == -1) return null
            total += r
        }
        return buf
    }

    private fun decompressZlib(raw: ByteArray): String {
        val inflater = Inflater(false)
        inflater.setInput(raw)
        val bos = java.io.ByteArrayOutputStream(raw.size * 4)
        val buf = ByteArray(4096)
        while (!inflater.finished()) {
            val count = inflater.inflate(buf)
            if (count <= 0 && inflater.needsInput()) break
            if (count > 0) {
                bos.write(buf, 0, count)
            }
        }
        inflater.end()
        return bos.toString("UTF-8")
    }

    fun sendCmd(
        req: Int,
        fields: Map<String, Any>? = null,
        expectReply: Boolean = true,
        timeoutMs: Int = 6000
    ): List<Map<String, Any>>? {
        synchronized(clientLock) {
            if (!isConnected || socket == null || outputStream == null || inputStream == null) {
                return null
            }

            try {
                drainSocket()

                val body = mutableMapOf<String, Any>("request" to req.toString())
                if (fields != null) {
                    body.putAll(fields)
                }

                val payload = Gson().toJson(body)
                val payloadBytes = payload.toByteArray(Charsets.UTF_8)
                val lenStr = String.format(Locale.US, "%07d", payloadBytes.size)
                val pkt = "Start${lenStr}End".toByteArray(Charsets.US_ASCII) + payloadBytes

                outputStream?.write(pkt)
                outputStream?.flush()

                if (!expectReply) {
                    return emptyList()
                }

                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    val remTime = (timeoutMs - (System.currentTimeMillis() - startTime)).toInt().coerceAtLeast(500)
                    val hdr = readExact(16, remTime) ?: return null

                    val magic = String(hdr, 0, 4, Charsets.US_ASCII)
                    if (magic != "GCDH") {
                        return null
                    }

                    val bb = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
                    bb.position(4)
                    val plen = bb.int
                    val mtype = bb.int
                    val status = bb.int

                    var resultList = mutableListOf<Map<String, Any>>()
                    if (plen > 0) {
                        val raw = readExact(plen, remTime) ?: return null
                        try {
                            val jsonStr = decompressZlib(raw)
                            val parsed = JsonParser.parseString(jsonStr)
                            if (parsed.isJsonArray) {
                                for (elem in parsed.asJsonArray) {
                                    if (elem.isJsonObject) {
                                        val map = jsonObjectToMap(elem.asJsonObject)
                                        resultList.add(map)
                                    }
                                }
                            } else if (parsed.isJsonObject) {
                                resultList.add(jsonObjectToMap(parsed.asJsonObject))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (mtype == req || (req == 3 && (mtype == 3 || mtype == 2001)) || (req == 1020 && (mtype == 1020 || mtype == 2005))) {
                        return resultList
                    } else if (mtype >= 2000) {
                        // Trailing async notification, continue waiting for reply
                        continue
                    } else {
                        return resultList
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }

            return null
        }
    }

    private fun jsonObjectToMap(obj: JsonObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        for ((key, value) in obj.entrySet()) {
            when {
                value.isJsonPrimitive -> {
                    val prim = value.asJsonPrimitive
                    when {
                        prim.isBoolean -> map[key] = prim.asBoolean
                        prim.isNumber -> map[key] = prim.asNumber
                        else -> map[key] = prim.asString
                    }
                }
                value.isJsonNull -> {}
                else -> map[key] = value.toString()
            }
        }
        return map
    }

    suspend fun fetchStbState(): StbState = withContext(Dispatchers.IO) {
        var res = sendCmd(14, timeoutMs = 4000)
        if (res.isNullOrEmpty()) {
            res = sendCmd(15, timeoutMs = 4000)
        }
        if (!res.isNullOrEmpty()) {
            val d = res[0]
            val chNum = d["ChannelNum"]?.toString()?.toIntOrNull() ?: 0
            val playing = d["playingServiceId"]?.toString() ?: ""
            val power = d["PowerMode"]?.toString()?.toIntOrNull() ?: 1
            return@withContext StbState(
                channelNum = chNum,
                playingServiceId = playing,
                powerMode = power
            )
        }
        StbState()
    }

    suspend fun fetchSatellites(): List<Satellite> = withContext(Dispatchers.IO) {
        val sats = mutableListOf<Satellite>()
        val res = sendCmd(22, timeoutMs = 5000) ?: return@withContext sats
        for (item in res) {
            val idx = item["SatIndex"]?.toString()?.toIntOrNull() ?: continue
            val name = item["SatName"]?.toString() ?: "ماهواره $idx"
            val longi = item["SatLongitude"]?.toString()?.toIntOrNull() ?: 0
            sats.add(Satellite(idx, name, longi))
        }
        sats
    }

    suspend fun fetchTransponders(): Map<Int, Map<String, Any>> = withContext(Dispatchers.IO) {
        val res = sendCmd(24, timeoutMs = 8000)
        if (!res.isNullOrEmpty()) {
            transponders.clear()
            for (tp in res) {
                val tpIdx = tp["TPIndex"]?.toString()?.toIntOrNull()
                if (tpIdx != null) {
                    transponders[tpIdx] = tp
                }
            }
        }
        transponders
    }

    suspend fun fetchChannels(toIndex: Int? = null): List<Channel> = withContext(Dispatchers.IO) {
        val channels = mutableListOf<Channel>()
        val maxIndex = toIndex ?: 800
        val step = 100
        var cur = 0
        while (cur <= maxIndex) {
            val stop = minOf(cur + step - 1, maxIndex)
            val fields = mapOf(
                "array" to listOf(
                    mapOf(
                        "FromIndex" to cur.toString(),
                        "ToIndex" to stop.toString()
                    )
                )
            )
            val res = sendCmd(0, fields, expectReply = true, timeoutMs = 8000) ?: break
            if (res.isEmpty()) break

            for (c in res) {
                val sid = c["ServiceID"]?.toString() ?: continue
                val sName = c["ServiceName"]?.toString() ?: "کانال"
                val isRadio = (c["Radio"]?.toString() == "1")
                val isHd = (c["HD"]?.toString() == "1")
                val isLocked = (c["Lock"]?.toString() == "1")
                val vPid = c["VideoPID"]?.toString()?.toIntOrNull() ?: 0
                val aPid = c["AudioPID"]?.toString()?.toIntOrNull() ?: 0
                val pmt = c["PMTPID"]?.toString()?.toIntOrNull() ?: 0
                val satIdx = if (sid.length >= 4) sid.substring(0, 4).toIntOrNull() ?: 0 else 0
                val tpIdx = if (sid.length >= 9) sid.substring(4, 9).toIntOrNull() ?: 0 else 0

                val tp = transponders[tpIdx]
                val freq = tp?.get("Freq")?.toString()?.toIntOrNull() ?: 0

                channels.add(
                    Channel(
                        serviceId = sid,
                        serviceName = sName,
                        isRadio = isRadio,
                        isHd = isHd,
                        isLocked = isLocked,
                        satIndex = satIdx,
                        tpIndex = tpIdx,
                        videoPid = vPid,
                        audioPid = aPid,
                        pmtPid = pmt,
                        realChannelNum = channels.size + 1,
                        frequency = freq
                    )
                )
            }
            if (res.size < (stop - cur + 1)) break
            cur = stop + 1
        }
        channels
    }

    suspend fun switchChannel(serviceId: String, isRadio: Boolean): Boolean = withContext(Dispatchers.IO) {
        val radioStr = if (isRadio) "1" else "0"
        val payload = mapOf(
            "TotalNum" to "1",
            "TvState" to radioStr,
            "array" to listOf(
                mapOf(
                    "ProgramId" to serviceId,
                    "TvState" to radioStr,
                    "channelType" to if (isRadio) 1 else 0
                )
            )
        )
        val res = sendCmd(1000, payload, expectReply = true, timeoutMs = 5000)
        res != null
    }

    suspend fun sendRcuKey(keyCode: Int): Boolean = withContext(Dispatchers.IO) {
        if (keyCode == 1041 || keyCode == RcuKey.POWER_TOGGLE) {
            val res = sendCmd(1041, expectReply = true, timeoutMs = 2000)
            return@withContext res != null
        }
        val payload = mapOf(
            "array" to listOf(
                mapOf("KeyValue" to keyCode.toString())
            )
        )
        val res = sendCmd(1040, payload, expectReply = true, timeoutMs = 2000)
        res != null
    }

    suspend fun fetchTimers(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        sendCmd(1020, timeoutMs = 5000) ?: emptyList()
    }

    suspend fun addStbTimer(timer: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        val payload = mapOf("array" to listOf(timer))
        val res = sendCmd(1021, payload, expectReply = true, timeoutMs = 5000)
        res != null
    }

    suspend fun editStbTimer(timer: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        val payload = mapOf("array" to listOf(timer))
        val res = sendCmd(1022, payload, expectReply = true, timeoutMs = 5000)
        res != null
    }

    suspend fun deleteStbTimer(timer: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        val payload = mapOf("array" to listOf(timer))
        val res = sendCmd(1023, payload, expectReply = true, timeoutMs = 5000)
        res != null
    }

    suspend fun fetchPvrFiles(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        sendCmd(27, timeoutMs = 5000) ?: emptyList()
    }

    suspend fun powerToggle(): Boolean = withContext(Dispatchers.IO) {
        sendRcuKey(RcuKey.POWER_TOGGLE)
    }

    suspend fun restartStb(): Boolean = withContext(Dispatchers.IO) {
        sendCmd(1043, expectReply = false, timeoutMs = 2000) != null
    }

    suspend fun sendOsdMessage(msg: String): Boolean = withContext(Dispatchers.IO) {
        val payload = mapOf("array" to listOf(mapOf("Message" to msg, "Time" to "5")))
        sendCmd(1042, payload, timeoutMs = 3000) != null
    }
}
