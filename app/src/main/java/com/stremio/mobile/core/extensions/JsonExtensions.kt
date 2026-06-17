package com.stremio.mobile.core.extensions

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection

inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T {
    return try {
        block(this)
    } finally {
        disconnect()
    }
}

fun JSONObject.optNullableString(name: String): String? {
    return optString(name).takeIf { it.isNotBlank() && it != "null" }
}

fun JSONObject.optStringArray(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val value = array.optString(index)
            if (value.isNotBlank()) {
                add(value)
            }
        }
    }
}
