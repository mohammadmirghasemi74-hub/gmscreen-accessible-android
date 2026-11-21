package com.gmscreen.accessible.ui.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import com.gmscreen.accessible.player.SatIpStreamPlayer
import com.gmscreen.accessible.ui.adapter.ChannelsAdapter
import com.gmscreen.accessible.util.AccessibilityHelper
import com.gmscreen.accessible.util.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ChannelsFragment : Fragment() {

    private lateinit var layoutAudioPlayingBar: LinearLayout
    private lateinit var txtAudioPlayingChannel: TextView
    private lateinit var btnStopAudioPlayback: MaterialButton
    private lateinit var btnToggleRecordAudioInBar: MaterialButton
    private lateinit var btnTvRadioToggle: MaterialButton
    private lateinit var btnSatelliteFilter: MaterialButton
    private lateinit var edtSearch: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var txtChannelCount: TextView
    private lateinit var recyclerViewChannels: RecyclerView
    private lateinit var fabRefreshChannels: ExtendedFloatingActionButton
    private lateinit var fabRecordings: ExtendedFloatingActionButton
    private lateinit var layoutJumpButtons: LinearLayout
    private lateinit var btnJumpToFirstChannel: MaterialButton
    private lateinit var btnJumpToLastChannel: MaterialButton

    private var isAudioStreaming = false
    private var currentAudioChannel: Channel? = null

    private val audioPlayerListener = object : com.gmscreen.accessible.player.AudioStreamPlayer.AudioPlayerListener {
        override fun onPlaybackStateChanged(isPlaying: Boolean, channel: Channel?) {
            if (!isAdded) return
            activity?.runOnUiThread {
                if (channel != null) {
                    currentAudioChannel = channel
                    isAudioStreaming = isPlaying
                    layoutAudioPlayingBar.visibility = View.VISIBLE
                    txtAudioPlayingChannel.text = if (isPlaying) "در حال پخش صوتی: ${channel.serviceName}" else "پخش متوقف موقت: ${channel.serviceName}"
                    updateAudioBarRecordBtn(com.gmscreen.accessible.player.StreamRecorderManager.isRecording)
                } else {
                    currentAudioChannel = null
                    isAudioStreaming = false
                    layoutAudioPlayingBar.visibility = View.GONE
                }
            }
        }
    }
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

        layoutAudioPlayingBar = view.findViewById(R.id.layoutAudioPlayingBar)
        txtAudioPlayingChannel = view.findViewById(R.id.txtAudioPlayingChannel)
        btnStopAudioPlayback = view.findViewById(R.id.btnStopAudioPlayback)
        btnToggleRecordAudioInBar = view.findViewById(R.id.btnToggleRecordAudioInBar)
        btnTvRadioToggle = view.findViewById(R.id.btnTvRadioToggle)
        btnSatelliteFilter = view.findViewById(R.id.btnSatelliteFilter)
        edtSearch = view.findViewById(R.id.edtSearch)
        btnClearSearch = view.findViewById(R.id.btnClearSearch)
        txtChannelCount = view.findViewById(R.id.txtChannelCount)
        recyclerViewChannels = view.findViewById(R.id.recyclerViewChannels)
        fabRefreshChannels = view.findViewById(R.id.fabRefreshChannels)
        fabRecordings = view.findViewById(R.id.fabRecordings)
        layoutJumpButtons = view.findViewById(R.id.layoutJumpButtons)
        btnJumpToFirstChannel = view.findViewById(R.id.btnJumpToFirstChannel)
        btnJumpToLastChannel = view.findViewById(R.id.btnJumpToLastChannel)

        recyclerViewChannels.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewChannels.setHasFixedSize(true)
        recyclerViewChannels.setItemViewCacheSize(20)

        setupAudioPlayerBar()
        setupControls()
        updateJumpButtonsUI()
        observeData()

        return view
    }

    private fun setupAudioPlayerBar() {
        updateAudioBarRecordBtn(com.gmscreen.accessible.player.StreamRecorderManager.isRecording)
        com.gmscreen.accessible.player.AudioStreamPlayer.addListener(audioPlayerListener)
        val activeAudioCh = com.gmscreen.accessible.player.AudioStreamPlayer.currentChannel
        if (activeAudioCh != null) {
            currentAudioChannel = activeAudioCh
            isAudioStreaming = com.gmscreen.accessible.player.AudioStreamPlayer.isPlaying
            layoutAudioPlayingBar.visibility = View.VISIBLE
            txtAudioPlayingChannel.text = if (isAudioStreaming) "در حال پخش صوتی: ${activeAudioCh.serviceName}" else "پخش متوقف موقت: ${activeAudioCh.serviceName}"
        }

        btnToggleRecordAudioInBar.setOnClickListener {
            val ch = currentAudioChannel ?: repository.currentChannel.value
            val ip = repository.connectedIp.value
            if (ch == null || ip.isBlank()) {
                Toast.makeText(requireContext(), "مشخصات کانال یافت نشد", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (com.gmscreen.accessible.player.StreamRecorderManager.isRecording) {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val file = com.gmscreen.accessible.player.StreamRecorderManager.stopRecording()
                    withContext(Dispatchers.Main) {
                        updateAudioBarRecordBtn(false)
                        val msg = "ضبط صوتی متوقف و ذخیره شد: ${file?.name ?: ""}"
                        AccessibilityHelper.announce(btnToggleRecordAudioInBar, msg)
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val file = com.gmscreen.accessible.player.StreamRecorderManager.startRecording(
                        requireContext(),
                        repository.client,
                        ip,
                        ch,
                        isAudioOnly = true
                    )
                    withContext(Dispatchers.Main) {
                        if (file != null) {
                            updateAudioBarRecordBtn(true)
                            val msg = "ضبط صوتی شبکه ${ch.serviceName} روی گوشی آغاز شد"
                            AccessibilityHelper.announce(btnToggleRecordAudioInBar, msg)
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "خطا در شروع ضبط صوتی", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        btnStopAudioPlayback.setOnClickListener {
            stopAudioPlayback()
            val msg = "پخش صوتی شبکه متوقف شد"
            AccessibilityHelper.announce(txtChannelCount, msg)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAudioBarRecordBtn(isRecording: Boolean) {
        if (!::btnToggleRecordAudioInBar.isInitialized) return
        if (isRecording) {
            btnToggleRecordAudioInBar.text = "■ توقف ضبط"
            btnToggleRecordAudioInBar.contentDescription = "در حال ضبط صوتی در گوشی. برای توقف و ذخیره ضربه بزنید"
            btnToggleRecordAudioInBar.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary))
        } else {
            val audFmt = appConfig.audioRecFormat.uppercase()
            btnToggleRecordAudioInBar.text = "● ضبط ($audFmt)"
            btnToggleRecordAudioInBar.contentDescription = "شروع ضبط صوتی با فرمت $audFmt روی حافظه گوشی"
            btnToggleRecordAudioInBar.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.card_bg))
        }
    }

    private fun setupControls() {
        updateTvRadioToggleUI()

        btnTvRadioToggle.setOnClickListener {
            isRadioMode = !isRadioMode
            updateTvRadioToggleUI()
            filterChannels()
            val modeText = if (isRadioMode) "رادیویی" else "تلویزیونی"
            val count = (recyclerViewChannels.adapter as? ChannelsAdapter)?.itemCount ?: 0
            AccessibilityHelper.announce(recyclerViewChannels, "حالت به کانال‌های $modeText تغییر کرد. $count کانال پیدا شد")
            recyclerViewChannels.scrollToPosition(0)

            if (repository.isConnected.value) {
                val targetMode = if (isRadioMode) 1 else 0
                if (repository.currentStbMode != targetMode) {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        repository.sendRcuKey(com.gmscreen.accessible.protocol.RcuKey.TV_RADIO)
                        repository.currentStbMode = targetMode
                    }
                }
            }
        }

        btnSatelliteFilter.setOnClickListener {
            showSatelliteSelectionDialog()
        }

        btnClearSearch.setOnClickListener {
            edtSearch.text.clear()
            btnClearSearch.visibility = View.GONE
            edtSearch.clearFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(edtSearch.windowToken, 0)
            recyclerViewChannels.requestFocus()
            AccessibilityHelper.announce(recyclerViewChannels, "جستجو پاک شد. تمام کانال‌ها نمایش داده شدند")
        }

        btnJumpToFirstChannel.setOnClickListener {
            val adapter = channelsAdapter ?: return@setOnClickListener
            if (adapter.itemCount > 0) {
                val lm = recyclerViewChannels.layoutManager as? LinearLayoutManager
                lm?.scrollToPositionWithOffset(0, 0)
                recyclerViewChannels.postDelayed({
                    val vh = recyclerViewChannels.findViewHolderForAdapterPosition(0)
                    vh?.itemView?.requestFocus()
                    vh?.itemView?.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
                    val firstCh = adapter.getChannels().firstOrNull()
                    val name = firstCh?.serviceName ?: "کانال اول"
                    AccessibilityHelper.announce(recyclerViewChannels, "پرش به ابتدای لیست: کانال ۱، $name")
                }, 150)
            }
        }

        btnJumpToLastChannel.setOnClickListener {
            val adapter = channelsAdapter ?: return@setOnClickListener
            val count = adapter.itemCount
            if (count > 0) {
                val lastIdx = count - 1
                val lm = recyclerViewChannels.layoutManager as? LinearLayoutManager
                lm?.scrollToPositionWithOffset(lastIdx, 0)
                recyclerViewChannels.postDelayed({
                    val vh = recyclerViewChannels.findViewHolderForAdapterPosition(lastIdx)
                    vh?.itemView?.requestFocus()
                    vh?.itemView?.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
                    val lastCh = adapter.getChannels().lastOrNull()
                    val name = lastCh?.serviceName ?: "کانال آخر"
                    AccessibilityHelper.announce(recyclerViewChannels, "پرش به انتهای لیست: کانال $count، $name")
                }, 150)
            }
        }

        fabRefreshChannels.setOnClickListener {
            refreshChannelsFromStb()
        }

        fabRecordings.visibility = if (appConfig.showRecordingsInChannels) View.VISIBLE else View.GONE
        fabRecordings.setOnClickListener {
            com.gmscreen.accessible.util.RecordingsDialogHelper.showRecordingsDialog(requireContext(), fabRecordings)
        }

        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                filterChannels()

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

    private fun updateTvRadioToggleUI() {
        if (isRadioMode) {
            btnTvRadioToggle.text = "رادیو (فعال)"
            btnTvRadioToggle.contentDescription = "در حال حاضر در حالت رادیو هستید. ضربه بزنید تا به شبکه‌های تلویزیونی تغییر کند"
            btnTvRadioToggle.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.secondary))
        } else {
            btnTvRadioToggle.text = "تلویزیون (فعال)"
            btnTvRadioToggle.contentDescription = "در حال حاضر در حالت تلویزیون هستید. ضربه بزنید تا به شبکه‌های رادیویی تغییر کند"
            btnTvRadioToggle.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary))
        }
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
                val allCh = repository.channels.value
                val count = allCh.size
                if (count > 0) {
                    val radioCount = allCh.count { it.isRadio }
                    val tvCount = count - radioCount
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
            return
        }

        val satChannelCounts = mutableMapOf<Int, Int>()
        for (c in allChannels) {
            satChannelCounts[c.satIndex] = (satChannelCounts[c.satIndex] ?: 0) + 1
        }

        val allSats = repository.satellites.value
        val satNameMap = allSats.associate { it.satelliteId to it.satelliteName }

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
    private var lastObservedPlayingPid: String? = null
    private var suppressChannelAnnounceUntil = 0L

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.channels.collect { allCh ->
                filterChannels()
                if (allCh.isNotEmpty() && !hasAnnouncedChannelsIntro) {
                    hasAnnouncedChannelsIntro = true
                    val radioCount = allCh.count { it.isRadio }
                    val tvCount = allCh.size - radioCount
                    val playingCh = repository.currentChannel.value

                    val introMsg = "تعداد کل ${allCh.size} کانال با موفقیت دریافت شد: شامل $tvCount شبکه تلویزیونی و $radioCount شبکه رادیویی."
                    AccessibilityHelper.announce(txtChannelCount, introMsg)

                    // Cooldown: prevent external channel monitor from cutting off the announcement
                    suppressChannelAnnounceUntil = System.currentTimeMillis() + 8000L
                    lastObservedPlayingPid = playingCh?.serviceId

                    if (playingCh != null) {
                        // Delay initial jump and focus by 4.5 seconds so TalkBack finishes reading the full intro first
                        recyclerViewChannels.postDelayed({
                            if (isAdded) {
                                focusAndScrollToChannel(playingCh, isInitial = true, announce = true)
                            }
                        }, 4500L)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repository.currentChannel.collect { currCh ->
                if (currCh != null) {
                    if (currCh.serviceId != lastObservedPlayingPid) {
                        val wasFirstTime = (lastObservedPlayingPid == null)
                        lastObservedPlayingPid = currCh.serviceId
                        if (!wasFirstTime && hasAnnouncedChannelsIntro && System.currentTimeMillis() > suppressChannelAnnounceUntil) {
                            focusAndScrollToChannel(currCh, isInitial = false, announce = true)
                        } else {
                            recyclerViewChannels.post {
                                channelsAdapter?.setCurrentPlayingChannel(currCh.serviceId)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun focusAndScrollToChannel(channel: Channel, isInitial: Boolean, announce: Boolean = true) {
        val typePrefix = if (channel.isRadio) "رادیو" else "شبکه"
        val channelNum = if (channel.realChannelNum > 0) "شماره ${channel.realChannelNum}" else ""
        val announceMsg = if (isInitial) {
            "کانال در حال پخش در رسیور: $typePrefix ${channel.serviceName} $channelNum"
        } else {
            "در حال پخش: $typePrefix ${channel.serviceName} $channelNum"
        }

        if (isRadioMode != channel.isRadio) {
            isRadioMode = channel.isRadio
            updateTvRadioToggleUI()
            filterChannels()
        }

        recyclerViewChannels.post {
            channelsAdapter?.setCurrentPlayingChannel(channel.serviceId)
            val currentList = channelsAdapter?.getChannels() ?: emptyList()
            val targetIdx = currentList.indexOfFirst { it.serviceId == channel.serviceId }
            if (targetIdx >= 0) {
                val lm = recyclerViewChannels.layoutManager as? LinearLayoutManager
                val h = recyclerViewChannels.height
                val offset = if (h > 0) (h / 2) - 100 else 150
                lm?.scrollToPositionWithOffset(targetIdx, offset)

                recyclerViewChannels.postDelayed({
                    if (!isAdded) return@postDelayed
                    try {
                        val vh = recyclerViewChannels.findViewHolderForAdapterPosition(targetIdx)
                        vh?.itemView?.requestFocus()
                        vh?.itemView?.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
                    } catch (_: Exception) {}
                }, 250)
            }
            if (announce) {
                AccessibilityHelper.announce(recyclerViewChannels, announceMsg)
                if (appConfig.autoAnnounceEpg) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        delay(1600)
                        val epgData = repository.getEpg(channel.serviceId)
                        val (cur, nxt) = com.gmscreen.accessible.util.EpgHelper.extractCurrentAndNextEvent(epgData)
                        val epgMsg = com.gmscreen.accessible.util.EpgHelper.buildEpgAnnouncement(cur, nxt, appConfig.epgAnnounceDetail)
                        if (!epgMsg.isNullOrBlank()) {
                            AccessibilityHelper.announce(recyclerViewChannels, epgMsg)
                        }
                    }
                }
            }
        }
    }

    private fun filterChannels() {
        val allChannels = repository.channels.value
        val keepGlobal = appConfig.keepGlobalChannelNum

        val filtered = allChannels.filter { ch ->
            val matchesType = if (isRadioMode) ch.isRadio else !ch.isRadio
            val matchesSat = if (selectedSatelliteId == -1) true else ch.satIndex == selectedSatelliteId
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                ch.serviceName.contains(searchQuery, ignoreCase = true) ||
                        ch.channelNumber.toString().contains(searchQuery)
            }
            matchesType && matchesSat && matchesSearch
        }

        val typeName = if (isRadioMode) "رادیویی" else "تلویزیونی"
        val countMsg = "${filtered.size} کانال $typeName"
        txtChannelCount.text = countMsg

        if (channelsAdapter == null) {
            channelsAdapter = ChannelsAdapter(
                filtered,
                keepGlobal,
                onChannelClick = { channel -> tuneChannelOnTv(channel) },
                onPlayClick = { channel ->
                    if (channel.isRadio) playAudioOnPhone(channel) else playVideoOnPhone(channel)
                },
                onChannelLongClick = { channel -> showChannelContextMenu(channel) }
            ).apply {
                setCurrentPlayingChannel(repository.currentChannel.value?.serviceId)
            }
            recyclerViewChannels.adapter = channelsAdapter
        } else {
            channelsAdapter?.updateChannels(filtered)
            channelsAdapter?.setCurrentPlayingChannel(repository.currentChannel.value?.serviceId)
        }
    }

    // Tuning TV on the receiver: DOES NOT stream to phone!
    private fun tuneChannelOnTv(channel: Channel) {
        val ip = repository.connectedIp.value
        if (ip.isBlank()) {
            AccessibilityHelper.announce(txtChannelCount, "رسیور متصل نیست")
            Toast.makeText(requireContext(), "رسیور متصل نیست", Toast.LENGTH_SHORT).show()
            return
        }

        val isAlreadyPlaying = (repository.currentChannel.value?.serviceId == channel.serviceId)

        if (isAlreadyPlaying) {
            val typePrefix = if (channel.isRadio) "رادیو" else "شبکه"
            val msg = "همین $typePrefix (${channel.serviceName}) در حال پخش است"
            AccessibilityHelper.announce(txtChannelCount, msg)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            return
        }

        val prefix = if (channel.isRadio) "رادیو" else "شبکه"
        AccessibilityHelper.announce(txtChannelCount, "$prefix ${channel.serviceName}")

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            if (isAudioStreaming || com.gmscreen.accessible.player.StreamRecorderManager.isStreaming) {
                withContext(Dispatchers.Main) {
                    stopAudioPlayback()
                }
                delay(250)
            }

            val switched = repository.switchChannel(channel)
            withContext(Dispatchers.Main) {
                if (switched) {
                    if (appConfig.hapticFeedbackEnabled) {
                        AccessibilityHelper.vibrateSuccess(requireContext())
                    }
                    channelsAdapter?.setCurrentPlayingChannel(channel.serviceId)
                } else {
                    AccessibilityHelper.announce(txtChannelCount, "خطا در تعویض کانال روی رسیور")
                    Toast.makeText(requireContext(), "خطا در تعویض کانال روی رسیور", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun playVideoOnPhone(channel: Channel) {
        val ip = repository.connectedIp.value
        if (ip.isBlank()) {
            AccessibilityHelper.announce(txtChannelCount, "رسیور متصل نیست")
            Toast.makeText(requireContext(), "رسیور متصل نیست", Toast.LENGTH_SHORT).show()
            return
        }

        stopAudioPlayback()

        val intent = Intent(requireContext(), com.gmscreen.accessible.ui.VideoPlayerActivity::class.java).apply {
            putExtra("EXTRA_CHANNEL_NAME", channel.serviceName)
            putExtra("EXTRA_SERVICE_ID", channel.serviceId)
        }
        startActivity(intent)
    }

    private fun playAudioOnPhone(channel: Channel) {
        val ip = repository.connectedIp.value
        if (ip.isBlank()) {
            AccessibilityHelper.announce(txtChannelCount, "رسیور متصل نیست")
            Toast.makeText(requireContext(), "رسیور متصل نیست", Toast.LENGTH_SHORT).show()
            return
        }

        val isAlreadyStreamingSameChannel = com.gmscreen.accessible.player.StreamRecorderManager.isStreaming &&
                com.gmscreen.accessible.player.StreamRecorderManager.currentChannel?.serviceId == channel.serviceId

        if (!isAlreadyStreamingSameChannel) {
            stopAudioPlayback()
        }

        currentAudioChannel = channel
        layoutAudioPlayingBar.visibility = View.VISIBLE
        txtAudioPlayingChannel.text = "در حال اتصال به پخش صوتی ${channel.serviceName}..."
        AccessibilityHelper.announce(layoutAudioPlayingBar, "در حال اتصال به پخش صوتی ${channel.serviceName}...")
        updateAudioBarRecordBtn(com.gmscreen.accessible.player.StreamRecorderManager.isRecording)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            if (!isAlreadyStreamingSameChannel) {
                com.gmscreen.accessible.player.StreamRecorderManager.stopStream(keepRecording = false)
                delay(250)

                repository.switchChannel(channel)
                delay(300)
            }

            val httpUrl = com.gmscreen.accessible.player.StreamRecorderManager.startStream(
                requireContext(),
                repository.client,
                ip,
                channel
            )

            withContext(Dispatchers.Main) {
                if (httpUrl != null) {
                    com.gmscreen.accessible.player.AudioStreamPlayer.play(requireContext(), httpUrl, channel)
                    isAudioStreaming = true
                    updateAudioBarRecordBtn(com.gmscreen.accessible.player.StreamRecorderManager.isRecording)
                    txtAudioPlayingChannel.text = "در حال پخش صوتی: ${channel.serviceName}"
                    val msg = "پخش صوتی شبکه ${channel.serviceName} با کیفیت عالی آغاز شد. دکمه توقف پخش در پایین صفحه فعال است."
                    AccessibilityHelper.announce(layoutAudioPlayingBar, msg)
                } else {
                    layoutAudioPlayingBar.visibility = View.GONE
                    val msg = "خطا در اتصال به استریم صوتی رسیور"
                    AccessibilityHelper.announce(txtChannelCount, msg)
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun stopAudioPlayback() {
        com.gmscreen.accessible.player.AudioStreamPlayer.stop()
        isAudioStreaming = false
        currentAudioChannel = null
        layoutAudioPlayingBar.visibility = View.GONE
    }

    private fun openStreamInExternalPlayer(channel: Channel) {
        val ip = repository.connectedIp.value
        if (ip.isBlank()) {
            Toast.makeText(requireContext(), "رسیور متصل نیست", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val httpUrl = com.gmscreen.accessible.player.StreamRecorderManager.startStream(
                    requireContext(),
                    repository.client,
                    ip,
                    channel
                )
                if (httpUrl.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "خطا در برقراری اتصال استریم زنده", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(httpUrl), "video/*")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(Intent.createChooser(intent, "پخش استریم در پلیر خارجی (VLC / MX Player)"))
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "پلیر ویدیویی خارجی در گوشی یافت نشد", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "خطا در آماده‌سازی استریم: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::fabRecordings.isInitialized) {
            fabRecordings.visibility = if (appConfig.showRecordingsInChannels) View.VISIBLE else View.GONE
        }
        updateJumpButtonsUI()
    }

    private fun updateJumpButtonsUI() {
        if (!::layoutJumpButtons.isInitialized) return
        val showFirst = appConfig.showJumpToFirstChannel
        val showLast = appConfig.showJumpToLastChannel
        btnJumpToFirstChannel.visibility = if (showFirst) View.VISIBLE else View.GONE
        btnJumpToLastChannel.visibility = if (showLast) View.VISIBLE else View.GONE
        layoutJumpButtons.visibility = if (showFirst || showLast) View.VISIBLE else View.GONE
    }

    private fun showChannelContextMenu(channel: Channel) {
        val isLocked = channel.isScrambled
        val lockTitle = if (isLocked) "بازگشایی موقت کانال کارتی (با پین رسیور)" else "قفل کانال (Parental Lock)"

        val options = arrayOf(
            "۱. پخش تصویری زنده در گوشی",
            "۲. پخش صوتی زنده در گوشی (Audio Only)",
            "۳. حذف این کانال از حافظه رسیور (Delete Channel)",
            "۴. حرکت دادن کانال به شماره دلخواه (Move Channel)",
            "۵. مشخصات و اطلاعات فنی کانال",
            "۶. جدول پخش برنامه‌های این شبکه (لیست EPG)",
            "۷. اعلام صوتی برنامه در حال پخش فعلی (EPG)",
            "۸. شروع ضبط زنده روی گوشی (MP4/MP3)",
            "۹. افزودن به زمان‌بندی ضبط روی فلش رسیور",
            "۱۰. افزودن به زمان‌بندی ضبط روی حافظه گوشی",
            "۱۱. پخش در برنامه خارجی (مانند VLC یا MX Player)",
            "۱۲. کپی آدرس استریم زنده (RTSP URL)",
            "۱۳. $lockTitle"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.channel_menu_title, channel.serviceName))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playVideoOnPhone(channel)
                    1 -> playAudioOnPhone(channel)
                    2 -> promptDeleteChannel(channel)
                    3 -> promptMoveChannel(channel)
                    4 -> showChannelInfoDialog(channel)
                    5 -> showChannelEpgDialog(channel)
                    6 -> announceCurrentEpg(channel)
                    7 -> startPhoneRecordingFromChannel(channel)
                    8 -> {
                        viewLifecycleOwner.lifecycleScope.launch {
                            repository.switchChannel(channel)
                        }
                        (activity as? com.gmscreen.accessible.ui.MainActivity)?.switchToTab(3)
                        Toast.makeText(requireContext(), "شبکه ${channel.serviceName} انتخاب شد؛ در سربرگ ضبط دکمه افزودن زمان‌بندی را بزنید", Toast.LENGTH_LONG).show()
                    }
                    9 -> {
                        viewLifecycleOwner.lifecycleScope.launch {
                            repository.switchChannel(channel)
                        }
                        (activity as? com.gmscreen.accessible.ui.MainActivity)?.switchToTab(3)
                        Toast.makeText(requireContext(), "شبکه ${channel.serviceName} انتخاب شد؛ در سربرگ ضبط دکمه افزودن زمان‌بندی گوشی را بزنید", Toast.LENGTH_LONG).show()
                    }
                    10 -> openStreamInExternalPlayer(channel)
                    11 -> {
                        val ip = repository.connectedIp.value
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            val q = if (repository.isConnected.value) repository.client.buildSatIpQuery(channel) else null
                            val url = channel.toSatIpUrl(ip, q)
                            withContext(Dispatchers.Main) {
                                val clip = ClipData.newPlainText("Stream URL", url)
                                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(clip)
                                val msg = "آدرس استریم زنده کپی شد: $url"
                                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                                AccessibilityHelper.announce(txtChannelCount, msg)
                            }
                        }
                    }
                    12 -> {
                        Toast.makeText(requireContext(), "دستور قفل/بازگشایی با پین 0000 ارسال شد", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun promptDeleteChannel(channel: Channel) {
        val ip = repository.connectedIp.value
        if (ip.isBlank() || !repository.isConnected.value) {
            Toast.makeText(requireContext(), "رسیور متصل نیست", Toast.LENGTH_SHORT).show()
            return
        }

        val chName = channel.serviceName
        val typeStr = if (channel.isRadio) "رادیویی" else "تلویزیونی"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("حذف کانال $chName")
            .setMessage("آیا از حذف کامل کانال $typeStr «$chName» از حافظه رسیور اطمینان دارید؟\nاین عملیات این کانال را به‌طور کامل از لیست رسیور پاک می‌کند.")
            .setPositiveButton("بله، حذف کن") { _, _ ->
                if (currentAudioChannel?.serviceId == channel.serviceId) {
                    stopAudioPlayback()
                }

                val startMsg = "در حال حذف کانال $chName از رسیور..."
                AccessibilityHelper.announce(txtChannelCount, startMsg)
                Toast.makeText(requireContext(), startMsg, Toast.LENGTH_SHORT).show()

                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val ok = repository.deleteChannel(channel)
                    withContext(Dispatchers.Main) {
                        if (ok) {
                            filterChannels()
                            val successMsg = "کانال $chName با موفقیت از رسیور حذف شد"
                            AccessibilityHelper.announce(recyclerViewChannels, successMsg)
                            AccessibilityHelper.vibrateSuccess(requireContext())
                            Toast.makeText(requireContext(), successMsg, Toast.LENGTH_LONG).show()
                        } else {
                            val errMsg = "خطا در ارسال دستور حذف کانال به رسیور"
                            AccessibilityHelper.announce(txtChannelCount, errMsg)
                            AccessibilityHelper.vibrateWarning(requireContext())
                            Toast.makeText(requireContext(), errMsg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun promptMoveChannel(channel: Channel) {
        val allChannels = repository.channels.value
        val sameTypeChannels = allChannels.filter { it.isRadio == channel.isRadio }
        val total = sameTypeChannels.size
        if (total < 2) {
            val msg = "شبکه‌های کافی برای جابه‌جایی وجود ندارد"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            AccessibilityHelper.announce(txtChannelCount, msg)
            return
        }

        val currentNum = channel.realChannelNum.takeIf { it > 0 } ?: (sameTypeChannels.indexOfFirst { it.serviceId == channel.serviceId } + 1)

        val input = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "شماره بین ۱ تا $total"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("جابه‌جایی کانال ${channel.serviceName}")
            .setMessage("شماره موقعیت فعلی: $currentNum\nلطفاً شماره موقعیت مقصد مورد نظر را وارد کنید (۱ تا $total):")
            .setView(input)
            .setPositiveButton("انتقال") { _, _ ->
                val targetNum = input.text.toString().trim().toIntOrNull()
                if (targetNum == null || targetNum < 1 || targetNum > total) {
                    val msg = "شماره نامعتبر است. باید عددی بین ۱ تا $total باشد."
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    AccessibilityHelper.announce(txtChannelCount, msg)
                    return@setPositiveButton
                }

                if (targetNum == currentNum) {
                    val msg = "شماره مبدا و مقصد یکسان است"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    AccessibilityHelper.announce(txtChannelCount, msg)
                    return@setPositiveButton
                }

                val targetChannel = sameTypeChannels.getOrNull(targetNum - 1)
                if (targetChannel == null) {
                    val msg = "کانال مقصد پیدا نشد"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    AccessibilityHelper.announce(txtChannelCount, msg)
                    return@setPositiveButton
                }

                val progMsg = "در حال انتقال کانال ${channel.serviceName} به شماره $targetNum..."
                AccessibilityHelper.announce(txtChannelCount, progMsg)
                Toast.makeText(requireContext(), progMsg, Toast.LENGTH_SHORT).show()

                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val ok = repository.moveChannel(channel, targetChannel)
                    withContext(Dispatchers.Main) {
                        if (ok) {
                            filterChannels()
                            val successMsg = "کانال ${channel.serviceName} با موفقیت به شماره $targetNum منتقل شد"
                            AccessibilityHelper.announce(recyclerViewChannels, successMsg)
                            Toast.makeText(requireContext(), successMsg, Toast.LENGTH_LONG).show()
                            focusAndScrollToChannel(channel, isInitial = false, announce = false)
                        } else {
                            val errMsg = "خطا در ارسال دستور جابه‌جایی کانال به رسیور"
                            AccessibilityHelper.announce(txtChannelCount, errMsg)
                            Toast.makeText(requireContext(), errMsg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showChannelEpgDialog(channel: Channel) {
        val progName = channel.serviceName
        Toast.makeText(requireContext(), "در حال دریافت جدول برنامه‌های $progName...", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val epgData = repository.getEpg(channel.serviceId)
            val programs = com.gmscreen.accessible.util.EpgHelper.extractAllPrograms(epgData)
            withContext(Dispatchers.Main) {
                if (programs.isEmpty()) {
                    val msg = "جدول برنامه‌ای برای $progName در رسیور یافت نشد"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                } else {
                    val items = programs.map { p ->
                        val timeStr = if (p.startRaw.isNotBlank() && p.endRaw.isNotBlank()) {
                            "${com.gmscreen.accessible.util.EpgHelper.formatFriendlyTime(p.startRaw)} تا ${com.gmscreen.accessible.util.EpgHelper.formatFriendlyTime(p.endRaw)}"
                        } else if (p.startRaw.isNotBlank()) {
                            com.gmscreen.accessible.util.EpgHelper.formatFriendlyTime(p.startRaw)
                        } else {
                            ""
                        }
                        val descStr = if (p.desc.isNotBlank()) " - ${p.desc}" else ""
                        "${p.title} ($timeStr)$descStr"
                    }.toTypedArray()

                    val headerView = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(32, 24, 32, 12)
                    }

                    val txtHeaderTitle = TextView(requireContext()).apply {
                        text = "جدول برنامه‌های $progName (${programs.size} برنامه)"
                        setTextColor(resources.getColor(R.color.text_primary, null))
                        textSize = 17f
                        paint.isFakeBoldText = true
                        setPadding(0, 0, 0, 12)
                    }

                    val btnCopyAll = com.google.android.material.button.MaterialButton(requireContext()).apply {
                        text = "📋 کپی کامل جدول برنامه‌ها"
                        contentDescription = "کپی کردن کل جدول پخش برنامه‌های $progName در حافظه کلیپ‌بورد"
                        setBackgroundColor(resources.getColor(R.color.primary, null))
                        setTextColor(resources.getColor(R.color.white, null))
                        setOnClickListener {
                            val sb = java.lang.StringBuilder()
                            sb.append("=== جدول پخش برنامه‌های $progName ===\n\n")
                            for (p in programs) {
                                val sFmt = com.gmscreen.accessible.util.EpgHelper.formatFriendlyTime(p.startRaw)
                                val eFmt = com.gmscreen.accessible.util.EpgHelper.formatFriendlyTime(p.endRaw)
                                val dText = p.desc.ifBlank { "" }
                                sb.append("• ${p.title} ($sFmt تا $eFmt)\n")
                                if (dText.isNotBlank()) sb.append("  توضیحات: $dText\n")
                            }
                            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("EPG $progName", sb.toString())
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(requireContext(), "جدول کامل برنامه‌های $progName در کلیپ‌بورد کپی شد", Toast.LENGTH_SHORT).show()
                        }
                    }
                    headerView.addView(txtHeaderTitle)
                    headerView.addView(btnCopyAll)

                    MaterialAlertDialogBuilder(requireContext())
                        .setCustomTitle(headerView)
                        .setItems(items) { _, which ->
                            val p = programs.getOrNull(which)
                            if (p != null) {
                                val sFmt = com.gmscreen.accessible.util.EpgHelper.formatFriendlyTime(p.startRaw)
                                val eFmt = com.gmscreen.accessible.util.EpgHelper.formatFriendlyTime(p.endRaw)
                                val dText = p.desc.ifBlank { "توضیحی ثبت نشده است" }
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(p.title)
                                    .setMessage("زمان پخش: $sFmt تا $eFmt\n\nتوضیحات:\n$dText")
                                    .setPositiveButton("بازگشت به جدول") { _, _ ->
                                        showChannelEpgDialog(channel)
                                    }
                                    .setOnCancelListener {
                                        showChannelEpgDialog(channel)
                                    }
                                    .show()
                            }
                        }
                        .setPositiveButton("بستن", null)
                        .show()
                }
            }
        }
    }

    private fun announceCurrentEpg(channel: Channel) {
        val progName = channel.serviceName
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val epgData = repository.getEpg(channel.serviceId)
            val (cur, nxt) = com.gmscreen.accessible.util.EpgHelper.extractCurrentAndNextEvent(epgData)
            val msg = com.gmscreen.accessible.util.EpgHelper.buildEpgAnnouncement(cur, nxt, appConfig.epgAnnounceDetail)
            withContext(Dispatchers.Main) {
                if (msg != null) {
                    val fullMsg = "$progName: $msg"
                    Toast.makeText(requireContext(), fullMsg, Toast.LENGTH_LONG).show()
                } else {
                    val fallback = "اطلاعات برنامه در حال پخش برای $progName در جدول رسیور یافت نشد"
                    Toast.makeText(requireContext(), fallback, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showChannelInfoDialog(channel: Channel) {
        val infoText = """
            نام کانال: ${channel.serviceName}
            شماره کانال در رسیور: ${channel.channelNumber}
            ماهواره: ${channel.satelliteName}
            فرکانس: ${channel.frequency} MHz
            نوع سرویس: ${if (channel.isRadio) "رادیو" else "تلویزیون"}
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

    private fun startPhoneRecordingFromChannel(channel: Channel) {
        val ip = repository.connectedIp.value
        if (ip.isBlank()) {
            Toast.makeText(requireContext(), "رسیور متصل نیست", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val file = com.gmscreen.accessible.player.StreamRecorderManager.startRecording(
                requireContext(),
                repository.client,
                ip,
                channel,
                isAudioOnly = channel.isRadio
            )
            withContext(Dispatchers.Main) {
                if (file != null) {
                    val msg = "ضبط زنده شبکه ${channel.serviceName} در گوشی آغاز شد: ${file.name}"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                } else {
                    val msg = "خطا در شروع ضبط زنده روی گوشی"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        announceJob?.cancel()
        com.gmscreen.accessible.player.AudioStreamPlayer.removeListener(audioPlayerListener)
    }
}
