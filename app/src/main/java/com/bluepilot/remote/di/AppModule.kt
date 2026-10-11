package com.bluepilot.remote.di

import com.bluepilot.remote.bluetooth.BluetoothDeviceScanner
import com.bluepilot.remote.domain.HidController
import com.bluepilot.remote.domain.NearbyScanner
import com.bluepilot.remote.domain.PermissionChecker
import com.bluepilot.remote.hid.HidEngine
import com.bluepilot.remote.permission.PermissionManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings: domain interfaces → concrete implementations.
 * Tests provide fakes for the same interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindHidController(impl: HidEngine): HidController

    @Binds
    @Singleton
    abstract fun bindPermissionChecker(impl: PermissionManager): PermissionChecker

    // V2 M4 — host profiles seam (fake-able in unit tests).
    @Binds
    @Singleton
    abstract fun bindHostProfiles(
        impl: com.bluepilot.remote.data.hosts.HostProfileStore
    ): com.bluepilot.remote.data.hosts.HostProfiles

    @Binds
    @Singleton
    abstract fun bindNearbyScanner(impl: BluetoothDeviceScanner): NearbyScanner

    @Binds
    @Singleton
    abstract fun bindHaptics(
        impl: com.bluepilot.remote.haptics.HapticEngine
    ): com.bluepilot.remote.haptics.Haptics
}
