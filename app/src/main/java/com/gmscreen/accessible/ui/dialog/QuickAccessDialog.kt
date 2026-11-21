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
import com.gmscreen.accessible.data.model.Channel
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
        val title: String,
        val description: String,
        val bgTint: Int = Color.parseColor("#1F2A3C"),
        val isDefault: Boolean = false
    )

    val ALL_AVAILABLE_ACTIONS = listOf(
        ActionItem("power", "🔴 روشن / استندبای (Power)", "خاموش یا روشن کردن رسیور", Color.parseColor("#B91C1C"), true),
        ActionItem("ch_up", "🔼 کانال بعدی (CH+)", "تعویض کانال به جلو", Color.parseColor("#047857"), true),
        ActionItem("ch_down", "🔽 کانال قبلی (CH-)", "تعویض کانال به عقب", Color.parseColor("#047857"), true),
        ActionItem("vol_slider", "🔊 لغزنده تنظیم صدا (گام‌های ۵ تایی)", "کادر لغزنده ولوم رسیور در بالای پنل", Color.parseColor("#1D4ED8"), true),
        ActionItem("mute", "🔇 بی‌صدا / باصدا (Mute)", "قطع یا وصل فوری صدای رسیور", Color.parseColor("#B45309"), true),
        ActionItem("rec_stb_instant", "🔴 ضبط فوری روی فلش رسیور", "آغاز فوری ضبط شبکه در حال پخش روی فلش رسیور", Color.parseColor("#B91C1C"), true),
        ActionItem("rec_stb_stop", "⏹ توقف ضبط فلش رسیور", "پایان دادن به ضبط فلش مموری رسیور", Color.parseColor("#374151"), true),
        ActionItem("rec_phone_instant", "🎙 ضبط صوتی فوری روی گوشی", "آغاز ضبط صدای شبکه زنده روی حافظه گوشی", Color.parseColor("#7C3AED"), true),
        ActionItem("rec_phone_stop", "⏹ توقف ضبط گوشی", "پایان ضبط و ذخیره‌سازی فایل صوتی روی گوشی", Color.parseColor("#374151"), true),
        ActionItem("status", "📢 گزارش وضعیت و زمان ضبط", "خواندن صوتی وضعیت رسیور، شبکه فعلی و مدت ضبط", Color.parseColor("#0369A1"), true),
        ActionItem("play_stream", "▶️ پخش زنده در پلیر گوشی", "پخش زنده تصویر شبکه فعلی در پلیر داخلی یا VLC", Color.parseColor("#0284C7"), true),
        ActionItem("epg", "📅 جدول برنامه‌ها (EPG)", "باز کردن جدول برنامه‌های رسیور", Color.parseColor("#1F2937")),
        ActionItem("info", "ℹ️ اطلاعات برنامه (INFO)", "نمایش توضیحات و اطلاعات برنامه فعلی", Color.parseColor("#1F2937")),
        ActionItem("audio_track", "🎧 تغییر زبان صدا (AUDIO)", "تغییر ترک یا زبان صوتی شبکه", Color.parseColor("#1F2937")),
        ActionItem("subtitle", "💬 زیرنویس (SUB)", "تنظیمات زیرنویس رسیور", Color.parseColor("#1F2937")),
        ActionItem("recall", "↩️ بازگشت به شبکه قبل (RECALL)", "پرش به آخرین شبکه مشاهده شده", Color.parseColor("#1F2937")),
        ActionItem("find_channel", "🔍 جستجوی سریع شبکه‌ها", "انتقال به بخش جستجوی کانال‌ها", Color.parseColor("#0284C7")),
        ActionItem("sat_list", "🛰 لیست ماهواره‌ها (SAT)", "باز کردن منوی لیست جهت‌های رسیور", Color.parseColor("#1F2937")),
        ActionItem("fav_list", "⭐ کانال‌های برگزیده (FAV)", "باز کردن لیست شبکه‌های مورد علاقه", Color.parseColor("#B45309")),
        ActionItem("restart_stb", "🔄 راه‌اندازی مجدد رسیور (Reboot)", "ری‌استارت کردن دستگاه رسیور از راه دور", Color.parseColor("#991B1B"))
    )

    fun show(activity: Activity) {
        val context = activity
        val appConfig = AppConfig(context)
        val repository = GMScreenApplication.instance.repository
        val dialogScope = CoroutineScope(Dispatchers.Main)

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_quick_access, null)
        val btnCloseTop = dialogView.findViewById<MaterialButton>(R.id.btnCloseQuickAccessTop)
        val btnCloseBottom = dialogView.findViewById<MaterialButton>(R.id.btnCloseQuickAccessBottom)
        val btnCustomize = dialogView.findViewById<MaterialButton>(R.id.btnCustomizeQuickAccess)
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

        // 1. Setup Volume Slider
        var currentVol = appConfig.stbVolume.coerceIn(0, 100)
        // round to nearest 5
        currentVol = ((currentVol + 2) / 5) * 5

        fun updateVolumeDisplay(vol: Int, announce: Boolean = false) {
            currentVol = vol.coerceIn(0, 100)
            txtVolumeBadge.text = "$currentVol٪"
            seekBarVolume.progress = currentVol
            seekBarVolume.contentDescription = "ولوم صدای رسیور: $currentVol درصد. با کشیدن انگشت به بالا و پایین یا گام‌های پنج‌تایی تغییر می‌کند."
            if (announce) {
                AccessibilityHelper.announce(seekBarVolume, "صدای رسیور: $currentVol درصد")
            }
        }

        updateVolumeDisplay(currentVol, announce = false)

        val adjustVolumeBy = { delta: Int ->
            val oldVol = currentVol
            val newVol = (currentVol + delta).coerceIn(0, 100)
            if (newVol != oldVol) {
                currentVol = newVol
                appConfig.stbVolume = newVol
                updateVolumeDisplay(newVol, announce = true)
                // Send RCU keys
                dialogScope.launch(Dispatchers.IO) {
                    if (repository.isConnected.value) {
                        val steps = Math.abs(delta) / 5
                        for (i in 0 until steps) {
                            if (delta > 0) repository.sendRcuKey(RcuKey.VOLUME_UP)
                            else repository.sendRcuKey(RcuKey.VOLUME_DOWN)
                        }
                    }
                }
            }
        }

        seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val snapped = ((progress + 2) / 5) * 5
                    if (snapped != currentVol) {
                        val delta = snapped - currentVol
                        currentVol = snapped
                        appConfig.stbVolume = snapped
                        updateVolumeDisplay(snapped, announce = false)
                        dialogScope.launch(Dispatchers.IO) {
                            if (repository.isConnected.value) {
                                val steps = Math.abs(delta) / 5
                                for (i in 0 until steps) {
                                    if (delta > 0) repository.sendRcuKey(RcuKey.VOLUME_UP)
                                    else repository.sendRcuKey(RcuKey.VOLUME_DOWN)
                                }
                            }
                        }
                    }
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                AccessibilityHelper.announce(seekBarVolume, "صدای رسیور تنظیم شد: $currentVol درصد")
            }
        })

        btnVolMinus5.setOnClickListener {
            adjustVolumeBy(-5)
        }

        btnVolPlus5.setOnClickListener {
            adjustVolumeBy(5)
        }

        btnQuickMute.setOnClickListener {
            dialogScope.launch(Dispatchers.IO) {
                repository.sendRcuKey(RcuKey.MUTE)
                withContext(Dispatchers.Main) {
                    val msg = "دستور قطع / وصل صدا (Mute) به رسیور ارسال شد"
                    AccessibilityHelper.announce(btnQuickMute, msg)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 2. Setup Action Buttons Pager (Carousel)
        fun refreshActionButtons() {
            val selectedIds = appConfig.quickAccessItems

            // Show/hide volume card based on user preference
            val showVolSlider = selectedIds.contains("vol_slider")
            cardVolume.visibility = if (showVolSlider) View.VISIBLE else View.GONE

            // Filter actions for the pager (excluding vol_slider as it has its own dedicated card)
            val actionsToDisplay = ALL_AVAILABLE_ACTIONS.filter {
                it.id != "vol_slider" && selectedIds.contains(it.id)
            }

            // Split into pages (up to 6 items per page)
            val pageSize = 6
            val pages = if (actionsToDisplay.isEmpty()) {
                listOf(emptyList())
            } else {
                actionsToDisplay.chunked(pageSize)
            }

            pager.adapter = QuickActionPagerAdapter(pages) { action ->
                handleQuickAction(activity, action)
            }

            val totalPages = pages.size
            if (totalPages <= 1) {
                rowNavigation.visibility = View.GONE
            } else {
                rowNavigation.visibility = View.VISIBLE
                val updateNavState = { pos: Int ->
                    txtPageIndicator.text = "صفحه ${pos + 1} از $totalPages (کشیدن با ۲ انگشت)"
                    btnPrevPage.isEnabled = pos > 0
                    btnNextPage.isEnabled = pos < totalPages - 1
                }
                updateNavState(0)

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

        // 3. Customize Button Click -> MultiChoice Dialog
        btnCustomize.setOnClickListener {
            val allTitles = ALL_AVAILABLE_ACTIONS.map { it.title }.toTypedArray()
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
                    if (newSet.isEmpty()) {
                        // At least keep one
                        newSet.add("status")
                    }
                    appConfig.quickAccessItems = newSet
                    refreshActionButtons()
                    val saveMsg = "کلیدهای دسترسی سریع با موفقیت به‌روزرسانی شدند."
                    AccessibilityHelper.announce(dialogView, saveMsg)
                    Toast.makeText(context, saveMsg, Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("بازگردانی به پیش‌فرض") { _, _ ->
                    appConfig.quickAccessItems = appConfig.defaultQuickAccessItems
                    refreshActionButtons()
                    val defMsg = "تنظیمات دسترسی سریع به حالت پیش‌فرض بازگشت."
                    AccessibilityHelper.announce(dialogView, defMsg)
                    Toast.makeText(context, defMsg, Toast.LENGTH_SHORT).show()
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
        AccessibilityHelper.announce(dialogView, "پنل کنترل و دسترسی سریع رسیور باز شد")
    }

    private fun handleQuickAction(activity: Activity, action: ActionItem) {
        val repository = GMScreenApplication.instance.repository
        val isConnected = repository.isConnected.value

        when (action.id) {
            "power" -> {
                sendRcuKeyAsync(RcuKey.POWER, "دستور پاور (روشن/خاموش)")
            }
            "ch_up" -> {
                sendRcuKeyAsync(RcuKey.CHANNEL_UP, "کانال بعدی")
            }
            "ch_down" -> {
                sendRcuKeyAsync(RcuKey.CHANNEL_DOWN, "کانال قبلی")
            }
            "mute" -> {
                sendRcuKeyAsync(RcuKey.MUTE, "بی‌صدا / باصدا")
            }
            "epg" -> {
                sendRcuKeyAsync(RcuKey.EPG, "جدول راهنمای برنامه‌ها (EPG)")
            }
            "info" -> {
                sendRcuKeyAsync(RcuKey.INFO, "اطلاعات برنامه (INFO)")
            }
            "audio_track" -> {
                sendRcuKeyAsync(RcuKey.AUDIO, "تغییر ترک صدا (AUDIO)")
            }
            "subtitle" -> {
                sendRcuKeyAsync(RcuKey.SUBTITLE, "زیرنویس (SUB)")
            }
            "recall" -> {
                sendRcuKeyAsync(RcuKey.RECALL, "بازگشت به کانال قبلی (RECALL)")
            }
            "sat_list" -> {
                sendRcuKeyAsync(RcuKey.SATELLITE, "لیست ماهواره‌ها (SAT)")
            }
            "fav_list" -> {
                sendRcuKeyAsync(RcuKey.FAVORITE, "کانال‌های برگزیده (FAV)")
            }
            "restart_stb" -> {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("راه‌اندازی مجدد رسیور")
                    .setMessage("آیا مطمئن هستید که می‌خواهید رسیور را مجدداً راه‌اندازی (Reboot) کنید؟")
                    .setPositiveButton("بله، ری‌استارت شود") { _, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            repository.client.sendCmd(1011, expectReply = false)
                            withContext(Dispatchers.Main) {
                                val msg = "دستور راه‌اندازی مجدد به رسیور ارسال شد."
                                AccessibilityHelper.announce(activity.window.decorView, msg)
                                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("انصراف", null)
                    .show()
            }
            "status" -> {
                StatusHelper.announceStatus(activity, repository, activity.window.decorView)
            }
            "rec_stb_instant" -> {
                if (!isConnected) {
                    Toast.makeText(activity, "رسیور متصل نیست", Toast.LENGTH_SHORT).show()
                    return
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val curCh = repository.currentChannel.value?.serviceName ?: "شبکه جاری"
                    repository.sendRcuKey(RcuKey.RECORD)
                    repository.setStbRecording(true, curCh)
                    withContext(Dispatchers.Main) {
                        val msg = "فرمان آغاز ضبط فوری شبکه $curCh بر روی فلش رسیور ارسال شد"
                        AccessibilityHelper.announce(activity.window.decorView, msg)
                        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
            "rec_stb_stop" -> {
                if (!isConnected) {
                    Toast.makeText(activity, "رسیور متصل نیست", Toast.LENGTH_SHORT).show()
                    return
                }
                CoroutineScope(Dispatchers.IO).launch {
                    repository.sendRcuKey(RcuKey.STOP)
                    delay(300)
                    repository.sendRcuKey(RcuKey.OK)
                    repository.setStbRecording(false, null)
                    withContext(Dispatchers.Main) {
                        val msg = "فرمان توقف ضبط فلش رسیور ارسال شد"
                        AccessibilityHelper.announce(activity.window.decorView, msg)
                        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
            "rec_phone_instant" -> {
                val ip = repository.connectedIp.value
                val curCh = repository.currentChannel.value
                if (ip.isBlank() || curCh == null) {
                    Toast.makeText(activity, "شبکه یا اتصال برای ضبط صوتی روی گوشی فعال نیست", Toast.LENGTH_SHORT).show()
                    return
                }
                if (com.gmscreen.accessible.player.StreamRecorderManager.isRecording.value) {
                    Toast.makeText(activity, "ضبط صوتی در حال حاضر روی گوشی فعال است", Toast.LENGTH_SHORT).show()
                    return
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val file = com.gmscreen.accessible.player.StreamRecorderManager.startRecording(
                        activity,
                        repository.client,
                        ip,
                        curCh,
                        isAudioOnly = true
                    )
                    withContext(Dispatchers.Main) {
                        if (file != null) {
                            val msg = "ضبط صوتی شبکه ${curCh.serviceName} روی گوشی آغاز شد"
                            AccessibilityHelper.announce(activity.window.decorView, msg)
                            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                        } else {
                            val err = "خطا در شروع ضبط صوتی روی گوشی"
                            AccessibilityHelper.announce(activity.window.decorView, err)
                            Toast.makeText(activity, err, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            "rec_phone_stop" -> {
                if (!com.gmscreen.accessible.player.StreamRecorderManager.isRecording.value) {
                    Toast.makeText(activity, "هیچ ضبط فعالی روی گوشی در حال اجرا نیست", Toast.LENGTH_SHORT).show()
                    return
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val saved = com.gmscreen.accessible.player.StreamRecorderManager.stopRecording()
                    withContext(Dispatchers.Main) {
                        val msg = "ضبط صوتی روی گوشی متوقف شد: ${saved?.name ?: ""}"
                        AccessibilityHelper.announce(activity.window.decorView, msg)
                        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
            "play_stream" -> {
                val curCh = repository.currentChannel.value
                val ip = repository.connectedIp.value
                if (curCh != null && ip.isNotBlank()) {
                    val intent = android.content.Intent(activity, com.gmscreen.accessible.player.VideoPlayerActivity::class.java).apply {
                        putExtra(com.gmscreen.accessible.player.VideoPlayerActivity.EXTRA_CHANNEL, curCh)
                        putExtra(com.gmscreen.accessible.player.VideoPlayerActivity.EXTRA_STB_IP, ip)
                    }
                    activity.startActivity(intent)
                } else {
                    Toast.makeText(activity, "شبکه‌ای برای پخش انتخاب نشده است", Toast.LENGTH_SHORT).show()
                }
            }
            "find_channel" -> {
                (activity as? com.gmscreen.accessible.ui.MainActivity)?.switchToTab(1)
            }
        }
    }

    private fun sendRcuKeyAsync(keyCode: Int, actionName: String) {
        val repository = GMScreenApplication.instance.repository
        CoroutineScope(Dispatchers.IO).launch {
            repository.sendRcuKey(keyCode)
            withContext(Dispatchers.Main) {
                Toast.makeText(GMScreenApplication.instance, actionName, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- ViewPager2 Adapter for Paging Action Buttons ---
    private class QuickActionPagerAdapter(
        private val pages: List<List<ActionItem>>,
        private val onActionClick: (ActionItem) -> Unit
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
            holder.recycler.adapter = QuickActionItemAdapter(itemsForPage, onActionClick)
        }
    }

    // --- Grid Adapter for Individual Buttons inside each page ---
    private class QuickActionItemAdapter(
        private val items: List<ActionItem>,
        private val onClick: (ActionItem) -> Unit
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
                AccessibilityHelper.announce(holder.button, item.title)
                onClick(item)
            }
        }
    }
}
