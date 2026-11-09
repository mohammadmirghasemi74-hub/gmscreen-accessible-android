package com.gmscreen.accessible.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceController(
    private val context: Context,
    private val listener: VoiceListener
) : RecognitionListener {

    interface VoiceListener {
        fun onReady()
        fun onListening()
        fun onResults(recognizedText: String)
        fun onError(errorMessage: String)
        fun onWakeWordTriggered(remainingCommand: String)
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false
    private var isWakeWordActive: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "VoiceController"
        val WAKE_WORDS = listOf("جی ام اسکرین", "جی ام", "رسیور", "دستگاه", "screen", "stb")
    }

    fun setWakeWordMode(enabled: Boolean) {
        isWakeWordActive = enabled
        if (enabled) {
            startListening()
        } else {
            stopListening()
        }
    }

    fun isWakeWordEnabled(): Boolean = isWakeWordActive

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onError("سرویس تشخیص گفتار گوگل در این دستگاه در دسترس نیست")
            return
        }

        stopRecognizerOnly()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Tuned specifically for phone microphones
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            listener.onError("خطا در راه‌اندازی ضبط صدا: ${e.message}")
        }
    }

    private fun stopRecognizerOnly() {
        try {
            isListening = false
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
    }

    fun stopListening() {
        isWakeWordActive = false
        stopRecognizerOnly()
    }

    fun isCurrentlyListening(): Boolean = isListening

    override fun onReadyForSpeech(params: Bundle?) {
        listener.onReady()
    }

    override fun onBeginningOfSpeech() {
        listener.onListening()
    }

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        isListening = false
    }

    override fun onError(error: Int) {
        isListening = false
        val msg = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "خطای ضبط صدا"
            SpeechRecognizer.ERROR_CLIENT -> "خطای کلاینت تشخیص گفتار"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "مجوز دسترسی به میکروفون داده نشده است"
            SpeechRecognizer.ERROR_NETWORK -> "خطای شبکه در ارتباط با سرور گفتار"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "زمان اتصال به سرور گفتار به پایان رسید"
            SpeechRecognizer.ERROR_NO_MATCH -> "صدایی شناسایی نشد"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "سرویس گفتار مشغول است"
            SpeechRecognizer.ERROR_SERVER -> "خطای سرور گوگل"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "صدایی دریافت نشد"
            else -> "خطا در تشخیص گفتار ($error)"
        }

        if (isWakeWordActive) {
            // Auto restart listening after brief delay in wake word mode
            mainHandler.postDelayed({
                if (isWakeWordActive) {
                    startListening()
                }
            }, 800)
        } else {
            listener.onError(msg)
        }
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val text = matches[0]
            if (isWakeWordActive) {
                val cleaned = text.trim()
                var wakeDetected = false
                var commandRemainder = ""
                for (ww in WAKE_WORDS) {
                    if (cleaned.contains(ww, ignoreCase = true)) {
                        wakeDetected = true
                        commandRemainder = cleaned.replace(Regex(Regex.escape(ww), RegexOption.IGNORE_CASE), "").trim()
                        break
                    }
                }
                if (wakeDetected) {
                    listener.onWakeWordTriggered(commandRemainder)
                }
                // Continue wake word loop
                mainHandler.postDelayed({
                    if (isWakeWordActive) {
                        startListening()
                    }
                }, 1000)
            } else {
                listener.onResults(text)
            }
        } else {
            if (!isWakeWordActive) {
                listener.onError("صدایی تشخیص داده نشد")
            }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
