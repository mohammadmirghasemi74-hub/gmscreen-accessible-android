package com.gmscreen.accessible.ui.fragment

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private lateinit var btnStbInstantRecord: MaterialButton
    private lateinit var btnStbStopRecord: MaterialButton

    private lateinit var btnAddStbTimer: MaterialButton
    private lateinit var containerStbTimers: LinearLayout
    private lateinit var txtNoStbTimers: TextView

    private lateinit var btnAddPhoneTimer: MaterialButton
    private lateinit var containerPhoneTimers: LinearLayout
    private lateinit var txtNoPhoneTimers: TextView

    private lateinit var containerRecordedFiles: LinearLayout
    private lateinit var txtNoRecordings: TextView

    private lateinit var btnRefreshStbPvrFiles: MaterialButton
    private lateinit var containerStbPvrFiles: LinearLayout
    private lateinit var txtNoStbPvrFiles: TextView

    private var isRecordingOnPhone = false
    private var currentRecordingFile: File? = null
    private var recSeconds = 0
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRecordingOnPhone) {
                recSeconds++
                val mins = recSeconds / 60
                val secs = recSeconds % 60
                txtInstantRecStatus.text = "در حال ضبط روی گوشی… مدت زمان: %02d:%02d".format(mins, secs)
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
        var isTriggered: Boolean = false
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
        btnStbInstantRecord = view.findViewById(R.id.btnStbInstantRecord)
        btnStbStopRecord = view.findViewById(R.id.btnStbStopRecord)

        btnAddStbTimer = view.findViewById(R.id.btnAddStbTimer)
        containerStbTimers = view.findViewById(R.id.containerStbTimers)
        txtNoStbTimers = view.findViewById(R.id.txtNoStbTimers)

        btnAddPhoneTimer = view.findViewById(R.id.btnAddPhoneTimer)
        containerPhoneTimers = view.findViewById(R.id.containerPhoneTimers)
        txtNoPhoneTimers = view.findViewById(R.id.txtNoPhoneTimers)

        containerRecordedFiles = view.findViewById(R.id.containerRecordedFiles)
        txtNoRecordings = view.findViewById(R.id.txtNoRecordings)

        btnRefreshStbPvrFiles = view.findViewById(R.id.btnRefreshStbPvrFiles)
        containerStbPvrFiles = view.findViewById(R.id.containerStbPvrFiles)
        txtNoStbPvrFiles = view.findViewById(R.id.txtNoStbPvrFiles)

        setupListeners()
        observeData()
        loadPhoneTimers()
        loadRecordedFiles()
        startPhoneTimerWatcher()

        return view
    }

    private fun setupListeners() {
        btnStartPhoneVideoRec.setOnClickListener {
            startPhoneRecording(isAudioOnly = false)
        }

        btnStartPhoneAudioRec.setOnClickListener {
            startPhoneRecording(isAudioOnly = true)
        }

        btnStopPhoneRec.setOnClickListener {
            stopPhoneRecording()
        }

        btnStbInstantRecord.setOnClickListener {
            if (repository.connectedIp.value.isBlank()) {
                val msg = "رسیور متصل نیست. ابتدا در سربرگ اتصال وصل شوید."
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                AccessibilityHelper.announce(txtInstantRecStatus, msg)
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                repository.sendRcuKey(RcuKey.RECORD)
                withContext(Dispatchers.Main) {
                    val msg = "دستور شروع ضبط روی فلش مموری رسیور ارسال شد"
                    txtInstantRecStatus.text = msg
                    AccessibilityHelper.announce(txtInstantRecStatus, msg)
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnStbStopRecord.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                repository.sendRcuKey(RcuKey.STOP)
                withContext(Dispatchers.Main) {
                    val msg = "دستور توقف ضبط به رسیور ارسال شد"
                    txtInstantRecStatus.text = msg
                    AccessibilityHelper.announce(txtInstantRecStatus, msg)
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
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

    private fun showAddStbTimerDialog(preselectedChannel: Channel? = null) {
        val channels = repository.channels.value
        if (channels.isEmpty()) {
            Toast.makeText(requireContext(), "ابتدا باید به رسیور متصل شوید تا کانال‌ها دریافت شوند", Toast.LENGTH_SHORT).show()
            return
        }

        val channelNames = channels.map { it.serviceName }.toTypedArray()
        var selectedChannelIdx = 0
        if (preselectedChannel != null) {
            val idx = channels.indexOfFirst { it.serviceId == preselectedChannel.serviceId }
            if (idx >= 0) selectedChannelIdx = idx
        }

        val cal = Calendar.getInstance()
        var startH = cal.get(Calendar.HOUR_OF_DAY)
        var startM = cal.get(Calendar.MINUTE) + 2
        var endH = (startH + 1) % 24
        var endM = startM

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("انتخاب کانال برای زمان‌بندی رسیور")
            .setSingleChoiceItems(channelNames, selectedChannelIdx) { dialog, which ->
                selectedChannelIdx = which
                dialog.dismiss()

                // Pick Start Time
                TimePickerDialog(requireContext(), { _, h, m ->
                    startH = h
                    startM = m
                    // Pick End Time
                    TimePickerDialog(requireContext(), { _, eh, em ->
                        endH = eh
                        endM = em
                        confirmAndSendStbTimer(channels[selectedChannelIdx], startH, startM, endH, endM)
                    }, (startH + 1) % 24, startM, true).apply {
                        setTitle("ساعت پایان ضبط رسیور")
                        show()
                    }
                }, startH, startM, true).apply {
                    setTitle("ساعت شروع ضبط رسیور")
                    show()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun confirmAndSendStbTimer(channel: Channel, sh: Int, sm: Int, eh: Int, em: Int) {
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
            "TimerRepeat" to "0",
            "TimerStatus" to "1",
            "TimerEventID" to "0"
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

        val channelNames = channels.map { it.serviceName }.toTypedArray()
        var selectedChannelIdx = 0
        if (preselectedChannel != null) {
            val idx = channels.indexOfFirst { it.serviceId == preselectedChannel.serviceId }
            if (idx >= 0) selectedChannelIdx = idx
        }

        val cal = Calendar.getInstance()
        var startH = cal.get(Calendar.HOUR_OF_DAY)
        var startM = cal.get(Calendar.MINUTE) + 2

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("انتخاب کانال برای زمان‌بندی گوشی")
            .setSingleChoiceItems(channelNames, selectedChannelIdx) { dialog, which ->
                selectedChannelIdx = which
                dialog.dismiss()

                // Choose format (Video vs Audio)
                val formats = arrayOf("ویدیو (تصویری MP4)", "صدا (فقط صوتی MP3)")
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("فرمت ضبط روی گوشی")
                    .setItems(formats) { _, fIndex ->
                        val isAudioOnly = (fIndex == 1)
                        TimePickerDialog(requireContext(), { _, h, m ->
                            startH = h
                            startM = m

                            val durations = arrayOf("۱۵ دقیقه", "۳۰ دقیقه", "۴۵ دقیقه", "۶۰ دقیقه", "۹۰ دقیقه", "۱۲۰ دقیقه")
                            val durationValues = listOf(15, 30, 45, 60, 90, 120)
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("مدت زمان ضبط گوشی")
                                .setItems(durations) { _, dIndex ->
                                    val dur = durationValues[dIndex]
                                    val targetCh = channels[selectedChannelIdx]
                                    val newTimer = PhoneScheduledTimer(
                                        id = System.currentTimeMillis().toString(),
                                        channelName = targetCh.serviceName,
                                        channelId = targetCh.serviceId,
                                        isAudioOnly = isAudioOnly,
                                        startHour = startH,
                                        startMinute = startM,
                                        durationMinutes = dur
                                    )
                                    phoneTimers.add(newTimer)
                                    savePhoneTimers()
                                    renderPhoneTimers()

                                    val msg = "زمان‌بندی ضبط ${targetCh.serviceName} در ساعت %02d:%02d به مدت $dur دقیقه در گوشی تنظیم شد".format(startH, startM)
                                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                                    AccessibilityHelper.announce(containerPhoneTimers, msg)
                                }
                                .show()

                        }, startH, startM, true).apply {
                            setTitle("ساعت شروع ضبط گوشی")
                            show()
                        }
                    }
                    .show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun renderPhoneTimers() {
        containerPhoneTimers.removeAllViews()
        if (phoneTimers.isEmpty()) {
            txtNoPhoneTimers.visibility = View.VISIBLE
        } else {
            txtNoPhoneTimers.visibility = View.GONE
            for (t in phoneTimers) {
                val formatStr = if (t.isAudioOnly) "صوتی MP3" else "تصویری MP4"
                val textStr = "زمان‌بندی: ${t.channelName} ($formatStr) در %02d:%02d (مدت: ${t.durationMinutes} دقیقه)".format(t.startHour, t.startMinute)

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
        val currentChannelName = repository.currentChannel.value?.serviceName ?: "Live_Stream"
        val file = StorageManager.generateRecordingFile(requireContext(), currentChannelName, isAudioOnly)
        currentRecordingFile = file
        isRecordingOnPhone = true
        recSeconds = 0

        btnStartPhoneVideoRec.visibility = View.GONE
        btnStartPhoneAudioRec.visibility = View.GONE
        btnStopPhoneRec.visibility = View.VISIBLE

        timerHandler.post(timerRunnable)

        val msg = if (isAudioOnly) "ضبط صوتی گوشی آغاز شد" else "ضبط تصویری گوشی آغاز شد"
        AccessibilityHelper.announce(txtInstantRecStatus, msg)
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun stopPhoneRecording() {
        if (!isRecordingOnPhone) return
        isRecordingOnPhone = false
        timerHandler.removeCallbacks(timerRunnable)

        btnStartPhoneVideoRec.visibility = View.VISIBLE
        btnStartPhoneAudioRec.visibility = View.VISIBLE
        btnStopPhoneRec.visibility = View.GONE

        val fileName = currentRecordingFile?.name ?: "فایل"
        txtInstantRecStatus.text = "ضبط متوقف شد. فایل در gm screen android/recordings ذخیره شد"
        val msg = "ضبط پایان یافت: $fileName"
        AccessibilityHelper.announce(txtInstantRecStatus, msg)
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()

        loadRecordedFiles()
    }

    private fun loadRecordedFiles() {
        containerRecordedFiles.removeAllViews()
        val files = StorageManager.listRecordedFiles(requireContext())
        if (files.isEmpty()) {
            txtNoRecordings.visibility = View.VISIBLE
        } else {
            txtNoRecordings.visibility = View.GONE
            for (file in files) {
                val btn = MaterialButton(requireContext()).apply {
                    text = "${file.name} (${file.length() / 1024} KB)"
                    contentDescription = "فایل ${file.name}. برای پخش یا مدیریت ضربه بزنید"
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        resources.getDimensionPixelSize(android.R.dimen.app_icon_size) + 12
                    ).apply { setMargins(0, 4, 0, 6) }
                    setBackgroundColor(resources.getColor(R.color.card_bg, null))
                    setTextColor(resources.getColor(R.color.text_primary, null))
                    textSize = 13f
                    setOnClickListener {
                        showFileOptions(file)
                    }
                }
                containerRecordedFiles.addView(btn)
            }
        }
    }

    private fun showFileOptions(file: File) {
        val options = arrayOf("پخش فایل", "اشتراک‌گذاری فایل", "حذف فایل")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openFile(file)
                    1 -> shareFile(file)
                    2 -> {
                        file.delete()
                        loadRecordedFiles()
                        AccessibilityHelper.announce(txtInstantRecStatus, "فایل حذف شد")
                    }
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, if (file.name.endsWith(".mp3")) "audio/*" else "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "پلیر مناسب در گوشی یافت نشد: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (file.name.endsWith(".mp3")) "audio/*" else "video/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "اشتراک‌گذاری فایل ضبط شده"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "خطا در اشتراک‌گذاری", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerHandler.removeCallbacks(timerRunnable)
    }
}
