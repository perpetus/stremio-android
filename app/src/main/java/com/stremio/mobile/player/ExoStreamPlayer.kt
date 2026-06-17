package com.stremio.mobile.player

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

class ExoStreamPlayer(context: Context) : Player {
    private val exoPlayer = run {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
        // Some devices/emulators advertise a codec (e.g. HEVC) that then fails to actually
        // decode a given stream. Without fallback enabled, ExoPlayer treats that as fatal and
        // playback never starts; with it, ExoPlayer retries with the next matching decoder.
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(renderersFactory)
            .build()
    }
    private var playerView: PlayerView? = null

    override fun attachView(view: View) {
        val mediaView = view as? PlayerView ?: return
        playerView = mediaView
        mediaView.player = exoPlayer
    }

    override fun detachView() {
        playerView?.player = null
        playerView = null
    }

    override fun load(
        uri: Uri,
        startPositionMs: Long,
        subtitles: List<ExternalSubtitle>,
        preferredSubtitleLang: String?,
    ) {
        val subtitleConfigs = subtitles.map { sub ->
            val isDefault = preferredSubtitleLang != null &&
                sub.lang.equals(preferredSubtitleLang, ignoreCase = true)
            val baseLabel = sub.label ?: sub.lang
            val label = if (sub.source != null) "$baseLabel (${sub.source})" else baseLabel
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setId(sub.id)
                .setMimeType(inferSubtitleMime(sub.url))
                .setLanguage(sub.lang)
                .setLabel(label)
                .setSelectionFlags(if (isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setSubtitleConfigurations(subtitleConfigs)
            .build()
        exoPlayer.setMediaItem(mediaItem, startPositionMs)
        exoPlayer.prepare()
    }

    override fun play() {
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun release() {
        detachView()
        exoPlayer.release()
    }

    override fun getPlayerImpl(): androidx.media3.common.Player? {
        return exoPlayer
    }
}

/** Addon subtitle URLs rarely carry a useful Content-Type; guess from the file extension. */
private fun inferSubtitleMime(url: String): String {
    val path = url.substringBefore('?').substringBefore('#')
    return when {
        path.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
        path.endsWith(".ass", ignoreCase = true) || path.endsWith(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}
