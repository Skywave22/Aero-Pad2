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
        const val KEY_TAP_DELAY_MS = 8L
        /** Kept for the status UI (attempt N of M in the fast phase). */
        const val RECONNECT_MAX_ATTEMPTS = 4
        /** How long to wait for the stack to answer a connect attempt. */
        const val CONNECT_RESULT_WAIT_MS = 3_000L
        const val RECONNECT_BASE_DELAY_MS = 1000L
        /** Round 6: "Connecting…" can never hang forever anymore. */
        const val CONNECT_TIMEOUT_MS = 15_000L
        // Round 8 — anti-flap: detect connect/drop ping-pong and break it.
        const val CONNECT_DEBOUNCE_MS = 3_000L
        const val STABLE_LINK_MS = 5_000L
        const val MAX_FLAPS = 3
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
    private var pendingConnect: BluetoothDevice? = null
    private var appRegistered = false
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
    private var connectingSinceMs = 0L
    private var connectedAtMs = 0L
    private var flapCount = 0
    private var extendedReports = false
    private var keepAlive: kotlinx.coroutines.Job? = null
    // C1 / C2 / M7 / A1 — single authoritative mouse button mask.
    private val mouseButtons = MouseButtonTracker()
    // L2 — configurable key tap delay (default 12ms; adjustable for TVs/BIOS).
    private var keyTapDelayMs: Long = 12L
    // A12 — power profile adjusts idle keep-alive cadence.
    private var keepAliveIntervalMs: Long = KEEPALIVE_MS
    // A6 — last 20 state transitions for diagnostics export.
    private val transitionLog = ArrayDeque<String>()

    init {
        scope.launch {
            settingsStore.appSettings.collect { a ->
                val changed = a.extendedReports != extendedReports
                extendedReports = a.extendedReports
                keepAliveIntervalMs = when (a.powerProfile) {
                    PowerProfile.PERFORMANCE -> 3_000L
                    PowerProfile.BALANCED -> 4_000L
                    PowerProfile.BATTERY_SAVER -> 8_000L
                }
                if (changed && appRegistered) {
                    Timber.i("extendedReports toggled — re-registering HID app")
                    connectedDevice = null
                    mouseButtons.clear()
                    runCatching { hidDevice?.unregisterApp() }
                    registerApp()
                }
            }
        }
        scope.launch {
            settingsStore.keyboardSettings.collect { k ->
                keyTapDelayMs = k.sanitized().keyTapDelayMs.toLong()
            }
        }
    }

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
    fun isExtendedDescriptorActive(): Boolean = extendedReports

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
        stopped = false
        ensureBtStateReceiver()
        when {
            !permissionManager.hasAllBluetoothPermissions() -> {
                updateState(HidConnectionState.PermissionMissing)
                Timber.w("HidEngine.start: missing Bluetooth permissions")
                return
            }
            bluetoothAdapter == null -> {
                updateState(HidConnectionState.HidUnsupported("This device has no Bluetooth adapter."))
                return
            }
            !safeIsEnabled() -> {
                updateState(HidConnectionState.BluetoothDisabled)
                return
            }
        }
        if (hidDevice != null && appRegistered) {
            Timber.d("HidEngine.start: already registered")
            return
        }
        if (proxyRequested) return

        updateState(HidConnectionState.Initializing)
        proxyRequested = try {
            bluetoothAdapter?.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE) == true
        } catch (t: Throwable) {
            Timber.e(t, "getProfileProxy failed")
            false
        }
        if (!proxyRequested) {
            updateState(HidConnectionState.HidUnsupported("Android refused the HID Device profile on this phone."))
        }
    }

    /** Fully tears down registration + proxy. Called from service shutdown. */
    override fun stop() {
        stopped = true
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        connectWatchdog?.cancel()
        keepAlive?.cancel()
        mouseButtons.clear()
        if (btReceiverRegistered) {
            runCatching { context.unregisterReceiver(btStateReceiver) }
                .onFailure { Timber.w(it, "unregisterReceiver failed") }
            btReceiverRegistered = false
        }
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
        updateState(HidConnectionState.Idle)
        Timber.i("HidEngine stopped")
    }

    // ------------------------------------------------------------------
    // Connection control
    // ------------------------------------------------------------------

    /** User-initiated connect: resets backoff + flap counters. */
    fun connect(device: BluetoothDevice) {
        reconnectAttempts = 0
        flapCount = 0
        autoConnect(device)
    }

    /**
     * Round 8 — anti-flap connect core. Duplicate connects to the same host
     * inside the debounce window are ignored (the #1 cause of
     * connect/disconnect ping-pong is stacking connect() calls on a link
     * that is still settling), and the watchdog no longer force-tears the
     * stack on timeout.
     */
    private fun autoConnect(device: BluetoothDevice) {
        userInitiatedDisconnect = false
        val now = System.currentTimeMillis()
        val cur = _state.value
        if (cur is HidConnectionState.Connecting &&
            cur.device.address == device.address &&
            now - connectingSinceMs < CONNECT_DEBOUNCE_MS
        ) {
            Timber.d("connect(%s) debounced", device.address)
            return
        }
        if (connectedDevice?.address == device.address) {
            Timber.d("connect(%s): already connected", device.address)
            return
        }
        val hd = hidDevice
        if (hd == null || !appRegistered) {
            Timber.d("connect: engine not ready, queueing %s", device.address)
            pendingConnect = device
            start()
            return
        }
        connectingSinceMs = now
        updateState(HidConnectionState.Connecting(device.toRemote()))
        val ok = runCatching { hd.connect(device) }
            .onFailure { Timber.e(it, "connect() threw") }
            .getOrDefault(false)
        if (!ok) {
            updateState(HidConnectionState.Error("Could not start connection to ${device.safeName()}."))
            return
        }
        // Round 6 watchdog — reports the stall but never yanks the stack.
        connectWatchdog?.cancel()
        connectWatchdog = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (connectedDevice == null && _state.value is HidConnectionState.Connecting) {
                Timber.w("connect watchdog timed out for %s", device.address)
                _reconnectStatus.value = null
                updateState(
                    HidConnectionState.Error(
                        "Connection to ${device.safeName()} timed out. " +
                            "Make sure the host is paired and awake, then try again."
                    )
                )
            }
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
            updateState(HidConnectionState.Error("Device $address not found."))
            return
        }
        connect(device)
    }

    /** Round 7 — Quick Connect: re-target the most recent host. */
    override fun reconnectLast() {
        val target = lastHostDevice ?: return
        userInitiatedDisconnect = false
        connect(target)
    }

    /** User-initiated disconnect — suppresses auto-reconnect. */
    override fun disconnect() {
        userInitiatedDisconnect = true
        mouseButtons.clear()
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
                    delay(keyTapDelayMs)
                    report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboardRelease())
                }
                is HidAction.KeyDown ->
                    report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboard(action.modifiers, listOf(action.key)))
                is HidAction.KeyRelease ->
                    report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboardRelease())
                is HidAction.TypeText -> typeText(action.text)
                is HidAction.MouseMove ->
                    // C1 fix + V2 PART A pooled buffer: include held mouse buttons on every move!
                    report(
                        HidDescriptors.REPORT_ID_MOUSE,
                        ReportPool.mouseInto(mouseButtons.currentByte, action.dx, action.dy, 0)
                    )
                is HidAction.MouseClick -> {
                    report(
                        HidDescriptors.REPORT_ID_MOUSE,
                        HidReportBuilder.mouse(buttons = mouseButtons.withTransientPress(action.button))
                    )
                    delay(keyTapDelayMs)
                    report(
                        HidDescriptors.REPORT_ID_MOUSE,
                        HidReportBuilder.mouse(buttons = mouseButtons.currentByte)
                    )
                }
                is HidAction.MouseDoubleClick -> {
                    repeat(2) {
                        report(
                            HidDescriptors.REPORT_ID_MOUSE,
                            HidReportBuilder.mouse(buttons = mouseButtons.withTransientPress(action.button))
                        )
                        delay(keyTapDelayMs)
                        report(
                            HidDescriptors.REPORT_ID_MOUSE,
                            HidReportBuilder.mouse(buttons = mouseButtons.currentByte)
                        )
                        delay(30L)
                    }
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
                is HidAction.MediaTap -> {
                    report(HidDescriptors.REPORT_ID_CONSUMER, HidReportBuilder.consumer(action.usage))
                    delay(keyTapDelayMs)
                    report(HidDescriptors.REPORT_ID_CONSUMER, HidReportBuilder.consumerRelease())
                }
                is HidAction.SystemTap -> {
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
            delay(keyTapDelayMs)
            report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboardRelease())
            delay(keyTapDelayMs)
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
            mouseButtons.clear()
            appRegistered = false
            proxyRequested = false
            updateState(HidConnectionState.Idle)
            // Round 6 — the BT stack restarts the profile when the adapter is
            // toggled; re-arm instead of leaving the engine dead until app restart.
            if (!stopped) {
                scope.launch {
                    delay(1000)
                    if (!stopped && hidDevice == null && !proxyRequested) start()
                }
            }
        }
    }

    /**
     * Round 6 — adapter on/off receiver. Before this, toggling Bluetooth
     * (or the system restarting the BT stack) left AeroPad permanently
     * unregistered until the app was force-stopped.
     */
    private val btStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
            // Round 10 — wake triggers. A link that died while the phone was in
            // Doze used to stay dead until the user did something; unlocking or
            // simply turning the screen on is the strongest possible signal that
            // they want to use the remote again, so retry immediately.
            when (intent?.action) {
                android.content.Intent.ACTION_SCREEN_ON,
                android.content.Intent.ACTION_USER_PRESENT -> {
                    if (connectedDevice == null &&
                        !userInitiatedDisconnect &&
                        lastHostDevice != null
                    ) {
                        Timber.i("screen woke with no link — retrying immediately")
                        retryNow()
                    }
                    return
                }
            }
            when (intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_OFF -> {
                    Timber.w("Bluetooth adapter turned off")
                    connectWatchdog?.cancel()
                    connectedDevice = null
                    mouseButtons.clear()
                    appRegistered = false
                    updateState(HidConnectionState.BluetoothDisabled)
                }
                BluetoothAdapter.STATE_ON -> {
                    Timber.i("Bluetooth adapter turned on — re-initializing HID engine")
                    proxyRequested = false
                    start()
                    // Round 10 — resume the link we lost when the radio went off.
                    if (lastHostDevice != null && !userInitiatedDisconnect) {
                        retryNow()
                    }
                }
            }
        }
    }

    private fun ensureBtStateReceiver() {
        if (btReceiverRegistered) return
        val filter = android.content.IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            // Round 10 — wake triggers (neither needs a permission).
            addAction(android.content.Intent.ACTION_SCREEN_ON)
            addAction(android.content.Intent.ACTION_USER_PRESENT)
        }
        btReceiverRegistered = runCatching {
            context.registerReceiver(btStateReceiver, filter)
            true
        }.recoverCatching {
            // API 33+ explicit-flag variant for completeness.
            context.registerReceiver(btStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            true
        }.getOrDefault(false)
        if (!btReceiverRegistered) Timber.w("could not register BT state receiver")
    }

    private fun registerApp() {
        val hd = hidDevice ?: return
        // Round 9: default = the proven-stable 3.3.0 descriptor; the
        // extended one (touch digitizer + analog triggers) is opt-in.
        val descriptor = if (extendedReports) HidDescriptors.COMBINED else HidDescriptors.LEGACY
        val sdp = BluetoothHidDeviceAppSdpSettings(
            SDP_NAME, SDP_DESCRIPTION, SDP_PROVIDER,
            SUBCLASS_COMBO, descriptor
        )
        val ok = runCatching { hd.registerApp(sdp, null, null, hidExecutor, hidCallback) }
            .onFailure { Timber.e(it, "registerApp threw") }
            .getOrDefault(false)
        if (!ok) {
            updateState(
                HidConnectionState.HidUnsupported(
                    "Android refused HID registration. This phone or ROM may not support HID Device mode."
                )
            )
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Timber.i("onAppStatusChanged registered=%s", registered)
            appRegistered = registered
            if (!registered) {
                connectedDevice = null
                mouseButtons.clear()
                if (_state.value !is HidConnectionState.HidUnsupported) {
                    updateState(HidConnectionState.Idle)
                }
                // Round 10 — this used to be a dead end: the app lost its HID
                // registration (ROM cleanup, radio reset, host stack restart)
                // and nothing ever re-registered it. Now the loop takes over.
                if (!stopped && !userInitiatedDisconnect) {
                    startReconnectLoop(reason = "HID app unregistered")
                }
                return
            }
            updateState(HidConnectionState.Idle)
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
                    connectWatchdog?.cancel()
                    connectedDevice = device
                    mouseButtons.clear()
                    connectedAtMs = System.currentTimeMillis()
                    device?.let { startKeepAlive(it) }
                    lastHostDevice = device
                    reconnectAttempts = 0
                    flapGuard.onStableLink()
                    reconnectJob?.cancel()
                    reconnectJob = null
                    _reconnectStatus.value = null           // ADV S5
                    _reconnectNextAtMs.value = null
                    health.markConnected()                   // ADV S5
                    device?.let {
                        history.onConnected("BLUETOOTH", it.safeName())   // #41
                        updateState(HidConnectionState.Connected(it.toRemote()))
                    }
                }
                BluetoothProfile.STATE_CONNECTING ->
                    device?.let { updateState(HidConnectionState.Connecting(it.toRemote())) }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    keepAlive?.cancel()
                    mouseButtons.clear()
                    val wasConnected = connectedDevice != null
                    connectedDevice = null
                    updateState(HidConnectionState.Idle)
                    if (wasConnected) {
                        health.markDisconnected()   // ADV S5
                        history.onDisconnected("BLUETOOTH",
                            if (userInitiatedDisconnect) "user disconnect" else "link dropped")  // #41
                    }
                    if (wasConnected && !userInitiatedDisconnect) {
                        // Round 8 — break the connect/drop ping-pong.
                        // Round 10 — but never punish permanently: a flapping
                        // link now *rests* (60 s, doubling to a 15-min cap) and
                        // then resumes on its own, because "the host was mid-
                        // wake" is exactly the case the user wants fixed.
                        val heldMs = System.currentTimeMillis() - connectedAtMs
                        val rest = flapGuard.onDrop(heldMs, System.currentTimeMillis())
                        if (rest != null) {
                            Timber.w("link flapping — resting %dms before retrying", rest.restMs)
                            _reconnectStatus.value = null
                            updateState(
                                HidConnectionState.Error(
                                    "The link keeps dropping right after connecting. " +
                                        "AeroPad will keep retrying every " +
                                        "${rest.restMs / 1000}s — keep the host awake " +
                                        "and nearby. Re-pair if it keeps happening."
                                )
                            )
                            scope.launch {
                                delay(rest.restMs)
                                if (connectedDevice == null && !userInitiatedDisconnect) {
                                    updateState(HidConnectionState.Idle)
                                    startReconnectLoop(reason = "after flapping rest")
                                }
                            }
                        } else {
                            startReconnectLoop(reason = "link dropped")
                        }
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
            val hd = hidDevice ?: return
            runCatching {
                val payload: ByteArray = when (id.toInt()) {
                    HidDescriptors.REPORT_ID_KEYBOARD -> HidReportBuilder.keyboardRelease()
                    HidDescriptors.REPORT_ID_MOUSE -> HidReportBuilder.mouse(buttons = mouseButtons.currentByte)
                    HidDescriptors.REPORT_ID_CONSUMER -> HidReportBuilder.consumerRelease()
                    HidDescriptors.REPORT_ID_SYSTEM -> HidReportBuilder.systemRelease()
                    HidDescriptors.REPORT_ID_GAMEPAD ->
                        ReportPool.gamepadInto(GamepadSnapshot(), extendedReports)
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
            Timber.i("onVirtualCableUnplug %s", device?.address)
            userInitiatedDisconnect = true // host asked us to unplug: do not fight it
            connectedDevice = null
            mouseButtons.clear()
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
                    resting = flapGuard.isResting(System.currentTimeMillis())
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
                    val fast = reconnectPolicy.isFastPhase(reconnectAttempts)
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
        val hd = hidDevice
        if (hd == null) {
            // The proxy itself is gone — rebuild it from scratch.
            proxyRequested = false
            start()
            return
        }
        runCatching { hd.unregisterApp() }
        registerApp()
    }

    /**
     * User pressed "retry now" (or the screen turned on and we noticed the link
     * was gone). Clears any penalty, cancels the waiting loop and starts a fresh
     * fast-phase attempt immediately.
     */
    override fun retryNow() {
        userInitiatedDisconnect = false
        flapGuard.forgive()
        reconnectAttempts = 0
        reconnectJob?.cancel()
        reconnectJob = null
        _reconnectStatus.value = null
        _reconnectNextAtMs.value = null
        if (lastHostDevice == null) {
            // Nothing remembered yet — make sure the engine is at least ready.
            if (!appRegistered) {
                proxyRequested = if (hidDevice != null) proxyRequested else false
                start()
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
}
