package com.gmscreen.accessible.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.util.AccessibilityHelper
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private lateinit var txtSettingsStbModel: TextView
    private lateinit var txtSettingsStbIp: TextView
    private lateinit var txtSettingsStbChip: TextView
    private lateinit var chkKeepGlobalNum: MaterialCheckBox
    private lateinit var chkHaptic: MaterialCheckBox
    private lateinit var chkSounds: MaterialCheckBox

    private val repository get() = GMScreenApplication.instance.repository
    private val appConfig get() = GMScreenApplication.instance.appConfig

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        txtSettingsStbModel = view.findViewById(R.id.txtSettingsStbModel)
        txtSettingsStbIp = view.findViewById(R.id.txtSettingsStbIp)
        txtSettingsStbChip = view.findViewById(R.id.txtSettingsStbChip)
        chkKeepGlobalNum = view.findViewById(R.id.chkKeepGlobalNum)
        chkHaptic = view.findViewById(R.id.chkHaptic)
        chkSounds = view.findViewById(R.id.chkSounds)

        initSettingsValues()
        setupListeners()
        observeStbInfo()

        return view
    }

    private fun initSettingsValues() {
        chkKeepGlobalNum.isChecked = appConfig.keepGlobalChannelNum
        chkHaptic.isChecked = appConfig.hapticFeedbackEnabled
        chkSounds.isChecked = appConfig.soundCuesEnabled
    }

    private fun setupListeners() {
        chkKeepGlobalNum.setOnCheckedChangeListener { _, isChecked ->
            appConfig.keepGlobalChannelNum = isChecked
            val stateText = if (isChecked) "فعال شد" else "غیرفعال شد"
            AccessibilityHelper.announce(
                chkKeepGlobalNum,
                "حفظ شماره کانال اصلی در زمان فیلتر ماهواره $stateText"
            )
        }

        chkHaptic.setOnCheckedChangeListener { _, isChecked ->
            appConfig.hapticFeedbackEnabled = isChecked
            val stateText = if (isChecked) "فعال شد" else "غیرفعال شد"
            AccessibilityHelper.announce(
                chkHaptic,
                "لرزش لمسی دکمه‌ها $stateText"
            )
        }

        chkSounds.setOnCheckedChangeListener { _, isChecked ->
            appConfig.soundCuesEnabled = isChecked
            val stateText = if (isChecked) "فعال شد" else "غیرفعال شد"
            AccessibilityHelper.announce(
                chkSounds,
                "صداهای راهنمای دسترس‌پذیری $stateText"
            )
        }
    }

    private fun observeStbInfo() {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.isConnected.collect { connected ->
                if (connected) {
                    val model = repository.deviceModel.value
                    val ip = repository.connectedIp.value
                    txtSettingsStbModel.text = "مدل رسیور: $model"
                    txtSettingsStbIp.text = "آدرس آی‌پی: $ip"
                    txtSettingsStbChip.text = "پردازنده: ALi High-Performance DVB-S2 SOC"
                } else {
                    txtSettingsStbModel.text = "مدل رسیور: متصل نیست"
                    txtSettingsStbIp.text = "آدرس آی‌پی: -"
                    txtSettingsStbChip.text = "پردازنده: ALi Processor"
                }
            }
        }
    }
}
