package com.gmscreen.accessible.data.model

data class Satellite(
    val satIndex: Int,
    val satName: String,
    val channelCount: Int = 0
) {
    val satelliteId: Int get() = satIndex
    val satelliteName: String get() = satName

    override fun toString(): String {
        return if (satIndex == -1) {
            satName
        } else {
            "$satName ($channelCount شبکه)"
        }
    }
}
