package com.aeropad.remote.viewmodel

import android.content.Context
import com.aeropad.remote.data.gamepad.GamepadProfileStore
import com.aeropad.remote.domain.HidController
import com.aeropad.remote.domain.MacroRecorder
import com.aeropad.remote.domain.MotionSource
import com.aeropad.remote.domain.SettingsStore
import com.aeropad.remote.domain.usecase.ObserveConnectionUseCase
import com.aeropad.remote.domain.usecase.SendHidActionUseCase
import com.aeropad.remote.model.AppSettings
import com.aeropad.remote.model.GamepadSettings
import com.aeropad.remote.model.HidAction
import com.aeropad.remote.model.HidConnectionState
import com.aeropad.remote.model.KeyboardSettings
import com.aeropad.remote.model.MouseButton
import com.aeropad.remote.model.MouseSettings
import com.aeropad.remote.model.RemoteDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

/**
 * Regression tests for the v3.5.0 constructor crash.
 *
 * The `init { }` safety-reset block used to be declared ABOVE the
 * `_dragLock` / `_scrollMode` / `lastFallbackStickKey` fields. Because Kotlin
 * runs initializers in source order and `viewModelScope` is
 * Dispatchers.Main.immediate, the launch body ran inline during construction,
 * the `isConnected` StateFlow emitted its initial `false` synchronously, and
 * the first line touched a still-null `_dragLock` -> NullPointerException.
 *
 * On a device this surfaced as "app instantly crashes when I open Mouse or
 * Keyboard", because those screens construct this ViewModel.
 *
 * The test uses UnconfinedTestDispatcher for Main so `launch` executes
 * eagerly exactly like Main.immediate does on a real device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteControlViewModelConstructionTest {

    private class FakeStore : SettingsStore {
        val gamepad = MutableStateFlow(GamepadSettings())
        override val appSettings: Flow<AppSettings> = MutableStateFlow(AppSettings())
        override val mouseSettings: Flow<MouseSettings> = MutableStateFlow(MouseSettings())
        override val keyboardSettings: Flow<KeyboardSettings> = MutableStateFlow(KeyboardSettings())
        override val gamepadSettings: Flow<GamepadSettings> = gamepad
        override suspend fun updateApp(settings: AppSettings) = Unit
        override suspend fun updateMouse(settings: MouseSettings) = Unit
        override suspend fun updateKeyboard(settings: KeyboardSettings) = Unit
        override suspend fun updateGamepad(settings: GamepadSettings) = Unit
    }

    /** Minimal HidController: starts disconnected, records everything sent. */
    private class FakeController : HidController {
        val sent = mutableListOf<HidAction>()
        private val _state = MutableStateFlow<HidConnectionState>(HidConnectionState.Idle)
        override val state: StateFlow<HidConnectionState> = _state
        override fun start() = Unit
        override fun stop() = Unit
        override fun connectTo(address: String) = Unit
        override fun disconnect() = Unit
        override fun bondedDevices(): List<RemoteDevice> = emptyList()
        override fun send(action: HidAction) { sent += action }
    }

    private val FALLBACK = com.aeropad.remote.model.GamepadMappingMode.KEYBOARD_FALLBACK

    private val dispatcher = UnconfinedTestDispatcher()

    /**
     * Coroutine failures inside `launch` never reach the test body by default
     * (they go to the uncaught-exception handler), so collect them here. On a
     * device that same path is a FATAL EXCEPTION on the main thread, which is
     * exactly the "app crashes on open" symptom being regression-tested.
     */
    private val uncaught = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        // Main.immediate semantics: coroutines started here run eagerly.
        Dispatchers.setMain(dispatcher)
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        Dispatchers.resetMain()
    }

    private fun assertNoCrash() {
        if (uncaught.isNotEmpty()) {
            throw AssertionError(
                "ViewModel construction crashed: " + uncaught.joinToString(" | ") { it.toString() },
                uncaught.first()
            )
        }
    }

    private lateinit var store: FakeStore

    private fun newViewModel(
        controller: FakeController = FakeController(),
        gamepadMode: com.aeropad.remote.model.GamepadMappingMode =
            com.aeropad.remote.model.GamepadMappingMode.HID_GAMEPAD
    ): RemoteControlViewModel {
        val context = Mockito.mock(Context::class.java)
        store = FakeStore()
        store.gamepad.value = GamepadSettings(mappingMode = gamepadMode)
        return RemoteControlViewModel(
            keyboardModifiers = com.aeropad.remote.domain.KeyboardModifierState(),
            observeConnection = ObserveConnectionUseCase(controller),
            sendAction = SendHidActionUseCase(controller, MacroRecorder()),
            settingsStore = store,
            motionSource = MotionSource(context),
            gamepadProfiles = GamepadProfileStore(context)
        )
    }

    /**
     * THE regression test. Constructing the ViewModel must not throw, even
     * though the disconnect observer fires synchronously during construction.
     */
    @Test
    fun `constructing the view model does not throw`() {
        val vm = newViewModel()
        assertNoCrash()
        assertFalse(vm.isConnected.value)
    }

    /** The safety reset must actually have run: no stale drag/scroll state. */
    @Test
    fun `starts with drag lock and scroll mode off`() {
        val vm = newViewModel()
        assertNoCrash()
        assertFalse(vm.dragLock.value)
        assertFalse(vm.scrollMode.value)
    }

    /**
     * H1 — KEYBOARD_FALLBACK must HOLD the arrow, not tap it.
     * v3.4.0 sent ~100 taps/sec; v3.5.0 sent one tap and then nothing.
     */
    @Test
    fun `fallback stick holds the arrow while pushed and releases on recentre`() {
        val controller = FakeController()
        val vm = newViewModel(controller, FALLBACK)
        assertNoCrash()
        // Many events in the same direction (stick held up) -> exactly one KeyDown.
        repeat(30) { vm.onStick(left = true, rawX = 0f, rawY = -1f) }
        val downs = controller.sent.filterIsInstance<HidAction.KeyDown>()
        assertEquals("expected exactly one key-down while held", 1, downs.size)
        assertEquals(com.aeropad.remote.model.HidKeys.ARROW_UP, downs.first().key)
        assertTrue(controller.sent.none { it is HidAction.KeyRelease })

        // Recentre -> single release, no extra down.
        vm.onStick(left = true, rawX = 0f, rawY = 0f)
        assertEquals(1, controller.sent.count { it is HidAction.KeyRelease })
        assertEquals(1, controller.sent.count { it is HidAction.KeyDown })

        // Push up again, then slide straight to the right in one motion:
        // the old key must be released before the new one goes down.
        vm.onStick(left = true, rawX = 0f, rawY = -1f)
        assertEquals(2, controller.sent.count { it is HidAction.KeyDown })
        vm.onStick(left = true, rawX = 1f, rawY = 0f)
        assertEquals(2, controller.sent.count { it is HidAction.KeyRelease })
        assertEquals(3, controller.sent.count { it is HidAction.KeyDown })
        assertEquals(
            com.aeropad.remote.model.HidKeys.ARROW_RIGHT,
            controller.sent.filterIsInstance<HidAction.KeyDown>().last().key
        )
    }

    /** H1 — leaving the gamepad must never leave an arrow stuck down. */
    @Test
    fun `resetGamepad releases a held fallback key`() {
        val controller = FakeController()
        val vm = newViewModel(controller, FALLBACK)
        vm.onStick(left = true, rawX = 0f, rawY = -1f)
        vm.resetGamepad()
        assertTrue(controller.sent.any { it is HidAction.KeyRelease })
        // Nothing is held afterwards: another reset emits no extra release.
        val before = controller.sent.size
        vm.resetGamepad()
        assertEquals(before, controller.sent.size)
    }

    /** Toggling drag lock sends a real button-down that a mouse move preserves (C1). */
    @Test
    fun `drag lock toggles down and up`() {
        val controller = FakeController()
        val vm = newViewModel(controller)
        assertNoCrash()
        vm.toggleDragLock()
        assertTrue(vm.dragLock.value)
        assertEquals(
            listOf<HidAction>(HidAction.MouseDown(MouseButton.LEFT)),
            controller.sent
        )
        vm.toggleDragLock()
        assertFalse(vm.dragLock.value)
        assertEquals(HidAction.MouseUp(MouseButton.LEFT), controller.sent.last())
    }
}
