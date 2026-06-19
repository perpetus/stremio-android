package com.stremio.mobile.player

import android.content.Context
import java.util.Locale
import org.json.JSONObject

object LanguageCatalog {
    const val DEFAULT_SUBTITLES_LANGUAGE = "eng"
    const val LOCAL_SUBTITLES_LANGUAGE = "local"

    val subtitleSizes = listOf(75, 100, 125, 150, 175, 200, 250)

    private val aliases = mapOf(
        "al" to "sqi",
        "alb" to "sqi",
        "sq" to "sqi",
        "am" to "amh",
        "ar" to "ara",
        "arm" to "hye",
        "hy" to "hye",
        "baq" to "eus",
        "eu" to "eus",
        "bur" to "mya",
        "my" to "mya",
        "chi" to "zho",
        "zh" to "zho",
        "cn" to "zho",
        "cze" to "ces",
        "cs" to "ces",
        "dut" to "nld",
        "nl" to "nld",
        "en" to "eng",
        "fr" to "fre",
        "fra" to "fre",
        "de" to "ger",
        "deu" to "ger",
        "ger" to "ger",
        "el" to "ell",
        "gre" to "ell",
        "geo" to "kat",
        "ka" to "kat",
        "he" to "heb",
        "iw" to "heb",
        "hi" to "hin",
        "id" to "ind",
        "in" to "ind",
        "is" to "isl",
        "ice" to "isl",
        "ja" to "jpn",
        "ko" to "kor",
        "mac" to "mkd",
        "mk" to "mkd",
        "mao" to "mri",
        "mi" to "mri",
        "may" to "msa",
        "ms" to "msa",
        "no" to "nor",
        "fa" to "fas",
        "per" to "fas",
        "pt-br" to "pob",
        "pb" to "pob",
        "ro" to "ron",
        "rum" to "ron",
        "sk" to "slk",
        "slo" to "slk",
        "es" to "spa",
        "tib" to "bod",
        "bo" to "bod",
        "cy" to "cym",
        "wel" to "cym",
        "local" to LOCAL_SUBTITLES_LANGUAGE,
    )

    @Volatile
    private var cachedNames: Map<String, String>? = null

    fun toCode(value: String?): String? {
        val raw = value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
        aliases[raw]?.let { return it }
        if (raw.length == 3) return raw

        if ('-' in raw) {
            runCatching {
                Locale.forLanguageTag(raw).isO3Language.lowercase(Locale.ROOT)
            }.getOrNull()?.let { return aliases[it] ?: it }
        }

        return runCatching {
            Locale.Builder().setLanguage(raw).build().isO3Language.lowercase(Locale.ROOT)
        }.getOrNull()?.let { aliases[it] ?: it }
    }

    fun matches(left: String?, right: String?): Boolean {
        val leftCode = toCode(left) ?: return false
        val rightCode = toCode(right) ?: return false
        return leftCode == rightCode
    }

    fun label(context: Context, code: String?): String {
        val normalized = toCode(code)
        if (normalized == LOCAL_SUBTITLES_LANGUAGE) return "Local"
        return normalized?.let { names(context)[it] } ?: code?.uppercase(Locale.ROOT) ?: "Unknown"
    }

    fun options(context: Context): List<Pair<String, String>> {
        return names(context)
            .toList()
            .sortedBy { (_, label) -> label.lowercase(Locale.ROOT) }
    }

    private fun names(context: Context): Map<String, String> {
        cachedNames?.let { return it }
        val loaded = context.assets.open("languageNames.json").use { input ->
            val json = JSONObject(input.bufferedReader().use { it.readText() })
            buildMap {
                json.keys().forEach { key ->
                    put(key, json.getString(key))
                }
            }
        }
        cachedNames = loaded
        return loaded
    }
}
