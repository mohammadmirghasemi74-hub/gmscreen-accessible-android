package com.gmscreen.accessible.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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
        fun onRmsChanged(rmsdB: Float) {}
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    companion object {
        private const val TAG = "VoiceController"
    }

    fun isCurrentlyListening(): Boolean = isListening

    private fun requestAudioFocus() {
        try {
            if (audioManager == null) {
                audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            }
            audioManager?.let { am ->
                if (am.isMicrophoneMute) {
                    am.isMicrophoneMute = false
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val playbackAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setOnAudioFocusChangeListener { /* no-op */ }
                        .build()
                    audioFocusRequest = request
                    am.requestAudioFocus(request)
                } else {
                    @Suppress("DEPRECATION")
                    am.requestAudioFocus(
                        { /* no-op */ },
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request notice: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        try {
            audioManager?.let { am ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                    audioFocusRequest = null
                } else {
                    @Suppress("DEPRECATION")
                    am.abandonAudioFocus { /* no-op */ }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus release notice: ${e.message}")
        }
    }

    fun startListening() {
        mainHandler.post {
            try {
                requestAudioFocus()

                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    speechRecognizer?.setRecognitionListener(this)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "fa-IR")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    // High-accuracy speech thresholds: prevents early cutoffs
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                    putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true)
                    putExtra("android.speech.extra.DICTATION_MODE", false)
                }

                speechRecognizer?.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                Log.e(TAG, "Error starting voice recognition: ${e.message}")
                isListening = false
                abandonAudioFocus()
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
            abandonAudioFocus()
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
            abandonAudioFocus()
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        listener.onReady()
    }

    override fun onBeginningOfSpeech() {
        listener.onListening()
    }

    override fun onRmsChanged(rmsdB: Float) {
        listener.onRmsChanged(rmsdB)
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        isListening = false
        abandonAudioFocus()
    }

    override fun onError(error: Int) {
        isListening = false
        abandonAudioFocus()
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
        abandonAudioFocus()
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
