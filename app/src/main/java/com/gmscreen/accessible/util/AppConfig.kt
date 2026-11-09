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
}
