package com.bluepilot.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluepilot.remote.domain.AirMouseCore
import com.bluepilot.remote.domain.AxisLock
import com.bluepilot.remote.domain.usecase.ObserveConnectionUseCase
import com.bluepilot.remote.domain.usecase.SendHidActionUseCase
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.MouseButton
import com.bluepilot.remote.sensors.MotionSensorSource
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
        streamJob = sensors.gyro()
            .onEach { sample ->
                // Device held like a pointer: yaw = rotation about Y axis,
                // pitch = about X axis.
                val (dx, dy) = core.step(gyroYaw = sample.y, gyroPitch = sample.x)
                if (dx != 0 || dy != 0) sendAction(HidAction.MouseMove(dx, dy))
            }
            .catch { /* sensor stream errors are non-fatal */ }
            .launchIn(viewModelScope)
    }

    private fun stop() {
        streamJob?.cancel()
        streamJob = null
    }

    /** Calibration: reset center point / accumulated smoothing. */
    fun recenter() = core.recenter()

    fun click(button: MouseButton) = sendAction(HidAction.MouseClick(button))

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
