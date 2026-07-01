package com.stremio.mobile.player

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlaybackState(
    val activeUri: String? = null,
    val title: String? = null,
    val isPlaying: Boolean = false,
)

class PlaybackManager(
    private val context: Context,
) {
    private val mutableState = MutableStateFlow(PlaybackState())
    private var player: Player? = null

    val state: StateFlow<PlaybackState> = mutableState

    fun load(
        uri: Uri,
        title: String? = null,
        startPositionMs: Long = 0,
        subtitles: List<ExternalSubtitle> = emptyList(),
        preferredSubtitleLang: String? = null,
        engine: PlayerEngine = PlayerEngine.EXO,
        settings: com.stremio.core.types.profile.Profile.Settings? = null,
    ) {
        player?.release()
        val fallbackMessage = if (engine == PlayerEngine.MPV) "MPV unavailable; using ExoPlayer." else null
        player = runCatching {
            PlayerFactory.create(context, engine, settings).also {
                it.load(uri, startPositionMs, subtitles, preferredSubtitleLang, settings)
                it.play()
            }
        }.getOrElse { failure ->
            if (engine != PlayerEngine.MPV) throw failure
            ExoStreamPlayer(context, settings).also {
                it.load(uri, startPositionMs, subtitles, preferredSubtitleLang, settings)
                it.play()
                it.reportNonFatalError(fallbackMessage)
            }
        }
        mutableState.value = PlaybackState(activeUri = uri.toString(), title = title, isPlaying = true)
    }

    fun attachView(view: android.view.View) = Unit

    fun detachView() = Unit

    fun play() {
        player?.play()
        mutableState.value = mutableState.value.copy(isPlaying = true)
    }

    fun pause() {
        player?.pause()
        mutableState.value = mutableState.value.copy(isPlaying = false)
    }

    fun addExternalSubtitleTracks(tracks: List<ExternalSubtitle>) {
        player?.addExternalSubtitleTracks(tracks)
    }

    fun addLocalSubtitle(track: ExternalSubtitle) {
        player?.addLocalSubtitle(track)
    }

    fun release() {
        player?.release()
        player = null
        mutableState.value = PlaybackState()
    }

    fun getPlayer(): Player? = player
}
