package com.bluepilot.remote.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ADV SECTION 4 — SpeechRecognizer wrapper.
 *
 * All framework calls are wrapped (recognizer availability differs wildly
 * across OEMs). Exposes plain StateFlows the ViewModel/UI can observe:
 * state, partial text, final text, real RMS level (for the waveform) and
 * typed error reasons with user-actionable messages.
 *
 * Offline: on Android 12+ we set EXTRA_PREFER_OFFLINE when requested —
 * on-device recognition is used where the device supports it.
 */
@Singleton
class VoiceInputManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    enum class State { IDLE, STARTING, LISTENING, PROCESSING }

    data class VoiceError(val message: String, val canRetry: Boolean)

    private var recognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Live partial recognition (updates while speaking). */
    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    /** Final result of the last utterance; consume with [consumeResult]. */
    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result.asStateFlow()

    /** REAL microphone RMS level in dB from the recognizer (-2..10 typical). */
    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _error = MutableStateFlow<VoiceError?>(null)
    val error: StateFlow<VoiceError?> = _error.asStateFlow()

    val isAvailable: Boolean
        get() = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    val supportsOnDevice: Boolean
        get() = Build.VERSION.SDK_INT >= 31 &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

    fun consumeResult() { _result.value = null }
    fun consumeError() { _error.value = null }

    /**
     * Start listening. [languageTag] "" = device default. [preferOffline]
     * requests on-device recognition (API 31+). [continuous] keeps the
     * session open for longer dictation via generous silence timeouts.
     */
    fun start(languageTag: String, preferOffline: Boolean, continuous: Boolean) {
        stop()
        if (!isAvailable) {
            _error.value = VoiceError(
                "Speech recognition is not available on this device (no recognizer service installed).",
                canRetry = false
            )
            return
        }
        _state.value = State.STARTING
        _partial.value = ""
        _error.value = null

        val ok = runCatching {
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            r.setRecognitionListener(listener)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                if (languageTag.isNotBlank()) {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                }
                if (preferOffline && Build.VERSION.SDK_INT >= 31) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
                if (continuous) {
                    // Longer silence windows ≈ continuous dictation feel.
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                }
            }
            r.startListening(intent)
            recognizer = r
        }.onFailure {
            Timber.e(it, "SpeechRecognizer start failed")
            _state.value = State.IDLE
            _error.value = VoiceError("Could not start the recognizer: ${it.message}", canRetry = true)
        }.isSuccess
        if (!ok) recognizer = null
    }

    fun stop() {
        runCatching {
            recognizer?.stopListening()
            recognizer?.destroy()
        }
        recognizer = null
        if (_state.value != State.IDLE) _state.value = State.IDLE
        _rmsDb.value = 0f
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { _state.value = State.LISTENING }
        override fun onBeginningOfSpeech() { _state.value = State.LISTENING }
        override fun onRmsChanged(rmsdB: Float) { _rmsDb.value = rmsdB }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() { _state.value = State.PROCESSING }

        override fun onError(error: Int) {
            _state.value = State.IDLE
            _rmsDb.value = 0f
            val (msg, retry) = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "No speech detected — try again closer to the microphone." to true
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "Network error — check internet, or enable offline recognition in the options." to true
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "Microphone permission denied — allow it in Settings to use voice input." to false
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    "Recognizer busy — wait a moment and retry." to true
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                13 /* ERROR_LANGUAGE_UNAVAILABLE (API 33) */ ->
                    "Selected language not supported by this device's recognizer." to false
                else -> "Recognition error ($error) — retry." to true
            }
            _error.value = VoiceError(msg, retry)
        }

        override fun onResults(results: Bundle?) {
            _state.value = State.IDLE
            _rmsDb.value = 0f
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _partial.value = ""
            if (text.isNotBlank()) _result.value = text
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) _partial.value = text
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
