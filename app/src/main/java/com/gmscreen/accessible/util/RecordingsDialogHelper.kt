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

    fun showRecordingsDialog(
        context: Context,
        announceView: View? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        val files = StorageManager.listRecordedFiles(context)
        if (files.isEmpty()) {
            val msg = "هیچ فایل ضبط‌شده‌ای در پوشه GMScreen یافت نشد"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            if (announceView != null) {
                AccessibilityHelper.announce(announceView, msg)
            }
            onDismiss?.invoke()
            return
        }

        val items = files.map { file ->
            val sizeMb = String.format("%.1f", file.length() / (1024f * 1024f))
            val parentName = file.parentFile?.name ?: ""
            val folderPrefix = if (parentName.isNotEmpty() && parentName != "recordings") "[$parentName] " else ""
            "$folderPrefix${file.name} ($sizeMb MB)"
        }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle("فایل‌های ضبط‌شده در گوشی (${files.size} فایل)")
            .setItems(items) { _, which ->
                val file = files.getOrNull(which)
                if (file != null) {
                    showFileActionsDialog(context, file, announceView) {
                        showRecordingsDialog(context, announceView, onDismiss)
                    }
                }
            }
            .setPositiveButton("پوشه فایل‌ها") { _, _ ->
                openRecordingsFolder(context)
                onDismiss?.invoke()
            }
            .setNeutralButton("حذف کل فایل‌ها") { _, _ ->
                confirmDeleteAllRecordings(context, files.size, announceView, onDismiss)
            }
            .setNegativeButton("بستن") { _, _ ->
                onDismiss?.invoke()
            }
            .setOnDismissListener {
                onDismiss?.invoke()
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
                if (announceView != null) {
                    AccessibilityHelper.announce(announceView, msg)
                }
                onDismiss?.invoke()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showFileActionsDialog(
        context: Context,
        file: File,
        announceView: View? = null,
        onFileChanged: (() -> Unit)? = null
    ) {
        val options = arrayOf("۱. پخش فایل", "۲. اشتراک‌گذاری", "۳. حذف فایل")
        MaterialAlertDialogBuilder(context)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playLocalFile(context, file)
                    1 -> shareLocalFile(context, file)
                    2 -> confirmDeleteSingleFile(context, file, announceView, onFileChanged)
                }
            }
            .setNegativeButton("انصراف", null)
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
                if (announceView != null) {
                    AccessibilityHelper.announce(announceView, msg)
                }
                onFileDeleted?.invoke()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    fun playLocalFile(context: Context, file: File) {
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
            Toast.makeText(context, "خطا در باز کردن پخش‌کننده: ${e.message}", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "مسیر فایل‌ها: /sdcard/GMScreen/recordings", Toast.LENGTH_LONG).show()
        }
    }
}
