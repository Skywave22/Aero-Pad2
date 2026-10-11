package com.bluepilot.remote.wifi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AEROPAD v1.0 — dual-mode transport selector.
 *
 * Single source of truth for which transport carries input right now.
 * The HID action pipeline consults this at exactly ONE point
 * (SendHidActionUseCase), so every screen keeps working identically —
 * only the wire changes. STABILITY: Bluetooth path is byte-for-byte
 * untouched when mode == BLUETOOTH (default).
 */
@Singleton
class TransportManager @Inject constructor() {

    enum class Mode { BLUETOOTH, WIFI }

    private val _mode = MutableStateFlow(Mode.BLUETOOTH)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    fun setMode(mode: Mode) { _mode.value = mode }

    val isWifi: Boolean get() = _mode.value == Mode.WIFI
}
