package com.aeropad.remote.data.tv

import com.aeropad.remote.model.HidAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-wide selected Wi-Fi TV target. Once selected, supported AeroPad
 * actions from other screens are routed here until the user disconnects the
 * target; unsupported HID-only actions remain on Bluetooth.
 */
object WifiTvSession {
    enum class RouteResult { NO_TV_TARGET, ROUTED_TO_TV, UNSUPPORTED_BY_TV }

    private val client = WifiTvRemoteClient()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outbound = Channel<String>(Channel.UNLIMITED)
    private val _targetIp = MutableStateFlow<String?>(null)
    val targetIp: StateFlow<String?> = _targetIp.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    private var liveTextBuffer = ""
    private var liveTextEntry = false

    init {
        ioScope.launch {
            for (command in outbound) {
                runCatching { client.sendRemoteCommand(command) }
                    .onFailure { _notice.value = it.message ?: "Could not send input to the Wi-Fi TV." }
            }
        }
    }

    fun connectedForControls(bluetooth: kotlinx.coroutines.flow.Flow<com.aeropad.remote.model.HidConnectionState>): kotlinx.coroutines.flow.Flow<Boolean> =
        kotlinx.coroutines.flow.combine(bluetooth, targetIp) { state, ip -> state.isConnected || ip != null }

    fun selectTarget(ip: String) {
        if (PhoneRemoteProtocol.isIpv4(ip)) {
            _targetIp.value = ip
            _notice.value = null
            liveTextBuffer = ""
            liveTextEntry = false
        }
    }

    fun disconnect() {
        _targetIp.value = null
        _notice.value = null
        liveTextBuffer = ""
        liveTextEntry = false
        ioScope.launch { client.disconnect() }
    }

    /** App keyboard relay sends only its changed text fragments; accumulate them for TV search. */
    fun typeLiveChunk(text: String): Boolean {
        if (_targetIp.value == null || text.isEmpty()) return false
        val command = synchronized(this) {
            if (!liveTextEntry) {
                liveTextBuffer = ""
                liveTextEntry = true
            }
            liveTextBuffer += text
            PhoneRemoteProtocol.searchCommand(liveTextBuffer)
        }
        sendCommand(command)
        return true
    }

    /** Returns whether a selected TV was used, unsupported, or not selected. */
    fun route(action: HidAction, bluetoothAvailable: Boolean): RouteResult {
        if (_targetIp.value == null) return RouteResult.NO_TV_TARGET
        if (action is HidAction.TypeText) {
            synchronized(this) {
                liveTextBuffer = action.text
                liveTextEntry = true
            }
            sendCommand(PhoneRemoteProtocol.searchCommand(action.text))
            return RouteResult.ROUTED_TO_TV
        }
        if (action is HidAction.KeyTap && action.modifiers.toInt() == 0) {
            when (action.key) {
                com.aeropad.remote.model.HidKeys.BACKSPACE -> {
                    val command = synchronized(this) {
                        if (!liveTextEntry) null else {
                            liveTextBuffer = liveTextBuffer.dropLast(1)
                            PhoneRemoteProtocol.searchCommand(liveTextBuffer)
                        }
                    }
                    if (command != null) {
                        sendCommand(command)
                        return RouteResult.ROUTED_TO_TV
                    }
                }
                com.aeropad.remote.model.HidKeys.ENTER -> synchronized(this) {
                    liveTextBuffer = ""
                    liveTextEntry = false
                }
            }
        }
        val commands = WifiTvActionMapper.commands(action)
        if (commands == null) {
            _notice.value = if (bluetoothAvailable) {
                "This control is not supported by the selected TV; it was sent to your Bluetooth host instead."
            } else {
                "This control needs Bluetooth HID; the selected TV protocol does not support it."
            }
            return RouteResult.UNSUPPORTED_BY_TV
        }
        sendCommands(commands)
        return RouteResult.ROUTED_TO_TV
    }

    private fun sendCommand(command: String) = sendCommands(listOf(command))

    private fun sendCommands(commands: List<String>) {
        commands.forEach { command ->
            if (outbound.trySend(command).isFailure) {
                _notice.value = "Could not queue input for the Wi-Fi TV."
            }
        }
    }

    fun consumeNotice() {
        _notice.value = null
    }
}
