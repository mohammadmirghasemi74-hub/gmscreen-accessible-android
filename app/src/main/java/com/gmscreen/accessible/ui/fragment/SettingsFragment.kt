package com.gmscreen.accessible.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.protocol.RcuKey
import com.gmscreen.accessible.ui.InternalBrowserActivity
import com.gmscreen.accessible.util.AccessibilityHelper
import com.gmscreen.accessible.util.StorageManager

class SettingsFragment : Fragment() {

    private lateinit var containerSettingsList: LinearLayout

    private val repository get() = GMScreenApplication.instance.repository
    private val appConfig get() = GMScreenApplication.instance.appConfig

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        containerSettingsList = view.findViewById(R.id.containerSettingsList)

        buildSettingsList()

        return view
    }

    private fun buildSettingsList() {
        containerSettingsList.removeAllViews()

        val categories = listOf(
            Pair("۱. مشخصات و وضعیت سخت‌افزاری رسیور (STB Information)", ::openStbInfoDialog),
            Pair("۲. تنظیمات رسیور (تایمر خواب، قفل‌ها و توان پاور)", ::openStbSettingsDialog),
            Pair("۳. تغییر رمز عبور رسیور (Parental PIN)", ::openChangePasswordDialog),
            Pair("۴. تنظیمات ضبط و ذخیره‌سازی در گوشی (Storage & Recordings)", ::openRecordingSettingsDialog),
            Pair("۵. تنظیمات راهنمای برنامه‌ها و اعلام خودکار (EPG & Announcements)", ::openEpgSettingsDialog),
            Pair("۶. تنظیمات کنترل صوتی و بیدارباش (Voice & Wake-Word)", ::openVoiceSettingsDialog),
            Pair("۷. تنظیمات دسترس‌پذیری، ویبره و شماره سراسری کانال‌ها", ::openAccessibilitySettingsDialog),
            Pair("۸. کتابچه راهنمای جامع کاربری نرم‌افزار (Help HTML)", ::openHelpBrowser),
            Pair("۹. راهنمای کامل فرامین صوتی (Voice Help HTML)", ::openVoiceHelpBrowser),
            Pair("۱۰. درباره نرم‌افزار و مشخصات سازنده (About GM Screen)", ::openAboutBrowser)
        )

        for ((title, handler) in categories) {
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 10)
                }
                radius = 12f
                setCardBackgroundColor(resources.getColor(R.color.card_bg, null))
                strokeColor = resources.getColor(R.color.divider, null)
                strokeWidth = 1
                isClickable = true
                isFocusable = true
                contentDescription = "$title. برای ورود دو بار ضربه بزنید."
                setOnClickListener { handler() }
            }

            val rowLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 20, 16, 20)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val txtTitle = TextView(requireContext()).apply {
                text = title
                setTextColor(resources.getColor(R.color.text_primary, null))
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val arrow = TextView(requireContext()).apply {
                text = "◄"
                setTextColor(resources.getColor(R.color.text_secondary, null))
                textSize = 14f
            }

            rowLayout.addView(txtTitle)
            rowLayout.addView(arrow)
            card.addView(rowLayout)
            containerSettingsList.addView(card)
        }
    }

    private fun openStbInfoDialog() {
        val model = repository.deviceModel.value
        val ip = repository.connectedIp.value
        val msg = if (ip.isNotBlank()) {
            "مدل رسیور: $model\nآدرس IP شبکه: $ip\nپورت ارتباطی: 20000\nپورت استریم SAT>IP: 554\nوضعیت اتصال: آنلاین و فعال"
        } else {
            "هیچ رسیوری در حال حاضر متصل نیست. لطفاً در سربرگ اتصال به رسیور وصل شوید."
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("مشخصات سخت‌افزاری رسیور")
            .setMessage(msg)
            .setPositiveButton("بستن", null)
            .show()
    }

    private fun openStbSettingsDialog() {
        val timers = arrayOf("تایمر خاموش: غیرفعال", "۱۵ دقیقه", "۳۰ دقیقه", "۶۰ دقیقه", "۹۰ دقیقه", "۱۲۰ دقیقه")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تنظیم تایمر خواب رسیور (Sleep Timer)")
            .setItems(timers) { _, which ->
                val mins = when (which) {
                    1 -> 15
                    2 -> 30
                    3 -> 60
                    4 -> 90
                    5 -> 120
                    else -> 0
                }
                appConfig.sleepTimerMinutes = mins
                Toast.makeText(requireContext(), "تایمر خواب روی $mins دقیقه تنظیم شد", Toast.LENGTH_SHORT).show()
                AccessibilityHelper.announce(containerSettingsList, "تایمر خواب روی $mins دقیقه تنظیم شد")
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun openChangePasswordDialog() {
        val input = EditText(requireContext()).apply {
            hint = "رمز عبور ۴ رقمی (پیش‌فرض: 0000)"
            setText(appConfig.parentalPin)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تغییر رمز عبور کنترل والدین (PIN)")
            .setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
                val pin = input.text.toString().trim()
                if (pin.length == 4) {
                    appConfig.parentalPin = pin
                    Toast.makeText(requireContext(), "رمز عبور با موفقیت ذخیره شد: $pin", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "رمز عبور باید ۴ رقم باشد", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun openRecordingSettingsDialog() {
        val rootPath = StorageManager.getAppRootDir(requireContext()).absolutePath
        val msg = """
            پوشه اصلی ذخیره‌سازی:
            $rootPath
            
            زیرپوشه‌های خودکار:
            • recordings: فایل‌های ویدیویی و صوتی ضبط شده
            • backups: فایل‌های پشتیبان کانال‌ها
            • playlists: فایل‌های پلی‌لیست M3U
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تنظیمات ضبط و ذخیره‌سازی")
            .setMessage(msg)
            .setPositiveButton("بستن", null)
            .show()
    }

    private fun openEpgSettingsDialog() {
        val items = arrayOf(
            "اعلام خودکار نام شبکه با تعویض کانال: ${if (appConfig.autoAnnounceChannel) "فعال" else "غیرفعال"}"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تنظیمات اعلام صوتی و EPG")
            .setItems(items) { _, which ->
                if (which == 0) {
                    appConfig.autoAnnounceChannel = !appConfig.autoAnnounceChannel
                    val state = if (appConfig.autoAnnounceChannel) "فعال" else "غیرفعال"
                    Toast.makeText(requireContext(), "اعلام خودکار شبکه: $state", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun openVoiceSettingsDialog() {
        val items = arrayOf(
            "حالت بیدارباش صوتی: ${if (appConfig.voiceAlwaysListening) "روشن" else "خاموش"}",
            "پخش صدای بوق شروع و پایان فرامین: ${if (appConfig.soundCuesEnabled) "روشن" else "خاموش"}"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تنظیمات کنترل صوتی و بیدارباش")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        appConfig.voiceAlwaysListening = !appConfig.voiceAlwaysListening
                        val state = if (appConfig.voiceAlwaysListening) "روشن" else "خاموش"
                        Toast.makeText(requireContext(), "بیدارباش: $state", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        appConfig.soundCuesEnabled = !appConfig.soundCuesEnabled
                        val state = if (appConfig.soundCuesEnabled) "روشن" else "خاموش"
                        Toast.makeText(requireContext(), "افکت صوتی: $state", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun openAccessibilitySettingsDialog() {
        val items = arrayOf(
            "ویبره و بازخورد لمسی (Haptic): ${if (appConfig.hapticFeedbackEnabled) "فعال" else "غیرفعال"}",
            "حفظ شماره سراسری کانال‌ها در فیلترها: ${if (appConfig.keepGlobalChannelNum) "فعال" else "غیرفعال"}"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تنظیمات دسترس‌پذیری")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        appConfig.hapticFeedbackEnabled = !appConfig.hapticFeedbackEnabled
                        val state = if (appConfig.hapticFeedbackEnabled) "فعال" else "غیرفعال"
                        Toast.makeText(requireContext(), "ویبره لمسی: $state", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        appConfig.keepGlobalChannelNum = !appConfig.keepGlobalChannelNum
                        val state = if (appConfig.keepGlobalChannelNum) "فعال" else "غیرفعال"
                        Toast.makeText(requireContext(), "شماره سراسری کانال‌ها: $state", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun openHelpBrowser() {
        InternalBrowserActivity.open(requireContext(), "help.htm", "کتابچه راهنمای جامع کاربری")
    }

    private fun openVoiceHelpBrowser() {
        InternalBrowserActivity.open(requireContext(), "voice_help.htm", "راهنمای کامل فرامین صوتی")
    }

    private fun openAboutBrowser() {
        InternalBrowserActivity.open(requireContext(), "about.htm", "درباره GM Screen و مشخصات سازنده")
    }
}
