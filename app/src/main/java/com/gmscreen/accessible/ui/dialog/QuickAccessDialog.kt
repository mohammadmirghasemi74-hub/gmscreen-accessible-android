package com.gmscreen.accessible.ui.dialog

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.player.StreamRecorderManager
import com.gmscreen.accessible.protocol.RcuKey
import com.gmscreen.accessible.util.AccessibilityHelper
import com.gmscreen.accessible.util.AppConfig
import com.gmscreen.accessible.util.StatusHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object QuickAccessDialog {

    data class ActionItem(
        val id: String,
        val defaultTitle: String,
        val defaultDescription: String,
        val defaultBgTint: Int = Color.parseColor("#1F2A3C"),
        val isDefault: Boolean = false,
        val dynamicTitle: (() -> String)? = null,
        val dynamicDescription: (() -> String)? = null,
        val dynamicBgTint: (() -> Int)? = null
    ) {
        val title: String get() = dynamicTitle?.invoke() ?: defaultTitle
        val description: String get() = dynamicDescription?.invoke() ?: defaultDescription
        val bgTint: Int get() = dynamicBgTint?.invoke() ?: defaultBgTint
    }

    private val repository get() = GMScreenApplication.instance.repository

    val ALL_AVAILABLE_ACTIONS = listOf(
        ActionItem("power", "🔴 روشن / خاموش (Power)", "خاموش یا روشن کردن رسیور", Color.parseColor("#B91C1C"), true),
        ActionItem("mute", "🔇 قطع و وصل صدا (Mute)", "قطع یا وصل فوری صدای رسیور", Color.parseColor("#B45309"), true),
        ActionItem("ch_up", "🔼 کانال بعدی (CH+)", "تعویض کانال به جلو", Color.parseColor("#047857"), true),
        ActionItem("ch_down", "🔽 کانال قبلی (CH-)", "تعویض کانال به عقب", Color.parseColor("#047857"), true),
        ActionItem("vol_slider", "🔊 لغزنده ولوم رسیور", "کادر لغزنده ولوم صدا در بالای پنل", Color.parseColor("#1D4ED8"), true),
        ActionItem(
            id = "rec_stb_toggle",
            defaultTitle = "🔴 ضبط فوری فلش",
            defaultDescription = "آغاز فوری ضبط شبکه در حال پخش روی فلش رسیور",
            defaultBgTint = Color.parseColor("#B91C1C"),
            isDefault = true,
            dynamicTitle = {
                if (repository.isStbRecording.value) "⏹ توقف ضبط فلش"
                else "🔴 ضبط فوری فلش"
            },
            dynamicDescription = {
                if (repository.isStbRecording.value) "توقف ضبط در حال انجام بر روی فلش مموری رسیور"
                else "آغاز فوری ضبط شبکه در حال پخش روی فلش مموری رسیور"
            },
            dynamicBgTint = {
                if (repository.isStbRecording.value) Color.parseColor("#991B1B")
                else Color.parseColor("#B91C1C")
            }
        ),
        ActionItem(
            id = "rec_phone_toggle",
            defaultTitle = "🎙 ضبط فوری گوشی",
            defaultDescription = "آغاز ضبط صدای شبکه زنده روی حافظه گوشی",
            defaultBgTint = Color.parseColor("#7C3AED"),
            isDefault = true,
            dynamicTitle = {
                if (StreamRecorderManager.isRecording) "⏹ توقف ضبط گوشی"
                else "🎙 ضبط فوری گوشی"
            },
            dynamicDescription = {
                if (StreamRecorderManager.isRecording) "پایان ضبط و ذخیره‌سازی فایل صوتی روی گوشی"
                else "آغاز ضبط صدای زنده شبکه فعلی روی حافظه گوشی"
            },
            dynamicBgTint = {
                if (StreamRecorderManager.isRecording) Color.parseColor("#4B5563")
                else Color.parseColor("#7C3AED")
            }
        ),
        ActionItem("status", "📢 گزارش وضعیت", "خواندن صوتی وضعیت رسیور، شبکه فعلی و مدت ضبط", Color.parseColor("#0369A1"), true),
        ActionItem("play_stream", "▶️ پخش زنده در گوشی", "پخش زنده تصویر شبکه در پلیر گوشی", Color.parseColor("#0284C7"), true),
        ActionItem("find_channel", "🔍 جستجوی کانال‌ها", "انتقال به جستجوی کانال‌ها", Color.parseColor("#0284C7")),
        ActionItem("epg", "📅 جدول برنامه‌ها (EPG)", "باز کردن جدول برنامه‌های رسیور", Color.parseColor("#1F2937")),
        ActionItem("info", "ℹ️ اطلاعات برنامه (INFO)", "نمایش اطلاعات برنامه فعلی", Color.parseColor("#1F2937")),
        ActionItem("audio_track", "🎧 تغییر صدا (AUDIO)", "تغییر ترک یا زبان صوتی شبکه", Color.parseColor("#1F2937")),
        ActionItem("subtitle", "💬 زیرنویس (SUB)", "تنظیمات زیرنویس رسیور", Color.parseColor("#1F2937")),
        ActionItem("recall", "↩️ بازگشت به شبکه قبل", "پرش به آخرین شبکه مشاهده شده", Color.parseColor("#1F2937")),
        ActionItem("sat_list", "🛰 لیست ماهواره‌ها (SAT)", "باز کردن لیست جهت‌های رسیور", Color.parseColor("#1F2937")),
        ActionItem("fav_list", "⭐ کانال‌های برگزیده (FAV)", "باز کردن لیست شبکه‌های مورد علاقه", Color.parseColor("#B45309")),
        ActionItem("restart_stb", "🔄 راه‌اندازی مجدد (Reboot)", "ری‌استارت دستگاه رسیور از راه دور", Color.parseColor("#991B1B"))
    )

    fun show(activity: Activity) {
        val context = activity
        val appConfig = AppConfig(context)
        val dialogScope = CoroutineScope(Dispatchers.Main)

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_quick_access, null)
        val btnCloseTop = dialogView.findViewById<MaterialButton>(R.id.btnCloseQuickAccessTop)
        val btnCloseBottom = dialogView.findViewById<MaterialButton>(R.id.btnCloseQuickAccessBottom)
        val btnSettings = dialogView.findViewById<MaterialButton>(R.id.btnCustomizeQuickAccess)
        val cardVolume = dialogView.findViewById<MaterialCardView>(R.id.cardVolumeControl)
        val txtVolumeBadge = dialogView.findViewById<TextView>(R.id.txtVolumeBadge)
        val seekBarVolume = dialogView.findViewById<SeekBar>(R.id.seekBarVolume)
        val btnVolMinus5 = dialogView.findViewById<MaterialButton>(R.id.btnVolMinus5)
        val btnVolPlus5 = dialogView.findViewById<MaterialButton>(R.id.btnVolPlus5)
        val btnQuickMute = dialogView.findViewById<MaterialButton>(R.id.btnQuickMute)
        val pager = dialogView.findViewById<ViewPager2>(R.id.pagerQuickActions)
        val rowNavigation = dialogView.findViewById<View>(R.id.rowPageNavigation)
        val btnPrevPage = dialogView.findViewById<MaterialButton>(R.id.btnPrevPage)
        val btnNextPage = dialogView.findViewById<MaterialButton>(R.id.btnNextPage)
        val txtPageIndicator = dialogView.findViewById<TextView>(R.id.txtPageIndicator)

        lateinit var dialog: AlertDialog

        // 1. Setup Volume Slider & Step Buttons
        var currentVol = appConfig.stbVolume.coerceIn(0, 100)
        currentVol = ((currentVol + 2) / 5) * 5
        var trackingStartVol = currentVol

        val showStepBtns = appConfig.showVolumeStepButtons
        btnVolMinus5.visibility = if (showStepBtns) View.VISIBLE else View.GONE
        btnVolPlus5.visibility = if (showStepBtns) View.VISIBLE else View.GONE

        fun updateVolumeDisplay(vol: Int) {
            currentVol = vol.coerceIn(0, 100)
            if (currentVol == 0) {
                txtVolumeBadge.text = "۰٪ (بی‌صدا)"
                seekBarVolume.contentDescription = "ولوم صدای رسیور: ۰ درصد، صدا کاملاً قطع و بی‌صدا است"
            } else {
                txtVolumeBadge.text = "$currentVol٪"
                seekBarVolume.contentDescription = "ولوم صدای رسیور: $currentVol درصد"
            }
            seekBarVolume.progress = currentVol
        }

        updateVolumeDisplay(currentVol)

        val adjustVolumeBy = { delta: Int ->
            if (!repository.isConnected.value) {
                val msg = "رسیور متصل نیست. لطفاً ابتدا به رسیور متصل شوید."
                AccessibilityHelper.announce(seekBarVolume, msg)
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            } else if (!repository.isStbPowerOn.value) {
                val msg = "رسیور خاموش (آماده‌به‌کار) است. ابتدا کلید پاور را بزنید."
                AccessibilityHelper.announce(seekBarVolume, msg)
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            } else {
                val oldVol = currentVol
                val newVol = (currentVol + delta).coerceIn(0, 100)
                if (newVol != oldVol) {
                    currentVol = newVol
                    appConfig.stbVolume = newVol
                    updateVolumeDisplay(newVol)
                    val announceText = if (newVol == 0) "صدای رسیور کاملاً قطع شد (۰ درصد)" else if (delta > 0) "افزایش صدا: $newVol درصد" else "کاهش صدا: $newVol درصد"
                    AccessibilityHelper.announce(seekBarVolume, announceText)
                    dialogScope.launch(Dispatchers.IO) {
                        if (repository.isConnected.value) {
                            if (newVol == 0) {
                                for (i in 0 until 30) {
                                    repository.sendRcuKey(RcuKey.VOL_DOWN)
                                    delay(20)
                                }
                            } else if (newVol == 100) {
                                for (i in 0 until 20) {
                                    repository.sendRcuKey(RcuKey.VOL_UP)
                                    delay(25)
                                }
                            } else {
                                val steps = (Math.abs(delta) / 5).coerceAtLeast(1)
                                for (i in 0 until steps) {
                                    if (delta > 0) repository.sendRcuKey(RcuKey.VOL_UP)
                                    else repository.sendRcuKey(RcuKey.VOL_DOWN)
                                    delay(35)
                                }
                            }
                        }
                    }
                }
            }
        }

        seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: SeekBar?) {
                if (!repository.isConnected.value) {
                    val msg = "رسیور متصل نیست. لطفاً ابتدا به رسیور متصل شوید."
                    AccessibilityHelper.announce(seekBarVolume, msg)
                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                    return
                }
                trackingStartVol = currentVol
            }

            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    if (!repository.isConnected.value) return
                    val snapped = ((progress + 2) / 5) * 5
                    currentVol = snapped
                    if (snapped == 0) {
                        txtVolumeBadge.text = "۰٪ (بی‌صدا)"
                        seekBarVolume.contentDescription = "ولوم صدای رسیور: ۰ درصد، بی‌صدا"
                    } else {
                        txtVolumeBadge.text = "$snapped٪"
                        seekBarVolume.contentDescription = "ولوم صدای رسیور: $snapped درصد"
                    }
                }
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                if (!repository.isConnected.value) {
                    val msg = "رسیور متصل نیست. لطفاً ابتدا به رسیور متصل شوید."
                    AccessibilityHelper.announce(seekBarVolume, msg)
                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                    return
                }
                val finalVol = currentVol
                appConfig.stbVolume = finalVol
                val delta = finalVol - trackingStartVol
                if (finalVol == 0) {
                    AccessibilityHelper.announce(seekBarVolume, "صدای رسیور کاملاً قطع شد (۰ درصد)")
                    dialogScope.launch(Dispatchers.IO) {
                        if (repository.isConnected.value) {
                            for (i in 0 until 35) {
                                repository.sendRcuKey(RcuKey.VOL_DOWN)
                                delay(20)
                            }
                        }
                    }
                } else {
                    AccessibilityHelper.announce(seekBarVolume, "صدای رسیور: $finalVol درصد")
                    if (delta != 0 || finalVol == 100) {
                        dialogScope.launch(Dispatchers.IO) {
                            if (repository.isConnected.value) {
                                if (finalVol == 100) {
                                    for (i in 0 until 25) {
                                        repository.sendRcuKey(RcuKey.VOL_UP)
                                        delay(25)
                                    }
                                } else {
                                    val steps = (Math.abs(delta) / 5).coerceAtLeast(1)
                                    for (i in 0 until steps) {
                                        if (delta > 0) repository.sendRcuKey(RcuKey.VOL_UP)
                                        else repository.sendRcuKey(RcuKey.VOL_DOWN)
                                        delay(35)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        })

        btnVolMinus5.setOnClickListener { adjustVolumeBy(-5) }
        btnVolPlus5.setOnClickListener { adjustVolumeBy(5) }

        // Mute button matching RemoteFragment exact behavior
        btnQuickMute.setOnClickListener {
            if (!repository.isConnected.value) {
                val msg = "رسیور متصل نیست. لطفاً ابتدا به رسیور متصل شوید."
                AccessibilityHelper.announce(btnQuickMute, msg)
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!repository.isStbPowerOn.value) {
                val msg = "رسیور خاموش (آماده‌به‌کار) است. ابتدا کلید پاور را بزنید."
                AccessibilityHelper.announce(btnQuickMute, msg)
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            executeMute(activity, btnQuickMute)
        }

        // 2. Setup Action Buttons Pager (Carousel)
        lateinit var refreshActionButtons: () -> Unit

        val removeAction = { item: ActionItem ->
            MaterialAlertDialogBuilder(activity)
                .setTitle("حذف از دسترسی سریع")
                .setMessage("آیا می‌خواهید «${item.title}» از پنل دسترسی سریع حذف شود؟")
                .setPositiveButton("حذف") { _, _ ->
                    val cur = appConfig.quickAccessItems.toMutableSet()
                    cur.remove(item.id)
                    appConfig.quickAccessItems = cur
                    refreshActionButtons()
                    val msg = "${item.title} حذف شد"
                    AccessibilityHelper.announce(dialogView, msg)
                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        refreshActionButtons = {
            val selectedIds = appConfig.quickAccessItems

            val showVolSlider = selectedIds.contains("vol_slider")
            cardVolume.visibility = if (showVolSlider) View.VISIBLE else View.GONE

            val actionsToDisplay = ALL_AVAILABLE_ACTIONS.filter {
                it.id != "vol_slider" && selectedIds.contains(it.id)
            }

            val pageSize = 6
            val pages = if (actionsToDisplay.isEmpty()) {
                listOf(emptyList())
            } else {
                actionsToDisplay.chunked(pageSize)
            }

            pager.adapter = QuickActionPagerAdapter(
                activity = activity,
                pages = pages,
                onActionClick = { action, buttonView ->
                    handleQuickAction(activity, action, buttonView)
                },
                onActionLongClick = { action ->
                    removeAction(action)
                }
            )

            val totalPages = pages.size
            if (totalPages <= 1) {
                rowNavigation.visibility = View.GONE
                pager.isUserInputEnabled = false
            } else {
                rowNavigation.visibility = View.VISIBLE
                pager.isUserInputEnabled = true
                val updateNavState = { pos: Int ->
                    txtPageIndicator.text = "صفحه ${pos + 1} از $totalPages (کشیدن با ۲ انگشت)"
                    btnPrevPage.isEnabled = pos > 0
                    btnNextPage.isEnabled = pos < totalPages - 1
                }
                updateNavState(pager.currentItem.coerceIn(0, totalPages - 1))

                pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        updateNavState(position)
                        AccessibilityHelper.announce(pager, "صفحه ${position + 1} از $totalPages")
                    }
                })

                btnPrevPage.setOnClickListener {
                    if (pager.currentItem > 0) {
                        pager.setCurrentItem(pager.currentItem - 1, true)
                    }
                }

                btnNextPage.setOnClickListener {
                    if (pager.currentItem < totalPages - 1) {
                        pager.setCurrentItem(pager.currentItem + 1, true)
                    }
                }
            }
        }

        refreshActionButtons()

        // 3. Settings Button: Full options including jump to Tab 6
        btnSettings.setOnClickListener {
            val options = arrayOf(
                "۱. سفارشی‌سازی کلیدها (انتخاب کلیدهای فعال در پنل)",
                "۲. تغییر نمایش دکمه‌های کم و زیاد ۵ درصدی ولوم",
                "۳. تغییر نمایش کلید دسترسی سریع بالای برنامه",
                "۴. ⚙️ رفتن به سربرگ تنظیمات برنامه (سربرگ ۶)"
            )
            MaterialAlertDialogBuilder(context)
                .setTitle("تنظیمات پنل دسترسی سریع")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showCustomizeKeysDialog(context, appConfig) { refreshActionButtons() }
                        1 -> {
                            appConfig.showVolumeStepButtons = !appConfig.showVolumeStepButtons
                            val show = appConfig.showVolumeStepButtons
                            btnVolMinus5.visibility = if (show) View.VISIBLE else View.GONE
                            btnVolPlus5.visibility = if (show) View.VISIBLE else View.GONE
                            val msg = if (show) "دکمه‌های ۵ درصدی ولوم نمایان شدند" else "دکمه‌های ۵ درصدی ولوم مخفی شدند"
                            AccessibilityHelper.announce(btnSettings, msg)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        2 -> {
                            appConfig.quickAccessEnabled = !appConfig.quickAccessEnabled
                            (activity as? com.gmscreen.accessible.ui.MainActivity)?.updateQuickAccessVisibility()
                            val msg = if (appConfig.quickAccessEnabled) "کلید دسترسی سریع فعال شد" else "کلید دسترسی سریع مخفی شد"
                            AccessibilityHelper.announce(btnSettings, msg)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        3 -> {
                            dialog.dismiss()
                            (activity as? com.gmscreen.accessible.ui.MainActivity)?.switchToTab(5)
                        }
                    }
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        // 4. Create and Show Dialog
        dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnCloseTop.setOnClickListener { dialog.dismiss() }
        btnCloseBottom.setOnClickListener { dialog.dismiss() }

        dialog.show()
        AccessibilityHelper.announce(dialogView, "پنل دسترسی سریع")
    }

    // --- Action Handlers Matching RemoteFragment Exact Behavior ---

    private fun handleQuickAction(activity: Activity, action: ActionItem, buttonView: MaterialButton) {
        val isConnected = repository.isConnected.value

        if (!isConnected && action.id != "status" && action.id != "rec_phone_toggle") {
            val msg = "رسیور متصل نیست. لطفاً ابتدا به رسیور متصل شوید."
            AccessibilityHelper.announce(buttonView, msg)
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            return
        }

        if (isConnected && !repository.isStbPowerOn.value && action.id != "power" && action.id != "status" && action.id != "rec_phone_toggle") {
            val prompt = "رسیور در حالت آماده‌به‌کار (خاموش) است. آیا می‌خواهید ابتدا رسیور روشن شود؟"
            AccessibilityHelper.announce(buttonView, prompt)
            MaterialAlertDialogBuilder(activity)
                .setTitle("روشن کردن رسیور")
                .setMessage(prompt)
                .setPositiveButton("بله، روشن کن") { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        repository.sendRcuKey(RcuKey.POWER_TOGGLE)
                        withContext(Dispatchers.Main) {
                            val onMsg = "رسیور روشن شد"
                            AccessibilityHelper.announce(buttonView, onMsg)
                            Toast.makeText(activity, onMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("انصراف", null)
                .show()
            return
        }

        when (action.id) {
            "power" -> {
                executePower(activity, buttonView)
            }
            "mute" -> {
                executeMute(activity, buttonView)
            }
            "ch_up" -> {
                sendRcuKeyMatchingRemote(RcuKey.CH_NEXT, buttonView, "کانال بعدی")
            }
            "ch_down" -> {
                sendRcuKeyMatchingRemote(RcuKey.CH_PREV, buttonView, "کانال قبلی")
            }
            "restart_stb" -> {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("راه‌اندازی مجدد رسیور")
                    .setMessage("آیا می‌خواهید رسیور را مجدداً راه‌اندازی (Reboot) کنید؟")
                    .setPositiveButton("بله") { _, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            repository.client.sendCmd(1011, expectReply = false)
                            withContext(Dispatchers.Main) {
                                val msg = "رسیور راه‌اندازی مجدد شد"
                                AccessibilityHelper.announce(buttonView, msg)
                                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("انصراف", null)
                    .show()
            }
            "status" -> {
                StatusHelper.announceStatus(activity, repository, buttonView)
            }
            "rec_stb_toggle" -> {
                if (!isConnected) {
                    Toast.makeText(activity, "رسیور متصل نیست", Toast.LENGTH_SHORT).show()
                    return
                }
                if (repository.isStbRecording.value) {
                    // Stop STB recording in-place
                    buttonView.text = "🔴 ضبط فوری فلش"
                    buttonView.contentDescription = "🔴 ضبط فوری فلش. آغاز فوری ضبط شبکه در حال پخش روی فلش مموری رسیور"
                    buttonView.setBackgroundColor(Color.parseColor("#B91C1C"))
                    AccessibilityHelper.announce(buttonView, "ضبط روی فلش متوقف شد")
                    Toast.makeText(activity, "ضبط روی فلش متوقف شد", Toast.LENGTH_SHORT).show()

                    CoroutineScope(Dispatchers.IO).launch {
                        repository.sendRcuKey(RcuKey.STOP)
                        delay(400)
                        repository.sendRcuKey(RcuKey.OK)
                        repository.setStbRecording(false, "")
                    }
                } else {
                    // Start STB recording in-place
                    val curCh = repository.currentChannel.value?.serviceName ?: "شبکه جاری"
                    buttonView.text = "⏹ توقف ضبط فلش"
                    buttonView.contentDescription = "⏹ توقف ضبط فلش. توقف ضبط در حال انجام بر روی فلش مموری رسیور"
                    buttonView.setBackgroundColor(Color.parseColor("#991B1B"))
                    AccessibilityHelper.announce(buttonView, "ضبط شروع شد روی فلش")
                    Toast.makeText(activity, "ضبط شروع شد روی فلش", Toast.LENGTH_SHORT).show()

                    CoroutineScope(Dispatchers.IO).launch {
                        repository.sendRcuKey(RcuKey.RECORD)
                        repository.setStbRecording(true, curCh)
                    }
                }
            }
            "rec_phone_toggle" -> {
                if (StreamRecorderManager.isRecording) {
                    // Stop phone recording in-place
                    buttonView.text = "🎙 ضبط فوری گوشی"
                    buttonView.contentDescription = "🎙 ضبط فوری گوشی. آغاز ضبط صدای شبکه فعلی روی حافظه گوشی"
                    buttonView.setBackgroundColor(Color.parseColor("#7C3AED"))
                    AccessibilityHelper.announce(buttonView, "ضبط روی گوشی متوقف شد")
                    Toast.makeText(activity, "ضبط روی گوشی متوقف شد", Toast.LENGTH_SHORT).show()

                    CoroutineScope(Dispatchers.IO).launch {
                        StreamRecorderManager.stopRecording()
                    }
                } else {
                    // Start phone recording in-place
                    val ip = repository.connectedIp.value
                    val curCh = repository.currentChannel.value
                    if (ip.isBlank() || curCh == null) {
                        Toast.makeText(activity, "شبکه‌ای برای ضبط در دسترس نیست", Toast.LENGTH_SHORT).show()
                        return
                    }
                    buttonView.text = "⏹ توقف ضبط گوشی"
                    buttonView.contentDescription = "⏹ توقف ضبط گوشی. پایان ضبط و ذخیره‌سازی فایل صوتی روی گوشی"
                    buttonView.setBackgroundColor(Color.parseColor("#4B5563"))
                    AccessibilityHelper.announce(buttonView, "ضبط شروع شد روی گوشی")
                    Toast.makeText(activity, "ضبط شروع شد روی گوشی", Toast.LENGTH_SHORT).show()

                    CoroutineScope(Dispatchers.IO).launch {
                        val file = StreamRecorderManager.startRecording(
                            activity,
                            repository.client,
                            ip,
                            curCh,
                            isAudioOnly = true
                        )
                        if (file == null) {
                            withContext(Dispatchers.Main) {
                                buttonView.text = "🎙 ضبط فوری گوشی"
                                buttonView.contentDescription = "🎙 ضبط فوری گوشی"
                                buttonView.setBackgroundColor(Color.parseColor("#7C3AED"))
                                Toast.makeText(activity, "خطا در شروع ضبط", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            "play_stream" -> {
                val curCh = repository.currentChannel.value
                val ip = repository.connectedIp.value
                if (curCh != null && ip.isNotBlank()) {
                    val intent = android.content.Intent(activity, com.gmscreen.accessible.ui.VideoPlayerActivity::class.java).apply {
                        putExtra("EXTRA_CHANNEL_NAME", curCh.serviceName)
                        putExtra("EXTRA_SERVICE_ID", curCh.serviceId)
                    }
                    activity.startActivity(intent)
                } else {
                    Toast.makeText(activity, "شبکه‌ای برای پخش یافت نشد", Toast.LENGTH_SHORT).show()
                }
            }
            "find_channel" -> {
                (activity as? com.gmscreen.accessible.ui.MainActivity)?.switchToTab(1)
            }
            "epg" -> sendRcuKeyMatchingRemote(RcuKey.EPG, buttonView, "جدول برنامه‌ها")
            "info" -> sendRcuKeyMatchingRemote(RcuKey.INFO, buttonView, "اطلاعات برنامه")
            "audio_track" -> sendRcuKeyMatchingRemote(RcuKey.AUDIO, buttonView, "تغییر صدا")
            "subtitle" -> sendRcuKeyMatchingRemote(RcuKey.SUBTITLE, buttonView, "زیرنویس")
            "recall" -> sendRcuKeyMatchingRemote(RcuKey.RECALL, buttonView, "کانال قبلی")
            "sat_list" -> sendRcuKeyMatchingRemote(RcuKey.SATELLITE, buttonView, "لیست ماهواره‌ها")
            "fav_list" -> sendRcuKeyMatchingRemote(RcuKey.FAVORITE, buttonView, "کانال‌های برگزیده")
        }
    }

    private fun executeMute(context: Context, view: View?) {
        if (!repository.isConnected.value) {
            val msg = "رسیور متصل نیست. لطفاً ابتدا به رسیور متصل شوید."
            AccessibilityHelper.announce(view, msg)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            repository.sendRcuKey(RcuKey.MUTE)
            delay(250)
            val isMuted = repository.getMuteState()
            val msg = when (isMuted) {
                true -> "صدا قطع شد"
                false -> "صدا وصل شد"
                null -> "بی‌صدا"
            }
            withContext(Dispatchers.Main) {
                AccessibilityHelper.announce(view, msg)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun executePower(context: Context, view: View?) {
        if (!repository.isConnected.value) {
            val msg = "رسیور متصل نیست. لطفاً ابتدا به رسیور متصل شوید."
            AccessibilityHelper.announce(view, msg)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val wasOn = repository.isStbPowerOn()
            repository.sendRcuKey(RcuKey.POWER_TOGGLE)
            val msg = if (wasOn) "رسیور خاموش شد" else "رسیور روشن شد"
            withContext(Dispatchers.Main) {
                AccessibilityHelper.announce(view, msg)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendRcuKeyMatchingRemote(keyCode: Int, view: View?, text: String) {
        if (!repository.isConnected.value) {
            val msg = "رسیور متصل نیست. لطفاً ابتدا به رسیور متصل شوید."
            AccessibilityHelper.announce(view, msg)
            Toast.makeText(GMScreenApplication.instance, msg, Toast.LENGTH_SHORT).show()
            return
        }
        AccessibilityHelper.announce(view, text)
        Toast.makeText(GMScreenApplication.instance, text, Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            repository.sendRcuKey(keyCode)
        }
    }

    fun showCustomizeKeysDialog(context: Context, appConfig: AppConfig, onSaved: () -> Unit) {
        val allTitles = ALL_AVAILABLE_ACTIONS.map { it.defaultTitle }.toTypedArray()
        val currentSelected = appConfig.quickAccessItems
        val checkedStates = BooleanArray(ALL_AVAILABLE_ACTIONS.size) { i ->
            currentSelected.contains(ALL_AVAILABLE_ACTIONS[i].id)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("سفارشی‌سازی کلیدهای پنل دسترسی سریع")
            .setMultiChoiceItems(allTitles, checkedStates) { _, which, isChecked ->
                checkedStates[which] = isChecked
            }
            .setPositiveButton("ذخیره و اعمال") { _, _ ->
                val newSet = mutableSetOf<String>()
                for (i in checkedStates.indices) {
                    if (checkedStates[i]) {
                        newSet.add(ALL_AVAILABLE_ACTIONS[i].id)
                    }
                }
                if (newSet.isEmpty()) newSet.add("status")
                appConfig.quickAccessItems = newSet
                onSaved()
                val saveMsg = "کلیدهای دسترسی سریع به‌روزرسانی شد"
                AccessibilityHelper.announce(null, saveMsg)
                Toast.makeText(context, saveMsg, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("پیش‌فرض") { _, _ ->
                appConfig.quickAccessItems = appConfig.defaultQuickAccessItems
                onSaved()
                val defMsg = "تنظیمات به حالت پیش‌فرض بازگشت"
                AccessibilityHelper.announce(null, defMsg)
                Toast.makeText(context, defMsg, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    // --- ViewPager2 Adapter for Paging Action Buttons ---
    private class QuickActionPagerAdapter(
        private val activity: Activity,
        private val pages: List<List<ActionItem>>,
        private val onActionClick: (ActionItem, MaterialButton) -> Unit,
        private val onActionLongClick: (ActionItem) -> Unit
    ) : RecyclerView.Adapter<QuickActionPagerAdapter.PageViewHolder>() {

        class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val recycler: RecyclerView = itemView as RecyclerView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val rv = RecyclerView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                layoutManager = GridLayoutManager(parent.context, 2)
                setPadding(4, 4, 4, 4)
            }
            return PageViewHolder(rv)
        }

        override fun getItemCount(): Int = pages.size

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val itemsForPage = pages[position]
            holder.recycler.adapter = QuickActionItemAdapter(activity, itemsForPage, onActionClick, onActionLongClick)
        }
    }

    // --- Grid Adapter for Individual Buttons inside each page ---
    private class QuickActionItemAdapter(
        private val activity: Activity,
        private val items: List<ActionItem>,
        private val onClick: (ActionItem, MaterialButton) -> Unit,
        private val onLongClick: (ActionItem) -> Unit
    ) : RecyclerView.Adapter<QuickActionItemAdapter.BtnViewHolder>() {

        class BtnViewHolder(val button: MaterialButton) : RecyclerView.ViewHolder(button)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BtnViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_quick_action_btn, parent, false) as MaterialButton
            return BtnViewHolder(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: BtnViewHolder, position: Int) {
            val item = items[position]
            holder.button.text = item.title
            holder.button.contentDescription = "${item.title}. ${item.description}"
            holder.button.setBackgroundColor(item.bgTint)

            holder.button.setOnClickListener {
                onClick(item, holder.button)
            }

            holder.button.setOnLongClickListener {
                onLongClick(item)
                true
            }

            androidx.core.view.ViewCompat.addAccessibilityAction(
                holder.button,
                "حذف این کلید از دسترسی سریع"
            ) { _, _ ->
                onLongClick(item)
                true
            }
        }
    }
}
