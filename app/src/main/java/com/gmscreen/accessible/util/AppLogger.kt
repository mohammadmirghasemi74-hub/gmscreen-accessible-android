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

    private var currentSessionErrorFile: File? = null

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
                val logOnlyErrors = try { app.appConfig.logOnlyErrors } catch (e: Exception) { true }

                if (logOnlyErrors) {
                    // Smart Error-Only logging: only create/write to file when an actual error occurs!
                    if (level == "ERROR" || throwable != null) {
                        val errorFile = getOrCreateSessionErrorFile(app)
                        val breadcrumbs = memoryLogs.takeLast(20).joinToString("\n")
                        val errorReport = buildString {
                            appendLine("==============================================================")
                            appendLine("گزارش خطای هوشمند GM Screen Accessible")
                            appendLine("زمان وقوع: $timeStr")
                            appendLine("دستگاه: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
                            appendLine("تگ خطا: $tag")
                            appendLine("پیام خطا: $message")
                            if (errStr.isNotBlank()) {
                                appendLine("جزئیات استک‌تریس:")
                                appendLine(errStr)
                            }
                            appendLine("--------------------------------------------------------------")
                            appendLine("رویدادهای اخیر سیستم قبل از بروز خطا (Context Breadcrumbs):")
                            appendLine(breadcrumbs)
                            appendLine("==============================================================")
                            appendLine()
                        }
                        val parent = errorFile.parentFile
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs()
                        }
                        FileWriter(errorFile, true).use { fw ->
                            fw.append(errorReport)
                        }
                    } else {
                        // In error-only mode, non-error logs are kept in memory breadcrumbs only
                    }
                } else {
                    // Full logging mode
                    val logFile = getLogFile(app)
                    val parent = logFile.parentFile
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs()
                    }
                    FileWriter(logFile, true).use { fw ->
                        fw.appendLine(entry)
                    }
                }
                Unit
            } catch (e: Exception) {
            }
        }
    }

    private fun getOrCreateSessionErrorFile(context: Context): File {
        if (currentSessionErrorFile != null) {
            return currentSessionErrorFile!!
        }
        val dir = getLogsDir(context)
        val fileTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val f = File(dir, "error_log_$fileTime.txt")
        currentSessionErrorFile = f
        return f
    }

    fun getLogsDir(context: Context): File {
        val root = StorageManager.getAppRootDir(context)
        val logsDir = File(root, "logs")
        if (!logsDir.exists()) logsDir.mkdirs()
        return logsDir
    }

    fun getLogFile(context: Context): File {
        return try {
            val dir = getLogsDir(context)
            File(dir, "gmscreen.log")
        } catch (_: Exception) {
            File(context.filesDir, "gmscreen.log")
        }
    }

    private fun ensureLogsLoaded(context: Context?) {
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
        }
    }

    fun getRecentLogs(context: Context? = null): String {
        ensureLogsLoaded(context)
        synchronized(lock) {
            val deviceHeader = """
                === سیستم خطایابی و ثبت رویدادهای GM Screen Accessible ===
                دستگاه: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})
                تعداد سطرهای ثبت‌شده: ${memoryLogs.size}
                ==========================================================
            """.trimIndent()

            return if (memoryLogs.isEmpty()) {
                "$deviceHeader\n\nهنوز خطایی یا رویدادی در سیستم ثبت نشده است."
            } else {
                "$deviceHeader\n\n" + memoryLogs.joinToString("\n")
            }
        }
    }

    fun getRecentLogsHtml(context: Context? = null): String {
        ensureLogsLoaded(context)
        val plainText = getRecentLogs(context)
        val escaped = plainText
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")

        return """
            <!DOCTYPE html>
            <html lang="fa" dir="rtl">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>گزارش کامل رویدادها و خطاهای سیستم</title>
                <style>
                    body {
                        font-family: Tahoma, Segoe UI, sans-serif;
                        background-color: #121212;
                        color: #e0e0e0;
                        line-height: 1.8;
                        margin: 16px;
                        font-size: 14px;
                    }
                    h1 {
                        color: #64b5f6;
                        border-bottom: 2px solid #1976d2;
                        padding-bottom: 8px;
                        font-size: 18px;
                    }
                    .info-box {
                        background-color: #1e1e1e;
                        border: 1px solid #333333;
                        border-radius: 8px;
                        padding: 12px;
                        margin-bottom: 16px;
                    }
                    .log-container {
                        background-color: #1a1a1a;
                        border: 1px solid #444;
                        border-radius: 6px;
                        padding: 14px;
                        font-family: monospace;
                        font-size: 13px;
                        white-space: pre-wrap;
                        word-break: break-all;
                        direction: ltr;
                        text-align: left;
                        color: #a5d6a7;
                    }
                </style>
            </head>
            <body>
                <h1>گزارش رویدادها و خطایابی نرم‌افزار GM Screen</h1>
                <div class="info-box">
                    دستگاه: ${Build.MANUFACTURER} ${Build.MODEL} (اندروید ${Build.VERSION.RELEASE})<br>
                    این گزارش جهت بررسی خطاهای شبکه، رسیور و ریموت کنترل تهیه شده است.
                </div>
                <div class="log-container">$escaped</div>
            </body>
            </html>
        """.trimIndent()
    }

    fun exportLogs(context: Context, asHtml: Boolean): File {
        val dir = getLogsDir(context)
        val ext = if (asHtml) "html" else "txt"
        val fileName = JalaliCalendar.formatJalaliLogFilename(ext)
        val file = File(dir, fileName)
        val content = if (asHtml) getRecentLogsHtml(context) else getRecentLogs(context)
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    fun listExportedLogFiles(context: Context): List<File> {
        val dir = getLogsDir(context)
        val files = dir.listFiles { f ->
            f.isFile && (f.name.endsWith(".html", ignoreCase = true) || f.name.endsWith(".txt", ignoreCase = true))
                    && f.name != "gmscreen.log"
        } ?: emptyArray()
        return files.sortedByDescending { it.lastModified() }
    }

    fun deleteExportedLogFile(file: File): Boolean {
        return try {
            if (file.exists()) file.delete() else true
        } catch (_: Exception) {
            false
        }
    }

    fun deleteAllExportedLogs(context: Context): Int {
        val files = listExportedLogFiles(context)
        var count = 0
        for (f in files) {
            try {
                if (f.delete()) count++
            } catch (_: Exception) {}
        }
        return count
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
