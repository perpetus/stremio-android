package com.stremio.mobile.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import com.stremio.mobile.core.theme.*

// ─── Blob Data ────────────────────────────────────────────────────────────────

private data class BlobSpec(
    val color: Color,
    val radiusFraction: Float,
    val xPhaseOffset: Float,
    val yPhaseOffset: Float,
    val xFrequency: Float,
    val yFrequency: Float,
    val xAmplitude: Float,
    val yAmplitude: Float,
)

private val DefaultBlobs = listOf(
    BlobSpec(Color(0xCC7B5EFF), 0.42f, 0f, 0.3f, 0.23f, 0.17f, 0.38f, 0.32f),
    BlobSpec(Color(0xBB3EC6FF), 0.35f, 1.2f, 2.1f, 0.19f, 0.25f, 0.34f, 0.40f),
    BlobSpec(Color(0xAAFF6ECB), 0.30f, 2.8f, 0.7f, 0.27f, 0.15f, 0.30f, 0.36f),
    BlobSpec(Color(0x9945E8B0), 0.28f, 4.0f, 3.5f, 0.21f, 0.29f, 0.36f, 0.28f),
    BlobSpec(Color(0x88FFB347), 0.24f, 5.3f, 1.9f, 0.31f, 0.13f, 0.28f, 0.34f),
)

// ─── LiquidBlobBackground ─────────────────────────────────────────────────────

/**
 * Animated background of slow-moving, blurred colorful blobs that drift
 * organically across the canvas. Sits behind glass layers to provide depth.
 *
 * When [isPressed] is true, blobs lift toward the glass surface — rising,
 * scaling up, and brightening — with a staggered spring so each blob moves
 * independently. On release they settle back with elastic bounce.
 *
 * [pointerOffset] makes the blobs gravitate toward the user's finger during
 * drag, creating a soft liquid-displacement effect.
 */
@Composable
fun LiquidBlobBackground(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 80.dp,
    isPressed: Boolean = false,
    pointerOffset: Offset = Offset.Unspecified,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "blobMotion")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "blobTime",
    )

    // Per-blob lift animatables (0f = resting, 1f = fully lifted)
    val blobLifts = remember {
        DefaultBlobs.map { Animatable(0f) }
    }

    // Staggered launch: each blob starts its animation with a small delay
    LaunchedEffect(isPressed) {
        DefaultBlobs.forEachIndexed { index, _ ->
            launch {
                // stagger: 35ms between each blob for organic feel
                delay(index * 35L)
                blobLifts[index].animateTo(
                    targetValue = if (isPressed) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = if (isPressed) 0.55f else 0.40f,
                        stiffness = if (isPressed) 300f else 150f,
                    ),
                )
            }
        }
    }

    // Snapping and spring-animated pointer X/Y to avoid jumping from (0,0)
    val attractX = remember { Animatable(0f) }
    val attractY = remember { Animatable(0f) }
    var hasSnapped by remember { mutableStateOf(false) }

    LaunchedEffect(pointerOffset) {
        if (pointerOffset != Offset.Unspecified) {
            if (!hasSnapped) {
                attractX.snapTo(pointerOffset.x)
                attractY.snapTo(pointerOffset.y)
                hasSnapped = true
            } else {
                launch {
                    attractX.animateTo(
                        pointerOffset.x,
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = 220f)
                    )
                }
                launch {
                    attractY.animateTo(
                        pointerOffset.y,
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = 220f)
                    )
                }
            }
        } else {
            hasSnapped = false
        }
    }

    // Animate the attraction weight (0f = no attraction/resting, 1f = fully attracted)
    val attractWeight by animateFloatAsState(
        targetValue = if (pointerOffset != Offset.Unspecified) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 150f),
        label = "blobAttractWeight",
    )

    // Animate blur radius on press to make blobs sharper and more prominent
    val animatedBlurRadius by animateDpAsState(
        targetValue = if (isPressed) 35.dp else blurRadius,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 180f),
        label = "blobBlurRadius",
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .blur(animatedBlurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded),
    ) {
        val w = size.width
        val h = size.height

        for ((index, blob) in DefaultBlobs.withIndex()) {
            val lift = blobLifts[index].value

            // lift shifts blob upward by up to 22% of canvas height
            val yShift = -h * 0.22f * lift
            // scale grows by up to 45%
            val scaleFactor = 1f + 0.45f * lift
            // alpha brightens by up to 60%
            val alphaBoost = 1f + 0.60f * lift

            val baseCx = w * 0.5f + w * blob.xAmplitude * sin(time * blob.xFrequency + blob.xPhaseOffset)
            val baseCy = h * 0.5f + h * blob.yAmplitude * cos(time * blob.yFrequency + blob.yPhaseOffset) + yShift

            // gravitate toward pointer — stronger for closer/smaller blobs
            val attractStrength = attractWeight * (0.45f + index * 0.10f)
            val cx = baseCx + (attractX.value - baseCx) * attractStrength
            val cy = baseCy + (attractY.value - baseCy) * attractStrength

            val r = min(w, h) * blob.radiusFraction * scaleFactor
            val boostedColor = blob.color.copy(alpha = (blob.color.alpha * alphaBoost).coerceAtMost(1f))

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(boostedColor, boostedColor.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }
    }
}

