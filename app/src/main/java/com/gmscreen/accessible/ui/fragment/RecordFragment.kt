package com.gmscreen.accessible.ui.fragment

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.data.model.Channel
import com.gmscreen.accessible.protocol.RcuKey
import com.gmscreen.accessible.util.AccessibilityHelper
import com.gmscreen.accessible.util.StorageManager
import com.gmscreen.accessible.util.TtsHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RecordFragment : Fragment() {

    private lateinit var txtInstantRecStatus: TextView
    private lateinit var btnStartPhoneVideoRec: MaterialButton
    private lateinit var btnStartPhoneAudioRec: MaterialButton
    private lateinit var btnStopPhoneRec: MaterialButton
    private lateinit var btnStbToggleRecord: MaterialButton
    private lateinit var btnViewRecordings: MaterialButton
    private lateinit var txtStbRecordStatus: TextView
    private var isStbRecording = false

    private lateinit var btnAddStbTimer: MaterialButton
    private lateinit var btnStopStbScheduledRec: MaterialButton
    private lateinit var containerStbTimers: LinearLayout
    private lateinit var txtNoStbTimers: TextView

    private lateinit var btnAddPhoneTimer: MaterialButton
    private lateinit var btnStopPhoneScheduledRec: MaterialButton
    private lateinit var containerPhoneTimers: LinearLayout
    private lateinit var txtNoPhoneTimers: TextView

    private lateinit var btnRefreshStbPvrFiles: MaterialButton
    private lateinit var containerStbPvrFiles: LinearLayout
    private lateinit var txtNoStbPvrFiles: TextView

    private var isRecordingOnPhone = false
    private var lastRecordingWasAudio = false
    private var currentRecordingFile: File? = null
    private var recSeconds = 0
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRecordingOnPhone) {
                recSeconds++
                val mins = recSeconds / 60
                val secs = recSeconds % 60
                val mb = String.format(Locale.US, "%.1f", com.gmscreen.accessible.player.StreamRecorderManager.recordedBytes / (1024f * 1024f))
                txtInstantRecStatus.text = "در حال ضبط روی گوشی… %02d:%02d ($mb MB)".format(mins, secs)
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    private var stbRecSeconds = 0
    private val stbTimerHandler = Handler(Looper.getMainLooper())
    private val stbTimerRunnable = object : Runnable {
        override fun run() {
            if (isStbRecording) {
                stbRecSeconds++
                val hours = stbRecSeconds / 3600
                val mins = (stbRecSeconds % 3600) / 60
                val secs = stbRecSeconds % 60
                val timeStr = if (hours > 0) {
                    "%02d:%02d:%02d".format(hours, mins, secs)
                } else {
                    "%02d:%02d".format(mins, secs)
                }
                txtStbRecordStatus.text = "در حال ضبط روی فلش رسیور… $timeStr"
                stbTimerHandler.postDelayed(this, 1000)
            }
        }
    }

    // Local STB Timers list
    data class StbScheduledTimer(
        val id: String,
        val channelName: String,
        val channelId: String,
        val isRadio: Boolean,
        val startHour: Int,
        val startMinute: Int,
        val durationMinutes: Int,
        val endHour: Int,
        val endMinute: Int,
        val repeatMode: Int = 0,
        val autoWake: Boolean = false,
        val autoPowerOff: Boolean = false,
        val autoDelete: Boolean = false,
        var isRecordingActive: Boolean = false,
        var isFinished: Boolean = false,
        var lastTriggeredDate: String = ""
    )
    private val stbTimers = mutableListOf<StbScheduledTimer>()
    private var isStbWatcherRunning = false
    private var isPhoneWatcherRunning = false
    private var isScheduledPhoneRecordingActive = false

    // Local Phone Timers list
    data class PhoneScheduledTimer(
        val id: String,
        val channelName: String,
        val channelId: String,
        val isAudioOnly: Boolean,
        val startHour: Int,
        val startMinute: Int,
        val durationMinutes: Int,
        var isTriggered: Boolean = false,
        val endHour: Int = (startHour + durationMinutes / 60) % 24,
        val endMinute: Int = (startMinute + durationMinutes % 60) % 60,
        val repeatMode: Int = 0,
        val autoWake: Boolean = false,
        val autoPowerOff: Boolean = false,
        val autoDelete: Boolean = false
    )
    private val phoneTimers = mutableListOf<PhoneScheduledTimer>()

    private val repository get() = GMScreenApplication.instance.repository
    private val appConfig get() = GMScreenApplication.instance.appConfig

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_record, container, false)

        txtInstantRecStatus = view.findViewById(R.id.txtInstantRecStatus)
        btnStartPhoneVideoRec = view.findViewById(R.id.btnStartPhoneVideoRec)
        btnStartPhoneAudioRec = view.findViewById(R.id.btnStartPhoneAudioRec)
        btnStopPhoneRec = view.findViewById(R.id.btnStopPhoneRec)
        btnStbToggleRecord = view.findViewById(R.id.btnStbToggleRecord)
        btnViewRecordings = view.findViewById(R.id.btnViewRecordings)
        txtStbRecordStatus = view.findViewById(R.id.txtStbRecordStatus)

        btnAddStbTimer = view.findViewById(R.id.btnAddStbTimer)
        btnStopStbScheduledRec = view.findViewById(R.id.btnStopStbScheduledRec)
        containerStbTimers = view.findViewById(R.id.containerStbTimers)
        txtNoStbTimers = view.findViewById(R.id.txtNoStbTimers)

        btnAddPhoneTimer = view.findViewById(R.id.btnAddPhoneTimer)
        btnStopPhoneScheduledRec = view.findViewById(R.id.btnStopPhoneScheduledRec)
        containerPhoneTimers = view.findViewById(R.id.containerPhoneTimers)
        txtNoPhoneTimers = view.findViewById(R.id.txtNoPhoneTimers)

        btnRefreshStbPvrFiles = view.findViewById(R.id.btnRefreshStbPvrFiles)
        containerStbPvrFiles = view.findViewById(R.id.containerStbPvrFiles)
        txtNoStbPvrFiles = view.findViewById(R.id.txtNoStbPvrFiles)

        setupListeners()
        observeData()
        loadStbTimers()
        startStbTimerWatcher()
        loadPhoneTimers()
        startPhoneTimerWatcher()
        updateFormatLabels()

        return view
    }

    override fun onResume() {
        super.onResume()
        updateFormatLabels()
        syncPhoneRecordingUi()
    }

    private fun syncPhoneRecordingUi() {
        if (!isAdded) return
        val isRec = com.gmscreen.accessible.player.StreamRecorderManager.isRecording
        if (isRec && !isRecordingOnPhone) {
            isRecordingOnPhone = true
            val chName = com.gmscreen.accessible.player.StreamRecorderManager.currentChannel?.serviceName ?: "شبکه"
            txtInstantRecStatus.text = "در حال ضبط روی گوشی ($chName)..."
            if (isScheduledPhoneRecordingActive) {
                btnStopPhoneScheduledRec.visibility = View.VISIBLE
                btnStopPhoneRec.visibility = View.GONE
            } else {
                btnStopPhoneRec.visibility = View.VISIBLE
                btnStopPhoneScheduledRec.visibility = View.GONE
            }
            timerHandler.post(timerRunnable)
        } else if (!isRec && isRecordingOnPhone) {
            isRecordingOnPhone = false
            isScheduledPhoneRecordingActive = false
            timerHandler.removeCallbacks(timerRunnable)
            btnStopPhoneRec.visibility = View.GONE
            btnStopPhoneScheduledRec.visibility = View.GONE
            btnStartPhoneVideoRec.isEnabled = true
            btnStartPhoneAudioRec.isEnabled = true
            updateFormatLabels()
        }
    }

    private fun updateFormatLabels() {
        if (!isAdded) return
        val vidFmt = appConfig.videoRecFormat.uppercase()
        val audFmt = appConfig.audioRecFormat.uppercase()
        btnStartPhoneVideoRec.text = "تصویری گوشی ($vidFmt)"
        btnStartPhoneVideoRec.contentDescription = "شروع ضبط تصویری با فرمت $vidFmt بر روی حافظه گوشی"
        btnStartPhoneAudioRec.text = "صوتی گوشی ($audFmt)"
        btnStartPhoneAudioRec.contentDescription = "شروع ضبط صوتی با فرمت $audFmt بر روی حافظه گوشی"
    }

    private fun setupListeners() {
        btnStartPhoneVideoRec.setOnClickListener {
            if (isRecordingOnPhone) {
                stopPhoneRecording()
            } else {
                isScheduledPhoneRecordingActive = false
                startPhoneRecording(isAudioOnly = false)
            }
        }

        btnStartPhoneAudioRec.setOnClickListener {
            if (isRecordingOnPhone) {
                stopPhoneRecording()
            } else {
                isScheduledPhoneRecordingActive = false
                startPhoneRecording(isAudioOnly = true)
            }
        }

        btnStopPhoneRec.setOnClickListener {
            stopPhoneRecording()
        }

        btnViewRecordings.setOnClickListener {
            com.gmscreen.accessible.util.RecordingsDialogHelper.showRecordingsDialog(requireContext(), btnViewRecordings)
        }

        btnStbToggleRecord.setOnClickListener {
            if (repository.connectedIp.value.isBlank()) {
                val msg = "رسیور متصل نیست. ابتدا در سربرگ اتصال وصل شوید."
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                if (!isStbRecording) {
                    repository.sendRcuKey(RcuKey.RECORD)
                    withContext(Dispatchers.Main) {
                        isStbRecording = true
                        com.gmscreen.accessible.service.GMScreenBackgroundService.notifyStbRecordingState(true, repository.currentChannel.value?.serviceName ?: "شبکه")
                        updateStbRecordButtonUi()
                        val msg = "دستور شروع ضبط روی فلش مموری رسیور ارسال شد"
                        AccessibilityHelper.announce(btnStbToggleRecord, msg)
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    repository.sendRcuKey(RcuKey.STOP)
                    kotlinx.coroutines.delay(800)
                    repository.sendRcuKey(RcuKey.OK)
                    kotlinx.coroutines.delay(500)
                    repository.sendRcuKey(RcuKey.EXIT)
                    withContext(Dispatchers.Main) {
                        isStbRecording = false
                        stbRecSeconds = 0
                        com.gmscreen.accessible.service.GMScreenBackgroundService.notifyStbRecordingState(false, "")
                        updateStbRecordButtonUi()
                        val msg = "دستور توقف ضبط به رسیور ارسال شد"
                        AccessibilityHelper.announce(btnStbToggleRecord, msg)
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnAddStbTimer.setOnClickListener {
            showAddOrEditStbTimerDialog()
        }

        btnAddPhoneTimer.setOnClickListener {
            showAddOrEditPhoneTimerDialog()
        }

        btnStopStbScheduledRec.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                repository.sendRcuKey(RcuKey.STOP)
                kotlinx.coroutines.delay(800)
                repository.sendRcuKey(RcuKey.OK)
                kotlinx.coroutines.delay(500)
                repository.sendRcuKey(RcuKey.EXIT)
                withContext(Dispatchers.Main) {
                    isStbRecording = false
                    stbRecSeconds = 0
                    com.gmscreen.accessible.service.GMScreenBackgroundService.notifyStbRecordingState(false, "")
                    updateStbRecordButtonUi()
                    val msg = "دستور توقف ضبط به رسیور ارسال شد"
                    AccessibilityHelper.announce(btnStopStbScheduledRec, msg)
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnStopPhoneScheduledRec.setOnClickListener {
            stopPhoneRecording()
            AccessibilityHelper.announce(btnStopPhoneScheduledRec, "ضبط زمان‌بندی گوشی متوقف شد")
        }

        btnRefreshStbPvrFiles.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                repository.refreshPvrFiles()
            }
        }
        updateStbRecordButtonUi()
    }

    private fun updateStbRecordButtonUi() {
        if (!isAdded) return
        if (isStbRecording) {
            val hours = stbRecSeconds / 3600
            val mins = (stbRecSeconds % 3600) / 60
            val secs = stbRecSeconds % 60
            val timeStr = if (hours > 0) "%02d:%02d:%02d".format(hours, mins, secs) else "%02d:%02d".format(mins, secs)
            btnStbToggleRecord.text = "توقف ضبط رسیور"
            btnStbToggleRecord.contentDescription = "دکمه توقف ضبط رسیور"
            btnStbToggleRecord.setBackgroundColor(resources.getColor(R.color.accent_red, null))
            txtStbRecordStatus.text = "در حال ضبط روی فلش رسیور… $timeStr"
            txtStbRecordStatus.setTextColor(resources.getColor(R.color.accent_red, null))
            btnStopStbScheduledRec.visibility = View.VISIBLE
            stbTimerHandler.removeCallbacks(stbTimerRunnable)
            stbTimerHandler.post(stbTimerRunnable)
        } else {
            stbTimerHandler.removeCallbacks(stbTimerRunnable)
            stbRecSeconds = 0
            btnStbToggleRecord.text = "شروع ضبط روی فلش رسیور"
            btnStbToggleRecord.contentDescription = "شروع ضبط کانال جاری بر روی حافظه فلش متصل به رسیور"
            btnStbToggleRecord.setBackgroundColor(resources.getColor(R.color.accent_green, null))
            txtStbRecordStatus.text = "وضعیت ضبط رسیور: غیرفعال"
            txtStbRecordStatus.setTextColor(resources.getColor(R.color.text_secondary, null))
            btnStopStbScheduledRec.visibility = View.GONE
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.pvrFiles.collect { files ->
                displayStbPvrFiles(files)
            }
        }
    }

    private fun showAccessibleHourPicker(title: String, onHourSelected: (Int) -> Unit) {
        // Ordered from 1 AM (1 صبح/بامداد) through 12 AM (12 نیمه‌شب / ۲۴)
        val orderedHours = (1..23).toList() + listOf(0)
        val hourLabels = orderedHours.map { h ->
            val h12 = if (h == 0 || h == 12) 12 else if (h > 12) h - 12 else h
            val period = when {
                h in 1..4 -> "بامداد"
                h in 5..11 -> "صبح"
                h == 12 -> "ظهر"
                h in 13..16 -> "بعدازظهر"
                h in 17..19 -> "عصر"
                h in 20..23 -> "شب"
                else -> "۱۲ بعد از ظهر / نیمه‌شب"
            }
            if (h == 0) {
                "ساعت ۰۰:۰۰ (۱۲ بعد از ظهر / نیمه‌شب)"
            } else {
                "ساعت %02d:۰۰ (%d %s)".format(h, h12, period)
            }
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setItems(hourLabels) { _, which ->
                val chosenHour = orderedHours[which]
                onHourSelected(chosenHour)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showAccessibleMinutePicker(title: String, onMinuteSelected: (Int) -> Unit) {
        val minutes = Array(12) { i ->
            val m = i * 5
            String.format(Locale.US, "%02d دقیقه", m)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setItems(minutes) { _, which ->
                onMinuteSelected(which * 5)
            }
            .setNeutralButton("تنظیم دقیق عددی (۰ تا ۵۹)") { _, _ ->
                showCustomMinuteInputDialog(onMinuteSelected)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showCustomMinuteInputDialog(onMinuteSelected: (Int) -> Unit) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "دقیقه بین ۰ تا ۵۹"
            setPadding(40, 30, 40, 30)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("ورود دقیقه دلخواه (۰ تا ۵۹)")
            .setView(input)
            .setPositiveButton("تأیید") { _, _ ->
                val v = input.text.toString().trim().toIntOrNull()?.coerceIn(0, 59) ?: 0
                onMinuteSelected(v)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showAddOrEditStbTimerDialog(existingTimer: StbScheduledTimer? = null) {
        val channels = repository.channels.value
        if (channels.isEmpty()) {
            Toast.makeText(requireContext(), "ابتدا باید به رسیور متصل شوید تا کانال‌ها دریافت شوند", Toast.LENGTH_SHORT).show()
            return
        }

        val cal = Calendar.getInstance()
        var targetChannel = if (existingTimer != null) {
            channels.find { it.serviceId == existingTimer.channelId }
                ?: Channel(
                    serviceId = existingTimer.channelId,
                    serviceName = existingTimer.channelName,
                    isRadio = existingTimer.isRadio
                )
        } else {
            repository.currentChannel.value ?: channels.first()
        }

        var startH = existingTimer?.startHour ?: ((cal.get(Calendar.HOUR_OF_DAY) + if (cal.get(Calendar.MINUTE) >= 55) 1 else 0) % 24)
        var startM = existingTimer?.startMinute ?: (((cal.get(Calendar.MINUTE) / 5) * 5 + 5) % 60)
        var endH = existingTimer?.endHour ?: ((startH + 1) % 24)
        var endM = existingTimer?.endMinute ?: startM
        var repeatMode = existingTimer?.repeatMode ?: 0
        var autoWake = existingTimer?.autoWake ?: false
        var autoPowerOff = existingTimer?.autoPowerOff ?: false
        var autoDelete = existingTimer?.autoDelete ?: false

        val repeatTitles = arrayOf("یک‌بار (پیش‌فرض)", "روزانه (هر روز)", "هفتگی (همین روز هر هفته)")

        val context = requireContext()
        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 20, 32, 20)
        }
        scrollView.addView(container)

        fun addRow(title: String, initialVal: String, onClick: (TextView, View) -> Unit): Pair<TextView, View> {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = true
                isClickable = true
                setBackgroundResource(R.drawable.card_channel_bg)
                setPadding(28, 20, 28, 20)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 6, 0, 8)
                }
            }
            val txtTitle = TextView(context).apply {
                text = title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(resources.getColor(R.color.accent_green, null))
            }
            val txtVal = TextView(context).apply {
                text = initialVal
                textSize = 13f
                setTextColor(resources.getColor(R.color.text_primary, null))
                setPadding(0, 4, 0, 0)
            }
            row.addView(txtTitle)
            row.addView(txtVal)
            row.contentDescription = "$title: $initialVal. برای تغییر ضربه بزنید."
            row.setOnClickListener { onClick(txtVal, row) }
            container.addView(row)
            return Pair(txtVal, row)
        }

        lateinit var txtCh: TextView; lateinit var rowCh: View
        lateinit var txtStartH: TextView; lateinit var rowStartH: View
        lateinit var txtStartM: TextView; lateinit var rowStartM: View
        lateinit var txtEndH: TextView; lateinit var rowEndH: View
        lateinit var txtEndM: TextView; lateinit var rowEndM: View
        lateinit var txtRepeat: TextView; lateinit var rowRepeat: View
        lateinit var txtWake: TextView; lateinit var rowWake: View
        lateinit var txtPowerOff: TextView; lateinit var rowPowerOff: View
        lateinit var txtDelete: TextView; lateinit var rowDelete: View

        val p0 = addRow("۱. شبکه انتخابی", targetChannel.serviceName) { txt, row ->
            showChannelPicker("انتخاب شبکه برای ضبط رسیور", targetChannel) { chosen ->
                targetChannel = chosen
                txt.text = targetChannel.serviceName
                row.contentDescription = "۱. شبکه انتخابی: ${targetChannel.serviceName}. برای تغییر ضربه بزنید."
                AccessibilityHelper.announce(row, "شبکه ${targetChannel.serviceName} انتخاب شد")
                row.requestFocus()
            }
        }
        txtCh = p0.first; rowCh = p0.second

        val p1 = addRow("۲. ساعت شروع ضبط", "%02d:00".format(startH)) { txt, row ->
            showAccessibleHourPicker("ساعت شروع ضبط رسیور") { h ->
                startH = h
                if (existingTimer == null) endH = (startH + 1) % 24
                txt.text = "%02d:00".format(startH)
                row.contentDescription = "۲. ساعت شروع ضبط: %02d. برای تغییر ضربه بزنید.".format(startH)
                txtEndH.text = "%02d:00".format(endH)
                rowEndH.contentDescription = "۴. ساعت پایان ضبط: %02d. برای تغییر ضربه بزنید.".format(endH)
                AccessibilityHelper.announce(row, "ساعت شروع ضبط: $startH تنظیم شد")
                row.requestFocus()
            }
        }
        txtStartH = p1.first; rowStartH = p1.second

        val p2 = addRow("۳. دقیقه شروع ضبط", "%02d دقیقه".format(startM)) { txt, row ->
            showAccessibleMinutePicker("دقیقه شروع ضبط رسیور") { m ->
                startM = m
                if (existingTimer == null) endM = startM
                txt.text = "%02d دقیقه".format(startM)
                row.contentDescription = "۳. دقیقه شروع ضبط: %02d. برای تغییر ضربه بزنید.".format(startM)
                txtEndM.text = "%02d دقیقه".format(endM)
                rowEndM.contentDescription = "۵. دقیقه پایان ضبط: %02d. برای تغییر ضربه بزنید.".format(endM)
                AccessibilityHelper.announce(row, "دقیقه شروع ضبط: $startM تنظیم شد")
                row.requestFocus()
            }
        }
        txtStartM = p2.first; rowStartM = p2.second

        val p3 = addRow("۴. ساعت پایان ضبط", "%02d:00".format(endH)) { txt, row ->
            showAccessibleHourPicker("ساعت پایان ضبط رسیور") { h ->
                endH = h
                txt.text = "%02d:00".format(endH)
                row.contentDescription = "۴. ساعت پایان ضبط: %02d. برای تغییر ضربه بزنید.".format(endH)
                AccessibilityHelper.announce(row, "ساعت پایان ضبط: $endH تنظیم شد")
                row.requestFocus()
            }
        }
        txtEndH = p3.first; rowEndH = p3.second

        val p4 = addRow("۵. دقیقه پایان ضبط", "%02d دقیقه".format(endM)) { txt, row ->
            showAccessibleMinutePicker("دقیقه پایان ضبط رسیور") { m ->
                endM = m
                txt.text = "%02d دقیقه".format(endM)
                row.contentDescription = "۵. دقیقه پایان ضبط: %02d. برای تغییر ضربه بزنید.".format(endM)
                AccessibilityHelper.announce(row, "دقیقه پایان ضبط: $endM تنظیم شد")
                row.requestFocus()
            }
        }
        txtEndM = p4.first; rowEndM = p4.second

        val p5 = addRow("۶. تکرار زمان‌بندی", repeatTitles[repeatMode]) { txt, row ->
            MaterialAlertDialogBuilder(context)
                .setTitle("انتخاب نحوه تکرار زمان‌بندی")
                .setItems(repeatTitles) { _, rIdx ->
                    repeatMode = rIdx
                    txt.text = repeatTitles[repeatMode]
                    row.contentDescription = "۶. تکرار زمان‌بندی: ${repeatTitles[repeatMode]}. برای تغییر ضربه بزنید."
                    AccessibilityHelper.announce(row, "تکرار: ${repeatTitles[repeatMode]} تنظیم شد")
                    row.requestFocus()
                }
                .show()
        }
        txtRepeat = p5.first; rowRepeat = p5.second

        val p6 = addRow("۷. روشن شدن خودکار رسیور از حالت آماده‌به‌کار", if (autoWake) "فعال" else "غیرفعال") { txt, row ->
            autoWake = !autoWake
            val st = if (autoWake) "فعال" else "غیرفعال"
            txt.text = st
            row.contentDescription = "۷. روشن شدن خودکار رسیور از حالت آماده‌به‌کار: $st. برای تغییر وضعیت ضربه بزنید."
            AccessibilityHelper.announce(row, "روشن شدن خودکار رسیور: $st شد")
            row.requestFocus()
        }
        txtWake = p6.first; rowWake = p6.second

        val p7 = addRow("۸. خاموش شدن خودکار رسیور پس از اتمام ضبط", if (autoPowerOff) "فعال" else "غیرفعال") { txt, row ->
            autoPowerOff = !autoPowerOff
            val st = if (autoPowerOff) "فعال" else "غیرفعال"
            txt.text = st
            row.contentDescription = "۸. خاموش شدن خودکار رسیور پس از اتمام ضبط: $st. برای تغییر وضعیت ضربه بزنید."
            AccessibilityHelper.announce(row, "خاموش شدن خودکار رسیور: $st شد")
            row.requestFocus()
        }
        txtPowerOff = p7.first; rowPowerOff = p7.second

        val p8 = addRow("۹. حذف خودکار زمان‌بندی از رسیور پس از اتمام", if (autoDelete) "فعال" else "غیرفعال") { txt, row ->
            autoDelete = !autoDelete
            val st = if (autoDelete) "فعال" else "غیرفعال"
            txt.text = st
            row.contentDescription = "۹. حذف خودکار زمان‌بندی از رسیور پس از اتمام: $st. برای تغییر وضعیت ضربه بزنید."
            AccessibilityHelper.announce(row, "حذف خودکار زمان‌بندی: $st شد")
            row.requestFocus()
        }
        txtDelete = p8.first; rowDelete = p8.second

        val dialogTitle = if (existingTimer != null) "ویرایش زمان‌بندی ضبط روی فلش رسیور" else "افزودن زمان‌بندی ضبط روی فلش رسیور"
        val btnConfirmText = if (existingTimer != null) "ذخیره تغییرات" else "ثبت زمان‌بندی در رسیور"

        MaterialAlertDialogBuilder(context)
            .setTitle(dialogTitle)
            .setView(scrollView)
            .setPositiveButton(btnConfirmText) { _, _ ->
                var dur = (endH * 60 + endM) - (startH * 60 + startM)
                if (dur <= 0) dur += 24 * 60

                if (existingTimer != null) {
                    val updated = existingTimer.copy(
                        channelName = targetChannel.serviceName,
                        channelId = targetChannel.serviceId,
                        isRadio = targetChannel.isRadio,
                        startHour = startH,
                        startMinute = startM,
                        durationMinutes = dur,
                        endHour = endH,
                        endMinute = endM,
                        repeatMode = repeatMode,
                        autoWake = autoWake,
                        autoPowerOff = autoPowerOff,
                        autoDelete = autoDelete,
                        isRecordingActive = false,
                        isFinished = false
                    )
                    val idx = stbTimers.indexOfFirst { it.id == existingTimer.id }
                    if (idx != -1) {
                        stbTimers[idx] = updated
                    } else {
                        stbTimers.add(updated)
                    }
                    saveStbTimers()
                    renderStbTimers()
                    val msg = "تغییرات زمان‌بندی ضبط ${targetChannel.serviceName} روی فلش ذخیره شد"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    AccessibilityHelper.announce(containerStbTimers, msg)
                } else {
                    val newTimer = StbScheduledTimer(
                        id = System.currentTimeMillis().toString(),
                        channelName = targetChannel.serviceName,
                        channelId = targetChannel.serviceId,
                        isRadio = targetChannel.isRadio,
                        startHour = startH,
                        startMinute = startM,
                        durationMinutes = dur,
                        endHour = endH,
                        endMinute = endM,
                        repeatMode = repeatMode,
                        autoWake = autoWake,
                        autoPowerOff = autoPowerOff,
                        autoDelete = autoDelete
                    )
                    stbTimers.add(newTimer)
                    saveStbTimers()
                    renderStbTimers()

                    confirmAndSendStbTimer(targetChannel, startH, startM, endH, endM, repeatMode, autoWake, autoPowerOff, autoDelete)

                    val msg = "زمان‌بندی ضبط ${targetChannel.serviceName} روی فلش رسیور از %02d:%02d تا %02d:%02d ثبت شد".format(startH, startM, endH, endM)
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    AccessibilityHelper.announce(containerStbTimers, msg)
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showChannelPicker(title: String, currentChannel: Channel, onChannelSelected: (Channel) -> Unit) {
        val allChannels = repository.channels.value
        if (allChannels.isEmpty()) {
            Toast.makeText(requireContext(), "ابتدا باید به رسیور متصل شوید تا کانال‌ها دریافت شوند", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_channel_picker, null)
        val txtTitle = dialogView.findViewById<TextView>(R.id.txtPickerTitle)
        val edtSearch = dialogView.findViewById<EditText>(R.id.edtPickerSearch)
        val btnClear = dialogView.findViewById<ImageButton>(R.id.btnPickerClearSearch)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerPickerChannels)
        val txtNoChannels = dialogView.findViewById<TextView>(R.id.txtPickerNoChannels)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnPickerCancel)

        txtTitle.text = title

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        val filteredChannels = mutableListOf<Channel>()
        filteredChannels.addAll(allChannels)

        lateinit var adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
        adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_picker_channel, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }

            override fun getItemCount(): Int = filteredChannels.size

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val ch = filteredChannels[position]
                val txtNum = holder.itemView.findViewById<TextView>(R.id.txtPickerChannelNumber)
                val txtName = holder.itemView.findViewById<TextView>(R.id.txtPickerChannelName)
                val txtType = holder.itemView.findViewById<TextView>(R.id.txtPickerChannelType)
                val badgeSelected = holder.itemView.findViewById<TextView>(R.id.badgePickerSelected)

                val numStr = if (ch.realChannelNum > 0) ch.realChannelNum.toString() else (allChannels.indexOf(ch) + 1).toString()
                txtNum.text = numStr
                txtName.text = ch.serviceName
                txtType.text = if (ch.isRadio) "رادیو" else "تلویزیون"

                val isSelected = (ch.serviceId == currentChannel.serviceId)
                badgeSelected.visibility = if (isSelected) View.VISIBLE else View.GONE
                if (isSelected) {
                    holder.itemView.setBackgroundResource(R.drawable.card_channel_playing_bg)
                } else {
                    holder.itemView.setBackgroundResource(R.drawable.card_channel_bg)
                }

                val typeLabel = if (ch.isRadio) "رادیو" else "کانال"
                val selLabel = if (isSelected) "، در حال حاضر انتخاب شده" else ""
                holder.itemView.contentDescription = "$typeLabel $numStr: ${ch.serviceName}$selLabel. برای انتخاب ضربه بزنید."

                holder.itemView.setOnClickListener {
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.hideSoftInputFromWindow(edtSearch.windowToken, 0)
                    dialog.dismiss()
                    onChannelSelected(ch)
                }
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        fun filter(query: String) {
            val q = query.trim().lowercase(Locale.ROOT)
                .replace("ي", "ی")
                .replace("ك", "ک")
            filteredChannels.clear()
            if (q.isEmpty()) {
                filteredChannels.addAll(allChannels)
            } else {
                for (ch in allChannels) {
                    val nameNorm = ch.serviceName.lowercase(Locale.ROOT)
                        .replace("ي", "ی")
                        .replace("ك", "ک")
                    val num = if (ch.realChannelNum > 0) ch.realChannelNum.toString() else (allChannels.indexOf(ch) + 1).toString()
                    if (nameNorm.contains(q) || num.contains(q)) {
                        filteredChannels.add(ch)
                    }
                }
            }
            adapter.notifyDataSetChanged()
            if (filteredChannels.isEmpty()) {
                txtNoChannels.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                txtNoChannels.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
            if (q.isNotEmpty()) {
                AccessibilityHelper.announce(recyclerView, "${filteredChannels.size} شبکه یافت شد")
            }
        }

        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString() ?: ""
                btnClear.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
                filter(text)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClear.setOnClickListener {
            edtSearch.text?.clear()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(edtSearch.windowToken, 0)
            edtSearch.clearFocus()
            AccessibilityHelper.announce(dialogView, "متن جست‌وجو پاک شد و صفحه کلید بسته شد")
        }

        btnCancel.setOnClickListener {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(edtSearch.windowToken, 0)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun confirmAndSendStbTimer(
        channel: Channel,
        sh: Int,
        sm: Int,
        eh: Int,
        em: Int,
        repeatMode: Int,
        autoWake: Boolean,
        autoPowerOff: Boolean,
        autoDelete: Boolean
    ) {
        val cal = Calendar.getInstance()
        val mon = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)

        val timerPayload = mapOf(
            "TimerIndex" to "0",
            "TimerProgramId" to channel.serviceId,
            "TimerProgramName" to channel.serviceName,
            "TimerMonth" to mon.toString(),
            "TimerDay" to day.toString(),
            "TimerStartHour" to sh.toString(),
            "TimerStartMin" to sm.toString(),
            "TimerEndHour" to eh.toString(),
            "TimerEndMin" to em.toString(),
            "TimerRepeat" to repeatMode.toString(),
            "TimerStatus" to "2", // 2 = Record mode on ALi PVR STB!
            "TimerEventID" to "0",
            "auto_wake" to if (autoWake) "1" else "0",
            "auto_poweroff" to if (autoPowerOff) "1" else "0",
            "auto_delete" to if (autoDelete) "1" else "0"
        )

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                repository.addStbTimer(timerPayload)
            } catch (_: Exception) {}
        }
    }

    private fun showTimerOptionsDialog(
        title: String,
        timerSummary: String,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage("مشخصات زمان‌بندی:\n$timerSummary\n\nعملیات مورد نظر خود را انتخاب کنید:")
            .setPositiveButton("ویرایش زمان‌بندی") { _, _ ->
                onEdit()
            }
            .setNegativeButton("حذف زمان‌بندی") { _, _ ->
                onDelete()
            }
            .setNeutralButton("انصراف", null)
            .show()
    }

    private fun renderStbTimers() {
        if (!isAdded) return
        containerStbTimers.removeAllViews()
        if (stbTimers.isEmpty()) {
            txtNoStbTimers.visibility = View.VISIBLE
        } else {
            txtNoStbTimers.visibility = View.GONE
            for ((index, t) in stbTimers.withIndex()) {
                val timerText = "تایمر ${index + 1}: ${t.channelName} از %02d:%02d تا %02d:%02d روی فلش".format(
                    t.startHour, t.startMinute, t.endHour, t.endMinute
                )

                fun confirmDelete() {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("حذف زمان‌بندی رسیور")
                        .setMessage("آیا می‌خواهید $timerText را حذف کنید؟")
                        .setPositiveButton("بله، حذف کن") { _, _ ->
                            stbTimers.remove(t)
                            saveStbTimers()
                            renderStbTimers()
                            AccessibilityHelper.announce(containerStbTimers, "زمان‌بندی رسیور حذف شد")
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val dummyPayload = mapOf(
                                        "TimerProgramId" to t.channelId,
                                        "TimerStartHour" to t.startHour.toString(),
                                        "TimerStartMin" to t.startMinute.toString()
                                    )
                                    repository.deleteStbTimer(dummyPayload)
                                } catch (_: Exception) {}
                            }
                        }
                        .setNegativeButton("انصراف", null)
                        .show()
                }

                val btn = MaterialButton(requireContext()).apply {
                    text = timerText
                    contentDescription = "$timerText. برای ویرایش یا حذف کلیک کنید."
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        resources.getDimensionPixelSize(android.R.dimen.app_icon_size) + 16
                    ).apply { setMargins(0, 4, 0, 6) }
                    setBackgroundColor(resources.getColor(R.color.card_bg, null))
                    setTextColor(resources.getColor(R.color.accent_green, null))
                    textSize = 13f
                    setOnClickListener {
                        showTimerOptionsDialog(
                            title = "مدیریت زمان‌بندی رسیور",
                            timerSummary = timerText,
                            onEdit = { showAddOrEditStbTimerDialog(t) },
                            onDelete = { confirmDelete() }
                        )
                    }
                    setOnLongClickListener {
                        confirmDelete()
                        true
                    }
                }
                containerStbTimers.addView(btn)
            }
        }
    }

    private fun saveStbTimers() {
        val ctx = context ?: return
        val file = File(StorageManager.getRecordingsDir(ctx), "stb_scheduled_timers.json")
        try {
            val json = Gson().toJson(stbTimers)
            file.writeText(json, Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    private fun loadStbTimers() {
        val ctx = context ?: return
        val file = File(StorageManager.getRecordingsDir(ctx), "stb_scheduled_timers.json")
        if (file.exists()) {
            try {
                val json = file.readText(Charsets.UTF_8)
                val type = object : TypeToken<List<StbScheduledTimer>>() {}.type
                val loaded: List<StbScheduledTimer> = Gson().fromJson(json, type)
                stbTimers.clear()
                stbTimers.addAll(loaded)
            } catch (_: Exception) {}
        }
        renderStbTimers()
    }

    private fun startStbTimerWatcher() {
        if (isStbWatcherRunning) return
        isStbWatcherRunning = true

        lifecycleScope.launch {
            while (true) {
                try {
                    val cal = Calendar.getInstance()
                    val curH = cal.get(Calendar.HOUR_OF_DAY)
                    val curM = cal.get(Calendar.MINUTE)
                    val curDay = cal.get(Calendar.DAY_OF_YEAR)
                    val todayStr = "$curDay-${cal.get(Calendar.YEAR)}"

                    val toDelete = mutableListOf<StbScheduledTimer>()

                    for (t in stbTimers.toList()) {
                        // Check Start:
                        val canStartToday = (t.repeatMode != 0) || (t.lastTriggeredDate != todayStr)
                        if (!t.isRecordingActive && t.startHour == curH && t.startMinute == curM && canStartToday) {
                            t.isRecordingActive = true
                            t.isFinished = false
                            t.lastTriggeredDate = todayStr
                            saveStbTimers()

                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    // 1. Auto wake if STB standby
                                    if (t.autoWake && repository.isConnected.value) {
                                        try {
                                            repository.client.sendCmd(1009, expectReply = false)
                                            kotlinx.coroutines.delay(2000)
                                        } catch (_: Exception) {}
                                    }

                                    // 2. Switch to channel if needed
                                    val ch = repository.channels.value.find { it.serviceId == t.channelId }
                                    if (ch != null) {
                                        repository.switchChannel(ch)
                                        kotlinx.coroutines.delay(1200)
                                    }

                                    // 3. Send RcuKey.RECORD to start PVR recording on STB USB
                                    repository.sendRcuKey(RcuKey.RECORD)

                                    withContext(Dispatchers.Main) {
                                        val startMsg = "ضبط زمان‌بندی‌شده شبکه ${t.channelName} بر روی حافظه فلش رسیور آغاز شد."
                                        TtsHelper.speak(startMsg)
                                        com.gmscreen.accessible.service.GMScreenBackgroundService.notifyStbRecordingState(true, t.channelName)
                                        if (isAdded) {
                                            isStbRecording = true
                                            updateStbRecordButtonUi()
                                            Toast.makeText(requireContext(), startMsg, Toast.LENGTH_LONG).show()
                                            AccessibilityHelper.announce(txtStbRecordStatus, startMsg)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("RecordFragment", "Error starting scheduled STB recording", e)
                                }
                            }
                        }

                        // Check End:
                        if (t.isRecordingActive && t.endHour == curH && t.endMinute == curM && !t.isFinished) {
                            t.isFinished = true
                            t.isRecordingActive = false
                            saveStbTimers()

                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    // 1. Send STOP -> 800ms -> OK -> 500ms -> EXIT
                                    repository.sendRcuKey(RcuKey.STOP)
                                    kotlinx.coroutines.delay(800)
                                    repository.sendRcuKey(RcuKey.OK)
                                    kotlinx.coroutines.delay(500)
                                    repository.sendRcuKey(RcuKey.EXIT)

                                    withContext(Dispatchers.Main) {
                                        val endMsg = "ضبط زمان‌بندی‌شده شبکه ${t.channelName} بر روی حافظه فلش به پایان رسید."
                                        TtsHelper.speak(endMsg)
                                        com.gmscreen.accessible.service.GMScreenBackgroundService.notifyStbRecordingState(false, "")
                                        if (isAdded) {
                                            isStbRecording = false
                                            stbRecSeconds = 0
                                            updateStbRecordButtonUi()
                                            Toast.makeText(requireContext(), endMsg, Toast.LENGTH_LONG).show()
                                            AccessibilityHelper.announce(txtStbRecordStatus, endMsg)
                                        }
                                    }

                                    // 2. Auto poweroff STB if enabled
                                    if (t.autoPowerOff && repository.isConnected.value) {
                                        kotlinx.coroutines.delay(3000)
                                        repository.sendRcuKey(RcuKey.POWER)
                                        withContext(Dispatchers.Main) {
                                            val pMsg = "رسیور پس از اتمام ضبط به حالت آماده‌به‌کار خاموش شد."
                                            TtsHelper.speak(pMsg)
                                            if (isAdded) {
                                                AccessibilityHelper.announce(txtStbRecordStatus, pMsg)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("RecordFragment", "Error stopping scheduled STB recording", e)
                                }
                            }

                            // 3. Auto delete if enabled
                            if (t.autoDelete && t.repeatMode == 0) {
                                toDelete.add(t)
                            }
                        }
                    }

                    if (toDelete.isNotEmpty()) {
                        stbTimers.removeAll(toDelete)
                        saveStbTimers()
                        withContext(Dispatchers.Main) {
                            renderStbTimers()
                        }
                    }
                } catch (_: Exception) {}

                kotlinx.coroutines.delay(5000) // check every 5 seconds
            }
        }
    }

    private fun showAddOrEditPhoneTimerDialog(existingTimer: PhoneScheduledTimer? = null) {
        val channels = repository.channels.value
        if (channels.isEmpty()) {
            Toast.makeText(requireContext(), "ابتدا باید به رسیور متصل شوید تا کانال‌ها دریافت شوند", Toast.LENGTH_SHORT).show()
            return
        }

        val cal = Calendar.getInstance()
        var targetChannel = if (existingTimer != null) {
            channels.find { it.serviceId == existingTimer.channelId }
                ?: Channel(
                    serviceId = existingTimer.channelId,
                    serviceName = existingTimer.channelName,
                    isRadio = existingTimer.isAudioOnly
                )
        } else {
            repository.currentChannel.value ?: channels.first()
        }

        var startH = existingTimer?.startHour ?: ((cal.get(Calendar.HOUR_OF_DAY) + if (cal.get(Calendar.MINUTE) >= 55) 1 else 0) % 24)
        var startM = existingTimer?.startMinute ?: (((cal.get(Calendar.MINUTE) / 5) * 5 + 5) % 60)
        var endH = existingTimer?.endHour ?: ((startH + 1) % 24)
        var endM = existingTimer?.endMinute ?: startM
        var isAudioOnly = existingTimer?.isAudioOnly ?: targetChannel.isRadio
        var repeatMode = existingTimer?.repeatMode ?: 0
        var autoWake = existingTimer?.autoWake ?: false
        var autoPowerOff = existingTimer?.autoPowerOff ?: false
        var autoDelete = existingTimer?.autoDelete ?: false

        val repeatTitles = arrayOf("یک‌بار (پیش‌فرض)", "روزانه (هر روز)", "هفتگی (همین روز هر هفته)")

        val context = requireContext()
        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 20, 32, 20)
        }
        scrollView.addView(container)

        fun addRow(title: String, initialVal: String, onClick: (TextView, View) -> Unit): Pair<TextView, View> {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = true
                isClickable = true
                setBackgroundResource(R.drawable.card_channel_bg)
                setPadding(28, 20, 28, 20)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 6, 0, 8)
                }
            }
            val txtTitle = TextView(context).apply {
                text = title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(resources.getColor(R.color.accent_green, null))
            }
            val txtVal = TextView(context).apply {
                text = initialVal
                textSize = 13f
                setTextColor(resources.getColor(R.color.text_primary, null))
                setPadding(0, 4, 0, 0)
            }
            row.addView(txtTitle)
            row.addView(txtVal)
            row.contentDescription = "$title: $initialVal. برای تغییر ضربه بزنید."
            row.setOnClickListener { onClick(txtVal, row) }
            container.addView(row)
            return Pair(txtVal, row)
        }

        lateinit var txtCh: TextView; lateinit var rowCh: View
        lateinit var txtStartH: TextView; lateinit var rowStartH: View
        lateinit var txtStartM: TextView; lateinit var rowStartM: View
        lateinit var txtEndH: TextView; lateinit var rowEndH: View
        lateinit var txtEndM: TextView; lateinit var rowEndM: View
        lateinit var txtFormat: TextView; lateinit var rowFormat: View
        lateinit var txtRepeat: TextView; lateinit var rowRepeat: View
        lateinit var txtWake: TextView; lateinit var rowWake: View
        lateinit var txtPowerOff: TextView; lateinit var rowPowerOff: View
        lateinit var txtDelete: TextView; lateinit var rowDelete: View

        val p0 = addRow("۱. شبکه انتخابی", targetChannel.serviceName) { txt, row ->
            showChannelPicker("انتخاب شبکه برای ضبط گوشی", targetChannel) { chosen ->
                targetChannel = chosen
                if (targetChannel.isRadio) isAudioOnly = true
                txt.text = targetChannel.serviceName
                row.contentDescription = "۱. شبکه انتخابی: ${targetChannel.serviceName}. برای تغییر ضربه بزنید."
                val fmtStr = if (isAudioOnly) "صوتی (${appConfig.audioRecFormat.uppercase()})" else "تصویری (${appConfig.videoRecFormat.uppercase()})"
                txtFormat.text = fmtStr
                rowFormat.contentDescription = "۶. نوع و فرمت فایل ضبط: $fmtStr. برای تغییر ضربه بزنید."
                AccessibilityHelper.announce(row, "شبکه ${targetChannel.serviceName} انتخاب شد")
                row.requestFocus()
            }
        }
        txtCh = p0.first; rowCh = p0.second

        val p1 = addRow("۲. ساعت شروع ضبط گوشی", "%02d:00".format(startH)) { txt, row ->
            showAccessibleHourPicker("ساعت شروع ضبط گوشی") { h ->
                startH = h
                if (existingTimer == null) endH = (startH + 1) % 24
                txt.text = "%02d:00".format(startH)
                row.contentDescription = "۲. ساعت شروع ضبط گوشی: %02d. برای تغییر ضربه بزنید.".format(startH)
                txtEndH.text = "%02d:00".format(endH)
                rowEndH.contentDescription = "۴. ساعت پایان ضبط گوشی: %02d. برای تغییر ضربه بزنید.".format(endH)
                AccessibilityHelper.announce(row, "ساعت شروع ضبط گوشی: $startH تنظیم شد")
                row.requestFocus()
            }
        }
        txtStartH = p1.first; rowStartH = p1.second

        val p2 = addRow("۳. دقیقه شروع ضبط گوشی", "%02d دقیقه".format(startM)) { txt, row ->
            showAccessibleMinutePicker("دقیقه شروع ضبط گوشی") { m ->
                startM = m
                if (existingTimer == null) endM = startM
                txt.text = "%02d دقیقه".format(startM)
                row.contentDescription = "۳. دقیقه شروع ضبط گوشی: %02d. برای تغییر ضربه بزنید.".format(startM)
                txtEndM.text = "%02d دقیقه".format(endM)
                rowEndM.contentDescription = "۵. دقیقه پایان ضبط گوشی: %02d. برای تغییر ضربه بزنید.".format(endM)
                AccessibilityHelper.announce(row, "دقیقه شروع ضبط گوشی: $startM تنظیم شد")
                row.requestFocus()
            }
        }
        txtStartM = p2.first; rowStartM = p2.second

        val p3 = addRow("۴. ساعت پایان ضبط گوشی", "%02d:00".format(endH)) { txt, row ->
            showAccessibleHourPicker("ساعت پایان ضبط گوشی") { h ->
                endH = h
                txt.text = "%02d:00".format(endH)
                row.contentDescription = "۴. ساعت پایان ضبط گوشی: %02d. برای تغییر ضربه بزنید.".format(endH)
                AccessibilityHelper.announce(row, "ساعت پایان ضبط گوشی: $endH تنظیم شد")
                row.requestFocus()
            }
        }
        txtEndH = p3.first; rowEndH = p3.second

        val p4 = addRow("۵. دقیقه پایان ضبط گوشی", "%02d دقیقه".format(endM)) { txt, row ->
            showAccessibleMinutePicker("دقیقه پایان ضبط گوشی") { m ->
                endM = m
                txt.text = "%02d دقیقه".format(endM)
                row.contentDescription = "۵. دقیقه پایان ضبط گوشی: %02d. برای تغییر ضربه بزنید.".format(endM)
                AccessibilityHelper.announce(row, "دقیقه پایان ضبط گوشی: $endM تنظیم شد")
                row.requestFocus()
            }
        }
        txtEndM = p4.first; rowEndM = p4.second

        val initFmt = if (isAudioOnly) "صوتی (${appConfig.audioRecFormat.uppercase()})" else "تصویری (${appConfig.videoRecFormat.uppercase()})"
        val p5 = addRow("۶. نوع و فرمت فایل ضبط", initFmt) { txt, row ->
            isAudioOnly = !isAudioOnly
            val fStr = if (isAudioOnly) "صوتی (${appConfig.audioRecFormat.uppercase()})" else "تصویری (${appConfig.videoRecFormat.uppercase()})"
            txt.text = fStr
            row.contentDescription = "۶. نوع و فرمت فایل ضبط: $fStr. برای تغییر ضربه بزنید."
            AccessibilityHelper.announce(row, "نوع فایل ضبط به $fStr تغییر یافت")
            row.requestFocus()
        }
        txtFormat = p5.first; rowFormat = p5.second

        val p6 = addRow("۷. تکرار زمان‌بندی", repeatTitles[repeatMode]) { txt, row ->
            MaterialAlertDialogBuilder(context)
                .setTitle("انتخاب نحوه تکرار زمان‌بندی گوشی")
                .setItems(repeatTitles) { _, rIdx ->
                    repeatMode = rIdx
                    txt.text = repeatTitles[repeatMode]
                    row.contentDescription = "۷. تکرار زمان‌بندی: ${repeatTitles[repeatMode]}. برای تغییر ضربه بزنید."
                    AccessibilityHelper.announce(row, "تکرار: ${repeatTitles[repeatMode]} تنظیم شد")
                    row.requestFocus()
                }
                .show()
        }
        txtRepeat = p6.first; rowRepeat = p6.second

        val p7 = addRow("۸. روشن شدن خودکار رسیور از حالت آماده‌به‌کار", if (autoWake) "فعال" else "غیرفعال") { txt, row ->
            autoWake = !autoWake
            val st = if (autoWake) "فعال" else "غیرفعال"
            txt.text = st
            row.contentDescription = "۸. روشن شدن خودکار رسیور از حالت آماده‌به‌کار: $st. برای تغییر وضعیت ضربه بزنید."
            AccessibilityHelper.announce(row, "روشن شدن خودکار رسیور: $st شد")
            row.requestFocus()
        }
        txtWake = p7.first; rowWake = p7.second

        val p8 = addRow("۹. خاموش شدن خودکار رسیور پس از اتمام ضبط", if (autoPowerOff) "فعال" else "غیرفعال") { txt, row ->
            autoPowerOff = !autoPowerOff
            val st = if (autoPowerOff) "فعال" else "غیرفعال"
            txt.text = st
            row.contentDescription = "۹. خاموش شدن خودکار رسیور پس از اتمام ضبط: $st. برای تغییر وضعیت ضربه بزنید."
            AccessibilityHelper.announce(row, "خاموش شدن خودکار رسیور: $st شد")
            row.requestFocus()
        }
        txtPowerOff = p8.first; rowPowerOff = p8.second

        val p9 = addRow("۱۰. حذف خودکار زمان‌بندی از لیست پس از اتمام", if (autoDelete) "فعال" else "غیرفعال") { txt, row ->
            autoDelete = !autoDelete
            val st = if (autoDelete) "فعال" else "غیرفعال"
            txt.text = st
            row.contentDescription = "۱۰. حذف خودکار زمان‌بندی از لیست پس از اتمام: $st. برای تغییر وضعیت ضربه بزنید."
            AccessibilityHelper.announce(row, "حذف خودکار زمان‌بندی: $st شد")
            row.requestFocus()
        }
        txtDelete = p9.first; rowDelete = p9.second

        val dialogTitle = if (existingTimer != null) "ویرایش زمان‌بندی ضبط در گوشی" else "افزودن زمان‌بندی ضبط در گوشی"
        val btnConfirmText = if (existingTimer != null) "ذخیره تغییرات" else "ثبت زمان‌بندی در گوشی"

        MaterialAlertDialogBuilder(context)
            .setTitle(dialogTitle)
            .setView(scrollView)
            .setPositiveButton(btnConfirmText) { _, _ ->
                var dur = (endH * 60 + endM) - (startH * 60 + startM)
                if (dur <= 0) dur += 24 * 60

                if (existingTimer != null) {
                    val updated = existingTimer.copy(
                        channelName = targetChannel.serviceName,
                        channelId = targetChannel.serviceId,
                        isAudioOnly = isAudioOnly,
                        startHour = startH,
                        startMinute = startM,
                        durationMinutes = dur,
                        endHour = endH,
                        endMinute = endM,
                        repeatMode = repeatMode,
                        autoWake = autoWake,
                        autoPowerOff = autoPowerOff,
                        autoDelete = autoDelete,
                        isTriggered = false
                    )
                    val idx = phoneTimers.indexOfFirst { it.id == existingTimer.id }
                    if (idx != -1) {
                        phoneTimers[idx] = updated
                    } else {
                        phoneTimers.add(updated)
                    }
                    savePhoneTimers()
                    renderPhoneTimers()
                    val msg = "تغییرات زمان‌بندی ضبط ${targetChannel.serviceName} در گوشی ذخیره شد"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    AccessibilityHelper.announce(containerPhoneTimers, msg)
                } else {
                    val newTimer = PhoneScheduledTimer(
                        id = System.currentTimeMillis().toString(),
                        channelName = targetChannel.serviceName,
                        channelId = targetChannel.serviceId,
                        isAudioOnly = isAudioOnly,
                        startHour = startH,
                        startMinute = startM,
                        durationMinutes = dur,
                        endHour = endH,
                        endMinute = endM,
                        repeatMode = repeatMode,
                        autoWake = autoWake,
                        autoPowerOff = autoPowerOff,
                        autoDelete = autoDelete
                    )
                    phoneTimers.add(newTimer)
                    savePhoneTimers()
                    renderPhoneTimers()

                    val msg = "زمان‌بندی ضبط ${targetChannel.serviceName} در گوشی از %02d:%02d تا %02d:%02d ثبت شد".format(startH, startM, endH, endM)
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    AccessibilityHelper.announce(containerPhoneTimers, msg)
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun renderPhoneTimers() {
        if (!isAdded) return
        containerPhoneTimers.removeAllViews()
        if (phoneTimers.isEmpty()) {
            txtNoPhoneTimers.visibility = View.VISIBLE
        } else {
            txtNoPhoneTimers.visibility = View.GONE
            for ((index, t) in phoneTimers.withIndex()) {
                val formatStr = if (t.isAudioOnly) "صوتی" else "تصویری"
                val textStr = "تایمر ${index + 1}: ${t.channelName} ($formatStr) از %02d:%02d تا %02d:%02d (مدت: ${t.durationMinutes} دقیقه)".format(
                    t.startHour, t.startMinute, t.endHour, t.endMinute
                )

                fun confirmDelete() {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("حذف زمان‌بندی گوشی")
                        .setMessage("آیا می‌خواهید $textStr را حذف کنید؟")
                        .setPositiveButton("بله، حذف کن") { _, _ ->
                            phoneTimers.remove(t)
                            savePhoneTimers()
                            renderPhoneTimers()
                            AccessibilityHelper.announce(containerPhoneTimers, "زمان‌بندی گوشی حذف شد")
                        }
                        .setNegativeButton("انصراف", null)
                        .show()
                }

                val btn = MaterialButton(requireContext()).apply {
                    text = textStr
                    contentDescription = "$textStr. برای ویرایش یا حذف کلیک کنید."
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        resources.getDimensionPixelSize(android.R.dimen.app_icon_size) + 16
                    ).apply { setMargins(0, 4, 0, 6) }
                    setBackgroundColor(resources.getColor(R.color.card_bg, null))
                    setTextColor(resources.getColor(R.color.primary, null))
                    textSize = 13f
                    setOnClickListener {
                        showTimerOptionsDialog(
                            title = "مدیریت زمان‌بندی گوشی",
                            timerSummary = textStr,
                            onEdit = { showAddOrEditPhoneTimerDialog(t) },
                            onDelete = { confirmDelete() }
                        )
                    }
                    setOnLongClickListener {
                        confirmDelete()
                        true
                    }
                }
                containerPhoneTimers.addView(btn)
            }
        }
    }

    private fun savePhoneTimers() {
        val file = File(StorageManager.getRecordingsDir(requireContext()), "scheduled_timers.json")
        try {
            val json = Gson().toJson(phoneTimers)
            file.writeText(json, Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    private fun loadPhoneTimers() {
        val file = File(StorageManager.getRecordingsDir(requireContext()), "scheduled_timers.json")
        if (file.exists()) {
            try {
                val json = file.readText(Charsets.UTF_8)
                val type = object : TypeToken<List<PhoneScheduledTimer>>() {}.type
                val loaded: List<PhoneScheduledTimer> = Gson().fromJson(json, type)
                phoneTimers.clear()
                phoneTimers.addAll(loaded)
            } catch (_: Exception) {}
        }
        renderPhoneTimers()
    }

    private fun startPhoneTimerWatcher() {
        if (isPhoneWatcherRunning) return
        isPhoneWatcherRunning = true
        lifecycleScope.launch {
            while (true) {
                val cal = Calendar.getInstance()
                val curH = cal.get(Calendar.HOUR_OF_DAY)
                val curM = cal.get(Calendar.MINUTE)

                for (t in phoneTimers.toList()) {
                    if (!t.isTriggered && t.startHour == curH && t.startMinute == curM) {
                        t.isTriggered = true

                        // Auto wake STB if requested
                        if (t.autoWake && repository.isConnected.value) {
                            try {
                                repository.client.sendCmd(1009, expectReply = false)
                            } catch (_: Exception) {}
                        }

                        val ch = repository.channels.value.find { it.serviceId == t.channelId }
                        if (ch != null) {
                            repository.switchChannel(ch)
                        }
                        isScheduledPhoneRecordingActive = true
                        startPhoneRecording(t.isAudioOnly)
                        val startMsg = "زمان‌بندی ضبط ${t.channelName} در گوشی آغاز شد"
                        TtsHelper.speak(startMsg)
                        if (isAdded) {
                            AccessibilityHelper.announce(txtInstantRecStatus, startMsg)
                        }

                        // Auto stop after duration
                        Handler(Looper.getMainLooper()).postDelayed({
                            stopPhoneRecording()
                            val endMsg = "ضبط زمان‌بندی‌شده ${t.channelName} در گوشی به پایان رسید"
                            TtsHelper.speak(endMsg)

                            // Auto power off STB if requested
                            if (t.autoPowerOff && repository.isConnected.value) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    repository.sendRcuKey(RcuKey.POWER)
                                }
                            }

                            // Auto delete timer if requested
                            if (t.autoDelete) {
                                phoneTimers.remove(t)
                                savePhoneTimers()
                                if (isAdded) {
                                    renderPhoneTimers()
                                }
                            }
                        }, t.durationMinutes * 60 * 1000L)
                    }
                }
                kotlinx.coroutines.delay(20000)
            }
        }
    }

    private fun displayStbPvrFiles(files: List<Map<String, Any>>) {
        containerStbPvrFiles.removeAllViews()
        if (files.isEmpty()) {
            txtNoStbPvrFiles.visibility = View.VISIBLE
        } else {
            txtNoStbPvrFiles.visibility = View.GONE
            for (f in files) {
                val name = f["FileName"]?.toString() ?: "ضبط PVR"
                val size = f["FileSize"]?.toString() ?: ""
                val textStr = "فایل فلش: $name ($size)"

                val btn = MaterialButton(requireContext()).apply {
                    text = textStr
                    contentDescription = textStr
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        resources.getDimensionPixelSize(android.R.dimen.app_icon_size) + 12
                    ).apply { setMargins(0, 4, 0, 6) }
                    setBackgroundColor(resources.getColor(R.color.card_bg, null))
                    setTextColor(resources.getColor(R.color.text_primary, null))
                    textSize = 13f
                    setOnClickListener {
                        Toast.makeText(requireContext(), "فایل ذخیره شده روی فلش مموری رسیور: $name", Toast.LENGTH_LONG).show()
                    }
                }
                containerStbPvrFiles.addView(btn)
            }
        }
    }

    private fun startPhoneRecording(isAudioOnly: Boolean) {
        val ip = repository.connectedIp.value
        val channel = repository.currentChannel.value
        if (ip.isBlank() || channel == null) {
            val msg = "رسیور متصل نیست یا شبکه‌ای انتخاب نشده است"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val file = com.gmscreen.accessible.player.StreamRecorderManager.startRecording(
                requireContext(),
                repository.client,
                ip,
                channel,
                isAudioOnly = isAudioOnly || channel.isRadio
            )
            withContext(Dispatchers.Main) {
                if (file != null) {
                    currentRecordingFile = file
                    isRecordingOnPhone = true
                    lastRecordingWasAudio = isAudioOnly
                    recSeconds = 0

                    if (isAudioOnly) {
                        btnStartPhoneAudioRec.text = "■ توقف ضبط صوتی"
                        btnStartPhoneAudioRec.contentDescription = "در حال ضبط صوتی در حافظه گوشی. برای توقف ضربه بزنید"
                        btnStartPhoneVideoRec.isEnabled = false
                        btnStartPhoneAudioRec.requestFocus()
                        btnStartPhoneAudioRec.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
                    } else {
                        btnStartPhoneVideoRec.text = "■ توقف ضبط تصویری"
                        btnStartPhoneVideoRec.contentDescription = "در حال ضبط تصویری در حافظه گوشی. برای توقف ضربه بزنید"
                        btnStartPhoneAudioRec.isEnabled = false
                        btnStartPhoneVideoRec.requestFocus()
                        btnStartPhoneVideoRec.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
                    }

                    if (isScheduledPhoneRecordingActive) {
                        btnStopPhoneScheduledRec.visibility = View.VISIBLE
                        btnStopPhoneRec.visibility = View.GONE
                    } else {
                        btnStopPhoneRec.visibility = View.VISIBLE
                        btnStopPhoneScheduledRec.visibility = View.GONE
                    }
                    timerHandler.post(timerRunnable)

                    val typeStr = if (isAudioOnly) "صوتی" else "تصویری"
                    val msg = "ضبط $typeStr شبکه ${channel.serviceName} در گوشی آغاز شد"
                    AccessibilityHelper.announce(txtInstantRecStatus, msg)
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                } else {
                    val msg = "خطا در شروع ضبط زنده روی گوشی"
                    AccessibilityHelper.announce(txtInstantRecStatus, msg)
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun stopPhoneRecording() {
        if (!isRecordingOnPhone) return
        isRecordingOnPhone = false
        isScheduledPhoneRecordingActive = false
        timerHandler.removeCallbacks(timerRunnable)

        btnStopPhoneRec.visibility = View.GONE
        btnStopPhoneScheduledRec.visibility = View.GONE

        btnStartPhoneVideoRec.isEnabled = true
        btnStartPhoneAudioRec.isEnabled = true
        updateFormatLabels()

        val activeBtn = if (lastRecordingWasAudio) btnStartPhoneAudioRec else btnStartPhoneVideoRec
        activeBtn.requestFocus()
        activeBtn.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val file = com.gmscreen.accessible.player.StreamRecorderManager.stopRecording()
            withContext(Dispatchers.Main) {
                val fileName = file?.name ?: currentRecordingFile?.name ?: "فایل"
                val sizeMb = String.format(Locale.US, "%.1f", (file?.length() ?: 0L) / (1024f * 1024f))
                txtInstantRecStatus.text = "ضبط متوقف شد. ذخیره در: $fileName ($sizeMb MB)"
                val msg = "ضبط پایان یافت: $fileName به حجم $sizeMb مگابایت"
                AccessibilityHelper.announce(txtInstantRecStatus, msg)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerHandler.removeCallbacks(timerRunnable)
        stbTimerHandler.removeCallbacks(stbTimerRunnable)
    }
}
