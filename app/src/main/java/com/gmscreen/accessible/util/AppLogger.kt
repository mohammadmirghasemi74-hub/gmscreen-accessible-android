package com.gmscreen.accessible.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale

object AppLogger {

    private const val TAG = "GMScreenApp"
    private const val MAX_MEMORY_LINES = 1000
    private val memoryLogs = LinkedList<String>()
    private val lock = Any()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun i(tag: String, message: String) = log("INFO", tag, message, null)
    fun d(tag: String, message: String) = log("DEBUG", tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log("WARN", tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log("ERROR", tag, message, throwable)

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val timeStr = synchronized(dateFormat) { dateFormat.format(Date()) }
        val errStr = throwable?.let { "\n" + Log.getStackTraceString(it) } ?: ""
        val entry = "[$timeStr] [$level] [$tag] $message$errStr"

        when (level) {
            "ERROR" -> Log.e(tag, message, throwable)
            "WARN" -> Log.w(tag, message, throwable)
            "DEBUG" -> Log.d(tag, message, throwable)
            else -> Log.i(tag, message, throwable)
        }

        synchronized(lock) {
            memoryLogs.add(entry)
            while (memoryLogs.size > MAX_MEMORY_LINES) {
                memoryLogs.removeFirst()
            }

            try {
                val app = com.gmscreen.accessible.GMScreenApplication.instance
                val logFile = getLogFile(app)
                val parent = logFile.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                FileWriter(logFile, true).use { fw ->
                    fw.appendLine(entry)
                }
            } catch (_: Exception) {}
        }
    }

    fun getLogFile(context: Context): File {
        return try {
            val root = StorageManager.getAppRootDir(context)
            val logsDir = File(root, "logs")
            if (!logsDir.exists()) logsDir.mkdirs()
            File(logsDir, "gmscreen.log")
        } catch (_: Exception) {
            File(context.filesDir, "gmscreen.log")
        }
    }

    fun getRecentLogs(context: Context? = null): String {
        synchronized(lock) {
            if (memoryLogs.isEmpty()) {
                val ctx = context ?: try { com.gmscreen.accessible.GMScreenApplication.instance } catch (_: Exception) { null }
                if (ctx != null) {
                    try {
                        val file = getLogFile(ctx)
                        if (file.exists() && file.length() > 0) {
                            val lines = file.readLines(Charsets.UTF_8).takeLast(MAX_MEMORY_LINES)
                            memoryLogs.clear()
                            memoryLogs.addAll(lines)
                        }
                    } catch (_: Exception) {}
                }
            }

            val deviceHeader = """
                === سیستم خطایابی و ثبت رویدادهای GM Screen Accessible ===
                نسخه برنامه: v2.3.20
                دستگاه: ${Build.MANUFACTURER} ${Build.MODEL}
                نسخه اندروید: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
                تعداد سطرهای ثبت‌شده: ${memoryLogs.size}
                ==========================================================
            """.trimIndent()

            return if (memoryLogs.isEmpty()) {
                "$deviceHeader\n\nهنوز خطایی یا رویداد غیرمنتظره‌ای در سیستم ثبت نشده است."
            } else {
                "$deviceHeader\n\n" + memoryLogs.joinToString("\n")
            }
        }
    }

    fun exportLogsToTxt(context: Context): File {
        val root = StorageManager.getAppRootDir(context)
        val logsDir = File(root, "logs")
        if (!logsDir.exists()) logsDir.mkdirs()
        val exportFile = File(logsDir, "gmscreen_export.txt")
        val content = getRecentLogs(context)
        exportFile.writeText(content, Charsets.UTF_8)
        return exportFile
    }

    fun clearLogs(context: Context): Boolean {
        synchronized(lock) {
            memoryLogs.clear()
            return try {
                val f = getLogFile(context)
                if (f.exists()) {
                    f.writeText("", Charsets.UTF_8)
                }
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
