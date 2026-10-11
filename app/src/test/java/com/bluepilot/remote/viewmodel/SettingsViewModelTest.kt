package com.bluepilot.remote.viewmodel

import com.bluepilot.remote.domain.SettingsStore
import com.bluepilot.remote.model.AppSettings
import com.bluepilot.remote.model.GamepadMappingMode
import com.bluepilot.remote.model.GamepadSettings
import com.bluepilot.remote.model.KeyboardSettings
import com.bluepilot.remote.model.MouseSettings
import com.bluepilot.remote.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * SettingsViewModel logic with an in-memory store fake.
 * Verifies updates flow through AND that the fake mimics the real store's
 * sanitize-on-write contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** In-memory SettingsStore honoring the sanitize contract. */
    private class FakeStore : SettingsStore {
        val app = MutableStateFlow(AppSettings())
        val mouse = MutableStateFlow(MouseSettings())
        val keyboard = MutableStateFlow(KeyboardSettings())
        val gamepad = MutableStateFlow(GamepadSettings())

        override val appSettings: Flow<AppSettings> = app
        override val mouseSettings: Flow<MouseSettings> = mouse
        override val keyboardSettings: Flow<KeyboardSettings> = keyboard
        override val gamepadSettings: Flow<GamepadSettings> = gamepad

        override suspend fun updateApp(settings: AppSettings) { app.value = settings }
        override suspend fun updateMouse(settings: MouseSettings) { mouse.value = settings.sanitized() }
        override suspend fun updateKeyboard(settings: KeyboardSettings) { keyboard.value = settings }
        override suspend fun updateGamepad(settings: GamepadSettings) { gamepad.value = settings.sanitized() }
    }

    private lateinit var store: FakeStore

    /** No-op haptics for JVM tests. */
    private val fakeHaptics = object : com.bluepilot.remote.haptics.Haptics {
        override var intensityScale: Float = 0.6f
        override fun play(pattern: com.bluepilot.remote.model.gamepad.HapticPattern) {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = FakeStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `theme change persists`() = runTest(dispatcher) {
        val vm = SettingsViewModel(store, fakeHaptics)
        advanceUntilIdle()
        vm.setTheme(ThemeMode.DARK)
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, store.app.value.theme)
        assertEquals(ThemeMode.DARK, vm.app.value.theme)
    }

    @Test
    fun `secure screen toggle persists`() = runTest(dispatcher) {
        val vm = SettingsViewModel(store, fakeHaptics)
        advanceUntilIdle()
        vm.setSecureScreen(true)
        advanceUntilIdle()
        assertTrue(store.app.value.secureScreen)
    }

    @Test
    fun `mouse sensitivity out of range is sanitized on write`() = runTest(dispatcher) {
        val vm = SettingsViewModel(store, fakeHaptics)
        advanceUntilIdle()
        vm.setMouseSensitivity(400)   // slider bug / bad input
        advanceUntilIdle()
        assertEquals(100, store.mouse.value.sensitivity)
    }

    @Test
    fun `gamepad mode change persists`() = runTest(dispatcher) {
        val vm = SettingsViewModel(store, fakeHaptics)
        advanceUntilIdle()
        vm.setGamepadMode(GamepadMappingMode.MOUSE_KEYBOARD)
        advanceUntilIdle()
        assertEquals(GamepadMappingMode.MOUSE_KEYBOARD, store.gamepad.value.mappingMode)
    }

    @Test
    fun `dead zone above cap is clamped`() = runTest(dispatcher) {
        val vm = SettingsViewModel(store, fakeHaptics)
        advanceUntilIdle()
        vm.setDeadZone(99)
        advanceUntilIdle()
        assertEquals(GamepadSettings.DEAD_ZONE_MAX, store.gamepad.value.deadZone)
    }

    @Test
    fun `keyboard text bar toggle persists`() = runTest(dispatcher) {
        val vm = SettingsViewModel(store, fakeHaptics)
        advanceUntilIdle()
        vm.setShowTextInputBar(false)
        advanceUntilIdle()
        assertEquals(false, store.keyboard.value.showTextInputBar)
    }
}
