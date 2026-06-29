package com.stremio.mobile.update

import android.content.Context
import android.os.Build
import com.stremio.mobile.BuildConfig
import com.stremio.mobile.core.extensions.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class UpdateInfo(
    val versionName: String,
    val tagName: String,
    val apkName: String,
    val apkUrl: String,
    val sha256Url: String?,
    val releaseNotes: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(
        val bytesRead: Long,
        val totalBytes: Long?,
        val progress: Float?,
    ) : UpdateState
    data class ReadyToInstall(
        val file: File,
        val needsUnknownSourcesPermission: Boolean = false,
    ) : UpdateState
    data class Error(val message: String) : UpdateState
}

class UpdateRepository(private val context: Context) {
    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "StremioMobile/${BuildConfig.VERSION_NAME}")
        }

        connection.use {
            if (it.responseCode !in 200..299) {
                return@withContext UpdateState.Error("GitHub returned HTTP ${it.responseCode}.")
            }

            val release = JSONObject(it.inputStream.bufferedReader().use { reader -> reader.readText() })
            val tagName = release.optString("tag_name").takeIf { tag -> tag.isNotBlank() }
                ?: return@withContext UpdateState.Error("Latest release has no tag.")
            val remoteVersion = tagName.removePrefix("v")
            if (!remoteVersion.isStrictSemver()) {
                return@withContext UpdateState.UpToDate
            }
            if (compareSemver(remoteVersion, BuildConfig.VERSION_NAME) <= 0) {
                return@withContext UpdateState.UpToDate
            }

            val assets = release.optJSONArray("assets")
                ?: return@withContext UpdateState.Error("Latest release has no assets.")
            val parsedAssets = buildList {
                for (index in 0 until assets.length()) {
                    val item = assets.optJSONObject(index) ?: continue
                    val name = item.optString("name")
                    val url = item.optString("browser_download_url")
                    if (name.isNotBlank() && url.isNotBlank()) {
                        add(ReleaseAsset(name = name, url = url))
                    }
                }
            }

            val apkAsset = selectApkAsset(parsedAssets)
                ?: return@withContext UpdateState.Error("No compatible APK found for this device.")
            val checksumAsset = parsedAssets.firstOrNull { asset ->
                asset.name.endsWith("-SHA256SUMS.txt", ignoreCase = true)
            }

            UpdateState.Available(
                UpdateInfo(
                    versionName = remoteVersion,
                    tagName = tagName,
                    apkName = apkAsset.name,
                    apkUrl = apkAsset.url,
                    sha256Url = checksumAsset?.url,
                    releaseNotes = release.optString("body").takeIf { body -> body.isNotBlank() }.orEmpty(),
                )
            )
        }
    }

    suspend fun download(
        info: UpdateInfo,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "updates").apply { mkdirs() }
        val destination = File(updatesDir, info.apkName)
        val partial = File(updatesDir, "${info.apkName}.part")
        if (partial.exists()) {
            partial.delete()
        }

        val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.android.package-archive")
            setRequestProperty("User-Agent", "StremioMobile/${BuildConfig.VERSION_NAME}")
        }

        connection.use {
            if (it.responseCode !in 200..299) {
                throw IllegalStateException("APK download failed with HTTP ${it.responseCode}.")
            }
            val total = it.contentLengthLong.takeIf { length -> length > 0L }
            var bytesRead = 0L
            it.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        onProgress(bytesRead, total)
                    }
                }
            }
        }

        info.sha256Url?.let { checksumUrl ->
            val expected = fetchExpectedSha256(checksumUrl, info.apkName)
            if (expected != null) {
                val actual = partial.sha256()
                if (!actual.equals(expected, ignoreCase = true)) {
                    partial.delete()
                    throw IllegalStateException("Downloaded APK checksum did not match the release checksum.")
                }
            }
        }

        if (destination.exists()) {
            destination.delete()
        }
        if (!partial.renameTo(destination)) {
            partial.copyTo(destination, overwrite = true)
            partial.delete()
        }
        destination
    }

    private fun selectApkAsset(assets: List<ReleaseAsset>): ReleaseAsset? {
        Build.SUPPORTED_ABIS.forEach { abi ->
            assets.firstOrNull { asset ->
                asset.name.endsWith("-$abi-release.apk", ignoreCase = true)
            }?.let { return it }
        }
        return assets.firstOrNull { asset ->
            asset.name.endsWith("-universal-release.apk", ignoreCase = true)
        }
    }

    private fun fetchExpectedSha256(checksumUrl: String, apkName: String): String? {
        val connection = (URL(checksumUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "text/plain")
            setRequestProperty("User-Agent", "StremioMobile/${BuildConfig.VERSION_NAME}")
        }

        return connection.use {
            if (it.responseCode !in 200..299) {
                return@use null
            }
            it.inputStream.bufferedReader().use { reader ->
                reader.lineSequence()
                    .map { line -> line.trim() }
                    .firstOrNull { line -> line.endsWith(" $apkName") || line.endsWith("  $apkName") }
                    ?.substringBefore(' ')
                    ?.takeIf { hash -> hash.length == 64 }
            }
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(Locale.US, byte.toInt() and 0xff) }
    }

    private fun String.isStrictSemver(): Boolean = SEMVER_REGEX.matches(this)

    private fun compareSemver(remote: String, current: String): Int {
        val remoteParts = remote.semverParts() ?: return 0
        val currentParts = current.semverParts() ?: return 1
        for (index in 0..2) {
            val diff = remoteParts[index].compareTo(currentParts[index])
            if (diff != 0) return diff
        }
        return 0
    }

    private fun String.semverParts(): List<Int>? {
        if (!isStrictSemver()) return null
        return split(".").map { it.toInt() }
    }

    private data class ReleaseAsset(
        val name: String,
        val url: String,
    )

    companion object {
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/perpetus/stremio-android/releases/latest"
        private val SEMVER_REGEX = Regex("""\d+\.\d+\.\d+""")
    }
}
