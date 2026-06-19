package com.stremio.mobile.player

data class MpvResizeProperties(
    val keepAspect: Boolean,
    val panscan: Double,
)

object MpvResizeMapper {
    fun properties(mode: PlayerResizeMode): MpvResizeProperties {
        return when (mode) {
            PlayerResizeMode.FIT -> MpvResizeProperties(keepAspect = true, panscan = 0.0)
            PlayerResizeMode.STRETCH -> MpvResizeProperties(keepAspect = false, panscan = 0.0)
            PlayerResizeMode.ZOOM -> MpvResizeProperties(keepAspect = true, panscan = 1.0)
        }
    }
}

