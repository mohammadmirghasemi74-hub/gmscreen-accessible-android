package com.gmscreen.accessible.protocol

import com.gmscreen.accessible.data.model.Channel
import com.gmscreen.accessible.data.model.Satellite
import com.gmscreen.accessible.data.model.StbState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.InflaterInputStream

class StbClient {

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    var isConnected: Boolean = false
        private set

    var currentIp: String = ""
        private set

    var rawModel: String = ""
        private set

    var formattedModel: String = ""
        private set

    private val _notifications = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val notifications = _notifications.asSharedFlow()

    private val xmlFactory = XmlPullParserFactory.newInstance().apply {
        isNamespaceAware = false
    }

    suspend fun connect(ip: String, port: Int = ProtocolConstants.DEFAULT_PORT): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        try {
            val sock = Socket().apply {
                tcpNoDelay = true
                soTimeout = 8000
                connect(InetSocketAddress(ip, port), 5000)
            }
            socket = sock
            inputStream = sock.getInputStream()
            outputStream = sock.getOutputStream()
            currentIp = ip

            // 1. Send Login Request 998
            val loginPayload = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><Command request=\"998\" />".toByteArray(Charsets.UTF_8)
            val loginPacket = buildPacket(loginPayload)
            outputStream?.write(loginPacket)
            outputStream?.flush()

            // 2. Read 108-byte Login Response
            val loginResp = ByteArray(108)
            var readTotal = 0
            while (readTotal < 108) {
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
            disconnect()
            false
        }
    }

    fun disconnect() {
        try {
            isConnected = false
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket = null
            inputStream = null
            outputStream = null
        }
    }

    private fun buildPacket(body: ByteArray): ByteArray {
        val lenStr = String.format("%07d", body.size)
        val header = "Start${lenStr}End".toByteArray(Charsets.US_ASCII)
        return header + body
    }

