package com.bluepilot.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluepilot.remote.domain.VoiceCommandParser
import com.bluepilot.remote.domain.usecase.ObserveConnectionUseCase
import com.bluepilot.remote.domain.usecase.SendHidActionUseCase
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.voice.VoiceInputManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ADV SECTION 4 — Voice input driver.
 *
 * Recognized speech → VoiceCommandParser → either HID actions (voice
 * shortcuts) or text typed to the host via HidAction.TypeText. With
 * confirm-before-send ON, text lands in [pendingText] for user approval.
 */
@HiltViewModel
class VoiceInputViewModel @Inject constructor(
    val voice: VoiceInputManager,
    observeConnection: ObserveConnectionUseCase,
    private val sendAction: SendHidActionUseCase
) : ViewModel() {

    val isConnected: StateFlow<Boolean> = observeConnection()
        .map { it.isConnected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ----- Options (session-scoped) -----
    private val _language = MutableStateFlow("")
    val language: StateFlow<String> = _language.asStateFlow()
    fun setLanguage(tag: String) { _language.value = tag }

    private val _continuous = MutableStateFlow(false)
    val continuous: StateFlow<Boolean> = _continuous.asStateFlow()
    fun setContinuous(v: Boolean) { _continuous.value = v }

    private val _preferOffline = MutableStateFlow(false)
    val preferOffline: StateFlow<Boolean> = _preferOffline.asStateFlow()
    fun setPreferOffline(v: Boolean) { _preferOffline.value = v }

    private val _confirmBeforeSend = MutableStateFlow(true)
    val confirmBeforeSend: StateFlow<Boolean> = _confirmBeforeSend.asStateFlow()
    fun setConfirmBeforeSend(v: Boolean) { _confirmBeforeSend.value = v }

    private val _commandsEnabled = MutableStateFlow(true)
    val commandsEnabled: StateFlow<Boolean> = _commandsEnabled.asStateFlow()
    fun setCommandsEnabled(v: Boolean) { _commandsEnabled.value = v }

    private val _manualPunctuation = MutableStateFlow(false)
    val manualPunctuation: StateFlow<Boolean> = _manualPunctuation.asStateFlow()
    fun setManualPunctuation(v: Boolean) { _manualPunctuation.value = v }

    // ----- Results -----
    /** Text awaiting user confirmation (confirm-before-send mode). */
    private val _pendingText = MutableStateFlow<String?>(null)
    val pendingText: StateFlow<String?> = _pendingText.asStateFlow()

    /** Last thing actually sent (text or command description). */
    private val _lastSent = MutableStateFlow("")
    val lastSent: StateFlow<String> = _lastSent.asStateFlow()

    init {
        // Route recognizer results through the parser automatically.
        viewModelScope.launch {
            voice.result.collect { text ->
                if (text.isNullOrBlank()) return@collect
                voice.consumeResult()
                handleRecognized(text)
                // Continuous mode: immediately listen again.
                if (_continuous.value) {
                    delay(250)
                    startListening()
                }
            }
        }
    }

    fun startListening() {
        voice.start(_language.value, _preferOffline.value, _continuous.value)
    }

    fun stopListening() {
        voice.stop()
    }

    private fun handleRecognized(text: String) {
        when (val parsed = VoiceCommandParser.parse(
            text, _commandsEnabled.value, _manualPunctuation.value
        )) {
            is VoiceCommandParser.Parsed.Actions -> {
                // Commands always fire immediately (that's their point).
                parsed.actions.forEach { sendAction(it) }
                _lastSent.value = parsed.description
            }
            is VoiceCommandParser.Parsed.Text -> {
                if (parsed.text.isBlank()) return
                if (_confirmBeforeSend.value) {
                    _pendingText.value = parsed.text
                } else {
                    typeNow(parsed.text)
                }
            }
        }
    }

    fun confirmPending() {
        _pendingText.value?.let { typeNow(it) }
        _pendingText.value = null
    }

    fun editPending(newText: String) { _pendingText.value = newText }

    fun discardPending() { _pendingText.value = null }

    private fun typeNow(text: String) {
        sendAction(HidAction.TypeText(text))
        _lastSent.value = text
    }

    override fun onCleared() {
        voice.stop()
        super.onCleared()
    }
}
