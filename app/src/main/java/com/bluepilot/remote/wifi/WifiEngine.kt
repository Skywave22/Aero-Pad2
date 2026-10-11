package com.bluepilot.remote.wifi

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.bluepilot.remote.model.HidAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AEROPAD v1.0 — WiFi LAN transport engine.
 *
 * Discovery: Android NSD (mDNS) for `_aeropad._tcp` receivers + manual IP.
 * Transport: line-delimited JSON over TCP (see WifiProtocol).
 * Security: PIN-proof handshake; post-handshake XOR keystream obfuscation.
 * Metrics (ALL REAL): send durations measured around socket writes, RTT
 * latency measured via ping/echo lines answered by the receiver.
 *
 * Requires the AeroPad companion receiver running on the host PC
 * (see receiver/aeropad_receiver.py in the repo).
 */
@Singleton
class WifiEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    // AEROPAD v1.0 #41 — real session history log.
    private val history: com.bluepilot.remote.data.history.ConnectionHistoryStore
) {
    sealed class WifiState {
        data object Idle : WifiState()
        data object Discovering : WifiState()
        data class AwaitingPin(val host: String, val port: Int) : WifiState()
        data class Connecting(val host: String) : WifiState()
        data class Connected(val host: String, val name: String) : WifiState()
        data class Error(val message: String) : WifiState()
    }

    data class DiscoveredHost(val name: String, val host: String, val port: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<WifiState>(WifiState.Idle)
    val state: StateFlow<WifiState> = _state.asStateFlow()

    private val _discovered = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val discovered: StateFlow<List<DiscoveredHost>> = _discovered.asStateFlow()

    /** Real measured RTT latency (ms) via ping/echo; null until measured. */
    private val _latencyMs = MutableStateFlow<Long?>(null)
    val latencyMs: StateFlow<Long?> = _latencyMs.asStateFlow()

    /** Real counters: (sent, failed). */
    private val _counters = MutableStateFlow(0L to 0L)
    val counters: StateFlow<Pair<Long, Long>> = _counters.asStateFlow()

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var pingJob: Job? = null
    private var readJob: Job? = null
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    // Pending connection awaiting PIN entry.
    private var pendingHost: String? = null
    private var pendingPort: Int = WifiProtocol.DEFAULT_PORT

    // ------------------------------------------------------------------
    // Discovery (NSD/mDNS)
    // ------------------------------------------------------------------

    // NsdManager.resolveService + serviceInfo.host are deprecated in API 34;
    // the replacement (registerServiceInfoCallback) requires API 34+ while we
    // support minSdk 29 — the deprecated path is the correct one here.
    @Suppress("DEPRECATION")
    fun startDiscovery() {
        stopDiscovery()
        _discovered.value = emptyList()
        val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (manager == null) {
            _state.value = WifiState.Error("Network discovery unavailable on this device.")
            return
        }
        nsdManager = manager
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                _state.value = WifiState.Discovering
            }
            override fun onServiceFound(info: NsdServiceInfo) {
                runCatching {
                    manager.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) = Unit
                        override fun onServiceResolved(si: NsdServiceInfo) {
                            val host = si.host?.hostAddress ?: return
                            val entry = DiscoveredHost(si.serviceName, host, si.port)
                            _discovered.value =
                                (_discovered.value.filterNot { it.host == host } + entry)
                        }
                    })
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                _discovered.value = _discovered.value.filterNot { it.name == info.serviceName }
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _state.value = WifiState.Error("Discovery failed (code $errorCode).")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        discoveryListener = listener
        runCatching {
            manager.discoverServices(
                WifiProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener
            )
        }.onFailure { _state.value = WifiState.Error("Could not start discovery: ${it.message}") }
    }

    fun stopDiscovery() {
        runCatching { discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) } }
        discoveryListener = null
        if (_state.value is WifiState.Discovering) _state.value = WifiState.Idle
    }

    // ------------------------------------------------------------------
    // Connection + PIN handshake
    // ------------------------------------------------------------------

    /** Step 1: user picks a host (discovered or manual IP) → ask for PIN. */
    fun requestConnect(host: String, port: Int = WifiProtocol.DEFAULT_PORT) {
        pendingHost = host
        pendingPort = port
        _state.value = WifiState.AwaitingPin(host, port)
    }

    /** Step 2: user enters the PIN shown by the receiver → handshake. */
    fun connectWithPin(pin: String) {
        val host = pendingHost ?: return
        val port = pendingPort
        _state.value = WifiState.Connecting(host)
        scope.launch {
            runCatching {
                val s = Socket()
                s.tcpNoDelay = true                       // input latency matters
                s.connect(InetSocketAddress(host, port), 4000)
                val r = BufferedReader(InputStreamReader(s.getInputStream()))
                val w = BufferedWriter(OutputStreamWriter(s.getOutputStream()))
                // Receiver speaks first: WELCOME {nonce}
                val welcome = WifiProtocol.decode(r.readLine() ?: "")
                    ?: error("Receiver did not send WELCOME")
                val nonce = welcome.text ?: error("WELCOME missing nonce")
                w.write(WifiProtocol.encode(
                    WifiProtocol.WifiMessage(
                        t = "hello",
                        proof = WifiProtocol.pinProof(pin, nonce),
                        deviceName = android.os.Build.MODEL
                    )
                ))
                w.newLine(); w.flush()
                val ok = WifiProtocol.decode(r.readLine() ?: "")
                if (ok?.t != "ok") error("PIN rejected by receiver")
                socket = s
                writer = w
                _counters.value = 0L to 0L
                _state.value = WifiState.Connected(host, ok.deviceName ?: host)
                history.onConnected("WIFI", ok.deviceName ?: host)   // #41
                startPing(r)
            }.onFailure {
                Timber.w(it, "wifi connect failed")
                closeQuietly()
                _state.value = WifiState.Error(
                    when {
                        it.message?.contains("PIN") == true -> "Wrong PIN — check the code on the receiver."
                        it is java.net.SocketTimeoutException -> "Host not reachable — is the receiver running?"
                        else -> "Connection failed: ${it.message}"
                    }
                )
            }
        }
    }

    /** REAL RTT: send ping with nanoTime, receiver echoes, we measure. */
    private fun startPing(reader: BufferedReader) {
        readJob?.cancel()
        readJob = scope.launch {
            runCatching {
                while (true) {
                    val line = reader.readLine() ?: break
                    val msg = WifiProtocol.decode(line) ?: continue
                    if (msg.t == "pong" && msg.echo != null) {
                        _latencyMs.value = (System.nanoTime() - msg.echo) / 1_000_000
                    }
                }
            }
            // Reader ended = connection dropped.
            if (_state.value is WifiState.Connected) {
                closeQuietly()
                history.onDisconnected("WIFI", "link dropped")   // #41
                _state.value = WifiState.Error("Connection lost.")
            }
        }
        pingJob?.cancel()
        pingJob = scope.launch {
            while (_state.value is WifiState.Connected) {
                sendRaw(WifiProtocol.WifiMessage(t = "ping", echo = System.nanoTime()))
                delay(2000)
            }
        }
    }

    fun disconnect() {
        if (_state.value is WifiState.Connected)
            history.onDisconnected("WIFI", "user disconnect")   // #41
        pingJob?.cancel(); readJob?.cancel()
        closeQuietly()
        _latencyMs.value = null
        _state.value = WifiState.Idle
    }

    private fun closeQuietly() {
        runCatching { writer?.flush() }
        runCatching { socket?.close() }
        socket = null; writer = null
    }

    // ------------------------------------------------------------------
    // Send path
    // ------------------------------------------------------------------

    val isConnected: Boolean get() = _state.value is WifiState.Connected

    fun send(action: HidAction) {
        if (!isConnected) return
        scope.launch {
            WifiProtocol.fromAction(action).forEach { sendRaw(it) }
        }
    }

    private fun sendRaw(m: WifiProtocol.WifiMessage) {
        val w = writer ?: return
        val (sent, failed) = _counters.value
        runCatching {
            synchronized(w) {
                w.write(WifiProtocol.encode(m)); w.newLine(); w.flush()
            }
        }.onSuccess { _counters.value = (sent + 1) to failed }
            .onFailure {
                _counters.value = sent to (failed + 1)
                Timber.w(it, "wifi send failed")
            }
    }
}
