package com.stremio.mobile.data.model

enum class StreamSortCriterion(val label: String) {
    DEFAULT("Default"),
    SEEDS("Seeds"),
    SIZE("Size"),
    QUALITY("Quality"),
}

/** Quality tags ranked best-first; unrecognized/missing quality ranks last. */
private val QUALITY_RANK = listOf("2160p", "1080p", "720p", "480p")

fun parseSeedCount(seeds: String?): Int = seeds?.toIntOrNull() ?: 0

/** Parses a size string like "2.5 GB" / "700 MB" / "500 KB" into a comparable byte count. */
fun parseSizeBytes(size: String?): Long {
    val match = size?.let { Regex("([\\d.]+)\\s*(GB|MB|KB|B)", RegexOption.IGNORE_CASE).find(it) } ?: return 0L
    val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
    val multiplier = when (match.groupValues[2].uppercase()) {
        "GB" -> 1_000_000_000L
        "MB" -> 1_000_000L
        "KB" -> 1_000L
        else -> 1L
    }
    return (value * multiplier).toLong()
}

/** Higher is better; an exact match against [preferredQuality] (when set) always ranks highest. */
fun qualityScore(quality: String?, preferredQuality: String?): Int {
    if (preferredQuality != null && preferredQuality != "Any" && quality == preferredQuality) {
        return QUALITY_RANK.size + 1
    }
    val index = quality?.let { QUALITY_RANK.indexOf(it) } ?: -1
    return if (index < 0) 0 else QUALITY_RANK.size - index
}
