package com.gmscreen.accessible.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false

    companion object {
        private const val TAG = "VoiceController"
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onError("سرویس تشخیص گفتار در این دستگاه در دسترس نیست")
            return
        }

        stopListening()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            listener.onError("خطا در راه‌اندازی ضبط صدا: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            isListening = false
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
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
            SpeechRecognizer.ERROR_NO_MATCH -> "صدایی شناسایی نشد، لطفاً مجدداً تلاش فرمایید"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "سرویس گفتار مشغول است"
            SpeechRecognizer.ERROR_SERVER -> "خطای سرور گوگل"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "صدایی دریافت نشد"
            else -> "خطای نامشخص در تشخیص گفتار ($error)"
        }
        listener.onError(msg)
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val recognizedText = matches[0]
            listener.onResults(recognizedText)
        } else {
            listener.onError("صدایی تشخیص داده نشد")
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
