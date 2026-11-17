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
    private lateinit var btnTvRadioToggle: MaterialButton
    private lateinit var btnSatelliteFilter: MaterialButton
    private lateinit var edtSearch: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var txtChannelCount: TextView
    private lateinit var recyclerViewChannels: RecyclerView
    private lateinit var fabRefreshChannels: ExtendedFloatingActionButton
    private lateinit var fabRecordings: ExtendedFloatingActionButton

    private var audioExoPlayer: androidx.media3.exoplayer.ExoPlayer? = null
    private var isAudioStreaming = false
    private var currentAudioChannel: Channel? = null
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
        btnTvRadioToggle = view.findViewById(R.id.btnTvRadioToggle)
        btnSatelliteFilter = view.findViewById(R.id.btnSatelliteFilter)
        edtSearch = view.findViewById(R.id.edtSearch)
        btnClearSearch = view.findViewById(R.id.btnClearSearch)
        txtChannelCount = view.findViewById(R.id.txtChannelCount)
        recyclerViewChannels = view.findViewById(R.id.recyclerViewChannels)
        fabRefreshChannels = view.findViewById(R.id.fabRefreshChannels)
        fabRecordings = view.findViewById(R.id.fabRecordings)

        recyclerViewChannels.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewChannels.setHasFixedSize(true)
        recyclerViewChannels.setItemViewCacheSize(20)

        setupAudioPlayerBar()
        setupControls()
        observeData()

        return view
    }

    private fun setupAudioPlayerBar() {
        btnStopAudioPlayback.setOnClickListener {
            stopAudioPlayback()
            val msg = "پخش صوتی شبکه متوقف شد"
            AccessibilityHelper.announce(txtChannelCount, msg)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
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
            AccessibilityHelper.announce(edtSearch, "جستجو پاک شد")
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
            AccessibilityHelper.announce(btnSatelliteFilter, "هنوز شبکه‌ای از رسیور دریافت نشده است")
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

                    val sb = java.lang.StringBuilder()
                    sb.append("تعداد کل ${allCh.size} کانال با موفقیت دریافت شد: شامل $tvCount شبکه تلویزیونی و $radioCount شبکه رادیویی.")
                    if (playingCh != null) {
                        val typePrefix = if (playingCh.isRadio) "رادیو" else "شبکه"
                        val chNum = if (playingCh.realChannelNum > 0) "شماره ${playingCh.realChannelNum}" else ""
                        sb.append(" در حال پخش در تلویزیون: $typePrefix ${playingCh.serviceName} $chNum.")
                    }
                    AccessibilityHelper.announce(txtChannelCount, sb.toString())

                    // Cooldown: prevent external channel monitor from cutting off the announcement
                    suppressChannelAnnounceUntil = System.currentTimeMillis() + 8000L
                    lastObservedPlayingPid = playingCh?.serviceId

                    if (playingCh != null) {
                        focusAndScrollToChannel(playingCh, isInitial = true, announce = false)
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
                recyclerViewChannels.scrollToPosition(targetIdx)
                if (!isInitial) {
                    recyclerViewChannels.postDelayed({
                        try {
                            val vh = recyclerViewChannels.findViewHolderForAdapterPosition(targetIdx)
                            vh?.itemView?.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
                        } catch (_: Exception) {}
                    }, 200)
                }
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

        stopAudioPlayback()

        currentAudioChannel = channel
        layoutAudioPlayingBar.visibility = View.VISIBLE
        txtAudioPlayingChannel.text = "در حال اتصال به پخش صوتی ${channel.serviceName}..."
        AccessibilityHelper.announce(layoutAudioPlayingBar, "در حال اتصال به پخش صوتی ${channel.serviceName}...")

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            com.gmscreen.accessible.player.StreamRecorderManager.stopStream(keepRecording = false)
            delay(250)

            repository.switchChannel(channel)
            delay(300)

            val httpUrl = com.gmscreen.accessible.player.StreamRecorderManager.startStream(
                requireContext(),
                repository.client,
                ip,
                channel
            )

            withContext(Dispatchers.Main) {
                if (httpUrl != null) {
                    initAudioPlayer()
                    val mediaItem = androidx.media3.common.MediaItem.fromUri(Uri.parse(httpUrl))
                    audioExoPlayer?.apply {
                        setMediaItem(mediaItem)
                        prepare()
                        play()
                    }
                    isAudioStreaming = true
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

    private fun initAudioPlayer() {
        if (audioExoPlayer == null) {
            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            audioExoPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(requireContext())
                .setAudioAttributes(audioAttributes, true)
                .build().apply {
                    playWhenReady = true
                }
        }
    }

    private fun stopAudioPlayback() {
        audioExoPlayer?.stop()
        audioExoPlayer?.clearMediaItems()
        audioExoPlayer?.release()
        audioExoPlayer = null
        isAudioStreaming = false
        currentAudioChannel = null
        layoutAudioPlayingBar.visibility = View.GONE
        com.gmscreen.accessible.player.StreamRecorderManager.stopStream(keepRecording = false)
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
    }

    private fun showChannelContextMenu(channel: Channel) {
        val isLocked = channel.isScrambled
        val lockTitle = if (isLocked) "بازگشایی موقت کانال کارتی (با پین رسیور)" else "قفل کانال (Parental Lock)"

        val options = arrayOf(
            "۱. پخش تصویری زنده در گوشی",
            "۲. پخش صوتی زنده در گوشی (Audio Only)",
            "۳. جدول پخش برنامه‌های این شبکه (EPG)",
            "۴. اعلام صوتی برنامه در حال پخش (EPG)",
            "۵. پخش در برنامه خارجی (مانند VLC یا MX Player)",
            "۶. شروع ضبط زنده روی گوشی (MP4/MP3)",
            "۷. $lockTitle",
            "۸. مشخصات و اطلاعات فنی کانال",
            "۹. کپی آدرس استریم زنده (RTSP URL)",
            "۱۰. افزودن به زمان‌بندی ضبط روی فلش رسیور",
            "۱۱. افزودن به زمان‌بندی ضبط روی حافظه گوشی",
            "۱۲. حرکت دادن کانال به شماره دلخواه (Move Channel)"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.channel_menu_title, channel.serviceName))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playVideoOnPhone(channel)
                    1 -> playAudioOnPhone(channel)
                    2 -> showChannelEpgDialog(channel)
                    3 -> announceCurrentEpg(channel)
                    4 -> openStreamInExternalPlayer(channel)
                    5 -> startPhoneRecordingFromChannel(channel)
                    6 -> {
                        Toast.makeText(requireContext(), "دستور قفل/بازگشایی با پین 0000 ارسال شد", Toast.LENGTH_SHORT).show()
                    }
                    7 -> showChannelInfoDialog(channel)
                    8 -> {
                        val ip = repository.connectedIp.value
                        val url = channel.toSatIpUrl(ip)
                        val clip = ClipData.newPlainText("Stream URL", url)
                        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(clip)
                        val msg = "آدرس استریم زنده کپی شد: $url"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        AccessibilityHelper.announce(txtChannelCount, msg)
                    }
                    9 -> {
                        viewLifecycleOwner.lifecycleScope.launch {
                            repository.switchChannel(channel)
                        }
                        (activity as? com.gmscreen.accessible.ui.MainActivity)?.switchToTab(3)
                        Toast.makeText(requireContext(), "شبکه ${channel.serviceName} انتخاب شد؛ در سربرگ ضبط دکمه افزودن زمان‌بندی را بزنید", Toast.LENGTH_LONG).show()
                    }
                    10 -> {
                        viewLifecycleOwner.lifecycleScope.launch {
                            repository.switchChannel(channel)
                        }
                        (activity as? com.gmscreen.accessible.ui.MainActivity)?.switchToTab(3)
                        Toast.makeText(requireContext(), "شبکه ${channel.serviceName} انتخاب شد؛ در سربرگ ضبط دکمه افزودن زمان‌بندی گوشی را بزنید", Toast.LENGTH_LONG).show()
                    }
                    11 -> promptMoveChannel(channel)
                }
            }
            .setNegativeButton("بستن", null)
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
        AccessibilityHelper.announce(txtChannelCount, "در حال دریافت جدول برنامه‌های $progName از رسیور")

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val epgData = repository.getEpg(channel.serviceId)
            val programs = com.gmscreen.accessible.util.EpgHelper.extractAllPrograms(epgData)
            withContext(Dispatchers.Main) {
                if (programs.isEmpty()) {
                    val msg = "جدول برنامه‌ای برای $progName در رسیور یافت نشد"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    AccessibilityHelper.announce(txtChannelCount, msg)
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

                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("جدول برنامه‌های $progName (${programs.size} برنامه)")
                        .setItems(items) { _, which ->
                            val p = programs.getOrNull(which)
                            if (p != null) {
                                val sFmt = com.gmscreen.accessible.util.EpgHelper.formatFriendlyTime(p.startRaw)
                                val eFmt = com.gmscreen.accessible.util.EpgHelper.formatFriendlyTime(p.endRaw)
                                val dText = p.desc.ifBlank { "توضیحی ثبت نشده است" }
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(p.title)
                                    .setMessage("زمان پخش: $sFmt تا $eFmt\n\nتوضیحات:\n$dText")
                                    .setPositiveButton("بستن", null)
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
                    AccessibilityHelper.announce(txtChannelCount, fullMsg)
                } else {
                    val fallback = "اطلاعات برنامه در حال پخش برای $progName در جدول رسیور یافت نشد"
                    Toast.makeText(requireContext(), fallback, Toast.LENGTH_SHORT).show()
                    AccessibilityHelper.announce(txtChannelCount, fallback)
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
                    AccessibilityHelper.announce(txtChannelCount, msg)
                } else {
                    val msg = "خطا در شروع ضبط زنده روی گوشی"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    AccessibilityHelper.announce(txtChannelCount, msg)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        announceJob?.cancel()
        stopAudioPlayback()
    }
}
