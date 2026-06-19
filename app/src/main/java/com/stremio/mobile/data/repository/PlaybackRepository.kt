package com.stremio.mobile.data.repository

import android.net.Uri
import com.stremio.mobile.core.StremioCore
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.player.ExternalSubtitle
import com.stremio.mobile.player.LanguageCatalog
import com.stremio.mobile.player.PlaybackManager
import com.stremio.mobile.player.PlaybackState
import com.stremio.mobile.player.Player
import com.stremio.mobile.player.PlayerEngine
import com.stremio.mobile.player.PlayerSubtitleStyle
import com.stremio.mobile.player.PlayerTrackOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

class PlaybackRepository(
    private val core: StremioCore,
    private val playbackManager: PlaybackManager
) {
    val state: StateFlow<PlaybackState> get() = playbackManager.state

    fun getPlayer(): Player? = playbackManager.getPlayer()

    fun attachView(view: android.view.View) = playbackManager.attachView(view)

    fun detachView() = playbackManager.detachView()

    fun release() = playbackManager.release()

    suspend fun resolveAndLoadStream(option: StreamOption, engine: PlayerEngine = PlayerEngine.EXO): Boolean {
        val url = runCatching { core.resolvePlayableUrl(option.core).first() }.getOrNull()
            ?: core.directUrl(option.core.stream)

        if (url.isNullOrBlank()) {
            return false
        }

        val subtitles = option.core.stream.subtitles.map {
            ExternalSubtitle(
                id = it.id,
                lang = it.lang,
                url = it.url,
                label = it.name,
                source = option.core.addonTitle,
                origin = option.core.addonTitle,
                addonSubtitleId = it.id,
                embedded = false,
            )
        }
        val preferredLang = runCatching { core.getSubtitleSettings().language }.getOrNull()
        val startPositionMs = runCatching { core.getResumePositionMs(option.core.streamRequest) }.getOrDefault(0L)

        playbackManager.load(
            uri = Uri.parse(url),
            title = option.name,
            startPositionMs = startPositionMs,
            subtitles = subtitles,
            preferredSubtitleLang = preferredLang,
            engine = engine,
        )
        return true
    }

    fun playerFlow(): Flow<com.stremio.core.models.Player> = core.playerFlow()

    fun extractAddonSubtitles(player: com.stremio.core.models.Player): List<ExternalSubtitle> {
        return player.subtitles.flatMap { loadable ->
            val subtitles = loadable.ready?.subtitles ?: return@flatMap emptyList()
            subtitles.map { subtitle ->
                ExternalSubtitle(
                    id = "${loadable.request.base}:${subtitle.id}",
                    lang = subtitle.lang,
                    url = subtitle.url,
                    label = subtitle.name,
                    source = loadable.title,
                    origin = loadable.title,
                    addonSubtitleId = subtitle.id,
                    embedded = false,
                )
            }
        }
    }

    fun addExternalSubtitleTracks(tracks: List<ExternalSubtitle>) = playbackManager.addExternalSubtitleTracks(tracks)

    fun addLocalSubtitle(track: ExternalSubtitle) = playbackManager.addLocalSubtitle(track)

    fun getPlayerStreamState(): com.stremio.core.models.Player.StreamState? =
        runCatching { core.getPlayer().streamState }.getOrNull()

    fun rememberAudioTrack(track: PlayerTrackOption) {
        runCatching { core.setPlayerAudioTrack(track.id, track.languageCode ?: LanguageCatalog.toCode(track.language)) }
    }

    fun rememberSubtitleTrack(track: PlayerTrackOption) {
        runCatching {
            core.setPlayerSubtitleTrack(
                id = track.id,
                embedded = track.embedded,
                language = track.languageCode ?: LanguageCatalog.toCode(track.language),
            )
        }
    }

    fun rememberSubtitlesDisabled() {
        runCatching { core.clearPlayerSubtitleTrack() }
    }

    fun rememberSubtitleStyle(style: PlayerSubtitleStyle) {
        runCatching {
            core.setPlayerSubtitleSettings(
                delayMs = style.delayMs,
                sizePercent = style.sizePercent.toFloat(),
                offsetPercent = style.offsetPercent.toFloat(),
            )
        }
    }

    fun getNextVideo(): com.stremio.core.types.resource.Video? = runCatching { core.getNextVideo() }.getOrNull()

    fun reportTimeChanged(timeMs: Long, durationMs: Long) = runCatching { core.playerTimeChanged(timeMs, durationMs) }

    fun reportSeek(timeMs: Long, durationMs: Long) = runCatching { core.playerSeek(timeMs, durationMs) }

    fun reportPausedChanged(paused: Boolean) = runCatching { core.playerPausedChanged(paused) }

    fun reportEnded() = runCatching { core.playerEnded() }

    fun reportNextVideo() = runCatching { core.playerNextVideo() }

    fun requestStreamStatistics(infoHash: String, fileIndex: Int) = runCatching { core.requestStreamStatistics(infoHash, fileIndex) }

    fun getStreamStatistics(): com.stremio.core.models.StreamingServer.Statistics? = runCatching { core.getStreamStatistics() }.getOrNull()

    fun getSubtitlePrefs(): Pair<Int, Int> {
        val prefs = runCatching { core.getSubtitleSettings() }.getOrNull()
        return Pair(prefs?.sizePercent ?: 100, prefs?.offsetPercent ?: 0)
    }

    fun updateSubtitlePrefs(sizePercent: Int, offsetPercent: Int) {
        runCatching { core.updateSubtitleSettings(sizePercent = sizePercent, offsetPercent = offsetPercent) }
    }
}
