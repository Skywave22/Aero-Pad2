package com.aeropad.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeropad.remote.domain.SettingsStore
import com.aeropad.remote.domain.usecase.ObserveConnectionUseCase
import com.aeropad.remote.domain.usecase.SendHidActionUseCase
import com.aeropad.remote.hid.CharToHidMapper
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
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared driver for all control screens (mouse/keyboard/numpad/media/
 * presenter/gamepad). Applies user settings to raw touch input, then emits
 * HidActions through the UseCase layer.
 */
@HiltViewModel
class RemoteControlViewModel @Inject constructor(
    private val keyboardModifiers: com.aeropad.remote.domain.KeyboardModifierState,
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

    // CRASH FIX (v3.6.0): the safety `init` block used to live HERE, above the
    // `_dragLock` / `_scrollMode` / `lastFallbackStickKey` declarations below.
    //
    // Kotlin runs property initializers and `init` blocks strictly in source
    // order, and `viewModelScope` uses Dispatchers.Main.immediate, so the
    // `launch { }` executed INLINE during construction. `isConnected` is a
    // StateFlow whose initial value is `false`, so `collect` emitted
    // synchronously and the very first line dereferenced `_dragLock` while it
    // was still null -> NullPointerException in the ViewModel constructor.
    // Every screen backed by this ViewModel (Mouse, Keyboard, Numpad, Media,
    // Presenter, Gamepad) therefore hard-crashed the moment it was opened.
    //
    // The block now lives at the END of the class (`resetInputStateWhenDisconnected`)
    // so all state it touches is already initialised.

    val mouseSettings: StateFlow<MouseSettings> = settingsStore.mouseSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, MouseSettings())

    val keyboardSettings: StateFlow<KeyboardSettings> = settingsStore.keyboardSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, KeyboardSettings())

    val gamepadSettings: StateFlow<GamepadSettings> = settingsStore.gamepadSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, GamepadSettings())

    val vibrationsEnabled: StateFlow<Boolean> = settingsStore.appSettings
        .map { it.touchVibrations }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // Round 9 — is the extended descriptor (touch digitizer) registered?
    val extendedReports: StateFlow<Boolean> = settingsStore.appSettings
        .map { it.extendedReports }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // M6 + L3 — user-visible feedback for skipped characters or disconnected taps.
    private val _uiNotice = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val uiNotice: kotlinx.coroutines.flow.StateFlow<String?> = _uiNotice
    private var lastDisconnectedWarnAtMs = 0L

    fun consumeNotice() {
        _uiNotice.value = null
    }

    private fun dispatch(action: HidAction, isDiscreteTap: Boolean = false) {
        if (!isConnected.value && isDiscreteTap) {
            val now = System.currentTimeMillis()
            if (now - lastDisconnectedWarnAtMs > 2_500L) {
                lastDisconnectedWarnAtMs = now
                _uiNotice.value = "Not connected — pair a host on the Connect screen first."
            }
        }
        sendAction(action)
    }

    // ------------------------------------------------------------------
    // Mouse
    // ------------------------------------------------------------------

    // OPTIMIZATION: shared TrackpadEngine (was ~30 duplicated lines).
    private val trackpad = com.aeropad.remote.domain.TrackpadEngine({ mouseSettings.value })

    /** Raw trackpad drag delta in px → settings-adjusted HID mouse move (or wheel in scroll mode). */
    fun onTrackpadDelta(dxPx: Float, dyPx: Float) {
        if (_scrollMode.value) {
            onScrollDelta(dyPx)
            return
        }
        // AEROPAD v1.0 #19 — Precision Mode: slow-motion cursor (x0.35).
        val scale = if (_precisionMode.value) 0.35f else 1f
        val (ix, iy) = trackpad.move(dxPx * scale, dyPx * scale)
        if (ix != 0 || iy != 0) sendAction(HidAction.MouseMove(ix, iy))
    }

    // AEROPAD v1.0 #19 — precision mode toggle (session state).
    private val _precisionMode = kotlinx.coroutines.flow.MutableStateFlow(false)
    val precisionMode: kotlinx.coroutines.flow.StateFlow<Boolean> = _precisionMode
    fun setPrecisionMode(on: Boolean) { _precisionMode.value = on }

    // A2 — scroll mode modifier: turns single-finger trackpad movement into wheel scroll
    // (works even while Drag Lock or a mouse button is held).
    private val _scrollMode = kotlinx.coroutines.flow.MutableStateFlow(false)
    val scrollMode: kotlinx.coroutines.flow.StateFlow<Boolean> = _scrollMode
    fun setScrollMode(on: Boolean) { _scrollMode.value = on }

    // AEROPAD v1.0 #22 — click-and-drag lock: LEFT held until unlocked.
    private val _dragLock = kotlinx.coroutines.flow.MutableStateFlow(false)
    val dragLock: kotlinx.coroutines.flow.StateFlow<Boolean> = _dragLock
    fun toggleDragLock() {
        if (_dragLock.value) {
            _dragLock.value = false
            dispatch(HidAction.MouseUp(MouseButton.LEFT), isDiscreteTap = true)
        } else {
            _dragLock.value = true
            dispatch(HidAction.MouseDown(MouseButton.LEFT), isDiscreteTap = true)
        }
    }

    /** Reset motion state when a new gesture starts (prevents smoothing bleed). */
    fun onTrackpadGestureStart() = trackpad.startGesture()

    /** Tap on trackpad → left click (honors tap-to-click setting). */
    /** Drag ended — flush the coalesced tail so the cursor lands exactly. */
    fun onTrackpadGestureEnd() {
        val (ix, iy) = trackpad.endGesture()
        if (ix != 0 || iy != 0) sendAction(HidAction.MouseMove(ix, iy))
    }

    fun onTrackpadTap() {
        if (!mouseSettings.value.tapToClick) return
        dispatch(HidAction.MouseClick(primaryButton), isDiscreteTap = true)
    }

    fun onTrackpadDoubleTap() {
        if (!mouseSettings.value.tapToClick) return
        dispatch(HidAction.MouseDoubleClick(primaryButton), isDiscreteTap = true)
    }

    /** Long-press on trackpad → secondary click. */
    fun onTrackpadLongPress() = dispatch(HidAction.MouseClick(secondaryButton), isDiscreteTap = true)

    /**
     * Two-finger tap = secondary click, matching every laptop trackpad. This
     * is the gesture users reach for first and it was missing.
     */
    fun onTrackpadTwoFingerTap() = dispatch(HidAction.MouseClick(secondaryButton), isDiscreteTap = true)

    /** Left-handed mode mirrors the two buttons everywhere. */
    private val primaryButton: MouseButton
        get() = if (mouseSettings.value.leftHanded) MouseButton.RIGHT else MouseButton.LEFT

    private val secondaryButton: MouseButton
        get() = if (mouseSettings.value.leftHanded) MouseButton.LEFT else MouseButton.RIGHT

    fun clickButton(button: MouseButton) = dispatch(HidAction.MouseClick(button), isDiscreteTap = true)
    fun buttonDown(button: MouseButton) = dispatch(HidAction.MouseDown(button), isDiscreteTap = true)
    /** M7 + D3 fix: release the specific button that was held, not hard-coded LEFT. */
    fun buttonUp(button: MouseButton = MouseButton.LEFT) = sendAction(HidAction.MouseUp(button))

    /** Scroll strip drag: accumulate px, emit whole wheel steps. */
    fun onScrollDelta(dyPx: Float) {
        trackpad.scroll(dyPx).takeIf { it != 0 }?.let { sendAction(HidAction.MouseScroll(it)) }
    }

    // ------------------------------------------------------------------
    // Keyboard / text
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // F3 — sticky (latched) modifiers
    //
    // Tap Shift once and the NEXT key is shifted; double-tap to lock it down.
    // This is what makes Ctrl+Shift+Esc or Alt+Tab possible one-handed.
    // Stored as a snapshot StateFlow so the keyboard screens can highlight the
    // latched/locked keys.
    // ------------------------------------------------------------------

    /**
     * Round 15 — the latch now lives in a Hilt singleton so the Full Keyboard
     * (a different ViewModel) and this screen share one modifier state. See
     * [com.aeropad.remote.domain.KeyboardModifierState].
     */
    val stickyState: kotlinx.coroutines.flow.StateFlow<
        com.aeropad.remote.domain.KeyboardModifierState.Ui
        > = keyboardModifiers.state

    /** Tap a modifier to latch it; double-tap to lock; tap a locked one to clear. */
    fun toggleStickyModifier(mod: Byte) = keyboardModifiers.toggle(mod)

    /** Release every latched/locked modifier. */
    fun clearStickyModifiers() = keyboardModifiers.clear()

    /**
     * A key tap, with any latched modifiers folded in.
     * The one-shot latch is consumed by this keystroke; locked ones persist.
     */
    fun keyTap(key: Byte, modifiers: Byte = 0) {
        val latch = keyboardModifiers.mask
        if (key == HidKeys.NONE && modifiers.toInt() == 0 && latch != 0) {
            // A modifier-only press (e.g. tapping Win on its own): send the
            // latched mask with no key so the host sees the modifier down.
            dispatch(
                HidAction.KeyTap(HidKeys.NONE, latch.toByte()),
                isDiscreteTap = true
            )
            return
        }
        val combined = ((modifiers.toInt() or latch) and 0xFF).toByte()
        dispatch(HidAction.KeyTap(key, combined), isDiscreteTap = true)
        if (latch != 0) keyboardModifiers.consume()
    }

    fun typeText(text: String) {
        if (text.isNotEmpty()) {
            // Typed text carries its own case/shift state, so applying a
            // latched modifier here would be wrong — but the latch has still
            // been "used up" by the user typing.
            if (keyboardModifiers.mask != 0) keyboardModifiers.consume()
            val skipped = CharToHidMapper.countUnmappable(text)
            if (skipped > 0) {
                _uiNotice.value = "$skipped character(s) could not be sent (unsupported by standard HID layout)."
            }
            dispatch(HidAction.TypeText(text), isDiscreteTap = true)
            // AEROPAD v1.0 #12 — clipboard history: last 10 sent texts,
            // newest first, deduped (session-scoped, privacy-friendly).
            _sentHistory.value =
                (listOf(text) + _sentHistory.value.filterNot { it == text }).take(10)
        }
    }

    /**
     * Round 15 — live IME typing. Same HID path as [typeText] but it does not
     * touch sent-history, because a character-per-keystroke relay would fill
     * the history with single letters.
     */
    fun typeLive(text: String) {
        if (text.isEmpty()) return
        dispatch(HidAction.TypeText(text), isDiscreteTap = true)
    }

    // AEROPAD v1.0 #12 — history of texts sent to the host (tap to resend).
    private val _sentHistory = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    val sentHistory: kotlinx.coroutines.flow.StateFlow<List<String>> = _sentHistory

    // ------------------------------------------------------------------
    // Media / system
    // ------------------------------------------------------------------

    fun mediaTap(usage: Int) = dispatch(HidAction.MediaTap(usage), isDiscreteTap = true)

    /** System page tap (power / sleep / wake bits). */
    fun systemTap(bits: Byte) = dispatch(HidAction.SystemTap(bits), isDiscreteTap = true)

    /**
     * Touch-screen control: streams an absolute HID digitizer contact to the
     * host. [x]/[y] are normalized 0..1 across the host screen.
     */
    fun touch(x: Float, y: Float, contact: Boolean) =
        sendAction(HidAction.TouchUpdate(contact, x, y))

    /** Types arbitrary text on the host as real HID keystrokes. */
    fun type(text: String) = typeText(text)

    // ------------------------------------------------------------------
    // Gamepad
    // ------------------------------------------------------------------

    private var gamepadState = GamepadSnapshot()
    // H1 fix — edge-trigger state for KEYBOARD_FALLBACK stick direction.
    private var lastFallbackStickKey: Byte? = null

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
                // H1 fix (v3.6.0) — hold the arrow key instead of tapping it.
                //
                // v3.4.0 flooded the PC with ~100 key taps per second (one per
                // stick event). v3.5.0 over-corrected: it emitted ONE tap per
                // direction change, so holding the stick moved the character a
                // single step and then stopped.
                //
                // Correct behaviour: key DOWN when the direction becomes active,
                // key RELEASE when it changes or the stick recentres — i.e. real
                // press-and-hold, which is what a D-pad does.
                if (!left) return
                val (x, y) = PointerMath.applyDeadZone(rawX, rawY, gs.deadZone)
                val nextKey = fallbackStickKeyFor(x, y)
                if (nextKey != lastFallbackStickKey) {
                    if (lastFallbackStickKey != null) {
                        sendAction(HidAction.KeyRelease)
                    }
                    lastFallbackStickKey = nextKey
                    if (nextKey != null) {
                        sendAction(HidAction.KeyDown(nextKey))
                    }
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
        // H1 — never leave a fallback arrow key stuck down.
        if (lastFallbackStickKey != null) {
            sendAction(HidAction.KeyRelease)
            lastFallbackStickKey = null
        }
        if (gamepadSettings.value.mappingMode == GamepadMappingMode.HID_GAMEPAD) {
            sendAction(HidAction.GamepadUpdate(gamepadState))
        }
    }

    /**
     * CRASH FIX (v3.6.0) — safety reset, declared LAST on purpose.
     *
     * Runs as an `init` block, i.e. in source order during construction, so it
     * MUST appear after every field it touches (`_dragLock`, `_scrollMode`,
     * `lastFallbackStickKey`). Putting it above them made the constructor throw
     * a NullPointerException and crashed every control screen on open.
     *
     * `viewModelScope` is Dispatchers.Main.immediate, so this body starts
     * inline; by now all state exists, so reading it is safe.
     */
    init {
        viewModelScope.launch {
            isConnected.collect { connected ->
                if (!connected) {
                    _dragLock.value = false
                    _scrollMode.value = false
                    lastFallbackStickKey = null
                    // F3 — never leave a modifier latched across a reconnect.
                    keyboardModifiers.clear()
                }
            }
        }
    }

    companion object {
        /** H1 — pure direction classifier for KEYBOARD_FALLBACK stick mode (unit-tested). */
        fun fallbackStickKeyFor(x: Float, y: Float): Byte? = when {
            y < -0.5f -> HidKeys.ARROW_UP
            y > 0.5f -> HidKeys.ARROW_DOWN
            x < -0.5f -> HidKeys.ARROW_LEFT
            x > 0.5f -> HidKeys.ARROW_RIGHT
            else -> null
        }
    }
}
