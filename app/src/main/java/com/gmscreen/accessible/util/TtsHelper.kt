package com.gmscreen.accessible.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

object TtsHelper {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingSpeeches = mutableListOf<Pair<String, Boolean>>()

    fun init(context: Context) {
        val app = context.applicationContext
        if (tts != null && isInitialized) return

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        isInitialized = false

        try {
            tts = TextToSpeech(app) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    try {
                        // Route TTS specifically to the Accessibility audio stream
                        val audioAttrs = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                        tts?.setAudioAttributes(audioAttrs)

                        val faLocale = Locale("fa")
                        val avail = tts?.isLanguageAvailable(faLocale)
                        if (avail != null && avail >= TextToSpeech.LANG_AVAILABLE) {
                            tts?.language = faLocale
                        }
                    } catch (_: Exception) {}
                    AppLogger.i("TtsHelper", "Accessibility-routed TTS initialized successfully")
                    synchronized(pendingSpeeches) {
                        for (item in pendingSpeeches) {
                            speak(item.first, item.second)
                        }
                        pendingSpeeches.clear()
                    }
                } else {
                    AppLogger.e("TtsHelper", "TTS initialization failed with status: $status")
                }
            }
        } catch (e: Exception) {
            AppLogger.e("TtsHelper", "Failed to create TextToSpeech instance: ${e.message}")
        }
    }

    fun speak(text: String, interrupt: Boolean = true) {
        if (text.isBlank()) return
        if (!isInitialized) {
            synchronized(pendingSpeeches) {
                if (interrupt) pendingSpeeches.clear()
                pendingSpeeches.add(Pair(text, interrupt))
            }
            return
        }
        try {
            val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ACCESSIBILITY)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            tts?.speak(text, queueMode, params, "gmscreen_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            AppLogger.e("TtsHelper", "TTS speak error: ${e.message}")
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
    }
}