    private suspend fun sendCommandAndReadXml(xmlCmd: String): String? = withContext(Dispatchers.IO) {
        if (!isConnected || outputStream == null || inputStream == null) return@withContext null
        try {
            val payload = xmlCmd.toByteArray(Charsets.UTF_8)
            val packet = buildPacket(payload)
            outputStream?.write(packet)
            outputStream?.flush()

            // Read 16-byte GCDH Header
            val gcdhHeader = ByteArray(16)
            var readH = 0
            while (readH < 16) {
                val r = inputStream?.read(gcdhHeader, readH, 16 - readH) ?: -1
                if (r == -1) return@withContext null
                readH += r
            }

            val magic = String(gcdhHeader, 0, 4, Charsets.US_ASCII)
            if (magic != "GCDH") return@withContext null

            val bb = ByteBuffer.wrap(gcdhHeader).order(ByteOrder.LITTLE_ENDIAN)
            bb.position(4)
            val payloadLen = bb.int
            val reqType = bb.int
            val status = bb.int

            if (payloadLen <= 0) return@withContext ""

            val compressed = ByteArray(payloadLen)
            var readP = 0
            while (readP < payloadLen) {
                val r = inputStream?.read(compressed, readP, payloadLen - readP) ?: -1
                if (r == -1) break
                readP += r
            }

            // Decompress zlib payload
            val decompressed = try {
                InflaterInputStream(ByteArrayInputStream(compressed)).readBytes()
            } catch (e: Exception) {
                compressed
            }

            String(decompressed, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun fetchChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Channel>()
        val xmlCmd = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><Command request=\"0\"><from>0</from><to>2000</to></Command>"
        val respXml = sendCommandAndReadXml(xmlCmd) ?: return@withContext list

        try {
            val parser = xmlFactory.newPullParser().apply {
                setInput(ByteArrayInputStream(respXml.toByteArray(Charsets.UTF_8)), "UTF-8")
            }
            var eventType = parser.eventType
            var currentTag = ""
            var sId = ""
            var sName = ""
            var isRadio = false
            var isHd = false
            var isLocked = false
            var satIdx = 0
            var tpIdx = 0
            var vPid = 0
            var aPid = 0
            var pmtPid = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        if (currentTag == "record") {
                            sId = ""
                            sName = ""
                            isRadio = false
                            isHd = false
                            isLocked = false
                            satIdx = 0
                            tpIdx = 0
                            vPid = 0
                            aPid = 0
                            pmtPid = 0
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val txt = parser.text.trim()
                        if (txt.isNotEmpty()) {
                            when (currentTag) {
                                "ServiceID" -> {
                                    sId = txt
                                    if (txt.length >= 4) {
                                        satIdx = txt.substring(0, 4).toIntOrNull() ?: 0
                                    }
                                    if (txt.length >= 9) {
                                        tpIdx = txt.substring(4, 9).toIntOrNull() ?: 0
                                    }
                                }
                                "ServiceName" -> sName = txt
                                "Radio" -> isRadio = (txt == "1")
                                "HD" -> isHd = (txt == "1")
                                "Lock" -> isLocked = (txt == "1")
                                "VideoPID" -> vPid = txt.toIntOrNull() ?: 0
                                "AudioPID" -> aPid = txt.toIntOrNull() ?: 0
                                "PMTPID" -> pmtPid = txt.toIntOrNull() ?: 0
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "record" && sId.isNotEmpty()) {
                            list.add(
                                Channel(
                                    serviceId = sId,
                                    serviceName = sName.ifBlank { "شبکه بدون نام" },
                                    isRadio = isRadio,
                                    isHd = isHd,
                                    isLocked = isLocked,
                                    satIndex = satIdx,
                                    tpIndex = tpIdx,
                                    videoPid = vPid,
                                    audioPid = aPid,
                                    pmtPid = pmtPid
                                )
                            )
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }

            // Assign real sequential channel numbers
            var tvCounter = 1
            var radioCounter = 1
            for (ch in list) {
                if (ch.isRadio) {
                    ch.realChannelNum = radioCounter++
                } else {
                    ch.realChannelNum = tvCounter++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        list
    }

    suspend fun fetchSatellites(): List<Satellite> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Satellite>()
        list.add(Satellite(-1, "همه ماهواره‌ها (عموم شبکه‌ها)", 0))

        val xmlCmd = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><Command request=\"1010\" />"
        val respXml = sendCommandAndReadXml(xmlCmd) ?: return@withContext list

        try {
            val parser = xmlFactory.newPullParser().apply {
                setInput(ByteArrayInputStream(respXml.toByteArray(Charsets.UTF_8)), "UTF-8")
            }
            var eventType = parser.eventType
            var currentTag = ""
            var sIdx = -1
            var sName = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTag = parser.name
                    XmlPullParser.TEXT -> {
                        val txt = parser.text.trim()
                        if (txt.isNotEmpty()) {
                            when (currentTag) {
                                "SatIndex" -> sIdx = txt.toIntOrNull() ?: -1
                                "SatName" -> sName = txt
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "parm" && sIdx != -1 && sName.isNotBlank()) {
                            list.add(Satellite(sIdx, sName, 0))
                            sIdx = -1
                            sName = ""
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun fetchPlayingChannel(): String? = withContext(Dispatchers.IO) {
        val xmlCmd = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><Command request=\"3\" />"
        val respXml = sendCommandAndReadXml(xmlCmd) ?: return@withContext null
        var pId: String? = null
        try {
            val parser = xmlFactory.newPullParser().apply {
                setInput(ByteArrayInputStream(respXml.toByteArray(Charsets.UTF_8)), "UTF-8")
            }
            var eventType = parser.eventType
            var currentTag = ""
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTag = parser.name
                    XmlPullParser.TEXT -> {
                        if (currentTag == "ProgramId") pId = parser.text.trim()
                    }
                    XmlPullParser.END_TAG -> currentTag = ""
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        pId
    }

    suspend fun switchChannel(serviceId: String, isRadio: Boolean): Boolean = withContext(Dispatchers.IO) {
        val radioVal = if (isRadio) 1 else 0
        val xmlCmd = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><Command request=\"1000\"><channelType>$radioVal</channelType><ProgramId>$serviceId</ProgramId></Command>"
        sendCommandAndReadXml(xmlCmd) != null
    }

    suspend fun sendRcuKey(keyCode: Int): Boolean = withContext(Dispatchers.IO) {
        val xmlCmd = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><Command request=\"1040\"><array><parm><KeyValue>$keyCode</KeyValue></parm></array></Command>"
        sendCommandAndReadXml(xmlCmd) != null
    }

    suspend fun togglePower(): Boolean = withContext(Dispatchers.IO) {
        val xmlCmd = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><Command request=\"1041\" />"
        sendCommandAndReadXml(xmlCmd) != null
    }

    suspend fun fetchStbState(): StbState = withContext(Dispatchers.IO) {
        val xmlCmd = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><Command request=\"1009\" />"
        val respXml = sendCommandAndReadXml(xmlCmd) ?: return@withContext StbState()
        var prod = ""
        var sw = ""
        var sn = ""
        var pMode = 1
        var sTime = 0
        var sSwitch = 0
        var cType = 0
        var cNum = 0

        try {
            val parser = xmlFactory.newPullParser().apply {
                setInput(ByteArrayInputStream(respXml.toByteArray(Charsets.UTF_8)), "UTF-8")
            }
            var eventType = parser.eventType
            var currentTag = ""
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTag = parser.name
                    XmlPullParser.TEXT -> {
                        val txt = parser.text.trim()
                        when (currentTag) {
                            "ProductName" -> prod = txt
                            "SoftwareVersion" -> sw = txt
                            "SerialNumber" -> sn = txt
                            "PowerMode" -> pMode = txt.toIntOrNull() ?: 1
                            "SleepTime" -> sTime = txt.toIntOrNull() ?: 0
                            "SleepSwitch" -> sSwitch = txt.toIntOrNull() ?: 0
                            "cur_channel_type" -> cType = txt.toIntOrNull() ?: 0
                            "ChannelNum" -> cNum = txt.toIntOrNull() ?: 0
                        }
                    }
                    XmlPullParser.END_TAG -> currentTag = ""
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        StbState(
            productName = ProtocolConstants.formatStbProductName(prod.ifBlank { rawModel }),
            softwareVersion = sw,
            serialNumber = sn,
            powerMode = pMode,
            sleepTime = sTime,
            sleepSwitch = sSwitch,
            curChannelType = cType,
            channelNum = cNum
        )
    }
}
