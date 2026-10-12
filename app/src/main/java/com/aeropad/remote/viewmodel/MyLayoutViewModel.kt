package com.aeropad.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeropad.remote.data.layout.LayoutRepository
import com.aeropad.remote.data.hosts.HostProfiles
import com.aeropad.remote.domain.HidController
import com.aeropad.remote.domain.MacroEngine
import com.aeropad.remote.domain.SettingsStore
import com.aeropad.remote.domain.WidgetInteractor
import com.aeropad.remote.domain.usecase.SendHidActionUseCase
import com.aeropad.remote.model.HidConnectionState
import com.aeropad.remote.model.MouseSettings
import com.aeropad.remote.model.widgets.LayoutSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Multi-device profiles: the "My Layout" surface. The layout shown is
 * whatever the CURRENTLY CONNECTED host has bound to it — connect the
 * laptop and its panel appears; switch to the TV and its panel appears.
 * Picking a layout while connected also remembers it for that host, so
 * you never rebuild controls per device.
 */
@HiltViewModel
class MyLayoutViewModel @Inject constructor(
    private val repository: LayoutRepository,
    private val hosts: HostProfiles,
    hid: HidController,
    sendAction: SendHidActionUseCase,
    settingsStore: SettingsStore,
    macroEngine: MacroEngine
) : ViewModel() {

    private val mouseSettings: StateFlow<MouseSettings> = settingsStore.mouseSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, MouseSettings())

    /** Live behavior for the rendered layout. */
    val interactor = WidgetInteractor(
        send = { sendAction(it) },
        mouseSettings = { mouseSettings.value },
        runMacro = { macroEngine.play(it) }
    )

    val profiles = repository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val connectedAddress: StateFlow<String?> = hid.state
        .map { (it as? HidConnectionState.Connected)?.device?.address }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _overrideId = MutableStateFlow<Long?>(null)

    private val _layout = MutableStateFlow<LayoutSpec?>(null)
    val layout: StateFlow<LayoutSpec?> = _layout.asStateFlow()

    init {
        viewModelScope.launch {
            combine(profiles, connectedAddress, _overrideId) { ps, addr, override ->
                val bound = addr?.let { a ->
                    ps.firstOrNull { it.address == a }?.layoutId?.takeIf { it > 0L }
                }
                override ?: bound
            }.collect { id ->
                _layout.value = id?.let { repository.byId(it)?.spec }
            }
        }
    }

    /** Show a layout now, and remember it for the connected host. */
    fun show(layoutId: Long) {
        _overrideId.value = layoutId
        connectedAddress.value?.let { hosts.bindLayout(it, layoutId) }
    }
}
