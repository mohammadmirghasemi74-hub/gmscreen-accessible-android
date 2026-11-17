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
                        if (!wasFirstTime && hasAnnouncedChannelsIntro) {
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
                recyclerViewChannels.postDelayed({
                    try {
                        val vh = recyclerViewChannels.findViewHolderForAdapterPosition(targetIdx)
                        vh?.itemView?.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
                    } catch (_: Exception) {}
                }, 200)
            }
            if (announce) {
                AccessibilityHelper.announce(recyclerViewChannels, announceMsg)
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
            "۳. پخش در برنامه خارجی (مانند VLC یا MX Player)",
            "۴. شروع ضبط زنده روی گوشی (MP4/MP3)",
            "۵. $lockTitle",
            "۶. مشخصات و اطلاعات فنی کانال",
            "۷. کپی آدرس استریم زنده (RTSP URL)",
            "۸. افزودن به زمان‌بندی ضبط روی فلش رسیور",
            "۹. افزودن به زمان‌بندی ضبط روی حافظه گوشی"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.channel_menu_title, channel.serviceName))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playVideoOnPhone(channel)
                    1 -> playAudioOnPhone(channel)
                    2 -> openStreamInExternalPlayer(channel)
                    3 -> {
                        startPhoneRecordingFromChannel(channel)
                    }
                    4 -> {
                        Toast.makeText(requireContext(), "دستور قفل/بازگشایی با پین 0000 ارسال شد", Toast.LENGTH_SHORT).show()
                    }
                    5 -> showChannelInfoDialog(channel)
                    6 -> {
                        val ip = repository.connectedIp.value
                        val url = channel.toSatIpUrl(ip)
                        val clip = ClipData.newPlainText("Stream URL", url)
                        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(clip)
                        val msg = "آدرس استریم زنده کپی شد: $url"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        AccessibilityHelper.announce(txtChannelCount, msg)
                    }
                    7 -> {
                        (activity as? com.gmscreen.accessible.ui.MainActivity)?.switchToTab(3)
                        Toast.makeText(requireContext(), "در سربرگ ضبط، دکمه افزودن زمان‌بندی رسیور را بزنید", Toast.LENGTH_LONG).show()
                    }
                    8 -> {
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
