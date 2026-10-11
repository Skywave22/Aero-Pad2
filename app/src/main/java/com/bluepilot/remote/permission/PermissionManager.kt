package com.bluepilot.remote.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for Bluetooth-related runtime permissions.
 *
 * Android 12+ (API 31+): BLUETOOTH_CONNECT / SCAN / ADVERTISE (runtime).
 * Android 10–11 (API 29–30): legacy BLUETOOTH/ADMIN are install-time,
 *   but discovery needs ACCESS_FINE_LOCATION (runtime).
 * Android 13+ (API 33+): POST_NOTIFICATIONS for the foreground service banner.
 */
@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) : com.bluepilot.remote.domain.PermissionChecker {

    /** Runtime permissions required for the HID engine to operate at all. */
    override fun requiredBluetoothPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** Optional extras we should ask for but can run without. */
    override fun optionalPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }

    fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** True when every required Bluetooth permission is granted. */
    override fun hasAllBluetoothPermissions(): Boolean =
        requiredBluetoothPermissions().all(::isGranted)

    /** Permissions still missing (for the request launcher). */
    override fun missingPermissions(): List<String> =
        (requiredBluetoothPermissions() + optionalPermissions()).filterNot(::isGranted)
}
