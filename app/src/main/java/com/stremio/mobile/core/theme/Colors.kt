package com.stremio.mobile.core.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared horizontal screen gutter so Home/Discover/Library/Search align to the same left edge. */
val ScreenGutter = 16.dp

/** Space reserved at the bottom of scrollable content so the last row clears the floating bottom bar. */
val BottomBarSpace = 96.dp

val StremioBackground = Color(0xFF050515)
val SearchBackground = Color(0xFF292932)
val CardFallback = Color(0xFF171724)
val AccentPurple = Color(0xFF7457F2)
val MutedText = Color(0xFFB9B5C6)
val GlassSurface = Color(0xB72A2935)

/** Secondary accent used by the web client for primary "play" affordances. */
val AccentGreen = Color(0xFF22B365)

/** stremio-web's root background: a 41° dark navy-to-purple diagonal gradient. */
val StremioBackgroundBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF0C0B11), Color(0xFF1A173E)),
)
