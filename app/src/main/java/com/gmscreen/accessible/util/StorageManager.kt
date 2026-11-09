package com.gmscreen.accessible.util

import android.content.Context
import android.os.Environment
import com.gmscreen.accessible.data.model.Channel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StorageManager {

    private const val ROOT_DIR_NAME = "gm screen android"
    private const val DIR_RECORDINGS = "recordings"
    private const val DIR_BACKUPS = "backups"
    private const val DIR_PLAYLISTS = "playlists"

    fun getAppRootDir(context: Context): File {
        // Try external public storage first so user can easily browse files with any file manager
        val externalStorage = Environment.getExternalStorageDirectory()
        val publicDir = File(externalStorage, ROOT_DIR_NAME)
        if (publicDir.exists() || publicDir.mkdirs()) {
            return publicDir
        }

        // Fallback to Documents directory
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val docTarget = File(docsDir, ROOT_DIR_NAME)
        if (docTarget.exists() || docTarget.mkdirs()) {
            return docTarget
        }

        // Final fallback to app-specific external files dir
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val fallback = File(base, ROOT_DIR_NAME)
        if (!fallback.exists()) fallback.mkdirs()
        return fallback
    }

    fun getRecordingsDir(context: Context): File {
        val dir = File(getAppRootDir(context), DIR_RECORDINGS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getBackupsDir(context: Context): File {
        val dir = File(getAppRootDir(context), DIR_BACKUPS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPlaylistsDir(context: Context): File {
        val dir = File(getAppRootDir(context), DIR_PLAYLISTS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun generateRecordingFile(context: Context, channelName: String, isAudioOnly: Boolean): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanName = channelName.replace(Regex("[^a-zA-Z0-9_\u0600-\u06FF-]"), "_")
        val ext = if (isAudioOnly) ".mp3" else ".mp4"
        val fileName = "REC_${cleanName}_$timeStamp$ext"
        return File(getRecordingsDir(context), fileName)
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

    fun exportM3uPlaylist(context: Context, channels: List<Channel>, hostIp: String): File {
        val file = File(getPlaylistsDir(context), "gm_screen_channels.m3u")
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        for (ch in channels) {
            sb.append("#EXTINF:-1,${ch.serviceName}\n")
            sb.append("${ch.toSatIpUrl(hostIp)}\n")
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    fun listRecordedFiles(context: Context): List<File> {
        val dir = getRecordingsDir(context)
        return dir.listFiles { f -> f.isFile && (f.name.endsWith(".mp4") || f.name.endsWith(".mp3") || f.name.endsWith(".ts") || f.name.endsWith(".aac")) }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun listBackupFiles(context: Context): List<File> {
        val dir = getBackupsDir(context)
        return dir.listFiles { f -> f.isFile && (f.name.endsWith(".json") || f.name.endsWith(".sdx")) }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
