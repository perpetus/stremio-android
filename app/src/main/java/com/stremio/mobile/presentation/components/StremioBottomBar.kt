package com.stremio.mobile.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.presentation.navigation.AppView
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun StremioBottomBar(
    selectedView: AppView,
    backdrop: LayerBackdrop,
    onSelect: (AppView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    // drag-reactive pointer tracking for highlight layer and liquid stretch
    var rawPointerOffset by remember { mutableStateOf(Offset.Unspecified) }
    var isBarPressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    val animatedPtrX by animateFloatAsState(
        targetValue = if (rawPointerOffset != Offset.Unspecified) rawPointerOffset.x else Float.NaN,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "barPtrX",
    )
    val animatedPtrY by animateFloatAsState(
        targetValue = if (rawPointerOffset != Offset.Unspecified) rawPointerOffset.y else Float.NaN,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "barPtrY",
    )
    val barPointer = if (animatedPtrX.isNaN() || animatedPtrY.isNaN()) Offset.Unspecified
    else Offset(animatedPtrX, animatedPtrY)

    val barScale by animateFloatAsState(
        targetValue = if (isBarPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "barScale",
    )

    BoxWithConstraints(
        modifier = modifier
            .scale(barScale)
            .fillMaxWidth(0.94f)
            .navigationBarsPadding()
            .padding(bottom = 10.dp)
            .height(74.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        rawPointerOffset = offset
                        isBarPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        tryAwaitRelease()
                        isBarPressed = false
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        rawPointerOffset = offset
                        isBarPressed = true
                        isDragging = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onDrag = { change, _ ->
                        rawPointerOffset = change.position
                    },
                    onDragEnd = {
                        isBarPressed = false
                        isDragging = false
                        rawPointerOffset = Offset.Unspecified
                    },
                    onDragCancel = {
                        isBarPressed = false
                        isDragging = false
                        rawPointerOffset = Offset.Unspecified
                    },
                )
            },
    ) {
        val selectedIndex = AppView.entries.indexOf(selectedView).coerceAtLeast(0)
        val tabCount = AppView.entries.size
        val horizontalPadding = 8.dp
        val tabWidth = (maxWidth - horizontalPadding * 2) / tabCount

        // Track the last index we vibrated for during sliding
        var lastVibratedIndex by remember { mutableStateOf(selectedIndex) }

        // Animatable indicator offset
        val indicatorOffset = remember { Animatable((horizontalPadding + tabWidth * selectedIndex).value) }

        // Animate indicator offset when selected index changes (not during drag)
        LaunchedEffect(selectedIndex, isDragging) {
            if (!isDragging) {
                val target = (horizontalPadding + tabWidth * selectedIndex).value
                indicatorOffset.animateTo(
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 430f)
                )
            }
        }

        // Keep indicator following the finger during drag
        LaunchedEffect(rawPointerOffset, isDragging) {
            if (isDragging && rawPointerOffset != Offset.Unspecified) {
                val pointerXDp = with(density) { rawPointerOffset.x.toDp() }
                val target = (pointerXDp - tabWidth / 2)
                    .coerceIn(horizontalPadding, maxWidth - horizontalPadding - tabWidth)
                indicatorOffset.snapTo(target.value)
            }
        }

        // When drag finishes, select the closest tab and spring to it
        LaunchedEffect(isDragging) {
            if (!isDragging && rawPointerOffset == Offset.Unspecified) {
                val currentOffsetDp = indicatorOffset.value.dp
                val nearestIndex = ((currentOffsetDp - horizontalPadding) / tabWidth)
                    .roundToInt()
                    .coerceIn(0, tabCount - 1)
                onSelect(AppView.entries[nearestIndex])

                val target = (horizontalPadding + tabWidth * nearestIndex).value
                indicatorOffset.animateTo(
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 430f)
                )
            }
        }

        // Track current nearest tab index and trigger haptics when crossing tab boundaries
        val currentOffsetDp = indicatorOffset.value.dp
        val nearestIndex = ((currentOffsetDp - horizontalPadding) / tabWidth)
            .roundToInt()
            .coerceIn(0, tabCount - 1)

        LaunchedEffect(nearestIndex) {
            if (nearestIndex != lastVibratedIndex) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                lastVibratedIndex = nearestIndex
            }
        }

        // Stretch and Lift Calculations
        val distanceX = remember(indicatorOffset.value, barPointer) {
            if (barPointer != Offset.Unspecified) {
                val fingerXDp = with(density) { barPointer.x.toDp() }
                val centerXDp = indicatorOffset.value + tabWidth.value / 2
                abs(fingerXDp.value - centerXDp)
            } else {
                0f
            }
        }

        val lift by animateFloatAsState(
            targetValue = if (isBarPressed) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 300f),
            label = "indicatorLift",
        )

        val stretch by animateFloatAsState(
            targetValue = (distanceX / tabWidth.value).coerceIn(0f, 0.35f) * lift,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
            label = "indicatorStretch",
        )

        val scaleX = 1f + 0.12f * lift + stretch
        val scaleY = 1f + 0.08f * lift - stretch * 0.4f

        // glass backdrop
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(30.dp) },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(
                            refractionHeight = 16.dp.toPx(),
                            refractionAmount = 32.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Ambient.copy(alpha = 0.75f) },
                    shadow = {
                        Shadow(
                            radius = 28.dp,
                            offset = DpOffset(0.dp, 8.dp),
                            color = Color.Black.copy(alpha = 0.42f),
                        )
                    },
                    onDrawSurface = {
                        drawRoundRect(Color(0x85131220))
                    },
                )
        )

        // drag-reactive specular highlight
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(30.dp)),
        ) {
            GlassHighlightLayer(pointerOffset = barPointer)
        }

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset.value.dp, y = 7.dp)
                .width(tabWidth)
                .height(60.dp)
                .graphicsLayer(
                    scaleX = scaleX,
                    scaleY = scaleY,
                    transformOrigin = TransformOrigin.Center
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(999.dp) },
                    effects = {
                        vibrancy()
                        blur(3.dp.toPx())
                        lens(
                            refractionHeight = 12.dp.toPx(),
                            refractionAmount = 24.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.35f + 0.15f * lift) },
                    shadow = {
                        Shadow(
                            radius = (18f + 6f * lift).dp,
                            offset = DpOffset(0.dp, 4.dp),
                            color = AccentPurple.copy(alpha = 0.24f + 0.08f * lift),
                        )
                    },
                    onDrawSurface = {
                        val alpha = 0.20f + 0.15f * lift
                        drawRoundRect(Color(0x326E55FF).copy(alpha = alpha))
                    },
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppView.entries.forEach { view ->
                BottomTab(
                    view = view,
                    selected = view == selectedView,
                    onClick = { onSelect(view) },
                )
            }
        }
    }
}

@Composable
private fun BottomTab(
    view: AppView,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color by animateColorAsState(
        targetValue = if (selected) Color.White else Color(0xFF9B96A8),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 520f),
        label = "bottomNavTabColor",
    )
    val iconSize by animateDpAsState(
        targetValue = if (selected) 25.dp else 21.dp,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 520f),
        label = "bottomNavIconSize",
    )

    Column(
        modifier = Modifier
            .width(70.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = view.icon,
            contentDescription = view.label,
            tint = if (selected) AccentPurple else color,
            modifier = Modifier
                .padding(top = 7.dp)
                .size(iconSize),
        )
        Text(
            text = view.label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 7.dp),
        )
    }
}
