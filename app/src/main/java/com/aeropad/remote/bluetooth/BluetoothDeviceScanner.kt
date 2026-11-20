package com.aeropad.remote.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.aeropad.remote.domain.NearbyScanner
import com.aeropad.remote.model.RemoteDevice
import com.aeropad.remote.permission.PermissionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classic Bluetooth discovery as a cold Flow.
 *
 * Collect → discovery starts and devices stream in.
 * Cancel  → receiver unregistered and discovery cancelled (no leaks).
 * All framework interactions are exception-guarded.
 */
@Singleton
@SuppressLint("MissingPermission") // permission checked before any call
class BluetoothDeviceScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionManager: PermissionManager
) : NearbyScanner {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    override fun scan(): Flow<RemoteDevice> = callbackFlow {
        if (!permissionManager.hasAllBluetoothPermissions() || adapter == null) {
            close()
            return@callbackFlow
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_DISCOVERY_FINISHED) {
                    close()
                    return
                }
                if (intent?.action != BluetoothDevice.ACTION_FOUND) return
                val device: BluetoothDevice? =
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val remote = device?.let {
                    runCatching {
                        RemoteDevice(
                            address = it.address,
                            name = it.name ?: "Unknown device",
                            isPaired = it.bondState == BluetoothDevice.BOND_BONDED
                        )
                    }.getOrNull()
                }
                remote?.let { trySend(it) }
            }
        }

        // If Android is already scanning, collect that scan rather than restarting it.
        // ACTION_FOUND/FINISHED are protected broadcasts from the Bluetooth UID.
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                },
                ContextCompat.RECEIVER_EXPORTED
            )
        }.onFailure {
            Timber.e(it, "registerReceiver failed")
            close(it); return@callbackFlow
        }

        var ownsDiscovery = false
        val started = runCatching {
            if (adapter.isDiscovering) true else adapter.startDiscovery().also { ownsDiscovery = it }
        }.getOrDefault(false)
        if (!started) close(IllegalStateException("Bluetooth discovery unavailable"))

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
            if (ownsDiscovery) runCatching { adapter.cancelDiscovery() }
            Timber.i("discovery stopped")
        }
    }
}
