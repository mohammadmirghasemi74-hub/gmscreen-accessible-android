package com.gmscreen.accessible.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

object RecordingsDialogHelper {

    var shouldReopenOnResume: Boolean = false
    var lastPlayedFolder: File? = null
    var lastPlayedFile: File? = null

    private fun isMediaFile(f: File): Boolean {
        val name = f.name.lowercase()
        return name.endsWith(".mp4") || name.endsWith(".ts") || name.endsWith(".mkv") ||
                name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".wav") ||
                name.endsWith(".aac") || name.endsWith(".ogg")
    }

    fun showRecordingsDialog(
        context: Context,
        announceView: View? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        val baseDir = StorageManager.getRecordingsDir(context)
        val allFiles = StorageManager.listRecordedFiles(context)
        if (allFiles.isEmpty()) {
            val msg = "هیچ فایل ضبط‌شده‌ای در پوشه ذخیره یافت نشد"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            onDismiss?.invoke()
            return
        }

        // Collect folders and loose files
        val subDirs = baseDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name } ?: emptyList()
        val looseFiles = baseDir.listFiles { f -> f.isFile && isMediaFile(f) }?.sortedByDescending { it.lastModified() } ?: emptyList()

        val folderItems = mutableListOf<Pair<String, File>>()

        for (dir in subDirs) {
            val filesInDir = dir.listFiles { f -> f.isFile && isMediaFile(f) } ?: emptyArray()
            if (filesInDir.isNotEmpty()) {
                val totalBytes = filesInDir.sumOf { it.length() }
                val sizeMb = String.format("%.1f", totalBytes / (1024f * 1024f))
                folderItems.add(Pair("📁 پوشه ${dir.name} (${filesInDir.size} فایل - $sizeMb MB)", dir))
            }
        }

        if (looseFiles.isNotEmpty()) {
            val totalBytes = looseFiles.sumOf { it.length() }
            val sizeMb = String.format("%.1f", totalBytes / (1024f * 1024f))
            folderItems.add(Pair("📁 فایل‌های عمومی و متفرقه (${looseFiles.size} فایل - $sizeMb MB)", baseDir))
        }

        val displayNames = folderItems.map { it.first }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle("پوشه‌های ضبط‌شده در گوشی (${allFiles.size} فایل کل)")
            .setItems(displayNames) { _, which ->
                val selected = folderItems.getOrNull(which)
                if (selected != null) {
                    showFolderContentsDialog(context, selected.second, announceView, onDismiss)
                }
            }
            .setPositiveButton("پوشه فایل‌ها") { _, _ ->
                openRecordingsFolder(context)
                onDismiss?.invoke()
            }
            .setNeutralButton("حذف کل فایل‌ها") { _, _ ->
                confirmDeleteAllRecordings(context, allFiles.size, announceView, onDismiss)
            }
            .setNegativeButton("بستن") { _, _ ->
                onDismiss?.invoke()
            }
            .setOnDismissListener {
                onDismiss?.invoke()
            }
            .show()
    }

    fun showFolderContentsDialog(
        context: Context,
        folder: File,
        announceView: View? = null,
        onDismiss: (() -> Unit)? = null,
        initialFocusFile: File? = null
    ) {
        val files = folder.listFiles { f -> f.isFile && isMediaFile(f) }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (files.isEmpty()) {
            Toast.makeText(context, "این پوشه خالی است", Toast.LENGTH_SHORT).show()
            showRecordingsDialog(context, announceView, onDismiss)
            return
        }

        val items = files.map { file ->
            val sizeMb = String.format("%.1f", file.length() / (1024f * 1024f))
            val isAudio = file.name.lowercase().let { it.endsWith(".mp3") || it.endsWith(".m4a") || it.endsWith(".wav") }
            val icon = if (isAudio) "🎵" else "🎬"
            "$icon ${file.name} ($sizeMb MB)"
        }.toTypedArray()

        val folderTitle = if (folder == StorageManager.getRecordingsDir(context)) "فایل‌های عمومی" else folder.name
        val initialIndex = if (initialFocusFile != null) {
            files.indexOfFirst { it.absolutePath == initialFocusFile.absolutePath }
        } else -1

        MaterialAlertDialogBuilder(context)
            .setTitle("پوشه «$folderTitle» (${files.size} فایل)")
            .setSingleChoiceItems(items, initialIndex) { dlg, which ->
                dlg.dismiss()
                val file = files.getOrNull(which)
                if (file != null) {
                    showFileActionsDialog(context, file, folder, announceView) {
                        showFolderContentsDialog(context, folder, announceView, onDismiss, initialFocusFile = file)
                    }
                }
            }
            .setPositiveButton("حذف این پوشه") { _, _ ->
                confirmDeleteFolder(context, folder, files.size, announceView) {
                    showRecordingsDialog(context, announceView, onDismiss)
                }
            }
            .setNegativeButton("بازگشت") { _, _ ->
                showRecordingsDialog(context, announceView, onDismiss)
            }
            .show()
    }

    fun reopenLastPlayedFolder(context: Context) {
        val folder = lastPlayedFolder ?: StorageManager.getRecordingsDir(context)
        val focusFile = lastPlayedFile
        showFolderContentsDialog(context, folder, initialFocusFile = focusFile)
    }

    private fun confirmDeleteFolder(
        context: Context,
        folder: File,
        fileCount: Int,
        announceView: View? = null,
        onCompleted: (() -> Unit)? = null
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle("حذف کامل پوشه")
            .setMessage("آیا مطمئن هستید که می‌خواهید پوشه «${folder.name}» و تمام $fileCount فایل داخل آن را حذف کنید؟")
            .setPositiveButton("بله، حذف شود") { _, _ ->
                val ok = folder.deleteRecursively()
                val msg = if (ok) "پوشه «${folder.name}» با موفقیت حذف شد" else "خطا در حذف پوشه"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                onCompleted?.invoke()
            }
            .setNegativeButton("انصراف") { _, _ ->
                onCompleted?.invoke()
            }
            .show()
    }

    private fun confirmDeleteAllRecordings(
        context: Context,
        fileCount: Int,
        announceView: View? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle("حذف کل فایل‌های ضبط‌شده")
            .setMessage("آیا مطمئن هستید که می‌خواهید تمام فایل‌های ضبط‌شده ($fileCount فایل) را حذف کنید؟ این عمل غیرقابل بازگشت است.")
            .setPositiveButton("بله، همه حذف شوند") { _, _ ->
                val deletedCount = StorageManager.deleteAllRecordings(context)
                val msg = "کل فایل‌های موجود با موفقیت حذف شدند ($deletedCount فایل)"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                onDismiss?.invoke()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showFileActionsDialog(
        context: Context,
        file: File,
        parentFolder: File,
        announceView: View? = null,
        onFileChanged: (() -> Unit)? = null
    ) {
        val options = arrayOf("۱. پخش فایل", "۲. اشتراک‌گذاری", "۳. حذف فایل")
        MaterialAlertDialogBuilder(context)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playLocalFile(context, file, parentFolder)
                    1 -> shareLocalFile(context, file)
                    2 -> confirmDeleteSingleFile(context, file, announceView, onFileChanged)
                }
            }
            .setNegativeButton("انصراف") { _, _ ->
                onFileChanged?.invoke()
            }
            .show()
    }

    private fun confirmDeleteSingleFile(
        context: Context,
        file: File,
        announceView: View? = null,
        onFileDeleted: (() -> Unit)? = null
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle("حذف فایل ضبط‌شده")
            .setMessage("آیا از حذف فایل «${file.name}» مطمئن هستید؟")
            .setPositiveButton("بله، حذف شود") { _, _ ->
                val name = file.name
                val ok = file.delete()
                val msg = if (ok) "فایل $name با موفقیت حذف شد" else "خطا در حذف فایل $name"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                onFileDeleted?.invoke()
            }
            .setNegativeButton("انصراف") { _, _ ->
                onFileDeleted?.invoke()
            }
            .show()
    }

    fun playLocalFile(context: Context, file: File, parentFolder: File? = null) {
        try {
            lastPlayedFolder = parentFolder ?: file.parentFile
            lastPlayedFile = file
            shouldReopenOnResume = true

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val nameLower = file.name.lowercase()
            val mime = when {
                nameLower.endsWith(".mp4") -> "video/mp4"
                nameLower.endsWith(".mkv") -> "video/x-matroska"
                nameLower.endsWith(".ts") -> "video/mp2t"
                nameLower.endsWith(".mp3") -> "audio/mpeg"
                nameLower.endsWith(".m4a") -> "audio/mp4"
                nameLower.endsWith(".wav") -> "audio/x-wav"
                nameLower.endsWith(".aac") -> "audio/aac"
                nameLower.endsWith(".ogg") -> "audio/ogg"
                else -> if (nameLower.endsWith(".ts") || nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv")) "video/*" else "audio/*"
            }

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(viewIntent, "پخش «${file.name}» با استفاده از:").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            // Fallback to internal video player
            try {
                val fallbackIntent = Intent(context, com.gmscreen.accessible.ui.VideoPlayerActivity::class.java).apply {
                    putExtra("STREAM_URL", file.absolutePath)
                    putExtra("CHANNEL_NAME", file.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, "خطا در پخش فایل: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareLocalFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val nameLower = file.name.lowercase()
            val mime = when {
                nameLower.endsWith(".mp4") -> "video/mp4"
                nameLower.endsWith(".mkv") -> "video/x-matroska"
                nameLower.endsWith(".ts") -> "video/mp2t"
                nameLower.endsWith(".mp3") -> "audio/mpeg"
                nameLower.endsWith(".m4a") -> "audio/mp4"
                nameLower.endsWith(".wav") -> "audio/x-wav"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری فایل ضبط شده"))
        } catch (e: Exception) {
            Toast.makeText(context, "خطا در اشتراک‌گذاری فایل: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openRecordingsFolder(context: Context) {
        try {
            val recDir = StorageManager.getRecordingsDir(context)
            val uri = Uri.parse(recDir.absolutePath)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "مسیر فایل‌ها: ${StorageManager.getRecordingsDir(context).absolutePath}", Toast.LENGTH_LONG).show()
        }
    }
}
