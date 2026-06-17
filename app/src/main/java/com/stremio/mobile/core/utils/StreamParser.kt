package com.stremio.mobile.core.utils

data class ParsedStreamMetadata(
    val seeds: String?,
    val size: String?,
    val origin: String?,
    val cleanDescription: String
)

fun parseStreamDescription(description: String?): ParsedStreamMetadata {
    val desc = description ?: ""
    var seeds: String? = null
    var size: String? = null
    var origin: String? = null

    // Emoji matches
    val seedsEmojiRegex = Regex("👤\\s*(\\d+)")
    val sizeEmojiRegex = Regex("💾\\s*([\\d\\.]+\\s*(?:GB|MB|KB|Bytes|B|gb|mb|kb|b))", RegexOption.IGNORE_CASE)
    val originEmojiRegex = Regex("⚙️\\s*([^\\n💾👤]+)")

    // Text matches
    val seedsTextRegex = Regex("(?:seeds|peers):?\\s*(\\d+)", RegexOption.IGNORE_CASE)
    val sizeTextRegex = Regex("(?:size):?\\s*([\\d\\.]+\\s*(?:GB|MB|KB|Bytes|B|gb|mb|kb|b))", RegexOption.IGNORE_CASE)

    seedsEmojiRegex.find(desc)?.let { seeds = it.groupValues[1] }
        ?: seedsTextRegex.find(desc)?.let { seeds = it.groupValues[1] }

    sizeEmojiRegex.find(desc)?.let { size = it.groupValues[1] }
        ?: sizeTextRegex.find(desc)?.let { size = it.groupValues[1] }

    originEmojiRegex.find(desc)?.let { origin = it.groupValues[1].trim() }

    var cleanDesc = desc
    cleanDesc = seedsEmojiRegex.replace(cleanDesc, "")
    cleanDesc = sizeEmojiRegex.replace(cleanDesc, "")
    cleanDesc = originEmojiRegex.replace(cleanDesc, "")
    cleanDesc = seedsTextRegex.replace(cleanDesc, "")
    cleanDesc = sizeTextRegex.replace(cleanDesc, "")
    cleanDesc = cleanDesc.replace(Regex("[👤💾⚙️]"), "")
    
    val cleanedText = cleanDesc.split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

    return ParsedStreamMetadata(seeds, size, origin, cleanedText)
}
