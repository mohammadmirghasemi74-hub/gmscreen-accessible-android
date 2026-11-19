package com.gmscreen.accessible.util

import java.util.Calendar
import java.util.Date

object JalaliCalendar {

    val JALALI_MONTH_NAMES = listOf(
        "", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy else gy - 1
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gDm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return Triple(jy, jm, jd)
    }

    fun formatRecordingFilename(
        pattern: String,
        recCounter: Int,
        channelName: String,
        ext: String,
        customName: String = ""
    ): String {
        val cal = Calendar.getInstance()
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)

        val (jy, jm, jd) = gregorianToJalali(gy, gm, gd)
        val safeCh = channelName.replace(Regex("[\\\\/*?:\"<>|]"), "_").trim().ifEmpty { "Channel" }
        val cntStr = "%02d".format(recCounter)
        val cleanExt = if (ext.startsWith(".")) ext else ".$ext"

        if (pattern == "custom" && customName.isNotBlank()) {
            val safeCustom = customName.replace(Regex("[\\\\/*?:\"<>|]"), "_").trim()
            if (safeCustom.isNotBlank()) {
                val dateStr = "%04d-%02d-%02d".format(jy, jm, jd)
                return "%s_%s_%s%s".format(cntStr, safeCustom, dateStr, cleanExt)
            }
        }

        val mName = if (jm in 1..12) JALALI_MONTH_NAMES[jm] else jm.toString()

        return when (pattern) {
            "jalali_month_year" -> {
                val dateStr = "$jd $mName $jy"
                "${cntStr}_${safeCh}_${dateStr}${cleanExt}"
            }
            "jalali_month" -> {
                val dateStr = "$jd $mName"
                "${cntStr}_${safeCh}_${dateStr}${cleanExt}"
            }
            "gregorian" -> {
                val dateStr = "%04d-%02d-%02d".format(gy, gm, gd)
                "${cntStr}_${safeCh}_${dateStr}${cleanExt}"
            }
            else -> { // "jalali_numeric" (default)
                val dateStr = "%04d-%02d-%02d".format(jy, jm, jd)
                "${cntStr}_${safeCh}_${dateStr}${cleanExt}"
            }
        }
    }

    fun formatJalaliLogFilename(ext: String): String {
        val cal = Calendar.getInstance()
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        val (jy, jm, jd) = gregorianToJalali(gy, gm, gd)
        val mName = if (jm in 1..12) JALALI_MONTH_NAMES[jm] else "$jm"
        val cleanExt = if (ext.startsWith(".")) ext else ".$ext"
        return "گزارش_سیستم_${jd}_${mName}_${jy}_ساعت_${hour}_و_${minute}$cleanExt"
    }
}
