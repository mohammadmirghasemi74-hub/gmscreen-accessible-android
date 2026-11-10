package com.gmscreen.accessible.data

import android.content.Context
import com.gmscreen.accessible.data.model.Channel
import com.gmscreen.accessible.data.model.Satellite
import com.gmscreen.accessible.data.model.StbDevice
import com.gmscreen.accessible.data.model.StbState
import com.gmscreen.accessible.protocol.StbClient
import com.gmscreen.accessible.protocol.StbDiscovery
import com.gmscreen.accessible.util.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StbRepository(context: Context) {

    private val appConfig = AppConfig(context)
    private val discovery = StbDiscovery(context)
    val client = StbClient()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedIp = MutableStateFlow("")
    val connectedIp: StateFlow<String> = _connectedIp.asStateFlow()

    private val _deviceModel = MutableStateFlow("")
    val deviceModel: StateFlow<String> = _deviceModel.asStateFlow()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _satellites = MutableStateFlow<List<Satellite>>(emptyList())
    val satellites: StateFlow<List<Satellite>> = _satellites.asStateFlow()

    private val _currentState = MutableStateFlow<StbState?>(null)
    val currentState: StateFlow<StbState?> = _currentState.asStateFlow()

    private val _currentChannel = MutableStateFlow<Channel?>(null)
    val currentChannel: StateFlow<Channel?> = _currentChannel.asStateFlow()

    private val _stbTimers = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val stbTimers: StateFlow<List<Map<String, Any>>> = _stbTimers.asStateFlow()

    private val _pvrFiles = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val pvrFiles: StateFlow<List<Map<String, Any>>> = _pvrFiles.asStateFlow()

    suspend fun discoverDevices(): List<StbDevice> {
        return discovery.discover(timeoutMs = 3500)
    }

    suspend fun connect(ip: String): Result<Boolean> {
        return try {
            val success = client.connect(ip)
            if (success) {
                _isConnected.value = true
                _connectedIp.value = ip
                appConfig.lastConnectedIp = ip
                _deviceModel.value = client.formattedModel

                // Launch initial data fetch in background without blocking connect UI
                CoroutineScope(Dispatchers.IO).launch {
                    refreshData()
                }
                Result.success(true)
            } else {
                Result.failure(Exception("اتصال به رسیور برقرار نشد"))
            }
        } catch (e: Exception) {
            _isConnected.value = false
            Result.failure(e)
        }
    }

    fun disconnect() {
        client.disconnect()
        _isConnected.value = false
        _currentState.value = null
        _stbTimers.value = emptyList()
        _pvrFiles.value = emptyList()
    }

    suspend fun refreshData() {
        if (!client.isConnected) return
        try {
            client.fetchTransponders()
            val sats = client.fetchSatellites()
            _satellites.value = sats

            val state = client.fetchStbState()
            _currentState.value = state

            val chList = client.fetchChannels(toIndex = if (state.totalChannels > 0) state.totalChannels else 800)
            _channels.value = chList

            if (state.playingServiceId.isNotBlank()) {
                _currentChannel.value = chList.find { it.serviceId == state.playingServiceId }
            }

            refreshTimers()
            refreshPvrFiles()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun refreshTimers() {
        if (!client.isConnected) return
        val timers = client.fetchTimers()
        _stbTimers.value = timers
    }

    suspend fun refreshPvrFiles() {
        if (!client.isConnected) return
        val files = client.fetchPvrFiles()
        _pvrFiles.value = files
    }

    suspend fun switchChannel(channel: Channel): Boolean {
        if (!client.isConnected) return false
        val success = client.switchChannel(channel.serviceId, channel.isRadio)
        if (success) {
            _currentChannel.value = channel
        }
        return success
    }

    suspend fun sendRcuKey(keyCode: Int): Boolean {
        if (!client.isConnected) return false
        return client.sendRcuKey(keyCode)
    }

    suspend fun addStbTimer(timer: Map<String, Any>): Boolean {
        if (!client.isConnected) return false
        val ok = client.addStbTimer(timer)
        if (ok) refreshTimers()
        return ok
    }

    suspend fun deleteStbTimer(timer: Map<String, Any>): Boolean {
        if (!client.isConnected) return false
        val ok = client.deleteStbTimer(timer)
        if (ok) refreshTimers()
        return ok
    }
}
