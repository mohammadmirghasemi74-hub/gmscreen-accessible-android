package com.gmscreen.accessible.data.model

data class StbState(
    val productName: String = "",
    val softwareVersion: String = "",
    val serialNumber: String = "",
    val powerMode: Int = 1,
    val sleepTime: Int = 0,
    val sleepSwitch: Int = 0,
    val curChannelType: Int = 0, // 0 = TV, 1 = Radio
    val channelNum: Int = 0,
    val playingServiceId: String = ""
)
