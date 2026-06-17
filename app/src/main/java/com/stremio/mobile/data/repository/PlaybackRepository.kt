package com.stremio.mobile.data.repository

import android.net.Uri
import com.stremio.mobile.core.StremioCore
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.player.ExternalSubtitle
import com.stremio.mobile.player.PlaybackManager
import com.stremio.mobile.player.PlaybackState
import com.stremio.mobile.player.Player
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

    suspend fun resolveAndLoadStream(option: StreamOption): Boolean {
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
            )
        }
        val preferredLang = runCatching { core.getSubtitleSettings().language }.getOrNull()

        playbackManager.load(
            uri = Uri.parse(url),
            title = option.name,
            subtitles = subtitles,
            preferredSubtitleLang = preferredLang
        )
        return true
    }

    fun getSubtitlePrefs(): Pair<Int, Int> {
        val prefs = runCatching { core.getSubtitleSettings() }.getOrNull()
        return Pair(prefs?.sizePercent ?: 100, prefs?.offsetPercent ?: 0)
    }

    fun updateSubtitlePrefs(sizePercent: Int, offsetPercent: Int) {
        runCatching { core.updateSubtitleSettings(sizePercent = sizePercent, offsetPercent = offsetPercent) }
    }
}
