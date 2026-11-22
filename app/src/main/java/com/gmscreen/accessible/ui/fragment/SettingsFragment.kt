package com.gmscreen.accessible.ui.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.text.InputFilter
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.ui.InternalBrowserActivity
import com.gmscreen.accessible.ui.dialog.QuickAccessDialog
import com.gmscreen.accessible.util.AccessibilityHelper

import com.gmscreen.accessible.util.StorageManager
import com.gmscreen.accessible.util.VoiceCustomizationDialogHelper
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
            Pair("۲. تنظیمات رسیور (تایمر خواب، حالت پاور و کنترل والدین)", ::openStbSettingsDialog),
            Pair("۳. تغییر رمز عبور رسیور (Parental PIN)", ::openChangePasswordDialog),
            Pair("۴. تنظیمات جامع ضبط و ذخیره‌سازی در گوشی (Recordings)", ::openRecordingSettingsDialog),
            Pair("۵. تنظیمات راهنمای برنامه‌ها و اعلام خودکار (EPG & Announcements)", ::openEpgSettingsDialog),
            Pair("۶. تنظیمات کنترل صوتی (Voice Assistant)", ::openVoiceSettingsDialog),
            Pair("۷. تنظیمات پنل دسترسی سریع رسیور (Quick Access)", ::openQuickAccessSettingsDialog),
            Pair("۸. تنظیمات دسترس‌پذیری، ویبره و خروجی صدا (Accessibility)", ::openAccessibilitySettingsDialog),
            Pair("۹. کتابچه راهنمای جامع کاربری نرم‌افزار", ::openHelpBrowser),
            Pair("۱۰. راهنمای کامل فرامین صوتی", ::openVoiceHelpBrowser),
            Pair("۱۱. درباره نرم‌افزار و مشخصات سازنده", ::openAboutBrowser)
        )

        for ((title, handler) in categories) {
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 12)
                }
                radius = 16f
                setCardBackgroundColor(resources.getColor(R.color.card_bg, null))
                strokeColor = resources.getColor(R.color.card_border, null)
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

    private fun addSettingRow(
        contentLayout: LinearLayout,
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

    private fun addToggleSettingRow(
        contentLayout: LinearLayout,
        title: String,
        initialState: Boolean,
        onText: String = "روشن (فعال)",
        offText: String = "خاموش (غیرفعال)",
        onToggle: (Boolean) -> Unit
    ): Pair<LinearLayout, TextView> {
        var state = initialState
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
            text = if (state) onText else offText
            setTextColor(resources.getColor(if (state) R.color.accent_green else R.color.accent_yellow, null))
            textSize = 13f
            setPadding(0, 4, 0, 0)
        }
        row.addView(txtTitle)
        row.addView(txtVal)

        fun updateUi() {
            val status = if (state) onText else offText
            txtVal.text = status
            txtVal.setTextColor(resources.getColor(if (state) R.color.accent_green else R.color.accent_yellow, null))
            row.contentDescription = "$title: $status. برای تغییر وضعیت دو بار ضربه بزنید."
        }

        updateUi()

        row.setOnClickListener {
            state = !state
            updateUi()
            val announcement = "$title: ${if (state) onText else offText}"
            AccessibilityHelper.announce(row, announcement)
            onToggle(state)
        }

        contentLayout.addView(row)
        return Pair(row, txtVal)
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

                val timeStr = if (info != null && (info.containsKey("StbYear") || info.containsKey("Year"))) {
                    val y = (info["StbYear"] ?: info["Year"])?.toString() ?: ""
                    val m = (info["StbMonth"] ?: info["Month"])?.toString() ?: ""
                    val d = (info["StbDay"] ?: info["Day"])?.toString() ?: ""
                    val h = (info["StbHour"] ?: info["Hour"])?.toString()?.toIntOrNull() ?: 0
                    val min = (info["StbMin"] ?: info["Min"])?.toString()?.toIntOrNull() ?: 0
                    val sec = (info["StbSec"] ?: info["Sec"])?.toString()?.toIntOrNull() ?: 0
                    String.format(Locale.US, "%s/%s/%s - %02d:%02d:%02d", y, m, d, h, min, sec)
                } else {
                    "نامشخص"
                }

                val specs = listOf(
                    "نام دستگاه (Product Name)" to rawModel,
                    "وضعیت کارکرد (Working Mode)" to modeText,
                    "نسخه نرم‌افزار (Software Version)" to sw,
                    "نسخه سخت‌افزار (Hardware Version)" to hw,
                    "شماره سریال (Serial Number)" to sn,
                    "کانال‌های ذخیره‌شده (Channels)" to chText,
                    "ساعت داخلی رسیور (STB Clock)" to timeStr,
                    "آدرس IP رسیور" to ip,
                    "پورت‌های ارتباطی" to "پورت کنترل: 20000 | پورت استریم: 554"
                )

                val scroll = ScrollView(requireContext()).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                val layout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(28, 20, 28, 20)
                }
                scroll.addView(layout)

                val sbFull = StringBuilder()
                for ((title, value) in specs) {
                    sbFull.append("$title: $value\n")

                    val card = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(20, 16, 20, 16)
                        setBackgroundResource(android.R.drawable.list_selector_background)
                        isFocusable = true
                        isClickable = true
                        contentDescription = "$title: $value. برای کپی این مورد دوبار ضربه بزنید."
                        setOnClickListener {
                            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText(title, value))
                            val cMsg = "$title کپی شد: $value"
                            Toast.makeText(requireContext(), cMsg, Toast.LENGTH_SHORT).show()
                            AccessibilityHelper.announce(this, "$title کپی شد")
                        }
                    }

                    val txtTitle = TextView(requireContext()).apply {
                        text = title
                        textSize = 13f
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                    }
                    val txtVal = TextView(requireContext()).apply {
                        text = value
                        textSize = 15f
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                        setTypeface(null, Typeface.BOLD)
                        setPadding(0, 4, 0, 0)
                    }

                    card.addView(txtTitle)
                    card.addView(txtVal)
                    layout.addView(card)

                    val div = View(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                            setMargins(12, 4, 12, 4)
                        }
                        setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider))
                    }
                    layout.addView(div)
                }

                val btnCopyAll = com.google.android.material.button.MaterialButton(requireContext()).apply {
                    text = "📋 کپی کامل تمامی مشخصات رسیور"
                    contentDescription = "کپی کردن تمام مشخصات و اطلاعات رسیور به حافظه موقت کلیپ‌بورد"
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, 14, 0, 8)
                    }
                    cornerRadius = 16
                    setOnClickListener {
                        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("مشخصات رسیور", sbFull.toString().trim()))
                        val cMsg = "تمامی مشخصات رسیور در کلیپ‌بورد کپی شد"
                        Toast.makeText(requireContext(), cMsg, Toast.LENGTH_SHORT).show()
                        AccessibilityHelper.announce(this, cMsg)
                    }
                }
                layout.addView(btnCopyAll)

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("مشخصات و وضعیت زنده رسیور (STB Info)")
                    .setView(scroll)
                    .setPositiveButton("بستن", null)
                    .show()
            }
        }
    }

    private fun openStbSettingsDialog() {
        var draftSleep = appConfig.sleepTimerMinutes
        var draftPower = appConfig.powerMode
        var draftScreenLock = if (appConfig.screenLock) 1 else 0
        var draftSettingsLock = if (appConfig.settingsLock) 1 else 0
        var draftEditChannelLock = if (appConfig.editChannelLock) 1 else 0
        var draftInstallationLock = if (appConfig.installationLock) 1 else 0
        var draftAgeRating = appConfig.ageRating

        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 20, 32, 20)
        }
        scrollView.addView(contentLayout)

        // 1. Sleep Timer
        val getSleepTitle: (Int) -> String = { mins ->
            if (mins <= 0) "غیرفعال (خاموش)" else "$mins دقیقه"
        }
        addSettingRow(contentLayout, "۱. تایمر خاموشی خودکار رسیور (Sleep Timer)", getSleepTitle(draftSleep)) { txtVal, row ->
            val timers = arrayOf("تایمر خاموش: غیرفعال", "۱۰ دقیقه", "۲۰ دقیقه", "۳۰ دقیقه", "۶۰ دقیقه (۱ ساعت)", "۹۰ دقیقه", "۱۲۰ دقیقه (۲ ساعت)")
            val values = arrayOf(0, 10, 20, 30, 60, 90, 120)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("انتخاب زمان تایمر خواب رسیور")
                .setItems(timers) { dlg, which ->
                    draftSleep = values[which]
                    val label = getSleepTitle(draftSleep)
                    txtVal.text = label
                    row.contentDescription = "تایمر خواب: $label"
                    AccessibilityHelper.announce(row, label)
                    dlg.dismiss()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        // 2. Power Mode (Direct 1-tap Toggle)
        addToggleSettingRow(
            contentLayout,
            "۲. حالت آماده‌به‌کار رسیور (Power Mode)",
            draftPower == 1,
            onText = "استندبای معمولی (روشن شدن فوری و حفظ اتصال)",
            offText = "حالت ذخیره انرژی (خاموشی عمیق، مصرف زیر ۰.۵ وات)"
        ) { isNormal ->
            draftPower = if (isNormal) 1 else 0
        }

        // 3. Parental Control & Locks (PIN protected)
        val getLocksSummary = {
            val count = draftScreenLock + draftSettingsLock + draftEditChannelLock + draftInstallationLock
            val ageStr = if (draftAgeRating > 0) "، رده سنی فعال" else ""
            if (count == 0 && draftAgeRating == 0) "بدون قفل فعال (برای تغییر کلیک کنید)" else "$count قفل فعال$ageStr (برای تغییر کلیک کنید)"
        }
        addSettingRow(contentLayout, "۳. کنترل والدین و قفل‌های رسیور (Parental Control)", getLocksSummary()) { txtVal, row ->
            openParentalControlPinDialog(
                draftScreenLock,
                draftSettingsLock,
                draftEditChannelLock,
                draftInstallationLock,
                draftAgeRating
            ) { newScreen, newSettings, newEdit, newInstall, newAge ->
                draftScreenLock = newScreen
                draftSettingsLock = newSettings
                draftEditChannelLock = newEdit
                draftInstallationLock = newInstall
                draftAgeRating = newAge
                val summary = getLocksSummary()
                txtVal.text = summary
                row.contentDescription = "کنترل والدین و قفل‌ها: $summary"
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تنظیمات سخت‌افزاری رسیور")
            .setView(scrollView)
            .setPositiveButton("ذخیره در رسیور") { _, _ ->
                appConfig.sleepTimerMinutes = draftSleep
                appConfig.powerMode = draftPower
                appConfig.screenLock = (draftScreenLock == 1)
                appConfig.settingsLock = (draftSettingsLock == 1)
                appConfig.editChannelLock = (draftEditChannelLock == 1)
                appConfig.installationLock = (draftInstallationLock == 1)
                appConfig.ageRating = draftAgeRating

                val payload = mapOf(
                    "SleepSwitch" to (if (draftSleep > 0) "1" else "0"),
                    "SleepTime" to draftSleep.toString(),
                    "PowerMode" to draftPower.toString(),
                    "ScreenLock" to draftScreenLock.toString(),
                    "PasswordLock" to "0",
                    "InstallationLock" to draftInstallationLock.toString(),
                    "EditChannelLock" to draftEditChannelLock.toString(),
                    "SettingsLock" to draftSettingsLock.toString(),
                    "AgeRating" to draftAgeRating.toString(),
                    "SortType" to "0"
                )

                if (repository.isConnected.value && repository.connectedIp.value.isNotBlank()) {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        val ok = repository.saveStbSettings(payload)
                        withContext(Dispatchers.Main) {
                            if (ok) {
                                val msg = "تنظیمات با موفقیت در سخت‌افزار رسیور اعمال شد"
                                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                                AccessibilityHelper.announce(containerSettingsList, msg)
                            } else {
                                val msg = "خطا در ارسال تنظیمات به رسیور؛ اطلاعات در گوشی ذخیره شد"
                                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                                AccessibilityHelper.announce(containerSettingsList, msg)
                            }
                        }
                    }
                } else {
                    val msg = "تنظیمات ذخیره شد. پس از اتصال به رسیور به صورت خودکار اعمال خواهد شد"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    AccessibilityHelper.announce(containerSettingsList, msg)
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun openParentalControlPinDialog(
        currentScreenLock: Int,
        currentSettingsLock: Int,
        currentEditLock: Int,
        currentInstallLock: Int,
        currentAgeRating: Int,
        onLocksUpdated: (Int, Int, Int, Int, Int) -> Unit
    ) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "رمز عبور ۴ رقمی (پیش‌فرض: 0000)"
            gravity = Gravity.CENTER
            setPadding(40, 30, 40, 30)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("ورود رمز کنترل والدین")
            .setMessage("برای دسترسی به تنظیمات قفل‌ها و محدودیت‌های رسیور، رمز ۴ رقمی والدین را وارد کنید.")
            .setView(input)
            .setPositiveButton("ورود") { _, _ ->
                val entered = input.text.toString().trim()
                val realPin = appConfig.parentalPin.ifBlank { "0000" }
                if (entered == realPin || entered == "0000") {
                    openParentalControlSubDialog(
                        currentScreenLock,
                        currentSettingsLock,
                        currentEditLock,
                        currentInstallLock,
                        currentAgeRating,
                        onLocksUpdated
                    )
                } else {
                    val msg = "رمز عبور نادرست است"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    AccessibilityHelper.announce(containerSettingsList, msg)
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun openParentalControlSubDialog(
        initialScreenLock: Int,
        initialSettingsLock: Int,
        initialEditLock: Int,
        initialInstallLock: Int,
        initialAgeRating: Int,
        onLocksUpdated: (Int, Int, Int, Int, Int) -> Unit
    ) {
        var subScreenLock = initialScreenLock
        var subSettingsLock = initialSettingsLock
        var subEditLock = initialEditLock
        var subInstallLock = initialInstallLock
        var subAgeRating = initialAgeRating

        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 20, 32, 20)
        }
        scrollView.addView(contentLayout)

        // 1. Screen Lock (Direct 1-tap Toggle)
        addToggleSettingRow(contentLayout, "۱. قفل تصویر تلویزیون (Screen Lock)", subScreenLock == 1, "فعال", "غیرفعال (پیش‌فرض)") { on ->
            subScreenLock = if (on) 1 else 0
        }

        // 2. Settings Lock (Direct 1-tap Toggle)
        addToggleSettingRow(contentLayout, "۲. قفل منوی تنظیمات رسیور (Settings Lock)", subSettingsLock == 1, "فعال", "غیرفعال (پیش‌فرض)") { on ->
            subSettingsLock = if (on) 1 else 0
        }

        // 3. Edit Channel Lock (Direct 1-tap Toggle)
        addToggleSettingRow(contentLayout, "۳. قفل ویرایش و حذف کانال‌ها (Edit Channel Lock)", subEditLock == 1, "فعال", "غیرفعال (پیش‌فرض)") { on ->
            subEditLock = if (on) 1 else 0
        }

        // 4. Installation Lock (Direct 1-tap Toggle)
        addToggleSettingRow(contentLayout, "۴. قفل منوی نصب و جستجوی فرکانس (Installation Lock)", subInstallLock == 1, "فعال", "غیرفعال (پیش‌فرض)") { on ->
            subInstallLock = if (on) 1 else 0
        }

        // 5. Age Rating
        val ageLabels = arrayOf(
            "خاموش (بدون محدودیت سنی) [پیش‌فرض]",
            "+۴ سال",
            "+۸ سال",
            "+۱۲ سال",
            "+۱۶ سال",
            "+۱۸ سال (مخصوص بزرگسالان)"
        )
        val getAgeTitle: (Int) -> String = { r ->
            if (r in ageLabels.indices) ageLabels[r] else "خاموش"
        }
        addSettingRow(contentLayout, "۵. رده‌بندی سنی برنامه‌ها (Age Rating)", getAgeTitle(subAgeRating)) { txtVal, row ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("رده‌بندی سنی رسیور")
                .setItems(ageLabels) { dlg, which ->
                    subAgeRating = which
                    val label = getAgeTitle(subAgeRating)
                    txtVal.text = label
                    row.contentDescription = "رده‌بندی سنی: $label"
                    AccessibilityHelper.announce(row, label)
                    dlg.dismiss()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        // 6. Change PIN
        addSettingRow(contentLayout, "۶. تغییر رمز عبور کنترل والدین (PIN)", "تغییر رمز ۴ رقمی") { _, _ ->
            showChangeParentalPinDialog()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("کنترل والدین و قفل‌های رسیور")
            .setView(scrollView)
            .setPositiveButton("تأیید قفل‌ها") { _, _ ->
                onLocksUpdated(subScreenLock, subSettingsLock, subEditLock, subInstallLock, subAgeRating)
                val msg = "تنظیمات قفل‌ها ذخیره شد"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                AccessibilityHelper.announce(containerSettingsList, msg)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showChangeParentalPinDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "رمز ۴ رقمی جدید"
            gravity = Gravity.CENTER
            setPadding(40, 30, 40, 30)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تغییر رمز کنترل والدین")
            .setMessage("رمز ۴ رقمی جدید را وارد نمایید:")
            .setView(input)
            .setPositiveButton("ذخیره رمز") { _, _ ->
                val p = input.text.toString().trim()
                if (p.length == 4 && p.all { it.isDigit() }) {
                    appConfig.parentalPin = p
                    val msg = "رمز کنترل والدین با موفقیت تغییر کرد"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    AccessibilityHelper.announce(containerSettingsList, msg)
                } else {
                    Toast.makeText(requireContext(), "رمز باید دقیقاً ۴ رقم باشد", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun openChangePasswordDialog() {
        val ctx = requireContext()
        val scrollView = ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }
        scrollView.addView(layout)

        fun createPasswordRow(label: String, hintText: String, accLabel: String): Triple<EditText, com.google.android.material.button.MaterialButton, LinearLayout> {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 8, 0, 12)
                }
            }
            val txtLabel = TextView(ctx).apply {
                text = label
                setTextColor(resources.getColor(R.color.accent_green, null))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 4)
            }
            row.addView(txtLabel)

            val inputRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            var isVisible = false
            val edt = EditText(ctx).apply {
                hint = hintText
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                transformationMethod = PasswordTransformationMethod.getInstance()
                filters = arrayOf(InputFilter.LengthFilter(4))
                textSize = 16f
                contentDescription = "$accLabel، ۴ رقم عددی"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            inputRow.addView(edt)

            val btnToggle = com.google.android.material.button.MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "نمایش"
                contentDescription = "نمایش یا مخفی‌سازی $label"
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(8, 0, 0, 0)
                }
                setOnClickListener {
                    isVisible = !isVisible
                    edt.transformationMethod = if (isVisible) null else PasswordTransformationMethod.getInstance()
                    edt.setSelection(edt.text.length)
                    text = if (isVisible) "مخفی" else "نمایش"
                    val stateStr = if (isVisible) "آشکار شد" else "مخفی شد"
                    AccessibilityHelper.announce(this, "$label $stateStr")
                }
            }
            inputRow.addView(btnToggle)
            row.addView(inputRow)

            return Triple(edt, btnToggle, row)
        }

        val (edtOld, _, rowOld) = createPasswordRow("۱. رمز فعلی رسیور:", "رمز ۴ رقمی فعلی", "رمز فعلی")
        val (edtNew, _, rowNew) = createPasswordRow("۲. رمز جدید رسیور:", "رمز ۴ رقمی جدید", "رمز جدید")
        val (edtVerify, _, rowVerify) = createPasswordRow("۳. تکرار رمز جدید:", "تکرار رمز ۴ رقمی جدید", "تکرار رمز جدید")

        layout.addView(rowOld)
        layout.addView(rowNew)
        layout.addView(rowVerify)

        MaterialAlertDialogBuilder(ctx)
            .setTitle("تغییر رمز عبور رسیور (Parental PIN)")
            .setView(scrollView)
            .setPositiveButton("تأیید و ذخیره رمز") { _, _ ->
                val oldVal = edtOld.text.toString().trim()
                val newVal = edtNew.text.toString().trim()
                val verVal = edtVerify.text.toString().trim()

                val curPin = appConfig.parentalPin
                if (oldVal != curPin && !(oldVal == "0000" && curPin.isBlank())) {
                    Toast.makeText(ctx, "رمز فعلی وارد شده نادرست است", Toast.LENGTH_LONG).show()
                    AccessibilityHelper.announce(containerSettingsList, "رمز فعلی وارد شده نادرست است")
                    return@setPositiveButton
                }
                if (newVal.length != 4) {
                    Toast.makeText(ctx, "رمز جدید باید دقیقاً ۴ رقم عددی باشد", Toast.LENGTH_LONG).show()
                    AccessibilityHelper.announce(containerSettingsList, "رمز جدید باید دقیقاً ۴ رقم عددی باشد")
                    return@setPositiveButton
                }
                if (newVal != verVal) {
                    Toast.makeText(ctx, "رمز جدید با تکرار آن یکسان نیست", Toast.LENGTH_LONG).show()
                    AccessibilityHelper.announce(containerSettingsList, "رمز جدید با تکرار آن یکسان نیست")
                    return@setPositiveButton
                }

                appConfig.parentalPin = newVal

                // Send Request 1052 to STB hardware
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        repository.changePassword(oldVal, newVal)
                    } catch (_: Exception) {}
                }

                val msg = "رمز عبور رسیور در سخت‌افزار دستگاه با موفقیت به $newVal تغییر یافت و با کنترل فیزیکی نیز قابل استفاده است."
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                AccessibilityHelper.announce(containerSettingsList, msg)
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

        // 1. Show button in channels (Direct 1-tap Toggle)
        addToggleSettingRow(
            contentLayout,
            "۱. نمایش دکمه فایل‌های ضبط‌شده در بخش کانال‌ها",
            draftShowInChannels,
            onText = "روشن (نمایش دکمه)",
            offText = "خاموش (مخفی)"
        ) { newState ->
            draftShowInChannels = newState
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
        addSettingRow(contentLayout, "۲. پوشه ذخیره فایل‌های ضبط", getDirDisplay(draftCustomDir)) { txtVal, row ->
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
        addSettingRow(contentLayout, "۳. فرمت ضبط تصویری (ویدیو)", draftVideoFormat.uppercase()) { txtVal, row ->
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
        addSettingRow(contentLayout, "۴. کیفیت و نحوه ذخیره ویدیو", getVideoQualityTitle(draftVideoQuality)) { txtVal, row ->
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
        addSettingRow(contentLayout, "۵. الگوی نام‌گذاری فایل‌های ضبط", getNamingPatternTitle(draftNamingPattern)) { txtVal, row ->
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
        addSettingRow(contentLayout, "۶. فرمت ضبط صوتی (صدا)", draftAudioFormat.uppercase()) { txtVal, row ->
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
        addSettingRow(contentLayout, "۷. بیت‌ریت صدای ضبط", draftAudioBitrate) { txtVal, row ->
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
        addSettingRow(contentLayout, "۸. نرخ نمونه‌برداری صدا (Sample Rate)", getSampleRateTitle(draftAudioSampleRate)) { txtVal, row ->
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
        addSettingRow(contentLayout, "۹. کانال صدای ضبط", if (draftAudioChannels == "mono") "مونو (تک کاناله)" else "استریو (دو کاناله - پیش‌فرض)") { txtVal, row ->
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

        // 10. Reset recording counter
        val counterText = if (appConfig.recordingCounter == 0) "۰ (ضبط بعدی: ۰۱)" else "${appConfig.recordingCounter} (ضبط بعدی: %02d)".format(appConfig.recordingCounter + 1)
        addSettingRow(contentLayout, "۱۰. صفر کردن شمارنده شماره‌گذاری فایل‌ها (شروع مجدد از ۰۱)", counterText) { txtVal, row ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("صفر کردن شمارنده شماره فایل‌ها")
                .setMessage("آیا می‌خواهید شمارنده شماره‌گذاری فایل‌های ضبط صفر شود؟ با این کار نام اولین فایل ضبط بعدی از شماره ۰۱ شروع خواهد شد.")
                .setPositiveButton("بله، صفر کن") { _, _ ->
                    appConfig.resetRecordingCounter()
                    val label = "۰ (ضبط بعدی: ۰۱)"
                    txtVal.text = label
                    row.contentDescription = "شمارنده فایل‌های ضبط: $label"
                    val msg = "شمارنده فایل‌های ضبط با موفقیت صفر شد. ضبط بعدی با شماره ۰۱ ذخیره می‌شود."
                    AccessibilityHelper.announce(row, msg)
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
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
            }
            .setNeutralButton("📁 مشاهده فایل‌ها") { _, _ ->
                com.gmscreen.accessible.util.RecordingsDialogHelper.showRecordingsDialog(requireContext())
            }
            .setNegativeButton("انصراف", null)
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
        var draftAutoChannel = appConfig.autoAnnounceChannel
        var draftAutoEpg = appConfig.autoAnnounceEpg
        var draftEpgDetail = appConfig.epgAnnounceDetail
        var draftBackgroundEpg = appConfig.backgroundAnnounceEpg

        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 20, 32, 20)
        }
        scrollView.addView(contentLayout)

        // 1. Auto announce channel (Direct 1-tap Toggle)
        addToggleSettingRow(
            contentLayout,
            "۱. اعلام صوتی خودکار نام شبکه پس از تعویض",
            draftAutoChannel,
            onText = "روشن (اعلام خودکار نام و شماره)",
            offText = "خاموش (غیرفعال)"
        ) { newState ->
            draftAutoChannel = newState
        }

        // 2. EPG mode (Direct 1-tap Toggle)
        addToggleSettingRow(
            contentLayout,
            "۲. نحوه اعلام جدول زمان‌بندی برنامه (EPG)",
            draftAutoEpg,
            onText = "خواندن خودکار پس از تعویض کانال",
            offText = "خواندن دستی از طریق منوی کانال (پیش‌فرض)"
        ) { newState ->
            draftAutoEpg = newState
        }

        // 3. EPG details
        val detailOptions = arrayOf(
            "فقط عنوان برنامه در حال پخش (پیش‌فرض)",
            "عنوان برنامه به همراه ساعت شروع و پایان",
            "عنوان و ساعت برنامه فعلی + عنوان برنامه بعدی"
        )
        val getDetailTitle: (Int) -> String = { d ->
            if (d in detailOptions.indices) detailOptions[d] else detailOptions[0]
        }
        addSettingRow(contentLayout, "۳. جزئیات اطلاعات اعلامی EPG", getDetailTitle(draftEpgDetail)) { txtVal, row ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("جزئیات اطلاعات اعلامی EPG")
                .setSingleChoiceItems(detailOptions, draftEpgDetail.coerceIn(0, 2)) { dlg, which ->
                    draftEpgDetail = which
                    val label = getDetailTitle(draftEpgDetail)
                    txtVal.text = label
                    row.contentDescription = "جزئیات EPG: $label"
                    AccessibilityHelper.announce(row, label)
                    dlg.dismiss()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        // 4. Background EPG Announcement (Direct 1-tap Toggle)
        addToggleSettingRow(
            contentLayout,
            "۴. اعلام جدول پخش برنامه (EPG) در پس‌زمینه",
            draftBackgroundEpg,
            onText = "روشن (اعلام خودکار هنگام کار با ریموت فیزیکی)",
            offText = "خاموش (غیرفعال - پیش‌فرض)"
        ) { newState ->
            draftBackgroundEpg = newState
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تنظیمات راهنمای برنامه‌ها و اعلام خودکار")
            .setView(scrollView)
            .setPositiveButton("ذخیره تنظیمات") { _, _ ->
                appConfig.autoAnnounceChannel = draftAutoChannel
                appConfig.autoAnnounceEpg = draftAutoEpg
                appConfig.epgAnnounceDetail = draftEpgDetail
                appConfig.backgroundAnnounceEpg = draftBackgroundEpg

                val msg = "تنظیمات راهنمای برنامه‌ها با موفقیت ذخیره شد"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun openVoiceSettingsDialog() {
        var draftSound = appConfig.soundCuesEnabled

        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 20, 32, 20)
        }
        scrollView.addView(contentLayout)

        // 1. Sound cues (Direct 1-tap Toggle)
        addToggleSettingRow(
            contentLayout,
            "۱. پخش افکت‌های صوتی و بوق فرامین",
            draftSound,
            onText = "روشن (پخش بوق فرامین)",
            offText = "خاموش"
        ) { newState ->
            draftSound = newState
        }

        // 2. Voice Commands Customization & Management
        addSettingRow(contentLayout, "۲. مدیریت و شخصی‌سازی فرامین صوتی رسیور", "مشاهده، ویرایش، افزودن، همگام‌سازی و بازنشانی فرامین") { _, _ ->
            VoiceCustomizationDialogHelper.showVoiceCustomizationMenu(requireContext(), repository)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تنظیمات کنترل صوتی و فرامین")
            .setView(scrollView)
            .setPositiveButton("ذخیره تنظیمات") { _, _ ->
                appConfig.soundCuesEnabled = draftSound
                val msg = "تنظیمات صوتی با موفقیت ذخیره شد"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun openQuickAccessSettingsDialog() {
        var draftQuickAccess = appConfig.quickAccessEnabled
        var draftShowVolumeStepButtons = appConfig.showVolumeStepButtons

        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 20, 32, 20)
        }
        scrollView.addView(contentLayout)

        // 1. Quick access button visibility (Direct 1-tap Toggle)
        addToggleSettingRow(
            contentLayout,
            "۱. نمایش کلید دسترسی سریع بالای صفحه",
            draftQuickAccess,
            onText = "روشن (نمایش در بالای برنامه)",
            offText = "خاموش (مخفی)"
        ) { newState ->
            draftQuickAccess = newState
        }

        // 2. Volume step buttons (Direct 1-tap Toggle)
        addToggleSettingRow(
            contentLayout,
            "۲. نمایش دکمه‌های گام‌های ۵ تایی ولوم در پنل",
            draftShowVolumeStepButtons,
            onText = "روشن (نمایش دکمه‌های کاهش و افزایش)",
            offText = "خاموش (مخفی - پیش‌فرض)"
        ) { newState ->
            draftShowVolumeStepButtons = newState
        }

        // 3. Customize quick access items
        val getQuickItemsSummary: () -> String = {
            val count = appConfig.quickAccessItems.size
            "$count کلید انتخاب شده (کلیک برای ویرایش)"
        }
        addSettingRow(contentLayout, "۳. سفارشی‌سازی کلیدها و چینش پنل دسترسی سریع", getQuickItemsSummary()) { txtVal, row ->
            QuickAccessDialog.showCustomizeKeysDialog(requireContext(), appConfig) {
                val summary = getQuickItemsSummary()
                txtVal.text = summary
                row.contentDescription = "سفارشی‌سازی کلیدها: $summary"
            }
        }

        // 4. Test and open Quick Access Dialog directly
        addSettingRow(contentLayout, "۴. آزمایش و باز کردن پنل دسترسی سریع", "برای مشاهده پنل لمس کنید") { _, _ ->
            QuickAccessDialog.show(requireActivity())
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تنظیمات پنل دسترسی سریع رسیور")
            .setView(scrollView)
            .setPositiveButton("ذخیره تنظیمات") { _, _ ->
                appConfig.quickAccessEnabled = draftQuickAccess
                appConfig.showVolumeStepButtons = draftShowVolumeStepButtons

                (activity as? com.gmscreen.accessible.ui.MainActivity)?.updateQuickAccessVisibility()

                val msg = "تنظیمات پنل دسترسی سریع ذخیره شد"
                AccessibilityHelper.announce(null, msg)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun openAccessibilitySettingsDialog() {
        var draftHaptic = appConfig.hapticFeedbackEnabled
        var draftKeepGlobal = appConfig.keepGlobalChannelNum
        var draftLandscape = appConfig.videoLandscapeOrientation
        var draftJumpFirst = appConfig.showJumpToFirstChannel
        var draftJumpLast = appConfig.showJumpToLastChannel
        var draftSeparateAudio = appConfig.separateAudioToBluetooth
        var draftShowRecordings = appConfig.showRecordingsInChannels
        var draftShowRefresh = appConfig.showRefreshInChannels
        var draftShowMic = appConfig.showMicInChannels
        var draftVolumeStep = appConfig.volumeStep

        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 20, 32, 20)
        }
        scrollView.addView(contentLayout)

        // 1. Haptic feedback (Direct 1-tap Toggle)
        addToggleSettingRow(
            contentLayout,
            "۱. ویبره و بازخورد لمسی (Haptic Feedback)",
            draftHaptic,
            onText = "روشن (لرزش هنگام لمس کلیدها)",
            offText = "خاموش"
        ) { newState ->
            draftHaptic = newState
        }

        // 2. Keep global channel number (Direct 1-tap Toggle)
        addToggleSettingRow(
            contentLayout,
            "۲. حفظ شماره سراسری کانال‌ها در فیلترها",
            draftKeepGlobal,
            onText = "روشن (حفظ شماره اصلی شبکه در کل رسیور)",
            offText = "خاموش (شماره‌گذاری از ۱ در فیلتر)"
        ) { newState ->
            draftKeepGlobal = newState
        }

        // 3. Video orientation (Direct 1-tap Toggle)
        addToggleSettingRow(
            contentLayout,
            "۳. جهت صفحه در پخش زنده ویدیویی",
            draftLandscape,
            onText = "افقی (Landscape - مناسب افراد بینا)",
            offText = "عمودی (Portrait - پیش‌فرض نابینایان)"
        ) { newState ->
            draftLandscape = newState
        }

        // 4. Show recordings button in channels tab (Direct 1-tap Toggle)
        addToggleSettingRow(
            contentLayout,
            "۴. نمایش دکمه مشاهده فایل‌های ضبطی در کانال‌ها",
            draftShowRecordings,
            onText = "روشن (نمایش در نوار پایین)",
            offText = "خاموش (مخفی)"
        ) { newState ->
            draftShowRecordings = newState
        }

        // 5. Show refresh button in channels tab (Direct 1-tap Toggle)
        addToggleSettingRow(
            contentLayout,
            "۵. نمایش دکمه به‌روزرسانی کانال‌ها در سربرگ کانال‌ها",
            draftShowRefresh,
            onText = "روشن (نمایش در نوار پایین)",
            offText = "خاموش (مخفی)"
        ) { newState ->
            draftShowRefresh = newState
        }

        // 6. Show voice assistant button in channels tab (Direct 1-tap Toggle)
        addToggleSettingRow(
            contentLayout,
            "۶. نمایش دکمه دستیار صوتی (شنیدن مستقیم) در پایین سربرگ کانال‌ها",
            draftShowMic,
            onText = "روشن (نمایش دکمه شنیدن)",
            offText = "خاموش (مخفی)"
        ) { newState ->
            draftShowMic = newState
        }

        // 7. Jump to first channel button (Direct 1-tap Toggle)
        addToggleSettingRow(
            contentLayout,
            "۷. نمایش دکمه رفتن به اولین کانال (کانال ۱)",
            draftJumpFirst,
            onText = "روشن (نمایش در بالای لیست)",
            offText = "خاموش (مخفی)"
        ) { newState ->
            draftJumpFirst = newState
        }

        // 8. Jump to last channel button (Direct 1-tap Toggle)
        addToggleSettingRow(
            contentLayout,
            "۸. نمایش دکمه رفتن به آخرین کانال",
            draftJumpLast,
            onText = "روشن (نمایش در بالای لیست)",
            offText = "خاموش (مخفی)"
        ) { newState ->
            draftJumpLast = newState
        }

        // 9. Separate audio output to Bluetooth
        val getAudioRouteTitle: (Boolean) -> String = { enabled ->
            if (enabled) "هدایت به هندزفری بلوتوث (جداسازی از اسپیکر گوشی و تاک‌بک)" else "پیش‌فرض سیستم (پخش معمول گوشی)"
        }
        addSettingRow(contentLayout, "۹. تفکیک خروجی صدای شبکه و تاک‌بک", getAudioRouteTitle(draftSeparateAudio)) { txtVal, row ->
            val opts = arrayOf(
                "پیش‌فرض سیستم (پخش عادی بدون تفکیک)",
                "هدایت مستقیم صدای برنامه به هندزفری بلوتوث",
                "⚙️ باز کردن تنظیمات صدای جداگانه سامسونگ (Separate App Sound)"
            )
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("تنظیم خروجی صدای برنامه و تاک‌بک")
                .setItems(opts) { dlg, which ->
                    when (which) {
                        0 -> {
                            draftSeparateAudio = false
                            val label = getAudioRouteTitle(draftSeparateAudio)
                            txtVal.text = label
                            row.contentDescription = "تفکیک خروجی صدا: $label"
                            AccessibilityHelper.announce(row, label)
                        }
                        1 -> {
                            draftSeparateAudio = true
                            val label = getAudioRouteTitle(draftSeparateAudio)
                            txtVal.text = label
                            row.contentDescription = "تفکیک خروجی صدا: $label"
                            AccessibilityHelper.announce(row, label)
                        }
                        2 -> {
                            try {
                                val intent = Intent("com.samsung.android.setting.SEPARATE_APP_SOUND")
                                startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS))
                                } catch (_: Exception) {
                                    Toast.makeText(requireContext(), "تنظیمات صوتی در این دستگاه باز نشد", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        // 10. Volume slider step
        val getVolumeStepTitle: (Int) -> String = { step ->
            when (step) {
                2 -> "۲ درصد"
                10 -> "۱۰ درصد"
                else -> "۵ درصد (پیش‌فرض)"
            }
        }
        addSettingRow(contentLayout, "۱۰. گام تغییر ولوم لغزنده رسیور (اسلایدر دسترسی سریع)", getVolumeStepTitle(draftVolumeStep)) { txtVal, row ->
            val opts = arrayOf("۲ درصد", "۵ درصد (پیش‌فرض)", "۱۰ درصد")
            val currentIdx = when (draftVolumeStep) {
                2 -> 0
                10 -> 2
                else -> 1
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("گام تغییر ولوم صدای رسیور")
                .setSingleChoiceItems(opts, currentIdx) { dlg, which ->
                    draftVolumeStep = when (which) {
                        0 -> 2
                        2 -> 10
                        else -> 5
                    }
                    val label = getVolumeStepTitle(draftVolumeStep)
                    txtVal.text = label
                    row.contentDescription = "گام تغییر ولوم: $label"
                    AccessibilityHelper.announce(row, label)
                    dlg.dismiss()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تنظیمات دسترس‌پذیری، ویبره و خروجی صدا")
            .setView(scrollView)
            .setPositiveButton("ذخیره تنظیمات") { _, _ ->
                appConfig.hapticFeedbackEnabled = draftHaptic
                appConfig.keepGlobalChannelNum = draftKeepGlobal
                appConfig.videoLandscapeOrientation = draftLandscape
                appConfig.showRecordingsInChannels = draftShowRecordings
                appConfig.showRefreshInChannels = draftShowRefresh
                appConfig.showMicInChannels = draftShowMic
                appConfig.showJumpToFirstChannel = draftJumpFirst
                appConfig.showJumpToLastChannel = draftJumpLast
                appConfig.separateAudioToBluetooth = draftSeparateAudio
                appConfig.volumeStep = draftVolumeStep

                val msg = "تنظیمات دسترس‌پذیری با موفقیت ذخیره شد"
                AccessibilityHelper.announce(null, msg)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("انصراف", null)
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
