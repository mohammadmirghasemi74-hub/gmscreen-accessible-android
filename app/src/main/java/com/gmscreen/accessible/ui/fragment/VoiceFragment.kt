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
import com.google.android.material.switchmaterial.SwitchMaterial
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.protocol.RcuKey
import com.gmscreen.accessible.ui.InternalBrowserActivity
import com.gmscreen.accessible.util.AccessibilityHelper
import com.gmscreen.accessible.voice.ChannelMatcher
import com.gmscreen.accessible.voice.VoiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceFragment : Fragment(), VoiceController.VoiceListener {

    private lateinit var btnMic: MaterialButton
    private lateinit var btnVoiceHelp: MaterialButton
    private lateinit var switchWakeWord: SwitchMaterial
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
            val msg = "مجوز دسترسی به میکروفون داده نشد"
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
        btnVoiceHelp = view.findViewById(R.id.btnVoiceHelp)
        switchWakeWord = view.findViewById(R.id.switchWakeWord)
        txtVoiceStatus = view.findViewById(R.id.txtVoiceStatus)
        txtRecognizedQuery = view.findViewById(R.id.txtRecognizedQuery)
        txtExecutionResult = view.findViewById(R.id.txtExecutionResult)

        voiceController = VoiceController(requireContext(), this)

        setupListeners()

        return view
    }

    private fun setupListeners() {
        btnMic.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startSpeech()
            } else {
                requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        btnVoiceHelp.setOnClickListener {
            InternalBrowserActivity.open(requireContext(), "voice_help.htm", "راهنمای کامل دستورات صوتی")
        }

        switchWakeWord.isChecked = appConfig.voiceAlwaysListening
        switchWakeWord.setOnCheckedChangeListener { _, isChecked ->
            appConfig.voiceAlwaysListening = isChecked
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    voiceController?.setWakeWordMode(true)
                    val msg = "حالت بیدارباش فعال شد. هر زمان بگویید «های رسیور»، برنامه فرمان شما را می‌شنود."
                    txtVoiceStatus.text = msg
                    AccessibilityHelper.announce(txtVoiceStatus, msg)
                } else {
                    switchWakeWord.isChecked = false
                    requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            } else {
                voiceController?.setWakeWordMode(false)
                val msg = "حالت بیدارباش غیرفعال شد."
                txtVoiceStatus.text = msg
                AccessibilityHelper.announce(txtVoiceStatus, msg)
            }
        }
    }

    private fun startSpeech() {
        if (voiceController?.isCurrentlyListening() == true) {
            voiceController?.stopListening()
            btnMic.text = "شروع گفتار"
            txtVoiceStatus.text = "شنیدن صدا متوقف شد"
            AccessibilityHelper.announce(txtVoiceStatus, "شنیدن صدا متوقف شد")
        } else {
            btnMic.text = "در حال گوش دادن…"
            txtVoiceStatus.text = getString(R.string.voice_listening)
            txtRecognizedQuery.visibility = View.GONE
            txtExecutionResult.visibility = View.GONE
            // Do not speak announcement here so mic doesn't record TalkBack
            if (appConfig.soundCuesEnabled) {
                AccessibilityHelper.playSoundCue()
            }
            voiceController?.startListening()
        }
    }

    override fun onReady() {
        if (voiceController?.isWakeWordEnabled() != true) {
            txtVoiceStatus.text = "میکروفون فعال شد، اکنون صحبت کنید…"
        }
    }

    override fun onListening() {
        if (voiceController?.isWakeWordEnabled() != true) {
            txtVoiceStatus.text = "در حال دریافت صدا…"
        }
    }

    override fun onResults(candidates: List<String>) {
        btnMic.text = "شروع گفتار"
        txtVoiceStatus.text = "صدا دریافت شد، در حال پردازش…"

        val primary = candidates.firstOrNull() ?: ""
        val channels = repository.channels.value

        // 1. Try matching RCU actions across all candidates
        for (text in candidates) {
            val rcuAction = ChannelMatcher.matchVoiceAction(text)
            if (rcuAction != null) {
                txtRecognizedQuery.visibility = View.VISIBLE
                txtRecognizedQuery.text = "عبارت شنیده شده: «$text»"
                AccessibilityHelper.announce(txtRecognizedQuery, "شنیده شد: $text")
                handleRcuAction(rcuAction)
                return
            }
        }

        // 2. Try matching Channels across all candidates
        for (text in candidates) {
            val matchedChannel = ChannelMatcher.findBestChannel(text, channels)
            if (matchedChannel != null) {
                txtRecognizedQuery.visibility = View.VISIBLE
                txtRecognizedQuery.text = "عبارت شنیده شده: «$text»"
                AccessibilityHelper.announce(txtRecognizedQuery, "شنیده شد: $text")

                val msg = "شبکه ${matchedChannel.serviceName} پیدا شد. در حال تعویض کانال…"
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
        }

        // 3. Not found
        txtRecognizedQuery.visibility = View.VISIBLE
        txtRecognizedQuery.text = "عبارت شنیده شده: «$primary»"
        AccessibilityHelper.announce(txtRecognizedQuery, "شنیده شد: $primary")

        val notFoundMsg = "کانال یا دستوری برای «$primary» پیدا نشد. لطفاً مجدداً امتحان کنید."
        txtExecutionResult.visibility = View.VISIBLE
        txtExecutionResult.text = notFoundMsg
        AccessibilityHelper.announce(txtExecutionResult, notFoundMsg)
    }

    override fun onWakeWordTriggered(remainingCommand: String) {
        if (appConfig.soundCuesEnabled) {
            AccessibilityHelper.playSoundCue()
        }
        if (remainingCommand.isNotBlank()) {
            txtRecognizedQuery.visibility = View.VISIBLE
            txtRecognizedQuery.text = "فرمان بیدارباش: «$remainingCommand»"
            processVoiceCommand(remainingCommand)
        } else {
            val msg = "بیدارباش تشخیص داده شد. گوش به زنگ فرمان شما هستم…"
            txtVoiceStatus.text = msg
            AccessibilityHelper.announce(txtVoiceStatus, msg)
        }
    }

    override fun onError(errorMessage: String) {
        btnMic.text = "شروع گفتار"
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
            val msg = "شبکه ${matchedChannel.serviceName} پیدا شد. در حال تعویض کانال…"
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
        when (action) {
            "reset_counter" -> {
                appConfig.resetRecordingCounter()
                val msg = "شمارنده فایل‌های ضبط با موفقیت صفر شد"
                txtExecutionResult.visibility = View.VISIBLE
                txtExecutionResult.text = msg
                AccessibilityHelper.announce(txtExecutionResult, msg)
                return
            }
            "wake_enable" -> {
                switchWakeWord.isChecked = true
                return
            }
            "wake_disable" -> {
                switchWakeWord.isChecked = false
                return
            }
            "open_manual" -> {
                InternalBrowserActivity.open(requireContext(), "help.htm", "راهنمای کاربری نرم‌افزار")
                return
            }
            "open_voice_help" -> {
                InternalBrowserActivity.open(requireContext(), "voice_help.htm", "راهنمای کامل دستورات صوتی")
                return
            }
            "minimize_app" -> {
                com.gmscreen.accessible.service.GMScreenBackgroundService.startService(requireContext())
                val msg = "برنامه در پس‌زمینه فعال باقی ماند"
                AccessibilityHelper.announce(txtExecutionResult, msg)
                requireActivity().moveTaskToBack(true)
                return
            }
            "close_app" -> {
                com.gmscreen.accessible.service.GMScreenBackgroundService.stopService(requireContext())
                repository.disconnect()
                requireActivity().finishAffinity()
                return
            }
            "refresh_channels" -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.refreshData()
                    val msg = "لیست شبکه‌ها با موفقیت به‌روزرسانی شد"
                    txtExecutionResult.visibility = View.VISIBLE
                    txtExecutionResult.text = msg
                    AccessibilityHelper.announce(txtExecutionResult, msg)
                }
                return
            }
        }

        val (keyCode, desc) = when (action) {
            "power" -> Pair(RcuKey.POWER_TOGGLE, "پاور رسیور ارسال شد")
            "ch_next" -> Pair(RcuKey.CH_NEXT, "کانال بعدی زده شد")
            "ch_prev" -> Pair(RcuKey.CH_PREV, "کانال قبلی زده شد")
            "recall" -> Pair(RcuKey.RECALL, "بازگشت به کانال قبلی زده شد")
            "tv_mode", "radio_mode" -> Pair(RcuKey.TV_RADIO, "تغییر حالت بین تلویزیون و رادیو ارسال شد")
            "vol_up" -> Pair(RcuKey.VOL_UP, "صدا افزایش یافت")
            "vol_down" -> Pair(RcuKey.VOL_DOWN, "صدا کاهش یافت")
            "mute" -> Pair(RcuKey.MUTE, "حالت بی‌صدا تغییر کرد")
            "audio_track" -> Pair(RcuKey.AUDIO, "تغییر باند صدا ارسال شد")
            "subtitle" -> Pair(RcuKey.SUBTITLE, "زیرنویس تغییر کرد")
            "menu" -> Pair(RcuKey.MENU, "منوی رسیور باز شد")
            "exit", "rcu_exit" -> Pair(RcuKey.EXIT, "خروج ارسال شد")
            "ok" -> Pair(RcuKey.OK, "تأیید زده شد")
            "epg" -> Pair(RcuKey.EPG, "جدول EPG باز شد")
            "info" -> Pair(RcuKey.INFO, "اطلاعات شبکه باز شد")
            "red_btn" -> Pair(RcuKey.RED, "دکمه قرمز ارسال شد")
            "green_btn" -> Pair(RcuKey.GREEN, "دکمه سبز ارسال شد")
            "yellow_btn" -> Pair(RcuKey.YELLOW, "دکمه زرد ارسال شد")
            "blue_btn" -> Pair(RcuKey.BLUE, "دکمه آبی ارسال شد")
            "rec_stb" -> Pair(RcuKey.RECORD, "دستور ضبط روی فلش رسیور ارسال شد")
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
