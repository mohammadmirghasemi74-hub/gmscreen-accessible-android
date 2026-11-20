package com.gmscreen.accessible.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
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
        fun onWakeWordTriggered(remainingCommand: String)
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false
    private var isWakeWordActive: Boolean = false
    private var isPausedDueToExternalAudio: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost ($focusChange). Yielding microphone to external app.")
                isPausedDueToExternalAudio = true
                stopRecognizerOnly()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained. Resuming microphone listening.")
                if (isWakeWordActive && isPausedDueToExternalAudio) {
                    isPausedDueToExternalAudio = false
                    mainHandler.postDelayed({
                        if (isWakeWordActive && !isPausedDueToExternalAudio) {
                            muteSystemSounds(true)
                            startListening()
                        }
                    }, 1200)
                }
            }
        }
    }

    companion object {
        private const val TAG = "VoiceController"
        val WAKE_WORDS = listOf(
            "های رسیور", "های‌رسیور", "هی رسیور", "هی‌رسیور", "hi receiver",
            "رسیور", "جی ام اسکرین", "دستگاه", "screen", "stb"
        )
    }

    private fun muteSystemSounds(mute: Boolean) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (mute) {
                am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
            } else {
                am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
            }
        } catch (_: Exception) {}
    }

    fun setWakeWordMode(enabled: Boolean) {
        isWakeWordActive = enabled
        isPausedDueToExternalAudio = false
        if (enabled) {
            startListening()
        } else {
            stopListening()
        }
    }

    fun isWakeWordEnabled(): Boolean = isWakeWordActive

    fun startListening() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startListening() }
            return
        }
        if (isPausedDueToExternalAudio) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onError("سرویس تشخیص گفتار در این دستگاه در دسترس نیست")
            return
        }

        stopRecognizerOnly()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                // Tuned specifically for microphones: high sensitivity and natural speaking pace
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra("android.speech.extra.AUDIO_SOURCE", android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            if (isWakeWordActive) {
                mainHandler.postDelayed({
                    if (isWakeWordActive && !isPausedDueToExternalAudio) {
                        startListening()
                    }
                }, 3000L)
            } else {
                listener.onError("خطا در راه‌اندازی ضبط صدا: ${e.message}")
            }
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
        isPausedDueToExternalAudio = false
        try {
            audioManager?.abandonAudioFocus(audioFocusChangeListener)
        } catch (_: Exception) {}
        muteSystemSounds(false)
        stopRecognizerOnly()
    }

    fun isCurrentlyListening(): Boolean = isListening

    override fun onReadyForSpeech(params: Bundle?) {
        if (!isWakeWordActive) {
            listener.onReady()
        }
    }

    override fun onBeginningOfSpeech() {
        if (!isWakeWordActive) {
            listener.onListening()
        }
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
            val backoffMs = when (error) {
                SpeechRecognizer.ERROR_AUDIO,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> 5000L // Yield cleanly to other apps using microphone
                else -> 1000L
            }
            mainHandler.postDelayed({
                if (isWakeWordActive && !isPausedDueToExternalAudio) {
                    muteSystemSounds(true)
                    startListening()
                }
            }, backoffMs)
        } else {
            listener.onError(msg)
        }
    }

    private fun checkWakeWordMatches(matches: List<String>): Pair<Boolean, String> {
        val configured = try {
            com.gmscreen.accessible.util.AppConfig(context).voiceWakeWord.trim()
        } catch (_: Exception) { "" }

        val candidateWords = mutableListOf<String>()
        if (configured.isNotBlank()) {
            candidateWords.add(configured)
        }
        for (ww in WAKE_WORDS) {
            if (!candidateWords.any { it.equals(ww, ignoreCase = true) }) {
                candidateWords.add(ww)
            }
        }

        for (text in matches) {
            val cleaned = text.trim()
            for (ww in candidateWords) {
                if (cleaned.contains(ww, ignoreCase = true)) {
                    val remainder = cleaned.replace(Regex(Regex.escape(ww), RegexOption.IGNORE_CASE), "").trim()
                    return Pair(true, remainder)
                }
            }
        }
        return Pair(false, "")
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

        if (isWakeWordActive) {
            if (!matches.isNullOrEmpty()) {
                val (wakeDetected, commandRemainder) = checkWakeWordMatches(matches)
                if (wakeDetected) {
                    listener.onWakeWordTriggered(commandRemainder)
                }
            }
            // Always continue listening loop if wake word mode is active
            mainHandler.postDelayed({
                if (isWakeWordActive && !isPausedDueToExternalAudio) {
                    startListening()
                }
            }, 800L)
        } else {
            if (!matches.isNullOrEmpty()) {
                listener.onResults(matches)
            } else {
                listener.onError("صدایی تشخیص داده نشد")
            }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        if (!isWakeWordActive) return
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        val (wakeDetected, commandRemainder) = checkWakeWordMatches(matches)
        if (wakeDetected) {
            stopListening()
            listener.onWakeWordTriggered(commandRemainder)
            mainHandler.postDelayed({
                if (isWakeWordActive && !isPausedDueToExternalAudio) {
                    startListening()
                }
            }, 1200L)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
