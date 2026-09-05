package com.aeropad.remote.di

import com.aeropad.remote.bluetooth.BluetoothDeviceScanner
import com.aeropad.remote.domain.HidController
import com.aeropad.remote.domain.NearbyScanner
import com.aeropad.remote.domain.PermissionChecker
import com.aeropad.remote.hid.HidEngine
import com.aeropad.remote.permission.PermissionManager
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
        impl: com.aeropad.remote.data.hosts.HostProfileStore
    ): com.aeropad.remote.data.hosts.HostProfiles

    @Binds
    @Singleton
    abstract fun bindConnectionStateSource(
        impl: com.aeropad.remote.domain.usecase.ConnectionStateHub
    ): com.aeropad.remote.domain.usecase.ConnectionStateSource

    @Binds
    @Singleton
    abstract fun bindNearbyScanner(impl: BluetoothDeviceScanner): NearbyScanner

    @Binds
    @Singleton
    abstract fun bindHaptics(
        impl: com.aeropad.remote.haptics.HapticEngine
    ): com.aeropad.remote.haptics.Haptics
}
