package com.gmscreen.accessible.util

import android.content.Context
import android.content.SharedPreferences

class AppConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "gmscreen_accessible_prefs"
        private const val KEY_LAST_IP = "last_connected_ip"
        private const val KEY_KEEP_GLOBAL_NUM = "keep_global_channel_num_on_filter"
        private const val KEY_HAPTIC_ENABLED = "haptic_feedback_enabled"
        private const val KEY_SOUND_ENABLED = "sound_cues_enabled"
        private const val KEY_VOICE_ALWAYS_LISTENING = "voice_always_listening"
        private const val KEY_VOICE_WAKE_WORD = "voice_wake_word"
        private const val KEY_AUTO_ANNOUNCE = "auto_announce_channel"
        private const val KEY_AUDIO_REC_FORMAT = "audio_rec_format"
        private const val KEY_AUDIO_REC_BITRATE = "audio_rec_bitrate"
        private const val KEY_AUDIO_REC_SAMPLERATE = "audio_rec_samplerate"
        private const val KEY_AUDIO_REC_CHANNELS = "audio_rec_channels"
        private const val KEY_VIDEO_REC_FORMAT = "video_rec_format"
        private const val KEY_VIDEO_REC_QUALITY = "video_rec_quality"
        private const val KEY_REC_NAMING_PATTERN = "rec_naming_pattern"
        private const val KEY_AUTO_ANNOUNCE_EPG = "auto_announce_epg"
        private const val KEY_EPG_ANNOUNCE_DETAIL = "epg_announce_detail"
        private const val KEY_SHOW_RECORDINGS_IN_CHANNELS = "show_recordings_in_channels"
        private const val KEY_PARENTAL_PIN = "parental_pin"
        private const val KEY_SLEEP_TIMER = "sleep_timer"
        private const val KEY_POWER_MODE = "power_mode"
        private const val KEY_VIDEO_LANDSCAPE = "video_landscape_orientation"
        private const val KEY_CUSTOM_REC_DIR = "custom_rec_dir"
        private const val KEY_SHOW_JUMP_FIRST = "show_jump_to_first_channel"
        private const val KEY_SHOW_JUMP_LAST = "show_jump_to_last_channel"
        private const val KEY_SCREEN_LOCK = "screen_lock"
        private const val KEY_SETTINGS_LOCK = "settings_lock"
        private const val KEY_EDIT_CHANNEL_LOCK = "edit_channel_lock"
        private const val KEY_INSTALLATION_LOCK = "installation_lock"
        private const val KEY_AGE_RATING = "age_rating"
        private const val KEY_SEPARATE_AUDIO_BLUETOOTH = "separate_audio_bluetooth"
        private const val KEY_REC_COUNTER = "recording_counter"
        private const val KEY_LOG_ONLY_ERRORS = "log_only_errors"
        private const val KEY_BACKUP_METHOD = "backup_method"
    }

    var backupMethod: String
        get() = prefs.getString(KEY_BACKUP_METHOD, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_BACKUP_METHOD, value).apply()

    var lastConnectedIp: String
        get() = prefs.getString(KEY_LAST_IP, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_IP, value).apply()

    var keepGlobalChannelNum: Boolean
        get() = prefs.getBoolean(KEY_KEEP_GLOBAL_NUM, false)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_GLOBAL_NUM, value).apply()

    var hapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, value).apply()

    var soundCuesEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var voiceAlwaysListening: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ALWAYS_LISTENING, false)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ALWAYS_LISTENING, value).apply()

    var voiceWakeWord: String
        get() = prefs.getString(KEY_VOICE_WAKE_WORD, "های رسیور") ?: "های رسیور"
        set(value) = prefs.edit().putString(KEY_VOICE_WAKE_WORD, value).apply()

    var autoAnnounceChannel: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ANNOUNCE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ANNOUNCE, value).apply()

    var showRecordingsInChannels: Boolean
        get() = prefs.getBoolean(KEY_SHOW_RECORDINGS_IN_CHANNELS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_RECORDINGS_IN_CHANNELS, value).apply()

    var audioRecFormat: String
        get() = prefs.getString(KEY_AUDIO_REC_FORMAT, "mp3") ?: "mp3"
        set(value) = prefs.edit().putString(KEY_AUDIO_REC_FORMAT, value).apply()

    var audioRecBitrate: String
        get() = prefs.getString(KEY_AUDIO_REC_BITRATE, "128k") ?: "128k"
        set(value) = prefs.edit().putString(KEY_AUDIO_REC_BITRATE, value).apply()

    var audioRecSampleRate: String
        get() = prefs.getString(KEY_AUDIO_REC_SAMPLERATE, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_AUDIO_REC_SAMPLERATE, value).apply()

    var audioRecChannels: String
        get() = prefs.getString(KEY_AUDIO_REC_CHANNELS, "stereo") ?: "stereo"
        set(value) = prefs.edit().putString(KEY_AUDIO_REC_CHANNELS, value).apply()

    var videoRecFormat: String
        get() = prefs.getString(KEY_VIDEO_REC_FORMAT, "mp4") ?: "mp4"
        set(value) = prefs.edit().putString(KEY_VIDEO_REC_FORMAT, value).apply()

    var videoRecQuality: String
        get() = prefs.getString(KEY_VIDEO_REC_QUALITY, "copy") ?: "copy"
        set(value) = prefs.edit().putString(KEY_VIDEO_REC_QUALITY, value).apply()

    var recNamingPattern: String
        get() = prefs.getString(KEY_REC_NAMING_PATTERN, "jalali_numeric") ?: "jalali_numeric"
        set(value) = prefs.edit().putString(KEY_REC_NAMING_PATTERN, value).apply()

    var autoAnnounceEpg: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ANNOUNCE_EPG, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ANNOUNCE_EPG, value).apply()

    var epgAnnounceDetail: Int
        get() = prefs.getInt(KEY_EPG_ANNOUNCE_DETAIL, 0)
        set(value) = prefs.edit().putInt(KEY_EPG_ANNOUNCE_DETAIL, value).apply()

    var parentalPin: String
        get() = prefs.getString(KEY_PARENTAL_PIN, "0000") ?: "0000"
        set(value) = prefs.edit().putString(KEY_PARENTAL_PIN, value).apply()

    var sleepTimerMinutes: Int
        get() = prefs.getInt(KEY_SLEEP_TIMER, 0)
        set(value) = prefs.edit().putInt(KEY_SLEEP_TIMER, value).apply()

    var powerMode: Int
        get() = prefs.getInt(KEY_POWER_MODE, 1)
        set(value) = prefs.edit().putInt(KEY_POWER_MODE, value).apply()

    var videoLandscapeOrientation: Boolean
        get() = prefs.getBoolean(KEY_VIDEO_LANDSCAPE, false)
        set(value) = prefs.edit().putBoolean(KEY_VIDEO_LANDSCAPE, value).apply()

    var customRecordingsDir: String
        get() = prefs.getString(KEY_CUSTOM_REC_DIR, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_REC_DIR, value).apply()

    var showJumpToFirstChannel: Boolean
        get() = prefs.getBoolean(KEY_SHOW_JUMP_FIRST, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_JUMP_FIRST, value).apply()

    var showJumpToLastChannel: Boolean
        get() = prefs.getBoolean(KEY_SHOW_JUMP_LAST, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_JUMP_LAST, value).apply()

    var screenLock: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_SCREEN_LOCK, value).apply()

    var settingsLock: Boolean
        get() = prefs.getBoolean(KEY_SETTINGS_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_SETTINGS_LOCK, value).apply()

    var editChannelLock: Boolean
        get() = prefs.getBoolean(KEY_EDIT_CHANNEL_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_EDIT_CHANNEL_LOCK, value).apply()

    var installationLock: Boolean
        get() = prefs.getBoolean(KEY_INSTALLATION_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_INSTALLATION_LOCK, value).apply()

    var ageRating: Int
        get() = prefs.getInt(KEY_AGE_RATING, 0)
        set(value) = prefs.edit().putInt(KEY_AGE_RATING, value).apply()

    var separateAudioToBluetooth: Boolean
        get() = prefs.getBoolean(KEY_SEPARATE_AUDIO_BLUETOOTH, false)
        set(value) = prefs.edit().putBoolean(KEY_SEPARATE_AUDIO_BLUETOOTH, value).apply()

    var recordingCounter: Int
        get() = prefs.getInt(KEY_REC_COUNTER, 0)
        set(value) = prefs.edit().putInt(KEY_REC_COUNTER, value).apply()

    var logOnlyErrors: Boolean
        get() = prefs.getBoolean(KEY_LOG_ONLY_ERRORS, true)
        set(value) = prefs.edit().putBoolean(KEY_LOG_ONLY_ERRORS, value).apply()

    fun getNextRecordingNumber(): Int {
        val next = recordingCounter + 1
        recordingCounter = next
        return next
    }

    fun resetRecordingCounter() {
        recordingCounter = 0
    }
}
