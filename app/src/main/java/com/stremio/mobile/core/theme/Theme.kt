package com.stremio.mobile.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.stremio.mobile.R

private val StremioDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF7CB7FF),
    secondary = Color(0xFF8BD8C7),
    tertiary = Color(0xFFFFC66D),
    background = Color(0xFF101216),
    surface = Color(0xFF171A20),
    onPrimary = Color(0xFF061A2E),
    onSecondary = Color(0xFF06251F),
    onTertiary = Color(0xFF2C1B00),
    onBackground = Color(0xFFE8EAEE),
    onSurface = Color(0xFFE8EAEE),
    onSurfaceVariant = Color(0xFFB8BEC9),
)

/**
 * stremio-web's typeface, ported from the same variable-weight TTF the web client bundles
 * (ref/stremio-web/assets/fonts/PlusJakartaSans.ttf). One physical font file, instantiated at
 * each weight the app actually uses via the variable font's weight axis (no-op below API 26,
 * where the font just renders at its default static weight).
 */
@OptIn(ExperimentalTextApi::class)
private fun jakartaWeight(weight: Int) = Font(
    resId = R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val PlusJakartaSans = FontFamily(
    jakartaWeight(400),
    jakartaWeight(500),
    jakartaWeight(600),
    jakartaWeight(700),
    jakartaWeight(800),
)

private val StremioTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = PlusJakartaSans),
        displayMedium = base.displayMedium.copy(fontFamily = PlusJakartaSans),
        displaySmall = base.displaySmall.copy(fontFamily = PlusJakartaSans),
        headlineLarge = base.headlineLarge.copy(fontFamily = PlusJakartaSans),
        headlineMedium = base.headlineMedium.copy(fontFamily = PlusJakartaSans),
        headlineSmall = base.headlineSmall.copy(fontFamily = PlusJakartaSans),
        titleLarge = base.titleLarge.copy(fontFamily = PlusJakartaSans),
        titleMedium = base.titleMedium.copy(fontFamily = PlusJakartaSans),
        titleSmall = base.titleSmall.copy(fontFamily = PlusJakartaSans),
        bodyLarge = base.bodyLarge.copy(fontFamily = PlusJakartaSans),
        bodyMedium = base.bodyMedium.copy(fontFamily = PlusJakartaSans),
        bodySmall = base.bodySmall.copy(fontFamily = PlusJakartaSans),
        labelLarge = base.labelLarge.copy(fontFamily = PlusJakartaSans),
        labelMedium = base.labelMedium.copy(fontFamily = PlusJakartaSans),
        labelSmall = base.labelSmall.copy(fontFamily = PlusJakartaSans),
    )
}

@Composable
fun StremioMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StremioDarkScheme,
        typography = StremioTypography,
        content = content,
    )
}
