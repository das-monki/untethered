package dev.labs910.voicecode.data.remote

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Manager for voice input using Android's SpeechRecognizer.
 * Equivalent to iOS VoiceInputManager using Apple Speech Framework.
 *
 * Key features:
 * - Real-time speech-to-text transcription
 * - Partial results during speech
 * - Audio level monitoring
 * - Error handling with retry capability
 */
class VoiceInputManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "VoiceInputManager"
    }

    private var speechRecognizer: SpeechRecognizer? = null

    // State
    private val _state = MutableStateFlow(VoiceInputState.IDLE)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    private val _partialTranscription = MutableStateFlow("")
    val partialTranscription: StateFlow<String> = _partialTranscription.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _error = MutableStateFlow<VoiceInputError?>(null)
    val error: StateFlow<VoiceInputError?> = _error.asStateFlow()

    // Callbacks
    private var onResult: ((String) -> Unit)? = null
    private var onPartialResult: ((String) -> Unit)? = null
    private var onError: ((VoiceInputError) -> Unit)? = null

    /**
     * Check if speech recognition is available on this device.
     */
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Initialize the speech recognizer.
     * Call this before starting recording.
     */
    fun initialize() {
        if (speechRecognizer != null) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createRecognitionListener())
        }
    }

    /**
     * Start recording and transcribing speech.
     *
     * @param onResult Callback when final transcription is ready
     * @param onPartialResult Callback for partial transcription updates
     * @param onError Callback for errors
     */
    fun startRecording(
        onResult: ((String) -> Unit)? = null,
        onPartialResult: ((String) -> Unit)? = null,
        onError: ((VoiceInputError) -> Unit)? = null
    ) {
        if (_state.value == VoiceInputState.RECORDING) {
            return
        }

        this.onResult = onResult
        this.onPartialResult = onPartialResult
        this.onError = onError

        // Reset state
        _transcription.value = ""
        _partialTranscription.value = ""
        _error.value = null

        // Create recognizer if needed
        if (speechRecognizer == null) {
            initialize()
        }

        val intent = createRecognizerIntent()
        speechRecognizer?.startListening(intent)

        _state.value = VoiceInputState.STARTING
    }

    /**
     * Stop recording.
     */
    fun stopRecording() {
        if (_state.value != VoiceInputState.RECORDING) {
            return
        }

        speechRecognizer?.stopListening()
        _state.value = VoiceInputState.PROCESSING
    }

    /**
     * Cancel recording without getting results.
     */
    fun cancelRecording() {
        speechRecognizer?.cancel()
        _state.value = VoiceInputState.IDLE
        _partialTranscription.value = ""
    }

    /**
     * Release resources.
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        _state.value = VoiceInputState.IDLE
    }

    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Enable on-device recognition if available (Android 11+)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = VoiceInputState.RECORDING
            }

            override fun onBeginningOfSpeech() {
                // Speech has started
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Normalize RMS to 0-1 range (RMS typically ranges from -2 to 10)
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _audioLevel.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Raw audio buffer - not used in basic implementation
            }

            override fun onEndOfSpeech() {
                _state.value = VoiceInputState.PROCESSING
            }

            override fun onError(errorCode: Int) {
                val voiceError = mapErrorCode(errorCode)
                _error.value = voiceError
                _state.value = VoiceInputState.ERROR
                onError?.invoke(voiceError)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcription = matches?.firstOrNull() ?: ""

                _transcription.value = transcription
                _partialTranscription.value = ""
                _state.value = VoiceInputState.IDLE
                _audioLevel.value = 0f

                onResult?.invoke(transcription)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull() ?: ""

                _partialTranscription.value = partial
                onPartialResult?.invoke(partial)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Additional events - not commonly used
            }
        }
    }

    private fun mapErrorCode(errorCode: Int): VoiceInputError {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> VoiceInputError.AUDIO_ERROR
            SpeechRecognizer.ERROR_CLIENT -> VoiceInputError.CLIENT_ERROR
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceInputError.PERMISSION_DENIED
            SpeechRecognizer.ERROR_NETWORK -> VoiceInputError.NETWORK_ERROR
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceInputError.NETWORK_TIMEOUT
            SpeechRecognizer.ERROR_NO_MATCH -> VoiceInputError.NO_MATCH
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceInputError.RECOGNIZER_BUSY
            SpeechRecognizer.ERROR_SERVER -> VoiceInputError.SERVER_ERROR
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceInputError.SPEECH_TIMEOUT
            else -> VoiceInputError.UNKNOWN
        }
    }
}

/**
 * Voice input state.
 */
enum class VoiceInputState {
    IDLE,
    STARTING,
    RECORDING,
    PROCESSING,
    ERROR
}

/**
 * Voice input error types.
 */
enum class VoiceInputError {
    AUDIO_ERROR,
    CLIENT_ERROR,
    PERMISSION_DENIED,
    NETWORK_ERROR,
    NETWORK_TIMEOUT,
    NO_MATCH,
    RECOGNIZER_BUSY,
    SERVER_ERROR,
    SPEECH_TIMEOUT,
    UNKNOWN;

    fun getMessage(): String = when (this) {
        AUDIO_ERROR -> "Audio recording error"
        CLIENT_ERROR -> "Client side error"
        PERMISSION_DENIED -> "Microphone permission denied"
        NETWORK_ERROR -> "Network error"
        NETWORK_TIMEOUT -> "Network timeout"
        NO_MATCH -> "No speech recognized"
        RECOGNIZER_BUSY -> "Speech recognizer busy"
        SERVER_ERROR -> "Server error"
        SPEECH_TIMEOUT -> "No speech detected"
        UNKNOWN -> "Unknown error"
    }

    fun isRetryable(): Boolean = when (this) {
        NETWORK_ERROR, NETWORK_TIMEOUT, SERVER_ERROR, RECOGNIZER_BUSY -> true
        else -> false
    }
}
