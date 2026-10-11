package com.bluepilot.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluepilot.remote.data.hosts.HostProfile
import com.bluepilot.remote.data.hosts.HostProfiles
import com.bluepilot.remote.domain.HidController
import com.bluepilot.remote.wifi.TransportManager
import com.bluepilot.remote.wifi.WifiEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AEROPAD v1.0 — WiFi connection screen driver.
 * Thin passthrough: all state lives in the singleton WifiEngine so the
 * connection survives navigation (same pattern as HidEngine).
 *
 * V2 MATRIX 4 — multi-host quick-switch: saved host profiles (BT + WiFi)
 * with one-tap reconnect. WiFi profiles may save the PIN (opt-in);
 * profiles are persisted only after a SUCCESSFUL handshake.
 */
@HiltViewModel
class WifiConnectViewModel @Inject constructor(
    private val engine: WifiEngine,
    private val transport: TransportManager,
    private val hostStore: HostProfiles,
    private val hid: HidController
) : ViewModel() {

    val state: StateFlow<WifiEngine.WifiState> = engine.state
    val discovered: StateFlow<List<WifiEngine.DiscoveredHost>> = engine.discovered
    val latencyMs: StateFlow<Long?> = engine.latencyMs
    val mode: StateFlow<TransportManager.Mode> = transport.mode

    // V2 M4 — saved host profiles for the quick-switch row.
    val hostProfiles: StateFlow<List<HostProfile>> = hostStore.profiles

    /** Pending save: (host, port, pin?) applied when Connected arrives. */
    private var pendingSave: Triple<String, Int, String?>? = null

    init {
        // Persist the profile ONLY on a successful handshake — a wrong PIN
        // or dead host never creates a profile.
        viewModelScope.launch {
            engine.state.collect { s ->
                if (s is WifiEngine.WifiState.Connected) {
                    pendingSave?.let { (host, port, pin) ->
                        hostStore.saveWifi(label = s.name, host = host, port = port, pin = pin)
                    }
                    pendingSave = null
                }
            }
        }
    }

    fun setMode(mode: TransportManager.Mode) = transport.setMode(mode)
    fun startDiscovery() = engine.startDiscovery()
    fun stopDiscovery() = engine.stopDiscovery()
    fun requestConnect(host: String, port: Int = com.bluepilot.remote.wifi.WifiProtocol.DEFAULT_PORT) =
        engine.requestConnect(host, port)

    /** V2 M4 — [savePin]: opt-in per connection; stored obfuscated. */
    fun connectWithPin(pin: String, savePin: Boolean = false) {
        val s = state.value
        if (s is WifiEngine.WifiState.AwaitingPin) {
            pendingSave = Triple(s.host, s.port, if (savePin) pin else null)
        }
        engine.connectWithPin(pin)
    }

    fun disconnect() = engine.disconnect()

    // ------------------------------------------------------------------
    // V2 MATRIX 4 — one-tap quick-switch
    // ------------------------------------------------------------------

    /**
     * Connect to a saved profile. WiFi with a saved PIN handshakes
     * immediately; without one it lands on the PIN prompt. BT switches
     * transport and asks HidEngine to connect to the stored MAC.
     */
    fun quickConnect(profile: HostProfile) {
        hostStore.touch(profile.id)
        when (profile.transport) {
            HostProfile.TRANSPORT_WIFI -> {
                transport.setMode(TransportManager.Mode.WIFI)
                engine.requestConnect(profile.address, profile.port)
                val pin = hostStore.pinFor(profile)
                if (pin.isNotEmpty()) engine.connectWithPin(pin)
            }
            else -> {
                transport.setMode(TransportManager.Mode.BLUETOOTH)
                runCatching { hid.connectTo(profile.address) }
            }
        }
    }

    /** Save the CURRENT Bluetooth host as a profile (from bonded list). */
    fun saveBtProfile(label: String, mac: String) = hostStore.saveBt(label, mac)

    fun removeProfile(id: String) = hostStore.remove(id)
}
