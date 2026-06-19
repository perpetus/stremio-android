package com.stremio.mobile.data.model

const val LIQUID_GLASS_RECOMMENDED_GLOBAL_ALPHA = 0.50f

data class LiquidGlassTuning(
    // Defaults follow kyant/backdrop's glass bottom bar and interactive glass
    // examples: 4.dp blur, 16/32 dp lens refraction, and 0.5 white surface.
    // Toggle/slider thumbs use the smaller reference values internally.
    val blurDp: Float = 4f,
    val refractionHeightDp: Float = 16f,
    val refractionAmountDp: Float = 32f,
    val surfaceAlpha: Float = 0.50f,
    val highlightAlpha: Float = 0.55f,
    val borderAlpha: Float = 0.35f,
    val shadowAlpha: Float = 0.30f,
    val trackAlpha: Float = 0.20f,
    val thumbAlpha: Float = 1f,
    val chromaticAberration: Boolean = true,
) {
    fun clamped(): LiquidGlassTuning = copy(
        blurDp = blurDp.coerceIn(0f, 24f),
        refractionHeightDp = refractionHeightDp.coerceIn(0f, 32f),
        refractionAmountDp = refractionAmountDp.coerceIn(0f, 64f),
        surfaceAlpha = surfaceAlpha.coerceIn(0f, 0.80f),
        highlightAlpha = highlightAlpha.coerceIn(0f, 1f),
        borderAlpha = borderAlpha.coerceIn(0f, 1f),
        shadowAlpha = shadowAlpha.coerceIn(0f, 0.80f),
        trackAlpha = trackAlpha.coerceIn(0f, 0.80f),
        thumbAlpha = thumbAlpha.coerceIn(0.20f, 1f),
    )
}
