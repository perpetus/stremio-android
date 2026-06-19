package com.stremio.mobile.presentation.state

import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.EpisodeOption
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.data.model.StreamSortCriterion

data class StreamsUiState(
    val forItem: CatalogItem? = null,
    val isOpen: Boolean = false,
    val isSeries: Boolean = false,
    val isLoading: Boolean = false,
    val isResolving: Boolean = false,
    val error: String? = null,
    val streams: List<StreamOption> = emptyList(),
    val episodes: List<EpisodeOption> = emptyList(),
    val seasons: List<Int> = emptyList(),
    val selectedSeason: Int? = null,
    val selectedVideoId: String? = null,
    val selectedEpisodeLabel: String? = null,
    val releaseDateLabel: String? = null,
    val selectedProvider: String? = null,
    val sortCriterion: StreamSortCriterion = StreamSortCriterion.DEFAULT,
) {
    /** Series with no episode chosen yet show the episode picker; everything else shows streams. */
    val showingEpisodes: Boolean get() = isSeries && selectedVideoId == null
}
