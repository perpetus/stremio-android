package com.stremio.mobile.data.model

data class AddonItem(
    val descriptor: com.stremio.core.types.addon.AddonDescriptor,
    val id: String,
    val name: String,
    val description: String?,
    val logo: String?,
    val version: String?,
    val types: List<String>,
    val transportUrl: String,
    val official: Boolean,
    val protected: Boolean,
    val installed: Boolean,
    val installable: Boolean,
    val upgradeable: Boolean,
    val uninstallable: Boolean,
    val configurable: Boolean,
    val configurationRequired: Boolean,
)
