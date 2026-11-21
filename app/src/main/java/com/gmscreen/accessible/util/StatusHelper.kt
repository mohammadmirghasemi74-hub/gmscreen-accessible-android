package com.gmscreen.accessible.util

import android.content.Context
import android.view.View
import com.gmscreen.accessible.data.StbRepository
import com.gmscreen.accessible.player.StreamRecorderManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object StatusHelper {

    fun getStatusSummary(context: Context, repository: StbRepository): String {
        val parts = mutableListOf<String>()

        // 1. Connection
        val ip = repository.connectedIp.value
        val model = repository.deviceModel.value
        if (ip.isNotBlank()) {
            val modelName = model.ifBlank { "رسیور" }
            parts.add("رسیور متصل است: $modelName در آدرس $ip")
        } else {
            parts.add("رسیور متصل نیست")
        }

        // 2. Active Channel
        val ch = repository.currentChannel.value
        if (ch != null && ch.serviceName.isNotBlank()) {
            val numStr = if (ch.realChannelNum > 0) "شماره ${ch.realChannelNum}" else ""
            val typeStr = if (ch.isRadio) "رادیو" else "شبکه"
            parts.add("شبکه فعال روی رسیور: $typeStr ${ch.serviceName} $numStr".trim())
        } else {
            val totalCh = repository.channels.value.size
            if (totalCh > 0) {
                parts.add("تعداد $totalCh شبکه در برنامه بارگذاری شده است")
            }
        }

        // 3. Playback on Phone
        if (StreamRecorderManager.isStreaming) {
            val chName = StreamRecorderManager.currentChannel?.serviceName ?: "شبکه"
            parts.add("پخش زنده روی گوشی فعال است برای شبکه $chName")
        } else {
            parts.add("پخش زنده روی گوشی غیرفعال است")
        }

        // 4. Phone Direct Recording
        if (StreamRecorderManager.isRecording) {
            val recCh = StreamRecorderManager.currentChannel?.serviceName ?: "شبکه"
            val elapsed = StreamRecorderManager.recordingElapsedSeconds
            val m = elapsed / 60
            val s = elapsed % 60
            val dur = if (m > 0) "$m دقیقه و $s ثانیه" else "$s ثانیه"
            parts.add("ضبط مستقیم روی گوشی فعال است برای شبکه $recCh، مدت زمان گذشته: $dur")
        } else {
            parts.add("هیچ فایلی در حال ضبط روی گوشی نیست")
        }

        // 5. STB Flash Recording (if known)
        val stbTimersFile = File(StorageManager.getRecordingsDir(context), "stb_scheduled_timers.json")
        var stbTimersCount = 0
        if (stbTimersFile.exists()) {
            try {
                val json = stbTimersFile.readText(Charsets.UTF_8)
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val list: List<Map<String, Any>> = Gson().fromJson(json, type) ?: emptyList()
                stbTimersCount = list.size
            } catch (_: Exception) {}
        }
        if (stbTimersCount > 0) {
            parts.add("تعداد $stbTimersCount زمان‌بندی ضبط روی فلش رسیور ثبت شده است")
        }

        // 6. Phone Timers
        val phoneTimersFile = File(StorageManager.getRecordingsDir(context), "scheduled_timers.json")
        var phoneTimersCount = 0
        if (phoneTimersFile.exists()) {
            try {
                val json = phoneTimersFile.readText(Charsets.UTF_8)
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val list: List<Map<String, Any>> = Gson().fromJson(json, type) ?: emptyList()
                phoneTimersCount = list.size
            } catch (_: Exception) {}
        }
        if (phoneTimersCount > 0) {
            parts.add("تعداد $phoneTimersCount زمان‌بندی ضبط خودکار در صف گوشی قرار دارد")
        }

        return parts.joinToString(". ") + "."
    }

    fun announceStatus(context: Context, repository: StbRepository, anchorView: View? = null) {
        val summary = getStatusSummary(context, repository)
        if (anchorView != null) {
            AccessibilityHelper.announce(anchorView, summary)
        }
        TtsHelper.speak(summary, interrupt = true)
    }
}
