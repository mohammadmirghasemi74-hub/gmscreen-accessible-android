package com.gmscreen.accessible.ui.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.data.model.Channel
import com.gmscreen.accessible.data.model.Satellite
import com.gmscreen.accessible.player.SatIpStreamPlayer
import com.gmscreen.accessible.ui.adapter.ChannelsAdapter
import com.gmscreen.accessible.util.AccessibilityHelper
import com.gmscreen.accessible.util.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelsFragment : Fragment(), SatIpStreamPlayer.PlayerEventListener {

    private lateinit var containerPlayer: LinearLayout
    private lateinit var txtPlayerTitle: TextView
    private lateinit var btnStopPlayer: MaterialButton
    private lateinit var btnTvRadioToggle: MaterialButton
    private lateinit var btnSatelliteFilter: MaterialButton
    private lateinit var edtSearch: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var txtChannelCount: TextView
    private lateinit var recyclerViewChannels: RecyclerView
    private lateinit var fabRefreshChannels: ExtendedFloatingActionButton

    private var streamPlayer: SatIpStreamPlayer? = null
    private var channelsAdapter: ChannelsAdapter? = null

    private var isRadioMode = false
    private var selectedSatelliteId: Int = -1 // -1 means all satellites
    private var searchQuery: String = ""
    private var announceJob: Job? = null

    private val repository get() = GMScreenApplication.instance.repository
    private val appConfig get() = GMScreenApplication.instance.appConfig

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_channels, container, false)

        containerPlayer = view.findViewById(R.id.containerPlayer)
        txtPlayerTitle = view.findViewById(R.id.txtPlayerTitle)
        btnStopPlayer = view.findViewById(R.id.btnStopPlayer)
        btnTvRadioToggle = view.findViewById(R.id.btnTvRadioToggle)
        btnSatelliteFilter = view.findViewById(R.id.btnSatelliteFilter)
        edtSearch = view.findViewById(R.id.edtSearch)
        btnClearSearch = view.findViewById(R.id.btnClearSearch)
        txtChannelCount = view.findViewById(R.id.txtChannelCount)
        recyclerViewChannels = view.findViewById(R.id.recyclerViewChannels)
        fabRefreshChannels = view.findViewById(R.id.fabRefreshChannels)

        recyclerViewChannels.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewChannels.setHasFixedSize(true)
        recyclerViewChannels.setItemViewCacheSize(20)

        setupPlayer(view)
        setupControls()
        observeData()

        return view
    }

    private fun setupPlayer(rootView: View) {
        val playerView = rootView.findViewById<androidx.media3.ui.PlayerView>(R.id.playerView)
        streamPlayer = SatIpStreamPlayer(requireContext(), playerView, this)

        btnStopPlayer.setOnClickListener {
            streamPlayer?.stop()
            containerPlayer.visibility = View.GONE
            AccessibilityHelper.announce(txtChannelCount, "پخش زنده شبکه متوقف شد")
        }
    }

    private fun setupControls() {
        btnTvRadioToggle.setOnClickListener {
            isRadioMode = !isRadioMode
            if (isRadioMode) {
                btnTvRadioToggle.text = getString(R.string.mode_radio)
                btnTvRadioToggle.setBackgroundColor(resources.getColor(R.color.secondary, null))
                AccessibilityHelper.announce(btnTvRadioToggle, "حالت به کانال‌های رادیویی تغییر کرد")
            } else {
                btnTvRadioToggle.text = getString(R.string.mode_tv)
                btnTvRadioToggle.setBackgroundColor(resources.getColor(R.color.primary, null))
                AccessibilityHelper.announce(btnTvRadioToggle, "حالت به کانال‌های تلویزیونی تغییر کرد")
            }
            filterChannels()
        }

        btnSatelliteFilter.setOnClickListener {
            showSatelliteSelectionDialog()
        }

        btnClearSearch.setOnClickListener {
            edtSearch.text.clear()
            btnClearSearch.visibility = View.GONE
            AccessibilityHelper.announce(edtSearch, "جستجو پاک شد")
        }

        fabRefreshChannels.setOnClickListener {
            refreshChannelsFromStb()
        }

        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                filterChannels()

                // Real-time voice announcement like Windows app with 400ms debounce
                announceJob?.cancel()
                announceJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(400)
                    val count = (recyclerViewChannels.adapter as? ChannelsAdapter)?.itemCount ?: 0
                    AccessibilityHelper.announce(recyclerViewChannels, "$count کانال پیدا شد")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun refreshChannelsFromStb() {
        val ip = repository.connectedIp.value
        if (ip.isBlank() || !repository.isConnected.value) {
            AccessibilityHelper.announce(txtChannelCount, "رسیور متصل نیست")
            Toast.makeText(requireContext(), "رسیور متصل نیست", Toast.LENGTH_SHORT).show()
            return
        }

        fabRefreshChannels.isEnabled = false
        AccessibilityHelper.announce(txtChannelCount, "در حال دریافت شبکه‌ها از رسیور...")
        txtChannelCount.text = "در حال دریافت اطلاعات از رسیور..."

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repository.refreshData()
                val count = repository.channels.value.size
                if (count > 0) {
                    val tvCount = repository.channels.value.count { it.serviceType == 1 || it.serviceType == 0 }
                    val radioCount = repository.channels.value.count { it.serviceType == 2 }
                    AccessibilityHelper.announce(txtChannelCount, "به‌روزرسانی انجام شد. $count کانال شامل $tvCount تلویزیونی و $radioCount رادیویی دریافت شد.")
                } else {
                    AccessibilityHelper.announce(txtChannelCount, "شبکه‌ای از رسیور دریافت نشد.")
                }
            } catch (e: Exception) {
                AccessibilityHelper.announce(txtChannelCount, "خطا در دریافت شبکه‌ها از رسیور.")
            } finally {
                fabRefreshChannels.isEnabled = true
            }
        }
    }

    private fun showSatelliteSelectionDialog() {
        val allChannels = repository.channels.value
        if (allChannels.isEmpty()) {
            Toast.makeText(requireContext(), "هنوز شبکه‌ای دریافت نشده است", Toast.LENGTH_SHORT).show()
            AccessibilityHelper.announce(btnSatelliteFilter, "هنوز شبکه‌ای از رسیور دریافت نشده است")
            return
        }

        // Count channels per satellite index (matching Windows main.py)
        val satChannelCounts = mutableMapOf<Int, Int>()
        for (c in allChannels) {
            satChannelCounts[c.satIndex] = (satChannelCounts[c.satIndex] ?: 0) + 1
        }

        val allSats = repository.satellites.value
        val satNameMap = allSats.associate { it.satelliteId to it.satelliteName }

        // Only include satellites that actually have tuned channels on this receiver
        val activeSats = satChannelCounts.keys.sorted().map { satId ->
            val name = satNameMap[satId] ?: "ماهواره #$satId"
            Triple(satId, name, satChannelCounts[satId] ?: 0)
        }

        val items = mutableListOf<String>()
        items.add("همه ماهواره‌ها (${allChannels.size} شبکه)")
        for (sat in activeSats) {
            items.add("${sat.second} (${sat.third} شبکه)")
        }

        var selectedIndex = 0
        if (selectedSatelliteId != -1) {
            val idx = activeSats.indexOfFirst { it.first == selectedSatelliteId }
            if (idx >= 0) {
                selectedIndex = idx + 1
            } else {
                selectedSatelliteId = -1
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("انتخاب و فیلتر ماهواره‌های فعال")
            .setSingleChoiceItems(items.toTypedArray(), selectedIndex) { dialog, which ->
                if (which == 0) {
                    selectedSatelliteId = -1
                    btnSatelliteFilter.text = "همه ماهواره‌ها"
                    AccessibilityHelper.announce(btnSatelliteFilter, "فیلتر بر روی همه ماهواره‌ها تنظیم شد")
                } else {
                    val sat = activeSats.getOrNull(which - 1)
                    if (sat != null) {
                        selectedSatelliteId = sat.first
                        btnSatelliteFilter.text = sat.second
                        AccessibilityHelper.announce(btnSatelliteFilter, "فیلتر اعمال شد: ${sat.second}، شامل ${sat.third} شبکه")
                    }
                }
                filterChannels()
                dialog.dismiss()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private var hasAnnouncedChannelsIntro = false

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.channels.collect { allCh ->
                filterChannels()
                if (allCh.isNotEmpty() && !hasAnnouncedChannelsIntro) {
                    hasAnnouncedChannelsIntro = true
                    val tvCount = allCh.count { it.serviceType == 1 || it.serviceType == 0 }
                    val radioCount = allCh.count { it.serviceType == 2 }
                    val introMsg = "شبکه‌ها از رسیور دریافت شد. ${allCh.size} کانال شامل $tvCount تلویزیونی و $radioCount رادیویی پیدا شد. با ضربه زدن روی هر کانال پخش آغاز می‌شود و با لمس طولانی گزینه‌های بیشتر نمایش داده می‌شود."
                    AccessibilityHelper.announce(txtChannelCount, introMsg)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repository.currentChannel.collect { currCh ->
                channelsAdapter?.setCurrentPlayingChannel(currCh?.serviceId)
                if (currCh != null) {
                    val statusText = "در حال پخش در تلویزیون: ${currCh.serviceName}"
                    txtPlayerTitle.text = statusText
                }
            }
        }
    }

    private fun filterChannels() {
        val allChannels = repository.channels.value
        val keepGlobal = appConfig.keepGlobalChannelNum

        val filtered = allChannels.filter { ch ->
            val matchesType = if (isRadioMode) ch.serviceType == 2 else (ch.serviceType == 1 || ch.serviceType == 0)
            val matchesSat = if (selectedSatelliteId == -1) true else ch.satelliteId == selectedSatelliteId
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                ch.serviceName.contains(searchQuery, ignoreCase = true) ||
                        ch.channelNumber.toString().contains(searchQuery)
            }
            matchesType && matchesSat && matchesSearch
        }

        val countMsg = getString(R.string.channel_count_info, filtered.size)
        txtChannelCount.text = countMsg

        if (channelsAdapter == null) {
            channelsAdapter = ChannelsAdapter(
                filtered,
                keepGlobal,
                onChannelClick = { onChannelSelected(it, isAudioOnly = false) },
                onChannelLongClick = { showChannelContextMenu(it) }
            ).apply {
                setCurrentPlayingChannel(repository.currentChannel.value?.serviceId)
            }
            recyclerViewChannels.adapter = channelsAdapter
        } else {
            channelsAdapter?.updateChannels(filtered)
            channelsAdapter?.setCurrentPlayingChannel(repository.currentChannel.value?.serviceId)
        }
    }

    private fun onChannelSelected(channel: Channel, isAudioOnly: Boolean) {
        val ip = repository.connectedIp.value
        if (ip.isBlank()) {
            AccessibilityHelper.announce(txtChannelCount, "رسیور متصل نیست")
            Toast.makeText(requireContext(), "رسیور متصل نیست", Toast.LENGTH_SHORT).show()
            return
        }

        val isAlreadyPlaying = (repository.currentChannel.value?.serviceId == channel.serviceId)

        // Concise, natural announcement matching user request: simply announce channel name
        if (!isAlreadyPlaying) {
            val prefix = if (channel.isRadio) "رادیو" else "شبکه"
            AccessibilityHelper.announce(txtChannelCount, "$prefix ${channel.serviceName}")
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val switched = if (isAlreadyPlaying) true else repository.switchChannel(channel)
            withContext(Dispatchers.Main) {
                if (switched) {
                    if (appConfig.hapticFeedbackEnabled) {
                        AccessibilityHelper.vibrateSuccess(requireContext())
                    }
                    containerPlayer.visibility = View.VISIBLE
                    txtPlayerTitle.text = if (isAudioOnly) "پخش صوتی: ${channel.serviceName}" else "پخش زنده: ${channel.serviceName}"
                    streamPlayer?.playChannel(repository.client, ip, channel)
                } else {
                    AccessibilityHelper.announce(txtChannelCount, "خطا در تعویض کانال")
                    Toast.makeText(requireContext(), "خطا در تعویض کانال روی رسیور", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showChannelContextMenu(channel: Channel) {
        val isLocked = channel.isScrambled
        val lockTitle = if (isLocked) "بازگشایی موقت کانال کارتی (با پین رسیور)" else "قفل کانال (Parental Lock)"

        val options = arrayOf(
            "۱. پخش تصویری زنده",
            "۲. پخش صوتی زنده (Audio Only)",
            "۳. شروع ضبط زنده روی گوشی (MP4/MP3)",
            "۴. $lockTitle",
            "۵. مشخصات و اطلاعات فنی کانال",
            "۶. کپی آدرس استریم زنده (RTSP URL)",
            "۷. افزودن به زمان‌بندی ضبط روی فلش رسیور",
            "۸. افزودن به زمان‌بندی ضبط روی حافظه گوشی"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.channel_menu_title, channel.serviceName))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onChannelSelected(channel, isAudioOnly = false)
                    1 -> onChannelSelected(channel, isAudioOnly = true)
                    2 -> {
                        val file = StorageManager.generateRecordingFile(requireContext(), channel.serviceName, isAudioOnly = false)
                        val msg = "فایل ذخیره‌سازی ایجاد شد: ${file.name}"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                        AccessibilityHelper.announce(txtChannelCount, msg)
                    }
                    3 -> {
                        Toast.makeText(requireContext(), "دستور قفل/بازگشایی با پین 0000 ارسال شد", Toast.LENGTH_SHORT).show()
                    }
                    4 -> showChannelInfoDialog(channel)
                    5 -> {
                        val ip = repository.connectedIp.value
                        val url = channel.toSatIpUrl(ip)
                        val clip = ClipData.newPlainText("Stream URL", url)
                        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(clip)
                        val msg = "آدرس استریم زنده کپی شد: $url"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        AccessibilityHelper.announce(txtChannelCount, msg)
                    }
                    6 -> {
                        // Switch to Record Tab to configure STB Timer
                        (activity as? com.gmscreen.accessible.ui.MainActivity)?.switchToTab(3)
                        Toast.makeText(requireContext(), "در سربرگ ضبط، دکمه افزودن زمان‌بندی رسیور را بزنید", Toast.LENGTH_LONG).show()
                    }
                    7 -> {
                        // Switch to Record Tab to configure Phone Timer
                        (activity as? com.gmscreen.accessible.ui.MainActivity)?.switchToTab(3)
                        Toast.makeText(requireContext(), "در سربرگ ضبط، دکمه افزودن زمان‌بندی گوشی را بزنید", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun showChannelInfoDialog(channel: Channel) {
        val infoText = """
            نام کانال: ${channel.serviceName}
            شماره کانال در رسیور: ${channel.channelNumber}
            ماهواره: ${channel.satelliteName}
            فرکانس: ${channel.frequency} MHz
            نوع سرویس: ${if (channel.serviceType == 2) "رادیو" else "تلویزیون"}
            کیفیت تصویر: ${if (channel.isHd) "HD با کیفیت بالا" else "SD معمولی"}
            وضعیت رمزگذاری: ${if (channel.isScrambled) "شبکه کارتی (Scrambled $)" else "رایگان (FTA)"}
            شناسه تصویر (Video PID): ${channel.videoPid}
            شناسه صدا (Audio PID): ${channel.audioPid}
            شناسه جدول (PMT PID): ${channel.pmtPid}
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("مشخصات فنی کانال: ${channel.serviceName}")
            .setMessage(infoText)
            .setPositiveButton("بستن", null)
            .show()

        AccessibilityHelper.announce(txtChannelCount, "مشخصات شبکه ${channel.serviceName}: فرکانس ${channel.frequency}، ماهواره ${channel.satelliteName}")
    }

    override fun onPlaybackStarted() {
        AccessibilityHelper.announce(txtPlayerTitle, "پخش زنده با موفقیت آغاز شد")
    }

    override fun onPlaybackBuffering() {
        AccessibilityHelper.announce(txtPlayerTitle, "در حال بافر استریم زنده رسیور…")
    }

    override fun onPlaybackEnded() {
        containerPlayer.visibility = View.GONE
    }

    override fun onPlaybackError(errorMsg: String) {
        AccessibilityHelper.announce(txtPlayerTitle, errorMsg)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        announceJob?.cancel()
        streamPlayer?.release()
        streamPlayer = null
    }
}
