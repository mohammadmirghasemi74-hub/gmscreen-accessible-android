package com.gmscreen.accessible.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

object AccessibilityHelper {

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        } catch (_: Exception) {
            toneGenerator = null
        }
    }

    /**
     * Announce text specifically for accessibility services (TalkBack).
     */
    fun announce(view: View?, message: String) {
        if (view == null || message.isBlank()) return
        view.post {
            view.announceForAccessibility(message)
        }
    }

    /**
     * Trigger light haptic click feedback (e.g. on remote key click).
     */
    fun vibrateClick(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(30)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Trigger stronger feedback (e.g. successful connection, channel change).
     */
    fun vibrateSuccess(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 50), -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 40, 60, 50), -1)
            }
        } catch (_: Exception) {}
    }

    /**
     * Play earcon beep for voice recognition start/finish or button feedback.
     */
    fun playSoundCue(toneType: Int = ToneGenerator.TONE_PROP_BEEP) {
        try {
            toneGenerator?.startTone(toneType, 120)
        } catch (_: Exception) {}
    }
}