// ─── GlassHighlightLayer ──────────────────────────────────────────────────────

/**
 * A specular highlight layer that follows the user's finger across a glass
 * surface. When no pointer is active the highlight rests at center.
 */
@Composable
fun GlassHighlightLayer(
    modifier: Modifier = Modifier,
    pointerOffset: Offset = Offset.Unspecified,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val hasPointer = pointerOffset != Offset.Unspecified

        // pointer-reactive position, centered by default
        val highlightCx = if (hasPointer) pointerOffset.x else w * 0.5f
        val highlightCy = if (hasPointer) pointerOffset.y - h * 0.35f else h * -0.15f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.06f),
                    Color.Transparent,
                ),
                center = Offset(highlightCx, highlightCy),
                radius = w * 0.55f,
            ),
            radius = w * 0.55f,
            center = Offset(highlightCx, highlightCy),
        )

        // inner glow along top edge
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                startY = 0f,
                endY = h * 0.35f,
            ),
        )

        // subtle bottom reflection
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.03f)),
                startY = h * 0.75f,
                endY = h,
            ),
        )
    }
}

// ─── LiquidGlassCard ──────────────────────────────────────────────────────────

/**
 * Frosted-glass card using [drawBackdrop] for GPU-accelerated blur, vibrancy
 * and lens refraction. Touch and drag the card to shift highlights toward
 * the pointer, with spring-animated scale and smooth highlight motion.
 */
@Composable
fun LiquidGlassCard(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)

    // pointer tracking — raw target follows finger, animated value springs behind
    var rawPointerOffset by remember { mutableStateOf(Offset.Unspecified) }
    var isPressed by remember { mutableStateOf(false) }

    val animatedX by animateFloatAsState(
        targetValue = if (rawPointerOffset != Offset.Unspecified) rawPointerOffset.x else Float.NaN,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "ptrX",
    )
    val animatedY by animateFloatAsState(
        targetValue = if (rawPointerOffset != Offset.Unspecified) rawPointerOffset.y else Float.NaN,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "ptrY",
    )
    val pointerOffset = if (animatedX.isNaN() || animatedY.isNaN()) Offset.Unspecified
    else Offset(animatedX, animatedY)

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "cardScale",
    )

    Box(modifier = modifier.scale(scale)) {
        // glass backdrop surface
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(cornerRadius) },
                    effects = {
                        vibrancy()
                        blur(12.dp.toPx())
                        lens(
                            refractionHeight = 10.dp.toPx(),
                            refractionAmount = 20.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Ambient.copy(alpha = 0.55f) },
                    shadow = {
                        Shadow(
                            radius = 24.dp,
                            offset = DpOffset(0.dp, 6.dp),
                            color = Color.Black.copy(alpha = 0.36f),
                        )
                    },
                    onDrawSurface = {
                        drawRoundRect(Color(0x30FFFFFF))
                    },
                ),
        )

        // luminous border
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 0.8.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.08f),
                        ),
                    ),
                    shape = shape,
                ),
        )

        // specular highlight chasing pointer
        Box(modifier = Modifier.matchParentSize().clip(shape)) {
            GlassHighlightLayer(pointerOffset = pointerOffset)
        }

        // gesture overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            rawPointerOffset = offset
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            rawPointerOffset = offset
                            isPressed = true
                        },
                        onDrag = { change, _ -> rawPointerOffset = change.position },
                        onDragEnd = { isPressed = false },
                        onDragCancel = { isPressed = false },
                    )
                },
        )

        // consumer content
        content()
    }
}

// ─── LiquidGlassButton ────────────────────────────────────────────────────────

/**
 * Pill-shaped translucent glass button with backdrop blur, press animation,
 * and inner highlight.
 */
@Composable
fun LiquidGlassButton(
    text: String,
    backdrop: LayerBackdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 700f),
        label = "btnScale",
    )

    val shape = RoundedCornerShape(999.dp)

    Box(modifier = modifier.scale(scale).height(52.dp)) {
        // glass backdrop fill
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(999.dp) },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(
                            refractionHeight = 6.dp.toPx(),
                            refractionAmount = 12.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.30f) },
                    shadow = {
                        Shadow(
                            radius = 14.dp,
                            offset = DpOffset(0.dp, 3.dp),
                            color = AccentPurple.copy(alpha = 0.22f),
                        )
                    },
                    onDrawSurface = {
                        drawRoundRect(Color(0x286E55FF))
                    },
                ),
        )

        // luminous border
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 0.7.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.32f),
                            AccentPurple.copy(alpha = 0.20f),
                        ),
                    ),
                    shape = shape,
                ),
        )

        // inner shimmer
        Box(modifier = Modifier.matchParentSize().clip(shape)) {
            GlassHighlightLayer()
        }

        // clickable label
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            )
        }
    }
}
