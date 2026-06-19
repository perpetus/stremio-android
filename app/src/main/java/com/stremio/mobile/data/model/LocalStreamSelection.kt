package com.stremio.mobile.data.model

data class LocalStreamSelection(
    val key: String,
    val addonTitle: String,
    val name: String,
    val description: String?,
    val quality: String?,
) {
    fun matches(option: StreamOption): Boolean {
        if (option.key == key) return true
        return option.addonTitle == addonTitle &&
            option.name == name &&
            option.description == description &&
            option.quality == quality
    }
}
