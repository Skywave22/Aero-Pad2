package com.aeropad.remote.features

import com.aeropad.remote.domain.SettingsStore
import com.aeropad.remote.model.AppSettings
import com.aeropad.remote.model.GamepadSettings
import com.aeropad.remote.model.KeyboardSettings
import com.aeropad.remote.model.MouseSettings
import com.aeropad.remote.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
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
 * Round 16 — settings writes used to be whole-object overwrites built from a
 * StateFlow snapshot, so two changes made before the first write landed
 * silently reverted each other. These tests pin the read-modify-write
 * behaviour that replaced it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsMutateTest {

    private val dispatcher = StandardTestDispatcher()

    /** No-op haptics for JVM tests. */
    private val fakeHaptics = object : com.aeropad.remote.haptics.Haptics {
        override var intensityScale: Float = 0.6f
        override fun play(pattern: com.aeropad.remote.model.gamepad.HapticPattern) {}
    }

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    /** In-memory store that applies the transform to its CURRENT value. */
    private class FakeStore(initial: AppSettings = AppSettings()) : SettingsStore {
        val app = MutableStateFlow(initial)
        var wholeObjectWrites = 0
            private set

        override val appSettings: Flow<AppSettings> = app
        override val mouseSettings: Flow<MouseSettings> = MutableStateFlow(MouseSettings())
        override val keyboardSettings: Flow<KeyboardSettings> = MutableStateFlow(KeyboardSettings())
        override val gamepadSettings: Flow<GamepadSettings> = MutableStateFlow(GamepadSettings())

        override suspend fun updateApp(settings: AppSettings) {
            wholeObjectWrites++
            app.value = settings
        }

        /** Mirrors DataStoreSettings: transform the CURRENT value. */
        override suspend fun mutateApp(transform: (AppSettings) -> AppSettings) {
            app.value = transform(app.value)
        }

        override suspend fun updateMouse(settings: MouseSettings) = Unit
        override suspend fun updateKeyboard(settings: KeyboardSettings) = Unit
        override suspend fun updateGamepad(settings: GamepadSettings) = Unit
    }

    @Test
    fun `two quick toggles both survive`() = runTest(dispatcher) {
        val store = FakeStore()
        val vm = SettingsViewModel(store, fakeHaptics)

        // Both fired before either write had been observed on the flow: with
        // the old snapshot-based update, the second would revert the first.
        vm.setFpsOverlay(true)
        vm.setSecureScreen(true)
        advanceUntilIdle()

        assertTrue("FPS overlay toggled off again", store.app.value.fpsOverlay)
        assertTrue("secure screen toggled off again", store.app.value.secureScreen)
    }

    @Test
    fun `mutate does not go through the whole-object write`() = runTest(dispatcher) {
        val store = FakeStore()
        val vm = SettingsViewModel(store, fakeHaptics)
        vm.setReduceMotion(true)
        advanceUntilIdle()
        assertEquals(
            "the ViewModel must mutate, not overwrite",
            0,
            store.wholeObjectWrites
        )
        assertTrue(store.app.value.reduceMotion)
    }

    @Test
    fun `a field set elsewhere is preserved`() = runTest(dispatcher) {
        val store = FakeStore(AppSettings(automationApi = true, automationToken = "abc"))
        val vm = SettingsViewModel(store, fakeHaptics)
        vm.setKeepScreenOn(false)
        advanceUntilIdle()
        assertTrue(store.app.value.automationApi)
        assertEquals("abc", store.app.value.automationToken)
        assertTrue(!store.app.value.keepScreenOn)
    }
}
