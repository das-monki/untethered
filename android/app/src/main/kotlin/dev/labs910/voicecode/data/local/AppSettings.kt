package dev.labs910.voicecode.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App settings storage using SharedPreferences.
 * Equivalent to iOS AppSettings.
 */
class AppSettings(context: Context) {

    companion object {
        private const val PREFS_NAME = "voice_code_settings"

        // Server settings
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_SERVER_PORT = "server_port"
        private const val DEFAULT_SERVER_URL = "localhost"
        private const val DEFAULT_SERVER_PORT = "9999"

        // Voice settings
        private const val KEY_SELECTED_VOICE = "selected_voice"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_PITCH = "pitch"
        private const val KEY_SILENT_MODE_RESPECTED = "silent_mode_respected"
        private const val KEY_AUTO_SPEAK_RESPONSES = "auto_speak_responses"
        private const val DEFAULT_SPEECH_RATE = 1.0f
        private const val DEFAULT_PITCH = 1.0f

        // Notification settings
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"

        // Message settings
        private const val KEY_MAX_MESSAGE_SIZE_KB = "max_message_size_kb"
        private const val DEFAULT_MAX_MESSAGE_SIZE_KB = 200

        // Resource settings
        private const val KEY_STORAGE_LOCATION = "storage_location"
        private const val DEFAULT_STORAGE_LOCATION = "~/Downloads"

        // Queue settings
        private const val KEY_REGULAR_QUEUE_ENABLED = "regular_queue_enabled"
        private const val KEY_PRIORITY_QUEUE_ENABLED = "priority_queue_enabled"

        // Display settings
        private const val KEY_AUTO_SCROLL = "auto_scroll"
        private const val KEY_RECENT_SESSIONS_LIMIT = "recent_sessions_limit"
        private const val DEFAULT_RECENT_SESSIONS_LIMIT = 5
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ==========================================================================
    // MARK: - Server Settings
    // ==========================================================================

    private val _serverUrl = MutableStateFlow(prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    fun setServerUrl(url: String) {
        _serverUrl.value = url
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }

    private val _serverPort = MutableStateFlow(prefs.getString(KEY_SERVER_PORT, DEFAULT_SERVER_PORT) ?: DEFAULT_SERVER_PORT)
    val serverPort: StateFlow<String> = _serverPort.asStateFlow()

    fun setServerPort(port: String) {
        _serverPort.value = port
        prefs.edit().putString(KEY_SERVER_PORT, port).apply()
    }

    val websocketUrl: String
        get() = "ws://${_serverUrl.value}:${_serverPort.value}/ws"

    // ==========================================================================
    // MARK: - Voice Settings
    // ==========================================================================

    private val _selectedVoice = MutableStateFlow(prefs.getString(KEY_SELECTED_VOICE, null))
    val selectedVoice: StateFlow<String?> = _selectedVoice.asStateFlow()

    fun setSelectedVoice(voice: String?) {
        _selectedVoice.value = voice
        if (voice != null) {
            prefs.edit().putString(KEY_SELECTED_VOICE, voice).apply()
        } else {
            prefs.edit().remove(KEY_SELECTED_VOICE).apply()
        }
    }

    private val _speechRate = MutableStateFlow(prefs.getFloat(KEY_SPEECH_RATE, DEFAULT_SPEECH_RATE))
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        _speechRate.value = clamped
        prefs.edit().putFloat(KEY_SPEECH_RATE, clamped).apply()
    }

    private val _pitch = MutableStateFlow(prefs.getFloat(KEY_PITCH, DEFAULT_PITCH))
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        _pitch.value = clamped
        prefs.edit().putFloat(KEY_PITCH, clamped).apply()
    }

    private val _silentModeRespected = MutableStateFlow(prefs.getBoolean(KEY_SILENT_MODE_RESPECTED, true))
    val silentModeRespected: StateFlow<Boolean> = _silentModeRespected.asStateFlow()

    fun setSilentModeRespected(respected: Boolean) {
        _silentModeRespected.value = respected
        prefs.edit().putBoolean(KEY_SILENT_MODE_RESPECTED, respected).apply()
    }

