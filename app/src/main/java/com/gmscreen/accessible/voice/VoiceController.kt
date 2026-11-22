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
        fun onResults(candidates: List<String>)
        fun onError(errorMessage: String)
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "VoiceController"
    }

    fun isCurrentlyListening(): Boolean = isListening

    fun startListening() {
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    speechRecognizer?.setRecognitionListener(this)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "fa-IR")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                }

                speechRecognizer?.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                Log.e(TAG, "Error starting voice recognition: ${e.message}")
                isListening = false
                listener.onError("خطا در راه‌اندازی ضبط صدا")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping voice recognition: ${e.message}")
            }
            isListening = false
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying speech recognizer: ${e.message}")
            }
            isListening = false
        }
    }

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
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "خطای ضبط صدا"
            SpeechRecognizer.ERROR_CLIENT -> "خطای داخلی سرویس گفتار"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "عدم دسترسی به میکروفون"
            SpeechRecognizer.ERROR_NETWORK -> "خطای شبکه اینترنت"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "پایان زمان برقراری ارتباط با شبکه"
            SpeechRecognizer.ERROR_NO_MATCH -> "صدایی شناسایی نشد"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "سرویس تشخیص صدا مشغول است"
            SpeechRecognizer.ERROR_SERVER -> "خطای سرور تشخیص صدا"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "صدایی شنیده نشد"
            else -> "خطای نامشخص تشخیص صدا"
        }
        listener.onError(message)
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
        if (matches.isNotEmpty()) {
            listener.onResults(matches)
        } else {
            listener.onError("فرمانی شناسایی نشد")
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
