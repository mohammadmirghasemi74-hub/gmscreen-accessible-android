package com.gmscreen.accessible.util

import android.content.Context
import android.provider.Settings
import android.speech.tts.TextToSpeech
import java.util.Locale

object TtsHelper {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentEngine: String? = null

    fun init(context: Context) {
        val app = context.applicationContext
        val targetEngine = detectBestTtsEngine(app)
        if (tts != null && currentEngine == targetEngine && isInitialized) return

        shutdown()
        currentEngine = targetEngine

        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                try {
                    val faLocale = Locale("fa")
                    val avail = tts?.isLanguageAvailable(faLocale)
                    if (avail != null && avail >= TextToSpeech.LANG_AVAILABLE) {
                        tts?.language = faLocale
                    }
                } catch (_: Exception) {}
                AppLogger.i("TtsHelper", "TTS initialized successfully with engine: ${currentEngine ?: "system_default"}")
            } else {
                AppLogger.e("TtsHelper", "TTS initialization failed with status: $status")
            }
        }

        try {
            tts = if (!targetEngine.isNullOrBlank()) {
                TextToSpeech(app, listener, targetEngine)
            } else {
                TextToSpeech(app, listener)
            }
        } catch (e: Exception) {
            AppLogger.e("TtsHelper", "Failed to create TextToSpeech instance with engine $targetEngine: ${e.message}")
            try {
                tts = TextToSpeech(app, listener)
            } catch (_: Exception) {}
        }
    }

    fun getInstalledEngines(context: Context): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        try {
            val probe = TextToSpeech(context.applicationContext) {}
            val engines = probe.engines
            probe.shutdown()
            for (eng in engines) {
                result.add(Pair(eng.name, eng.label))
            }
        } catch (_: Exception) {}
        return result
    }

    private fun detectBestTtsEngine(context: Context): String? {
        val configEngine = try {
            com.gmscreen.accessible.GMScreenApplication.instance.appConfig.preferredTtsEngine
        } catch (_: Exception) { "" }

        if (configEngine.isNotBlank()) {
            return configEngine
        }

        // Check if user set a default synth in Android Secure settings
        val systemDefault = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH)
        } catch (_: Exception) { null }

        // Probe installed engines to find eSpeak if present
        var espeakEngine: String? = null
        try {
            val probeTts = TextToSpeech(context.applicationContext) {}
            val engines = probeTts.engines
            probeTts.shutdown()

            for (eng in engines) {
                val pkg = eng.name.lowercase()
                val label = eng.label.lowercase()
                if (pkg.contains("espeak") || label.contains("espeak")) {
                    espeakEngine = eng.name
                    break
                }
            }
        } catch (_: Exception) {}

        // If eSpeak is installed on device, prioritize it so background speech matches TalkBack eSpeak!
        if (espeakEngine != null) {
            return espeakEngine
        }

        return systemDefault
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
            currentEngine = null
        } catch (_: Exception) {}
    }
}
