package com.stremio.mobile.presentation.screens.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeMute
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.BrightnessLow
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.AccentPurple
import kotlinx.coroutines.delay
import kotlin.math.abs

/** A full screen-width horizontal swipe seeks across this much of the video, VLC-style. */
const val SEEK_GESTURE_RANGE_MS = 90_000L

/**
 * Observable state for the player's touch gestures, owned by this layer rather than [PlayerScreen]
 * so that background player-state changes (buffering, play/pause) can't reach into the controls
 * overlay mid-gesture. [isGestureActive] is the Compose equivalent of VLC's `touchAction != TOUCH_NONE`:
 * while it is true, the screen must not auto-show its controls.
 */
@Stable
class PlayerGestureState {
    /** "volume" | "brightness" | "seek" while a drag is in progress, else null. */
    var activeGestureType by mutableStateOf<String?>(null)

    /** True from the moment a drag is recognized until the finger lifts. */
    var isGestureActive by mutableStateOf(false)

    /** Whether the transient HUD (volume/brightness bar or seek preview) is showing. */
    var showGestureOverlay by mutableStateOf(false)

    var gestureVolume by mutableFloatStateOf(0f)
    var gestureBrightness by mutableFloatStateOf(0f)

    var seekGestureStartMs by mutableLongStateOf(0L)
    var seekPreviewMs by mutableStateOf<Long?>(null)

    var showLeftSeekIndicator by mutableStateOf(false)
    var showRightSeekIndicator by mutableStateOf(false)

    /** Reset transient gesture state when the media changes. */
    fun reset() {
        activeGestureType = null
        isGestureActive = false
        showGestureOverlay = false
        seekPreviewMs = null
        showLeftSeekIndicator = false
        showRightSeekIndicator = false
    }
}

@Composable
fun rememberPlayerGestureState(): PlayerGestureState = remember { PlayerGestureState() }

/**
 * VLC-style player touch handling: single/double tap, vertical volume (right half) / brightness
 * (left half), and horizontal swipe-to-seek. Volume and brightness side effects are applied here;
 * everything the player owns (hiding controls, committing seeks, the debounced tap toggle) is routed
 * through callbacks so this layer never touches `showControls` directly.
 *
 * @param position current playback position provider (ms), read lazily so the modifier never holds
 *   stale [PlayerScreen] state.
 * @param duration current media duration provider (ms).
 * @param onGestureStart invoked once when a drag is recognized — hide controls and cancel any
 *   pending single-tap toggle.
 * @param onSingleTap invoked on a clean single tap — schedule the debounced show/hide toggle.
 * @param onDoubleTapSeek invoked on a double tap; `forward` = tap on the right half.
 * @param onSeekCommit invoked when a swipe-seek is released, with the target position (ms).
 */
fun Modifier.playerGestures(
    state: PlayerGestureState,
    audioManager: AudioManager,
    activity: Activity?,
    context: Context,
    position: () -> Long,
    duration: () -> Long,
    onGestureStart: () -> Unit,
    onSingleTap: () -> Unit,
    onDoubleTapSeek: (forward: Boolean) -> Unit,
    onSeekCommit: (Long) -> Unit,
    key1: Any?,
    key2: Any?,
): Modifier = this.pointerInput(key1, key2) {
    // Slightly lower than the system touchSlop so vertical/horizontal swipes
    // are recognized sooner and feel more responsive (bigger effective hitbox).
    val gestureSlop = viewConfiguration.touchSlop * 0.6f
    var lastTapTime = 0L
    var lastTapPosition = Offset.Zero

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startTime = System.currentTimeMillis()
        val startPosition = down.position
        var isDrag = false
        var dragGestureType: String? = null

        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val initialVolume = if (maxVol > 0) currentVol.toFloat() / maxVol else 0f

        val curBright = activity?.window?.attributes?.screenBrightness ?: -1f
        val initialBrightness = if (curBright < 0f) {
            try {
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                ) / 255f
            } catch (e: Exception) {
                0.5f
            }
        } else {
            curBright
        }

        val screenWidth = size.width
        val screenHeight = size.height
        // Left half = brightness, right half = volume — indicators render on the
        // same side as the touch, like iOS's vertical volume/brightness bars.
        val isLeft = startPosition.x < screenWidth / 2f
        val verticalGestureType = if (isLeft) "brightness" else "volume"
        val seekStartPositionMs = position()

        val pointerId = down.id
        while (true) {
            val event = awaitPointerEvent()
            val dragPointer = event.changes.find { it.id == pointerId } ?: break

            if (dragPointer.pressed) {
                val currentPosition = dragPointer.position
                val diff = currentPosition - startPosition

                if (!isDrag) {
                    val distance = diff.getDistance()
                    if (distance > gestureSlop) {
                        isDrag = true
                        state.isGestureActive = true
                        onGestureStart()
                        if (abs(diff.y) > abs(diff.x)) {
                            dragGestureType = verticalGestureType
                            state.activeGestureType = verticalGestureType
                        } else {
                            // VLC-style swipe-to-seek: drag horizontally anywhere
                            // to scrub, release to commit.
                            dragGestureType = "seek"
                            state.activeGestureType = "seek"
                            state.seekGestureStartMs = seekStartPositionMs
                            state.seekPreviewMs = seekStartPositionMs
                        }
                        state.showGestureOverlay = true
                    }
                }

                if (isDrag) {
                    dragPointer.consume()
                    when (dragGestureType) {
                        "brightness" -> {
                            val deltaPercent = -(currentPosition.y - startPosition.y) / screenHeight
                            val targetBrightness = (initialBrightness + deltaPercent).coerceIn(0f, 1f)
                            state.gestureBrightness = targetBrightness
                            activity?.let { act ->
                                val lp = act.window.attributes
                                lp.screenBrightness = targetBrightness
                                act.window.attributes = lp
                            }
                        }
                        "volume" -> {
                            val deltaPercent = -(currentPosition.y - startPosition.y) / screenHeight
                            val targetVolumePercent = (initialVolume + deltaPercent).coerceIn(0f, 1f)
                            state.gestureVolume = targetVolumePercent
                            val targetVol = (targetVolumePercent * maxVol).toInt().coerceIn(0, maxVol)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                        }
                        "seek" -> {
                            val deltaMs = ((currentPosition.x - startPosition.x) / screenWidth) * SEEK_GESTURE_RANGE_MS
                            state.seekPreviewMs = (seekStartPositionMs + deltaMs.toLong()).coerceIn(0L, duration())
                        }
                    }
                }
            } else {
                dragPointer.consume()
                break
            }
        }

        if (!isDrag) {
            val endTime = System.currentTimeMillis()
            val tapDuration = endTime - startTime
            val distance = (lastTapPosition - startPosition).getDistance()

            if (tapDuration < 300) {
                if (endTime - lastTapTime < 300 && distance < 100) {
                    // Double tap!
                    val forward = startPosition.x >= screenWidth / 2f
                    if (forward) state.showRightSeekIndicator = true else state.showLeftSeekIndicator = true
                    onDoubleTapSeek(forward)
                    lastTapTime = 0L
                } else {
                    // Single tap!
                    onSingleTap()
                    lastTapTime = endTime
                    lastTapPosition = startPosition
                }
            }
        } else {
            if (dragGestureType == "seek") {
                state.seekPreviewMs?.let { onSeekCommit(it) }
                state.seekPreviewMs = null
            }
            // Drag ended: indicator lingers briefly then hides (no fade animation).
            state.isGestureActive = false
            state.activeGestureType = null
        }
    }
}

