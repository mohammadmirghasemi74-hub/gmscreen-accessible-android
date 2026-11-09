package com.gmscreen.accessible.data.model

data class StbDevice(
    val ip: String,
    val mac: String,
    val rawModel: String,
    val formattedModel: String
) {
    val productName: String get() = formattedModel

    override fun toString(): String {
        return "$formattedModel ($ip)"
    }
}
