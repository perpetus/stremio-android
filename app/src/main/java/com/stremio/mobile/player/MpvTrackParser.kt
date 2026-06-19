package com.stremio.mobile.player

data class MpvTrackId(
    val kind: String,
    val mpvId: Int,
) {
    fun encode(): String = "$PREFIX:$kind:$mpvId"

    companion object {
        private const val PREFIX = "mpv"

        fun audio(mpvId: Int) = MpvTrackId("audio", mpvId)
        fun subtitle(mpvId: Int) = MpvTrackId("sub", mpvId)

        fun parse(value: String): MpvTrackId? {
            val parts = value.split(':')
            if (parts.size != 3 || parts[0] != PREFIX) return null
            if (parts[1] != "audio" && parts[1] != "sub") return null
            val mpvId = parts[2].toIntOrNull() ?: return null
            return MpvTrackId(parts[1], mpvId)
        }
    }
}

interface MpvTrackPropertyReader {
    fun getInt(property: String): Int?
    fun getString(property: String): String?
    fun getBoolean(property: String): Boolean?
}

object MpvTrackParser {
    fun parse(reader: MpvTrackPropertyReader): Pair<List<PlayerTrackOption>, List<PlayerTrackOption>> {
        val audio = mutableListOf<PlayerTrackOption>()
        val subtitles = mutableListOf<PlayerTrackOption>()
        val count = reader.getInt("track-list/count") ?: 0

        for (index in 0 until count) {
            val type = reader.getString("track-list/$index/type") ?: continue
            if (type != "audio" && type != "sub") continue

            val mpvId = reader.getInt("track-list/$index/id") ?: continue
            val lang = reader.getString("track-list/$index/lang")
            val title = reader.getString("track-list/$index/title")
            val external = reader.getBoolean("track-list/$index/external") == true
            val selected = reader.getBoolean("track-list/$index/selected") == true
            val optionType = if (type == "audio") PlayerTrackType.AUDIO else PlayerTrackType.SUBTITLE
            val target = if (type == "audio") audio else subtitles

            target.add(
                PlayerTrackOption(
                    id = if (type == "audio") MpvTrackId.audio(mpvId).encode() else MpvTrackId.subtitle(mpvId).encode(),
                    type = optionType,
                    label = buildTrackLabel(
                        fallbackPrefix = if (type == "audio") "Track" else "Subtitles",
                        fallbackNumber = target.size + 1,
                        mpvId = mpvId,
                        title = title,
                        language = lang,
                        external = external,
                    ),
                    language = lang,
                    selected = selected,
                    languageCode = LanguageCatalog.toCode(lang),
                    origin = if (type == "audio") {
                        "AUDIO"
                    } else if (external) {
                        "EXTERNAL"
                    } else {
                        "EMBEDDED"
                    },
                    embedded = type == "sub" && !external,
                )
            )
        }

        return audio to subtitles
    }

    private fun buildTrackLabel(
        fallbackPrefix: String,
        fallbackNumber: Int,
        mpvId: Int,
        title: String?,
        language: String?,
        external: Boolean,
    ): String {
        val base = when {
            !title.isNullOrBlank() && !language.isNullOrBlank() -> "$title (${language.uppercase()})"
            !title.isNullOrBlank() -> title
            !language.isNullOrBlank() -> language.uppercase()
            else -> "$fallbackPrefix $fallbackNumber"
        }
        val externalSuffix = if (external) "External" else "Embedded"
        return "$base ($externalSuffix #$mpvId)"
    }
}
