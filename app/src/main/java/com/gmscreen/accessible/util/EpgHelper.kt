package com.gmscreen.accessible.util

import java.util.Calendar

data class EpgProgram(
    val title: String,
    val subTitle: String,
    val desc: String,
    val startRaw: String,
    val endRaw: String,
    val startMinutes: Int?,
    val endMinutes: Int?
)

object EpgHelper {

    fun parseEpgMinutes(valStr: String?): Int? {
        if (valStr.isNullOrBlank()) return null
        val s = valStr.trim()
        if (s.length == 4 && s.all { it.isDigit() }) {
            val h = s.substring(0, 2).toIntOrNull() ?: return null
            val m = s.substring(2, 4).toIntOrNull() ?: return null
            return h * 60 + m
        }
        if (s.length == 3 && s.all { it.isDigit() }) {
            val h = s.substring(0, 1).toIntOrNull() ?: return null
            val m = s.substring(1, 3).toIntOrNull() ?: return null
            return h * 60 + m
        }
        if (":" in s) {
            val parts = s.split(" ").last().split(":")
            if (parts.size >= 2) {
                val h = parts[0].toIntOrNull() ?: return null
                val m = parts[1].toIntOrNull() ?: return null
                return h * 60 + m
            }
        }
        return null
    }

    fun formatFriendlyTime(rawTime: String?): String {
        if (rawTime.isNullOrBlank()) return ""
        val m = parseEpgMinutes(rawTime) ?: return rawTime
        val h = m / 60
        val mn = m % 60
        return if (mn == 0) "ساعت $h" else "ساعت $h و $mn دقیقه"
    }

    fun extractAllPrograms(epgPayload: Map<String, Any>?): List<EpgProgram> {
        if (epgPayload == null) return emptyList()
        val dailyList = epgPayload["daily_epg_list"] as? List<*> ?: return emptyList()
        val programs = mutableListOf<EpgProgram>()

        for (day in dailyList) {
            if (day !is Map<*, *>) continue
            val evList = day["event_list"] as? List<*> ?: continue
            for (ev in evList) {
                if (ev !is Map<*, *>) continue
                val contentList = ev["event_content_list"] as? List<*> ?: continue

                var title = ""
                var subTitle = ""
                var desc = ""

                for (c in contentList) {
                    if (c !is Map<*, *>) continue
                    if (title.isBlank() && c["event_title"] != null) {
                        title = c["event_title"].toString().trim()
                    }
                    if (subTitle.isBlank() && c["event_sub_title"] != null) {
                        subTitle = c["event_sub_title"].toString().trim()
                    }
                    if (desc.isBlank() && c["event_desc"] != null) {
                        desc = c["event_desc"].toString().trim()
                    }
                }

                if (title.isBlank()) continue

                val startRaw = ev["event_start_time"]?.toString()?.trim() ?: ""
                val endRaw = ev["event_end_time"]?.toString()?.trim() ?: ""

                programs.add(
                    EpgProgram(
                        title = title,
                        subTitle = subTitle,
                        desc = desc,
                        startRaw = startRaw,
                        endRaw = endRaw,
                        startMinutes = parseEpgMinutes(startRaw),
                        endMinutes = parseEpgMinutes(endRaw)
                    )
                )
            }
        }
        return programs
    }

    fun extractCurrentAndNextEvent(epgPayload: Map<String, Any>?): Pair<EpgProgram?, EpgProgram?> {
        val programs = extractAllPrograms(epgPayload)
        if (programs.isEmpty()) return Pair(null, null)

        val cal = Calendar.getInstance()
        val curMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        var currentMatch: EpgProgram? = null
        var nextMatch: EpgProgram? = null

        for (i in programs.indices) {
            val p = programs[i]
            val s = p.startMinutes
            val e = p.endMinutes

            if (s != null && e != null) {
                val isCurrent = if (s <= e) {
                    curMinutes in s until e
                } else {
                    // Spans midnight (e.g. 23:00 to 01:00)
                    curMinutes >= s || curMinutes < e
                }

                if (isCurrent) {
                    currentMatch = p
                    if (i + 1 < programs.size) {
                        nextMatch = programs[i + 1]
                    }
                    break
                }
            }
        }

        if (currentMatch == null && programs.isNotEmpty()) {
            currentMatch = programs[0]
            if (programs.size > 1) nextMatch = programs[1]
        }

        return Pair(currentMatch, nextMatch)
    }

    fun buildEpgAnnouncement(
        current: EpgProgram?,
        next: EpgProgram?,
        detailLevel: Int
    ): String? {
        if (current == null) return null
        val title = current.title
        val sFmt = formatFriendlyTime(current.startRaw)
        val eFmt = formatFriendlyTime(current.endRaw)

        return when (detailLevel) {
            1 -> {
                if (sFmt.isNotBlank() && eFmt.isNotBlank()) {
                    "برنامه در حال پخش: $title، از $sFmt تا $eFmt"
                } else if (eFmt.isNotBlank()) {
                    "برنامه در حال پخش: $title، تا $eFmt"
                } else {
                    "برنامه در حال پخش: $title"
                }
            }
            2 -> {
                val timePart = if (eFmt.isNotBlank()) " تا $eFmt" else ""
                val nextPart = if (next != null) "؛ برنامه بعدی: ${next.title}" else ""
                "برنامه در حال پخش: $title$timePart$nextPart"
            }
            else -> { // 0
                "برنامه در حال پخش: $title"
            }
        }
    }
}
