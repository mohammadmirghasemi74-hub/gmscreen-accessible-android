package com.gmscreen.accessible.ui.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.protocol.RcuKey
import com.gmscreen.accessible.util.AccessibilityHelper
import com.gmscreen.accessible.voice.ChannelMatcher
import com.gmscreen.accessible.voice.VoiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceFragment : Fragment(), VoiceController.VoiceListener {

    private lateinit var btnMic: MaterialButton
    private lateinit var txtVoiceStatus: TextView
    private lateinit var txtRecognizedQuery: TextView
    private lateinit var txtExecutionResult: TextView

    private var voiceController: VoiceController? = null

    private val repository get() = GMScreenApplication.instance.repository
    private val appConfig get() = GMScreenApplication.instance.appConfig

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSpeech()
        } else {
            val msg = "مجوز دسترسی به میکروفون رد شد"
            txtVoiceStatus.text = msg
            AccessibilityHelper.announce(txtVoiceStatus, msg)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_voice, container, false)

        btnMic = view.findViewById(R.id.btnMic)
        txtVoiceStatus = view.findViewById(R.id.txtVoiceStatus)
        txtRecognizedQuery = view.findViewById(R.id.txtRecognizedQuery)
        txtExecutionResult = view.findViewById(R.id.txtExecutionResult)

        voiceController = VoiceController(requireContext(), this)

        btnMic.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startSpeech()
            } else {
                requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        return view
    }

    private fun startSpeech() {
        if (voiceController?.isCurrentlyListening() == true) {
            voiceController?.stopListening()
            btnMic.text = "🎤\nشروع گفتار"
            txtVoiceStatus.text = "شنیدن صدا متوقف شد"
            AccessibilityHelper.announce(txtVoiceStatus, "شنیدن صدا متوقف شد")
        } else {
            btnMic.text = "⏹\nدر حال شنیدن…"
            txtVoiceStatus.text = getString(R.string.voice_listening)
            txtRecognizedQuery.visibility = View.GONE
            txtExecutionResult.visibility = View.GONE
            AccessibilityHelper.announce(txtVoiceStatus, "گوش به زنگ… بفرمایید")
            if (appConfig.soundCuesEnabled) {
                AccessibilityHelper.playSoundCue()
            }
            voiceController?.startListening()
        }
    }

    override fun onReady() {
        txtVoiceStatus.text = "میکروفون فعال شد، اکنون صحبت کنید…"
    }

    override fun onListening() {
        txtVoiceStatus.text = "در حال دریافت صوت…"
    }

    override fun onResults(recognizedText: String) {
        btnMic.text = "🎤\nشروع گفتار"
        txtVoiceStatus.text = "صوت دریافت شد، در حال پردازش…"

        txtRecognizedQuery.visibility = View.VISIBLE
        txtRecognizedQuery.text = "عبارت شنیده‌شده: «$recognizedText»"
        AccessibilityHelper.announce(txtRecognizedQuery, "عبارت شنیده‌شده: $recognizedText")

        processVoiceCommand(recognizedText)
    }

    override fun onError(errorMessage: String) {
        btnMic.text = "🎤\nشروع گفتار"
        txtVoiceStatus.text = errorMessage
        AccessibilityHelper.announce(txtVoiceStatus, errorMessage)
    }

    private fun processVoiceCommand(text: String) {
        // 1. Check for system RCU Actions
        val rcuAction = ChannelMatcher.matchVoiceAction(text)
        if (rcuAction != null) {
            handleRcuAction(rcuAction)
            return
        }

        // 2. Check for Channel Match
        val channels = repository.channels.value
        val matchedChannel = ChannelMatcher.findBestChannel(text, channels)
        if (matchedChannel != null) {
            val msg = "کانال ${matchedChannel.serviceName} یافت شد. در حال تغییر کانال…"
            txtExecutionResult.visibility = View.VISIBLE
            txtExecutionResult.text = "شبکه پخش شد: ${matchedChannel.serviceName}"
            AccessibilityHelper.announce(txtExecutionResult, msg)

            if (appConfig.hapticFeedbackEnabled) {
                AccessibilityHelper.vibrateSuccess(requireContext())
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val switched = repository.switchChannel(matchedChannel)
                withContext(Dispatchers.Main) {
                    if (switched && appConfig.soundCuesEnabled) {
                        AccessibilityHelper.playSoundCue()
                    }
                }
            }
            return
        }

        val notFoundMsg = "کانال یا دستوری برای «$text» پیدا نشد. لطفاً مجدداً امتحان کنید."
        txtExecutionResult.visibility = View.VISIBLE
        txtExecutionResult.text = notFoundMsg
        AccessibilityHelper.announce(txtExecutionResult, notFoundMsg)
    }

    private fun handleRcuAction(action: String) {
        val (keyCode, desc) = when (action) {
            "power" -> Pair(RcuKey.POWER, "پاور رسیور ارسال شد")
            "ch_next" -> Pair(RcuKey.CH_NEXT, "کانال بعدی زده شد")
            "ch_prev" -> Pair(RcuKey.CH_PREV, "کانال قبلی زده شد")
            "vol_up" -> Pair(RcuKey.VOL_UP, "صدا افزایش یافت")
            "vol_down" -> Pair(RcuKey.VOL_DOWN, "صدا کاهش یافت")
            "mute" -> Pair(RcuKey.MUTE, "حالت بی‌صدا تغییر کرد")
            "menu" -> Pair(RcuKey.MENU, "منوی رسیور باز شد")
            "exit" -> Pair(RcuKey.EXIT, "خروج ارسال شد")
            "ok" -> Pair(RcuKey.OK, "تأیید زده شد")
            "epg" -> Pair(RcuKey.EPG, "جدول EPG باز شد")
            "info" -> Pair(RcuKey.INFO, "اینفوی شبکه باز شد")
            else -> Pair(null, "")
        }

        if (keyCode != null) {
            txtExecutionResult.visibility = View.VISIBLE
            txtExecutionResult.text = desc
            AccessibilityHelper.announce(txtExecutionResult, desc)

            if (appConfig.hapticFeedbackEnabled) {
                AccessibilityHelper.vibrateClick(requireContext())
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                repository.sendRcuKey(keyCode)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        voiceController?.stopListening()
        voiceController = null
    }
}
