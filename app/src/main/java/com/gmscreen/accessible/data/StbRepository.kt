package com.gmscreen.accessible.data

import android.content.Context
import com.gmscreen.accessible.data.model.Channel
import com.gmscreen.accessible.data.model.Satellite
import com.gmscreen.accessible.data.model.StbDevice
import com.gmscreen.accessible.data.model.StbState
import com.gmscreen.accessible.protocol.StbClient
import com.gmscreen.accessible.protocol.StbDiscovery
import com.gmscreen.accessible.util.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StbRepository(context: Context) {

    private val appConfig = AppConfig(context)
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

    suspend fun discoverDevices(): List<StbDevice> {
        return StbDiscovery.discover(timeoutMs = 3500)
    }

    suspend fun connect(ip: String): Result<Boolean> {
        return try {
            val success = client.connect(ip)
            if (success) {
                _isConnected.value = true
                _connectedIp.value = ip
                appConfig.lastConnectedIp = ip
                _deviceModel.value = client.productName

                // Fetch initial data
                refreshData()
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
    }

    suspend fun refreshData() {
        if (!_isConnected.value) return
        try {
            val sats = client.fetchSatellites()
            _satellites.value = sats

            val chList = client.fetchChannels(type = 0) // TV channels
            _channels.value = chList

            val state = client.fetchCurrentState()
            _currentState.value = state
            if (state != null) {
                _currentChannel.value = chList.find { it.serviceId == state.serviceId }
            }
        } catch (_: Exception) {}
    }

    suspend fun switchChannel(channel: Channel): Boolean {
        if (!_isConnected.value) return false
        val success = client.switchChannel(channel)
        if (success) {
            _currentChannel.value = channel
        }
        return success
    }

    suspend fun sendRcuKey(keyCode: Int): Boolean {
        if (!_isConnected.value) return false
        return client.sendRcuKey(keyCode)
    }
}
