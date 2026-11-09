package com.gmscreen.accessible.ui.fragment

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
import com.gmscreen.accessible.protocol.RcuKey
import com.gmscreen.accessible.util.AccessibilityHelper
import com.gmscreen.accessible.util.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RecordFragment : Fragment() {

    private lateinit var txtPhoneRecStatus: TextView
    private lateinit var btnStartPhoneVideoRec: MaterialButton
    private lateinit var btnStartPhoneAudioRec: MaterialButton
    private lateinit var btnStopPhoneRec: MaterialButton
    private lateinit var containerRecordedFiles: LinearLayout
    private lateinit var txtNoRecordings: TextView

    private lateinit var txtStbRecStatus: TextView
    private lateinit var btnStbInstantRecord: MaterialButton
    private lateinit var btnStbStopRecord: MaterialButton

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
                txtPhoneRecStatus.text = "در حال ضبط روی گوشی… مدت زمان: %02d:%02d (ذخیره در پوشه gm screen android/recordings)".format(mins, secs)
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    private val repository get() = GMScreenApplication.instance.repository
    private val appConfig get() = GMScreenApplication.instance.appConfig

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_record, container, false)

        txtPhoneRecStatus = view.findViewById(R.id.txtPhoneRecStatus)
        btnStartPhoneVideoRec = view.findViewById(R.id.btnStartPhoneVideoRec)
        btnStartPhoneAudioRec = view.findViewById(R.id.btnStartPhoneAudioRec)
        btnStopPhoneRec = view.findViewById(R.id.btnStopPhoneRec)
        containerRecordedFiles = view.findViewById(R.id.containerRecordedFiles)
        txtNoRecordings = view.findViewById(R.id.txtNoRecordings)

        txtStbRecStatus = view.findViewById(R.id.txtStbRecStatus)
        btnStbInstantRecord = view.findViewById(R.id.btnStbInstantRecord)
        btnStbStopRecord = view.findViewById(R.id.btnStbStopRecord)

        setupListeners()
        loadRecordedFiles()

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
                AccessibilityHelper.announce(txtStbRecStatus, msg)
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                repository.sendRcuKey(RcuKey.RECORD)
                withContext(Dispatchers.Main) {
                    txtStbRecStatus.text = "دستور شروع ضبط آنی به رسیور ارسال شد (ذخیره روی فلش مموری رسیور)"
                    AccessibilityHelper.announce(txtStbRecStatus, "دستور شروع ضبط روی فلش رسیور ارسال شد")
                    Toast.makeText(requireContext(), "دستور ضبط رسیور ارسال شد", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnStbStopRecord.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                repository.sendRcuKey(RcuKey.STOP)
                withContext(Dispatchers.Main) {
                    txtStbRecStatus.text = "دستور توقف ضبط به رسیور ارسال شد"
                    AccessibilityHelper.announce(txtStbRecStatus, "دستور توقف ضبط به رسیور ارسال شد")
                    Toast.makeText(requireContext(), "دستور توقف ضبط ارسال شد", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startPhoneRecording(isAudioOnly: Boolean) {
        val currentChannelName = "Live_Channel"
        val file = StorageManager.generateRecordingFile(requireContext(), currentChannelName, isAudioOnly)
        currentRecordingFile = file
        isRecordingOnPhone = true
        recSeconds = 0

        btnStartPhoneVideoRec.visibility = View.GONE
        btnStartPhoneAudioRec.visibility = View.GONE
        btnStopPhoneRec.visibility = View.VISIBLE

        timerHandler.post(timerRunnable)

        val msg = if (isAudioOnly) "ضبط صوتی گوشی آغاز شد" else "ضبط تصویری گوشی آغاز شد"
        AccessibilityHelper.announce(txtPhoneRecStatus, msg)
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun stopPhoneRecording() {
        isRecordingOnPhone = false
        timerHandler.removeCallbacks(timerRunnable)

        btnStartPhoneVideoRec.visibility = View.VISIBLE
        btnStartPhoneAudioRec.visibility = View.VISIBLE
        btnStopPhoneRec.visibility = View.GONE

        val fileName = currentRecordingFile?.name ?: "فایل"
        txtPhoneRecStatus.text = "ضبط متوقف شد. فایل ذخیره شد در پوشه: gm screen android/recordings"
        val msg = "ضبط متوقف شد و فایل در حافظه ذخیره گردید: $fileName"
        AccessibilityHelper.announce(txtPhoneRecStatus, msg)
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
                    contentDescription = "فایل ضبط شده ${file.name}. برای پخش یا مدیریت ضربه بزنید"
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        resources.getDimensionPixelSize(android.R.dimen.app_icon_size) + 12
                    ).apply {
                        setMargins(0, 4, 0, 6)
                    }
                    setBackgroundColor(resources.getColor(R.color.card_bg, null))
                    setTextColor(resources.getColor(R.color.text_primary, null))
                    textSize = 14f
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
                        AccessibilityHelper.announce(txtPhoneRecStatus, "فایل حذف شد")
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
