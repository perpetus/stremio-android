package com.stremio.mobile.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.GlassSurface
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.core.theme.StremioBackgroundBrush
import com.stremio.mobile.data.model.EpisodeOption
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.presentation.state.StreamsUiState

@Composable
fun StreamsSheet(
    state: StreamsUiState,
    onBack: () -> Unit,
    onSelect: (StreamOption) -> Unit,
    onSelectEpisode: (EpisodeOption) -> Unit,
    onSelectSeason: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StremioBackgroundBrush)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GlassSurface)
                    .clickable(onClick = onBack)
                    .padding(9.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.showingEpisodes) "Episodes" else "Streams",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                state.forItem?.let {
                    Text(
                        text = buildString {
                            append(it.name)
                            state.selectedEpisodeLabel?.let { label ->
                                append(" · $label")
                            }
                        },
                        color = MutedText,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (state.isResolving) {
                CircularProgressIndicator(color = AccentPurple, modifier = Modifier.size(24.dp))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        when {
            state.error != null && state.streams.isEmpty() && state.episodes.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error,
                        color = Color(0xFFFFC66D),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            state.isLoading && state.streams.isEmpty() && state.episodes.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = AccentPurple, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (state.isSeries) "Loading episodes…" else "Finding streams across your addons…",
                        color = MutedText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            state.showingEpisodes -> {
                // Season selector row
                if (state.seasons.size > 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 8.dp),
                    ) {
                        items(state.seasons) { season ->
                            val selected = season == state.selectedSeason
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(if (selected) AccentPurple else GlassSurface)
                                    .clickable { onSelectSeason(season) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Season $season",
                                    color = if (selected) Color.White else MutedText,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                // Episode list filtered by selected season
                val filteredEpisodes = state.episodes.filter {
                    state.selectedSeason == null || it.season == state.selectedSeason
                }
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredEpisodes, key = { it.videoId }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            onClick = { onSelectEpisode(episode) }
                        )
                    }
                }
            }
            else -> {
                // Loading streams for a selected episode
                if (state.isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = AccentPurple, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Finding streams across your addons…",
                            color = MutedText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(state.streams, key = { it.key }) { option ->
                            StreamRow(
                                option = option,
                                enabled = !state.isResolving,
                                onSelect = { onSelect(option) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodeOption,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (episode.isCurrent) Color(0xFF2A2042) else GlassSurface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Episode number badge
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (episode.isCurrent) AccentPurple else Color(0xFF2A2A3E)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${episode.episode}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "S${episode.season}E${episode.episode}",
                color = MutedText,
                fontSize = 12.sp,
            )
        }
        if (episode.watched) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AccentPurple)
            )
        }
    }
}

@Composable
private fun StreamRow(
    option: StreamOption,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AccentPurple),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quality Badge (if available, e.g. 4K, 1080p)
                option.quality?.let { qual ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF3B3B4F))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = qual.uppercase(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Addon Provider Badge
                if (option.addonTitle.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentPurple.copy(alpha = 0.12f))
                            .border(1.dp, AccentPurple.copy(alpha = 0.24f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = option.addonTitle,
                            color = AccentPurple,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Text(
                    text = option.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            
            // Metadata Badges Row (Seeds, Size, Origin)
            if (option.seeds != null || option.size != null || option.origin != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    option.seeds?.let { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = "Seeds",
                                tint = Color(0xFF4CAF50), // Green for healthy seeds
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = s,
                                color = Color(0xFF81C784),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    option.size?.let { sz ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Storage,
                                contentDescription = "Size",
                                tint = MutedText,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = sz,
                                color = MutedText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    option.origin?.let { o ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Cloud,
                                contentDescription = "Origin",
                                tint = MutedText,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = o,
                                color = MutedText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            // Clean Description Underneath
            val cleanDesc = option.cleanDescription ?: option.description ?: option.addonTitle
            if (cleanDesc.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = cleanDesc,
                    color = MutedText,
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
