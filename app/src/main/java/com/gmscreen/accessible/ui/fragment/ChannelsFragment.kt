package com.gmscreen.accessible.ui.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.data.model.Channel
import com.gmscreen.accessible.data.model.Satellite
import com.gmscreen.accessible.player.SatIpStreamPlayer
import com.gmscreen.accessible.ui.adapter.ChannelsAdapter
import com.gmscreen.accessible.util.AccessibilityHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelsFragment : Fragment(), SatIpStreamPlayer.PlayerEventListener {

    private lateinit var containerPlayer: LinearLayout
    private lateinit var txtPlayerTitle: TextView
    private lateinit var btnStopPlayer: MaterialButton
    private lateinit var btnTvRadioToggle: MaterialButton
    private lateinit var spinnerSatellite: Spinner
    private lateinit var edtSearch: TextInputEditText
    private lateinit var txtChannelCount: TextView
    private lateinit var recyclerViewChannels: RecyclerView

    private var streamPlayer: SatIpStreamPlayer? = null
    private var channelsAdapter: ChannelsAdapter? = null

    private var isRadioMode = false
    private var selectedSatelliteId: Int = -1 // -1 means all satellites
    private var searchQuery: String = ""

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
        spinnerSatellite = view.findViewById(R.id.spinnerSatellite)
        edtSearch = view.findViewById(R.id.edtSearch)
        txtChannelCount = view.findViewById(R.id.txtChannelCount)
        recyclerViewChannels = view.findViewById(R.id.recyclerViewChannels)

        recyclerViewChannels.layoutManager = LinearLayoutManager(requireContext())

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
                AccessibilityHelper.announce(btnTvRadioToggle, "حالت به شبکه‌های رادیویی تغییر کرد")
            } else {
                btnTvRadioToggle.text = getString(R.string.mode_tv)
                btnTvRadioToggle.setBackgroundColor(resources.getColor(R.color.primary, null))
                AccessibilityHelper.announce(btnTvRadioToggle, "حالت به شبکه‌های تلویزیونی تغییر کرد")
            }
            refreshChannelsList()
        }

        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                filterChannels()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.satellites.collect { sats ->
                setupSatelliteSpinner(sats)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repository.channels.collect {
                filterChannels()
            }
        }
    }

    private fun setupSatelliteSpinner(satellites: List<Satellite>) {
        val satNames = mutableListOf("همه ماهواره‌ها")
        satNames.addAll(satellites.map { it.satelliteName })

        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, satNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerSatellite.adapter = spinnerAdapter

        spinnerSatellite.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSatelliteId = if (position == 0) {
                    -1
                } else {
                    satellites.getOrNull(position - 1)?.satelliteId ?: -1
                }
                filterChannels()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun refreshChannelsList() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            repository.client.fetchChannels()
            withContext(Dispatchers.Main) {
                filterChannels()
            }
        }
    }

    private fun filterChannels() {
        val allChannels = repository.channels.value
        val keepGlobal = appConfig.keepGlobalChannelNum

        val filtered = allChannels.filter { ch ->
            // Filter by Service Type (TV vs Radio)
            val matchesType = if (isRadioMode) ch.serviceType == 2 else (ch.serviceType == 1 || ch.serviceType == 0)

            // Filter by Satellite
            val matchesSat = if (selectedSatelliteId == -1) true else ch.satelliteId == selectedSatelliteId

            // Filter by Search Query
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
            channelsAdapter = ChannelsAdapter(filtered, keepGlobal) { selectedChannel ->
                onChannelSelected(selectedChannel)
            }
            recyclerViewChannels.adapter = channelsAdapter
        } else {
            channelsAdapter = ChannelsAdapter(filtered, keepGlobal) { selectedChannel ->
                onChannelSelected(selectedChannel)
            }
            recyclerViewChannels.adapter = channelsAdapter
        }
    }

    private fun onChannelSelected(channel: Channel) {
        val ip = repository.connectedIp.value
        if (ip.isBlank()) {
            AccessibilityHelper.announce(txtChannelCount, "ابتدا به رسیور متصل شوید")
            return
        }

        val msg = "در حال انتقال به کانال ${channel.serviceName}..."
        AccessibilityHelper.announce(txtChannelCount, msg)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val switched = repository.switchChannel(channel)
            withContext(Dispatchers.Main) {
                if (switched) {
                    AccessibilityHelper.announce(txtChannelCount, "کانال ${channel.serviceName} پخش شد")
                    if (appConfig.hapticFeedbackEnabled) {
                        AccessibilityHelper.vibrateSuccess(requireContext())
                    }
                    // Start live stream
                    containerPlayer.visibility = View.VISIBLE
                    txtPlayerTitle.text = "پخش زنده: ${channel.serviceName}"
                    streamPlayer?.playChannel(ip, channel)
                }
            }
        }
    }

    override fun onPlaybackStarted() {
        AccessibilityHelper.announce(txtPlayerTitle, "پخش زنده صدا و تصویر آغاز شد")
    }

    override fun onPlaybackBuffering() {
        AccessibilityHelper.announce(txtPlayerTitle, "در حال بارگذاری استریم شبکه...")
    }

    override fun onPlaybackEnded() {
        containerPlayer.visibility = View.GONE
    }

    override fun onPlaybackError(errorMsg: String) {
        AccessibilityHelper.announce(txtPlayerTitle, errorMsg)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        streamPlayer?.release()
        streamPlayer = null
    }
}
