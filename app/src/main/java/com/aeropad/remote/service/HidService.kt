package com.aeropad.remote.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aeropad.remote.MainActivity
import com.aeropad.remote.R
import com.aeropad.remote.domain.HidController
import com.aeropad.remote.model.HidConnectionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service that keeps the HID link alive while the app is
 * backgrounded, with a live notification showing connection status.
 *
 * Start/stop is idempotent; the service never crashes on double-start,
 * missing notification permission, or engine failures.
 */
@AndroidEntryPoint
class HidService : Service() {

    companion object {
        private const val CHANNEL_ID = "aeropad_hid"
        private const val NOTIFICATION_ID = 42
        // Round 7 — Quick Connect actions straight from the notification.
        private const val ACTION_RECONNECT = "aeropad.action.RECONNECT"
        private const val ACTION_DISCONNECT = "aeropad.action.DISCONNECT"

        /** Starts the service (safe if already running; skips if Bluetooth runtime permissions are missing). */
        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hasConnect = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.BLUETOOTH_CONNECT
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasAdvertise = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.BLUETOOTH_ADVERTISE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasScan = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.BLUETOOTH_SCAN
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasConnect && !hasAdvertise && !hasScan) {
                    Timber.w("Skipping HidService start: Bluetooth runtime permissions not granted yet")
                    return
                }
            }
            runCatching {
                context.startForegroundService(Intent(context, HidService::class.java))
            }.onFailure { Timber.e(it, "HidService start failed") }
        }

        /** Stops the service (safe if not running). */
        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, HidService::class.java))
            }.onFailure { Timber.w(it, "HidService stop failed") }
        }
    }

    @Inject lateinit var hidController: HidController
    // F6 — connect/disconnect trigger macros (per host).
    @Inject lateinit var hostTriggers: com.aeropad.remote.domain.HostTriggerRunner
    @Inject lateinit var haptics: com.aeropad.remote.haptics.Haptics

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // Foreground immediately — required within 5s of startForegroundService().
        if (!startInForeground(buildNotification("Starting…"))) {
            return
        }
        // Keep the notification in sync with the connection state machine.
        // Battery: map to label first + distinctUntilChanged — the
        // NotificationManager is only touched when the visible text actually
        // changes (state spam like repeated Idle costs zero wakeups).
        hidController.state
            .onEach { st ->
                currentState = st
                updateNotification(stateLabel(st))
            }
            .launchIn(serviceScope)
        // SECTION 8 — connection event haptics: double pulse on connect,
        // heavy thud on error (distinctUntilChanged = one buzz per change).
        hidController.state
            .map { st ->
                when (st) {
                    is HidConnectionState.Connected -> "Connected"
                    is HidConnectionState.Error, is HidConnectionState.HidUnsupported -> "Error"
                    else -> "Other"
                }
            }
            .distinctUntilChanged()
            .onEach { kind ->
                when (kind) {
                    "Connected" -> haptics.play(com.aeropad.remote.model.gamepad.HapticPattern.DOUBLE_PULSE)
                    "Error" -> haptics.play(com.aeropad.remote.model.gamepad.HapticPattern.HEAVY_THUD)
                }
            }
            .launchIn(serviceScope)
        // F6 — the service is alive exactly as long as the HID link, which is
        // exactly when connect/disconnect triggers need to fire.
        hostTriggers.start()
        Timber.i("HidService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RECONNECT -> {
                hidController.start()
                hidController.reconnectLast()
            }
            ACTION_DISCONNECT -> hidController.disconnect()
            else -> hidController.start()
        }
        return START_STICKY // restart if the system kills us; engine re-registers safely
    }

    override fun onDestroy() {
        // Round 16 — the runner was started in onCreate and never stopped, so
        // connect/disconnect triggers kept firing after the service died. The
        // runner guards double-start, but the lifetime claim in its comment
        // ("alive exactly as long as the HID link") was only true one way.
        runCatching { hostTriggers.stop() }
        serviceScope.cancel()
        Timber.i("HidService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------

    private fun startInForeground(notification: Notification): Boolean {
        return runCatching {
            // minSdk is 29 (Q), so the typed overload is always available —
            // the old SDK_INT branch could never take its else path.
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
            true
        }.getOrElse { e ->
            Timber.e(e, "startForeground failed; stopping service gracefully")
            runCatching { stopSelf() }
            false
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        // Round 16 — these five strings already existed (translated into
        // Arabic, Spanish, Hindi, Indonesian and Urdu) and were never used:
        // the whole notification was hardcoded English. This is the first
        // screen a user sees outside the app, so it is worth localising.
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW // silent, no sound/vibration
        ).apply { description = getString(R.string.notif_channel_desc) }
        runCatching { manager.createNotificationChannel(channel) }
    }

    private var currentState: HidConnectionState = HidConnectionState.Idle

    private fun serviceAction(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code,
            Intent(this, HidService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        // Round 7 — Quick Connect: act without opening the app.
        if (currentState is HidConnectionState.Connected) {
            builder.addAction(
                0, getString(R.string.notif_action_stop), serviceAction(ACTION_DISCONNECT, 2)
            )
        } else {
            builder.addAction(
                0, getString(R.string.notif_action_connect), serviceAction(ACTION_RECONNECT, 1)
            )
        }
        return builder.build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification(text)) }
    }

    private fun stateLabel(state: HidConnectionState): String = when (state) {
        is HidConnectionState.Connected -> "Connected to ${state.device.name}"
        is HidConnectionState.Connecting -> "Connecting to ${state.device.name}…"
        is HidConnectionState.Initializing -> "Initializing…"
        is HidConnectionState.Idle -> getString(R.string.notif_text_idle)
        is HidConnectionState.BluetoothDisabled -> "Bluetooth is off"
        is HidConnectionState.PermissionMissing -> "Permissions needed"
        is HidConnectionState.HidUnsupported -> "HID not supported on this phone"
        is HidConnectionState.Error -> "Error: ${state.message}"
    }
}
