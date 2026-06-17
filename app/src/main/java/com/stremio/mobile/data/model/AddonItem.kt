package com.stremio.mobile.data.model

data class AddonItem(
    val id: String,
    val name: String,
    val description: String?,
    val logo: String?,
    val version: String?,
    val types: List<String>,
    val transportUrl: String?,
)
