package com.bluepilot.remote.viewmodel

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluepilot.remote.domain.ConnectionHealthTracker
import com.bluepilot.remote.domain.SettingsStore
import com.bluepilot.remote.domain.usecase.SendHidActionUseCase
import com.bluepilot.remote.hid.HidEngine
import com.bluepilot.remote.model.GamepadSnapshot
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.HidConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ADV SECTION 5 — Connection Health dashboard driver.
 *
 * ALL surfaced numbers are real:
 *  - report counters / send durations: measured in HidEngine around the
 *    actual sendReport() framework call,
 *  - battery level/current: BatteryManager (hardware fuel gauge),
 *  - battery saver: PowerManager.isPowerSaveMode,
 *  - reconnect status: HidEngine's real backoff state.
 * What Android does NOT provide (host round-trip time, RSSI on a HID
 * Device-role link) is labeled as unavailable in the UI — never invented.
 */
@HiltViewModel
class ConnectionHealthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: HidEngine,
    private val sendAction: SendHidActionUseCase,
    settingsStore: SettingsStore,
    // AEROPAD v1.0 — WiFi metrics for the dual-mode dashboard.
    private val wifiEngine: com.bluepilot.remote.wifi.WifiEngine,
    private val transportManager: com.bluepilot.remote.wifi.TransportManager,
    private val historyStore: com.bluepilot.remote.data.history.ConnectionHistoryStore
) : ViewModel() {

    val connection: StateFlow<HidConnectionState> = engine.state

    val reconnectStatus: StateFlow<Pair<Int, Int>?> = engine.reconnectStatus

    /** AEROPAD v1.0 #39 — real next-retry timestamp for countdown UI. */
    val reconnectNextAtMs: StateFlow<Long?> = engine.reconnectNextAtMs

    // ----- AEROPAD v1.0: WiFi-side state (all REAL) -----
    val transportMode: StateFlow<com.bluepilot.remote.wifi.TransportManager.Mode> =
        transportManager.mode
    val wifiState: StateFlow<com.bluepilot.remote.wifi.WifiEngine.WifiState> = wifiEngine.state
    /** Real measured WiFi round-trip time (ping/echo), null until measured. */
    val wifiLatencyMs: StateFlow<Long?> = wifiEngine.latencyMs
    /** Real WiFi counters: (sent, failed). */
    val wifiCounters: StateFlow<Pair<Long, Long>> = wifiEngine.counters
    fun setTransportMode(mode: com.bluepilot.remote.wifi.TransportManager.Mode) =
        transportManager.setMode(mode)

    // AEROPAD v1.0 #41 — real past sessions (refreshed on screen open).
    private val _history = MutableStateFlow<List<com.bluepilot.remote.data.history.ConnectionSession>>(emptyList())
    val history: StateFlow<List<com.bluepilot.remote.data.history.ConnectionSession>> = _history.asStateFlow()
    fun refreshHistory() { _history.value = historyStore.all() }

    private val _snapshot = MutableStateFlow(engine.health.snapshot())
    val snapshot: StateFlow<ConnectionHealthTracker.Snapshot> = _snapshot.asStateFlow()

    private val _batterySaver = MutableStateFlow(false)
    val batterySaver: StateFlow<Boolean> = _batterySaver.asStateFlow()

    /** (levelPercent, currentMicroAmps?) — both from BatteryManager. */
    private val _battery = MutableStateFlow<Pair<Int, Int?>>(0 to null)
    val battery: StateFlow<Pair<Int, Int?>> = _battery.asStateFlow()

    val appSettings = settingsStore.appSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.bluepilot.remote.model.AppSettings())

    init {
        // 1Hz refresh while this screen is alive (ViewModel scope).
        viewModelScope.launch {
            while (true) {
                _snapshot.value = engine.health.snapshot()
                readBattery()
                delay(1000)
            }
        }
    }

    private fun readBattery() {
        runCatching {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            _batterySaver.value = pm?.isPowerSaveMode == true
            val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
            // CURRENT_NOW: microamps; negative = discharging on most devices.
            val current = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                ?.takeIf { it != Int.MIN_VALUE && it != 0 }
            _battery.value = level to current
        }
    }

    // ------------------------------------------------------------------
    // Diagnostic test: sends a burst of REAL neutral gamepad reports and
    // reports the measured stats for exactly that burst.
    // ------------------------------------------------------------------

    data class DiagnosticResult(
        val sent: Int,
        val failed: Int,
        val meanUs: Long,
        val maxUs: Long,
        val totalMs: Long
    )

    private val _diagnostic = MutableStateFlow<DiagnosticResult?>(null)
    val diagnostic: StateFlow<DiagnosticResult?> = _diagnostic.asStateFlow()

    private val _diagRunning = MutableStateFlow(false)
    val diagRunning: StateFlow<Boolean> = _diagRunning.asStateFlow()

    fun runDiagnostic() {
        if (_diagRunning.value) return
        _diagRunning.value = true
        viewModelScope.launch {
            val before = engine.health.snapshot()
            val t0 = System.currentTimeMillis()
            // 25 neutral reports at ~50Hz — harmless (all-neutral state).
            repeat(25) {
                sendAction(HidAction.GamepadUpdate(GamepadSnapshot()))
                delay(20)
            }
            delay(150) // let the HID thread drain
            val after = engine.health.snapshot()
            val sent = (after.totalSent - before.totalSent).toInt()
            val failed = (after.totalFailed - before.totalFailed).toInt()
            _diagnostic.value = DiagnosticResult(
                sent = sent,
                failed = failed,
                meanUs = after.recentMeanSendUs,
                maxUs = after.recentMaxSendUs,
                totalMs = System.currentTimeMillis() - t0
            )
            _diagRunning.value = false
        }
    }

    /** Export a plain-text diagnostic log (real values only). */
    fun buildDiagnosticLog(): String {
        val s = _snapshot.value
        val conn = connection.value
        val diag = _diagnostic.value
        return buildString {
            appendLine("BluePilot Remote — diagnostic log")
            appendLine("generated: ${java.util.Date()}")
            appendLine("connection: ${conn::class.simpleName}" +
                ((conn as? HidConnectionState.Connected)?.device?.let { "  host=${it.name} (${it.address})" } ?: ""))
            appendLine("session: ${s.sessionMs / 1000}s, disconnects=${s.disconnects}")
            appendLine("reports: sent=${s.totalSent} failed=${s.totalFailed} rate=${s.reportsPerSecond}/s")
            appendLine("send-time (BluetoothHidDevice.sendReport, measured): mean=${s.recentMeanSendUs}µs max=${s.recentMaxSendUs}µs (last 5s)")
            appendLine("rssi: ${s.rssiDbm?.let { "$it dBm" } ?: "not exposed by platform for HID-device role"}")
            appendLine("battery: ${_battery.value.first}%" +
                (_battery.value.second?.let { ", current=${it / 1000}mA" } ?: "") +
                if (_batterySaver.value) ", BATTERY SAVER ON" else "")
            diag?.let {
                appendLine("last diagnostic burst: sent=${it.sent} failed=${it.failed} mean=${it.meanUs}µs max=${it.maxUs}µs over ${it.totalMs}ms")
            }
            appendLine()
            appendLine("history (per second: sent/failed/mean µs):")
            s.history.takeLast(60).forEach { b ->
                appendLine("  t${b.epochSecond % 100000}: ${b.sent}/${b.failed}/${b.meanSendUs}")
            }
        }
    }

    private val _exportPayload = MutableStateFlow<Pair<String, String>?>(null)
    val exportPayload: StateFlow<Pair<String, String>?> = _exportPayload.asStateFlow()
    fun requestExport() {
        _exportPayload.value = "bluepilot-diagnostics.txt" to buildDiagnosticLog()
    }
    fun consumeExport() { _exportPayload.value = null }
}
