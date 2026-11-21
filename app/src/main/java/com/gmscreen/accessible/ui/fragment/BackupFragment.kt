package com.gmscreen.accessible.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.data.model.Channel
import com.gmscreen.accessible.protocol.RcuKey
import com.gmscreen.accessible.util.AccessibilityHelper
import com.gmscreen.accessible.util.AppConfig
import com.gmscreen.accessible.util.StorageManager
import com.gmscreen.accessible.util.TtsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class BackupFragment : Fragment() {

    private lateinit var txtBackupDeviceInfo: TextView
    private lateinit var btnSelectBackupMethod: MaterialButton
    private lateinit var btnBackupStbUsb: MaterialButton
    private lateinit var txtStatus: TextView
    private lateinit var btnExportDocx: MaterialButton
    private lateinit var btnOpenPlaylistCreator: MaterialButton
    private lateinit var btnManagePlaylists: MaterialButton
    private lateinit var btnManageBackups: MaterialButton
    private lateinit var btnBackupPhone: MaterialButton

    private val repository get() = GMScreenApplication.instance.repository
    private val appConfig by lazy { AppConfig(requireContext()) }

    private val methodKeys = listOf("auto", "ali_standard", "starsat_classic", "gx_mini", "universal_menu")
    private val methodLabels = arrayOf(
        "۱. تشخیص خودکار هوشمند (توصیه‌شده)",
        "۲. رسیورهای مدیواستار و استارست سری جدید (USB + SAT + 1114)",
        "۳. رسیورهای استارست هایپر و ژئانت کلاسیک (USB + دکمه SAT)",
        "۴. رسیورهای چیپست GX و مدل‌های مینی (USB + کلید قرمز)",
        "۵. منوی همگانی رسیورها (Menu -> Right -> Down -> OK)"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_backup, container, false)

        txtBackupDeviceInfo = view.findViewById(R.id.txtBackupDeviceInfo)
        btnSelectBackupMethod = view.findViewById(R.id.btnSelectBackupMethod)
        btnBackupStbUsb = view.findViewById(R.id.btnBackupStbUsb)
        txtStatus = view.findViewById(R.id.txtStatus)
        btnExportDocx = view.findViewById(R.id.btnExportDocx)
        btnOpenPlaylistCreator = view.findViewById(R.id.btnOpenPlaylistCreator)
        btnManagePlaylists = view.findViewById(R.id.btnManagePlaylists)
        btnManageBackups = view.findViewById(R.id.btnManageBackups)
        btnBackupPhone = view.findViewById(R.id.btnBackupPhone)

        setupUI()
        observeData()

        return view
    }

    private fun detectStbFamily(modelName: String): String {
        val model = modelName.lowercase()
        return when {
            listOf("gx", "mini", "6605", "6621", "6628").any { it in model } -> "gx_mini"
            listOf("hyper", "2000hd", "8800", "geant").any { it in model } -> "starsat_classic"
            else -> "ali_standard"
        }
    }

    private fun updateDeviceInfo() {
        val ip = repository.connectedIp.value
        val model = repository.deviceModel.value.ifBlank { "رسیور متصل" }
        if (ip.isNotBlank()) {
            val fam = detectStbFamily(model)
            val famNames = mapOf(
                "ali_standard" to "مدیواستار و استارست سری جدید (ALi)",
                "starsat_classic" to "استارست هایپر و ژئانت کلاسیک",
                "gx_mini" to "پردازنده‌های GX / سری مینی",
                "universal_menu" to "منوی همگانی"
            )
            val famStr = famNames[fam] ?: "ALi Standard"
            txtBackupDeviceInfo.text = "مدل دستگاه متصل: $model ($ip) - ساختار شناسایی‌شده: $famStr"
        } else {
            txtBackupDeviceInfo.text = "مدل رسیور متصل: نامشخص (اتصال برقرار نیست)"
        }
    }

    private fun updateMethodButtonText() {
        val curKey = appConfig.backupMethod
        val idx = methodKeys.indexOf(curKey).takeIf { it >= 0 } ?: 0
        btnSelectBackupMethod.text = methodLabels[idx]
        btnSelectBackupMethod.contentDescription = "روش بکاپ‌گیری سخت‌افزاری: ${methodLabels[idx]}"
    }

    private fun setupUI() {
        updateDeviceInfo()
        updateMethodButtonText()

        // 1. Select hardware backup method
        btnSelectBackupMethod.setOnClickListener {
            val curKey = appConfig.backupMethod
            val curIdx = methodKeys.indexOf(curKey).takeIf { it >= 0 } ?: 0
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("انتخاب دستور سخت‌افزاری پشتیبان‌گیری")
                .setSingleChoiceItems(methodLabels, curIdx) { dialog, which ->
                    appConfig.backupMethod = methodKeys[which]
                    updateMethodButtonText()
                    AccessibilityHelper.announce(btnSelectBackupMethod, "روش بکاپ‌گیری تنظیم شد: ${methodLabels[which]}")
                    dialog.dismiss()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        // 2. Hardware channel backup to STB USB
        btnBackupStbUsb.setOnClickListener {
            val ip = repository.connectedIp.value
            if (ip.isBlank()) {
                val err = "ابتدا به رسیور متصل شوید."
                AccessibilityHelper.announce(btnBackupStbUsb, err)
                Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val curMethod = appConfig.backupMethod
            val family = if (curMethod == "auto") {
                detectStbFamily(repository.deviceModel.value)
            } else {
                curMethod
            }

            val startingMsg = "در حال ارسال دستور هوشمند تهیه فایل پشتیبان بر روی فلش مموری رسیور..."
            txtStatus.text = startingMsg
            AccessibilityHelper.announce(btnBackupStbUsb, startingMsg)
            Toast.makeText(requireContext(), startingMsg, Toast.LENGTH_SHORT).show()

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    when (family) {
                        "ali_standard" -> {
                            // ALi M2661 / MediaStar 4030 / StarSat Extreme (USB + SAT + 1114)
                            repository.sendRcuKey(RcuKey.USB)
                            delay(1500)
                            repository.sendRcuKey(RcuKey.SATELLITE)
                            delay(400)
                            repository.sendRcuKey(RcuKey.KEY_1)
                            delay(300)
                            repository.sendRcuKey(RcuKey.KEY_1)
                            delay(300)
                            repository.sendRcuKey(RcuKey.KEY_1)
                            delay(300)
                            repository.sendRcuKey(RcuKey.KEY_4)
                            delay(3000)
                            repository.sendRcuKey(RcuKey.EXIT)
                            delay(500)
                            repository.sendRcuKey(RcuKey.EXIT)
                            withContext(Dispatchers.Main) {
                                val doneMsg = "دستور بکاپ‌گیری با فرمت ALi به رسیور ارسال شد. فایل پشتیبان با پسوند sdx روی فلش مموری ایجاد شد."
                                txtStatus.text = doneMsg
                                AccessibilityHelper.announce(btnBackupStbUsb, doneMsg)
                                Toast.makeText(requireContext(), doneMsg, Toast.LENGTH_LONG).show()
                            }
                        }
                        "starsat_classic" -> {
                            // StarSat 2000 Hyper / Geant (USB + SAT -> OK)
                            repository.sendRcuKey(RcuKey.USB)
                            delay(1500)
                            repository.sendRcuKey(RcuKey.SATELLITE)
                            delay(500)
                            repository.sendRcuKey(RcuKey.OK)
                            delay(3000)
                            repository.sendRcuKey(RcuKey.EXIT)
                            delay(500)
                            repository.sendRcuKey(RcuKey.EXIT)
                            withContext(Dispatchers.Main) {
                                val doneMsg = "دستور بکاپ‌گیری هایپر و کلاسیک به رسیور ارسال شد."
                                txtStatus.text = doneMsg
                                AccessibilityHelper.announce(btnBackupStbUsb, doneMsg)
                                Toast.makeText(requireContext(), doneMsg, Toast.LENGTH_LONG).show()
                            }
                        }
                        "gx_mini" -> {
                            // GX Chipsets (USB + RED -> OK)
                            repository.sendRcuKey(RcuKey.USB)
                            delay(1500)
                            repository.sendRcuKey(RcuKey.RED)
                            delay(500)
                            repository.sendRcuKey(RcuKey.OK)
                            delay(3000)
                            repository.sendRcuKey(RcuKey.EXIT)
                            delay(500)
                            repository.sendRcuKey(RcuKey.EXIT)
                            withContext(Dispatchers.Main) {
                                val doneMsg = "دستور بکاپ‌گیری چیپست GX به رسیور ارسال شد."
                                txtStatus.text = doneMsg
                                AccessibilityHelper.announce(btnBackupStbUsb, doneMsg)
                                Toast.makeText(requireContext(), doneMsg, Toast.LENGTH_LONG).show()
                            }
                        }
                        else -> {
                            // Universal Menu (Menu -> Right -> Down -> OK)
                            repository.sendRcuKey(RcuKey.MENU)
                            delay(1000)
                            repository.sendRcuKey(RcuKey.RIGHT)
                            delay(400)
                            repository.sendRcuKey(RcuKey.DOWN)
                            delay(400)
                            repository.sendRcuKey(RcuKey.OK)
                            delay(3000)
                            repository.sendRcuKey(RcuKey.EXIT)
                            withContext(Dispatchers.Main) {
                                val doneMsg = "دستور بکاپ‌گیری همگانی منو ارسال شد."
                                txtStatus.text = doneMsg
                                AccessibilityHelper.announce(btnBackupStbUsb, doneMsg)
                                Toast.makeText(requireContext(), doneMsg, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val err = "خطا در ارسال فرامین پشتیبان‌گیری به رسیور: ${e.message}"
                        txtStatus.text = err
                        AccessibilityHelper.announce(btnBackupStbUsb, err)
                        Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 3. Export Word docx
        btnExportDocx.setOnClickListener {
            val channels = repository.channels.value
            if (channels.isEmpty()) {
                val msg = "لیست شبکه‌ها خالی است. ابتدا به رسیور متصل شده و لیست شبکه‌ها را دریافت کنید."
                AccessibilityHelper.announce(btnExportDocx, msg)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val waitMsg = "در حال ذخیره لیست شبکه‌ها و فرکانس‌ها در فایل ورد..."
            txtStatus.text = waitMsg
            AccessibilityHelper.announce(btnExportDocx, waitMsg)

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val model = repository.deviceModel.value
                    val docxFile = StorageManager.exportChannelsToWordDocx(requireContext(), channels, model)
                    withContext(Dispatchers.Main) {
                        val msg = "فایل ورد با ${channels.size} شبکه با موفقیت ایجاد و در پوشه پشتیبان ذخیره شد."
                        txtStatus.text = msg
                        AccessibilityHelper.announce(btnExportDocx, msg)
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val err = "خطا در ایجاد فایل ورد شبکه‌ها: ${e.message}"
                        txtStatus.text = err
                        AccessibilityHelper.announce(btnExportDocx, err)
                        Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 4. Open M3U playlist creator dialog
        btnOpenPlaylistCreator.setOnClickListener {
            val channels = repository.channels.value
            val ip = repository.connectedIp.value
            if (channels.isEmpty() || ip.isBlank()) {
                val msg = "لیست شبکه‌ها خالی است. ابتدا به رسیور متصل شوید."
                AccessibilityHelper.announce(btnOpenPlaylistCreator, msg)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openPlaylistCreatorDialog(channels, ip)
        }

        // 5. Manage playlists
        btnManagePlaylists.setOnClickListener {
            showPlaylistsManagerDialog()
        }

        // 6. Manage backups and word files
        btnManageBackups.setOnClickListener {
            showBackupsManagerDialog()
        }

        // 7. Local JSON backup on phone
        btnBackupPhone.setOnClickListener {
            val channels = repository.channels.value
            if (channels.isEmpty()) {
                val msg = "لیست کانال‌ها خالی است. ابتدا به رسیور متصل شوید."
                AccessibilityHelper.announce(btnBackupPhone, msg)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val model = repository.deviceModel.value
            val file = StorageManager.saveBackupChannels(requireContext(), channels, model)
            val msg = "پشتیبان‌گیری از ${channels.size} کانال در فایل ${file.name} با موفقیت انجام شد."
            txtStatus.text = msg
            AccessibilityHelper.announce(btnBackupPhone, msg)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.connectedIp.collect {
                updateDeviceInfo()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repository.deviceModel.collect {
                updateDeviceInfo()
            }
        }
    }

    // --- M3U Playlist Creator Dialog (Accessible Dialog mirroring Windows AccessiblePlaylistCreatorDialog) ---
    private fun openPlaylistCreatorDialog(allChannels: List<Channel>, hostIp: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_playlist_creator, null)

        val txtCount = dialogView.findViewById<TextView>(R.id.txtSelectedCount)
        val btnPlaylistMode = dialogView.findViewById<MaterialButton>(R.id.btnPlaylistMode)
        val btnSelectAll = dialogView.findViewById<MaterialButton>(R.id.btnSelectAll)
        val btnDeselectAll = dialogView.findViewById<MaterialButton>(R.id.btnDeselectAll)
        val edtSearch = dialogView.findViewById<EditText>(R.id.edtSearchChannel)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerChannels)
        val btnGenerate = dialogView.findViewById<MaterialButton>(R.id.btnGeneratePlaylists)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnCloseDialog)

        var playlistModeIndex = 0 // 0: تک‌تک (پیش‌فرض), 1: جامع, 2: هر دو
        val modeOptions = arrayOf(
            "تولید فایل‌های مجزا برای هر شبکه (تک‌تک - پیش‌فرض)",
            "تولید یک فایل جامع کلی برای تمام شبکه‌ها",
            "تولید هر دو حالت (هم مجزا و هم فایل جامع)"
        )

        btnPlaylistMode.text = modeOptions[playlistModeIndex]
        btnPlaylistMode.contentDescription = "حالت ساخت لیست پخش: ${modeOptions[playlistModeIndex]}. برای تغییر دو بار ضربه بزنید."

        btnPlaylistMode.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("حالت ساخت فایل‌های لیست پخش")
                .setSingleChoiceItems(modeOptions, playlistModeIndex) { dlg, which ->
                    playlistModeIndex = which
                    val selectedText = modeOptions[which]
                    btnPlaylistMode.text = selectedText
                    btnPlaylistMode.contentDescription = "حالت ساخت لیست پخش: $selectedText. برای تغییر دو بار ضربه بزنید."
                    AccessibilityHelper.announce(btnPlaylistMode, "حالت انتخاب شد: $selectedText")
                    dlg.dismiss()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        val selectedSet = HashSet<Channel>(allChannels)
        var filteredList = allChannels.toList()

        lateinit var dialog: AlertDialog
        lateinit var adapter: PlaylistChannelAdapter

        fun updateCountText() {
            val countText = "انتخاب شده: ${selectedSet.size} از ${allChannels.size}"
            txtCount.text = countText
            txtCount.contentDescription = countText
        }

        adapter = PlaylistChannelAdapter(filteredList, selectedSet) {
            updateCountText()
        }

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        updateCountText()

        btnSelectAll.setOnClickListener {
            selectedSet.addAll(allChannels)
            adapter.notifyDataSetChanged()
            updateCountText()
            val msg = "تمام ${allChannels.size} شبکه انتخاب شدند."
            AccessibilityHelper.announce(btnSelectAll, msg)
        }

        btnDeselectAll.contentDescription = "لغو انتخاب همه شبکه‌ها"
        btnDeselectAll.setOnClickListener {
            selectedSet.clear()
            adapter.notifyDataSetChanged()
            updateCountText()
            val msg = "لغو انتخاب تمام شبکه‌ها انجام شد."
            AccessibilityHelper.announce(btnDeselectAll, msg)
        }

        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                filteredList = if (query.isEmpty()) {
                    allChannels
                } else {
                    allChannels.filter {
                        it.serviceName.contains(query, ignoreCase = true) ||
                        it.channelNumber.toString().contains(query)
                    }
                }
                adapter.updateList(filteredList)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnGenerate.setOnClickListener {
            if (selectedSet.isEmpty()) {
                val err = "هیچ شبکه‌ای انتخاب نشده است."
                AccessibilityHelper.announce(btnGenerate, err)
                Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val doIndiv = (playlistModeIndex == 0 || playlistModeIndex == 2)
            val doComb = (playlistModeIndex == 1 || playlistModeIndex == 2)

            val selectedChannelsOrdered = allChannels.filter { selectedSet.contains(it) }

            btnGenerate.isEnabled = false
            btnGenerate.text = "در حال ساخت پلی‌لیست..."
            val progressMsg = "در حال تولید فایل‌های پلی‌لیست M3U و استعلام فرکانس‌ها..."
            AccessibilityHelper.announce(btnGenerate, progressMsg)

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // 1. Wake STB RTSP daemon if receiver is connected
                    if (repository.isConnected.value && hostIp.isNotBlank()) {
                        repository.client.sendCmd(1009, expectReply = false)
                        if (repository.client.transponders.isEmpty()) {
                            repository.client.fetchTransponders()
                        }
                    }

                    // 2. Pre-generate proper SAT>IP URL for each selected channel
                    val urlMap = mutableMapOf<String, String>()
                    for (ch in selectedChannelsOrdered) {
                        var satipQuery: String? = null
                        if (repository.isConnected.value) {
                            satipQuery = repository.client.buildSatIpQuery(ch)
                        }
                        val streamUrl = if (!satipQuery.isNullOrBlank()) {
                            val cleanQ = if (satipQuery.startsWith("?")) satipQuery else "?$satipQuery"
                            "rtsp://$hostIp:554/$cleanQ"
                        } else {
                            val prog = if (ch.serviceId.length >= 4) ch.serviceId.takeLast(4) else "0001"
                            "rtsp://$hostIp:554/?pids=0&prognumber=$prog"
                        }
                        urlMap[ch.serviceId] = streamUrl
                    }

                    val created = StorageManager.exportPlaylists(
                        context = requireContext(),
                        channels = selectedChannelsOrdered,
                        hostIp = hostIp,
                        doIndividual = doIndiv,
                        doCombined = doComb,
                        channelUrls = urlMap
                    )

                    withContext(Dispatchers.Main) {
                        val successMsg = "تعداد $created فایل لیست پخش ام تری یو با موفقیت ساخته شد."
                        txtStatus.text = successMsg
                        AccessibilityHelper.announce(txtStatus, successMsg)
                        Toast.makeText(requireContext(), successMsg, Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        btnGenerate.isEnabled = true
                        btnGenerate.text = "ساخت پلی‌لیست"
                        val errMsg = "خطا در ساخت پلی‌لیست: ${e.message}"
                        Toast.makeText(requireContext(), errMsg, Toast.LENGTH_SHORT).show()
                        AccessibilityHelper.announce(btnGenerate, errMsg)
                    }
                }
            }
        }

        dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        val openMsg = "پنجره ساخت لیست پخش باز شد. ${allChannels.size} شبکه آماده انتخاب است."
        AccessibilityHelper.announce(dialogView, openMsg)
    }

    // --- Dialog to manage generated M3U Playlists ---
    private fun showPlaylistsManagerDialog() {
        val files = StorageManager.listPlaylistFiles(requireContext())
        if (files.isEmpty()) {
            val msg = "هیچ فایل پلی‌لیست M3U ذخیره نشده است."
            AccessibilityHelper.announce(btnManagePlaylists, msg)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            return
        }

        val fileNames = files.map { "${it.name} (${it.length() / 1024} KB)" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("فایل‌های پلی‌لیست M3U (${files.size} فایل)")
            .setItems(fileNames) { _, which ->
                val selectedFile = files[which]
                showPlaylistFileOptions(selectedFile)
            }
            .setNeutralButton("حذف همه فایل‌ها") { _, _ ->
                confirmDeleteAllPlaylists(files)
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun confirmDeleteAllPlaylists(files: List<File>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("حذف تمام فایل‌های پلی‌لیست M3U")
            .setMessage("آیا مطمئن هستید که می‌خواهید تمام ${files.size} فایل پلی‌لیست M3U را حذف کنید؟ این عمل غیرقابل بازگشت است.")
            .setPositiveButton("حذف همه") { _, _ ->
                var count = 0
                for (f in files) {
                    if (f.delete()) count++
                }
                val msg = "تعداد $count فایل پلی‌لیست با موفقیت حذف شدند."
                AccessibilityHelper.announce(btnManagePlaylists, msg)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showPlaylistFileOptions(file: File) {
        val options = arrayOf(
            "پخش فایل در پلیرهای گوشی (VLC، MX Player و...)",
            "اشتراک‌گذاری فایل پلی‌لیست",
            "حذف فایل"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openFileWithExternalApp(file, "audio/x-mpegurl")
                    1 -> shareFile(file, "audio/x-mpegurl")
                    2 -> {
                        file.delete()
                        val msg = "فایل ${file.name} حذف شد."
                        AccessibilityHelper.announce(btnManagePlaylists, msg)
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    // --- Dialog to manage Backups and Word DOCX files ---
    private fun showBackupsManagerDialog() {
        val files = StorageManager.listBackupFiles(requireContext())
        if (files.isEmpty()) {
            val msg = "هیچ فایل پشتیبان یا سند وردی ذخیره نشده است."
            AccessibilityHelper.announce(btnManageBackups, msg)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            return
        }

        val fileNames = files.map { file ->
            val ext = file.extension.uppercase()
            "${file.name} ($ext - ${file.length() / 1024} KB)"
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("فایل‌های پشتیبان و اسناد ورد (${files.size} فایل)")
            .setItems(fileNames) { _, which ->
                val selectedFile = files[which]
                showBackupFileOptions(selectedFile)
            }
            .setNeutralButton("حذف همه فایل‌ها") { _, _ ->
                confirmDeleteAllBackups(files)
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun confirmDeleteAllBackups(files: List<File>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("حذف تمام فایل‌های پشتیبان و اسناد ورد")
            .setMessage("آیا مطمئن هستید که می‌خواهید تمام ${files.size} فایل پشتیبان و اسناد ورد را حذف کنید؟ این عمل غیرقابل بازگشت است.")
            .setPositiveButton("حذف همه") { _, _ ->
                var count = 0
                for (f in files) {
                    if (f.delete()) count++
                }
                val msg = "تعداد $count فایل پشتیبان با موفقیت حذف شدند."
                AccessibilityHelper.announce(btnManageBackups, msg)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showBackupFileOptions(file: File) {
        val isDocx = file.name.endsWith(".docx", ignoreCase = true)
        val isJson = file.name.endsWith(".json", ignoreCase = true)

        val options = when {
            isDocx -> arrayOf("باز کردن فایل ورد در گوشی", "اشتراک‌گذاری فایل", "حذف فایل")
            isJson -> arrayOf("مشاهده مشخصات فایل پشتیبان", "اشتراک‌گذاری فایل", "حذف فایل")
            else -> arrayOf("اشتراک‌گذاری فایل", "حذف فایل")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(file.name)
            .setItems(options) { _, which ->
                if (isDocx) {
                    when (which) {
                        0 -> openFileWithExternalApp(file, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        1 -> shareFile(file, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        2 -> {
                            file.delete()
                            val msg = "فایل سند ورد با موفقیت حذف شد"
                            AccessibilityHelper.announce(btnManageBackups, msg)
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (isJson) {
                    when (which) {
                        0 -> showBackupFileInfo(file)
                        1 -> shareFile(file, "application/json")
                        2 -> {
                            file.delete()
                            val msg = "فایل پشتیبان با موفقیت حذف شد"
                            AccessibilityHelper.announce(btnManageBackups, msg)
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    when (which) {
                        0 -> shareFile(file, "application/octet-stream")
                        1 -> {
                            file.delete()
                            val msg = "فایل با موفقیت حذف شد"
                            AccessibilityHelper.announce(btnManageBackups, msg)
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showBackupFileInfo(file: File) {
        val channels = StorageManager.loadBackupChannels(file)
        val count = channels?.size ?: 0
        val sizeKb = file.length() / 1024
        val msg = "این فایل پشتیبان شامل اطلاعات $count شبکه است (حجم: $sizeKb کیلوبایت). توجه داشته باشید برای بازگردانی کانال‌ها روی رسیور، باید فایل بکاپ فلش (SDX) مستقیماً از طریق منوی USB رسیور اجرا شود."
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("اطلاعات فایل پشتیبان")
            .setMessage(msg)
            .setPositiveButton("متوجه شدم", null)
            .show()
        AccessibilityHelper.announce(btnManageBackups, msg)
    }

    private fun openFileWithExternalApp(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            val err = "برنامه‌ای برای باز کردن این فایل یافت نشد: ${e.message}"
            Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "اشتراک‌گذاری فایل"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "خطا در اشتراک‌گذاری", Toast.LENGTH_SHORT).show()
        }
    }

    // --- RecyclerView Adapter for channel selection in playlist dialog ---
    private class PlaylistChannelAdapter(
        private var list: List<Channel>,
        private val selectedSet: HashSet<Channel>,
        private val onSelectionChanged: () -> Unit
    ) : RecyclerView.Adapter<PlaylistChannelAdapter.ViewHolder>() {

        fun updateList(newList: List<Channel>) {
            list = newList
            notifyDataSetChanged()
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val chk: MaterialCheckBox = itemView.findViewById(R.id.chkChannel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist_channel, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val ch = list[position]
            val typeTag = if (ch.isRadio) "[رادیو]" else "[تلویزیون]"
            val label = String.format(Locale.US, "%03d - %s %s", position + 1, ch.serviceName, typeTag)

            holder.chk.text = label
            val isChecked = selectedSet.contains(ch)
            holder.chk.isChecked = isChecked
            holder.chk.contentDescription = "$label: ${if (isChecked) "انتخاب شده" else "انتخاب نشده"}"

            holder.chk.setOnClickListener {
                if (holder.chk.isChecked) {
                    selectedSet.add(ch)
                    AccessibilityHelper.announce(holder.chk, "انتخاب شد: $label")
                } else {
                    selectedSet.remove(ch)
                    AccessibilityHelper.announce(holder.chk, "لغو شد: $label")
                }
                onSelectionChanged()
            }
        }
    }
}
