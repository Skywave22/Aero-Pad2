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
    repository: LayoutRepository
) : ViewModel() {

    val layouts: StateFlow<List<LayoutProfile>> = repository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hostProfiles: StateFlow<List<HostProfile>> = hosts.profiles

    fun bind(address: String, layoutId: Long) = hosts.bindLayout(address, layoutId)

    fun boundLayoutId(address: String): Long = hosts.layoutFor(address)
}
