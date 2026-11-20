package com.gmscreen.accessible.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

object TtsHelper {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    try {
                        val faLocale = Locale("fa")
                        val res = tts?.setLanguage(faLocale)
                        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts?.language = Locale.getDefault()
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun speak(text: String, interrupt: Boolean = true) {
        if (!isInitialized || text.isBlank()) return
        try {
            val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(text, queueMode, null, "gmscreen_${System.currentTimeMillis()}")
        } catch (_: Exception) {}
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
            tts = null
            isInitialized = false
        } catch (_: Exception) {}
    }
}
