package com.stremio.mobile.presentation.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeMute
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt
import org.json.JSONObject
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.AccentGreen
import com.stremio.mobile.core.theme.GlassSurface

@Composable
fun PlayerScreen(
    player: com.stremio.mobile.player.Player?,
    activeUri: String?,
    title: String,
    onAttachView: (View) -> Unit,
    onDetachView: () -> Unit,
    onBack: () -> Unit,
    getSubtitlePrefs: () -> Pair<Int, Int> = { 100 to 0 },
    onSubtitlePrefsChanged: (sizePercent: Int, offsetPercent: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val exoPlayer = remember(player) { player?.getPlayerImpl() }
    val initialSubtitlePrefs = remember { getSubtitlePrefs() }

    // Player State
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var bufferedPositionMs by remember { mutableStateOf(0L) }
    var currentSpeed by remember { mutableStateOf(1.0f) }

    // Subtitle Customization State — size/offset seeded from the user's persisted core profile
    // preference (web parity); delay is session-local only, matching web's per-stream handling.
    var subtitleSize by remember { mutableFloatStateOf(initialSubtitlePrefs.first / 100f) }
    var subtitleOffset by remember { mutableFloatStateOf(initialSubtitlePrefs.second / 100f) }
    var subtitleDelay by remember { mutableFloatStateOf(0.0f) }

    // Persist size/offset changes to the core profile, debounced so dragging a Stepper doesn't
    // spam JNI dispatch calls.
    LaunchedEffect(subtitleSize, subtitleOffset) {
        delay(500)
        onSubtitlePrefsChanged((subtitleSize * 100).roundToInt(), (subtitleOffset * 100).roundToInt())
    }

    // UI Configuration
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showControls by remember { mutableStateOf(true) }
    var lastActivityTime by remember { mutableStateOf(System.currentTimeMillis()) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // Gesture control state
    var gestureVolume by remember { mutableFloatStateOf(0f) }
    var gestureBrightness by remember { mutableFloatStateOf(0f) }
    var activeGestureType by remember { mutableStateOf<String?>(null) }
    var showGestureOverlay by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    val initialVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    var lastVolume by remember { mutableIntStateOf(if (initialVol > 0) initialVol else (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 0.3f).toInt()) }

    // Orientation & Status Bar visibility management
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val window = activity?.window
        
        // Force landscape playback
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Hide system/status bars
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            activity?.requestedOrientation = originalOrientation
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.statusBars())
                controller.show(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    // Auto-hide gesture overlay after dragging stops
    LaunchedEffect(activeGestureType) {
        if (activeGestureType == null && showGestureOverlay) {
            delay(1000)
            showGestureOverlay = false
        }
    }

    // Dialogs / Track selectors
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }

    // Double tap seeking indicators
    var showLeftSeekIndicator by remember { mutableStateOf(false) }
    var showRightSeekIndicator by remember { mutableStateOf(false) }

    // Stats Panel
    var showStatsPanel by remember { mutableStateOf(false) }
    var torrentStats by remember { mutableStateOf<JSONObject?>(null) }

    val infoHash = remember(activeUri) {
        activeUri?.let { uri ->
            Regex("/(?:stream/)?([a-fA-F0-9]{40})").find(uri)?.groupValues?.get(1)
        }
    }

    // Activity Timer Reset Helper
    fun resetActivityTimer() {
        lastActivityTime = System.currentTimeMillis()
        showControls = true
    }

    // Seek helper
    fun seekTo(pos: Long) {
        positionMs = pos
        exoPlayer?.seekTo(pos)
        resetActivityTimer()
    }

    // Media3 Listener Setup
    DisposableEffect(exoPlayer) {
        if (exoPlayer == null) return@DisposableEffect onDispose {}

        val listener = object : Media3Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                resetActivityTimer()
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Media3Player.STATE_BUFFERING
                if (state == Media3Player.STATE_READY) {
                    playbackError = null
                }
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                resetActivityTimer()
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.message ?: "Playback failed"
            }
        }

        exoPlayer.addListener(listener)

        // Initial fetch
        isPlaying = exoPlayer.isPlaying
        isBuffering = exoPlayer.playbackState == Media3Player.STATE_BUFFERING
        positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        durationMs = exoPlayer.duration.coerceAtLeast(0L)
        bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
        currentSpeed = exoPlayer.playbackParameters.speed

        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Continuous Position Tracker
    LaunchedEffect(exoPlayer, isPlaying) {
        if (exoPlayer == null || !isPlaying) return@LaunchedEffect
        while (true) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Inactivity Auto-Hide Timer
    LaunchedEffect(showControls, isPlaying, lastActivityTime) {
        if (showControls && isPlaying) {
            delay(3000)
            if (System.currentTimeMillis() - lastActivityTime >= 3000) {
                showControls = false
            }
        }
    }

    // Live Torrent Stats Polling
    LaunchedEffect(infoHash, showStatsPanel) {
        if (infoHash == null || !showStatsPanel) {
            torrentStats = null
            return@LaunchedEffect
        }
        while (true) {
            val statsStr = withContext(Dispatchers.IO) {
                fetchEngineStats(infoHash)
            }
            if (statsStr != null) {
                torrentStats = try { JSONObject(statsStr) } catch (e: Exception) { null }
            }
            delay(2000)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Exoplayer View Wrapper
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    this.resizeMode = resizeMode
                    keepScreenOn = true
                    // Default caption style draws an opaque box behind the text; web renders
                    // subtitles as outlined text with no background box, so match that here.
                    subtitleView?.setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null,
                        )
                    )
                    onAttachView(this)
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
                view.subtitleView?.let { subView ->
                    subView.setApplyEmbeddedFontSizes(false)
                    subView.setFractionalTextSize(0.0533f * subtitleSize)

                    val maxPaddingPx = with(view.resources.displayMetrics) { 150 * density }
                    val bottomPadding = (subtitleOffset * maxPaddingPx).toInt()
                    subView.setPadding(subView.paddingLeft, subView.paddingTop, subView.paddingRight, bottomPadding)
                }
            }
        )

        DisposableEffect(Unit) {
            onDispose { onDetachView() }
        }

        // Tap HUD Gesture Overlay & Swipe Controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val touchSlop = viewConfiguration.touchSlop
                    var lastTapTime = 0L
                    var lastTapPosition = Offset.Zero

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startTime = System.currentTimeMillis()
                        val startPosition = down.position
                        var isDrag = false
                        var isDragRejected = false

                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val initialVolume = if (maxVol > 0) currentVol.toFloat() / maxVol else 0f

                        val curBright = activity?.window?.attributes?.screenBrightness ?: -1f
                        val initialBrightness = if (curBright < 0f) {
                            try {
                                android.provider.Settings.System.getInt(
                                    context.contentResolver,
                                    android.provider.Settings.System.SCREEN_BRIGHTNESS
                                ) / 255f
                            } catch (e: Exception) {
                                0.5f
                            }
                        } else {
                            curBright
                        }

                        val screenWidth = size.width
                        val screenHeight = size.height
                        val isLeft = startPosition.x < screenWidth / 2f
                        val currentGestureType = if (isLeft) "brightness" else "volume"

                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val dragPointer = event.changes.find { it.id == pointerId } ?: break

                            if (dragPointer.pressed) {
                                val currentPosition = dragPointer.position
                                val diff = currentPosition - startPosition

                                if (!isDrag && !isDragRejected) {
                                    val distance = diff.getDistance()
                                    if (distance > touchSlop) {
                                        if (Math.abs(diff.y) > Math.abs(diff.x)) {
                                            isDrag = true
                                            activeGestureType = currentGestureType
                                            showGestureOverlay = true
                                        } else {
                                            isDragRejected = true
                                        }
                                    }
                                }

                                if (isDrag) {
                                    dragPointer.consume()
                                    val deltaY = -(currentPosition.y - startPosition.y)
                                    val sensitivity = 1.0f
                                    val deltaPercent = (deltaY / screenHeight) * sensitivity

                                    if (currentGestureType == "brightness") {
                                        val targetBrightness = (initialBrightness + deltaPercent).coerceIn(0f, 1f)
                                        gestureBrightness = targetBrightness
                                        activity?.let { act ->
                                            val lp = act.window.attributes
                                            lp.screenBrightness = targetBrightness
                                            act.window.attributes = lp
                                        }
                                    } else {
                                        val targetVolumePercent = (initialVolume + deltaPercent).coerceIn(0f, 1f)
                                        gestureVolume = targetVolumePercent
                                        val targetVol = (targetVolumePercent * maxVol).toInt().coerceIn(0, maxVol)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                    }
                                }
                            } else {
                                dragPointer.consume()
                                break
                            }
                        }

                        if (!isDrag) {
                            val endTime = System.currentTimeMillis()
                            val duration = endTime - startTime
                            val distance = (lastTapPosition - startPosition).getDistance()

                            if (duration < 300) {
                                if (endTime - lastTapTime < 300 && distance < 100) {
                                    // Double tap!
                                    val isDoubleTapLeft = startPosition.x < screenWidth / 2f
                                    if (isDoubleTapLeft) {
                                        val newPos = (positionMs - 10000).coerceAtLeast(0)
                                        seekTo(newPos)
                                        showLeftSeekIndicator = true
                                    } else {
                                        val newPos = (positionMs + 10000).coerceAtMost(durationMs)
                                        seekTo(newPos)
                                        showRightSeekIndicator = true
                                    }
                                    lastTapTime = 0L
                                } else {
                                    // Single tap!
                                    if (showControls) {
                                        showControls = false
                                    } else {
                                        resetActivityTimer()
                                    }
                                    lastTapTime = endTime
                                    lastTapPosition = startPosition
                                }
                            }
                        } else {
                            // Drag ended: trigger overlay fadeout
                            activeGestureType = null
                        }
                    }
                }
        )

        // Seek indicators LaunchedEffects
        if (showLeftSeekIndicator) {
            LaunchedEffect(Unit) {
                delay(650)
                showLeftSeekIndicator = false
            }
        }
        if (showRightSeekIndicator) {
            LaunchedEffect(Unit) {
                delay(650)
                showRightSeekIndicator = false
            }
        }

        // Double tap feedback overlays
        AnimatedVisibility(
            visible = showLeftSeekIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 48.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.FastRewind,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
                Text("-10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        AnimatedVisibility(
            visible = showRightSeekIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 48.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
                Text("+10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        // Playback error overlay — shown when the codec/source fails so it's visible instead
        // of an infinite buffering spinner, with a one-tap retry.
        if (playbackError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = "Couldn't play this stream",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    Text(
                        text = playbackError ?: "",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onBack) { Text("Back") }
                        Button(onClick = {
                            playbackError = null
                            exoPlayer?.prepare()
                        }) { Text("Retry") }
                    }
                }
            }
        }

        // Floating Stats Overlay Panel
        if (showStatsPanel && torrentStats != null) {
            // Full-screen backdrop detector to close the stats panel when clicking outside
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            showStatsPanel = false
                        }
                    }
            ) {
                val stats = torrentStats!!
                val downSpeed = stats.optDouble("downloadSpeed", 0.0)
                val upSpeed = stats.optDouble("uploadSpeed", 0.0)
                val activePeers = stats.optLong("peers", 0L)
                val swarmSize = stats.optLong("swarmSize", 0L)
                val totalDown = stats.optLong("downloaded", 0L)

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 80.dp, end = 20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC101018))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                // Consume taps inside the stats card so they do not propagate to the backdrop and close it
                            }
                        }
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Torrent Statistics",
                            color = AccentPurple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        HorizontalDivider(color = Color(0x22FFFFFF), modifier = Modifier.width(160.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.width(160.dp)
                        ) {
                            Text("Download", color = Color.Gray, fontSize = 12.sp)
                            Text(formatSpeed(downSpeed), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.width(160.dp)
                        ) {
                            Text("Upload", color = Color.Gray, fontSize = 12.sp)
                            Text(formatSpeed(upSpeed), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.width(160.dp)
                        ) {
                            Text("Peers", color = Color.Gray, fontSize = 12.sp)
                            Text("$activePeers / $swarmSize", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.width(160.dp)
                        ) {
                            Text("Downloaded", color = Color.Gray, fontSize = 12.sp)
                            Text(formatBytes(totalDown), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Buffer Loading Overlay
        if (isBuffering) {
            CircularProgressIndicator(
                color = AccentPurple,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
            )
        }

        // Gesture HUD Overlay
        AnimatedVisibility(
            visible = showGestureOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC101018))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val progress = if (activeGestureType == "brightness") gestureBrightness else gestureVolume
                    Icon(
                        imageVector = if (activeGestureType == "brightness") {
                            if (progress < 0.3f) Icons.Outlined.BrightnessLow
                            else if (progress < 0.7f) Icons.Outlined.BrightnessMedium
                            else Icons.Outlined.BrightnessHigh
                        } else {
                            if (progress == 0f) Icons.AutoMirrored.Outlined.VolumeMute
                            else if (progress < 0.5f) Icons.AutoMirrored.Outlined.VolumeDown
                            else Icons.AutoMirrored.Outlined.VolumeUp
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (activeGestureType == "brightness") "Brightness" else "Volume",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0x33FFFFFF))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(AccentPurple, Color(0xFF9E8CFE))
                                        )
                                    )
                            )
                        }
                    }

                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }
        }

        // Overlay HUD Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top & Bottom gradient backdrops for readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xE6050510), Color.Transparent)
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xE6050510))
                            )
                        )
                )

                // Top Menu Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0x33000000))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Center Play / Pause & Seek controls
                if (!isBuffering) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        // Skip Backward 10s button
                        IconButton(
                            onClick = {
                                val newPos = (positionMs - 10000).coerceAtLeast(0)
                                seekTo(newPos)
                                showLeftSeekIndicator = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0x33000000))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FastRewind,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Center Play/Pause button
                        IconButton(
                            onClick = {
                                exoPlayer?.let {
                                    if (isPlaying) it.pause() else it.play()
                                }
                                resetActivityTimer()
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(if (isPlaying) Color(0x4D000000) else AccentGreen)
                                .border(1.5.dp, Color(0x33FFFFFF), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        // Skip Forward 10s button
                        IconButton(
                            onClick = {
                                val newPos = (positionMs + 10000).coerceAtMost(durationMs)
                                seekTo(newPos)
                                showRightSeekIndicator = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0x33000000))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FastForward,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                // Bottom HUD Panels
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    // Timeline Seekbar Row (web style: [Time] [Slider] [Duration/Remaining])
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Current time label
                        Text(
                            text = formatTime(positionMs),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(44.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        // Seek Slider & Buffer
                        val duration = durationMs.coerceAtLeast(1L)
                        val progress = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
                        val bufferedProgress = (bufferedPositionMs.toFloat() / duration).coerceIn(0f, 1f)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .padding(horizontal = 6.dp)
                            ) {
                                val trackHeight = 4.dp.toPx()
                                val width = size.width
                                val centerY = size.height / 2

                                drawRoundRect(
                                    color = Color(0x26FFFFFF),
                                    topLeft = Offset(0f, centerY - trackHeight / 2),
                                    size = Size(width, trackHeight),
                                    cornerRadius = CornerRadius(trackHeight / 2)
                                )

                                drawRoundRect(
                                    color = Color(0x4DFFFFFF),
                                    topLeft = Offset(0f, centerY - trackHeight / 2),
                                    size = Size(width * bufferedProgress, trackHeight),
                                    cornerRadius = CornerRadius(trackHeight / 2)
                                )
                            }

                            Slider(
                                value = progress,
                                onValueChange = { seekTo((it * duration).toLong()) },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = AccentPurple,
                                    inactiveTrackColor = Color.Transparent,
                                    activeTickColor = Color.Transparent,
                                    inactiveTickColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Clickable Duration/Remaining time label
                        var remainingTimeMode by remember { mutableStateOf(false) }
                        val rightTimeText = if (remainingTimeMode) {
                            "-" + formatTime((durationMs - positionMs).coerceAtLeast(0L))
                        } else {
                            formatTime(durationMs)
                        }

                        Text(
                            text = rightTimeText,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .width(44.dp)
                                .clickable { remainingTimeMode = !remainingTimeMode },
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    // Bottom Controls Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Controls Group
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Small Play/Pause toggle
                            IconButton(
                                onClick = {
                                    exoPlayer?.let {
                                        if (isPlaying) it.pause() else it.play()
                                    }
                                    resetActivityTimer()
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Volume Toggle Mute
                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val volumePercent = if (maxVol > 0) currentVol.toFloat() / maxVol else 0f
                            val volumeIcon = if (isMuted || volumePercent == 0f) {
                                Icons.AutoMirrored.Outlined.VolumeMute
                            } else if (volumePercent < 0.5f) {
                                Icons.AutoMirrored.Outlined.VolumeDown
                            } else {
                                Icons.AutoMirrored.Outlined.VolumeUp
                            }

                            IconButton(
                                onClick = {
                                    if (isMuted) {
                                        val restoreVol = if (lastVolume > 0) lastVolume else (maxVol * 0.3f).toInt().coerceAtLeast(1)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVol, 0)
                                        isMuted = false
                                    } else {
                                        lastVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                                        isMuted = true
                                    }
                                    resetActivityTimer()
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = volumeIcon,
                                    contentDescription = "Mute Toggle",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Right Controls Group
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Torrent Stats toggle button
                            if (infoHash != null) {
                                IconButton(
                                    onClick = {
                                        showStatsPanel = !showStatsPanel
                                        resetActivityTimer()
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (showStatsPanel) AccentPurple else Color.Transparent)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = "Torrent Stats",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Playback speed selector button
                            val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                            TextButton(
                                onClick = {
                                    val nextIndex = (speedOptions.indexOf(currentSpeed) + 1) % speedOptions.size
                                    val nextSpeed = speedOptions[nextIndex]
                                    currentSpeed = nextSpeed
                                    exoPlayer?.setPlaybackSpeed(nextSpeed)
                                    resetActivityTimer()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Speed,
                                    contentDescription = "Playback Speed",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${currentSpeed}x",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Subtitles track selector
                            IconButton(
                                onClick = {
                                    showSubtitleDialog = true
                                    resetActivityTimer()
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Subtitles,
                                    contentDescription = "Subtitles",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Audio track selector
                            IconButton(
                                onClick = {
                                    showAudioDialog = true
                                    resetActivityTimer()
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Audiotrack,
                                    contentDescription = "Audio Tracks",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Aspect Ratio / Resize selector button
                            val resizeLabels = mapOf(
                                AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
                                AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom"
                            )
                            TextButton(
                                onClick = {
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    resetActivityTimer()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AspectRatio,
                                    contentDescription = "Aspect Ratio",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = resizeLabels[resizeMode] ?: "Fit",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Audio Tracks Dialogue
    if (showAudioDialog) {
        TrackSelectorDialog(
            title = "Audio Tracks",
            options = getTrackOptions(exoPlayer, C.TRACK_TYPE_AUDIO),
            onSelect = { option ->
                exoPlayer?.let { player ->
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(TrackSelectionOverride(option.mediaTrackGroup, option.index))
                        .build()
                }
                showAudioDialog = false
            },
            onDismiss = { showAudioDialog = false }
        )
    }

    // Subtitle Tracks Dialogue
    if (showSubtitleDialog) {
        val options = getTrackOptions(exoPlayer, C.TRACK_TYPE_TEXT)
        val isSubDisabled = exoPlayer?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) ?: true

        SubtitlesCustomizationDialog(
            options = options,
            isNoneSelected = isSubDisabled,
            subtitleSize = subtitleSize,
            subtitleOffset = subtitleOffset,
            subtitleDelay = subtitleDelay,
            onSubtitleSizeChange = { subtitleSize = it },
            onSubtitleOffsetChange = { subtitleOffset = it },
            onSubtitleDelayChange = { subtitleDelay = it },
            onSelect = { option ->
                exoPlayer?.let { player ->
                    val disabledTypes = player.trackSelectionParameters.disabledTrackTypes.toMutableSet()
                    disabledTypes.remove(C.TRACK_TYPE_TEXT)
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setDisabledTrackTypes(disabledTypes)
                        .setOverrideForType(TrackSelectionOverride(option.mediaTrackGroup, option.index))
                        .build()
                }
                showSubtitleDialog = false
            },
            onSelectNone = {
                exoPlayer?.let { player ->
                    val disabledTypes = player.trackSelectionParameters.disabledTrackTypes.toMutableSet()
                    disabledTypes.add(C.TRACK_TYPE_TEXT)
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setDisabledTrackTypes(disabledTypes)
                        .build()
                }
                showSubtitleDialog = false
            },
            onDismiss = { showSubtitleDialog = false }
        )
    }
}

// Track Options helper data structure
data class TrackOption(
    val index: Int,
    val label: String,
    val isSelected: Boolean,
    val mediaTrackGroup: TrackGroup
)

private fun getTrackOptions(player: Media3Player?, trackType: Int): List<TrackOption> {
    val options = mutableListOf<TrackOption>()
    val tracks = player?.currentTracks ?: return options
    for (group in tracks.groups) {
        if (group.type == trackType) {
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val lang = format.language?.uppercase()
                val label = format.label ?: lang ?: if (trackType == C.TRACK_TYPE_AUDIO) "Track ${options.size + 1}" else "Subtitles ${options.size + 1}"
                options.add(
                    TrackOption(
                        index = i,
                        label = label,
                        isSelected = group.isTrackSelected(i),
                        mediaTrackGroup = group.mediaTrackGroup
                    )
                )
            }
        }
    }
    return options
}

@Composable
fun TrackSelectorDialog(
    title: String,
    options: List<TrackOption>,
    hasNoneOption: Boolean = false,
    isNoneSelected: Boolean = false,
    onSelect: (TrackOption) -> Unit,
    onSelectNone: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 400.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassSurface)
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (hasNoneOption) {
                        item {
                            TrackRow(
                                label = "None",
                                isSelected = isNoneSelected,
                                onClick = onSelectNone
                            )
                        }
                    }

                    items(options) { option ->
                        TrackRow(
                            label = option.label,
                            isSelected = option.isSelected && (!hasNoneOption || !isNoneSelected),
                            onClick = { onSelect(option) }
                        )
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close", color = AccentPurple, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TrackRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0x337457F2) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color(0xFFE2E2E6),
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = AccentPurple,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp)
            )
        }
    }
}

// Time Formatting Helper
private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}

// Bytes Formatting Helper
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

// Speed Formatting Helper
private fun formatSpeed(bytesPerSec: Double): String {
    val kb = bytesPerSec / 1024.0
    if (kb < 1024.0) {
        return String.format("%.1f KB/s", kb)
    }
    val mb = kb / 1024.0
    return String.format("%.1f MB/s", mb)
}

// Network fetch stats helper
private fun fetchEngineStats(infoHash: String): String? {
    return try {
        val url = URL("http://127.0.0.1:11470/$infoHash/stats.json")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 1500
        conn.readTimeout = 1500
        if (conn.responseCode == 200) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun SubtitlesCustomizationDialog(
    options: List<TrackOption>,
    isNoneSelected: Boolean,
    subtitleSize: Float,
    subtitleOffset: Float,
    subtitleDelay: Float,
    onSubtitleSizeChange: (Float) -> Unit,
    onSubtitleOffsetChange: (Float) -> Unit,
    onSubtitleDelayChange: (Float) -> Unit,
    onSelect: (TrackOption) -> Unit,
    onSelectNone: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassSurface)
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Subtitles Settings",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Tracks",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            item {
                                TrackRow(
                                    label = "None",
                                    isSelected = isNoneSelected,
                                    onClick = onSelectNone
                                )
                            }

                            items(options) { option ->
                                TrackRow(
                                    label = option.label,
                                    isSelected = option.isSelected && !isNoneSelected,
                                    onClick = { onSelect(option) }
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(Color(0x22FFFFFF))
                    )

                    Column(
                        modifier = Modifier.width(200.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Customize",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )

                        Stepper(
                            label = "DELAY",
                            value = subtitleDelay,
                            unit = "s",
                            step = 0.25f,
                            min = -10.0f,
                            max = 10.0f,
                            formatValue = { String.format(java.util.Locale.US, if (it >= 0) "+%.2f" else "%.2f", it) },
                            onChange = onSubtitleDelayChange
                        )

                        Stepper(
                            label = "SIZE",
                            value = subtitleSize * 100f,
                            unit = "%",
                            step = 25f,
                            min = 75f,
                            max = 250f,
                            formatValue = { "${it.toInt()}" },
                            onChange = { onSubtitleSizeChange(it / 100f) }
                        )

                        Stepper(
                            label = "POSITION",
                            value = subtitleOffset * 100f,
                            unit = "%",
                            step = 1f,
                            min = 0f,
                            max = 100f,
                            formatValue = { "${it.toInt()}" },
                            onChange = { onSubtitleOffsetChange(it / 100f) }
                        )
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close", color = AccentPurple, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun Stepper(
    label: String,
    value: Float?,
    unit: String,
    step: Float,
    min: Float? = null,
    max: Float? = null,
    disabled: Boolean = false,
    formatValue: (Float) -> String = { "${it.toInt()}" },
    onChange: (Float) -> Unit
) {
    val decreaseDisabled = disabled || value == null || (min != null && value <= min)
    val increaseDisabled = disabled || value == null || (max != null && value >= max)
    val valueLabel = if (disabled || value == null) "--" else formatValue(value) + unit

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(180.dp)
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x1AFFFFFF))
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            IconButton(
                onClick = { if (!decreaseDisabled && value != null) onChange((value - step).coerceAtLeast(min ?: (value - step))) },
                enabled = !decreaseDisabled,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Remove,
                    contentDescription = "Decrease",
                    tint = if (decreaseDisabled) Color.Gray else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = valueLabel,
                color = if (disabled) Color.Gray else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(56.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            IconButton(
                onClick = { if (!increaseDisabled && value != null) onChange((value + step).coerceAtMost(max ?: (value + step))) },
                enabled = !increaseDisabled,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Increase",
                    tint = if (increaseDisabled) Color.Gray else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
