package com.gmscreen.accessible.util

import android.content.Context
import android.os.Build
import android.os.Environment
import com.gmscreen.accessible.data.model.Channel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StorageManager {

    private const val ROOT_DIR_NAME = "GMScreen"
    private const val DIR_RECORDINGS = "recordings"
    private const val DIR_BACKUPS = "backups"
    private const val DIR_PLAYLISTS = "playlists"

    fun getAppRootDir(context: Context): File {
        // 1. If permission is granted or older Android, use /sdcard/GMScreen
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            try {
                val externalStorage = Environment.getExternalStorageDirectory()
                val rootDir = File(externalStorage, ROOT_DIR_NAME)
                if (rootDir.exists() || rootDir.mkdirs()) {
                    if (rootDir.canWrite()) return rootDir
                }
            } catch (_: Exception) {}
        }

        // 2. Fallback to app external storage (guaranteed 100% writeable without permissions)
        val extAppDir = context.getExternalFilesDir(null) ?: context.filesDir
        val fallbackRoot = File(extAppDir, ROOT_DIR_NAME)
        if (!fallbackRoot.exists()) fallbackRoot.mkdirs()
        return fallbackRoot
    }

    fun getRecordingsDir(context: Context): File {
        val appConfig = AppConfig(context)
        val custom = appConfig.customRecordingsDir
        if (custom.isNotBlank()) {
            val customDir = File(custom)
            if (customDir.exists() || customDir.mkdirs()) {
                if (customDir.canWrite()) return customDir
            }
        }
        val root = getAppRootDir(context)
        val dir = File(root, DIR_RECORDINGS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getBackupsDir(context: Context): File {
        val root = getAppRootDir(context)
        val dir = File(root, DIR_BACKUPS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPlaylistsDir(context: Context): File {
        val root = getAppRootDir(context)
        val dir = File(root, DIR_PLAYLISTS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getChannelRecordingsDir(context: Context, channelName: String): File {
        val safeCh = channelName.replace(Regex("[\\\\/*?:\"<>|]"), "_").trim().ifEmpty { "Channel" }
        val channelDir = File(getRecordingsDir(context), safeCh)
        if (!channelDir.exists()) channelDir.mkdirs()
        return channelDir
    }

    fun generateRecordingFile(
        context: Context,
        channelName: String,
        isAudioOnly: Boolean,
        customExtension: String? = null,
        customName: String = ""
    ): File {
        val appConfig = AppConfig(context)
        val ext = when {
            !customExtension.isNullOrBlank() -> if (customExtension.startsWith(".")) customExtension else ".$customExtension"
            isAudioOnly -> {
                val f = appConfig.audioRecFormat.lowercase()
                if (f.startsWith(".")) f else ".$f"
            }
            else -> {
                val f = appConfig.videoRecFormat.lowercase()
                if (f.startsWith(".")) f else ".$f"
            }
        }

        val primaryChannelDir = getChannelRecordingsDir(context, channelName)
        var recCounter = appConfig.getNextRecordingNumber()

        var fileName = JalaliCalendar.formatRecordingFilename(
            pattern = appConfig.recNamingPattern,
            recCounter = recCounter,
            channelName = channelName,
            ext = ext,
            customName = customName
        )

        var targetFile = File(primaryChannelDir, fileName)
        while (targetFile.exists()) {
            recCounter = appConfig.getNextRecordingNumber()
            fileName = JalaliCalendar.formatRecordingFilename(
                pattern = appConfig.recNamingPattern,
                recCounter = recCounter,
                channelName = channelName,
                ext = ext,
                customName = customName
            )
            targetFile = File(primaryChannelDir, fileName)
        }

        // Verify write access; if blocked by Scoped Storage, fallback safely to app storage
        try {
            if (!primaryChannelDir.exists()) primaryChannelDir.mkdirs()
            val testFos = FileOutputStream(targetFile, true)
            testFos.close()
        } catch (_: Exception) {
            val appStorageDir = File(
                File(context.getExternalFilesDir(null) ?: context.filesDir, DIR_RECORDINGS),
                primaryChannelDir.name
            )
            if (!appStorageDir.exists()) appStorageDir.mkdirs()
            targetFile = File(appStorageDir, fileName)
            try {
                val test2 = FileOutputStream(targetFile, true)
                test2.close()
            } catch (_: Exception) {}
        }

        return targetFile
    }

    fun saveBackupChannels(context: Context, channels: List<Channel>, modelName: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanModel = modelName.ifBlank { "STB" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(getBackupsDir(context), "Backup_${cleanModel}_$timeStamp.json")
        val json = Gson().toJson(channels)
        file.writeText(json, Charsets.UTF_8)
        return file
    }

    fun loadBackupChannels(file: File): List<Channel>? {
        return try {
            val json = file.readText(Charsets.UTF_8)
            val type = object : TypeToken<List<Channel>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    fun exportM3uPlaylist(
        context: Context,
        channels: List<Channel>,
        hostIp: String,
        channelUrls: Map<String, String>? = null
    ): File {
        val file = File(getPlaylistsDir(context), "gm_screen_channels.m3u")
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        for (ch in channels) {
            val url = channelUrls?.get(ch.serviceId) ?: ch.toSatIpUrl(hostIp)
            sb.append("#EXTINF:-1 tvg-id=\"${ch.serviceId}\",${ch.serviceName}\n")
            sb.append("$url\n")
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    fun exportPlaylists(
        context: Context,
        channels: List<Channel>,
        hostIp: String,
        doIndividual: Boolean,
        doCombined: Boolean,
        channelUrls: Map<String, String>? = null
    ): Int {
        var createdCount = 0
        val targetDir = getPlaylistsDir(context)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

        fun sanitizeFilename(name: String): String {
            return name.replace(Regex("[\\\\/*?:\"<>|]"), "_").trim().ifEmpty { "Channel" }
        }

        if (doIndividual) {
            channels.forEachIndexed { idx, ch ->
                val num = idx + 1
                val safeName = sanitizeFilename(ch.serviceName)
                val fileName = String.format(Locale.US, "%03d - %s.m3u", num, safeName)
                val file = File(targetDir, fileName)
                val url = channelUrls?.get(ch.serviceId) ?: ch.toSatIpUrl(hostIp)
                val lines = listOf(
                    "#EXTM3U",
                    "#EXTINF:-1 tvg-id=\"${ch.serviceId}\",${ch.serviceName}",
                    url
                )
                file.writeText(lines.joinToString("\n") + "\n", Charsets.UTF_8)
                createdCount++
            }
        }

        if (doCombined) {
            val fileName = "All_Selected_Channels_$timeStamp.m3u"
            val file = File(targetDir, fileName)
            val sb = StringBuilder()
            sb.append("#EXTM3U\n")
            channels.forEach { ch ->
                val url = channelUrls?.get(ch.serviceId) ?: ch.toSatIpUrl(hostIp)
                sb.append("#EXTINF:-1 tvg-id=\"${ch.serviceId}\",${ch.serviceName}\n")
                sb.append("$url\n")
            }
            file.writeText(sb.toString(), Charsets.UTF_8)
            createdCount++
        }

        return createdCount
    }

    fun exportChannelsToWordDocx(
        context: Context,
        channels: List<Channel>,
        stbModel: String
    ): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val destFile = File(getBackupsDir(context), "Channels_List_$timeStamp.docx")

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        fun esc(text: String?): String {
            return (text ?: "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }

        val headers = listOf(
            Pair("ردیف", 1000),
            Pair("نام شبکه", 3200),
            Pair("نوع", 1300),
            Pair("ماهواره", 2200),
            Pair("فرکانس", 1400),
            Pair("پلاریته", 1100),
            Pair("سیمبل‌ریت", 1400)
        )

        val hdrSb = StringBuilder()
        hdrSb.append("<w:tr><w:trPr><w:tblHeader/><w:bidi/></w:trPr>")
        for ((hTitle, w) in headers) {
            hdrSb.append("<w:tc><w:tcPr><w:tcW w:w=\"$w\" w:type=\"dxa\"/><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"2B6CB0\"/></w:tcPr>")
            hdrSb.append("<w:p><w:pPr><w:jc w:val=\"center\"/><w:bidi/></w:pPr>")
            hdrSb.append("<w:r><w:rPr><w:rFonts w:ascii=\"Tahoma\" w:cs=\"Tahoma\"/><w:b/><w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/><w:color w:val=\"FFFFFF\"/><w:rtl/></w:rPr>")
            hdrSb.append("<w:t>${esc(hTitle)}</w:t></w:r></w:p></w:tc>")
        }
        hdrSb.append("</w:tr>")

        val rowsSb = StringBuilder()
        rowsSb.append(hdrSb)

        channels.forEachIndexed { idx, ch ->
            val rowIdx = idx + 1
            val name = ch.serviceName.ifBlank { "شبکه $rowIdx" }
            val chType = if (ch.isRadio) "رادیویی" else "تلویزیونی"
            val satName = ch.satelliteName.ifBlank { if (ch.satIndex > 0) "ماهواره #${ch.satIndex}" else "نامشخص" }
            val freq = if (ch.frequency > 0) ch.frequency.toString() else "-"
            val pol = "-"
            val sr = "-"

            val rowData = listOf(
                Pair(rowIdx.toString(), 1000),
                Pair(name, 3200),
                Pair(chType, 1300),
                Pair(satName, 2200),
                Pair(freq, 1400),
                Pair(pol, 1100),
                Pair(sr, 1400)
            )

            val bgColor = if (rowIdx % 2 == 0) "F7FAFC" else "FFFFFF"
            rowsSb.append("<w:tr><w:trPr><w:bidi/></w:trPr>")
            for ((valText, w) in rowData) {
                rowsSb.append("<w:tc><w:tcPr><w:tcW w:w=\"$w\" w:type=\"dxa\"/><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"$bgColor\"/></w:tcPr>")
                rowsSb.append("<w:p><w:pPr><w:jc w:val=\"center\"/><w:bidi/></w:pPr>")
                rowsSb.append("<w:r><w:rPr><w:rFonts w:ascii=\"Tahoma\" w:cs=\"Tahoma\"/><w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/><w:rtl/></w:rPr>")
                rowsSb.append("<w:t>${esc(valText)}</w:t></w:r></w:p></w:tc>")
            }
            rowsSb.append("</w:tr>")
        }

        val tblXml = """<w:tbl>
  <w:tblPr>
    <w:tblW w:w="0" w:type="auto"/>
    <w:jc w:val="center"/>
    <w:bidiVisual/>
    <w:tblBorders>
      <w:top w:val="single" w:sz="6" w:space="0" w:color="CBD5E0"/>
      <w:left w:val="single" w:sz="6" w:space="0" w:color="CBD5E0"/>
      <w:bottom w:val="single" w:sz="6" w:space="0" w:color="CBD5E0"/>
      <w:right w:val="single" w:sz="6" w:space="0" w:color="CBD5E0"/>
      <w:insideH w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>
      <w:insideV w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>
    </w:tblBorders>
  </w:tblPr>
  $rowsSb
</w:tbl>"""

        val titleText = esc("لیست شبکه‌ها و مشخصات فرکانس رسیور ${stbModel.ifBlank { "متصل" }}".trim())
        val summaryText = esc("تعداد کل شبکه‌ها: ${channels.size}")

        val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p><w:pPr><w:jc w:val="center"/><w:bidi/></w:pPr>
      <w:r><w:rPr><w:rFonts w:ascii="Tahoma" w:cs="Tahoma"/><w:b/><w:sz w:val="34"/><w:szCs w:val="34"/><w:color w:val="1A365D"/><w:rtl/></w:rPr>
        <w:t>$titleText</w:t></w:r></w:p>
    <w:p><w:pPr><w:jc w:val="center"/><w:bidi/></w:pPr>
      <w:r><w:rPr><w:rFonts w:ascii="Tahoma" w:cs="Tahoma"/><w:sz w:val="22"/><w:szCs w:val="22"/><w:color w:val="4A5568"/><w:rtl/></w:rPr>
        <w:t>$summaryText</w:t></w:r></w:p>
    <w:p/>
    $tblXml
    <w:sectPr><w:bidi/></w:sectPr>
  </w:body>
</w:document>"""

        destFile.parentFile?.mkdirs()
        val zos = java.util.zip.ZipOutputStream(java.io.FileOutputStream(destFile))
        fun addZipEntry(name: String, content: String) {
            val entry = java.util.zip.ZipEntry(name)
            zos.putNextEntry(entry)
            val bytes = content.toByteArray(Charsets.UTF_8)
            zos.write(bytes, 0, bytes.size)
            zos.closeEntry()
        }
        addZipEntry("[Content_Types].xml", contentTypes)
        addZipEntry("_rels/.rels", rels)
        addZipEntry("word/document.xml", docXml)
        zos.finish()
        zos.close()

        return destFile
    }

    private val RECORDED_EXTENSIONS = listOf(".ts", ".mp4", ".mkv", ".mp3", ".m4a", ".wav", ".aac", ".ogg")

    private fun collectFilesRecursively(dir: File, result: MutableList<File>) {
        val list = dir.listFiles() ?: return
        for (item in list) {
            if (item.isDirectory) {
                collectFilesRecursively(item, result)
            } else if (item.isFile) {
                val nameLower = item.name.lowercase()
                if (RECORDED_EXTENSIONS.any { nameLower.endsWith(it) }) {
                    if (result.none { it.absolutePath == item.absolutePath }) {
                        result.add(item)
                    }
                }
            }
        }
    }

    fun listRecordedFiles(context: Context): List<File> {
        val files = mutableListOf<File>()

        // 1. Files from primary root (/sdcard/GMScreen/recordings)
        try {
            val primaryDir = File(File(Environment.getExternalStorageDirectory(), ROOT_DIR_NAME), DIR_RECORDINGS)
            if (primaryDir.exists()) {
                collectFilesRecursively(primaryDir, files)
            }
        } catch (_: Exception) {}

        // 2. Files from app external files dir
        try {
            val appDir = File(context.getExternalFilesDir(null) ?: context.filesDir, DIR_RECORDINGS)
            if (appDir.exists()) {
                collectFilesRecursively(appDir, files)
            }
        } catch (_: Exception) {}

        return files.sortedByDescending { it.lastModified() }
    }

    fun deleteAllRecordings(context: Context): Int {
        val files = listRecordedFiles(context)
        var count = 0
        for (f in files) {
            try {
                if (f.delete()) count++
            } catch (_: Exception) {}
        }

        // Clean up empty channel folders
        val cleanupDirs = listOf(
            File(File(Environment.getExternalStorageDirectory(), ROOT_DIR_NAME), DIR_RECORDINGS),
            File(context.getExternalFilesDir(null) ?: context.filesDir, DIR_RECORDINGS)
        )
        for (baseDir in cleanupDirs) {
            try {
                val subDirs = baseDir.listFiles { d -> d.isDirectory } ?: emptyArray()
                for (sd in subDirs) {
                    val remaining = sd.listFiles()
                    if (remaining.isNullOrEmpty()) {
                        sd.delete()
                    }
                }
            } catch (_: Exception) {}
        }

        return count
    }

    fun listBackupFiles(context: Context): List<File> {
        val dir = getBackupsDir(context)
        return dir.listFiles { f ->
            f.isFile && (f.name.endsWith(".json", ignoreCase = true) ||
                         f.name.endsWith(".sdx", ignoreCase = true) ||
                         f.name.endsWith(".docx", ignoreCase = true))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun listPlaylistFiles(context: Context): List<File> {
        val dir = getPlaylistsDir(context)
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".m3u", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
