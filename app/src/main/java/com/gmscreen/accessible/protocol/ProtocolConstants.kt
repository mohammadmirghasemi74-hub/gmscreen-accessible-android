package com.gmscreen.accessible.protocol

object ProtocolConstants {
    const val DEFAULT_PORT = 20000
    const val DISCOVERY_PORT = 20000
    const val DISCOVERY_LISTEN_PORT = 20001
    const val OBFUSCATION_KEY: Byte = 0x5B

    const val REQ_GET_CHANNEL_LIST = 0
    const val REQ_GET_PLAYING_CHANNEL = 3
    const val REQ_SWITCH_CHANNEL = 1000
    const val REQ_DELETE_CHANNEL = 1002
    const val REQ_LOCK_CHANNEL = 1003
    const val REQ_MOVE_CHANNEL = 1005
    const val REQ_GET_STB_STATE = 1009
    const val REQ_GET_SATELLITE_LIST = 1010
    const val REQ_SAVE_SETTINGS = 1014
    const val REQ_SEND_KEY = 1040
    const val REQ_POWER_TOGGLE = 1041

    fun formatStbProductName(rawModel: String?): String {
        if (rawModel.isNullOrBlank()) return "رسیور نامشخص"
        val m = rawModel.trim()
        val u = m.uppercase()

        // If Persian brand already present
        val persianBrands = listOf("مدیااستار", "استارست", "ژننت", "ویارک", "تایگر")
        if (persianBrands.any { m.contains(it) }) return m

        return when {
            u.contains("MS-") || u.contains("MEDIASTAR") -> "مدیااستار $m"
            u.contains("SR-") || u.contains("STARSAT") -> "استارست $m"
            u.contains("GN-") || u.contains("GEANT") -> "ژننت $m"
            u.contains("VIARK") -> "ویارک $m"
            u.contains("TIGER") -> "تایگر $m"
            else -> "رسیور $m"
        }
    }
}
