package com.stremio.mobile.data.repository

import com.stremio.mobile.core.StremioCore
import com.stremio.mobile.core.extensions.optNullableString
import com.stremio.mobile.core.extensions.optStringArray
import com.stremio.mobile.core.extensions.use
import com.stremio.mobile.data.model.AddonItem
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CatalogRepository(private val core: StremioCore) {

    fun getDiscoverFlow(): Flow<com.stremio.core.models.CatalogWithFilters> = core.discover()

    fun getLibraryFlow(): Flow<com.stremio.core.models.LibraryWithFilters> = core.library()

    fun getLibraryShelfFlow(): Flow<CatalogShelf> {
        return core.library().map { libraryWithFilters ->
            val catalogItems = libraryWithFilters.catalog.map { item ->
                CatalogItem(
                    id = item.id,
                    type = item.type,
                    name = item.name,
                    poster = item.poster,
                    background = null,
                    releaseInfo = null,
                    imdbRating = null,
                    watched = item.watched,
                    remainingEpisodes = item.remainingEpisodes
                )
            }
            CatalogShelf(
                title = "Library",
                items = catalogItems,
                isLoading = false
            )
        }
    }

    fun getDiscover(): com.stremio.core.models.CatalogWithFilters = core.getDiscover()

    fun loadDiscover(request: com.stremio.core.types.addon.ResourceRequest) {
        core.loadDiscover(request)
    }

    fun loadLibrary(request: com.stremio.core.models.LibraryWithFilters.LibraryRequest) {
        core.loadLibrary(request)
    }

    suspend fun syncLibrary() {
        core.syncLibrary()
    }

    fun addToLibrary(item: CatalogItem) {
        core.addToLibrary(item)
    }

    fun removeFromLibrary(id: String) {
        core.removeFromLibrary(id)
    }

    fun search(query: String): Flow<com.stremio.core.models.CatalogsWithExtra> {
        return core.search(query)
    }

    fun loadSearchRange(start: Int, end: Int) {
        core.loadSearchRange(start, end)
    }

    fun getMetaDetailsFlow(
        type: String,
        id: String,
        videoId: String?,
        guessStreamPath: Boolean
    ): Flow<com.stremio.core.models.MetaDetails> {
        return core.metaDetails(type = type, id = id, videoId = videoId, guessStreamPath = guessStreamPath)
    }

    fun extractVideos(details: com.stremio.core.models.MetaDetails) = core.extractVideos(details)

    fun extractStreams(details: com.stremio.core.models.MetaDetails) = core.extractStreams(details)

    fun directUrl(stream: com.stremio.core.types.resource.Stream): String? = core.directUrl(stream)

    fun extractDiscoverItems(discover: com.stremio.core.models.CatalogWithFilters): List<CatalogItem> {
        return discover.catalog.pages.flatMap { page ->
            val content = page.content
            if (content is com.stremio.core.models.LoadablePage.Content.Ready) {
                content.value.metaItems.map { item ->
                    CatalogItem(
                        id = item.id,
                        type = item.type,
                        name = item.name,
                        poster = item.poster,
                        background = item.background,
                        releaseInfo = item.releaseInfo,
                        imdbRating = null,
                        inCinema = item.inCinema,
                        watched = item.watched
                    )
                }
            } else {
                emptyList()
            }
        }
    }

    fun fetchCatalog(endpoint: String): List<CatalogItem> {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }

        return connection.use {
            val body = it.inputStream.bufferedReader().use { reader -> reader.readText() }
            val metas = JSONObject(body).getJSONArray("metas")
            buildList {
                for (index in 0 until metas.length()) {
                    val meta = metas.getJSONObject(index)
                    add(
                        CatalogItem(
                            id = meta.optString("id", meta.optString("imdb_id")),
                            type = meta.optString("type"),
                            name = meta.optString("name"),
                            poster = meta.optNullableString("poster"),
                            background = meta.optNullableString("background"),
                            releaseInfo = meta.optNullableString("releaseInfo"),
                            imdbRating = meta.optNullableString("imdbRating"),
                            inCinema = meta.optBoolean("inCinema", false),
                        ),
                    )
                }
            }
        }
    }

    fun fetchAddons(endpoint: String): List<AddonItem> {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }

        return connection.use {
            val body = it.inputStream.bufferedReader().use { reader -> reader.readText() }
            val jsonAddons = JSONObject(body).getJSONArray("addons")
            buildList {
                for (index in 0 until jsonAddons.length()) {
                    val addon = jsonAddons.getJSONObject(index)
                    val manifest = addon.optJSONObject("manifest") ?: continue
                    add(
                        AddonItem(
                            id = manifest.optString("id", addon.optString("transportUrl")),
                            name = manifest.optString("name", "Unknown addon"),
                            description = manifest.optNullableString("description"),
                            logo = manifest.optNullableString("logo"),
                            version = manifest.optNullableString("version"),
                            types = manifest.optStringArray("types"),
                            transportUrl = addon.optNullableString("transportUrl"),
                        ),
                    )
                }
            }
        }
    }
}
