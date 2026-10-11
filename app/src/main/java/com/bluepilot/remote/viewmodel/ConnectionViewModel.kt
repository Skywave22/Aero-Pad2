package com.bluepilot.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluepilot.remote.domain.NearbyScanner
import com.bluepilot.remote.domain.PermissionChecker
import com.bluepilot.remote.domain.usecase.ConnectDeviceUseCase
import com.bluepilot.remote.domain.usecase.DisconnectDeviceUseCase
import com.bluepilot.remote.domain.usecase.GetBondedDevicesUseCase
import com.bluepilot.remote.domain.usecase.ObserveConnectionUseCase
import com.bluepilot.remote.domain.usecase.StartHidEngineUseCase
import com.bluepilot.remote.model.HidConnectionState
import com.bluepilot.remote.model.RemoteDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Drives Permission, Connection, and Devices screens.
 *
 * All dependencies are interfaces/UseCases — unit tests inject fakes.
 */
@HiltViewModel
class ConnectionViewModel @Inject constructor(
    observeConnection: ObserveConnectionUseCase,
    private val startEngine: StartHidEngineUseCase,
    private val connectDevice: ConnectDeviceUseCase,
    private val disconnectDevice: DisconnectDeviceUseCase,
    private val getBondedDevices: GetBondedDevicesUseCase,
    private val permissionChecker: PermissionChecker,
    private val scanner: NearbyScanner,
    // V2 M4 deferred-item — save bonded hosts as quick-switch profiles.
    private val hostStore: com.bluepilot.remote.data.hosts.HostProfiles
) : ViewModel() {

    /** V2 M4 — saved-host addresses (for the ☆/★ toggle on device rows). */
    val savedHostAddresses: StateFlow<Set<String>> =
        hostStore.profiles
            .map { list ->
                list.filter { it.transport == com.bluepilot.remote.data.hosts.HostProfile.TRANSPORT_BT }
                    .map { it.address }.toSet()
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Toggle a bonded device as a saved quick-switch host. */
    fun toggleSavedHost(device: RemoteDevice) {
        val existing = hostStore.profiles.value.firstOrNull {
            it.transport == com.bluepilot.remote.data.hosts.HostProfile.TRANSPORT_BT &&
                it.address == device.address
        }
        if (existing != null) hostStore.remove(existing.id)
        else hostStore.saveBt(label = device.name, mac = device.address)
    }

    companion object {
        private const val SCAN_DURATION_MS = 15_000L
    }

    /** Connection state straight from the engine (sealed class). */
    val connectionState: StateFlow<HidConnectionState> = observeConnection()
        .stateIn(viewModelScope, SharingStarted.Eagerly, HidConnectionState.Idle)

    private val _bondedDevices = MutableStateFlow<List<RemoteDevice>>(emptyList())
    val bondedDevices: StateFlow<List<RemoteDevice>> = _bondedDevices.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<RemoteDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<RemoteDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _permissionsGranted = MutableStateFlow(permissionChecker.hasAllBluetoothPermissions())
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    private var scanJob: Job? = null
    private var scanTimeoutJob: Job? = null
    /** Generation counter: a stale auto-stop can never kill a newer scan. */
    private var scanGeneration = 0

    // ------------------------------------------------------------------

    /** Called by the permission screen after the system dialog returns. */
    fun onPermissionsResult() {
        val granted = permissionChecker.hasAllBluetoothPermissions()
        _permissionsGranted.value = granted
        if (granted) {
            startEngine()
            refreshBondedDevices()
        }
    }

    /** Permissions the UI launcher should request. */
    fun permissionsToRequest(): List<String> = permissionChecker.missingPermissions()

    /** Boot engine + load devices (call when entering the connection flow). */
    fun initialize() {
        if (permissionChecker.hasAllBluetoothPermissions()) {
            _permissionsGranted.value = true
            startEngine()
            refreshBondedDevices()
        } else {
            _permissionsGranted.value = false
        }
    }

    fun refreshBondedDevices() {
        _bondedDevices.value = getBondedDevices()
    }

    fun connect(address: String) {
        connectDevice(address)
    }

    fun disconnect() {
        disconnectDevice()
    }

    /** Runs a time-boxed discovery scan; results accumulate without duplicates. */
    fun startScan() {
        if (_isScanning.value) return
        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        val generation = ++scanGeneration
        scanJob = scanner.scan()
            .onEach { device ->
                val current = _discoveredDevices.value
                if (current.none { it.address == device.address }) {
                    _discoveredDevices.value = current + device
                }
            }
            .catch { Timber.w(it, "scan flow error") }
            .launchIn(viewModelScope)
        // Auto-stop after the scan window. Generation check prevents a stale
        // timer (from a previous scan) cancelling a scan the user restarted.
        scanTimeoutJob = viewModelScope.launch {
            delay(SCAN_DURATION_MS)
            if (generation == scanGeneration) stopScan()
        }
    }

    fun stopScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}
