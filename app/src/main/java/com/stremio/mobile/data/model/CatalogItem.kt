package com.stremio.mobile.data.model

data class CatalogItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val releaseInfo: String?,
    val imdbRating: String?,
    val progress: Float? = null,
    val inCinema: Boolean = false,
    val watched: Boolean = false,
    val remainingEpisodes: Int? = null,
    val continueWatchingVideoId: String? = null,
    val isContinueWatching: Boolean = false,
)
