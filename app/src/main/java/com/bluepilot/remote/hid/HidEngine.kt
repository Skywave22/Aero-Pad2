package com.bluepilot.remote.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.bluepilot.remote.model.GamepadSnapshot
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.HidConnectionState
import com.bluepilot.remote.model.MouseButton
import com.bluepilot.remote.model.RemoteDevice
import com.bluepilot.remote.permission.PermissionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The heart of BluePilot: registers this phone as a Bluetooth HID Device
 * (keyboard + mouse + consumer + system + gamepad in one descriptor) and
 * sends input reports to the connected host.
 *
 * Crash-safety rules enforced here:
 *  - EVERY framework call is wrapped (SecurityException, IllegalState, remote errors).
 *  - Disconnects NEVER throw into UI; they only move the state machine.
 *  - Reports are serialized on a single-thread dispatcher (low latency, ordered).
 *  - Auto-reconnect with capped exponential backoff after unexpected drops.
 *
 * State is exposed as StateFlow<HidConnectionState> (sealed class) — the
 * only thing UI layers ever observe.
 */
@Singleton
@SuppressLint("MissingPermission") // guarded manually via PermissionManager before every call
class HidEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionManager: PermissionManager,
    // AEROPAD v1.0 #41 — real session history log.
    private val history: com.bluepilot.remote.data.history.ConnectionHistoryStore
) : com.bluepilot.remote.domain.HidController {

    private companion object {
        const val SDP_NAME = "AeroPad"
        const val SDP_DESCRIPTION = "Keyboard, mouse, media and gamepad remote"
        const val SDP_PROVIDER = "AeroPad"
        /** HID subclass: 0xC0 = combo keyboard+pointing device. */
        const val SUBCLASS_COMBO: Byte = 0xC0.toByte()
        const val KEY_TAP_DELAY_MS = 8L
        const val RECONNECT_MAX_ATTEMPTS = 4
        const val RECONNECT_BASE_DELAY_MS = 1000L
    }

    // Single-thread executor keeps report order deterministic and latency low.
    // V2 PART A — RT-input thread: raised to URGENT_AUDIO (highest priority
    // Android grants unrooted apps; true SCHED_FIFO needs root — flagged).
    private val hidExecutor = Executors.newSingleThreadExecutor { r ->
        Thread({
            runCatching {
                android.os.Process.setThreadPriority(
                    android.os.Process.THREAD_PRIORITY_URGENT_AUDIO
                )
            }
            r.run()
        }, "BluePilot-HID").apply { priority = Thread.MAX_PRIORITY }
    }
    private val hidDispatcher = hidExecutor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + hidDispatcher)

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private var pendingConnect: BluetoothDevice? = null
    private var appRegistered = false
    private var proxyRequested = false
    private var userInitiatedDisconnect = false
    private var reconnectAttempts = 0
    private var lastHostDevice: BluetoothDevice? = null

    private val _state = MutableStateFlow<HidConnectionState>(HidConnectionState.Idle)
    override val state: StateFlow<HidConnectionState> = _state.asStateFlow()

    /** ADV S5 — real measured connection-health metrics (public read). */
    val health = com.bluepilot.remote.domain.ConnectionHealthTracker()

    /** ADV S5 — reconnect status for the dashboard (attempt#, max). */
    private val _reconnectStatus = MutableStateFlow<Pair<Int, Int>?>(null)
    val reconnectStatus: StateFlow<Pair<Int, Int>?> = _reconnectStatus.asStateFlow()

    /** AEROPAD v1.0 #39 — epoch ms of the next retry (real backoff timer). */
    private val _reconnectNextAtMs = MutableStateFlow<Long?>(null)
    val reconnectNextAtMs: StateFlow<Long?> = _reconnectNextAtMs.asStateFlow()

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Boots the engine: checks permissions/adapter, obtains the HID_DEVICE
     * proxy and registers our combined HID app. Safe to call repeatedly.
     */
    override fun start() {
        when {
            !permissionManager.hasAllBluetoothPermissions() -> {
                _state.value = HidConnectionState.PermissionMissing
                Timber.w("HidEngine.start: missing Bluetooth permissions")
                return
            }
            bluetoothAdapter == null -> {
                _state.value = HidConnectionState.HidUnsupported("This device has no Bluetooth adapter.")
                return
            }
            !safeIsEnabled() -> {
                _state.value = HidConnectionState.BluetoothDisabled
                return
            }
        }
        if (hidDevice != null && appRegistered) {
            Timber.d("HidEngine.start: already registered")
            return
        }
        if (proxyRequested) return

        _state.value = HidConnectionState.Initializing
        proxyRequested = try {
            bluetoothAdapter?.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE) == true
        } catch (t: Throwable) {
            Timber.e(t, "getProfileProxy failed")
            false
        }
        if (!proxyRequested) {
            _state.value = HidConnectionState.HidUnsupported("Android refused the HID Device profile on this phone.")
        }
    }

    /** Fully tears down registration + proxy. Called from service shutdown. */
    override fun stop() {
        userInitiatedDisconnect = true
        runCatching { hidDevice?.let { hd -> connectedDevice?.let { hd.disconnect(it) } } }
            .onFailure { Timber.w(it, "disconnect during stop failed") }
        runCatching { hidDevice?.unregisterApp() }
            .onFailure { Timber.w(it, "unregisterApp failed") }
        runCatching { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice) }
            .onFailure { Timber.w(it, "closeProfileProxy failed") }
        hidDevice = null
        connectedDevice = null
        pendingConnect = null
        appRegistered = false
        proxyRequested = false
        _state.value = HidConnectionState.Idle
        Timber.i("HidEngine stopped")
    }

    // ------------------------------------------------------------------
    // Connection control
    // ------------------------------------------------------------------

    /** Connect to a bonded host. If registration isn't ready yet, queues it. */
    fun connect(device: BluetoothDevice) {
        userInitiatedDisconnect = false
        reconnectAttempts = 0
        val hd = hidDevice
        if (hd == null || !appRegistered) {
            Timber.d("connect: engine not ready, queueing %s", device.address)
            pendingConnect = device
            start()
            return
        }
        _state.value = HidConnectionState.Connecting(device.toRemote())
        val ok = runCatching { hd.connect(device) }
            .onFailure { Timber.e(it, "connect() threw") }
            .getOrDefault(false)
        if (!ok) {
            _state.value = HidConnectionState.Error("Could not start connection to ${device.safeName()}.")
        }
    }

    /**
     * Connect by MAC address (domain-facing entry point).
     * Uses getRemoteDevice so even a host that is mid-pairing can be targeted.
     */
    override fun connectTo(address: String) {
        val device = runCatching { bluetoothAdapter?.getRemoteDevice(address) }
            .onFailure { Timber.e(it, "getRemoteDevice(%s) failed", address) }
            .getOrNull()
        if (device == null) {
            _state.value = HidConnectionState.Error("Device $address not found.")
            return
        }
        connect(device)
    }

    /** User-initiated disconnect — suppresses auto-reconnect. */
    override fun disconnect() {
        userInitiatedDisconnect = true
        val hd = hidDevice ?: return
        val dev = connectedDevice ?: return
        runCatching { hd.disconnect(dev) }
            .onFailure { Timber.w(it, "disconnect() threw") }
    }

    /** Bonded devices for the picker UI (never throws). */
    override fun bondedDevices(): List<RemoteDevice> =
        runCatching {
            bluetoothAdapter?.bondedDevices.orEmpty().map { it.toRemote() }
        }.getOrDefault(emptyList())

    /** Resolve a bonded BluetoothDevice by MAC (for connect-by-address). */
    fun bondedDeviceByAddress(address: String): BluetoothDevice? =
        runCatching {
            bluetoothAdapter?.bondedDevices.orEmpty().firstOrNull { it.address == address }
        }.getOrNull()

    // ------------------------------------------------------------------
    // Action dispatch — the single entry point for all input
    // ------------------------------------------------------------------

    /**
     * Executes any [HidAction]. Fire-and-forget; runs on the HID thread.
     * Returns immediately — never blocks the UI thread.
     */
    override fun send(action: HidAction) {
        scope.launch { executeAction(action) }
    }

    private suspend fun executeAction(action: HidAction) {
        try {
            when (action) {
                is HidAction.KeyTap -> {
                    report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboard(action.modifiers, listOf(action.key)))
                    delay(KEY_TAP_DELAY_MS)
                    report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboardRelease())
                }
                is HidAction.KeyDown ->
                    report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboard(action.modifiers, listOf(action.key)))
                is HidAction.KeyRelease ->
                    report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboardRelease())
                is HidAction.TypeText -> typeText(action.text)
                is HidAction.MouseMove ->
                    // V2 PART A — pooled buffer: zero alloc on the hot path.
                    report(HidDescriptors.REPORT_ID_MOUSE, ReportPool.mouseInto(0, action.dx, action.dy, 0))
                is HidAction.MouseClick -> {
                    report(HidDescriptors.REPORT_ID_MOUSE, HidReportBuilder.mouse(buttons = action.button.mask))
                    delay(KEY_TAP_DELAY_MS)
                    report(HidDescriptors.REPORT_ID_MOUSE, HidReportBuilder.mouse())
                }
                is HidAction.MouseDoubleClick -> {
                    repeat(2) {
                        report(HidDescriptors.REPORT_ID_MOUSE, HidReportBuilder.mouse(buttons = action.button.mask))
                        delay(KEY_TAP_DELAY_MS)
                        report(HidDescriptors.REPORT_ID_MOUSE, HidReportBuilder.mouse())
                        delay(30L)
                    }
                }
                is HidAction.MouseDown ->
                    report(HidDescriptors.REPORT_ID_MOUSE, HidReportBuilder.mouse(buttons = action.button.mask))
                is HidAction.MouseUp ->
                    report(HidDescriptors.REPORT_ID_MOUSE, HidReportBuilder.mouse())
                is HidAction.MouseScroll ->
                    report(HidDescriptors.REPORT_ID_MOUSE, HidReportBuilder.mouse(wheel = action.amount))
                is HidAction.MouseDrag ->
                    report(HidDescriptors.REPORT_ID_MOUSE, HidReportBuilder.mouse(buttons = action.button.mask, dx = action.dx, dy = action.dy))
                is HidAction.MediaTap -> {
                    report(HidDescriptors.REPORT_ID_CONSUMER, HidReportBuilder.consumer(action.usage))
                    delay(KEY_TAP_DELAY_MS)
                    report(HidDescriptors.REPORT_ID_CONSUMER, HidReportBuilder.consumerRelease())
                }
                is HidAction.SystemTap -> {
                    report(HidDescriptors.REPORT_ID_SYSTEM, HidReportBuilder.system(action.bits))
                    delay(KEY_TAP_DELAY_MS)
                    report(HidDescriptors.REPORT_ID_SYSTEM, HidReportBuilder.systemRelease())
                }
                is HidAction.GamepadUpdate ->
                    // V2 PART A — pooled buffer: zero alloc on the hot path.
                    report(HidDescriptors.REPORT_ID_GAMEPAD, ReportPool.gamepadInto(action.snapshot))
            }
        } catch (t: Throwable) {
            // Absolute last line of defense: an action must never crash the app.
            Timber.e(t, "executeAction(%s) failed", action::class.simpleName)
        }
    }

    /** Types a string as individual keystrokes, handling repeated chars correctly. */
    private suspend fun typeText(text: String) {
        for (char in text) {
            val stroke = CharToHidMapper.map(char)
            if (stroke == null) {
                Timber.w("typeText: unmappable char '%s' skipped", char)
                continue
            }
            report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboard(stroke.modifiers, listOf(stroke.key)))
            delay(KEY_TAP_DELAY_MS)
            report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboardRelease())
            delay(KEY_TAP_DELAY_MS)
        }
    }

    /** Sends one raw report; false (and log) on any failure. Never throws.
     *  ADV S5: every send is timed (System.nanoTime around the REAL
     *  framework call) and fed to the health tracker. */
    private fun report(id: Int, data: ByteArray): Boolean {
        val hd = hidDevice
        val dev = connectedDevice
        if (hd == null || dev == null) return false
        val t0 = System.nanoTime()
        val ok = runCatching { hd.sendReport(dev, id, data) }
            .onFailure { Timber.w(it, "sendReport(id=%d) failed", id) }
            .getOrDefault(false)
        health.onReport(ok, (System.nanoTime() - t0) / 1000)
        return ok
    }

    // ------------------------------------------------------------------
    // Framework callbacks
    // ------------------------------------------------------------------

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            Timber.i("HID_DEVICE proxy connected")
            hidDevice = proxy as? BluetoothHidDevice
            proxyRequested = false
            registerApp()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            Timber.w("HID_DEVICE proxy disconnected")
            hidDevice = null
            connectedDevice = null
            appRegistered = false
            proxyRequested = false
            _state.value = HidConnectionState.Idle
        }
    }

    private fun registerApp() {
        val hd = hidDevice ?: return
        val sdp = BluetoothHidDeviceAppSdpSettings(
            SDP_NAME, SDP_DESCRIPTION, SDP_PROVIDER,
            SUBCLASS_COMBO, HidDescriptors.COMBINED
        )
        val ok = runCatching { hd.registerApp(sdp, null, null, hidExecutor, hidCallback) }
            .onFailure { Timber.e(it, "registerApp threw") }
            .getOrDefault(false)
        if (!ok) {
            _state.value = HidConnectionState.HidUnsupported(
                "Android refused HID registration. This phone or ROM may not support HID Device mode."
            )
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Timber.i("onAppStatusChanged registered=%s", registered)
            appRegistered = registered
            if (!registered) {
                connectedDevice = null
                if (_state.value !is HidConnectionState.HidUnsupported) {
                    _state.value = HidConnectionState.Idle
                }
                return
            }
            _state.value = HidConnectionState.Idle
            // Flush a queued connect request, if any.
            pendingConnect?.let { queued ->
                pendingConnect = null
                connect(queued)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, btState: Int) {
            Timber.i("onConnectionStateChanged state=%d device=%s", btState, device?.address)
            when (btState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    lastHostDevice = device
                    reconnectAttempts = 0
                    _reconnectStatus.value = null           // ADV S5
                    health.markConnected()                   // ADV S5
                    device?.let {
                        history.onConnected("BLUETOOTH", it.safeName())   // #41
                        _state.value = HidConnectionState.Connected(it.toRemote())
                    }
                }
                BluetoothProfile.STATE_CONNECTING ->
                    device?.let { _state.value = HidConnectionState.Connecting(it.toRemote()) }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val wasConnected = connectedDevice != null
                    connectedDevice = null
                    _state.value = HidConnectionState.Idle
                    if (wasConnected) {
                        health.markDisconnected()   // ADV S5
                        history.onDisconnected("BLUETOOTH",
                            if (userInitiatedDisconnect) "user disconnect" else "link dropped")  // #41
                    }
                    if (wasConnected && !userInitiatedDisconnect) scheduleReconnect()
                }
                // STATE_DISCONNECTING: transient; we wait for DISCONNECTED.
            }
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            Timber.i("onVirtualCableUnplug %s", device?.address)
            userInitiatedDisconnect = true // host asked us to unplug: do not fight it
            connectedDevice = null
            _state.value = HidConnectionState.Idle
        }
    }

    /** Auto-reconnect with capped exponential backoff (1s, 2s, 4s, 8s). */
    private fun scheduleReconnect() {
        val target = lastHostDevice ?: return
        if (reconnectAttempts >= RECONNECT_MAX_ATTEMPTS) {
            Timber.w("auto-reconnect gave up after %d attempts", reconnectAttempts)
            return
        }
        val attempt = ++reconnectAttempts
        val backoff = RECONNECT_BASE_DELAY_MS shl (attempt - 1)
        Timber.i("auto-reconnect attempt %d in %dms", attempt, backoff)
        _reconnectStatus.value = attempt to RECONNECT_MAX_ATTEMPTS   // ADV S5
        _reconnectNextAtMs.value = System.currentTimeMillis() + backoff   // #39
        scope.launch {
            delay(backoff)
            _reconnectNextAtMs.value = null   // #39
            if (connectedDevice == null && !userInitiatedDisconnect && appRegistered) {
                connect(target)
            } else {
                _reconnectStatus.value = null
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun safeIsEnabled(): Boolean =
        runCatching { bluetoothAdapter?.isEnabled == true }.getOrDefault(false)

    private fun BluetoothDevice.toRemote(): RemoteDevice =
        RemoteDevice(address = address, name = safeName(), isPaired = safeBonded())

    private fun BluetoothDevice.safeName(): String =
        runCatching { name }.getOrNull() ?: "Unknown device"

    private fun BluetoothDevice.safeBonded(): Boolean =
        runCatching { bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
}
