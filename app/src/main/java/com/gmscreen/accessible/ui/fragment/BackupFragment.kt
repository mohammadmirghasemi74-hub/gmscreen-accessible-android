package com.gmscreen.accessible.ui.fragment

import android.content.Intent
import android.os.Bundle
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BackupFragment : Fragment() {

    private lateinit var txtBackupStatus: TextView
    private lateinit var btnBackupPhone: MaterialButton
    private lateinit var btnExportM3u: MaterialButton
    private lateinit var btnBackupStbUsb: MaterialButton
    private lateinit var containerBackupFiles: LinearLayout
    private lateinit var txtNoBackups: TextView

    private val repository get() = GMScreenApplication.instance.repository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_backup, container, false)

        txtBackupStatus = view.findViewById(R.id.txtBackupStatus)
        btnBackupPhone = view.findViewById(R.id.btnBackupPhone)
        btnExportM3u = view.findViewById(R.id.btnExportM3u)
        btnBackupStbUsb = view.findViewById(R.id.btnBackupStbUsb)
        containerBackupFiles = view.findViewById(R.id.containerBackupFiles)
        txtNoBackups = view.findViewById(R.id.txtNoBackups)

        setupListeners()
        loadBackupsList()

        return view
    }

    private fun setupListeners() {
        btnBackupPhone.setOnClickListener {
            val channels = repository.channels.value
            if (channels.isEmpty()) {
                val msg = "لیست کانال‌ها خالی است. ابتدا به رسیور متصل شوید تا کانال‌ها دریافت شوند."
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                AccessibilityHelper.announce(txtBackupStatus, msg)
                return@setOnClickListener
            }

            val model = repository.deviceModel.value
            val file = StorageManager.saveBackupChannels(requireContext(), channels, model)
            val msg = "پشتیبان‌گیری از ${channels.size} کانال در فایل ${file.name} با موفقیت انجام شد."
            txtBackupStatus.text = msg
            AccessibilityHelper.announce(txtBackupStatus, msg)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            loadBackupsList()
        }

        btnExportM3u.setOnClickListener {
            val channels = repository.channels.value
            val ip = repository.connectedIp.value
            if (channels.isEmpty() || ip.isBlank()) {
                val msg = "برای ساخت پلی‌لیست، اتصال به رسیور و دریافت کانال‌ها الزامی است."
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                AccessibilityHelper.announce(txtBackupStatus, msg)
                return@setOnClickListener
            }

            val file = StorageManager.exportM3uPlaylist(requireContext(), channels, ip)
            val msg = "پلی‌لیست M3U در مسیر gm screen android/playlists ذخیره شد."
            txtBackupStatus.text = msg
            AccessibilityHelper.announce(txtBackupStatus, msg)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }

        btnBackupStbUsb.setOnClickListener {
            if (repository.connectedIp.value.isBlank()) {
                val msg = "رسیور متصل نیست. ابتدا به رسیور متصل شوید."
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                AccessibilityHelper.announce(txtBackupStatus, msg)
                return@setOnClickListener
            }

            // Trigger STB Hardware Backup Sequence (USB + SAT + 1114)
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                repository.sendRcuKey(RcuKey.USB)
                delay(600)
                repository.sendRcuKey(RcuKey.SATELLITE)
                delay(600)
                repository.sendRcuKey(RcuKey.KEY_1)
                delay(200)
                repository.sendRcuKey(RcuKey.KEY_1)
                delay(200)
                repository.sendRcuKey(RcuKey.KEY_1)
                delay(200)
                repository.sendRcuKey(RcuKey.KEY_4)
                withContext(Dispatchers.Main) {
                    val msg = "میانبر پشتیبان‌گیری سخت‌افزاری به رسیور ارسال شد. فایل SDX در فلش مموری رسیور ذخیره می‌شود."
                    txtBackupStatus.text = msg
                    AccessibilityHelper.announce(txtBackupStatus, msg)
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadBackupsList() {
        containerBackupFiles.removeAllViews()
        val files = StorageManager.listBackupFiles(requireContext())
        if (files.isEmpty()) {
            txtNoBackups.visibility = View.VISIBLE
        } else {
            txtNoBackups.visibility = View.GONE
            for (file in files) {
                val btn = MaterialButton(requireContext()).apply {
                    text = "${file.name} (${file.length() / 1024} KB)"
                    contentDescription = "نسخه پشتیبان ${file.name}. برای گزینه‌ها ضربه بزنید"
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
                        showBackupOptions(file)
                    }
                }
                containerBackupFiles.addView(btn)
            }
        }
    }

    private fun showBackupOptions(file: File) {
        val options = arrayOf("بازیابی کانال‌ها از این پشتیبان", "اشتراک‌گذاری فایل بکاپ", "حذف فایل")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> restoreFromBackup(file)
                    1 -> shareFile(file)
                    2 -> {
                        file.delete()
                        loadBackupsList()
                        AccessibilityHelper.announce(txtBackupStatus, "نسخه پشتیبان حذف شد")
                    }
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun restoreFromBackup(file: File) {
        val channels = StorageManager.loadBackupChannels(file)
        if (!channels.isNullOrEmpty()) {
            val msg = "${channels.size} کانال از فایل بکاپ با موفقیت بازیابی شد."
            txtBackupStatus.text = msg
            AccessibilityHelper.announce(txtBackupStatus, msg)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        } else {
            val err = "خطا در خواندن فایل بکاپ"
            Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "اشتراک‌گذاری نسخه پشتیبان"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "خطا در اشتراک‌گذاری", Toast.LENGTH_SHORT).show()
        }
    }
}
