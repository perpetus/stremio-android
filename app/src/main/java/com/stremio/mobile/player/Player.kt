package com.stremio.mobile.player

import android.net.Uri
import android.view.View

/**
 * An addon-provided subtitle, independent of the generated stremio-core protobuf type so the
 * player module doesn't need to depend on it.
 */
data class ExternalSubtitle(
    val id: String,
    val lang: String,
    val url: String,
    val label: String?,
    /** The addon that supplied this subtitle, shown alongside the label so the track picker
     * can distinguish addon-provided subtitles from ones embedded in the video container. */
    val source: String? = null,
)

interface Player {
    fun attachView(view: View)
    fun detachView()
    fun load(
        uri: Uri,
        startPositionMs: Long = 0,
        subtitles: List<ExternalSubtitle> = emptyList(),
        preferredSubtitleLang: String? = null,
    )
    fun play()
    fun pause()
    fun release()
    fun getPlayerImpl(): androidx.media3.common.Player?
}
