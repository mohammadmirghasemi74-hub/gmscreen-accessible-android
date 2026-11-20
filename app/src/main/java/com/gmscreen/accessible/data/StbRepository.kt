package com.gmscreen.accessible.data

import android.content.Context
import com.gmscreen.accessible.data.model.Channel
import com.gmscreen.accessible.data.model.Satellite
import com.gmscreen.accessible.data.model.StbDevice
import com.gmscreen.accessible.data.model.StbState
import com.gmscreen.accessible.protocol.RcuKey
import com.gmscreen.accessible.protocol.StbClient
import com.gmscreen.accessible.protocol.StbDiscovery
import com.gmscreen.accessible.util.AppConfig
import com.gmscreen.accessible.util.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class StbRepository(private val context: Context) {

    private val appConfig = AppConfig(context)
    private val discovery = StbDiscovery(context)
    val client = StbClient()

    var currentStbMode: Int = 0 // 0 = TV, 1 = Radio

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()

    sealed class ReconnectEvent {
        object Attempting : ReconnectEvent()
        data class Reconnected(val ip: String) : ReconnectEvent()
        object Failed : ReconnectEvent()
    }

    private val _reconnectEvent = MutableSharedFlow<ReconnectEvent>(extraBufferCapacity = 16)
    val reconnectEvent: SharedFlow<ReconnectEvent> = _reconnectEvent.asSharedFlow()

    private var isUserInitiatedDisconnect = false
    private var reconnectJob: Job? = null

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

    private var monitorJob: Job? = null
    private val repoScope = CoroutineScope(Dispatchers.IO)

    suspend fun discoverDevices(): List<StbDevice> {
        return discovery.discover(timeoutMs = 3500)
    }

    suspend fun connect(ip: String): Result<Boolean> {
        isUserInitiatedDisconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        _isReconnecting.value = false
        return try {
            val success = client.connect(ip)
            if (success) {
                _isConnected.value = true
                _connectedIp.value = ip
                appConfig.lastConnectedIp = ip
                _deviceModel.value = client.formattedModel

                try {
                    com.gmscreen.accessible.service.GMScreenBackgroundService.startService(context)
                } catch (_: Exception) {}

                // Launch initial data fetch in background cleanly without monitor thread interference
                repoScope.launch {
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

    private fun startChannelMonitor() {
        monitorJob?.cancel()
        monitorJob = repoScope.launch {
            var consecutiveErrors = 0
            while (isActive && client.isConnected) {
                delay(800)
                if (!client.isConnected) break
                // Avoid socket conflicts if user sent a command recently (matching Windows architecture)
                if (System.currentTimeMillis() - client.lastUserCmdTime < 1200L) {
                    continue
                }
                try {
                    val pid = client.getPlayingChannel()
                    if (pid != null) {
                        consecutiveErrors = 0
                        val cleanPid = pid.trim()
                        if (cleanPid.isNotBlank() && cleanPid != _currentChannel.value?.serviceId) {
                            val ch = _channels.value.find { c ->
                                c.serviceId == cleanPid ||
                                c.serviceId.endsWith(cleanPid) ||
                                cleanPid.endsWith(c.serviceId) ||
                                c.serviceId.trimStart('0') == cleanPid.trimStart('0')
                            }
                            if (ch != null) {
                                AppLogger.i("StbRepository", "Channel monitor: playing channel changed to ${ch.serviceName} (${ch.serviceId})")
                                _currentChannel.value = ch
                            }
                        }
                    } else {
                        consecutiveErrors++
                    }
                } catch (e: Exception) {
                    consecutiveErrors++
                    if (e is java.net.SocketTimeoutException || e is java.io.IOException) {
                        delay(1200)
                    }
                }

                // Never terminate monitor loop mid-zap on transient timeouts.
                // Only disconnect if client socket is truly closed or after 30 consecutive failures (>45s of total silence).
                if (!client.isConnected || consecutiveErrors >= 30) {
                    break
                }
            }

            if (!isUserInitiatedDisconnect && _isConnected.value) {
                AppLogger.w("StbRepository", "Connection lost unexpectedly. Triggering smart reconnect...")
                triggerSmartReconnect()
            }
        }
    }

    fun triggerSmartReconnect() {
        if (_isReconnecting.value || isUserInitiatedDisconnect) return
        _isReconnecting.value = true
        _reconnectEvent.tryEmit(ReconnectEvent.Attempting)

        reconnectJob?.cancel()
        reconnectJob = repoScope.launch {
            val targetIp = appConfig.lastConnectedIp.ifBlank { _connectedIp.value }
            AppLogger.i("StbRepository", "Starting smart auto-reconnect loop to $targetIp...")

            var reconnected = false
            var activeIp = targetIp
            // 30 attempts, each with 3s delay = 90 seconds (standard STB reboot duration)
            for (attempt in 1..30) {
                if (!isActive || isUserInitiatedDisconnect) break
                delay(3000)

                // Try direct connect to last known IP
                if (activeIp.isNotBlank()) {
                    val ok = client.connect(activeIp, timeoutMs = 2500)
                    if (ok) {
                        reconnected = true
                        break
                    }
                }

                // Every 15 seconds (attempts 5, 10, 15, 20), run background UDP discovery in case DHCP IP changed
                if (attempt % 5 == 0) {
                    try {
                        val devices = discovery.discover(timeoutMs = 2500)
                        if (devices.isNotEmpty()) {
                            val foundDevice = devices.first()
                            activeIp = foundDevice.ip
                            val ok = client.connect(activeIp, timeoutMs = 2500)
                            if (ok) {
                                reconnected = true
                                break
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            _isReconnecting.value = false

            if (reconnected) {
                _isConnected.value = true
                _connectedIp.value = activeIp
                appConfig.lastConnectedIp = activeIp
                _deviceModel.value = client.formattedModel
                AppLogger.i("StbRepository", "Smart reconnect SUCCEEDED to $activeIp")
                _reconnectEvent.tryEmit(ReconnectEvent.Reconnected(activeIp))
                try {
                    com.gmscreen.accessible.service.GMScreenBackgroundService.startService(context)
                } catch (_: Exception) {}
                refreshData()
            } else {
                _isConnected.value = false
                _currentState.value = null
                AppLogger.w("StbRepository", "Smart reconnect FAILED after timeout.")
                _reconnectEvent.tryEmit(ReconnectEvent.Failed)
            }
        }
    }

    fun disconnect() {
        isUserInitiatedDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        _isReconnecting.value = false
        monitorJob?.cancel()
        monitorJob = null
        client.disconnect()
        _isConnected.value = false
        _currentState.value = null
        _stbTimers.value = emptyList()
        _pvrFiles.value = emptyList()
    }

    suspend fun refreshData() {
        if (!client.isConnected) return
        monitorJob?.cancel()
        try {
            // 1. Fetch STB State first to get channel count
            val state = client.fetchStbState()
            _currentState.value = state
            currentStbMode = if (state.curChannelType == 1) 1 else 0

            // 2. Fetch Satellites for names
            val sats = client.fetchSatellites()
            _satellites.value = sats

            // 3. Fetch Channels in 100-channel pages
            val toIdx = if (state.channelNum > 0) state.channelNum - 1 else 800
            val chList = client.fetchChannels(toIndex = toIdx)

            if (chList.isNotEmpty()) {
                val satMap = sats.associate { it.satelliteId to it.satelliteName }
                var tvCounter = 1
                var radioCounter = 1
                for (ch in chList) {
                    ch.satelliteName = satMap[ch.satIndex] ?: "ماهواره #${ch.satIndex}"
                    if (ch.isRadio) {
                        ch.realChannelNum = radioCounter++
                    } else {
                        ch.realChannelNum = tvCounter++
                    }
                }

                // 4. Update currently playing channel BEFORE setting channels to avoid race conditions
                val playingPid = if (state.playingServiceId.isNotBlank()) state.playingServiceId else client.getPlayingChannel()
                val matched = if (!playingPid.isNullOrBlank()) {
                    chList.find { it.serviceId == playingPid || it.serviceId.endsWith(playingPid) }
                } else null

                _currentChannel.value = matched
                _channels.value = chList
                com.gmscreen.accessible.voice.VoiceCustomizationManager.autoIndexChannels(context, chList)
            }

            // 5. Fetch transponders in background only if not cached yet (needed for stream playback)
            if (client.transponders.isEmpty()) {
                client.fetchTransponders()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            startChannelMonitor()
        }
    }

    suspend fun moveChannel(sourceChannel: Channel, targetChannel: Channel): Boolean {
        if (!client.isConnected) return false
        val ok = client.moveChannel(sourceChannel.serviceId, targetChannel.serviceId, sourceChannel.isRadio)
        if (ok) {
            val currentList = _channels.value.toMutableList()
            val srcIdx = currentList.indexOfFirst { it.serviceId == sourceChannel.serviceId }
            val tgtIdx = currentList.indexOfFirst { it.serviceId == targetChannel.serviceId }
            if (srcIdx != -1 && tgtIdx != -1) {
                val item = currentList.removeAt(srcIdx)
                currentList.add(tgtIdx, item)
                var tvCounter = 1
                var radioCounter = 1
                for (ch in currentList) {
                    if (ch.isRadio) {
                        ch.realChannelNum = radioCounter++
                    } else {
                        ch.realChannelNum = tvCounter++
                    }
                }
                _channels.value = currentList
            }
        }
        return ok
    }

    suspend fun deleteChannel(channel: Channel): Boolean {
        if (!client.isConnected) return false
        val ok = client.deleteChannel(channel.serviceId, channel.isRadio)
        if (ok) {
            val currentList = _channels.value.toMutableList()
            currentList.removeAll { it.serviceId == channel.serviceId }
            var tvCounter = 1
            var radioCounter = 1
            for (ch in currentList) {
                if (ch.isRadio) {
                    ch.realChannelNum = radioCounter++
                } else {
                    ch.realChannelNum = tvCounter++
                }
            }
            _channels.value = currentList
            if (_currentChannel.value?.serviceId == channel.serviceId) {
                _currentChannel.value = currentList.firstOrNull { it.isRadio == channel.isRadio }
            }
        }
        return ok
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
            currentStbMode = if (channel.isRadio) 1 else 0
            _currentChannel.value = channel
        }
        return success
    }

    suspend fun saveStbSettings(settings: Map<String, Any>): Boolean {
        if (!client.isConnected) return false
        return client.saveStbSettings(settings)
    }

    suspend fun sendRcuKey(keyCode: Int): Boolean {
        if (!client.isConnected) return false
        return client.sendRcuKey(keyCode)
    }

    suspend fun restartStb(): Boolean {
        if (!client.isConnected) return false
        val ok = client.restartStb()
        if (ok) {
            isUserInitiatedDisconnect = false
            client.disconnect()
            triggerSmartReconnect()
        }
        return ok
    }

    suspend fun getMuteState(): Boolean? {
        if (!client.isConnected) return null
        return client.getMuteState()
    }

    suspend fun isStbPowerOn(): Boolean {
        if (!client.isConnected) return false
        return client.isStbPowerOn()
    }

    suspend fun addStbTimer(timer: Map<String, Any>): Boolean {
        if (!client.isConnected) return false
        val ok = client.addStbTimer(timer)
        if (ok) refreshTimers()
        return ok
    }

    suspend fun getEpg(programId: String): Map<String, Any>? {
        if (!client.isConnected) return null
        return client.getEpg(programId)
    }

    suspend fun deleteStbTimer(timer: Map<String, Any>): Boolean {
        if (!client.isConnected) return false
        val ok = client.deleteStbTimer(timer)
        if (ok) refreshTimers()
        return ok
    }

    suspend fun changePassword(oldPwd: String, newPwd: String): Boolean {
        if (!client.isConnected) return false
        return client.changePassword(oldPwd, newPwd)
    }
}
