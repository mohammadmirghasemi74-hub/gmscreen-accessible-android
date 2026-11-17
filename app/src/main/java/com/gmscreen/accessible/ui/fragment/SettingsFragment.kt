package com.gmscreen.accessible.ui.fragment

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.protocol.RcuKey
import com.gmscreen.accessible.ui.InternalBrowserActivity
import com.gmscreen.accessible.util.AccessibilityHelper
import com.gmscreen.accessible.util.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

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
        val isConnected = repository.isConnected.value && repository.connectedIp.value.isNotBlank()
        if (!isConnected) {
            val msg = "وضعیت اتصال رسیور: رسیور در حال حاضر متصل نیست (قطع ارتباط).\n\nنکته: برای مشاهده مشخصات زنده، مدل، شماره سریال و ساعت رسیور، ابتدا در برگه اول به رسیور متصل شوید."
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("مشخصات و وضعیت سخت‌افزاری رسیور")
                .setMessage(msg)
                .setPositiveButton("بستن", null)
                .show()
            return
        }

        val ip = repository.connectedIp.value
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("مشخصات و وضعیت سخت‌افزاری رسیور")
            .setMessage("در حال استعلام مشخصات زنده از رسیور...")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val info = repository.client.fetchBoxInfo()
            val channelCount = repository.channels.value.size
            val isRadio = repository.currentStbMode == 1

            withContext(Dispatchers.Main) {
                loadingDialog.dismiss()
                val rawModel = info?.get("ProductName")?.toString()?.takeIf { it.isNotBlank() }
                    ?: repository.deviceModel.value.takeIf { it.isNotBlank() } ?: "نامشخص"
                val sw = info?.get("SoftwareVersion")?.toString()?.takeIf { it.isNotBlank() } ?: "نامشخص"
                val hw = info?.get("HardWareVersion")?.toString()?.takeIf { it.isNotBlank() } ?: "نامشخص"
                val sn = info?.get("SerialNumber")?.toString()?.takeIf { it.isNotBlank() } ?: "نامشخص"
                val modeText = if (isRadio) "رادیو (Radio)" else "تلویزیون (TV)"
                val chText = "$channelCount شبکه ذخیره شده"

                val timeStr = if (info != null && info.containsKey("Year")) {
                    val y = info["Year"]?.toString() ?: ""
                    val m = info["Month"]?.toString() ?: ""
                    val d = info["Day"]?.toString() ?: ""
                    val h = info["Hour"]?.toString()?.toIntOrNull() ?: 0
                    val min = info["Min"]?.toString()?.toIntOrNull() ?: 0
                    val sec = info["Sec"]?.toString()?.toIntOrNull() ?: 0
                    String.format(Locale.US, "%s/%s/%s - %02d:%02d:%02d", y, m, d, h, min, sec)
                } else {
                    "نامشخص"
                }

                val sb = StringBuilder()
                sb.append("نام دستگاه (Product Name): $rawModel\n")
                sb.append("وضعیت کارکرد (Working Mode): $modeText\n")
                sb.append("نسخه نرم‌افزار (Software Version): $sw\n")
                sb.append("نسخه سخت‌افزار (Hardware Version): $hw\n")
                sb.append("شماره سریال (Serial Number): $sn\n")
                sb.append("کانال‌های استفاده شده (Channel Used): $chText\n")
                sb.append("ساعت داخلی رسیور (STB Clock): $timeStr\n")
                sb.append("آدرس IP رسیور: $ip\n")
                sb.append("پورت کنترل: 20000 | پورت استریم: 554")

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("مشخصات و وضعیت زنده رسیور (STB Info)")
                    .setMessage(sb.toString())
                    .setPositiveButton("بستن", null)
                    .show()
            }
        }
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
        var draftShowInChannels = appConfig.showRecordingsInChannels
        var draftCustomDir = appConfig.customRecordingsDir
        var draftVideoFormat = appConfig.videoRecFormat
        var draftVideoQuality = appConfig.videoRecQuality
        var draftNamingPattern = appConfig.recNamingPattern
        var draftAudioFormat = appConfig.audioRecFormat
        var draftAudioBitrate = appConfig.audioRecBitrate
        var draftAudioSampleRate = appConfig.audioRecSampleRate
        var draftAudioChannels = appConfig.audioRecChannels

        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 20, 32, 20)
        }
        scrollView.addView(contentLayout)

        fun addSettingRow(
            title: String,
            initialValueText: String,
            onClick: (TextView, View) -> Unit
        ): Pair<LinearLayout, TextView> {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 18, 16, 18)
                isClickable = true
                isFocusable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
            }
            val txtTitle = TextView(requireContext()).apply {
                text = title
                setTextColor(resources.getColor(R.color.text_primary, null))
                textSize = 15f
                paint.isFakeBoldText = true
            }
            val txtVal = TextView(requireContext()).apply {
                text = initialValueText
                setTextColor(resources.getColor(R.color.accent_yellow, null))
                textSize = 13f
                setPadding(0, 4, 0, 0)
            }
            row.addView(txtTitle)
            row.addView(txtVal)
            row.contentDescription = "$title: $initialValueText. برای تغییر ضربه بزنید."
            row.setOnClickListener {
                onClick(txtVal, row)
            }
            contentLayout.addView(row)
            return Pair(row, txtVal)
        }

        // 1. Show button in channels
        addSettingRow(
            "۱. نمایش دکمه فایل‌های ضبط‌شده در بخش کانال‌ها",
            if (draftShowInChannels) "فعال (پیش‌فرض)" else "غیرفعال"
        ) { txtVal, row ->
            val options = arrayOf("فعال (نمایش دکمه شناور)", "غیرفعال (مخفی کردن از بخش کانال‌ها)")
            val curIdx = if (draftShowInChannels) 0 else 1
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("نمایش دکمه در بخش کانال‌ها")
                .setSingleChoiceItems(options, curIdx) { dlg, which ->
                    draftShowInChannels = (which == 0)
                    val label = if (draftShowInChannels) "فعال (پیش‌فرض)" else "غیرفعال"
                    txtVal.text = label
                    row.contentDescription = "نمایش دکمه فایل‌های ضبط در بخش کانال‌ها: $label"
                    AccessibilityHelper.announce(row, label)
                    dlg.dismiss()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        // 2. Custom storage directory
        val getDirDisplay: (String) -> String = { dir ->
            when {
                dir.isBlank() -> "پوشه پیش‌فرض برنامه (/sdcard/GMScreen/recordings)"
                dir.contains("Download") -> "پوشه دانلودها ($dir)"
                dir.contains("Movies") -> "پوشه فیلم‌ها ($dir)"
                else -> "پوشه سفارشی: $dir"
            }
        }
        addSettingRow("۲. پوشه ذخیره فایل‌های ضبط", getDirDisplay(draftCustomDir)) { txtVal, row ->
            val dirs = arrayOf(
                "پوشه پیش‌فرض برنامه (/sdcard/GMScreen/recordings)",
                "پوشه دانلودهای گوشی (/sdcard/Download/GMScreen)",
                "پوشه فیلم‌ها و ویدیوها (/sdcard/Movies/GMScreen)",
                "ورود مسیر پوشه دلخواه دستی..."
            )
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("انتخاب پوشه ذخیره فایل‌های ضبط")
                .setItems(dirs) { _, which ->
                    when (which) {
                        0 -> {
                            draftCustomDir = ""
                            val label = getDirDisplay(draftCustomDir)
                            txtVal.text = label
                            row.contentDescription = "پوشه ذخیره: $label"
                            AccessibilityHelper.announce(row, label)
                        }
                        1 -> {
                            draftCustomDir = File(Environment.getExternalStorageDirectory(), "Download/GMScreen").absolutePath
                            val label = getDirDisplay(draftCustomDir)
                            txtVal.text = label
                            row.contentDescription = "پوشه ذخیره: $label"
                            AccessibilityHelper.announce(row, label)
                        }
                        2 -> {
                            draftCustomDir = File(Environment.getExternalStorageDirectory(), "Movies/GMScreen").absolutePath
                            val label = getDirDisplay(draftCustomDir)
                            txtVal.text = label
                            row.contentDescription = "پوشه ذخیره: $label"
                            AccessibilityHelper.announce(row, label)
                        }
                        3 -> {
                            val input = EditText(requireContext()).apply {
                                hint = "/sdcard/..."
                                setText(draftCustomDir.ifBlank { "/sdcard/GMScreen/recordings" })
                            }
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("ورود مسیر پوشه دلخواه")
                                .setView(input)
                                .setPositiveButton("تأیید") { _, _ ->
                                    val custom = input.text.toString().trim()
                                    if (custom.isNotBlank()) {
                                        draftCustomDir = custom
                                        val label = getDirDisplay(draftCustomDir)
                                        txtVal.text = label
                                        row.contentDescription = "پوشه ذخیره: $label"
                                        AccessibilityHelper.announce(row, label)
                                    }
                                }
                                .setNegativeButton("انصراف", null)
                                .show()
                        }
                    }
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        // 3. Video recording format
        addSettingRow("۳. فرمت ضبط تصویری (ویدیو)", draftVideoFormat.uppercase()) { txtVal, row ->
            val formats = arrayOf("MP4 (پیش‌فرض - سازگار با همه دستگاه‌ها)", "MKV (کیفیت چندرسانه‌ای)", "TS (جریان اصلی بدون تغییر کانتینر)")
            val values = arrayOf("mp4", "mkv", "ts")
            val cur = values.indexOf(draftVideoFormat).let { if (it >= 0) it else 0 }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("انتخاب فرمت ضبط تصویری")
                .setSingleChoiceItems(formats, cur) { dlg, which ->
                    draftVideoFormat = values[which]
                    txtVal.text = draftVideoFormat.uppercase()
                    row.contentDescription = "فرمت ویدیو: ${draftVideoFormat.uppercase()}"
                    AccessibilityHelper.announce(row, draftVideoFormat.uppercase())
                    dlg.dismiss()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        // 4. Video quality
        addSettingRow("۴. کیفیت و نحوه ذخیره ویدیو", getVideoQualityTitle(draftVideoQuality)) { txtVal, row ->
            val qualities = arrayOf(
                "کیفیت اصلی شبکه (کپی مستقیم - بدون افت کیفیت و بدون مصرف پردازنده - پیش‌فرض)",
                "فشرده‌سازی بسیار بالا (H.264 - CRF 18)",
                "فشرده‌سازی متعادل (H.264 - CRF 23)",
                "فشرده‌سازی کم‌حجم و سبک (H.264 - 720p - CRF 28)"
            )
            val values = arrayOf("copy", "high", "medium", "low")
            val cur = values.indexOf(draftVideoQuality).let { if (it >= 0) it else 0 }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("کیفیت ذخیره ویدیو")
                .setSingleChoiceItems(qualities, cur) { dlg, which ->
                    draftVideoQuality = values[which]
                    val label = getVideoQualityTitle(draftVideoQuality)
                    txtVal.text = label
                    row.contentDescription = "کیفیت ویدیو: $label"
                    AccessibilityHelper.announce(row, label)
                    dlg.dismiss()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        // 5. Naming pattern
        addSettingRow("۵. الگوی نام‌گذاری فایل‌های ضبط", getNamingPatternTitle(draftNamingPattern)) { txtVal, row ->
            val patterns = arrayOf(
                "شمسی عددی استاندارد (مثال: 1405-08-27) [پیش‌فرض]",
                "شمسی با نام ماه و سال (مثال: 27 آبان 1405)",
                "شمسی با نام ماه بدون سال (مثال: 27 آبان)",
                "تاریخ و زمان میلادی (مثال: 2026-11-17)"
            )
            val values = arrayOf("jalali_numeric", "jalali_month_year", "jalali_month", "gregorian")
            val cur = values.indexOf(draftNamingPattern).let { if (it >= 0) it else 0 }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("الگوی نام‌گذاری فایل‌های ضبط")
                .setSingleChoiceItems(patterns, cur) { dlg, which ->
                    draftNamingPattern = values[which]
                    val label = getNamingPatternTitle(draftNamingPattern)
                    txtVal.text = label
                    row.contentDescription = "الگوی نام‌گذاری: $label"
                    AccessibilityHelper.announce(row, label)
                    dlg.dismiss()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        // 6. Audio recording format
        addSettingRow("۶. فرمت ضبط صوتی (صدا)", draftAudioFormat.uppercase()) { txtVal, row ->
            val formats = arrayOf("MP3 (پیش‌فرض - سازگار با همه دستگاه‌ها)", "M4A (کدک مدرن AAC با شفافیت بالا)", "WAV (کیفیت خام استودیویی)")
            val values = arrayOf("mp3", "m4a", "wav")
            val cur = values.indexOf(draftAudioFormat).let { if (it >= 0) it else 0 }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("انتخاب فرمت ضبط صوتی")
                .setSingleChoiceItems(formats, cur) { dlg, which ->
                    draftAudioFormat = values[which]
                    txtVal.text = draftAudioFormat.uppercase()
                    row.contentDescription = "فرمت صوتی: ${draftAudioFormat.uppercase()}"
                    AccessibilityHelper.announce(row, draftAudioFormat.uppercase())
                    dlg.dismiss()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        // 7. Audio bitrate
        addSettingRow("۷. بیت‌ریت صدای ضبط", draftAudioBitrate) { txtVal, row ->
            val bitrates = arrayOf("96 kbps (کم‌حجم)", "128 kbps (استاندارد - پیش‌فرض)", "192 kbps (کیفیت عالی)", "256 kbps (بسیار بالا)", "320 kbps (حداکثر)")
            val values = arrayOf("96k", "128k", "192k", "256k", "320k")
            val cur = values.indexOf(draftAudioBitrate).let { if (it >= 0) it else 1 }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("انتخاب بیت‌ریت صدای ضبط")
                .setSingleChoiceItems(bitrates, cur) { dlg, which ->
                    draftAudioBitrate = values[which]
                    txtVal.text = draftAudioBitrate
                    row.contentDescription = "بیت‌ریت صدا: $draftAudioBitrate"
                    AccessibilityHelper.announce(row, draftAudioBitrate)
                    dlg.dismiss()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        // 8. Audio sample rate
        addSettingRow("۸. نرخ نمونه‌برداری صدا (Sample Rate)", getSampleRateTitle(draftAudioSampleRate)) { txtVal, row ->
            val rates = arrayOf("خودکار (نرخ شبکه رسیور - پیش‌فرض)", "44100 هرتز (کیفیت صوتی CD)", "48000 هرتز (استاندارد شبکه‌های تلویزیونی)")
            val values = arrayOf("auto", "44100", "48000")
            val cur = values.indexOf(draftAudioSampleRate).let { if (it >= 0) it else 0 }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("نرخ نمونه‌برداری صدا")
                .setSingleChoiceItems(rates, cur) { dlg, which ->
                    draftAudioSampleRate = values[which]
                    val label = getSampleRateTitle(draftAudioSampleRate)
                    txtVal.text = label
                    row.contentDescription = "نرخ نمونه‌برداری: $label"
                    AccessibilityHelper.announce(row, label)
                    dlg.dismiss()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        // 9. Audio channels
        addSettingRow("۹. کانال صدای ضبط", if (draftAudioChannels == "mono") "مونو (تک کاناله)" else "استریو (دو کاناله - پیش‌فرض)") { txtVal, row ->
            val channels = arrayOf("استریو (دو کاناله - تفکیک کامل صدا - پیش‌فرض)", "مونو (تک کاناله - نصف شدن حجم فایل)")
            val values = arrayOf("stereo", "mono")
            val cur = values.indexOf(draftAudioChannels).let { if (it >= 0) it else 0 }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("کانال صدای ضبط")
                .setSingleChoiceItems(channels, cur) { dlg, which ->
                    draftAudioChannels = values[which]
                    val label = if (draftAudioChannels == "mono") "مونو (تک کاناله)" else "استریو (دو کاناله - پیش‌فرض)"
                    txtVal.text = label
                    row.contentDescription = "کانال صدا: $label"
                    AccessibilityHelper.announce(row, label)
                    dlg.dismiss()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تنظیمات جامع ضبط و ذخیره‌سازی")
            .setView(scrollView)
            .setPositiveButton("ذخیره تنظیمات") { _, _ ->
                appConfig.showRecordingsInChannels = draftShowInChannels
                appConfig.customRecordingsDir = draftCustomDir
                appConfig.videoRecFormat = draftVideoFormat
                appConfig.videoRecQuality = draftVideoQuality
                appConfig.recNamingPattern = draftNamingPattern
                appConfig.audioRecFormat = draftAudioFormat
                appConfig.audioRecBitrate = draftAudioBitrate
                appConfig.audioRecSampleRate = draftAudioSampleRate
                appConfig.audioRecChannels = draftAudioChannels

                val msg = "تنظیمات ضبط با موفقیت ذخیره شد"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                AccessibilityHelper.announce(containerSettingsList, msg)
            }
            .setNeutralButton("📁 مشاهده فایل‌ها") { _, _ ->
                com.gmscreen.accessible.util.RecordingsDialogHelper.showRecordingsDialog(requireContext())
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
