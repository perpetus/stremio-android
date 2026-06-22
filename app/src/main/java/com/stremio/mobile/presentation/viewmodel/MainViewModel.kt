package com.stremio.mobile.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
import com.stremio.mobile.player.ExternalSubtitle
import com.stremio.mobile.player.LanguageCatalog
import com.stremio.mobile.player.PlayerEngine
import com.stremio.mobile.player.PlayerSubtitleStyle
import com.stremio.mobile.player.PlayerTrackOption
import com.stremio.mobile.presentation.state.*
import com.stremio.mobile.server.StreamingServerController
import com.stremio.mobile.server.StreamingServerState
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val addonRepository: AddonRepository,
    private val playbackRepository: PlaybackRepository,
    private val serverController: StreamingServerController,
    private val core: StremioCore,
    appContext: Context,
) : ViewModel() {
    private val appContext = appContext.applicationContext
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
    private val selectedAddonDetails = MutableStateFlow<AddonDetailsUiState?>(null)
    private val nextVideo = MutableStateFlow<com.stremio.core.types.resource.Video?>(null)
    private val showNextVideoPopup = MutableStateFlow(false)
    private val showNoSeedsBanner = MutableStateFlow(false)
    private val noSeedsReason = MutableStateFlow<String?>(null)
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
    private val minSeedsThreshold = MutableStateFlow(authRepository.getMinSeedsThreshold())
    private val minDownloadSpeedBps = MutableStateFlow(authRepository.getMinDownloadSpeedBps())
    private val preferredQuality = MutableStateFlow(authRepository.getPreferredQuality())
    private val globalUiStyle = MutableStateFlow(authRepository.getGlobalUiStyle())
    private val glassEffectsMode = MutableStateFlow(authRepository.getGlassEffectsMode())
    private val isAutoSwitchOnDeadStream = MutableStateFlow(authRepository.isAutoSwitchOnDeadStream())
    private val globalGlassAlpha = MutableStateFlow(authRepository.getGlobalGlassAlpha())
    private val adaptiveGlassContrast = MutableStateFlow(authRepository.isAdaptiveGlassContrastEnabled())
    private val glassHapticsEnabled = MutableStateFlow(authRepository.getGlassHapticsEnabled())
    private val hapticsIntensity = MutableStateFlow(authRepository.getHapticsIntensity())
    private val liquidGlassTuning = MutableStateFlow(authRepository.getLiquidGlassTuning())

    private var lastServerActivityMs: Long = System.currentTimeMillis()
    private val IDLE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

    private val serverPingStatus = MutableStateFlow("Not checked")
    private val isPingLoading = MutableStateFlow(false)

    private val configPath = File(this.appContext.filesDir, "stream-server").absolutePath
    private val cachePath = File(this.appContext.cacheDir, "stream-server").absolutePath

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
    private var addonDetailsJob: Job? = null
    private var nextVideoJob: Job? = null
    private var subtitleObserverJob: Job? = null
    private var lastPlayedOption: StreamOption? = null
    private var dismissedNextVideoId: String? = null
    private var lastTimeReportMs = 0L
    private var noSeedsPollJob: Job? = null
    private var unhealthySinceMs: Long? = null

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
            isSearchOpen,
            selectedAddonDetails,
            nextVideo,
            showNextVideoPopup,
            minSeedsThreshold,
            minDownloadSpeedBps,
            preferredQuality,
            globalUiStyle,
            glassEffectsMode,
            isAutoSwitchOnDeadStream,
            showNoSeedsBanner,
            noSeedsReason,
            globalGlassAlpha,
            adaptiveGlassContrast,
            glassHapticsEnabled,
            hapticsIntensity,
            liquidGlassTuning
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
            selectedAddonDetails = values[29] as AddonDetailsUiState?,
            nextVideo = values[30] as com.stremio.core.types.resource.Video?,
            showNextVideoPopup = values[31] as Boolean,
            minSeedsThreshold = values[32] as Int,
            minDownloadSpeedBps = values[33] as Long,
            preferredQuality = values[34] as String,
            globalUiStyle = values[35] as String,
            glassEffectsMode = values[36] as String,
            isAutoSwitchOnDeadStream = values[37] as Boolean,
            showNoSeedsBanner = values[38] as Boolean,
            noSeedsReason = values[39] as String?,
            globalGlassAlpha = values[40] as Float,
            adaptiveGlassContrast = values[41] as Boolean,
            glassHapticsEnabled = values[42] as Boolean,
            hapticsIntensity = values[43] as String,
            liquidGlassTuning = values[44] as LiquidGlassTuning,
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
        observeAddons()
        refreshLibrary()
        observeCoreAuth()
        observeStreamingServer()
        restoreCoreSession()

        viewModelScope.launch {
            while (true) {
                delay(60_000L)
                stopServerIfIdle()
            }
        }
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

    private suspend fun startServerInternal() {
        lastServerActivityMs = System.currentTimeMillis()
        serverController.start()
    }

    private suspend fun stopServerIfIdle() {
        val now = System.currentTimeMillis()
        val isPlayerActive = playerOpen.value
        val seeding = isSeedingEnabled.value
        val isStopped = serverController.state.value is StreamingServerState.Stopped

        if (!isPlayerActive && !seeding && (now - lastServerActivityMs) >= IDLE_TIMEOUT_MS && !isStopped) {
            serverController.stop()
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

    fun setMinSeedsThreshold(value: Int) {
        authRepository.setMinSeedsThreshold(value)
        minSeedsThreshold.value = value
    }

    fun setMinDownloadSpeedBps(value: Long) {
        authRepository.setMinDownloadSpeedBps(value)
        minDownloadSpeedBps.value = value
    }

    fun setPreferredQuality(value: String) {
        authRepository.setPreferredQuality(value)
        preferredQuality.value = value
    }

    fun setGlobalUiStyle(value: String) {
        authRepository.setGlobalUiStyle(value)
        globalUiStyle.value = if (value == "modern") "modern" else "classic"
    }

    fun setGlassEffectsMode(value: String) {
        val resolved = when (value) {
            "full" -> "full"
            "static" -> "static"
            else -> "balanced"
        }
        authRepository.setGlassEffectsMode(resolved)
        glassEffectsMode.value = resolved
    }

    fun setGlobalGlassAlpha(value: Float) {
        authRepository.setGlobalGlassAlpha(value)
        globalGlassAlpha.value = value
    }

    fun setAdaptiveGlassContrastEnabled(enabled: Boolean) {
        authRepository.setAdaptiveGlassContrastEnabled(enabled)
        adaptiveGlassContrast.value = enabled
    }

    fun setGlassHapticsEnabled(enabled: Boolean) {
        authRepository.setGlassHapticsEnabled(enabled)
        glassHapticsEnabled.value = enabled
    }

    fun setHapticsIntensity(value: String) {
        authRepository.setHapticsIntensity(value)
        hapticsIntensity.value = value
    }

    fun setLiquidGlassTuning(value: LiquidGlassTuning) {
        val tuning = value.clamped()
        authRepository.setLiquidGlassTuning(tuning)
        liquidGlassTuning.value = tuning
    }

    fun resetLiquidGlassTuning() {
        setLiquidGlassTuning(LiquidGlassTuning())
    }

    fun setAutoSwitchOnDeadStream(enabled: Boolean) {
        authRepository.setAutoSwitchOnDeadStream(enabled)
        isAutoSwitchOnDeadStream.value = enabled
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
        val rememberedSelection = localContinueWatchingStreamSelection(item)
        if (rememberedSelection != null) {
            openRememberedContinueWatchingStream(item, rememberedSelection)
            return
        }

        streamsJob = launchStreamsMenuJob(item)
    }

    private fun openRememberedContinueWatchingStream(item: CatalogItem, rememberedSelection: LocalStreamSelection) {
        val isSeries = item.type == "series"
        streams.value = StreamsUiState(
            forItem = item,
            isOpen = false,
            isLoading = true,
            isSeries = isSeries,
            selectedVideoId = if (isSeries) item.continueWatchingVideoId else null,
        )

        streamsJob = viewModelScope.launch {
            runCatching { startServerInternal() }

            val videoId = if (isSeries) item.continueWatchingVideoId else null
            val matchedOption = runCatching {
                withTimeoutOrNull(30_000) {
                    catalogRepository.getMetaDetailsFlow(
                        type = item.type,
                        id = item.id,
                        videoId = videoId,
                        guessStreamPath = !isSeries,
                    )
                        .map { details ->
                            if (isSeries && videoId != null) {
                                episodeDisplayInfo(details, videoId)?.let { episode ->
                                    streams.value = streams.value.copy(
                                        selectedEpisodeLabel = episode.label,
                                        releaseDateLabel = episode.releaseDate,
                                    )
                                }
                            }
                            catalogRepository.extractStreams(details)
                                .mapIndexed { index, coreStream -> buildStreamOption(index, coreStream) }
                                .firstOrNull { rememberedSelection.matches(it) }
                        }
                        .first { it != null }
                }
            }.getOrNull()

            if (matchedOption != null) {
                playStream(matchedOption)
            } else {
                streams.value = StreamsUiState()
                streamsJob = launchStreamsMenuJob(item)
            }
        }
    }

    private fun localContinueWatchingStreamSelection(item: CatalogItem): LocalStreamSelection? {
        if (!item.isContinueWatching) return null
        val videoId = continueWatchingVideoKey(item)
        return authRepository.getLocalStreamSelection(item.type, item.id, videoId)
    }

    private fun continueWatchingVideoKey(item: CatalogItem): String {
        return item.continueWatchingVideoId?.takeIf { it.isNotBlank() } ?: item.id
    }

    private fun launchStreamsMenuJob(item: CatalogItem): Job {
        val isSeries = item.type == "series"
        streams.value = StreamsUiState(forItem = item, isOpen = true, isLoading = true, isSeries = isSeries)

        return viewModelScope.launch {
            runCatching { startServerInternal() }

            if (isSeries) {
                val collector = launch {
                    catalogRepository.getMetaDetailsFlow(type = item.type, id = item.id, videoId = null, guessStreamPath = false)
                        .collect { details ->
                            val videos = catalogRepository.extractVideos(details)
                            if (videos.isNotEmpty()) {
                                val seasons = videos.mapNotNull { it.seriesInfo?.season?.toInt() }.distinct().sorted()
                                val defaultSeason = defaultSeasonForVideos(videos) ?: seasons.firstOrNull()
                                val episodes = videos.map { video ->
                                    EpisodeOption(
                                        videoId = video.id,
                                        season = video.seriesInfo?.season?.toInt() ?: 0,
                                        episode = video.seriesInfo?.episode?.toInt() ?: 0,
                                        title = video.title,
                                        thumbnail = video.thumbnail,
                                        releaseDate = formatReleaseDate(video.released),
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
                                    releaseDateLabel = movieReleaseDate(details),
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
            selectedEpisodeLabel = episodeDisplayLabel(episode.season, episode.episode, episode.title),
            releaseDateLabel = episode.releaseDate,
            isLoading = true,
            streams = emptyList(),
            error = null,
        )
        streamsJob = viewModelScope.launch {
            runCatching { startServerInternal() }
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

    fun selectStreamProvider(provider: String?) {
        streams.value = streams.value.copy(selectedProvider = provider)
    }

    fun selectStreamSortCriterion(criterion: StreamSortCriterion) {
        streams.value = streams.value.copy(sortCriterion = criterion)
    }

    fun backToEpisodes() {
        streams.value = streams.value.copy(
            selectedVideoId = null,
            selectedEpisodeLabel = null,
            releaseDateLabel = null,
            streams = emptyList(),
            error = null,
        )
    }

    private fun defaultSeasonForVideos(videos: List<com.stremio.core.types.resource.Video>): Int? {
        val currentVideo = videos.firstOrNull { it.currentVideo }
        val lastProgressedVideo = videos
            .filter { it.watched || (it.progress ?: 0.0) > 0.0 }
            .maxWithOrNull(
                compareBy<com.stremio.core.types.resource.Video> { it.seriesInfo?.season ?: 0L }
                    .thenBy { it.seriesInfo?.episode ?: 0L }
            )
        return (currentVideo ?: lastProgressedVideo)?.seriesInfo?.season?.toInt()
    }

    private fun movieReleaseDate(details: com.stremio.core.models.MetaDetails): String? {
        val content = details.metaItem?.content
        return if (content is com.stremio.core.models.LoadableMetaItem.Content.Ready) {
            formatReleaseDate(content.value.released) ?: content.value.releaseInfo
        } else {
            streams.value.forItem?.releaseInfo
        }
    }

    private data class EpisodeDisplayInfo(
        val label: String,
        val releaseDate: String?,
    )

    private fun episodeDisplayInfo(
        details: com.stremio.core.models.MetaDetails,
        videoId: String,
    ): EpisodeDisplayInfo? {
        return catalogRepository.extractVideos(details)
            .firstOrNull { it.id == videoId }
            ?.let { video ->
                EpisodeDisplayInfo(
                    label = episodeDisplayLabel(
                        season = video.seriesInfo?.season?.toInt(),
                        episode = video.seriesInfo?.episode?.toInt(),
                        title = video.title,
                    ) ?: video.title,
                    releaseDate = formatReleaseDate(video.released),
                )
            }
    }

    private fun episodeDisplayLabel(season: Int?, episode: Int?, title: String?): String? {
        val cleanTitle = title?.takeIf { it.isNotBlank() }
        val number = if ((season ?: 0) > 0 && (episode ?: 0) > 0) {
            "S${season}E${episode}"
        } else {
            null
        }
        return listOfNotNull(cleanTitle, number)
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }
    }

    private fun playbackDisplayTitle(): String? {
        val current = streams.value
        val item = current.forItem ?: return null
        return if (current.isSeries) {
            current.selectedEpisodeLabel?.takeIf { it.isNotBlank() } ?: item.name
        } else {
            item.name
        }
    }

    private fun formatReleaseDate(timestamp: pbandk.wkt.Timestamp?): String? {
        val seconds = timestamp?.seconds ?: return null
        if (seconds <= 0L) return null
        return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date(seconds * 1000L))
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
        subtitleObserverJob?.cancel()
        streams.value = streams.value.copy(isResolving = true, error = null)
        playJob = viewModelScope.launch {
            runCatching { startServerInternal() }
            val engine = PlayerEngine.fromProfileValue(profileSettings.value?.playerType)
            val loaded = playbackRepository.resolveAndLoadStream(
                option = option,
                engine = engine,
                displayTitle = playbackDisplayTitle(),
            )
            if (!loaded) {
                streams.value = streams.value.copy(isResolving = false, error = "Could not resolve a playable stream.")
                return@launch
            }
            rememberLocalStreamSelection(option)
            lastPlayedOption = option
            nextVideo.value = null
            showNextVideoPopup.value = false
            dismissedNextVideoId = null
            lastTimeReportMs = 0L
            showNoSeedsBanner.value = false
            noSeedsReason.value = null
            unhealthySinceMs = null
            streams.value = streams.value.copy(isResolving = false, isOpen = false)
            playerOpen.value = true
            observeAddonSubtitlesForPlayback()
            startHealthWatch(option)
        }
    }

    private fun rememberLocalStreamSelection(option: StreamOption) {
        val currentStreams = streams.value
        val item = currentStreams.forItem ?: return
        val videoId = currentStreams.selectedVideoId
            ?: item.continueWatchingVideoId
            ?: item.id
        authRepository.rememberLocalStreamSelection(item.type, item.id, videoId, option)
    }

    fun closePlayer() {
        playJob?.cancel()
        playJob = null
        nextVideoJob?.cancel()
        nextVideoJob = null
        subtitleObserverJob?.cancel()
        subtitleObserverJob = null
        noSeedsPollJob?.cancel()
        noSeedsPollJob = null
        playbackRepository.release()
        playerOpen.value = false
        nextVideo.value = null
        showNextVideoPopup.value = false
        showNoSeedsBanner.value = false
        noSeedsReason.value = null

        lastServerActivityMs = 0L
        viewModelScope.launch {
            stopServerIfIdle()
        }
    }

    /** Polls live torrent health for a torrent stream and surfaces a fallback when it's dead/too slow. */
    private fun startHealthWatch(option: StreamOption) {
        noSeedsPollJob?.cancel()
        val source = option.core.stream.source
        if (source !is com.stremio.core.types.resource.Stream.Source.Tramvai) return
        val infoHash = source.value.infoHash
        val fileIndex = source.value.fileIdx ?: 0

        noSeedsPollJob = viewModelScope.launch {
            while (true) {
                playbackRepository.requestStreamStatistics(infoHash, fileIndex)
                // Local JNI/HTTP roundtrip to the on-device streaming server; comfortably finishes well under this.
                delay(800)
                val stats = playbackRepository.getStreamStatistics()
                if (stats != null) {
                    val tooFewSeeds = stats.peers < minSeedsThreshold.value
                    val tooSlow = minDownloadSpeedBps.value > 0 && stats.downloadSpeed < minDownloadSpeedBps.value
                    val isNearComplete = stats.streamProgress >= 0.95
                    if ((tooFewSeeds || tooSlow) && !isNearComplete) {
                        val since = unhealthySinceMs ?: System.currentTimeMillis().also { unhealthySinceMs = it }
                        if (System.currentTimeMillis() - since >= 20_000) {
                            val reason = if (tooFewSeeds) "No seeds found for this stream." else "This stream is downloading too slowly."
                            if (isAutoSwitchOnDeadStream.value && playNextBestStream()) {
                                return@launch
                            }
                            noSeedsReason.value = reason
                            showNoSeedsBanner.value = true
                        }
                    } else {
                        unhealthySinceMs = null
                        showNoSeedsBanner.value = false
                    }
                }
                delay(2_200)
            }
        }
    }

    /** Switches to the best remaining candidate stream, ranked by seeds then closeness to the preferred quality. Returns false if there was nothing else to try. */
    fun playNextBestStream(): Boolean {
        val current = lastPlayedOption
        val candidates = streams.value.streams.filter { it.key != current?.key }
        val best = candidates.sortedWith(
            compareByDescending<StreamOption> { parseSeedCount(it.seeds) }
                .thenByDescending { qualityScore(it.quality, preferredQuality.value) }
        ).firstOrNull() ?: return false
        showNoSeedsBanner.value = false
        playStream(best)
        return true
    }

    /** Called every ~500ms by [PlayerScreen]'s position tracker. */
    fun onPlayerTick(positionMs: Long, durationMs: Long) {
        lastServerActivityMs = System.currentTimeMillis()
        if (durationMs <= 0) return

        if (positionMs - lastTimeReportMs >= 5_000 || lastTimeReportMs == 0L) {
            lastTimeReportMs = positionMs
            playbackRepository.reportTimeChanged(positionMs, durationMs)
        }

        val next = nextVideo.value ?: playbackRepository.getNextVideo()?.also { nextVideo.value = it }
        if (next == null) {
            showNextVideoPopup.value = false
            return
        }

        val notificationDurationMs = profileSettings.value?.nextVideoNotificationDuration ?: return
        val remainingMs = durationMs - positionMs
        showNextVideoPopup.value = remainingMs in 0..notificationDurationMs && dismissedNextVideoId != next.id
    }

    fun onPlayerSeek(positionMs: Long, durationMs: Long) {
        lastTimeReportMs = positionMs
        playbackRepository.reportSeek(positionMs, durationMs)
    }

    fun onPlayerPausedChanged(paused: Boolean) {
        playbackRepository.reportPausedChanged(paused)
    }

    fun dismissNextVideoPopup() {
        dismissedNextVideoId = nextVideo.value?.id
        showNextVideoPopup.value = false
    }

    /** Called when ExoPlayer reaches the end of the current stream. */
    fun onPlaybackEnded() {
        playbackRepository.reportEnded()
        val next = nextVideo.value
        if (profileSettings.value?.bingeWatching == true && next != null) {
            playNextVideo(next)
        } else {
            closePlayer()
        }
    }

    /** Plays [video] next: re-resolves its streams and auto-picks one, skipping the streams sheet. */
    fun playNextVideo(video: com.stremio.core.types.resource.Video) {
        val item = streams.value.forItem ?: return
        playbackRepository.reportNextVideo()
        showNextVideoPopup.value = false
        streams.value = streams.value.copy(
            selectedVideoId = video.id,
            selectedEpisodeLabel = episodeDisplayLabel(
                season = video.seriesInfo?.season?.toInt(),
                episode = video.seriesInfo?.episode?.toInt(),
                title = video.title,
            ),
            releaseDateLabel = formatReleaseDate(video.released),
        )
        nextVideoJob?.cancel()
        nextVideoJob = viewModelScope.launch {
            runCatching { startServerInternal() }
            val collector = launch {
                catalogRepository.getMetaDetailsFlow(type = item.type, id = item.id, videoId = video.id, guessStreamPath = false)
                    .collect { details ->
                        val options = catalogRepository.extractStreams(details).mapIndexed { index, coreStream ->
                            buildStreamOption(index, coreStream)
                        }
                        if (options.isNotEmpty()) {
                            val preferred = lastPlayedOption?.let { last ->
                                options.firstOrNull { it.addonTitle == last.addonTitle }
                            }
                            playStream(preferred ?: options.first())
                            nextVideoJob?.cancel()
                        }
                    }
            }
            withTimeoutOrNull(30_000) { collector.join() }
        }
    }

    fun attachPlayerView(view: android.view.View) = playbackRepository.attachView(view)

    fun detachPlayerView() = playbackRepository.detachView()

    fun getSubtitlePrefs(): Pair<Int, Int> = playbackRepository.getSubtitlePrefs()

    fun updateSubtitlePrefs(sizePercent: Int, offsetPercent: Int) {
        playbackRepository.updateSubtitlePrefs(sizePercent, offsetPercent)
    }

    fun getPlayerStreamState(): com.stremio.core.models.Player.StreamState? =
        playbackRepository.getPlayerStreamState()

    fun rememberAudioTrack(track: PlayerTrackOption) {
        playbackRepository.rememberAudioTrack(track)
    }

    fun rememberSubtitleTrack(track: PlayerTrackOption) {
        playbackRepository.rememberSubtitleTrack(track)
    }

    fun rememberSubtitlesDisabled() {
        playbackRepository.rememberSubtitlesDisabled()
    }

    fun rememberSubtitleStyle(style: PlayerSubtitleStyle) {
        playbackRepository.rememberSubtitleStyle(style)
    }

    fun importLocalSubtitle(uri: Uri) {
        viewModelScope.launch {
            val track = withContext(Dispatchers.IO) { copyLocalSubtitle(uri) } ?: return@launch
            playbackRepository.addLocalSubtitle(track)
        }
    }

    fun getPlayer(): com.stremio.mobile.player.Player? = playbackRepository.getPlayer()

    fun acceptIntent(intent: Intent?) {
        latestIntentUri.value = intent?.dataString
    }

    fun startServer() {
        viewModelScope.launch {
            startServerInternal()
        }
    }

    fun stopServer() {
        viewModelScope.launch {
            serverController.stop()
        }
    }

    fun onAppForegrounded() {
        // No-op for now, symmetry/future use
    }

    fun onAppBackgrounded() {
        viewModelScope.launch {
            stopServerIfIdle()
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

    private fun observeAddons() {
        viewModelScope.launch {
            addonRepository.getAddonsFlow().collect { raw ->
                addons.value = addonRepository.extractAddonsUiState(raw)
            }
        }
    }

    fun loadInstalledAddons(type: String? = null) {
        addonRepository.loadInstalledAddons(type)
    }

    /** Applies a filter taken from `state.addons.selectableTypes`/`selectableCatalogs`. */
    fun selectAddonsFilter(request: com.stremio.core.types.addon.ResourceRequest) {
        addonRepository.selectFilter(request)
    }

    fun installAddon(item: AddonItem) {
        addonRepository.installAddon(item)
    }

    fun uninstallAddon(item: AddonItem) {
        addonRepository.uninstallAddon(item)
    }

    fun upgradeAddon(item: AddonItem) {
        addonRepository.upgradeAddon(item)
    }

    fun openAddonDetails(transportUrl: String) {
        addonDetailsJob?.cancel()
        selectedAddonDetails.value = AddonDetailsUiState(transportUrl = transportUrl)
        addonDetailsJob = viewModelScope.launch {
            addonRepository.getAddonDetailsFlow(transportUrl).collect { details ->
                addonRepository.extractAddonDetailsUiState(details)?.let { selectedAddonDetails.value = it }
            }
        }
    }

    fun closeAddonDetails() {
        addonDetailsJob?.cancel()
        addonDetailsJob = null
        selectedAddonDetails.value = null
    }

    /** Validates a pasted manifest URL, then opens its details sheet so the user confirms Install there. */
    fun installAddonByUrl(rawUrl: String): Boolean {
        val url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        return runCatching { URL(url) }.onSuccess { openAddonDetails(url) }.isSuccess
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

    private fun observeAddonSubtitlesForPlayback() {
        subtitleObserverJob?.cancel()
        val seen = mutableSetOf<String>()
        subtitleObserverJob = viewModelScope.launch {
            playbackRepository.playerFlow().collect { player ->
                val newTracks = playbackRepository.extractAddonSubtitles(player)
                    .filter { seen.add(it.id) }
                if (newTracks.isNotEmpty()) {
                    playbackRepository.addExternalSubtitleTracks(newTracks)
                }
            }
        }
    }

    private fun copyLocalSubtitle(uri: Uri): ExternalSubtitle? {
        val displayName = queryDisplayName(uri) ?: "subtitle-${System.currentTimeMillis()}.srt"
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val outDir = File(appContext.cacheDir, "local-subtitles").apply { mkdirs() }
        val outFile = File(outDir, "${System.currentTimeMillis()}-$safeName")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return null

        return ExternalSubtitle(
            id = "local:${outFile.name}",
            lang = LanguageCatalog.LOCAL_SUBTITLES_LANGUAGE,
            url = Uri.fromFile(outFile).toString(),
            label = displayName,
            source = "Local",
            origin = "LOCAL",
            embedded = false,
            local = true,
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        return appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
    }
}
