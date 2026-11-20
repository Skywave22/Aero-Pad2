package com.aeropad.remote.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.aeropad.remote.model.GamepadSnapshot
import com.aeropad.remote.model.HidAction
import com.aeropad.remote.model.HidConnectionState
import com.aeropad.remote.model.MouseButton
import com.aeropad.remote.model.PowerProfile
import com.aeropad.remote.model.RemoteDevice
import com.aeropad.remote.permission.PermissionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The heart of AeroPad: registers this phone as a Bluetooth HID Device
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
    private val history: com.aeropad.remote.data.history.ConnectionHistoryStore,
    // Round 9 — descriptor mode (legacy = stable 3.3.0 wire format).
    private val settingsStore: com.aeropad.remote.domain.SettingsStore
) : com.aeropad.remote.domain.HidController {

    private companion object {
        const val SDP_NAME = "AeroPad"
        const val SDP_DESCRIPTION = "Keyboard, mouse, media and gamepad remote"
        const val SDP_PROVIDER = "AeroPad"
        /** HID subclass: 0xC0 = combo keyboard+pointing device. */
        const val SUBCLASS_COMBO: Byte = 0xC0.toByte()
        /** Kept for the status UI (attempt N of M in the fast phase). */
        const val RECONNECT_MAX_ATTEMPTS = 4
        /** How long to wait for the stack to answer a connect attempt. */
        const val CONNECT_RESULT_WAIT_MS = 3_000L
        /** Round 6: "Connecting…" can never hang forever anymore. */
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val REGISTRATION_TIMEOUT_MS = 10_000L
        /** Round 10 — gentle idle keep-alive so power-hungry host stacks
         *  (notably some Android HID hosts) never see a "silent" link. */
        const val KEEPALIVE_MS = 4_000L
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
        }, "AeroPad-HID").apply { priority = Thread.MAX_PRIORITY }
    }
    private val hidDispatcher = hidExecutor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + hidDispatcher)

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private val inputSession = HidInputSession(checkNotNull(scope.coroutineContext[kotlinx.coroutines.Job]))
    private val keyboardReports = Mutex()
    private var heldKeyboardReport: ByteArray? = null
    private val consumerReports = Mutex()
    private val systemReports = Mutex()
    private val mouseClicks = Mutex()
    private var connectingDevice: BluetoothDevice? = null
    private var pendingConnect: BluetoothDevice? = null
    @Volatile private var appRegistered = false
    private var proxyRequested = false
    private var userInitiatedDisconnect = false
    private var reconnectAttempts = 0
    /** Round 10 — never-give-up reconnect (see ReconnectPolicy). */
    private val reconnectPolicy = com.aeropad.remote.domain.ReconnectPolicy()
    private val flapGuard = com.aeropad.remote.domain.FlapGuard()
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private var lastHostDevice: BluetoothDevice? = null
    // Round 6 — lifecycle resilience: adapter on/off recovery + connect watchdog.
    private var stopped = false
    private var btReceiverRegistered = false
    private var connectWatchdog: kotlinx.coroutines.Job? = null
    private var registrationWatchdog: kotlinx.coroutines.Job? = null
    private var profileWatchdog: kotlinx.coroutines.Job? = null
    private var cancelledConnection: BluetoothDevice? = null
    private var connectedAtMs = 0L
    private var wantExtendedReports = false
    @Volatile private var extendedReports = false
    private var registrationCallback: BluetoothHidDevice.Callback? = null
    private var profileListener: BluetoothProfile.ServiceListener? = null
    private var keepAlive: kotlinx.coroutines.Job? = null
    // C1 / C2 / M7 / A1 — single authoritative mouse button mask.
    private val mouseButtons = MouseButtonTracker()
    // L2 — configurable key tap delay (default 12ms; adjustable for TVs/BIOS).
    private var keyTapDelayMs: Long = 12L
    // A12 — power profile adjusts idle keep-alive cadence.
    private var keepAliveIntervalMs: Long = KEEPALIVE_MS
    // A6 — last 20 state transitions for diagnostics export.
    private val transitionLog = ArrayDeque<String>()

    private val _state = MutableStateFlow<HidConnectionState>(HidConnectionState.Idle)
    override val state: StateFlow<HidConnectionState> = _state.asStateFlow()

    private fun updateState(next: HidConnectionState) {
        if (_state.value != next) {
            val label = when (next) {
                is HidConnectionState.Connected -> "Connected(${next.device.name})"
                is HidConnectionState.Connecting -> "Connecting(${next.device.name})"
                is HidConnectionState.HidUnsupported -> "HidUnsupported(${next.reason})"
                is HidConnectionState.Error -> "Error(${next.message})"
                else -> next::class.simpleName ?: "Unknown"
            }
            synchronized(transitionLog) {
                transitionLog.addLast("${System.currentTimeMillis() % 100000}ms: $label")
                while (transitionLog.size > 20) transitionLog.removeFirst()
            }
        }
        _state.value = next
    }

    /** A6 — recent state transitions for diagnostics export. */
    fun recentStateTransitions(): List<String> = synchronized(transitionLog) { transitionLog.toList() }

    /** A6 — whether the extended HID descriptor is currently active. */
    fun isExtendedDescriptorActive(): Boolean = appRegistered && extendedReports

    /** ADV S5 — real measured connection-health metrics (public read). */
    val health = com.aeropad.remote.domain.ConnectionHealthTracker()

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
        scope.launch { startOnHidThread() }
    }

    private fun startOnHidThread() {
        stopped = false
        ensureBtStateReceiver()
        when {
            !permissionManager.hasAllBluetoothPermissions() -> {
                cancelReconnect()
                retireProxy()
                clearLink("Bluetooth permission missing")
                updateState(HidConnectionState.PermissionMissing)
                Timber.w("HidEngine.start: missing Bluetooth permissions")
                return
            }
            bluetoothAdapter == null -> {
                updateState(HidConnectionState.HidUnsupported("This device has no Bluetooth adapter."))
                return
            }
            !safeIsEnabled() -> {
                cancelReconnect()
                retireProxy()
                clearLink("Bluetooth off")
                updateState(HidConnectionState.BluetoothDisabled)
                return
            }
        }
        if (hidDevice != null) {
            if (!appRegistered) registerApp()
            return
        }
        if (proxyRequested) return

        updateState(HidConnectionState.Initializing)
        val listener = HidProfileListener()
        profileListener = listener
        proxyRequested = try {
            bluetoothAdapter?.getProfileProxy(context, listener, BluetoothProfile.HID_DEVICE) == true
        } catch (t: Throwable) {
            Timber.e(t, "getProfileProxy failed")
            false
        }
        if (!proxyRequested) {
            profileListener = null
            updateState(HidConnectionState.HidUnsupported("Android refused the HID Device profile on this phone."))
        } else {
            profileWatchdog?.cancel()
            profileWatchdog = scope.launch {
                delay(REGISTRATION_TIMEOUT_MS)
                if (profileListener === listener && proxyRequested) {
                    profileListener = null
                    proxyRequested = false
                    updateState(HidConnectionState.Error("Android did not open the Bluetooth HID profile. Try again."))
                }
            }
        }
    }

    /** Fully tears down registration + proxy. Called from service shutdown. */
    override fun stop() {
        scope.launch { stopOnHidThread() }
    }

    private fun stopOnHidThread() {
        stopped = true
        userInitiatedDisconnect = true
        cancelReconnect()
        resetInputsOnHidThread()
        if (btReceiverRegistered) {
            runCatching { context.unregisterReceiver(btStateReceiver) }
                .onFailure { Timber.w(it, "unregisterReceiver failed") }
            btReceiverRegistered = false
        }
        runCatching { hidDevice?.let { hd -> connectedDevice?.let { hd.disconnect(it) } } }
            .onFailure { Timber.w(it, "disconnect during stop failed") }
        runCatching { hidDevice?.unregisterApp() }
            .onFailure { Timber.w(it, "unregisterApp failed") }
        retireProxy()
        clearLink("service stopped")
        pendingConnect = null
        cancelledConnection = null
        updateState(HidConnectionState.Idle)
        Timber.i("HidEngine stopped")
    }

    // ------------------------------------------------------------------
    // Connection control
    // ------------------------------------------------------------------

    /** User-initiated connect: resets backoff + flap counters. */
    private fun connect(device: BluetoothDevice) {
        cancelReconnect()
        reconnectAttempts = 0
        flapGuard.forgive()
        autoConnect(device)
    }

    /** Only one outgoing connection may be in progress; a new target waits its turn. */
    private fun autoConnect(device: BluetoothDevice) {
        if (connectedDevice == device || connectingDevice == device) return
        userInitiatedDisconnect = false
        if (cancelledConnection == device) cancelledConnection = null
        val hd = hidDevice
        val currentHost = connectedDevice
        if (hd != null && currentHost != null) {
            // Android exposes a single active HID host. Wait for the old link
            // to go down before attempting the next one.
            val alreadySwitching = pendingConnect != null
            pendingConnect = device
            if (alreadySwitching) return
            resetInputsOnHidThread()
            if (!runCatching { hd.disconnect(currentHost) }.getOrDefault(false)) {
                pendingConnect = null
                Timber.w("Could not disconnect the current HID host to switch devices")
            } else {
                connectWatchdog?.cancel()
                connectWatchdog = scope.launch {
                    delay(CONNECT_TIMEOUT_MS)
                    if (connectedDevice == currentHost) pendingConnect = null
                }
            }
            return
        }
        if (hd == null || !appRegistered) {
            Timber.d("connect: engine not ready, queueing %s", device.address)
            pendingConnect = device
            startOnHidThread()
            return
        }
        if (connectingDevice != null && connectingDevice != device) {
            pendingConnect = device
            return
        }
        connectingDevice = device
        updateState(HidConnectionState.Connecting(device.toRemote()))
        val ok = runCatching { hd.connect(device) }
            .onFailure { Timber.e(it, "connect() threw") }
            .getOrDefault(false)
        if (!ok) {
            connectingDevice = null
            connectWatchdog?.cancel()
            updateState(HidConnectionState.Error("Could not start connection to ${device.safeName()}."))
            return
        }
        watchConnect(device)
    }

    private fun watchConnect(device: BluetoothDevice) {
        connectWatchdog?.cancel()
        connectWatchdog = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (connectingDevice != device) return@launch
            connectingDevice = null
            if (userInitiatedDisconnect) {
                updateState(HidConnectionState.Idle)
            } else {
                updateState(HidConnectionState.Error(
                    "Connection to ${device.safeName()} timed out. Make sure the host is paired and awake, then try again."
                ))
            }
            connectPendingHost()
        }
    }

    /**
     * Connect by MAC address (domain-facing entry point).
     * Uses getRemoteDevice so even a host that is mid-pairing can be targeted.
     */
    override fun connectTo(address: String) {
        scope.launch {
            val device = runCatching { bluetoothAdapter?.getRemoteDevice(address) }.getOrNull()
            if (device != null) connect(device)
            else if (connectedDevice == null) updateState(HidConnectionState.Error("Device not found."))
        }
    }

    override fun reconnectLast() {
        scope.launch { lastHostDevice?.let(::connect) }
    }

    override fun disconnect() {
        scope.launch {
            userInitiatedDisconnect = true
            pendingConnect = null
            cancelReconnect()
            resetInputsOnHidThread()
            val dev = connectedDevice ?: connectingDevice ?: return@launch
            cancelledConnection = dev
            runCatching { hidDevice?.disconnect(dev) }
                .onFailure { Timber.w(it, "disconnect() threw") }
        }
    }

    override fun releaseInputs() {
        scope.launch { resetInputsOnHidThread() }
    }

    private fun resetInputsOnHidThread() {
        inputSession.reset()
        mouseButtons.clear()
        heldKeyboardReport = null
        if (connectedDevice == null) return
        report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboardRelease())
        report(HidDescriptors.REPORT_ID_MOUSE, HidReportBuilder.mouse())
        report(HidDescriptors.REPORT_ID_CONSUMER, HidReportBuilder.consumerRelease())
        report(HidDescriptors.REPORT_ID_SYSTEM, HidReportBuilder.systemRelease())
        report(HidDescriptors.REPORT_ID_GAMEPAD, ReportPool.gamepadInto(GamepadSnapshot(), extendedReports))
        if (extendedReports) report(HidDescriptors.REPORT_ID_TOUCH, HidReportBuilder.touch(false, 0, 0))
    }

    private fun clearLink(reason: String): Boolean {
        val wasConnected = connectedDevice != null
        connectedDevice = null
        connectingDevice = null
        connectWatchdog?.cancel()
        keepAlive?.cancel()
        resetInputsOnHidThread()
        if (wasConnected) {
            health.markDisconnected()
            history.onDisconnected("BLUETOOTH", reason)
        }
        return wasConnected
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        _reconnectStatus.value = null
        _reconnectNextAtMs.value = null
    }

    private fun connectPendingHost(): Boolean {
        val target = pendingConnect ?: return false
        pendingConnect = null
        autoConnect(target)
        return true
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
        scope.launch(inputSession.job) {
            if (connectedDevice != null) executeAction(action)
        }
    }

    private suspend fun executeAction(action: HidAction) {
        try {
            when (action) {
                is HidAction.KeyTap -> keyboardReports.withLock {
                    report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboard(action.modifiers, listOf(action.key)))
                    delay(keyTapDelayMs)
                    releaseKeyboardTap()
                }
                is HidAction.KeyDown -> keyboardReports.withLock {
                    val payload = HidReportBuilder.keyboard(action.modifiers, listOf(action.key))
                    heldKeyboardReport = payload
                    report(HidDescriptors.REPORT_ID_KEYBOARD, payload)
                }
                is HidAction.KeyRelease -> keyboardReports.withLock {
                    heldKeyboardReport = null
                    report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboardRelease())
                }
                is HidAction.TypeText -> keyboardReports.withLock { typeText(action.text) }
                is HidAction.MouseMove ->
                    // C1 fix + V2 PART A pooled buffer: include held mouse buttons on every move!
                    report(
                        HidDescriptors.REPORT_ID_MOUSE,
                        ReportPool.mouseInto(mouseButtons.currentByte, action.dx, action.dy, 0)
                    )
                is HidAction.MouseClick -> mouseClicks.withLock { clickMouse(action.button) }
                is HidAction.MouseDoubleClick -> mouseClicks.withLock {
                    clickMouse(action.button)
                    delay(30L)
                    clickMouse(action.button)
                }
                is HidAction.MouseDown ->
                    // C1 / A1: set button bit in persistent engine state.
                    report(
                        HidDescriptors.REPORT_ID_MOUSE,
                        HidReportBuilder.mouse(buttons = mouseButtons.onDown(action.button))
                    )
                is HidAction.MouseUp ->
                    // M7 / C1: clear ONLY the released button bit, keeping any other held buttons.
                    report(
                        HidDescriptors.REPORT_ID_MOUSE,
                        HidReportBuilder.mouse(buttons = mouseButtons.onUp(action.button))
                    )
                is HidAction.MouseScroll ->
                    // C1 / A2: preserve held buttons while scrolling (scroll-while-dragging).
                    report(
                        HidDescriptors.REPORT_ID_MOUSE,
                        ReportPool.mouseInto(mouseButtons.currentByte, 0, 0, action.amount)
                    )
                is HidAction.MouseDrag ->
                    report(
                        HidDescriptors.REPORT_ID_MOUSE,
                        HidReportBuilder.mouse(
                            buttons = mouseButtons.withTransientPress(action.button),
                            dx = action.dx,
                            dy = action.dy
                        )
                    )
                is HidAction.MediaTap -> consumerReports.withLock {
                    report(HidDescriptors.REPORT_ID_CONSUMER, HidReportBuilder.consumer(action.usage))
                    delay(keyTapDelayMs)
                    report(HidDescriptors.REPORT_ID_CONSUMER, HidReportBuilder.consumerRelease())
                }
                is HidAction.SystemTap -> systemReports.withLock {
                    report(HidDescriptors.REPORT_ID_SYSTEM, HidReportBuilder.system(action.bits))
                    delay(keyTapDelayMs)
                    report(HidDescriptors.REPORT_ID_SYSTEM, HidReportBuilder.systemRelease())
                }
                is HidAction.GamepadUpdate ->
                    // V2 PART A — pooled buffer: zero alloc on the hot path.
                    report(
                        HidDescriptors.REPORT_ID_GAMEPAD,
                        ReportPool.gamepadInto(action.snapshot, extendedReports)
                    )
                is HidAction.TouchUpdate -> {
                    if (!extendedReports) return // digitizer not registered (legacy mode)
                    // Normalized 0..1 → absolute 15-bit digitizer space.
                    val tx = (action.x.coerceIn(0f, 1f) * 32767f).toInt()
                    val ty = (action.y.coerceIn(0f, 1f) * 32767f).toInt()
                    report(HidDescriptors.REPORT_ID_TOUCH, HidReportBuilder.touch(action.contact, tx, ty))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Exception) {
            // Absolute last line of defense: an action must never crash the app.
            Timber.e(t, "executeAction(%s) failed", action::class.simpleName)
        }
    }

    private suspend fun clickMouse(button: MouseButton) {
        mouseButtons.setClick(button)
        report(HidDescriptors.REPORT_ID_MOUSE, HidReportBuilder.mouse(buttons = mouseButtons.currentByte))
        delay(keyTapDelayMs)
        mouseButtons.setClick(null)
        report(HidDescriptors.REPORT_ID_MOUSE, HidReportBuilder.mouse(buttons = mouseButtons.currentByte))
    }

    private fun releaseKeyboardTap() {
        report(HidDescriptors.REPORT_ID_KEYBOARD, heldKeyboardReport ?: HidReportBuilder.keyboardRelease())
    }

    /** Types a string as individual keystrokes, handling repeated chars correctly. */
    private suspend fun typeText(text: String) {
        for (char in text) {
            val stroke = CharToHidMapper.map(char)
            if (stroke == null) continue
            report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboard(stroke.modifiers, listOf(stroke.key)))
            delay(keyTapDelayMs)
            report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboardRelease())
            delay(keyTapDelayMs)
        }
        if (heldKeyboardReport != null) releaseKeyboardTap()
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

    private inner class HidProfileListener : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            scope.launch {
                if (stopped || profileListener !== this@HidProfileListener || !safeIsEnabled()) {
                    runCatching { bluetoothAdapter?.closeProfileProxy(profile, proxy) }
                    return@launch
                }
                profileWatchdog?.cancel()
                hidDevice = proxy as? BluetoothHidDevice
                proxyRequested = false
                if (hidDevice == null) {
                    profileListener = null
                    updateState(HidConnectionState.HidUnsupported("Android returned an incompatible Bluetooth profile."))
                    return@launch
                }
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            scope.launch {
                if (profileListener !== this@HidProfileListener) return@launch
                retireProxy()
                clearLink("HID profile lost")
                updateState(if (safeIsEnabled()) HidConnectionState.Idle else HidConnectionState.BluetoothDisabled)
                delay(1000)
                if (!stopped && profileListener == null && safeIsEnabled()) {
                    startOnHidThread()
                    startReconnectLoop(reason = "HID profile lost")
                }
            }
        }
    }

    private fun retireProxy() {
        profileWatchdog?.cancel()
        registrationWatchdog?.cancel()
        profileListener = null
        registrationCallback = null
        appRegistered = false
        proxyRequested = false
        runCatching { hidDevice?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) } }
        hidDevice = null
    }

    /**
     * Round 6 — adapter on/off receiver. Before this, toggling Bluetooth
     * (or the system restarting the BT stack) left AeroPad permanently
     * unregistered until the app was force-stopped.
     */
    private val btStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            val action = intent?.action ?: return
            val adapterState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            scope.launch {
                if (stopped) return@launch
                when (action) {
                    android.content.Intent.ACTION_SCREEN_ON, android.content.Intent.ACTION_USER_PRESENT -> {
                        if (connectedDevice == null && !userInitiatedDisconnect && lastHostDevice != null) {
                            retryOnHidThread()
                        }
                    }
                    BluetoothAdapter.ACTION_STATE_CHANGED -> when (adapterState) {
                        BluetoothAdapter.STATE_OFF -> {
                            if (safeIsEnabled()) return@launch
                            cancelReconnect()
                            retireProxy()
                            clearLink("Bluetooth off")
                            updateState(HidConnectionState.BluetoothDisabled)
                        }
                        BluetoothAdapter.STATE_ON -> {
                            if (!safeIsEnabled()) return@launch
                            startOnHidThread()
                            if (lastHostDevice != null && pendingConnect == null && !userInitiatedDisconnect) {
                                startReconnectLoop(reason = "Bluetooth on", immediate = true)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun ensureBtStateReceiver() {
        if (btReceiverRegistered) return
        val filter = android.content.IntentFilter().apply {
            // Round 10 — wake triggers (neither needs a permission).
            addAction(android.content.Intent.ACTION_SCREEN_ON)
            addAction(android.content.Intent.ACTION_USER_PRESENT)
        }
        // Round 16 — ContextCompat handles the API 33+ "flags are mandatory"
        // rule and the pre-33 overloads in one call. The hand-rolled
        // try/recover chain before this compiled to an API-33-only method
        // reference and depended on NoSuchMethodError being swallowed.
        btReceiverRegistered = runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                btStateReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            try {
                // Android Bluetooth broadcasts can originate from the privileged
                // Bluetooth app UID, not the system UID. This action is protected.
                androidx.core.content.ContextCompat.registerReceiver(
                    context,
                    btStateReceiver,
                    android.content.IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                    androidx.core.content.ContextCompat.RECEIVER_EXPORTED
                )
            } catch (e: Exception) {
                runCatching { context.unregisterReceiver(btStateReceiver) }
                throw e
            }
        }.isSuccess
        if (!btReceiverRegistered) Timber.w("could not register BT state receiver")
    }

    private fun applyDescriptorPreference() {
        if (!appRegistered || extendedReports == wantExtendedReports) return
        val hd = hidDevice ?: return
        resetInputsOnHidThread()
        if (runCatching { hd.unregisterApp() }.getOrDefault(false)) {
            appRegistered = false
            clearLink("HID report mode changed")
            updateState(HidConnectionState.Initializing)
            // Re-register only after Android acknowledges unregistering.
            registrationCallback?.let(::watchRegistration)
        } else {
            Timber.w("Android refused to change the HID report mode")
        }
    }

    private fun registerApp() {
        if (registrationCallback != null || stopped) return
        val hd = hidDevice ?: return
        // Round 9: default = the proven-stable 3.3.0 descriptor; the
        // extended one (touch digitizer + analog triggers) is opt-in.
        extendedReports = wantExtendedReports
        val descriptor = if (extendedReports) HidDescriptors.COMBINED else HidDescriptors.LEGACY
        val callback = HidRegistrationCallback()
        registrationCallback = callback
        updateState(HidConnectionState.Initializing)
        val ok = runCatching {
            val sdp = BluetoothHidDeviceAppSdpSettings(SDP_NAME, SDP_DESCRIPTION, SDP_PROVIDER, SUBCLASS_COMBO, descriptor)
            hd.registerApp(sdp, null, null, hidExecutor, callback)
        }
            .onFailure { Timber.e(it, "registerApp threw") }
            .getOrDefault(false)
        if (!ok) {
            registrationCallback = null
            updateState(HidConnectionState.HidUnsupported(
                "Android refused HID registration. This phone or ROM may not support HID Device mode."
            ))
        } else {
            watchRegistration(callback)
        }
    }

    private fun watchRegistration(callback: BluetoothHidDevice.Callback) {
        registrationWatchdog?.cancel()
        registrationWatchdog = scope.launch {
            delay(REGISTRATION_TIMEOUT_MS)
            if (registrationCallback === callback) {
                runCatching { hidDevice?.unregisterApp() }
                retireProxy()
                updateState(HidConnectionState.Error("Android did not finish Bluetooth HID setup. Try again."))
            }
        }
    }

    private inner class HidRegistrationCallback : BluetoothHidDevice.Callback() {

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (registrationCallback !== this) return
            registrationWatchdog?.cancel()
            appRegistered = registered
            if (!registered) {
                registrationCallback = null
                clearLink("HID app unregistered")
                if (!safeIsEnabled()) {
                    updateState(HidConnectionState.BluetoothDisabled)
                } else if (extendedReports != wantExtendedReports || pendingConnect != null) {
                    registerApp()
                    if (!userInitiatedDisconnect) startReconnectLoop(reason = "HID report mode changed")
                } else {
                    updateState(HidConnectionState.Idle)
                    if (!userInitiatedDisconnect) startReconnectLoop(reason = "HID app unregistered")
                }
                return
            }
            if (extendedReports != wantExtendedReports) {
                applyDescriptorPreference()
                if (!appRegistered) return
            }
            if (connectedDevice == null) updateState(HidConnectionState.Idle)
            connectPendingHost()
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, btState: Int) {
            if (registrationCallback !== this || !appRegistered || device == null) return
            when (btState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (connectedDevice == device) return
                    if (connectedDevice != null ||
                        (connectingDevice != null && connectingDevice != device) ||
                        cancelledConnection == device
                    ) {
                        runCatching { hidDevice?.disconnect(device) }
                        return
                    }
                    connectWatchdog?.cancel()
                    connectingDevice = null
                    inputSession.reset()
                    connectedDevice = device
                    mouseButtons.clear()
                    connectedAtMs = android.os.SystemClock.elapsedRealtime()
                    startKeepAlive(device)
                    lastHostDevice = device
                    userInitiatedDisconnect = false
                    reconnectAttempts = 0
                    cancelReconnect()
                    health.markConnected()
                    history.onConnected("BLUETOOTH", device.safeName())
                    updateState(HidConnectionState.Connected(device.toRemote()))
                    connectPendingHost()
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    if (connectedDevice == null && (connectingDevice == null || connectingDevice == device)) {
                        if (connectingDevice == null && cancelledConnection != device) {
                            connectingDevice = device
                            watchConnect(device)
                            updateState(HidConnectionState.Connecting(device.toRemote()))
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (cancelledConnection == device) cancelledConnection = null
                    if (connectedDevice != device && connectingDevice != device) return
                    val reason = when {
                        userInitiatedDisconnect -> "user disconnect"
                        pendingConnect != null -> "host switched"
                        else -> "link dropped"
                    }
                    val wasConnected = clearLink(reason)
                    updateState(HidConnectionState.Idle)
                    if (connectPendingHost()) return
                    if (wasConnected && !userInitiatedDisconnect) {
                        val heldMs = android.os.SystemClock.elapsedRealtime() - connectedAtMs
                        val rest = flapGuard.onDrop(heldMs, android.os.SystemClock.elapsedRealtime())
                        if (rest != null) {
                            updateState(HidConnectionState.Error(
                                "The link keeps dropping. AeroPad will retry in ${rest.restMs / 1000}s. " +
                                    "Keep the host awake and nearby, or re-pair it."
                            ))
                        }
                        startReconnectLoop(reason = "link dropped")
                    }
                }
                // STATE_DISCONNECTING: transient; we wait for DISCONNECTED.
            }
        }

        /**
         * Round 10 — answer host control requests. Android's HID host issues
         * GET_REPORT after connecting; a device that never replies can be
         * dropped by the host stack, which matches the "connected then
         * suddenly disconnected" symptom on Android hosts.
         */
        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            if (registrationCallback !== this) return
            val hd = hidDevice ?: return
            runCatching {
                val payload: ByteArray = when (id.toInt()) {
                    HidDescriptors.REPORT_ID_KEYBOARD -> HidReportBuilder.keyboardRelease()
                    HidDescriptors.REPORT_ID_MOUSE -> HidReportBuilder.mouse(buttons = mouseButtons.currentByte)
                    HidDescriptors.REPORT_ID_CONSUMER -> HidReportBuilder.consumerRelease()
                    HidDescriptors.REPORT_ID_SYSTEM -> HidReportBuilder.systemRelease()
                    HidDescriptors.REPORT_ID_GAMEPAD ->
                        HidReportBuilder.gamepadNeutral(extendedReports)
                    HidDescriptors.REPORT_ID_TOUCH ->
                        if (!extendedReports) ByteArray(0)
                        else HidReportBuilder.touch(contact = false, x = 0, y = 0)
                    else -> ByteArray(0)
                }
                hd.replyReport(device, type, id, payload)
            }.onFailure { Timber.w(it, "replyReport(id=%s) failed", id) }
        }

        /** Host→device output report (keyboard LEDs etc.) — acknowledged by returning. */
        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            Timber.d("onSetReport id=%s len=%s", id, data?.size ?: 0)
        }

        override fun onSetProtocol(device: BluetoothDevice?, protocol: Byte) {
            Timber.d("onSetProtocol protocol=%s", protocol)
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            if (registrationCallback !== this || device == null || device != connectedDevice) return
            userInitiatedDisconnect = true
            pendingConnect = null
            cancelReconnect()
            clearLink("host unplugged")
            updateState(HidConnectionState.Idle)
        }
    }

    /**
     * Round 10 + C2 fix — idle keep-alive: a neutral mouse report every few
     * seconds while connected, so host stacks with idle-link power policies
     * never consider the link dead.
     *
     * C2 fix: never emit a keep-alive while any mouse button is held down,
     * and always pass [mouseButtons.currentByte] so a drag can never be
     * interrupted by the timer.
     */
    private fun startKeepAlive(dev: BluetoothDevice) {
        keepAlive?.cancel()
        keepAlive = scope.launch {
            while (true) {
                delay(keepAliveIntervalMs)
                if (connectedDevice == dev) {
                    if (!mouseButtons.hasHeldButtons) {
                        report(
                            HidDescriptors.REPORT_ID_MOUSE,
                            ReportPool.mouseInto(mouseButtons.currentByte, 0, 0, 0)
                        )
                    }
                } else {
                    break
                }
            }
        }
    }

    /**
     * Round 10 — persistent reconnect.
     *
     * Replaces the old 15-second-and-give-up timer. Runs until the link is back,
     * the user disconnects, or the engine stops. Each pass:
     *
     *  1. re-registers the HID app if Android dropped it (this used to silently
     *     end the story: the old code required `appRegistered` and did nothing
     *     when it was false);
     *  2. waits the policy delay (fast phase, then a calm 15–30 s cadence);
     *  3. asks the stack to connect and gives the callback time to answer.
     *
     * Idempotent: a second call while the loop is alive is a no-op, so a burst
     * of disconnect callbacks cannot start two competing loops.
     */
    private fun startReconnectLoop(reason: String, immediate: Boolean = false) {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            Timber.i("reconnect loop start (%s)", reason)
            var first = true
            while (!stopped && !userInitiatedDisconnect && connectedDevice == null) {
                val decision = com.aeropad.remote.domain.ReconnectDecision.decide(
                    adapterOn = safeIsEnabled(),
                    appRegistered = appRegistered,
                    hasRememberedHost = lastHostDevice != null,
                    userInitiatedDisconnect = userInitiatedDisconnect,
                    resting = flapGuard.isResting(android.os.SystemClock.elapsedRealtime())
                )
                when (decision) {
                    com.aeropad.remote.domain.ReconnectDecision.Action.STOP -> break

                    com.aeropad.remote.domain.ReconnectDecision.Action.WAIT_FOR_ADAPTER -> {
                        // Radio is off (or resting). Do not poll hot — a cheap
                        // 2 s tick keeps us responsive without waking the CPU
                        // constantly.
                        _reconnectStatus.value = null
                        _reconnectNextAtMs.value = null
                        delay(2_000)
                        continue
                    }

                    com.aeropad.remote.domain.ReconnectDecision.Action.RE_REGISTER -> {
                        Timber.i("reconnect: HID app was unregistered — re-registering")
                        reRegisterApp()
                        delay(1_500)
                        continue
                    }

                    com.aeropad.remote.domain.ReconnectDecision.Action.CONNECT -> Unit
                }

                val target = lastHostDevice ?: break
                if (!first || !immediate) {
                    reconnectAttempts++
                    val delayMs = reconnectPolicy.delayFor(
                        attempt = reconnectAttempts,
                        random = kotlin.random.Random.nextFloat()
                    )
                    _reconnectStatus.value = reconnectAttempts to
                        RECONNECT_MAX_ATTEMPTS
                    _reconnectNextAtMs.value = System.currentTimeMillis() + delayMs
                    Timber.i("reconnect attempt %d in %dms", reconnectAttempts, delayMs)
                    delay(delayMs)
                    _reconnectNextAtMs.value = null
                }
                first = false

                if (userInitiatedDisconnect || connectedDevice != null) break
                autoConnect(target)
                // Give the Bluetooth callback a moment to report back before
                // deciding whether to schedule another pass.
                delay(CONNECT_RESULT_WAIT_MS)
            }
            _reconnectStatus.value = null
            _reconnectNextAtMs.value = null
            reconnectJob = null
            Timber.i("reconnect loop end (connected=%s)", connectedDevice != null)
        }
    }

    /** Re-register the HID app after the system or a ROM dropped it. */
    private fun reRegisterApp() {
        if (appRegistered) return
        if (hidDevice == null) startOnHidThread() else registerApp()
    }

    /**
     * User pressed "retry now" (or the screen turned on and we noticed the link
     * was gone). Clears any penalty, cancels the waiting loop and starts a fresh
     * fast-phase attempt immediately.
     */
    override fun retryNow() {
        scope.launch { retryOnHidThread() }
    }

    private fun retryOnHidThread() {
        userInitiatedDisconnect = false
        flapGuard.forgive()
        reconnectAttempts = 0
        cancelReconnect()
        if (lastHostDevice == null) {
            // Nothing remembered yet — make sure the engine is at least ready.
            if (!appRegistered) {
                startOnHidThread()
            }
            return
        }
        startReconnectLoop(reason = "user retry", immediate = true)
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

    init {
        scope.launch {
            settingsStore.appSettings.collect { a ->
                wantExtendedReports = a.extendedReports
                keepAliveIntervalMs = when (a.powerProfile) {
                    PowerProfile.PERFORMANCE -> 3_000L
                    PowerProfile.BALANCED -> 4_000L
                    PowerProfile.BATTERY_SAVER -> 8_000L
                }
                applyDescriptorPreference()
            }
        }
        scope.launch {
            settingsStore.keyboardSettings.collect { k ->
                keyTapDelayMs = k.sanitized().keyTapDelayMs.toLong()
            }
        }
    }

}