    private val _autoSpeakResponses = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SPEAK_RESPONSES, false))
    val autoSpeakResponses: StateFlow<Boolean> = _autoSpeakResponses.asStateFlow()

    fun setAutoSpeakResponses(enabled: Boolean) {
        _autoSpeakResponses.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_SPEAK_RESPONSES, enabled).apply()
    }

    // ==========================================================================
    // MARK: - Notification Settings
    // ==========================================================================

    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    // ==========================================================================
    // MARK: - Message Settings
    // ==========================================================================

    private val _maxMessageSizeKb = MutableStateFlow(prefs.getInt(KEY_MAX_MESSAGE_SIZE_KB, DEFAULT_MAX_MESSAGE_SIZE_KB))
    val maxMessageSizeKb: StateFlow<Int> = _maxMessageSizeKb.asStateFlow()

    fun setMaxMessageSizeKb(sizeKb: Int) {
        val clamped = sizeKb.coerceIn(50, 250)
        _maxMessageSizeKb.value = clamped
        prefs.edit().putInt(KEY_MAX_MESSAGE_SIZE_KB, clamped).apply()
    }

    // ==========================================================================
    // MARK: - Resource Settings
    // ==========================================================================

    private val _storageLocation = MutableStateFlow(prefs.getString(KEY_STORAGE_LOCATION, DEFAULT_STORAGE_LOCATION) ?: DEFAULT_STORAGE_LOCATION)
    val storageLocation: StateFlow<String> = _storageLocation.asStateFlow()

    fun setStorageLocation(location: String) {
        _storageLocation.value = location
        prefs.edit().putString(KEY_STORAGE_LOCATION, location).apply()
    }

    // ==========================================================================
    // MARK: - Queue Settings
    // ==========================================================================

    private val _regularQueueEnabled = MutableStateFlow(prefs.getBoolean(KEY_REGULAR_QUEUE_ENABLED, false))
    val regularQueueEnabled: StateFlow<Boolean> = _regularQueueEnabled.asStateFlow()

    fun setRegularQueueEnabled(enabled: Boolean) {
        _regularQueueEnabled.value = enabled
        prefs.edit().putBoolean(KEY_REGULAR_QUEUE_ENABLED, enabled).apply()
    }

    private val _priorityQueueEnabled = MutableStateFlow(prefs.getBoolean(KEY_PRIORITY_QUEUE_ENABLED, false))
    val priorityQueueEnabled: StateFlow<Boolean> = _priorityQueueEnabled.asStateFlow()

    fun setPriorityQueueEnabled(enabled: Boolean) {
        _priorityQueueEnabled.value = enabled
        prefs.edit().putBoolean(KEY_PRIORITY_QUEUE_ENABLED, enabled).apply()
    }

    // ==========================================================================
    // MARK: - Display Settings
    // ==========================================================================

    private val _autoScroll = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SCROLL, true))
    val autoScroll: StateFlow<Boolean> = _autoScroll.asStateFlow()

    fun setAutoScroll(enabled: Boolean) {
        _autoScroll.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_SCROLL, enabled).apply()
    }

    private val _recentSessionsLimit = MutableStateFlow(prefs.getInt(KEY_RECENT_SESSIONS_LIMIT, DEFAULT_RECENT_SESSIONS_LIMIT))
    val recentSessionsLimit: StateFlow<Int> = _recentSessionsLimit.asStateFlow()

    fun setRecentSessionsLimit(limit: Int) {
        val clamped = limit.coerceIn(1, 20)
        _recentSessionsLimit.value = clamped
        prefs.edit().putInt(KEY_RECENT_SESSIONS_LIMIT, clamped).apply()
    }

    // ==========================================================================
    // MARK: - Reset
    // ==========================================================================

    fun resetToDefaults() {
        prefs.edit().clear().apply()

        _serverUrl.value = DEFAULT_SERVER_URL
        _serverPort.value = DEFAULT_SERVER_PORT
        _selectedVoice.value = null
        _speechRate.value = DEFAULT_SPEECH_RATE
        _pitch.value = DEFAULT_PITCH
        _silentModeRespected.value = true
        _autoSpeakResponses.value = false
        _notificationsEnabled.value = true
        _maxMessageSizeKb.value = DEFAULT_MAX_MESSAGE_SIZE_KB
        _storageLocation.value = DEFAULT_STORAGE_LOCATION
        _regularQueueEnabled.value = false
        _priorityQueueEnabled.value = false
        _autoScroll.value = true
        _recentSessionsLimit.value = DEFAULT_RECENT_SESSIONS_LIMIT
    }
}
