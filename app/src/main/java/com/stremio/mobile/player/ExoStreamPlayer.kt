package com.stremio.mobile.player

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.View
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class ExoStreamPlayer(context: Context) : Player {
    override val engine: PlayerEngine = PlayerEngine.EXO

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableRuntimeState = MutableStateFlow(PlayerRuntimeState())

    override val runtimeState: StateFlow<PlayerRuntimeState> = mutableRuntimeState

    private val exoPlayer = run {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
            .setDataSourceFactory(dataSourceFactory)
        val renderersFactory = DefaultRenderersFactory(appContext)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(renderersFactory)
            .build()
    }

    private var playerView: PlayerView? = null
    private var currentUri: Uri? = null
    private var currentStartPositionMs: Long = 0L
    private var currentSubtitles: List<ExternalSubtitle> = emptyList()
    private var currentPreferredSubtitleLang: String? = null
    private var currentSubtitleStyle = PlayerSubtitleStyle()

    private val listener = object : androidx.media3.common.Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            publishState(ended = playbackState == androidx.media3.common.Player.STATE_ENDED)
        }

        override fun onPlayerError(error: PlaybackException) {
            publishState(error = error.message ?: "Playback failed")
        }

        override fun onTracksChanged(tracks: Tracks) {
            publishState()
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            publishState()
        }
    }

    init {
        exoPlayer.addListener(listener)
        scope.launch {
            while (isActive) {
                publishState()
                delay(500)
            }
        }
    }

    override fun createView(context: Context): View {
        return PlayerView(context).apply {
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            keepScreenOn = true
            resizeMode = playerView?.resizeMode ?: AspectRatioFrameLayout.RESIZE_MODE_FIT
            player = exoPlayer
            playerView = this
            applySubtitleStyleToView()
        }
    }

    override fun load(
        uri: Uri,
        startPositionMs: Long,
        subtitles: List<ExternalSubtitle>,
        preferredSubtitleLang: String?,
    ) {
        currentUri = uri
        currentStartPositionMs = startPositionMs
        currentSubtitles = subtitles
        currentPreferredSubtitleLang = preferredSubtitleLang

        val mediaItem = buildMediaItem(uri, subtitles, preferredSubtitleLang)
        exoPlayer.setMediaItem(mediaItem, startPositionMs)
        exoPlayer.prepare()
        publishState(error = null, ended = false)
    }

    override fun retry() {
        mutableRuntimeState.value = mutableRuntimeState.value.copy(error = null, ended = false)
        currentUri?.let { uri ->
            val resumePosition = exoPlayer.currentPosition.coerceAtLeast(currentStartPositionMs)
            exoPlayer.setMediaItem(buildMediaItem(uri, currentSubtitles, currentPreferredSubtitleLang), resumePosition)
        }
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun reportNonFatalError(message: String?) {
        if (message == null) return
        publishState(error = message, ended = false)
    }

    override fun play() {
        exoPlayer.play()
        publishState()
    }

    override fun pause() {
        exoPlayer.pause()
        publishState()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        publishState()
    }

    override fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
        publishState()
    }

    override fun setResizeMode(mode: PlayerResizeMode) {
        playerView?.resizeMode = when (mode) {
            PlayerResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            PlayerResizeMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            PlayerResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
    }

    override fun selectAudioTrack(id: String) {
        val parsed = ExoTrackId.parse(id) ?: return
        if (parsed.type != PlayerTrackType.AUDIO) return
        selectTrack(parsed, C.TRACK_TYPE_AUDIO)
    }

    override fun selectSubtitleTrack(id: String) {
        val parsed = ExoTrackId.parse(id) ?: return
        if (parsed.type != PlayerTrackType.SUBTITLE) return
        enableTextTracks()
        selectTrack(parsed, C.TRACK_TYPE_TEXT)
    }

    override fun disableSubtitles() {
        val disabledTypes = exoPlayer.trackSelectionParameters.disabledTrackTypes.toMutableSet()
        disabledTypes.add(C.TRACK_TYPE_TEXT)
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setDisabledTrackTypes(disabledTypes)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        publishState()
    }

    override fun setSubtitleStyle(style: PlayerSubtitleStyle) {
        currentSubtitleStyle = style
        applySubtitleStyleToView()
    }

    override fun addExternalSubtitleTracks(tracks: List<ExternalSubtitle>) {
        val unique = (currentSubtitles + tracks)
            .distinctBy { it.id }
        if (unique.size == currentSubtitles.size) return
        currentSubtitles = unique
        rebuildMediaItemPreservingPlayback()
    }

    override fun addLocalSubtitle(track: ExternalSubtitle) {
        currentPreferredSubtitleLang = LanguageCatalog.LOCAL_SUBTITLES_LANGUAGE
        addExternalSubtitleTracks(
            listOf(
                track.copy(
                    lang = track.lang.ifBlank { LanguageCatalog.LOCAL_SUBTITLES_LANGUAGE },
                    origin = "LOCAL",
                    embedded = false,
                    local = true,
                )
            )
        )
        enableTextTracks()
    }

    override fun release() {
        scope.cancel()
        playerView?.player = null
        playerView = null
        exoPlayer.removeListener(listener)
        exoPlayer.release()
        mutableRuntimeState.value = PlayerRuntimeState()
    }

    private fun applySubtitleStyleToView() {
        playerView?.subtitleView?.let { subView ->
            val style = currentSubtitleStyle
            subView.setApplyEmbeddedFontSizes(false)
            subView.setApplyEmbeddedStyles(style.assStyling)
            subView.setFractionalTextSize(0.0533f * (style.sizePercent / 100f))

            val textColor = parseSubtitleColor(style.textColor, Color.WHITE)
            val backgroundColor = parseSubtitleColor(style.backgroundColor, Color.TRANSPARENT)
            val outlineColor = parseSubtitleColor(style.outlineColor, Color.BLACK)
            subView.setStyle(
                CaptionStyleCompat(
                    textColor,
                    backgroundColor,
                    Color.TRANSPARENT,
                    if (Color.alpha(outlineColor) == 0) {
                        CaptionStyleCompat.EDGE_TYPE_NONE
                    } else {
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE
                    },
                    outlineColor,
                    null,
                )
            )

            val maxPaddingPx = with(subView.resources.displayMetrics) { 150 * density }
            val bottomPadding = ((style.offsetPercent / 100f) * maxPaddingPx).toInt()
            subView.setPadding(subView.paddingLeft, subView.paddingTop, subView.paddingRight, bottomPadding)
        }
    }

    private fun rebuildMediaItemPreservingPlayback() {
        val uri = currentUri ?: return
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        val wasPlaying = exoPlayer.isPlaying || exoPlayer.playWhenReady
        val speed = exoPlayer.playbackParameters.speed
        exoPlayer.setMediaItem(buildMediaItem(uri, currentSubtitles, currentPreferredSubtitleLang), position)
        exoPlayer.prepare()
        exoPlayer.setPlaybackSpeed(speed)
        if (wasPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
        publishState(error = null, ended = false)
    }

    private fun buildMediaItem(
        uri: Uri,
        subtitles: List<ExternalSubtitle>,
        preferredSubtitleLang: String?,
    ): MediaItem {
        val subtitleConfigs = subtitles.map { sub ->
            val isDefault = preferredSubtitleLang != null &&
                LanguageCatalog.matches(sub.lang, preferredSubtitleLang)
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
        return MediaItem.Builder()
            .setUri(uri)
            .setSubtitleConfigurations(subtitleConfigs)
            .build()
    }

    private fun selectTrack(parsed: ExoTrackId, media3Type: Int) {
        val group = exoPlayer.currentTracks.groups.getOrNull(parsed.groupIndex) ?: return
        if (group.type != media3Type || parsed.trackIndex !in 0 until group.length) return
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, parsed.trackIndex))
            .build()
        publishState()
    }

    private fun enableTextTracks() {
        val disabledTypes = exoPlayer.trackSelectionParameters.disabledTrackTypes.toMutableSet()
        disabledTypes.remove(C.TRACK_TYPE_TEXT)
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setDisabledTrackTypes(disabledTypes)
            .build()
    }

    private fun publishState(
        error: String? = mutableRuntimeState.value.error,
        ended: Boolean = exoPlayer.playbackState == androidx.media3.common.Player.STATE_ENDED,
    ) {
        mutableRuntimeState.value = PlayerRuntimeState(
            isPlaying = exoPlayer.isPlaying,
            isBuffering = exoPlayer.playbackState == androidx.media3.common.Player.STATE_BUFFERING,
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
            durationMs = if (exoPlayer.duration == C.TIME_UNSET) 0L else exoPlayer.duration.coerceAtLeast(0L),
            bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L),
            speed = exoPlayer.playbackParameters.speed,
            audioTracks = getTrackOptions(C.TRACK_TYPE_AUDIO),
            subtitleTracks = getTrackOptions(C.TRACK_TYPE_TEXT),
            subtitlesDisabled = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT),
            error = error,
            ended = ended,
        )
    }

    private fun getTrackOptions(trackType: Int): List<PlayerTrackOption> {
        val options = mutableListOf<PlayerTrackOption>()
        for ((groupIndex, group) in exoPlayer.currentTracks.groups.withIndex()) {
            if (group.type != trackType) continue
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val externalSubtitle = if (trackType == C.TRACK_TYPE_TEXT) {
                    findExternalSubtitle(format.id, group.mediaTrackGroup.id, format.label, format.language)
                } else {
                    null
                }
                val lang = LanguageCatalog.toCode(format.language)
                    ?: LanguageCatalog.toCode(externalSubtitle?.lang)
                val label = externalSubtitle?.let { buildExternalSubtitleLabel(it) }
                    ?: format.label
                    ?: lang?.uppercase(Locale.ROOT)
                    ?: if (trackType == C.TRACK_TYPE_AUDIO) {
                        "Track ${options.size + 1}"
                    } else {
                        "Subtitles ${options.size + 1}"
                    }
                val optionType = if (trackType == C.TRACK_TYPE_AUDIO) PlayerTrackType.AUDIO else PlayerTrackType.SUBTITLE
                options.add(
                    PlayerTrackOption(
                        id = ExoTrackId(
                            type = optionType,
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                        ).encode(),
                        type = optionType,
                        label = label,
                        language = format.language ?: externalSubtitle?.lang,
                        selected = group.isTrackSelected(trackIndex),
                        languageCode = lang,
                        origin = externalSubtitle?.origin ?: if (trackType == C.TRACK_TYPE_TEXT) "EMBEDDED" else "AUDIO",
                        url = externalSubtitle?.url,
                        fallbackUrl = externalSubtitle?.fallbackUrl,
                        addonSubtitleId = externalSubtitle?.addonSubtitleId,
                        embedded = trackType == C.TRACK_TYPE_TEXT && externalSubtitle == null,
                        local = externalSubtitle?.local == true,
                        exclusive = externalSubtitle?.exclusive == true,
                    )
                )
            }
        }
        return options
    }

    private fun findExternalSubtitle(
        formatId: String?,
        groupId: String?,
        label: String?,
        language: String?,
    ): ExternalSubtitle? {
        return currentSubtitles.firstOrNull { subtitle ->
            subtitle.id == formatId || subtitle.id == groupId
        } ?: currentSubtitles.firstOrNull { subtitle ->
            subtitle.label != null &&
                subtitle.label == label &&
                LanguageCatalog.matches(subtitle.lang, language)
        }
    }

    private fun buildExternalSubtitleLabel(subtitle: ExternalSubtitle): String {
        val base = subtitle.label?.takeIf { it.isNotBlank() } ?: subtitle.lang.uppercase(Locale.ROOT)
        return if (subtitle.source != null) "$base (${subtitle.source})" else base
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

private fun parseSubtitleColor(value: String?, fallback: Int): Int {
    val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return fallback
    return runCatching {
        when {
            raw.length == 9 && raw.startsWith("#") -> {
                val alpha = raw.substring(1, 3)
                val rgb = raw.substring(3)
                Color.parseColor("#$alpha$rgb")
            }
            raw.length == 7 && raw.startsWith("#") -> Color.parseColor(raw)
            else -> Color.parseColor(raw)
        }
    }.getOrDefault(fallback)
}
