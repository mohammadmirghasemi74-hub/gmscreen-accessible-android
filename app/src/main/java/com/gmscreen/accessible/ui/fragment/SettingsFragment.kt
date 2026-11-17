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
        val showBtnText = if (appConfig.showRecordingsInChannels) "فعال (پیش‌فرض)" else "غیرفعال"
        val vidFmtText = appConfig.videoRecFormat.uppercase()
        val vidQualText = getVideoQualityTitle(appConfig.videoRecQuality)
        val patText = getNamingPatternTitle(appConfig.recNamingPattern)
        val audFmtText = appConfig.audioRecFormat.uppercase()
        val audBrText = appConfig.audioRecBitrate
        val audSrText = getSampleRateTitle(appConfig.audioRecSampleRate)
        val audChText = if (appConfig.audioRecChannels == "mono") "مونو (تک کاناله)" else "استریو (دو کاناله - پیش‌فرض)"

        val items = arrayOf(
            "۱. نمایش دکمه فایل‌های ضبط‌شده در بخش کانال‌ها: $showBtnText",
            "۲. فرمت ضبط تصویری (ویدیو): $vidFmtText",
            "۳. کیفیت و نحوه ذخیره ویدیو: $vidQualText",
            "۴. الگوی نام‌گذاری فایل‌های ضبط: $patText",
            "۵. فرمت ضبط صوتی (صدا): $audFmtText",
            "۶. بیت‌ریت صدای ضبط: $audBrText",
            "۷. نرخ نمونه‌برداری صدا (Sample Rate): $audSrText",
            "۸. کانال صدا: $audChText",
            "۹. 📁 مشاهده و مدیریت فایل‌های ضبط‌شده در حافظه گوشی",
            "۱۰. اطلاعات پوشه اصلی ذخیره‌سازی"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تنظیمات جامع ضبط و ذخیره‌سازی")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> toggleShowRecordingsInChannels()
                    1 -> showVideoFormatDialog()
                    2 -> showVideoQualityDialog()
                    3 -> showNamingPatternDialog()
                    4 -> showAudioFormatDialog()
                    5 -> showAudioBitrateDialog()
                    6 -> showAudioSampleRateDialog()
                    7 -> showAudioChannelsDialog()
                    8 -> com.gmscreen.accessible.util.RecordingsDialogHelper.showRecordingsDialog(requireContext())
                    9 -> showStorageFolderInfoDialog()
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun toggleShowRecordingsInChannels() {
        appConfig.showRecordingsInChannels = !appConfig.showRecordingsInChannels
        val state = if (appConfig.showRecordingsInChannels) "فعال شد" else "غیرفعال شد"
        val msg = "دکمه مشاهده فایل‌های ضبط‌شده در بخش کانال‌ها: $state"
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        AccessibilityHelper.announce(containerSettingsList, msg)
        openRecordingSettingsDialog()
    }

    private fun showVideoFormatDialog() {
        val formats = arrayOf(
            "MP4 (پیش‌فرض - سازگار با همه دستگاه‌ها و پلیرها)",
            "MKV (فرمت چندرسانه‌ای باکیفیت)",
            "TS (جریان اصلی ضبط رسیور بدون تغییر کانتینر)"
        )
        val values = arrayOf("mp4", "mkv", "ts")
        val currentIdx = values.indexOf(appConfig.videoRecFormat).let { if (it >= 0) it else 0 }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("انتخاب فرمت ضبط تصویری")
            .setSingleChoiceItems(formats, currentIdx) { dialog, which ->
                val chosen = values[which]
                appConfig.videoRecFormat = chosen
                val msg = "فرمت ضبط تصویری روی ${chosen.uppercase()} تنظیم شد"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                AccessibilityHelper.announce(containerSettingsList, msg)
                dialog.dismiss()
                openRecordingSettingsDialog()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showVideoQualityDialog() {
        val qualities = arrayOf(
            "کیفیت اصلی شبکه (کپی مستقیم - بدون افت کیفیت و بدون مصرف پردازنده - پیش‌فرض)",
            "فشرده‌سازی با کیفیت بسیار بالا (H.264 - CRF 18)",
            "فشرده‌سازی متعادل (H.264 - CRF 23)",
            "فشرده‌سازی کم‌حجم و سبک (H.264 - 720p - CRF 28)"
        )
        val values = arrayOf("copy", "high", "medium", "low")
        val currentIdx = values.indexOf(appConfig.videoRecQuality).let { if (it >= 0) it else 0 }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("کیفیت و نحوه ذخیره ویدیو")
            .setSingleChoiceItems(qualities, currentIdx) { dialog, which ->
                val chosen = values[which]
                appConfig.videoRecQuality = chosen
                val title = getVideoQualityTitle(chosen)
                val msg = "کیفیت ذخیره ویدیو تنظیم شد: $title"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                AccessibilityHelper.announce(containerSettingsList, msg)
                dialog.dismiss()
                openRecordingSettingsDialog()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showNamingPatternDialog() {
        val patterns = arrayOf(
            "شمسی عددی استاندارد (مثال: 1405-08-27) [پیش‌فرض]",
            "شمسی با نام ماه و سال (مثال: 27 آبان 1405)",
            "شمسی با نام ماه بدون سال (مثال: 27 آبان)",
            "تاریخ و زمان میلادی (مثال: 2026-11-17)"
        )
        val values = arrayOf("jalali_numeric", "jalali_month_year", "jalali_month", "gregorian")
        val currentIdx = values.indexOf(appConfig.recNamingPattern).let { if (it >= 0) it else 0 }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("الگوی نام‌گذاری فایل‌های ضبط")
            .setSingleChoiceItems(patterns, currentIdx) { dialog, which ->
                val chosen = values[which]
                appConfig.recNamingPattern = chosen
                val title = getNamingPatternTitle(chosen)
                val msg = "الگوی نام‌گذاری فایل‌ها تنظیم شد: $title"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                AccessibilityHelper.announce(containerSettingsList, msg)
                dialog.dismiss()
                openRecordingSettingsDialog()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showAudioFormatDialog() {
        val formats = arrayOf(
            "MP3 (پیش‌فرض - سازگار با کلیه دستگاه‌ها و پلیرها)",
            "M4A (فرمت مدرن اپل و اندروید - کدک AAC با شفافیت بالا)",
            "WAV (کیفیت خام استودیویی - بدون فشرده‌سازی)"
        )
        val values = arrayOf("mp3", "m4a", "wav")
        val currentIdx = values.indexOf(appConfig.audioRecFormat).let { if (it >= 0) it else 0 }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("انتخاب فرمت ضبط صوتی")
            .setSingleChoiceItems(formats, currentIdx) { dialog, which ->
                val chosen = values[which]
                appConfig.audioRecFormat = chosen
                val msg = "فرمت ضبط صوتی روی ${chosen.uppercase()} تنظیم شد"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                AccessibilityHelper.announce(containerSettingsList, msg)
                dialog.dismiss()
                openRecordingSettingsDialog()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showAudioBitrateDialog() {
        val bitrates = arrayOf(
            "96 kbps (کم‌حجم - مناسب رادیو)",
            "128 kbps (استاندارد - پیش‌فرض)",
            "192 kbps (کیفیت عالی)",
            "256 kbps (بسیار بالا)",
            "320 kbps (حداکثر کیفیت)"
        )
        val values = arrayOf("96k", "128k", "192k", "256k", "320k")
        val currentIdx = values.indexOf(appConfig.audioRecBitrate).let { if (it >= 0) it else 1 }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("انتخاب بیت‌ریت صدای ضبط")
            .setSingleChoiceItems(bitrates, currentIdx) { dialog, which ->
                val chosen = values[which]
                appConfig.audioRecBitrate = chosen
                val msg = "بیت‌ریت صدا روی $chosen تنظیم شد"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                AccessibilityHelper.announce(containerSettingsList, msg)
                dialog.dismiss()
                openRecordingSettingsDialog()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showAudioSampleRateDialog() {
        val rates = arrayOf(
            "خودکار (نرخ شبکه رسیور - پیش‌فرض)",
            "44100 هرتز (کیفیت صوتی CD)",
            "48000 هرتز (استاندارد شبکه‌های تلویزیونی)"
        )
        val values = arrayOf("auto", "44100", "48000")
        val currentIdx = values.indexOf(appConfig.audioRecSampleRate).let { if (it >= 0) it else 0 }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("انتخاب نرخ نمونه‌برداری صدا (Sample Rate)")
            .setSingleChoiceItems(rates, currentIdx) { dialog, which ->
                val chosen = values[which]
                appConfig.audioRecSampleRate = chosen
                val msg = "نرخ نمونه‌برداری روی ${getSampleRateTitle(chosen)} تنظیم شد"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                AccessibilityHelper.announce(containerSettingsList, msg)
                dialog.dismiss()
                openRecordingSettingsDialog()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showAudioChannelsDialog() {
        val channels = arrayOf(
            "استریو (دو کاناله - تفکیک کامل صدا - پیش‌فرض)",
            "مونو (تک کاناله - نصف شدن حجم فایل)"
        )
        val values = arrayOf("stereo", "mono")
        val currentIdx = values.indexOf(appConfig.audioRecChannels).let { if (it >= 0) it else 0 }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("انتخاب کانال صدای ضبط")
            .setSingleChoiceItems(channels, currentIdx) { dialog, which ->
                val chosen = values[which]
                appConfig.audioRecChannels = chosen
                val msg = if (chosen == "mono") "کانال صدا روی مونو تنظیم شد" else "کانال صدا روی استریو تنظیم شد"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                AccessibilityHelper.announce(containerSettingsList, msg)
                dialog.dismiss()
                openRecordingSettingsDialog()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showStorageFolderInfoDialog() {
        val rootPath = StorageManager.getAppRootDir(requireContext()).absolutePath
        val recPath = StorageManager.getRecordingsDir(requireContext()).absolutePath
        val msg = """
            پوشه اصلی برنامه در حافظه:
            $rootPath
            
            پوشه ذخیره فایل‌های ضبط شده:
            $recPath
            
            کلیه فایل‌های ضبط شده با فرمت‌های انتخاب شده شما در این پوشه ذخیره می‌شوند و در هر زمان از دکمه مشاهده فایل‌ها قابل مدیریت هستند.
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("اطلاعات پوشه ذخیره‌سازی")
            .setMessage(msg)
            .setPositiveButton("باز کردن در مدیریت فایل") { _, _ ->
                com.gmscreen.accessible.util.RecordingsDialogHelper.openRecordingsFolder(requireContext())
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun getVideoQualityTitle(quality: String): String {
        return when (quality.lowercase()) {
            "copy" -> "کیفیت اصلی شبکه (کپی مستقیم - پیش‌فرض)"
            "high" -> "فشرده‌سازی بسیار بالا (H.264 - CRF 18)"
            "medium" -> "فشرده‌سازی متعادل (H.264 - CRF 23)"
            "low" -> "فشرده‌سازی کم‌حجم (H.264 - 720p - CRF 28)"
            else -> quality
        }
    }

    private fun getNamingPatternTitle(pattern: String): String {
        return when (pattern.lowercase()) {
            "jalali_numeric" -> "شمسی عددی (1405-08-27) [پیش‌فرض]"
            "jalali_month_year" -> "شمسی با ماه و سال (27 آبان 1405)"
            "jalali_month" -> "شمسی با ماه (27 آبان)"
            "gregorian" -> "میلادی (2026-11-17)"
            else -> pattern
        }
    }

    private fun getSampleRateTitle(rate: String): String {
        return when (rate.lowercase()) {
            "auto" -> "خودکار (نرخ شبکه رسیور - پیش‌فرض)"
            "44100" -> "44100 هرتز (CD)"
            "48000" -> "48000 هرتز (تلویزیون)"
            else -> "$rate هرتز"
        }
    }

    private fun openEpgSettingsDialog() {
        val epgModeStr = if (appConfig.autoAnnounceEpg) "خودکار پس از تعویض کانال" else "خواندن دستی از منوی کانال (پیش‌فرض)"
        val epgDetailStr = when (appConfig.epgAnnounceDetail) {
            1 -> "عنوان برنامه به همراه ساعت شروع و پایان"
            2 -> "عنوان و ساعت فعلی + عنوان برنامه بعدی"
            else -> "فقط عنوان برنامه در حال پخش (پیش‌فرض)"
        }

        val items = arrayOf(
            "۱. اعلام خودکار نام شبکه با تعویض کانال: ${if (appConfig.autoAnnounceChannel) "فعال" else "غیرفعال"}",
            "۲. نحوه اعلام جدول برنامه EPG: $epgModeStr",
            "۳. جزئیات اطلاعات اعلامی EPG: $epgDetailStr"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تنظیمات جامع راهنمای برنامه‌ها (EPG)")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        appConfig.autoAnnounceChannel = !appConfig.autoAnnounceChannel
                        val state = if (appConfig.autoAnnounceChannel) "فعال" else "غیرفعال"
                        val msg = "اعلام خودکار شبکه: $state"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        AccessibilityHelper.announce(containerSettingsList, msg)
                        openEpgSettingsDialog()
                    }
                    1 -> showEpgModeDialog()
                    2 -> showEpgDetailDialog()
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun showEpgModeDialog() {
        val modes = arrayOf(
            "خواندن دستی از طریق منوی کانال (پیش‌فرض)",
            "خواندن خودکار پس از تعویض کانال (فقط در صورت وجود جدول برنامه)"
        )
        val currentIdx = if (appConfig.autoAnnounceEpg) 1 else 0

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("نحوه اعلام جدول برنامه EPG")
            .setSingleChoiceItems(modes, currentIdx) { dialog, which ->
                appConfig.autoAnnounceEpg = (which == 1)
                val msg = if (which == 1) "اعلام خودکار EPG با تعویض کانال فعال شد" else "اعلام EPG بر روی حالت دستی تنظیم شد"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                AccessibilityHelper.announce(containerSettingsList, msg)
                dialog.dismiss()
                openEpgSettingsDialog()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showEpgDetailDialog() {
        val details = arrayOf(
            "فقط عنوان برنامه در حال پخش (پیش‌فرض)",
            "عنوان برنامه به همراه ساعت شروع و پایان",
            "عنوان و ساعت برنامه فعلی + عنوان برنامه بعدی"
        )
        val currentIdx = appConfig.epgAnnounceDetail.coerceIn(0, 2)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("جزئیات اطلاعات اعلامی EPG")
            .setSingleChoiceItems(details, currentIdx) { dialog, which ->
                appConfig.epgAnnounceDetail = which
                val msg = "جزئیات EPG تنظیم شد: ${details[which]}"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                AccessibilityHelper.announce(containerSettingsList, msg)
                dialog.dismiss()
                openEpgSettingsDialog()
            }
            .setNegativeButton("انصراف", null)
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
        val currentOrientation = if (appConfig.videoLandscapeOrientation) "افقی (Landscape)" else "عمودی (Portrait)"
        val items = arrayOf(
            "ویبره و بازخورد لمسی (Haptic): ${if (appConfig.hapticFeedbackEnabled) "فعال" else "غیرفعال"}",
            "حفظ شماره سراسری کانال‌ها در فیلترها: ${if (appConfig.keepGlobalChannelNum) "فعال" else "غیرفعال"}",
            "جهت صفحه در پخش زنده ویدیویی: $currentOrientation"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تنظیمات دسترس‌پذیری و نمایش")
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
                    2 -> {
                        appConfig.videoLandscapeOrientation = !appConfig.videoLandscapeOrientation
                        val state = if (appConfig.videoLandscapeOrientation) "افقی (مناسب افراد بینا)" else "عمودی (پیش‌فرض)"
                        val msg = "جهت تصویر در پخش زنده: $state"
                        AccessibilityHelper.announce(containerSettingsList, msg)
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
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
