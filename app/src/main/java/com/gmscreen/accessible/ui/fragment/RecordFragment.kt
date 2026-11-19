package com.gmscreen.accessible.ui.fragment

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.data.model.Channel
import com.gmscreen.accessible.protocol.RcuKey
import com.gmscreen.accessible.util.AccessibilityHelper
import com.gmscreen.accessible.util.StorageManager
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
    private lateinit var containerStbTimers: LinearLayout
    private lateinit var txtNoStbTimers: TextView

    private lateinit var btnAddPhoneTimer: MaterialButton
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
        val repeatMode: Int = 0
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
        containerStbTimers = view.findViewById(R.id.containerStbTimers)
        txtNoStbTimers = view.findViewById(R.id.txtNoStbTimers)

        btnAddPhoneTimer = view.findViewById(R.id.btnAddPhoneTimer)
        containerPhoneTimers = view.findViewById(R.id.containerPhoneTimers)
        txtNoPhoneTimers = view.findViewById(R.id.txtNoPhoneTimers)

        btnRefreshStbPvrFiles = view.findViewById(R.id.btnRefreshStbPvrFiles)
        containerStbPvrFiles = view.findViewById(R.id.containerStbPvrFiles)
        txtNoStbPvrFiles = view.findViewById(R.id.txtNoStbPvrFiles)

        setupListeners()
        observeData()
        loadPhoneTimers()
        startPhoneTimerWatcher()
        updateFormatLabels()

        return view
    }

    override fun onResume() {
        super.onResume()
        updateFormatLabels()
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
                startPhoneRecording(isAudioOnly = false)
            }
        }

        btnStartPhoneAudioRec.setOnClickListener {
            if (isRecordingOnPhone) {
                stopPhoneRecording()
            } else {
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
                        updateStbRecordButtonUi()
                        val msg = "دستور شروع ضبط روی فلش مموری رسیور ارسال شد"
                        AccessibilityHelper.announce(btnStbToggleRecord, msg)
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    repository.sendRcuKey(RcuKey.STOP)
                    withContext(Dispatchers.Main) {
                        isStbRecording = false
                        updateStbRecordButtonUi()
                        val msg = "دستور توقف ضبط به رسیور ارسال شد"
                        AccessibilityHelper.announce(btnStbToggleRecord, msg)
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnAddStbTimer.setOnClickListener {
            showAddStbTimerDialog()
        }

        btnAddPhoneTimer.setOnClickListener {
            showAddPhoneTimerDialog()
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
            btnStbToggleRecord.text = "توقف ضبط رسیور (در حال حاضر فعال)"
            btnStbToggleRecord.contentDescription = "رسیور در حال ضبط روی فلش است. برای توقف ضربه بزنید."
            btnStbToggleRecord.setBackgroundColor(resources.getColor(R.color.accent_red, null))
            txtStbRecordStatus.text = "وضعیت: رسیور در حال حاضر مشغول ضبط روی فلش مموری است"
            txtStbRecordStatus.setTextColor(resources.getColor(R.color.accent_red, null))
        } else {
            btnStbToggleRecord.text = "شروع ضبط روی فلش رسیور"
            btnStbToggleRecord.contentDescription = "شروع ضبط کانال جاری بر روی حافظه فلش متصل به رسیور"
            btnStbToggleRecord.setBackgroundColor(resources.getColor(R.color.accent_green, null))
            txtStbRecordStatus.text = "وضعیت ضبط رسیور: غیرفعال"
            txtStbRecordStatus.setTextColor(resources.getColor(R.color.text_secondary, null))
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.stbTimers.collect { timers ->
                displayStbTimers(timers)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repository.pvrFiles.collect { files ->
                displayStbPvrFiles(files)
            }
        }
    }

    private fun showAccessibleHourPicker(title: String, onHourSelected: (Int) -> Unit) {
        val hours = Array(24) { h ->
            val period = if (h < 12) "صبح" else if (h == 12) "ظهر" else if (h < 17) "بعدازظهر" else if (h < 20) "عصر" else "شب"
            val h12 = if (h == 0) 12 else if (h > 12) h - 12 else h
            String.format(Locale.US, "ساعت %02d (%d %s)", h, h12, period)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setItems(hours) { _, which ->
                onHourSelected(which)
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

    private fun showAddStbTimerDialog(preselectedChannel: Channel? = null) {
        val channels = repository.channels.value
        if (channels.isEmpty()) {
            Toast.makeText(requireContext(), "ابتدا باید به رسیور متصل شوید تا کانال‌ها دریافت شوند", Toast.LENGTH_SHORT).show()
            return
        }

        var targetChannel = preselectedChannel ?: repository.currentChannel.value ?: channels.first()

        val cal = Calendar.getInstance()
        var startH = (cal.get(Calendar.HOUR_OF_DAY) + if (cal.get(Calendar.MINUTE) >= 55) 1 else 0) % 24
        var startM = ((cal.get(Calendar.MINUTE) / 5) * 5 + 5) % 60
        var endH = (startH + 1) % 24
        var endM = startM
        var repeatMode = 0 // 0: یکبار, 1: روزانه, 2: هفتگی
        var autoWake = true
        var autoPowerOff = false
        var autoDelete = true

        val repeatTitles = arrayOf("یک‌بار (پیش‌فرض)", "روزانه (هر روز)", "هفتگی (همین روز هر هفته)")

        fun openDialog() {
            val items = arrayOf(
                "۱. شبکه انتخابی: ${targetChannel.serviceName} (برای تغییر کلیک کنید)",
                "۲. ساعت شروع ضبط: %02d (کلیک برای انتخاب ساعت)".format(startH),
                "۳. دقیقه شروع ضبط: %02d (کلیک برای انتخاب دقیقه)".format(startM),
                "۴. ساعت پایان ضبط: %02d (کلیک برای انتخاب ساعت)".format(endH),
                "۵. دقیقه پایان ضبط: %02d (کلیک برای انتخاب دقیقه)".format(endM),
                "۶. تکرار زمان‌بندی: ${repeatTitles[repeatMode]}",
                "۷. روشن شدن خودکار رسیور از حالت آماده‌به‌کار: ${if (autoWake) "فعال" else "غیرفعال"}",
                "۸. خاموش شدن خودکار رسیور پس از اتمام ضبط: ${if (autoPowerOff) "فعال" else "غیرفعال"}",
                "۹. حذف خودکار زمان‌بندی از رسیور پس از اتمام: ${if (autoDelete) "فعال" else "غیرفعال"}"
            )

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("افزودن زمان‌بندی ضبط روی فلش رسیور")
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> showChannelPicker("انتخاب شبکه برای ضبط رسیور", targetChannel) { chosen ->
                            targetChannel = chosen
                            openDialog()
                        }
                        1 -> showAccessibleHourPicker("ساعت شروع ضبط رسیور") { h ->
                            startH = h
                            endH = (startH + 1) % 24
                            openDialog()
                        }
                        2 -> showAccessibleMinutePicker("دقیقه شروع ضبط رسیور") { m ->
                            startM = m
                            endM = startM
                            openDialog()
                        }
                        3 -> showAccessibleHourPicker("ساعت پایان ضبط رسیور") { h ->
                            endH = h
                            openDialog()
                        }
                        4 -> showAccessibleMinutePicker("دقیقه پایان ضبط رسیور") { m ->
                            endM = m
                            openDialog()
                        }
                        5 -> {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("انتخاب نحوه تکرار زمان‌بندی")
                                .setItems(repeatTitles) { _, rIdx ->
                                    repeatMode = rIdx
                                    openDialog()
                                }
                                .setNegativeButton("انصراف") { _, _ -> openDialog() }
                                .show()
                        }
                        6 -> {
                            autoWake = !autoWake
                            openDialog()
                        }
                        7 -> {
                            autoPowerOff = !autoPowerOff
                            openDialog()
                        }
                        8 -> {
                            autoDelete = !autoDelete
                            openDialog()
                        }
                    }
                }
                .setPositiveButton("ثبت زمان‌بندی در رسیور") { _, _ ->
                    confirmAndSendStbTimer(targetChannel, startH, startM, endH, endM, repeatMode, autoWake, autoPowerOff, autoDelete)
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        openDialog()
    }

    private fun showChannelPicker(title: String, currentChannel: Channel, onChannelSelected: (Channel) -> Unit) {
        val channels = repository.channels.value
        val items = channels.mapIndexed { i, ch -> "${i + 1}. ${ch.serviceName}" }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setItems(items) { dialog, which ->
                val chosen = channels.getOrNull(which)
                if (chosen != null) onChannelSelected(chosen)
                dialog.dismiss()
            }
            .setNegativeButton("انصراف", null)
            .show()
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
            "TimerMonth" to mon.toString(),
            "TimerDay" to day.toString(),
            "TimerStartHour" to sh.toString(),
            "TimerStartMin" to sm.toString(),
            "TimerEndHour" to eh.toString(),
            "TimerEndMin" to em.toString(),
            "TimerRepeat" to repeatMode.toString(),
            "TimerStatus" to "1",
            "TimerEventID" to "0",
            "auto_wake" to if (autoWake) "1" else "0",
            "auto_poweroff" to if (autoPowerOff) "1" else "0",
            "auto_delete" to if (autoDelete) "1" else "0"
        )

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = repository.addStbTimer(timerPayload)
            withContext(Dispatchers.Main) {
                if (ok) {
                    val msg = "زمان‌بندی ضبط ${channel.serviceName} از %02d:%02d تا %02d:%02d روی رسیور ثبت شد".format(sh, sm, eh, em)
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    AccessibilityHelper.announce(containerStbTimers, msg)
                } else {
                    Toast.makeText(requireContext(), "خطا در ثبت تایمر روی رسیور", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun displayStbTimers(timers: List<Map<String, Any>>) {
        containerStbTimers.removeAllViews()
        if (timers.isEmpty()) {
            txtNoStbTimers.visibility = View.VISIBLE
        } else {
            txtNoStbTimers.visibility = View.GONE
            for ((index, t) in timers.withIndex()) {
                val progId = t["TimerProgramId"]?.toString() ?: ""
                val ch = repository.channels.value.find { it.serviceId == progId }
                val chName = ch?.serviceName ?: "کانال ناشناس ($progId)"
                val sh = t["TimerStartHour"]?.toString() ?: "0"
                val sm = t["TimerStartMin"]?.toString() ?: "0"
                val eh = t["TimerEndHour"]?.toString() ?: "0"
                val em = t["TimerEndMin"]?.toString() ?: "0"
                val timerText = "تایمر ${index + 1}: $chName از $sh:$sm تا $eh:$em روی فلش"

                val btn = MaterialButton(requireContext()).apply {
                    text = timerText
                    contentDescription = "$timerText. برای حذف تایمر کلیک کنید."
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        resources.getDimensionPixelSize(android.R.dimen.app_icon_size) + 16
                    ).apply { setMargins(0, 4, 0, 6) }
                    setBackgroundColor(resources.getColor(R.color.card_bg, null))
                    setTextColor(resources.getColor(R.color.accent_green, null))
                    textSize = 13f
                    setOnClickListener {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("حذف تایمر رسیور")
                            .setMessage("آیا می‌خواهید $timerText را از حافظه رسیور حذف کنید؟")
                            .setPositiveButton("بله، حذف کن") { _, _ ->
                                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                                    repository.deleteStbTimer(t)
                                }
                            }
                            .setNegativeButton("انصراف", null)
                            .show()
                    }
                }
                containerStbTimers.addView(btn)
            }
        }
    }

    private fun showAddPhoneTimerDialog(preselectedChannel: Channel? = null) {
        val channels = repository.channels.value
        if (channels.isEmpty()) {
            Toast.makeText(requireContext(), "ابتدا باید به رسیور متصل شوید تا کانال‌ها دریافت شوند", Toast.LENGTH_SHORT).show()
            return
        }

        var targetChannel = preselectedChannel ?: repository.currentChannel.value ?: channels.first()

        val cal = Calendar.getInstance()
        var startH = (cal.get(Calendar.HOUR_OF_DAY) + if (cal.get(Calendar.MINUTE) >= 55) 1 else 0) % 24
        var startM = ((cal.get(Calendar.MINUTE) / 5) * 5 + 5) % 60
        var endH = (startH + 1) % 24
        var endM = startM
        var isAudioOnly = targetChannel.isRadio
        var repeatMode = 0 // 0: یکبار, 1: روزانه, 2: هفتگی

        val repeatTitles = arrayOf("یک‌بار (پیش‌فرض)", "روزانه (هر روز)", "هفتگی (همین روز هر هفته)")

        fun openDialog() {
            var diffMins = (endH * 60 + endM) - (startH * 60 + startM)
            if (diffMins <= 0) diffMins += 24 * 60

            val formatStr = if (isAudioOnly) "صوتی (${appConfig.audioRecFormat.uppercase()})" else "تصویری (${appConfig.videoRecFormat.uppercase()})"
            val items = arrayOf(
                "۱. شبکه: ${targetChannel.serviceName} (برای تغییر کلیک کنید)",
                "۲. ساعت شروع ضبط گوشی: %02d (کلیک برای انتخاب ساعت)".format(startH),
                "۳. دقیقه شروع ضبط گوشی: %02d (کلیک برای انتخاب دقیقه)".format(startM),
                "۴. ساعت پایان ضبط گوشی: %02d (کلیک برای انتخاب ساعت)".format(endH),
                "۵. دقیقه پایان ضبط گوشی: %02d (کلیک برای انتخاب دقیقه)".format(endM),
                "۶. نوع و فرمت فایل ضبط: $formatStr (مدت: $diffMins دقیقه)",
                "۷. تکرار زمان‌بندی: ${repeatTitles[repeatMode]}"
            )

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("افزودن زمان‌بندی ضبط در گوشی")
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> showChannelPicker("انتخاب شبکه برای ضبط گوشی", targetChannel) { chosen ->
                            targetChannel = chosen
                            if (targetChannel.isRadio) isAudioOnly = true
                            openDialog()
                        }
                        1 -> showAccessibleHourPicker("ساعت شروع ضبط گوشی") { h ->
                            startH = h
                            endH = (startH + 1) % 24
                            openDialog()
                        }
                        2 -> showAccessibleMinutePicker("دقیقه شروع ضبط گوشی") { m ->
                            startM = m
                            endM = startM
                            openDialog()
                        }
                        3 -> showAccessibleHourPicker("ساعت پایان ضبط گوشی") { h ->
                            endH = h
                            openDialog()
                        }
                        4 -> showAccessibleMinutePicker("دقیقه پایان ضبط گوشی") { m ->
                            endM = m
                            openDialog()
                        }
                        5 -> {
                            isAudioOnly = !isAudioOnly
                            openDialog()
                        }
                        6 -> {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("انتخاب نحوه تکرار زمان‌بندی گوشی")
                                .setItems(repeatTitles) { _, rIdx ->
                                    repeatMode = rIdx
                                    openDialog()
                                }
                                .setNegativeButton("انصراف") { _, _ -> openDialog() }
                                .show()
                        }
                    }
                }
                .setPositiveButton("ثبت زمان‌بندی در گوشی") { _, _ ->
                    var dur = (endH * 60 + endM) - (startH * 60 + startM)
                    if (dur <= 0) dur += 24 * 60

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
                        repeatMode = repeatMode
                    )
                    phoneTimers.add(newTimer)
                    savePhoneTimers()
                    renderPhoneTimers()

                    val msg = "زمان‌بندی ضبط ${targetChannel.serviceName} در گوشی از %02d:%02d تا %02d:%02d ثبت شد".format(startH, startM, endH, endM)
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    AccessibilityHelper.announce(containerPhoneTimers, msg)
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        openDialog()
    }

    private fun renderPhoneTimers() {
        containerPhoneTimers.removeAllViews()
        if (phoneTimers.isEmpty()) {
            txtNoPhoneTimers.visibility = View.VISIBLE
        } else {
            txtNoPhoneTimers.visibility = View.GONE
            for (t in phoneTimers) {
                val formatStr = if (t.isAudioOnly) "صوتی" else "تصویری"
                val textStr = "زمان‌بندی: ${t.channelName} ($formatStr) از %02d:%02d تا %02d:%02d (مدت: ${t.durationMinutes} دقیقه)".format(t.startHour, t.startMinute, t.endHour, t.endMinute)

                val btn = MaterialButton(requireContext()).apply {
                    text = textStr
                    contentDescription = "$textStr. برای حذف ضربه بزنید."
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        resources.getDimensionPixelSize(android.R.dimen.app_icon_size) + 16
                    ).apply { setMargins(0, 4, 0, 6) }
                    setBackgroundColor(resources.getColor(R.color.card_bg, null))
                    setTextColor(resources.getColor(R.color.primary, null))
                    textSize = 13f
                    setOnClickListener {
                        phoneTimers.remove(t)
                        savePhoneTimers()
                        renderPhoneTimers()
                        AccessibilityHelper.announce(containerPhoneTimers, "زمان‌بندی گوشی حذف شد")
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
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                val cal = Calendar.getInstance()
                val curH = cal.get(Calendar.HOUR_OF_DAY)
                val curM = cal.get(Calendar.MINUTE)

                for (t in phoneTimers.toList()) {
                    if (!t.isTriggered && t.startHour == curH && t.startMinute == curM) {
                        t.isTriggered = true
                        val ch = repository.channels.value.find { it.serviceId == t.channelId }
                        if (ch != null) {
                            repository.switchChannel(ch)
                        }
                        startPhoneRecording(t.isAudioOnly)
                        AccessibilityHelper.announce(txtInstantRecStatus, "زمان‌بندی ضبط ${t.channelName} در گوشی آغاز شد")

                        // Auto stop after duration
                        Handler(Looper.getMainLooper()).postDelayed({
                            stopPhoneRecording()
                            phoneTimers.remove(t)
                            savePhoneTimers()
                            renderPhoneTimers()
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
        timerHandler.removeCallbacks(timerRunnable)

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
    }
}
