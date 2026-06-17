package com.stremio.mobile.data.model

import com.stremio.mobile.core.CoreStream

data class StreamOption(
    val key: String,
    val name: String,
    val description: String?,
    val addonTitle: String,
    val quality: String?,
    val core: CoreStream,
    val seeds: String? = null,
    val size: String? = null,
    val origin: String? = null,
    val cleanDescription: String? = null,
)
