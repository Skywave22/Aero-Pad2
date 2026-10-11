package com.bluepilot.remote.viewmodel

import com.bluepilot.remote.domain.HidController
import com.bluepilot.remote.domain.NearbyScanner
import com.bluepilot.remote.domain.PermissionChecker
import com.bluepilot.remote.domain.usecase.ConnectDeviceUseCase
import com.bluepilot.remote.domain.usecase.DisconnectDeviceUseCase
import com.bluepilot.remote.domain.usecase.GetBondedDevicesUseCase
import com.bluepilot.remote.domain.usecase.ObserveConnectionUseCase
import com.bluepilot.remote.domain.usecase.StartHidEngineUseCase
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.HidConnectionState
import com.bluepilot.remote.model.RemoteDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ViewModel logic tests using fakes for all domain interfaces —
 * no Android framework, no Bluetooth needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    // ---------------- Fakes ----------------

    private class FakeController : HidController {
        val stateFlow = MutableStateFlow<HidConnectionState>(HidConnectionState.Idle)
        var started = false
        var connectedAddress: String? = null
        var disconnected = false
        var bonded: List<RemoteDevice> = emptyList()

        override val state: StateFlow<HidConnectionState> get() = stateFlow
        override fun start() { started = true }
        override fun stop() {}
        override fun connectTo(address: String) { connectedAddress = address }
        override fun disconnect() { disconnected = true }
        override fun bondedDevices(): List<RemoteDevice> = bonded
        override fun send(action: HidAction) {}
    }

    /** V2 M4 — in-memory host profiles fake (list logic tested separately). */
    private class FakeHostProfiles : com.bluepilot.remote.data.hosts.HostProfiles {
        private val _profiles = MutableStateFlow<List<com.bluepilot.remote.data.hosts.HostProfile>>(emptyList())
        override val profiles: StateFlow<List<com.bluepilot.remote.data.hosts.HostProfile>> = _profiles
        override fun saveBt(label: String, mac: String) {
            _profiles.value = com.bluepilot.remote.data.hosts.HostProfileCodec.upsert(
                _profiles.value,
                com.bluepilot.remote.data.hosts.HostProfile(
                    id = mac, label = label,
                    transport = com.bluepilot.remote.data.hosts.HostProfile.TRANSPORT_BT,
                    address = mac
                )
            )
        }
        override fun saveWifi(label: String, host: String, port: Int, pin: String?) {}
        override fun pinFor(profile: com.bluepilot.remote.data.hosts.HostProfile) = ""
        override fun touch(id: String) {}
        override fun remove(id: String) {
            _profiles.value = com.bluepilot.remote.data.hosts.HostProfileCodec.remove(_profiles.value, id)
        }
    }

    private class FakePermissions(var granted: Boolean) : PermissionChecker {
        override fun requiredBluetoothPermissions() = listOf("BT_CONNECT")
        override fun optionalPermissions() = emptyList<String>()
        override fun hasAllBluetoothPermissions() = granted
        override fun missingPermissions() = if (granted) emptyList() else listOf("BT_CONNECT")
    }

    private class FakeScanner(private val devices: List<RemoteDevice> = emptyList()) : NearbyScanner {
        override fun scan(): Flow<RemoteDevice> = flowOf(*devices.toTypedArray())
    }

    // ---------------- Setup ----------------

    private lateinit var controller: FakeController
    private lateinit var permissions: FakePermissions

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        controller = FakeController()
        permissions = FakePermissions(granted = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(scanner: NearbyScanner = FakeScanner()) = ConnectionViewModel(
        observeConnection = ObserveConnectionUseCase(controller),
        startEngine = StartHidEngineUseCase(controller),
        connectDevice = ConnectDeviceUseCase(controller),
        disconnectDevice = DisconnectDeviceUseCase(controller),
        getBondedDevices = GetBondedDevicesUseCase(controller),
        permissionChecker = permissions,
        scanner = scanner,
        hostStore = FakeHostProfiles()
    )

    // ---------------- Tests ----------------

    @Test
    fun `initialize with permissions starts engine and loads devices`() = runTest(dispatcher) {
        controller.bonded = listOf(RemoteDevice("AA:BB:CC:DD:EE:FF", "My PC", isPaired = true))
        val vm = buildViewModel()
        vm.initialize()
        assertTrue(controller.started)
        assertEquals(1, vm.bondedDevices.value.size)
        assertTrue(vm.permissionsGranted.value)
    }

    @Test
    fun `initialize without permissions does not start engine`() = runTest(dispatcher) {
        permissions.granted = false
        val vm = buildViewModel()
        vm.initialize()
        assertFalse(controller.started)
        assertFalse(vm.permissionsGranted.value)
    }

    @Test
    fun `connect passes valid mac to controller`() = runTest(dispatcher) {
        val vm = buildViewModel()
        vm.connect("AA:BB:CC:DD:EE:FF")
        assertEquals("AA:BB:CC:DD:EE:FF", controller.connectedAddress)
    }

    @Test
    fun `connect rejects malformed mac`() = runTest(dispatcher) {
        val vm = buildViewModel()
        vm.connect("not-a-mac")
        vm.connect("AA:BB:CC:DD:EE")      // too short
        vm.connect("GG:HH:II:JJ:KK:LL")   // invalid hex
        assertEquals(null, controller.connectedAddress)
    }

    @Test
    fun `disconnect delegates to controller`() = runTest(dispatcher) {
        val vm = buildViewModel()
        vm.disconnect()
        assertTrue(controller.disconnected)
    }

    @Test
    fun `connection state mirrors engine state`() = runTest(dispatcher) {
        val vm = buildViewModel()
        val device = RemoteDevice("AA:BB:CC:DD:EE:FF", "My PC")
        controller.stateFlow.value = HidConnectionState.Connected(device)
        advanceTimeBy(1)
        assertEquals(HidConnectionState.Connected(device), vm.connectionState.value)
        assertTrue(vm.connectionState.value.isConnected)
    }

    @Test
    fun `scan collects devices without duplicates and stops`() = runTest(dispatcher) {
        val d1 = RemoteDevice("11:22:33:44:55:66", "TV")
        val scanner = FakeScanner(listOf(d1, d1, d1))
        val vm = buildViewModel(scanner)
        vm.startScan()
        advanceTimeBy(100)
        assertEquals(1, vm.discoveredDevices.value.size)
        vm.stopScan()
        assertFalse(vm.isScanning.value)
    }

    @Test
    fun `onPermissionsResult granted boots engine`() = runTest(dispatcher) {
        permissions.granted = false
        val vm = buildViewModel()
        vm.initialize()
        assertFalse(controller.started)
        permissions.granted = true
        vm.onPermissionsResult()
        assertTrue(controller.started)
        assertTrue(vm.permissionsGranted.value)
    }
}
