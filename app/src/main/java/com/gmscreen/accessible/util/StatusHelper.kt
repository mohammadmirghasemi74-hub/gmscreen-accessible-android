package com.gmscreen.accessible.util

import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager
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
            parts.add("رسیور $modelName متصل است")
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
            val h = elapsed / 3600
            val m = (elapsed % 3600) / 60
            val s = elapsed % 60
            val dur = if (h > 0) "$h ساعت و $m دقیقه" else if (m > 0) "$m دقیقه و $s ثانیه" else "$s ثانیه"
            parts.add("کانال $recCh در حال ضبط روی گوشی است. مدت زمان ضبط: $dur")
        } else {
            parts.add("هیچ شبکه‌ای در حال ضبط روی گوشی نیست")
        }

        // 5. STB Flash Recording
        if (repository.isStbRecording.value) {
            val recCh = repository.stbRecordingChannelName.value.ifBlank {
                repository.currentChannel.value?.serviceName ?: "شبکه"
            }
            val startMs = repository.stbRecordingStartTime.value
            val elapsedSec = if (startMs > 0L) (System.currentTimeMillis() - startMs) / 1000 else 0L
            val h = elapsedSec / 3600
            val m = (elapsedSec % 3600) / 60
            val s = elapsedSec % 60
            val dur = if (h > 0) "$h ساعت و $m دقیقه" else if (m > 0) "$m دقیقه و $s ثانیه" else "$s ثانیه"
            parts.add("کانال $recCh در حال ضبط روی فلش رسیور است. مدت زمان ضبط: $dur")
        } else {
            parts.add("هیچ شبکه‌ای در حال ضبط روی فلش نیست")
        }

        // 6. STB Scheduled Timers (if known)
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

        // 7. Phone Timers
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
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val isScreenReaderActive = am != null && am.isEnabled && am.isTouchExplorationEnabled

        if (isScreenReaderActive && anchorView != null) {
            // When TalkBack / screen reader is active, announce through accessibility service so eSpeak reads it once
            AccessibilityHelper.announce(anchorView, summary)
        } else {
            // Sighted user or accessibility touch exploration not active: speak via internal TTS
            TtsHelper.speak(summary, interrupt = true)
        }
    }
}
