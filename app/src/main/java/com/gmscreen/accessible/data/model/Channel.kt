package com.gmscreen.accessible.data.model

data class Channel(
    val serviceId: String,
    val serviceName: String,
    val isRadio: Boolean = false,
    val isHd: Boolean = false,
    val isLocked: Boolean = false,
    val satIndex: Int = 0,
    val tpIndex: Int = 0,
    val videoPid: Int = 0,
    val audioPid: Int = 0,
    val pmtPid: Int = 0,
    var realChannelNum: Int = 0
) {
    fun getDisplayName(indexInFiltered: Int, keepGlobalNum: Boolean): String {
        val num = if (keepGlobalNum && realChannelNum > 0) realChannelNum else indexInFiltered
        val typeLabel = if (isRadio) "رادیو" else "کانال"
        val hdLabel = if (isHd) "اچ‌دی" else "معمولی"
        val lockLabel = if (isLocked) "قفل" else "آزاد"
        return "$typeLabel $num: $serviceName - $hdLabel - $lockLabel"
    }
}
