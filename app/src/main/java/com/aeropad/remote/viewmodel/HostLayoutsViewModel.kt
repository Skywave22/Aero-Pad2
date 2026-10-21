package com.aeropad.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeropad.remote.data.hosts.HostProfiles
import com.aeropad.remote.data.layout.LayoutRepository
import com.aeropad.remote.data.layout.LayoutProfile
import com.aeropad.remote.data.hosts.HostProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Multi-device profiles seam for the Devices screen (layout ⇄ host binding). */
@HiltViewModel
class HostLayoutsViewModel @Inject constructor(
    private val hosts: HostProfiles,
    repository: LayoutRepository,
    macroRepository: com.aeropad.remote.data.macros.MacroRepository
) : ViewModel() {

    val layouts: StateFlow<List<LayoutProfile>> = repository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hostProfiles: StateFlow<List<HostProfile>> = hosts.profiles

    fun bind(address: String, layoutId: Long) = hosts.bindLayout(address, layoutId)

    fun bindTuning(
        address: String,
        layoutId: Long,
        mouseSensitivity: Int,
        keyTapDelayMs: Int,
        gamepadMode: String,
        themeMode: String,
        scrollSpeed: Int = -1,
        hapticIntensity: String = "",
        onConnectMacroId: Long = 0,
        onDisconnectMacroId: Long = 0
    ) = hosts.bindHostTuning(
        address = address,
        layoutId = layoutId,
        mouseSensitivity = mouseSensitivity,
        keyTapDelayMs = keyTapDelayMs,
        gamepadMode = gamepadMode,
        themeMode = themeMode,
        scrollSpeed = scrollSpeed,
        hapticIntensity = hapticIntensity,
        onConnectMacroId = onConnectMacroId,
        onDisconnectMacroId = onDisconnectMacroId
    )

    /** Reset every per-host override for this address. */
    fun clearTuning(address: String) = hosts.clearTuning(address)

    /** F6 — macros that can be bound as connect/disconnect triggers. */
    val macros: StateFlow<List<com.aeropad.remote.data.macros.Macro>> =
        macroRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun profileFor(address: String): HostProfile? = hosts.profileFor(address)

    fun boundLayoutId(address: String): Long = hosts.layoutFor(address)
}
