package com.bluepilot.remote.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.bluepilot.remote.domain.NearbyScanner
import com.bluepilot.remote.model.RemoteDevice
import com.bluepilot.remote.permission.PermissionManager
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

        runCatching {
            context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        }.onFailure {
            Timber.e(it, "registerReceiver failed")
            close(); return@callbackFlow
        }

        val started = runCatching {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
        }.getOrDefault(false)
        Timber.i("discovery started=%s", started)

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { adapter.cancelDiscovery() }
            Timber.i("discovery stopped")
        }
    }
}