/**
 * The transient gesture HUDs — double-tap seek indicators, the side volume/brightness bar, and the
 * center swipe-seek preview — rendered purely from [PlayerGestureState]. Independent of the player's
 * controls overlay, mirroring VLC's separate seek/volume/brightness HUD.
 */
@Composable
fun PlayerGestureOverlays(
    state: PlayerGestureState,
    modifier: Modifier = Modifier,
) {
    // Auto-hide the volume/brightness/seek HUD shortly after dragging stops.
    LaunchedEffect(state.activeGestureType) {
        if (state.activeGestureType == null && state.showGestureOverlay) {
            delay(1000)
            state.showGestureOverlay = false
        }
    }
    if (state.showLeftSeekIndicator) {
        LaunchedEffect(Unit) {
            delay(650)
            state.showLeftSeekIndicator = false
        }
    }
    if (state.showRightSeekIndicator) {
        LaunchedEffect(Unit) {
            delay(650)
            state.showRightSeekIndicator = false
        }
    }

    Box(modifier = modifier) {
        // Double tap feedback overlays — iOS/YouTube-style circular "10s" seek pointer, no animation
        if (state.showLeftSeekIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 48.dp)
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Replay10,
                    contentDescription = "Rewind 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        if (state.showRightSeekIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 48.dp)
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Forward10,
                    contentDescription = "Forward 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Volume — vertical bar on the left edge (same side you swipe), no animation.
        if (state.showGestureOverlay && state.activeGestureType == "volume") {
            VerticalGestureBar(
                progress = state.gestureVolume,
                icon = when {
                    state.gestureVolume == 0f -> Icons.AutoMirrored.Outlined.VolumeMute
                    state.gestureVolume < 0.5f -> Icons.AutoMirrored.Outlined.VolumeDown
                    else -> Icons.AutoMirrored.Outlined.VolumeUp
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp),
            )
        }

        // Brightness — vertical bar on the right edge, no animation.
        if (state.showGestureOverlay && state.activeGestureType == "brightness") {
            VerticalGestureBar(
                progress = state.gestureBrightness,
                icon = when {
                    state.gestureBrightness < 0.3f -> Icons.Outlined.BrightnessLow
                    state.gestureBrightness < 0.7f -> Icons.Outlined.BrightnessMedium
                    else -> Icons.Outlined.BrightnessHigh
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp),
            )
        }

        // VLC-style swipe-to-seek preview — centered, no animation.
        if (state.showGestureOverlay && state.activeGestureType == "seek" && state.seekPreviewMs != null) {
            val previewMs = state.seekPreviewMs!!
            val deltaMs = previewMs - state.seekGestureStartMs
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC101018))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = if (deltaMs >= 0) Icons.Outlined.FastForward else Icons.Outlined.FastRewind,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = formatTime(previewMs),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = (if (deltaMs >= 0) "+" else "-") + formatTime(abs(deltaMs)),
                        color = Color(0xFFC084FC),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** iOS-style vertical fill bar for the volume/brightness swipe gesture (shown on the swiped side). */
@Composable
private fun VerticalGestureBar(
    progress: Float,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(46.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(Color(0xCC101018))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(23.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .width(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x33FFFFFF)),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(progress.coerceIn(0f, 1f))
                    .background(
                        Brush.verticalGradient(colors = listOf(Color(0xFF9E8CFE), AccentPurple))
                    )
            )
        }
        Text(
            text = "${(progress * 100).toInt()}",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

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
