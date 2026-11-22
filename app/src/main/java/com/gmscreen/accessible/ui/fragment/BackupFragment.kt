package com.gmscreen.accessible.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupFragment : Fragment() {

    private lateinit var txtBackupDeviceInfo: TextView
    private lateinit var btnSelectBackupMethod: MaterialButton
    private lateinit var btnBackupStbUsb: MaterialButton
    private lateinit var txtStatus: TextView
    private lateinit var btnExportChannels: MaterialButton
    private lateinit var btnManageBackups: MaterialButton
    private lateinit var btnOpenPlaylistCreator: MaterialButton
    private lateinit var btnManagePlaylists: MaterialButton

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
        btnExportChannels = view.findViewById(R.id.btnExportChannels)
        btnManageBackups = view.findViewById(R.id.btnManageBackups)
        btnOpenPlaylistCreator = view.findViewById(R.id.btnOpenPlaylistCreator)
        btnManagePlaylists = view.findViewById(R.id.btnManagePlaylists)

        val btnHeader1 = view.findViewById<MaterialButton>(R.id.btnHeaderSection1)
        val layoutContent1 = view.findViewById<View>(R.id.layoutSection1Content)
        val btnHeader2 = view.findViewById<MaterialButton>(R.id.btnHeaderSection2)
        val layoutContent2 = view.findViewById<View>(R.id.layoutSection2Content)
        val btnHeader3 = view.findViewById<MaterialButton>(R.id.btnHeaderSection3)
        val layoutContent3 = view.findViewById<View>(R.id.layoutSection3Content)

        btnHeader1.setOnClickListener {
            val isExpanded = layoutContent1.visibility == View.VISIBLE
            layoutContent1.visibility = if (isExpanded) View.GONE else View.VISIBLE
            btnHeader1.text = "۱. پشتیبان‌گیری روی فلش رسیور (USB)  " + if (isExpanded) "▶" else "▼"
            AccessibilityHelper.announce(btnHeader1, if (isExpanded) "بخش ۱ بسته شد" else "بخش ۱ باز شد")
        }

        btnHeader2.setOnClickListener {
            val isExpanded = layoutContent2.visibility == View.VISIBLE
            layoutContent2.visibility = if (isExpanded) View.GONE else View.VISIBLE
            btnHeader2.text = "۲. ساخت و مشاهده پلی‌لیست‌های M3U  " + if (isExpanded) "▶" else "▼"
            AccessibilityHelper.announce(btnHeader2, if (isExpanded) "بخش ۲ بسته شد" else "بخش ۲ باز شد")
        }

        btnHeader3.setOnClickListener {
            val isExpanded = layoutContent3.visibility == View.VISIBLE
            layoutContent3.visibility = if (isExpanded) View.GONE else View.VISIBLE
            btnHeader3.text = "۳. ذخیره‌سازی کانال‌ها در گوشی (Word, TXT, ...)  " + if (isExpanded) "▶" else "▼"
            AccessibilityHelper.announce(btnHeader3, if (isExpanded) "بخش ۳ بسته شد" else "بخش ۳ باز شد")
        }

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
            txtStatus.visibility = View.VISIBLE
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

        // 3. Export channel data with format chooser (Word, TXT, HTML, JSON)
        btnExportChannels.setOnClickListener {
            openFormatSelectionDialog()
        }

        // 4. View and manage saved backup files
        btnManageBackups.setOnClickListener {
            showCustomFileManagerDialog(isPlaylist = false)
        }

        // 5. Open M3U playlist creator dialog
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

        // 6. View and manage M3U playlists
        btnManagePlaylists.setOnClickListener {
            showCustomFileManagerDialog(isPlaylist = true)
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

    // --- Dialog to choose export format: Word, TXT, HTML, JSON ---
    private fun openFormatSelectionDialog() {
        val channels = repository.channels.value
        if (channels.isEmpty()) {
            val msg = "لیست کانال‌ها خالی است. ابتدا به رسیور متصل شده و لیست شبکه‌ها را دریافت کنید."
            AccessibilityHelper.announce(btnExportChannels, msg)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            return
        }

        val formatOptions = arrayOf(
            "۱. سند مایکروسافت ورد (.docx) - جدول زیبا و رسمی فرکانس‌ها و مشخصات",
            "۲. فایل متنی ساده (.txt) - جدول ستون‌بندی شده مناسب خواندن با صفحه‌خوان",
            "۳. فایل وب شیک و واکنش‌گرا (.html) - قالب مشکی اولد با فیلتر جستجوی لحظه‌ای",
            "۴. فایل داده ساختاریافته (.json) - مناسب برای بازگردانی و توسعه‌دهندگان"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("انتخاب فرمت ذخیره‌سازی اطلاعات شبکه‌ها")
            .setItems(formatOptions) { _, which ->
                val (formatLabel, extName) = when (which) {
                    0 -> Pair("سند مایکروسافت ورد", "docx")
                    1 -> Pair("فایل متنی ساده", "txt")
                    2 -> Pair("فایل وب HTML", "html")
                    else -> Pair("فایل داده JSON", "json")
                }

                val waitMsg = "در حال ذخیره‌سازی $formatLabel از ${channels.size} شبکه..."
                txtStatus.visibility = View.VISIBLE
                txtStatus.text = waitMsg
                AccessibilityHelper.announce(btnExportChannels, waitMsg)

                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val model = repository.deviceModel.value
                        val createdFile = when (which) {
                            0 -> StorageManager.exportChannelsToWordDocx(requireContext(), channels, model)
                            1 -> StorageManager.exportChannelsToTxt(requireContext(), channels, model)
                            2 -> StorageManager.exportChannelsToHtml(requireContext(), channels, model)
                            else -> StorageManager.exportChannelsToJson(requireContext(), channels, model)
                        }

                        withContext(Dispatchers.Main) {
                            val successMsg = "$formatLabel با موفقیت ایجاد شد: ${createdFile.name}"
                            txtStatus.text = successMsg
                            AccessibilityHelper.announce(btnExportChannels, successMsg)
                            Toast.makeText(requireContext(), successMsg, Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            val err = "خطا در ایجاد $formatLabel: ${e.message}"
                            txtStatus.text = err
                            AccessibilityHelper.announce(btnExportChannels, err)
                            Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    // --- Unified Accessible File Manager Dialog (Playlists or Saved Data Files) ---
    private fun showCustomFileManagerDialog(isPlaylist: Boolean) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_file_manager, null)

        val txtTitle = dialogView.findViewById<TextView>(R.id.txtDialogTitle)
        val txtCount = dialogView.findViewById<TextView>(R.id.txtFilesCount)
        val btnDeleteAll = dialogView.findViewById<MaterialButton>(R.id.btnDeleteAllFiles)
        val txtEmpty = dialogView.findViewById<TextView>(R.id.txtEmptyFiles)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerFiles)
        val btnSortOrder = dialogView.findViewById<MaterialButton>(R.id.btnToggleSortOrder)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnCloseDialog)

        val title = if (isPlaylist) "مدیریت فایل‌های پلی‌لیست M3U" else "مدیریت فایل‌های ذخیره‌شده (Word, TXT, HTML, JSON)"
        txtTitle.text = title

        var isAscending = true
        val fileList = mutableListOf<File>()
        val refreshFilesList: () -> Unit = {
            fileList.clear()
            if (isPlaylist) {
                fileList.addAll(StorageManager.listPlaylistFiles(requireContext()))
            } else {
                fileList.addAll(StorageManager.listBackupFiles(requireContext()))
            }
            if (!isAscending) {
                fileList.reverse()
            }
        }
        lateinit var adapter: SavedFilesAdapter
        lateinit var dialog: AlertDialog

        refreshFilesList()

        btnSortOrder?.setOnClickListener {
            isAscending = !isAscending
            if (isAscending) {
                fileList.sortWith(StorageManager.NATURAL_FILE_COMPARATOR)
                btnSortOrder.text = "ترتیب: صعودی (۱ به آخر)"
                btnSortOrder.contentDescription = "ترتیب فایل‌ها: صعودی از ۱ به آخر. برای تغییر به نزولی ضربه بزنید."
                AccessibilityHelper.announce(btnSortOrder, "ترتیب فایل‌ها به صورت صعودی از شماره ۱ مرتب شد.")
            } else {
                fileList.sortWith(StorageManager.NATURAL_FILE_COMPARATOR.reversed())
                btnSortOrder.text = "ترتیب: نزولی (آخر به ۱)"
                btnSortOrder.contentDescription = "ترتیب فایل‌ها: نزولی از آخر به ۱. برای تغییر به صعودی ضربه بزنید."
                AccessibilityHelper.announce(btnSortOrder, "ترتیب فایل‌ها به صورت نزولی مرتب شد.")
            }
            adapter.notifyDataSetChanged()
            recycler.scrollToPosition(0)
        }

        fun updateUiState() {
            txtCount.text = "تعداد: ${fileList.size} فایل"
            txtCount.contentDescription = "تعداد فایل‌های موجود: ${fileList.size} فایل"
            if (fileList.isEmpty()) {
                txtEmpty.visibility = View.VISIBLE
                recycler.visibility = View.GONE
                btnDeleteAll.isEnabled = false
            } else {
                txtEmpty.visibility = View.GONE
                recycler.visibility = View.VISIBLE
                btnDeleteAll.isEnabled = true
            }
        }

        adapter = SavedFilesAdapter(fileList) { selectedFile, position ->
            showSingleFileActionsDialog(selectedFile, isPlaylist) { wasDeleted ->
                if (wasDeleted) {
                    fileList.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    adapter.notifyItemRangeChanged(position, fileList.size - position)
                    updateUiState()
                    if (fileList.isNotEmpty()) {
                        recycler.post {
                            recycler.scrollToPosition(0)
                            val firstVh = recycler.findViewHolderForAdapterPosition(0)
                            firstVh?.itemView?.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
                        }
                    }
                }
            }
        }

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        updateUiState()

        btnDeleteAll.setOnClickListener {
            if (fileList.isEmpty()) return@setOnClickListener
            val count = fileList.size
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("حذف تمام فایل‌ها")
                .setMessage("آیا مطمئن هستید که می‌خواهید تمام $count فایل موجود در این لیست را حذف کنید؟ این عمل غیرقابل بازگشت است.")
                .setPositiveButton("حذف همه") { _, _ ->
                    var deletedCount = 0
                    for (f in fileList) {
                        if (f.delete()) deletedCount++
                    }
                    fileList.clear()
                    adapter.notifyDataSetChanged()
                    updateUiState()
                    val delMsg = "تعداد $deletedCount فایل با موفقیت حذف شدند."
                    AccessibilityHelper.announce(dialogView, delMsg)
                    Toast.makeText(requireContext(), delMsg, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        val openMsg = "$title باز شد. ${fileList.size} فایل موجود است."
        AccessibilityHelper.announce(dialogView, openMsg)
    }

    private fun showSingleFileActionsDialog(file: File, isPlaylist: Boolean, onResult: (Boolean) -> Unit) {
        val ext = file.extension.lowercase()
        val mimeType = when (ext) {
            "m3u", "m3u8" -> "video/x-mpegurl"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "json" -> "application/json"
            else -> "*/*"
        }

        val openLabel = when {
            isPlaylist -> "۱. پخش در پلیرهای گوشی (VLC، MX Player و...)"
            ext == "docx" -> "۱. باز کردن سند در مایکروسافت ورد"
            ext == "txt" -> "۱. باز کردن فایل متنی در ویرایشگر"
            ext in listOf("html", "htm") -> "۱. باز کردن و مشاهده در مرورگر گوشی"
            ext == "json" -> "۱. مشاهده مشخصات فایل داده ساختاریافته"
            else -> "۱. باز کردن فایل"
        }

        val options = arrayOf(
            openLabel,
            "۲. اشتراک‌گذاری فایل (شبکه‌های اجتماعی، ایمیل یا بلوتوث)",
            "۳. حذف این فایل"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (ext == "json") {
                            showBackupFileInfo(file)
                        } else {
                            openFileWithExternalApp(file, mimeType)
                        }
                        onResult(false)
                    }
                    1 -> {
                        shareFile(file, mimeType)
                        onResult(false)
                    }
                    2 -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("تأیید حذف فایل")
                            .setMessage("آیا از حذف دائمی فایل \"${file.name}\" اطمینان دارید؟")
                            .setPositiveButton("بله، حذف شود") { _, _ ->
                                val deleted = file.delete()
                                if (deleted) {
                                    val delMsg = "فایل ${file.name} حذف شد."
                                    AccessibilityHelper.announce(requireView(), delMsg)
                                    Toast.makeText(requireContext(), delMsg, Toast.LENGTH_SHORT).show()
                                    onResult(true)
                                } else {
                                    Toast.makeText(requireContext(), "خطا در حذف فایل", Toast.LENGTH_SHORT).show()
                                    onResult(false)
                                }
                            }
                            .setNegativeButton("انصراف") { _, _ ->
                                onResult(false)
                            }
                            .show()
                    }
                }
            }
            .setNegativeButton("انصراف") { _, _ ->
                onResult(false)
            }
            .show()
    }

    private fun showBackupFileInfo(file: File) {
        val channels = StorageManager.loadBackupChannels(file)
        val count = channels?.size ?: 0
        val sizeKb = (file.length() + 1023) / 1024
        val msg = "این فایل پشتیبان شامل اطلاعات $count شبکه است (حجم: $sizeKb کیلوبایت)."
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("اطلاعات فایل پشتیبان JSON")
            .setMessage(msg)
            .setPositiveButton("متوجه شدم", null)
            .show()
        AccessibilityHelper.announce(requireView(), msg)
    }

    private fun openFileWithExternalApp(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
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

    // --- M3U Playlist Creator Dialog ---
    private fun openPlaylistCreatorDialog(allChannels: List<Channel>, hostIp: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_playlist_creator, null)

        val txtCount = dialogView.findViewById<TextView>(R.id.txtSelectedCount)
        val btnPlaylistMode = dialogView.findViewById<MaterialButton>(R.id.btnPlaylistMode)
        val btnChannelTypeFilter = dialogView.findViewById<MaterialButton>(R.id.btnChannelTypeFilter)
        val btnSelectAll = dialogView.findViewById<MaterialButton>(R.id.btnSelectAll)
        val btnDeselectAll = dialogView.findViewById<MaterialButton>(R.id.btnDeselectAll)
        val edtSearch = dialogView.findViewById<EditText>(R.id.edtSearchChannel)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerChannels)
        val btnGenerate = dialogView.findViewById<MaterialButton>(R.id.btnGeneratePlaylists)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnCloseDialog)

        var playlistModeIndex = 0 // 0: تک‌تک (پیش‌فرض), 1: جامع, 2: هر دو
        val modeOptions = arrayOf(
            "فایل‌های مجزا برای هر شبکه (تک‌تک)",
            "یک فایل جامع برای تمام شبکه‌ها",
            "هر دو حالت (مجزا و فایل جامع)"
        )

        btnPlaylistMode.text = modeOptions[playlistModeIndex]
        btnPlaylistMode.contentDescription = "حالت ساخت: ${modeOptions[playlistModeIndex]}"

        btnPlaylistMode.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("حالت ساخت پلی‌لیست")
                .setSingleChoiceItems(modeOptions, playlistModeIndex) { dlg, which ->
                    playlistModeIndex = which
                    val selectedText = modeOptions[which]
                    btnPlaylistMode.text = selectedText
                    btnPlaylistMode.contentDescription = "حالت ساخت: $selectedText"
                    AccessibilityHelper.announce(btnPlaylistMode, selectedText)
                    dlg.dismiss()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        var channelTypeFilterIndex = 0 // 0: همه, 1: تلویزیون, 2: رادیو
        val filterOptions = arrayOf(
            "همه شبکه‌ها (تلویزیون و رادیو)",
            "فقط شبکه‌های تلویزیونی",
            "فقط شبکه‌های رادیویی"
        )
        btnChannelTypeFilter.text = filterOptions[channelTypeFilterIndex]
        btnChannelTypeFilter.contentDescription = "نوع شبکه: ${filterOptions[channelTypeFilterIndex]}"

        val selectedSet = HashSet<Channel>(allChannels)
        var filteredList = allChannels.toList()

        lateinit var dialog: AlertDialog
        lateinit var adapter: PlaylistChannelAdapter

        fun getActiveList(): List<Channel> {
            val byType = when (channelTypeFilterIndex) {
                1 -> allChannels.filter { !it.isRadio }
                2 -> allChannels.filter { it.isRadio }
                else -> allChannels
            }
            val query = edtSearch.text?.toString()?.trim() ?: ""
            return if (query.isEmpty()) byType else byType.filter {
                it.serviceName.contains(query, ignoreCase = true) ||
                it.channelNumber.toString().contains(query)
            }
        }

        fun updateCountText() {
            val countText = "انتخاب شده: ${selectedSet.size} از ${allChannels.size}"
            txtCount.text = countText
            txtCount.contentDescription = countText
        }

        btnChannelTypeFilter.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("انتخاب نوع شبکه‌ها")
                .setSingleChoiceItems(filterOptions, channelTypeFilterIndex) { dlg, which ->
                    channelTypeFilterIndex = which
                    val opt = filterOptions[which]
                    btnChannelTypeFilter.text = opt
                    btnChannelTypeFilter.contentDescription = "نوع شبکه: $opt"
                    AccessibilityHelper.announce(btnChannelTypeFilter, opt)
                    filteredList = getActiveList()
                    adapter.updateList(filteredList)
                    updateCountText()
                    dlg.dismiss()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        adapter = PlaylistChannelAdapter(filteredList, selectedSet) {
            updateCountText()
        }

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        updateCountText()

        btnSelectAll.setOnClickListener {
            val curActive = getActiveList()
            selectedSet.addAll(curActive)
            adapter.notifyDataSetChanged()
            updateCountText()
            val msg = "${curActive.size} شبکه انتخاب شد"
            AccessibilityHelper.announce(btnSelectAll, msg)
        }

        btnDeselectAll.contentDescription = "لغو انتخاب همه شبکه‌ها"
        btnDeselectAll.setOnClickListener {
            selectedSet.clear()
            adapter.notifyDataSetChanged()
            updateCountText()
            val msg = "لغو انتخاب همه انجام شد"
            AccessibilityHelper.announce(btnDeselectAll, msg)
        }

        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filteredList = getActiveList()
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

    // --- Saved Files RecyclerView Adapter ---
    private class SavedFilesAdapter(
        private val files: List<File>,
        private val onItemClick: (File, Int) -> Unit
    ) : RecyclerView.Adapter<SavedFilesAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imgIcon: ImageView = itemView.findViewById(R.id.imgFileIcon)
            val txtName: TextView = itemView.findViewById(R.id.txtFileName)
            val txtInfo: TextView = itemView.findViewById(R.id.txtFileInfo)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_saved_file, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = files.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.txtName.text = file.name

            val ext = file.extension.uppercase()
            val sizeKb = (file.length() + 1023) / 1024
            val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
            holder.txtInfo.text = "$ext • $sizeKb KB • $dateStr"

            // Set appropriate icon
            when (ext) {
                "M3U", "M3U8" -> holder.imgIcon.setImageResource(android.R.drawable.ic_media_play)
                "DOCX" -> holder.imgIcon.setImageResource(android.R.drawable.ic_menu_agenda)
                "TXT" -> holder.imgIcon.setImageResource(android.R.drawable.ic_menu_edit)
                "HTML", "HTM" -> holder.imgIcon.setImageResource(android.R.drawable.ic_menu_view)
                "JSON" -> holder.imgIcon.setImageResource(android.R.drawable.ic_menu_save)
                else -> holder.imgIcon.setImageResource(android.R.drawable.ic_menu_agenda)
            }

            val desc = "${file.name}، فرمت $ext، حجم $sizeKb کیلوبایت. برای گزینه‌های باز کردن، اشتراک‌گذاری یا حذف دو بار ضربه بزنید."
            holder.itemView.contentDescription = desc

            holder.itemView.setOnClickListener {
                onItemClick(file, holder.adapterPosition)
            }
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
