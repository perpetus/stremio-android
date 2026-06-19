package com.stremio.mobile.player

data class ExoTrackId(
    val type: PlayerTrackType,
    val groupIndex: Int,
    val trackIndex: Int,
) {
    fun encode(): String = "$PREFIX:${type.name.lowercase()}:$groupIndex:$trackIndex"

    companion object {
        private const val PREFIX = "exo"

        fun parse(value: String): ExoTrackId? {
            val parts = value.split(':')
            if (parts.size != 4 || parts[0] != PREFIX) return null
            val type = when (parts[1]) {
                "audio" -> PlayerTrackType.AUDIO
                "subtitle" -> PlayerTrackType.SUBTITLE
                else -> return null
            }
            val groupIndex = parts[2].toIntOrNull() ?: return null
            val trackIndex = parts[3].toIntOrNull() ?: return null
            return ExoTrackId(type, groupIndex, trackIndex)
        }
    }
}

