package com.gmscreen.accessible.protocol

import android.content.Context
import android.net.wifi.WifiManager
import com.gmscreen.accessible.data.model.StbDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

class StbDiscovery(private val context: Context) {

    suspend fun discover(timeoutMs: Int = 3500): List<StbDevice> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<StbDevice>()
        val seenIps = mutableSetOf<String>()

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("GMScreenDiscoveryLock")?.apply {
            setReferenceCounted(true)
            acquire()
        }

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = timeoutMs
                bind(InetSocketAddress(25860))
            }

            val buffer = ByteArray(512)
            val packet = DatagramPacket(buffer, buffer.size)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    socket.receive(packet)
                    val ip = packet.address.hostAddress ?: continue
                    if (seenIps.contains(ip)) continue

                    if (packet.length == 108) {
                        val dec = ByteArray(108) { i ->
                            (buffer[107 - i].toInt() xor 0x5B).toByte()
                        }
                        val magic = String(dec, 0, 12, Charsets.US_ASCII)
                        if (magic.startsWith("39WwijOog")) {
                            val modelBytes = dec.copyOfRange(20, 52)
                            val endIdx = modelBytes.indexOf(0).let { if (it >= 0) it else modelBytes.size }
                            val rawModel = String(modelBytes, 0, endIdx, Charsets.US_ASCII).trim()
                            val formatted = ProtocolConstants.formatStbProductName(rawModel)

                            val dev = StbDevice(
                                ip = ip,
                                mac = "",
                                rawModel = rawModel,
                                formattedModel = formatted
                            )
                            discovered.add(dev)
                            seenIps.add(ip)
                        }
                    }
                } catch (e: Exception) {
                    // Socket timeout or receive error
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket?.close()
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        discovered
    }
}
