package com.bluepilot.remote.service

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
import com.bluepilot.remote.MainActivity
import com.bluepilot.remote.R
import com.bluepilot.remote.domain.HidController
import com.bluepilot.remote.model.HidConnectionState
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
        private const val CHANNEL_ID = "bluepilot_hid"
        private const val NOTIFICATION_ID = 42

        /** Starts the service (safe if already running). */
        fun start(context: Context) {
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
    @Inject lateinit var haptics: com.bluepilot.remote.haptics.Haptics

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // Foreground immediately — required within 5s of startForegroundService().
        startInForeground(buildNotification("Starting…"))
        // Keep the notification in sync with the connection state machine.
        // Battery: map to label first + distinctUntilChanged — the
        // NotificationManager is only touched when the visible text actually
        // changes (state spam like repeated Idle costs zero wakeups).
        hidController.state
            .map { stateLabel(it) }
            .distinctUntilChanged()
            .onEach { label -> updateNotification(label) }
            .launchIn(serviceScope)
        // SECTION 8 — connection event haptics: double pulse on connect,
        // heavy thud on error (distinctUntilChanged = one buzz per change).
        hidController.state
            .map { it::class.simpleName ?: "" }
            .distinctUntilChanged()
            .onEach { name ->
                when (name) {
                    "Connected" -> haptics.play(com.bluepilot.remote.model.gamepad.HapticPattern.DOUBLE_PULSE)
                    "Error", "HidUnsupported" -> haptics.play(com.bluepilot.remote.model.gamepad.HapticPattern.HEAVY_THUD)
                }
            }
            .launchIn(serviceScope)
        Timber.i("HidService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        hidController.start()
        return START_STICKY // restart if the system kills us; engine re-registers safely
    }

    override fun onDestroy() {
        serviceScope.cancel()
        Timber.i("HidService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------

    private fun startInForeground(notification: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { Timber.e(it, "startForeground failed") }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID, "HID connection",
            NotificationManager.IMPORTANCE_LOW // silent, no sound/vibration
        ).apply { description = "Shows the Bluetooth remote connection status" }
        runCatching { manager.createNotificationChannel(channel) }
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification(text)) }
    }

    private fun stateLabel(state: HidConnectionState): String = when (state) {
        is HidConnectionState.Connected -> "Connected to ${state.device.name}"
        is HidConnectionState.Connecting -> "Connecting to ${state.device.name}…"
        is HidConnectionState.Initializing -> "Initializing…"
        is HidConnectionState.Idle -> "Ready — waiting for a host"
        is HidConnectionState.BluetoothDisabled -> "Bluetooth is off"
        is HidConnectionState.PermissionMissing -> "Permissions needed"
        is HidConnectionState.HidUnsupported -> "HID not supported on this phone"
        is HidConnectionState.Error -> "Error: ${state.message}"
    }
}
