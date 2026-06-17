package com.stremio.mobile.presentation.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stremio.core.runtime.RuntimeEvent
import com.stremio.core.runtime.msg.Event
import com.stremio.mobile.core.CoreStream
import com.stremio.mobile.core.StremioCore
import com.stremio.mobile.core.utils.parseStreamDescription
import com.stremio.mobile.data.model.*
import com.stremio.mobile.data.repository.*
import com.stremio.mobile.player.PlaybackState
import com.stremio.mobile.presentation.state.*
import com.stremio.mobile.server.StreamingServerController
import com.stremio.mobile.server.StreamingServerState
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

class MainViewModel(
    private val authRepository: AuthRepository,
    private val boardRepository: BoardRepository,
    private val catalogRepository: CatalogRepository,
    private val playbackRepository: PlaybackRepository,
    private val serverController: StreamingServerController,
    private val core: StremioCore,
    appContext: Context,
) : ViewModel() {
    private val latestIntentUri = MutableStateFlow<String?>(null)
    private val account = MutableStateFlow(authRepository.accountFromCore())
    private var authInFlight = false
    private val selectedSection = MutableStateFlow(MainSection.Home)
    private val searchQuery = MutableStateFlow("")
    private val searchResults = MutableStateFlow(CatalogShelf(title = "Search", isLoading = false))
    private val searchShelves = MutableStateFlow<List<CatalogShelf>>(emptyList())
    private val isSearchOpen = MutableStateFlow(false)
    private val library = MutableStateFlow(CatalogShelf(title = "Library", isLoading = false))
    private val addons = MutableStateFlow(AddonsUiState())
    private val selectedDetails = MutableStateFlow<MetaDetails?>(null)
    private val continueWatching = MutableStateFlow(CatalogShelf(title = "Continue Watching"))
    private val boardShelves = MutableStateFlow<List<CatalogShelf>>(emptyList())
    private val requestedRequests = mutableSetOf<String>()

    @Volatile
    private var cachedBoard: com.stremio.core.models.CatalogsWithExtra? = null

    private val discoverCatalogRequest = MutableStateFlow<com.stremio.core.types.addon.ResourceRequest?>(null)
    private val discoverCatalogTitle = MutableStateFlow<String?>(null)
    private val discoverCatalog = MutableStateFlow(CatalogShelf(title = "Discover", isLoading = false))
    private val discoverCatalogWithFilters = MutableStateFlow<com.stremio.core.models.CatalogWithFilters?>(null)
    private val libraryWithFilters = MutableStateFlow<com.stremio.core.models.LibraryWithFilters?>(null)
    private val isDiscoverSeeAll = MutableStateFlow(false)
    private val profileSettings = MutableStateFlow<com.stremio.core.types.profile.Profile.Settings?>(null)
    private val serverSettings = MutableStateFlow<com.stremio.core.models.StreamingServer.Settings?>(null)
    private val isAutoStartOnBoot = MutableStateFlow(authRepository.isAutoStartOnBoot())
    private val isServerInForeground = MutableStateFlow(authRepository.isServerInForeground())
    private val isMobileDataWarning = MutableStateFlow(authRepository.isMobileDataWarning())
    private val isKeepScreenOn = MutableStateFlow(authRepository.isKeepScreenOn())
    private val isTraktAuthenticated = MutableStateFlow(false)
    private val isSeedingEnabled = MutableStateFlow(true)

    private val serverPingStatus = MutableStateFlow("Not checked")
    private val isPingLoading = MutableStateFlow(false)

    private val configPath = File(appContext.filesDir, "stream-server").absolutePath
    private val cachePath = File(appContext.cacheDir, "stream-server").absolutePath

    private val streams = MutableStateFlow(StreamsUiState())
    val streamsState: StateFlow<StreamsUiState> = streams

    private val isRestoringSession = MutableStateFlow(
        run {
            val authKey = authRepository.getSavedAuthKey()
            !authKey.isNullOrBlank() && authKey != "mock_auth_key" && !authRepository.isAuthenticated()
        }
    )
    val sessionRestoring: StateFlow<Boolean> = isRestoringSession

    val playbackState: StateFlow<PlaybackState> = playbackRepository.state

    private val playerOpen = MutableStateFlow(false)
    val isPlayerOpen: StateFlow<Boolean> = playerOpen

    private var streamsJob: Job? = null
    private var playJob: Job? = null
    private var searchJob: Job? = null
    private var detailsJob: Job? = null

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<MainUiState> = combine(
        listOf(
            serverController.state,
            serverPingStatus,
            isPingLoading,
            latestIntentUri,
            account,
            selectedSection,
            searchQuery,
            searchResults,
            library,
            addons,
            selectedDetails,
            continueWatching,
            boardShelves,
            discoverCatalogRequest,
            discoverCatalogTitle,
            discoverCatalog,
            discoverCatalogWithFilters,
            libraryWithFilters,
            isDiscoverSeeAll,
            searchShelves,
            profileSettings,
            serverSettings,
            isAutoStartOnBoot,
            isServerInForeground,
            isMobileDataWarning,
            isKeepScreenOn,
            isTraktAuthenticated,
            isSeedingEnabled,
            isSearchOpen
        )
    ) { values ->
        MainUiState(
            server = values[0] as StreamingServerState,
            serverPingStatus = values[1] as String,
            isPingLoading = values[2] as Boolean,
            serverVersion = "0.1.5",
            serverConfigPath = configPath,
            serverCachePath = cachePath,
            latestIntentUri = values[3] as String?,
            account = values[4] as AccountUiState,
            selectedSection = values[5] as MainSection,
            searchQuery = values[6] as String,
            searchResults = values[7] as CatalogShelf,
            searchShelves = values[19] as List<CatalogShelf>,
            library = values[8] as CatalogShelf,
            addons = values[9] as AddonsUiState,
            selectedDetails = values[10] as MetaDetails?,
            continueWatching = values[11] as CatalogShelf,
            boardShelves = values[12] as List<CatalogShelf>,
            discoverCatalogRequest = values[13] as com.stremio.core.types.addon.ResourceRequest?,
            discoverCatalogTitle = values[14] as String?,
            discoverCatalog = values[15] as CatalogShelf,
            discoverCatalogWithFilters = values[16] as com.stremio.core.models.CatalogWithFilters?,
            libraryWithFilters = values[17] as com.stremio.core.models.LibraryWithFilters?,
            isDiscoverSeeAll = values[18] as Boolean,
            profileSettings = values[20] as com.stremio.core.types.profile.Profile.Settings?,
            serverSettings = values[21] as com.stremio.core.models.StreamingServer.Settings?,
            isAutoStartOnBoot = values[22] as Boolean,
            isServerInForeground = values[23] as Boolean,
            isMobileDataWarning = values[24] as Boolean,
            isKeepScreenOn = values[25] as Boolean,
            isTraktAuthenticated = values[26] as Boolean,
            isSeedingEnabled = values[27] as Boolean,
            isSearchOpen = values[28] as Boolean,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    init {
        observeBoard()
        observeContinueWatching()
        observeDiscover()
        observeLibrary()
        refreshAddons()
        refreshLibrary()
        observeCoreAuth()
        observeStreamingServer()
        restoreCoreSession()
    }

    private fun observeCoreAuth() {
        viewModelScope.launch {
            authRepository.getAccountStateFlow().collect { accountState ->
                account.value = accountState
                isRestoringSession.value = false
                if (accountState.isAuthenticated && accountState.authKey != null) {
                    authRepository.saveAuthKeyAndEmail(accountState.authKey, accountState.email ?: "")
                    refreshLibrary()
                }
            }
        }
        viewModelScope.launch {
            authRepository.getProfileSettingsFlow().collect { settings ->
                profileSettings.value = settings
            }
        }
        viewModelScope.launch {
            authRepository.getTraktAuthFlow().collect { traktAuth ->
                isTraktAuthenticated.value = traktAuth
            }
        }
        viewModelScope.launch {
            core.events.collect { runtimeEvent ->
                val inner = runtimeEvent.event
                if (inner is RuntimeEvent.Event.CoreEvent && inner.value.type is Event.Type.Error && authInFlight) {
                    authInFlight = false
                    val message = (inner.value.type as Event.Type.Error).value.error
                    account.value = account.value.copy(isLoading = false, error = message)
                }
            }
        }
    }

    private fun observeStreamingServer() {
        viewModelScope.launch {
            core.streamingServerFlow().collect { server ->
                val content = server.settings.content
                if (content is com.stremio.core.models.LoadableSettings.Content.Ready) {
                    serverSettings.value = content.value
                }
            }
        }
        viewModelScope.launch {
            serverController.state.collect { state ->
                if (state is StreamingServerState.Ready) {
                    fetchStreamingServerSettingsDirectly()
                }
            }
        }
    }

    fun fetchStreamingServerSettingsDirectly() {
        viewModelScope.launch {
            val currentState = serverController.state.value
            if (currentState is StreamingServerState.Ready) {
                val url = "${currentState.baseUrl}/settings"
                val settingsObj = withContext(Dispatchers.IO) {
                    try {
                        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 3000
                        connection.readTimeout = 3000
                        connection.setRequestProperty("Accept", "application/json")
                        if (connection.responseCode == 200) {
                            val body = connection.inputStream.bufferedReader().use { it.readText() }
                            JSONObject(body).optJSONObject("values")
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "Failed to fetch settings directly from server", e)
                        null
                    }
                }
                if (settingsObj != null) {
                    val current = serverSettings.value
                    val newSettings = com.stremio.core.models.StreamingServer.Settings(
                        appPath = settingsObj.optString("appPath", current?.appPath ?: ""),
                        cacheRoot = settingsObj.optString("cacheRoot", current?.cacheRoot ?: ""),
                        serverVersion = settingsObj.optString("serverVersion", current?.serverVersion ?: ""),
                        remoteHttps = if (settingsObj.has("remoteHttps") && !settingsObj.isNull("remoteHttps")) settingsObj.optString("remoteHttps") else null,
                        transcodeProfile = if (settingsObj.has("transcodeProfile") && !settingsObj.isNull("transcodeProfile")) settingsObj.optString("transcodeProfile") else null,
                        cacheSize = if (settingsObj.has("cacheSize") && !settingsObj.isNull("cacheSize")) settingsObj.optDouble("cacheSize") else null,
                        proxyStreamsEnabled = settingsObj.optBoolean("proxyStreamsEnabled", current?.proxyStreamsEnabled ?: true),
                        btMaxConnections = settingsObj.optLong("btMaxConnections", current?.btMaxConnections ?: 200L),
                        btHandshakeTimeout = settingsObj.optLong("btHandshakeTimeout", current?.btHandshakeTimeout ?: 200L),
                        btRequestTimeout = settingsObj.optLong("btRequestTimeout", current?.btRequestTimeout ?: 10L),
                        btDownloadSpeedSoftLimit = settingsObj.optDouble("btDownloadSpeedSoftLimit", current?.btDownloadSpeedSoftLimit ?: 1.0),
                        btDownloadSpeedHardLimit = settingsObj.optDouble("btDownloadSpeedHardLimit", current?.btDownloadSpeedHardLimit ?: 2.0),
                        btMinPeersForStable = settingsObj.optLong("btMinPeersForStable", current?.btMinPeersForStable ?: 10L)
                    )
                    serverSettings.value = newSettings
                    isSeedingEnabled.value = settingsObj.optBoolean("seedingEnabled", true)
                    core.updateStreamingServerSettings(newSettings)
                }
            }
        }
    }

    fun updateProfileSettings(newSettings: com.stremio.core.types.profile.Profile.Settings) {
        viewModelScope.launch {
            authRepository.updateSettings(newSettings)
        }
    }

    fun updateStreamingServerSettings(newSettings: com.stremio.core.models.StreamingServer.Settings) {
        viewModelScope.launch {
            serverSettings.value = newSettings
            core.updateStreamingServerSettings(newSettings)

            val currentState = serverController.state.value
            if (currentState is StreamingServerState.Ready) {
                val url = "${currentState.baseUrl}/settings"
                withContext(Dispatchers.IO) {
                    try {
                        val payloadObj = JSONObject().apply {
                            put("appPath", newSettings.appPath)
                            put("cacheRoot", newSettings.cacheRoot)
                            put("serverVersion", newSettings.serverVersion)
                            put("remoteHttps", newSettings.remoteHttps ?: JSONObject.NULL)
                            put("transcodeProfile", newSettings.transcodeProfile ?: JSONObject.NULL)
                            put("cacheSize", newSettings.cacheSize ?: JSONObject.NULL)
                            put("proxyStreamsEnabled", newSettings.proxyStreamsEnabled)
                            put("btMaxConnections", newSettings.btMaxConnections)
                            put("btHandshakeTimeout", newSettings.btHandshakeTimeout)
                            put("btRequestTimeout", newSettings.btRequestTimeout)
                            put("btDownloadSpeedSoftLimit", newSettings.btDownloadSpeedSoftLimit)
                            put("btDownloadSpeedHardLimit", newSettings.btDownloadSpeedHardLimit)
                            put("btMinPeersForStable", newSettings.btMinPeersForStable)
                            put("seedingEnabled", isSeedingEnabled.value)
                        }

                        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.connectTimeout = 3000
                        connection.readTimeout = 3000
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.setRequestProperty("Accept", "application/json")

                        connection.outputStream.use { os ->
                            os.write(payloadObj.toString().toByteArray(Charsets.UTF_8))
                        }

                        val responseCode = connection.responseCode
                        if (responseCode == 200) {
                            android.util.Log.d("MainViewModel", "Successfully updated settings directly on server")
                        } else {
                            android.util.Log.e("MainViewModel", "Failed to update settings directly on server: response code $responseCode")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "Error posting settings to server", e)
                    }
                }
            }
        }
    }

    fun setSeedingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            isSeedingEnabled.value = enabled
            val currentSettings = serverSettings.value
            if (currentSettings != null) {
                updateStreamingServerSettings(currentSettings)
            }
        }
    }

    fun isAutoStartOnBoot(): Boolean = authRepository.isAutoStartOnBoot()
    fun setAutoStartOnBoot(enabled: Boolean) {
        authRepository.setAutoStartOnBoot(enabled)
        isAutoStartOnBoot.value = enabled
    }

    fun isServerInForeground(): Boolean = authRepository.isServerInForeground()
    fun setServerInForeground(enabled: Boolean) {
        authRepository.setServerInForeground(enabled)
        isServerInForeground.value = enabled
    }

    fun isMobileDataWarning(): Boolean = authRepository.isMobileDataWarning()
    fun setMobileDataWarning(enabled: Boolean) {
        authRepository.setMobileDataWarning(enabled)
        isMobileDataWarning.value = enabled
    }

    fun isKeepScreenOn(): Boolean = authRepository.isKeepScreenOn()
    fun setKeepScreenOn(enabled: Boolean) {
        authRepository.setKeepScreenOn(enabled)
        isKeepScreenOn.value = enabled
    }

    fun isTraktAuthenticated(): Boolean = authRepository.isTraktAuthenticated()

    fun authenticateTrakt(context: Context) {
        authRepository.authenticateTrakt(context)
    }

    fun logoutTrakt() {
        viewModelScope.launch {
            authRepository.logoutTrakt()
        }
    }

    fun installTraktAddon() {
        viewModelScope.launch {
            authRepository.installTraktAddon()
        }
    }

    private fun restoreCoreSession() {
        if (authRepository.isAuthenticated()) {
            isRestoringSession.value = false
            return
        }
        val authKey = authRepository.getSavedAuthKey()
        if (!authKey.isNullOrBlank() && authKey != "mock_auth_key") {
            isRestoringSession.value = true
            viewModelScope.launch {
                val result = withTimeoutOrNull(5000) {
                    runCatching { authRepository.loginWithToken(authKey) }
                }
                if (result == null || result.isFailure) {
                    isRestoringSession.value = false
                }
            }
        } else {
            isRestoringSession.value = false
        }
    }

    private fun observeBoard() {
        viewModelScope.launch {
            boardRepository.getBoardFlow()
                .map { board -> board to boardRepository.extractBoardShelves(board) }
                .flowOn(Dispatchers.Default)
                .collect { (board, shelves) ->
                    cachedBoard = board
                    boardShelves.value = shelves
                    if (board.catalogs.isNotEmpty()) {
                        val toPreload = board.catalogs.take(5)
                            .flatMap { catalog -> catalog.pages.filter { it.content == null } }
                            .map { it.request.toString() }
                            .filter { it !in requestedRequests }
                        if (toPreload.isNotEmpty()) {
                            requestedRequests.addAll(toPreload)
                            boardRepository.loadBoardRange(0, minOf(board.catalogs.size - 1, 4))
                        }
                    }
                }
        }
    }

    fun onShelfVisible(shelfIndex: Int) {
        val board = cachedBoard ?: return
        var currentShelfIdx = 0
        var foundCatalogIdx = -1
        for (cIdx in board.catalogs.indices) {
            val catalog = board.catalogs[cIdx]
            val pageCount = catalog.pages.size
            if (shelfIndex >= currentShelfIdx && shelfIndex < currentShelfIdx + pageCount) {
                foundCatalogIdx = cIdx
                break
            }
            currentShelfIdx += pageCount
        }
        if (foundCatalogIdx != -1) {
            val start = foundCatalogIdx
            val end = minOf(foundCatalogIdx + 2, board.catalogs.size - 1)
            var needLoad = false
            for (i in start..end) {
                if (i in board.catalogs.indices) {
                    val catalog = board.catalogs[i]
                    for (page in catalog.pages) {
                        if (page.content == null) {
                            val reqStr = page.request.toString()
                            if (!requestedRequests.contains(reqStr)) {
                                requestedRequests.add(reqStr)
                                needLoad = true
                            }
                        }
                    }
                }
            }
            if (needLoad) {
                android.util.Log.d("MainViewModel", "onShelfVisible($shelfIndex): catalogIndex=$foundCatalogIdx. Loading range $start..$end")
                boardRepository.loadBoardRange(start, end)
            }
        }
    }

    private fun observeContinueWatching() {
        viewModelScope.launch {
            boardRepository.getContinueWatchingFlow().collect { cwShelf ->
                continueWatching.value = cwShelf
            }
        }
    }

    private fun observeDiscover() {
        viewModelScope.launch {
            catalogRepository.getDiscoverFlow().collect { discover ->
                discoverCatalogWithFilters.value = discover
                var req = discoverCatalogRequest.value
                if (req == null) {
                    val movieType = discover.selectable.types.find { it.type.lowercase() == "movie" }
                    if (movieType != null) {
                        req = movieType.request
                    } else {
                        req = discover.selected?.request
                    }
                    if (req == null && discover.selectable.types.isNotEmpty()) {
                        req = discover.selectable.types.first().request
                    }
                    if (req != null) {
                        discoverCatalogRequest.value = req
                        discoverCatalogTitle.value = "Discover"
                        catalogRepository.loadDiscover(req)
                    }
                }

                val currentReq = discoverCatalogRequest.value
                if (currentReq != null && (discover.selected?.request == currentReq)) {
                    val items = catalogRepository.extractDiscoverItems(discover)
                    val isLoading = discover.catalog.pages.any { it.content is com.stremio.core.models.LoadablePage.Content.Loading || it.content == null }
                    discoverCatalog.value = CatalogShelf(
                        title = discoverCatalogTitle.value ?: "Discover",
                        items = items,
                        isLoading = isLoading,
                        seeAllRequest = currentReq
                    )
                }
            }
        }
    }

    private fun observeLibrary() {
        viewModelScope.launch {
            catalogRepository.getLibraryFlow().collect { libraryWithFiltersVal ->
                libraryWithFilters.value = libraryWithFiltersVal
            }
        }
        viewModelScope.launch {
            catalogRepository.getLibraryShelfFlow().collect { shelf ->
                library.value = shelf
            }
        }
    }

    fun openDiscoverCatalog(request: com.stremio.core.types.addon.ResourceRequest, title: String) {
        isDiscoverSeeAll.value = true
        discoverCatalogRequest.value = request
        discoverCatalogTitle.value = title
        discoverCatalog.value = CatalogShelf(title = title, isLoading = true, seeAllRequest = request)
        selectedSection.value = MainSection.Discover
        catalogRepository.loadDiscover(request)
    }

    fun closeDiscoverCatalog() {
        isDiscoverSeeAll.value = false
        discoverCatalogRequest.value = null
        discoverCatalogTitle.value = null
        discoverCatalog.value = CatalogShelf(title = "Discover", isLoading = false)
        selectedSection.value = MainSection.Home
    }

    fun openStreams(item: CatalogItem) {
        streamsJob?.cancel()
        val isSeries = item.type == "series"
        streams.value = StreamsUiState(forItem = item, isOpen = true, isLoading = true, isSeries = isSeries)

        streamsJob = viewModelScope.launch {
            runCatching { serverController.start() }

            if (isSeries) {
                val collector = launch {
                    catalogRepository.getMetaDetailsFlow(type = item.type, id = item.id, videoId = null, guessStreamPath = false)
                        .collect { details ->
                            val videos = catalogRepository.extractVideos(details)
                            if (videos.isNotEmpty()) {
                                val seasons = videos.mapNotNull { it.seriesInfo?.season?.toInt() }.distinct().sorted()
                                val defaultSeason = seasons.firstOrNull()
                                val episodes = videos.map { video ->
                                    EpisodeOption(
                                        videoId = video.id,
                                        season = video.seriesInfo?.season?.toInt() ?: 0,
                                        episode = video.seriesInfo?.episode?.toInt() ?: 0,
                                        title = video.title,
                                        thumbnail = video.thumbnail,
                                        watched = video.watched,
                                        isCurrent = video.currentVideo,
                                    )
                                }
                                if (streams.value.isOpen) {
                                    streams.value = streams.value.copy(
                                        isLoading = false,
                                        episodes = episodes,
                                        seasons = seasons,
                                        selectedSeason = defaultSeason,
                                    )
                                }
                            }
                        }
                }
                withTimeoutOrNull(15_000) { collector.join() }
                if (streams.value.isOpen && streams.value.isLoading) {
                    streams.value = streams.value.copy(
                        isLoading = false,
                        error = if (streams.value.episodes.isEmpty()) "No episodes found." else null,
                    )
                }
            } else {
                val collector = launch {
                    catalogRepository.getMetaDetailsFlow(type = item.type, id = item.id, videoId = null, guessStreamPath = true)
                        .collect { details ->
                            val options = catalogRepository.extractStreams(details).mapIndexed { index, coreStream ->
                                buildStreamOption(index, coreStream)
                            }
                            if (streams.value.isOpen) {
                                streams.value = streams.value.copy(
                                    streams = options,
                                    isLoading = options.isEmpty(),
                                )
                            }
                        }
                }
                withTimeoutOrNull(30_000) { collector.join() }
                if (streams.value.isOpen && streams.value.isLoading) {
                    streams.value = streams.value.copy(
                        isLoading = false,
                        error = if (streams.value.streams.isEmpty()) "No streams found. Install a stream addon to watch." else null,
                    )
                }
            }
        }
    }

    fun selectEpisode(episode: EpisodeOption) {
        val item = streams.value.forItem ?: return
        streamsJob?.cancel()
        streams.value = streams.value.copy(
            selectedVideoId = episode.videoId,
            selectedEpisodeLabel = "S${episode.season}E${episode.episode} · ${episode.title}",
            isLoading = true,
            streams = emptyList(),
            error = null,
        )
        streamsJob = viewModelScope.launch {
            runCatching { serverController.start() }
            val collector = launch {
                catalogRepository.getMetaDetailsFlow(type = item.type, id = item.id, videoId = episode.videoId, guessStreamPath = false)
                    .collect { details ->
                        val options = catalogRepository.extractStreams(details).mapIndexed { index, coreStream ->
                            buildStreamOption(index, coreStream)
                        }
                        if (streams.value.isOpen) {
                            streams.value = streams.value.copy(
                                streams = options,
                                isLoading = options.isEmpty(),
                            )
                        }
                    }
            }
            withTimeoutOrNull(30_000) { collector.join() }
            if (streams.value.isOpen && streams.value.isLoading) {
                streams.value = streams.value.copy(
                    isLoading = false,
                    error = if (streams.value.streams.isEmpty()) "No streams found. Install a stream addon to watch." else null,
                )
            }
        }
    }

    fun selectSeason(season: Int) {
        streams.value = streams.value.copy(selectedSeason = season)
    }

    fun backToEpisodes() {
        streams.value = streams.value.copy(
            selectedVideoId = null,
            selectedEpisodeLabel = null,
            streams = emptyList(),
            error = null,
        )
    }

    private fun buildStreamOption(index: Int, coreStream: CoreStream): StreamOption {
        val rawDescription = coreStream.stream.description?.takeIf { it.isNotBlank() }
            ?: coreStream.stream.thumbnail
        val parsed = parseStreamDescription(rawDescription)
        val quality = coreStream.stream.name?.let { name ->
            val resolutions = listOf("2160p", "4k", "1080p", "720p", "480p")
            resolutions.firstOrNull { name.contains(it, ignoreCase = true) }
        }
        return StreamOption(
            key = "$index-${coreStream.addonTitle}-${coreStream.stream.name ?: ""}-${rawDescription ?: ""}",
            name = coreStream.stream.name?.takeIf { it.isNotBlank() } ?: coreStream.addonTitle,
            description = rawDescription,
            addonTitle = coreStream.addonTitle,
            quality = quality,
            core = coreStream,
            seeds = parsed.seeds,
            size = parsed.size,
            origin = parsed.origin,
            cleanDescription = parsed.cleanDescription,
        )
    }

    fun closeStreams() {
        streamsJob?.cancel()
        streamsJob = null
        streams.value = StreamsUiState()
    }

    fun playStream(option: StreamOption) {
        playJob?.cancel()
        streams.value = streams.value.copy(isResolving = true, error = null)
        playJob = viewModelScope.launch {
            runCatching { serverController.start() }
            val loaded = playbackRepository.resolveAndLoadStream(option)
            if (!loaded) {
                streams.value = streams.value.copy(isResolving = false, error = "Could not resolve a playable stream.")
                return@launch
            }
            streams.value = streams.value.copy(isResolving = false, isOpen = false)
            playerOpen.value = true
        }
    }

    fun closePlayer() {
        playJob?.cancel()
        playJob = null
        playbackRepository.release()
        playerOpen.value = false
    }

    fun attachPlayerView(view: android.view.View) = playbackRepository.attachView(view)

    fun detachPlayerView() = playbackRepository.detachView()

    fun getSubtitlePrefs(): Pair<Int, Int> = playbackRepository.getSubtitlePrefs()

    fun updateSubtitlePrefs(sizePercent: Int, offsetPercent: Int) {
        playbackRepository.updateSubtitlePrefs(sizePercent, offsetPercent)
    }

    fun getPlayer(): com.stremio.mobile.player.Player? = playbackRepository.getPlayer()

    fun acceptIntent(intent: Intent?) {
        latestIntentUri.value = intent?.dataString
    }

    fun startServer() {
        viewModelScope.launch {
            serverController.start()
        }
    }

    fun stopServer() {
        viewModelScope.launch {
            serverController.stop()
        }
    }

    fun checkServerWorking() {
        viewModelScope.launch {
            isPingLoading.value = true
            serverPingStatus.value = "Checking..."
            val currentState = serverController.state.value
            if (currentState is StreamingServerState.Ready) {
                val url = "${currentState.baseUrl}/heartbeat"
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val connection = URL(url).openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 3000
                        connection.readTimeout = 3000
                        val responseCode = connection.responseCode
                        if (responseCode == 200) {
                            val text = connection.inputStream.bufferedReader().use { it.readText() }
                            text.contains("success")
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        false
                    }
                }
                if (ok) {
                    serverPingStatus.value = "Online"
                } else {
                    serverPingStatus.value = "Offline (no response)"
                }
            } else {
                serverPingStatus.value = "Server is not started"
            }
            isPingLoading.value = false
        }
    }

    fun refreshCatalogs() {
        requestedRequests.clear()
        boardRepository.loadBoard()
    }

    fun selectSection(section: MainSection) {
        selectedSection.value = section
        isDiscoverSeeAll.value = false
        if (section == MainSection.Settings) {
            fetchStreamingServerSettingsDirectly()
        }
        if ((section == MainSection.Addons || section == MainSection.Settings) &&
            addons.value.official.isEmpty() &&
            addons.value.community.isEmpty()
        ) {
            refreshAddons()
        }
        if (section == MainSection.Library) {
            refreshLibrary()
        }
        if (section == MainSection.Discover) {
            val discover = catalogRepository.getDiscover()
            var req = discoverCatalogRequest.value
            if (req == null) {
                val movieType = discover.selectable.types.find { it.type.lowercase() == "movie" }
                if (movieType != null) {
                    req = movieType.request
                } else {
                    req = discover.selected?.request
                }
                if (req == null && discover.selectable.types.isNotEmpty()) {
                    req = discover.selectable.types.first().request
                }
            }
            if (req != null) {
                discoverCatalogRequest.value = req
                discoverCatalogTitle.value = "Discover"
                catalogRepository.loadDiscover(req)
            }
        }
    }

    fun search(query: String) {
        searchQuery.value = query
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            searchResults.value = CatalogShelf(title = "Search", isLoading = false)
            return
        }
        searchResults.value = CatalogShelf(title = "Search", isLoading = true)
        searchShelves.value = emptyList()
        searchJob = viewModelScope.launch {
            delay(300)
            val collector = launch {
                catalogRepository.search(trimmed)
                    .map { board -> board to boardRepository.extractBoardShelves(board).filter { it.items.isNotEmpty() } }
                    .flowOn(Dispatchers.Default)
                    .collect { (board, shelves) ->
                        val toPreload = board.catalogs
                            .flatMap { catalog -> catalog.pages.filter { it.content == null } }
                            .map { it.request.toString() }
                            .filter { it !in requestedRequests }
                        if (board.catalogs.isNotEmpty() && toPreload.isNotEmpty()) {
                            requestedRequests.addAll(toPreload)
                            catalogRepository.loadSearchRange(0, minOf(board.catalogs.size - 1, 14))
                        }
                        searchShelves.value = shelves
                        val items = shelves.flatMap { it.items }.distinctBy { "${it.type}-${it.id}" }
                        searchResults.value = CatalogShelf(
                            title = "Search",
                            items = items,
                            isLoading = false
                        )
                    }
            }
            withTimeoutOrNull(15_000) { collector.join() }
            if (searchResults.value.isLoading) {
                searchResults.value = searchResults.value.copy(
                    isLoading = false,
                    error = if (searchShelves.value.isEmpty()) "No results for \"$trimmed\"." else null,
                )
            }
        }
    }

    fun openSearch() {
        isSearchOpen.value = true
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        isSearchOpen.value = false
        searchQuery.value = ""
        searchResults.value = CatalogShelf(title = "Search", isLoading = false)
        searchShelves.value = emptyList()
    }

    fun openDetails(item: CatalogItem) {
        detailsJob?.cancel()
        selectedDetails.value = MetaDetails(item = item, isLoading = true)
        detailsJob = viewModelScope.launch {
            catalogRepository.getMetaDetailsFlow(type = item.type, id = item.id, videoId = null, guessStreamPath = false)
                .collect { details ->
                    val metaItem = details.metaItem
                    if (metaItem == null) {
                        selectedDetails.value = MetaDetails(item = item, isLoading = true)
                        return@collect
                    }
                    when (val content = metaItem.content) {
                        is com.stremio.core.models.LoadableMetaItem.Content.Loading -> {
                            selectedDetails.value = MetaDetails(item = item, isLoading = true)
                        }
                        is com.stremio.core.models.LoadableMetaItem.Content.Error -> {
                            selectedDetails.value = MetaDetails(
                                item = item,
                                isLoading = false,
                                error = content.value.message,
                            )
                        }
                        is com.stremio.core.models.LoadableMetaItem.Content.Ready -> {
                            val meta = content.value
                            val detailsItem = CatalogItem(
                                id = meta.id,
                                type = meta.type,
                                name = meta.name,
                                poster = meta.poster ?: item.poster,
                                background = meta.background ?: item.background,
                                releaseInfo = meta.releaseInfo ?: item.releaseInfo,
                                imdbRating = item.imdbRating,
                                inCinema = item.inCinema,
                                watched = meta.watched
                            )
                            val genres = meta.links.filter {
                                it.category.equals("genre", ignoreCase = true) || it.category.equals("genres", ignoreCase = true)
                            }.map { it.name }
                            val cast = meta.links.filter {
                                it.category.equals("cast", ignoreCase = true) || it.category.equals("actor", ignoreCase = true)
                            }.map { it.name }.take(8)
                            val director = meta.links.filter {
                                it.category.equals("director", ignoreCase = true) || it.category.equals("directors", ignoreCase = true)
                            }.map { it.name }.take(4)
                            val trailer = meta.trailerStreams.firstOrNull()?.let { catalogRepository.directUrl(it) }

                            selectedDetails.value = MetaDetails(
                                item = detailsItem,
                                description = meta.description,
                                genres = genres,
                                cast = cast,
                                director = director,
                                runtime = meta.runtime,
                                year = meta.releaseInfo ?: detailsItem.releaseInfo,
                                trailer = trailer,
                                isLoading = false,
                            )
                        }
                        else -> {
                            selectedDetails.value = MetaDetails(item = item, isLoading = true)
                        }
                    }
                }
        }
    }

    fun closeDetails() {
        detailsJob?.cancel()
        detailsJob = null
        selectedDetails.value = null
    }

    fun toggleLibrary(item: CatalogItem) {
        val inLibrary = library.value.items.any { it.id == item.id && it.type == item.type }
        if (inLibrary) {
            catalogRepository.removeFromLibrary(item.id)
        } else {
            catalogRepository.addToLibrary(item)
        }
    }

    fun refreshAddons() {
        addons.value = addons.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            addons.value = try {
                val official = withContext(Dispatchers.IO) {
                    catalogRepository.fetchAddons("https://v3-cinemeta.strem.io/addon_catalog/all/official.json")
                }
                val community = withContext(Dispatchers.IO) {
                    catalogRepository.fetchAddons("https://v3-cinemeta.strem.io/addon_catalog/all/community.json")
                }
                AddonsUiState(
                    official = official.take(20),
                    community = community.take(40),
                    isLoading = false,
                )
            } catch (error: Exception) {
                AddonsUiState(
                    isLoading = false,
                    error = error.message ?: "Unable to load addons",
                )
            }
        }
    }

    fun login(email: String, password: String) {
        authInFlight = true
        account.value = account.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { authRepository.login(email, password) }.onFailure {
                authInFlight = false
                account.value = account.value.copy(isLoading = false, error = it.message ?: "Login failed")
            }
        }
    }

    fun signup(email: String, password: String, marketingConsent: Boolean) {
        authInFlight = true
        account.value = account.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { authRepository.register(email, password, marketingConsent) }.onFailure {
                authInFlight = false
                account.value = account.value.copy(isLoading = false, error = it.message ?: "Sign up failed")
            }
        }
    }

    fun logout() {
        viewModelScope.launch { runCatching { authRepository.logout() } }
        authRepository.clearSavedSession()
        account.value = AccountUiState()
        refreshLibrary()
    }

    fun clearAccountError() {
        account.value = account.value.copy(error = null)
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            try {
                catalogRepository.syncLibrary()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to sync library", e)
            }
        }
    }

    fun selectLibraryFilter(request: com.stremio.core.models.LibraryWithFilters.LibraryRequest) {
        viewModelScope.launch {
            try {
                catalogRepository.loadLibrary(request)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to load library request", e)
            }
        }
    }

    fun selectDiscoverFilter(request: com.stremio.core.types.addon.ResourceRequest) {
        discoverCatalogRequest.value = request
        discoverCatalog.value = discoverCatalog.value.copy(isLoading = true, seeAllRequest = request)
        viewModelScope.launch {
            try {
                catalogRepository.loadDiscover(request)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to load discover request", e)
            }
        }
    }
}
