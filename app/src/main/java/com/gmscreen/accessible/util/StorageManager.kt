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

    fun generateRecordingFile(
        context: Context,
        channelName: String,
        isAudioOnly: Boolean,
        customExtension: String? = null
    ): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanName = channelName.replace(Regex("[^a-zA-Z0-9_\u0600-\u06FF-]"), "_")
        
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
        val fileName = "REC_${cleanName}_$timeStamp$ext"

        val primaryDir = getRecordingsDir(context)
        var targetFile = File(primaryDir, fileName)

        // Verify write access; if blocked by Scoped Storage, fallback safely to app storage
        try {
            if (!primaryDir.exists()) primaryDir.mkdirs()
            val testFos = FileOutputStream(targetFile, true)
            testFos.close()
        } catch (_: Exception) {
            val appStorageDir = File(context.getExternalFilesDir(null) ?: context.filesDir, DIR_RECORDINGS)
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

    private val RECORDED_EXTENSIONS = listOf(".ts", ".mp4", ".mkv", ".mp3", ".m4a", ".wav", ".aac", ".ogg")

    fun listRecordedFiles(context: Context): List<File> {
        val files = mutableListOf<File>()

        // 1. Files from primary root (/sdcard/GMScreen/recordings)
        try {
            val primaryDir = File(File(Environment.getExternalStorageDirectory(), ROOT_DIR_NAME), DIR_RECORDINGS)
            if (primaryDir.exists()) {
                val rootFiles = primaryDir.listFiles { f ->
                    f.isFile && RECORDED_EXTENSIONS.any { ext -> f.name.lowercase().endsWith(ext) }
                }
                if (rootFiles != null) files.addAll(rootFiles)
            }
        } catch (_: Exception) {}

        // 2. Files from app external files dir
        try {
            val appDir = File(context.getExternalFilesDir(null) ?: context.filesDir, DIR_RECORDINGS)
            if (appDir.exists()) {
                val appFiles = appDir.listFiles { f ->
                    f.isFile && RECORDED_EXTENSIONS.any { ext -> f.name.lowercase().endsWith(ext) }
                }
                if (appFiles != null) {
                    for (af in appFiles) {
                        if (files.none { it.name == af.name }) {
                            files.add(af)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return files.sortedByDescending { it.lastModified() }
    }

    fun listBackupFiles(context: Context): List<File> {
        val dir = getBackupsDir(context)
        return dir.listFiles { f -> f.isFile && (f.name.endsWith(".json") || f.name.endsWith(".sdx")) }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
