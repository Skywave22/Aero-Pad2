package com.aeropad.remote.viewmodel

import androidx.lifecycle.ViewModel
import com.aeropad.remote.voice.VoiceInputManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Voice-recognition lifecycle for the Wi-Fi TV screen; it never sends via Bluetooth HID. */
@HiltViewModel
class WifiTvRemoteViewModel @Inject constructor(
    val voice: VoiceInputManager
) : ViewModel() {
    fun startVoice() = voice.start(languageTag = "", preferOffline = true, continuous = false)
    fun stopVoice() = voice.stop()

    override fun onCleared() {
        voice.stop()
        super.onCleared()
    }
}
