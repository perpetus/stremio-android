package com.stremio.mobile.core

import android.content.Context
import com.stremio.core.Storage

/**
 * File-backed implementation of the stremio-core [Storage] contract. The core persists its
 * profile (including the user's installed addons), library and other buckets through this
 * key/value store, so the same addons are available on the next launch.
 */
class AndroidStorage(context: Context) : Storage {
    private val prefs = context.applicationContext
        .getSharedPreferences("stremio_core_storage", Context.MODE_PRIVATE)

    override fun get(key: String): Storage.Result<String?> {
        return try {
            Storage.Result.Ok(prefs.getString(key, null))
        } catch (e: Exception) {
            Storage.Result.Err(e.message ?: "Failed to read key $key")
        }
    }

    override fun set(key: String, value: String?): Storage.Result<Unit> {
        return try {
            prefs.edit().apply {
                if (value == null) remove(key) else putString(key, value)
            }.apply()
            Storage.Result.Ok(Unit)
        } catch (e: Exception) {
            Storage.Result.Err(e.message ?: "Failed to write key $key")
        }
    }
}
