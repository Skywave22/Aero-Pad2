package com.aeropad.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeropad.remote.domain.AirMouseCore
import com.aeropad.remote.domain.AxisLock
import com.aeropad.remote.domain.usecase.ObserveConnectionUseCase
import com.aeropad.remote.domain.usecase.SendHidActionUseCase
import com.aeropad.remote.model.HidAction
import com.aeropad.remote.model.MouseButton
import com.aeropad.remote.sensors.MotionSensorSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Air Mouse: point the phone like a Wii remote — gyro moves the cursor.
 * Streaming only runs while the screen is active AND the user armed it.
 */
@HiltViewModel
class AirMouseViewModel @Inject constructor(
    private val sensors: MotionSensorSource,
    observeConnection: ObserveConnectionUseCase,
    private val sendAction: SendHidActionUseCase
) : ViewModel() {

    val hasGyro: Boolean get() = sensors.hasGyroscope

    val isConnected: StateFlow<Boolean> = observeConnection()
        .map { it.isConnected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val core = AirMouseCore()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _sensitivity = MutableStateFlow(50)
    val sensitivity: StateFlow<Int> = _sensitivity.asStateFlow()

    private val _smoothing = MutableStateFlow(40)
    val smoothing: StateFlow<Int> = _smoothing.asStateFlow()

    private val _axisLock = MutableStateFlow(AxisLock.BOTH)
    val axisLock: StateFlow<AxisLock> = _axisLock.asStateFlow()

    private var streamJob: Job? = null
    private var lastFlushMs = 0L

    fun setSensitivity(value: Int) {
        _sensitivity.value = value.coerceIn(0, 100)
        core.sensitivity = _sensitivity.value
    }

    fun setSmoothing(value: Int) {
        _smoothing.value = value.coerceIn(0, 100)
        core.smoothing = _smoothing.value
    }

    fun setAxisLock(lock: AxisLock) {
        _axisLock.value = lock
        core.axisLock = lock
    }

    /** Toggle air-mouse streaming. */
    fun setActive(value: Boolean) {
        if (value == _active.value) return
        _active.value = value
        if (value) start() else stop()
    }

    private fun start() {
        core.recenter()
        core.resetBias()
        // Round 15 — gyros report at 100-400 Hz, but a Bluetooth HID link is
        // happiest around 60 reports/s. Sending one per sample queued reports
        // faster than the radio could drain them, which is what made the air
        // mouse feel laggy under movement. Samples are integrated as they
        // arrive and flushed at a steady cadence.
        var pendingX = 0f
        var pendingY = 0f
        streamJob = sensors.gyro()
            .onEach { sample ->
                // Device held like a pointer: yaw = rotation about Y axis,
                // pitch = about X axis.
                val (dx, dy) = core.step(
                    gyroYaw = sample.y,
                    gyroPitch = sample.x,
                    timestampNs = sample.timestampNs
                )
                pendingX += dx
                pendingY += dy
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastFlushMs >= FLUSH_INTERVAL_MS) {
                    lastFlushMs = now
                    val fx = pendingX.toInt()
                    val fy = pendingY.toInt()
                    if (fx != 0 || fy != 0) {
                        pendingX -= fx
                        pendingY -= fy
                        sendAction(
                            HidAction.MouseMove(
                                fx.coerceIn(-127, 127),
                                fy.coerceIn(-127, 127)
                            )
                        )
                    }
                }
            }
            .catch { /* sensor stream errors are non-fatal */ }
            .launchIn(viewModelScope)
    }

    private companion object {
        /** ~60 reports per second is both smooth and kind to the radio. */
        const val FLUSH_INTERVAL_MS = 16L
    }

    private fun stop() {
        streamJob?.cancel()
        streamJob = null
    }

    /** Calibration: reset center point / accumulated smoothing. */
    /**
     * Puts the pointer back on the "front" of the phone AND relearns the gyro
     * bias. Holding Recenter is the calibration gesture: point the phone at the
     * screen, tap once, and the drift that built up while it lay on the sofa
     * is gone.
     */
    fun recenter() {
        core.recenter()
        core.resetBias()
    }

    fun click(button: MouseButton) = sendAction(HidAction.MouseClick(button))

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
