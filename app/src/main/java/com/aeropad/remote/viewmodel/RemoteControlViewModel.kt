package com.aeropad.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeropad.remote.domain.SettingsStore
import com.aeropad.remote.domain.usecase.ObserveConnectionUseCase
import com.aeropad.remote.domain.usecase.SendHidActionUseCase
import com.aeropad.remote.hid.PointerMath
import com.aeropad.remote.model.DpadDirection
import com.aeropad.remote.model.GamepadButton
import com.aeropad.remote.model.GamepadKeyboardMapping
import com.aeropad.remote.model.GamepadMappingMode
import com.aeropad.remote.model.GamepadSettings
import com.aeropad.remote.model.GamepadSnapshot
import com.aeropad.remote.model.HidAction
import com.aeropad.remote.model.HidConnectionState
import com.aeropad.remote.model.HidKeys
import com.aeropad.remote.model.KeyboardSettings
import com.aeropad.remote.model.MouseButton
import com.aeropad.remote.model.MouseSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Shared driver for all control screens (mouse/keyboard/numpad/media/
 * presenter/gamepad). Applies user settings to raw touch input, then emits
 * HidActions through the UseCase layer.
 */
@HiltViewModel
class RemoteControlViewModel @Inject constructor(
    observeConnection: ObserveConnectionUseCase,
    private val sendAction: SendHidActionUseCase,
    settingsStore: SettingsStore,
    // Round 7 — Pro gamepad: motion input + per-game profiles.
    val motionSource: com.aeropad.remote.domain.MotionSource,
    val gamepadProfiles: com.aeropad.remote.data.gamepad.GamepadProfileStore
) : ViewModel() {

    val connectionState: StateFlow<HidConnectionState> = observeConnection()
        .stateIn(viewModelScope, SharingStarted.Eagerly, HidConnectionState.Idle)

    val isConnected: StateFlow<Boolean> = observeConnection()
        .map { it.isConnected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val mouseSettings: StateFlow<MouseSettings> = settingsStore.mouseSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, MouseSettings())

    val keyboardSettings: StateFlow<KeyboardSettings> = settingsStore.keyboardSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, KeyboardSettings())

    val gamepadSettings: StateFlow<GamepadSettings> = settingsStore.gamepadSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, GamepadSettings())

    val vibrationsEnabled: StateFlow<Boolean> = settingsStore.appSettings
        .map { it.touchVibrations }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // ------------------------------------------------------------------
    // Mouse
    // ------------------------------------------------------------------

    // OPTIMIZATION: shared TrackpadEngine (was ~30 duplicated lines).
    private val trackpad = com.aeropad.remote.domain.TrackpadEngine { mouseSettings.value }

    /** Raw trackpad drag delta in px → settings-adjusted HID mouse move. */
    fun onTrackpadDelta(dxPx: Float, dyPx: Float) {
        // AEROPAD v1.0 #19 — Precision Mode: slow-motion cursor (x0.35).
        val scale = if (_precisionMode.value) 0.35f else 1f
        val (ix, iy) = trackpad.move(dxPx * scale, dyPx * scale)
        if (ix != 0 || iy != 0) sendAction(HidAction.MouseMove(ix, iy))
    }

    // AEROPAD v1.0 #19 — precision mode toggle (session state).
    private val _precisionMode = kotlinx.coroutines.flow.MutableStateFlow(false)
    val precisionMode: kotlinx.coroutines.flow.StateFlow<Boolean> = _precisionMode
    fun setPrecisionMode(on: Boolean) { _precisionMode.value = on }

    // AEROPAD v1.0 #22 — click-and-drag lock: LEFT held until unlocked.
    private val _dragLock = kotlinx.coroutines.flow.MutableStateFlow(false)
    val dragLock: kotlinx.coroutines.flow.StateFlow<Boolean> = _dragLock
    fun toggleDragLock() {
        if (_dragLock.value) {
            _dragLock.value = false
            sendAction(HidAction.MouseUp(MouseButton.LEFT))
        } else {
            _dragLock.value = true
            sendAction(HidAction.MouseDown(MouseButton.LEFT))
        }
    }

    /** Reset motion state when a new gesture starts (prevents smoothing bleed). */
    fun onTrackpadGestureStart() = trackpad.startGesture()

    /** Tap on trackpad → left click (honors tap-to-click setting). */
    fun onTrackpadTap() {
        if (mouseSettings.value.tapToClick) sendAction(HidAction.MouseClick(MouseButton.LEFT))
    }

    fun onTrackpadDoubleTap() {
        if (mouseSettings.value.tapToClick) sendAction(HidAction.MouseDoubleClick(MouseButton.LEFT))
    }

    /** Long-press on trackpad → right click. */
    fun onTrackpadLongPress() = sendAction(HidAction.MouseClick(MouseButton.RIGHT))

    fun clickButton(button: MouseButton) = sendAction(HidAction.MouseClick(button))
    fun buttonDown(button: MouseButton) = sendAction(HidAction.MouseDown(button))
    fun buttonUp() = sendAction(HidAction.MouseUp(MouseButton.LEFT))

    /** Scroll strip drag: accumulate px, emit whole wheel steps. */
    fun onScrollDelta(dyPx: Float) {
        trackpad.scroll(dyPx).takeIf { it != 0 }?.let { sendAction(HidAction.MouseScroll(it)) }
    }

    // ------------------------------------------------------------------
    // Keyboard / text
    // ------------------------------------------------------------------

    fun keyTap(key: Byte, modifiers: Byte = 0) = sendAction(HidAction.KeyTap(key, modifiers))
    fun typeText(text: String) {
        if (text.isNotEmpty()) {
            sendAction(HidAction.TypeText(text))
            // AEROPAD v1.0 #12 — clipboard history: last 10 sent texts,
            // newest first, deduped (session-scoped, privacy-friendly).
            _sentHistory.value =
                (listOf(text) + _sentHistory.value.filterNot { it == text }).take(10)
        }
    }

    // AEROPAD v1.0 #12 — history of texts sent to the host (tap to resend).
    private val _sentHistory = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    val sentHistory: kotlinx.coroutines.flow.StateFlow<List<String>> = _sentHistory

    // ------------------------------------------------------------------
    // Media / system
    // ------------------------------------------------------------------

    fun mediaTap(usage: Int) = sendAction(HidAction.MediaTap(usage))

    /** System page tap (power / sleep / wake bits). */
    fun systemTap(bits: Byte) = sendAction(HidAction.SystemTap(bits))

    /**
     * Touch-screen control: streams an absolute HID digitizer contact to the
     * host. [x]/[y] are normalized 0..1 across the host screen.
     */
    fun touch(x: Float, y: Float, contact: Boolean) =
        sendAction(HidAction.TouchUpdate(contact, x, y))

    /** Types arbitrary text on the host as real HID keystrokes. */
    fun type(text: String) = sendAction(HidAction.TypeText(text))

    // ------------------------------------------------------------------
    // Gamepad
    // ------------------------------------------------------------------

    private var gamepadState = GamepadSnapshot()

    /** Left/right stick position (-1..1), already normalized by the UI. */
    fun onStick(left: Boolean, rawX: Float, rawY: Float) {
        val gs = gamepadSettings.value
        when (gs.mappingMode) {
            GamepadMappingMode.HID_GAMEPAD -> {
                val g = PointerMath.joystickGain(gs.joystickSensitivity)
                val (x, y) = PointerMath.applyDeadZone(
                    (rawX * g).coerceIn(-1f, 1f),
                    (rawY * g).coerceIn(-1f, 1f),
                    gs.deadZone
                )
                gamepadState = if (left) gamepadState.copy(leftX = x, leftY = y)
                else gamepadState.copy(rightX = x, rightY = y)
                sendAction(HidAction.GamepadUpdate(gamepadState))
            }
            GamepadMappingMode.MOUSE_KEYBOARD -> {
                if (left) {
                    // Left stick drives the mouse pointer.
                    val (x, y) = PointerMath.applyDeadZone(rawX, rawY, gs.deadZone)
                    val speed = 12f * PointerMath.joystickGain(gs.joystickSensitivity)
                    val dx = (x * speed).toInt()
                    val dy = (y * speed).toInt()
                    if (dx != 0 || dy != 0) sendAction(HidAction.MouseMove(dx, dy))
                }
            }
            GamepadMappingMode.KEYBOARD_FALLBACK -> {
                // Stick → WASD-style arrows when pushed past half range.
                if (!left) return
                val (x, y) = PointerMath.applyDeadZone(rawX, rawY, gs.deadZone)
                when {
                    y < -0.5f -> keyTap(HidKeys.ARROW_UP)
                    y > 0.5f -> keyTap(HidKeys.ARROW_DOWN)
                    x < -0.5f -> keyTap(HidKeys.ARROW_LEFT)
                    x > 0.5f -> keyTap(HidKeys.ARROW_RIGHT)
                }
            }
        }
    }

    /** Round 7 — analog trigger 0..1 (pressure-style, HID axes Rx/Ry). */
    fun onTrigger(right: Boolean, pressure: Float) {
        if (gamepadSettings.value.mappingMode != GamepadMappingMode.HID_GAMEPAD) return
        val p = pressure.coerceIn(0f, 1f)
        gamepadState = if (right) gamepadState.copy(rightTrigger = p)
        else gamepadState.copy(leftTrigger = p)
        sendAction(HidAction.GamepadUpdate(gamepadState))
    }

    /** Round 7 — tilt steering (left X) + gyro aiming (right stick). */
    fun onMotion(steer: Float, aimX: Float, aimY: Float) {
        val gs = gamepadSettings.value
        if (gs.mappingMode != GamepadMappingMode.HID_GAMEPAD) return
        if (!gs.gyroAim && !gs.tiltSteer) return
        var s = gamepadState
        if (gs.tiltSteer) {
            val g = 0.5f + gs.tiltSensitivity / 100f
            s = s.copy(leftX = (steer * g).coerceIn(-1f, 1f))
        }
        if (gs.gyroAim) {
            val g = 0.5f + gs.gyroSensitivity / 100f
            s = s.copy(
                rightX = (aimX * g).coerceIn(-1f, 1f),
                rightY = (aimY * g).coerceIn(-1f, 1f)
            )
        }
        gamepadState = s
        sendAction(HidAction.GamepadUpdate(s))
    }

    fun onGamepadButton(button: GamepadButton, pressed: Boolean) {
        when (gamepadSettings.value.mappingMode) {
            GamepadMappingMode.HID_GAMEPAD -> {
                gamepadState = if (pressed) gamepadState.press(button) else gamepadState.release(button)
                sendAction(HidAction.GamepadUpdate(gamepadState))
            }
            else -> {
                // Fallback modes: map buttons to keyboard keys on press only.
                if (pressed) {
                    GamepadKeyboardMapping.DEFAULT[button]?.let { keyTap(it) }
                }
            }
        }
    }

    fun onDpad(direction: DpadDirection) {
        when (gamepadSettings.value.mappingMode) {
            GamepadMappingMode.HID_GAMEPAD -> {
                gamepadState = gamepadState.withDpad(direction)
                sendAction(HidAction.GamepadUpdate(gamepadState))
            }
            else -> when (direction) {
                DpadDirection.UP -> keyTap(HidKeys.ARROW_UP)
                DpadDirection.DOWN -> keyTap(HidKeys.ARROW_DOWN)
                DpadDirection.LEFT -> keyTap(HidKeys.ARROW_LEFT)
                DpadDirection.RIGHT -> keyTap(HidKeys.ARROW_RIGHT)
                else -> Unit
            }
        }
    }

    /** Center sticks + release everything (call when leaving the screen). */
    fun resetGamepad() {
        gamepadState = GamepadSnapshot()
        if (gamepadSettings.value.mappingMode == GamepadMappingMode.HID_GAMEPAD) {
            sendAction(HidAction.GamepadUpdate(gamepadState))
        }
    }
}
