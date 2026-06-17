package com.stremio.mobile.data.model

data class EpisodeOption(
    val videoId: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val thumbnail: String?,
    val watched: Boolean,
    val isCurrent: Boolean,
)
