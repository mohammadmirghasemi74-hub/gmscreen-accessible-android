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
            "${file.name} ($sizeMb MB)"
        }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle("فایل‌های ضبط‌شده در حافظه گوشی (${files.size} فایل)")
            .setItems(items) { _, which ->
                val file = files.getOrNull(which)
                if (file != null) {
                    showFileActionsDialog(context, file, announceView, onDismiss)
                }
            }
            .setPositiveButton("باز کردن پوشه در مدیریت فایل") { _, _ ->
                openRecordingsFolder(context)
                onDismiss?.invoke()
            }
            .setNegativeButton("بستن") { _, _ ->
                onDismiss?.invoke()
            }
            .setOnDismissListener {
                onDismiss?.invoke()
            }
            .show()
    }

    private fun showFileActionsDialog(
        context: Context,
        file: File,
        announceView: View? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        val options = arrayOf("۱. پخش فایل", "۲. اشتراک‌گذاری", "۳. حذف فایل")
        MaterialAlertDialogBuilder(context)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playLocalFile(context, file)
                    1 -> shareLocalFile(context, file)
                    2 -> {
                        file.delete()
                        val msg = "فایل ${file.name} حذف شد"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (announceView != null) {
                            AccessibilityHelper.announce(announceView, msg)
                        }
                        onDismiss?.invoke()
                    }
                }
            }
            .setNegativeButton("بستن") { _, _ ->
                onDismiss?.invoke()
            }
            .show()
    }

    fun playLocalFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val nameLower = file.name.lowercase()
            val mime = if (nameLower.endsWith(".mp3") || nameLower.endsWith(".m4a") || nameLower.endsWith(".wav") || nameLower.endsWith(".aac") || nameLower.endsWith(".ogg")) "audio/*" else "video/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "پلیر مناسب در گوشی یافت نشد: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareLocalFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val nameLower = file.name.lowercase()
            val mime = if (nameLower.endsWith(".mp3") || nameLower.endsWith(".m4a") || nameLower.endsWith(".wav") || nameLower.endsWith(".aac") || nameLower.endsWith(".ogg")) "audio/*" else "video/*"
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
