package com.stremio.mobile.player

import android.content.Context
import android.net.Uri
import android.view.View
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
    ) {
        player?.release()
        player = ExoStreamPlayer(context).also {
            it.load(uri, startPositionMs, subtitles, preferredSubtitleLang)
            it.play()
        }
        mutableState.value = PlaybackState(activeUri = uri.toString(), title = title, isPlaying = true)
    }

    fun attachView(view: View) {
        player?.attachView(view)
    }

    fun detachView() {
        player?.detachView()
    }

    fun play() {
        player?.play()
        mutableState.value = mutableState.value.copy(isPlaying = true)
    }

    fun pause() {
        player?.pause()
        mutableState.value = mutableState.value.copy(isPlaying = false)
    }

    fun release() {
        player?.release()
        player = null
        mutableState.value = PlaybackState()
    }

    fun getPlayer(): Player? = player
}
