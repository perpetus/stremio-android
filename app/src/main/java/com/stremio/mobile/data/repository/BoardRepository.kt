package com.stremio.mobile.data.repository

import com.stremio.mobile.core.StremioCore
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BoardRepository(private val core: StremioCore) {

    fun getBoardFlow(): Flow<com.stremio.core.models.CatalogsWithExtra> = core.board()

    fun getContinueWatchingFlow(): Flow<CatalogShelf> {
        return core.continueWatchingPreview().map { preview ->
            extractContinueWatching(preview)
        }
    }

    fun loadBoardRange(start: Int, end: Int) {
        core.loadBoardRange(start, end)
    }

    fun loadBoard() {
        core.loadBoard()
    }

    fun extractBoardShelves(board: com.stremio.core.models.CatalogsWithExtra): List<CatalogShelf> {
        return board.catalogs.flatMap { catalog ->
            catalog.pages.map { page ->
                val items = when (val content = page.content) {
                    is com.stremio.core.models.LoadablePage.Content.Ready -> {
                        content.value.metaItems.map { item ->
                            CatalogItem(
                                id = item.id,
                                type = item.type,
                                name = item.name,
                                poster = item.poster,
                                background = item.background,
                                releaseInfo = item.releaseInfo,
                                imdbRating = null,
                                inCinema = item.inCinema,
                                watched = item.watched
                            )
                        }
                    }
                    else -> emptyList()
                }
                val error = when (val content = page.content) {
                    is com.stremio.core.models.LoadablePage.Content.Error -> content.value.message
                    else -> null
                }
                val rawTitle = page.catalogName ?: page.title
                val typeSuffix = when (page.catalogType) {
                    "movie" -> "Movies"
                    "series" -> "Series"
                    else -> ""
                }
                val title = if (typeSuffix.isNotEmpty() && (rawTitle.equals("Popular", ignoreCase = true) || rawTitle.equals("Featured", ignoreCase = true))) {
                    "$rawTitle $typeSuffix"
                } else {
                    rawTitle
                }
                CatalogShelf(
                    title = title,
                    items = items,
                    isLoading = page.content is com.stremio.core.models.LoadablePage.Content.Loading || page.content == null,
                    error = error,
                    seeAllRequest = page.request,
                    type = page.catalogType
                )
            }
        }
    }

    fun extractContinueWatching(preview: com.stremio.core.models.ContinueWatchingPreview): CatalogShelf {
        val items = preview.libraryItems.map { item ->
            val progressVal = if (item.state.duration > 0) {
                item.state.timeOffset.toFloat() / item.state.duration.toFloat()
            } else 0f
            CatalogItem(
                id = item.id,
                type = item.type,
                name = item.name,
                poster = item.poster,
                background = null,
                releaseInfo = item.state.videoId?.let { "Episode" },
                imdbRating = null,
                progress = progressVal,
                watched = item.watched,
                remainingEpisodes = item.remainingEpisodes
            )
        }
        return CatalogShelf(
            title = "Continue Watching",
            items = items,
            isLoading = false
        )
    }
}
