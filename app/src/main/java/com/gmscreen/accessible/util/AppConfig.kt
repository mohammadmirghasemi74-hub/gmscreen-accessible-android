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
        private const val KEY_VIDEO_REC_FORMAT = "video_rec_format"
        private const val KEY_PARENTAL_PIN = "parental_pin"
        private const val KEY_SLEEP_TIMER = "sleep_timer"
        private const val KEY_POWER_MODE = "power_mode"
    }

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
        get() = prefs.getString(KEY_VOICE_WAKE_WORD, "جی ام اسکرین") ?: "جی ام اسکرین"
        set(value) = prefs.edit().putString(KEY_VOICE_WAKE_WORD, value).apply()

    var autoAnnounceChannel: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ANNOUNCE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ANNOUNCE, value).apply()

    var audioRecFormat: String
        get() = prefs.getString(KEY_AUDIO_REC_FORMAT, "mp3") ?: "mp3"
        set(value) = prefs.edit().putString(KEY_AUDIO_REC_FORMAT, value).apply()

    var videoRecFormat: String
        get() = prefs.getString(KEY_VIDEO_REC_FORMAT, "mp4") ?: "mp4"
        set(value) = prefs.edit().putString(KEY_VIDEO_REC_FORMAT, value).apply()

    var parentalPin: String
        get() = prefs.getString(KEY_PARENTAL_PIN, "0000") ?: "0000"
        set(value) = prefs.edit().putString(KEY_PARENTAL_PIN, value).apply()

    var sleepTimerMinutes: Int
        get() = prefs.getInt(KEY_SLEEP_TIMER, 0)
        set(value) = prefs.edit().putInt(KEY_SLEEP_TIMER, value).apply()

    var powerMode: Int
        get() = prefs.getInt(KEY_POWER_MODE, 1)
        set(value) = prefs.edit().putInt(KEY_POWER_MODE, value).apply()
}
