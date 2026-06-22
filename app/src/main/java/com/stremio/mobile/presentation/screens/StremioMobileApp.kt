package com.stremio.mobile.presentation.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.stremio.mobile.presentation.components.LocalGlobalUiTheme
import com.stremio.mobile.presentation.components.GlobalUiTheme
import com.stremio.mobile.presentation.components.LocalGlassAlpha
import com.stremio.mobile.core.theme.*
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.LiquidGlassTuning
import com.stremio.mobile.presentation.components.*
import com.stremio.mobile.presentation.navigation.AppView
import com.stremio.mobile.presentation.navigation.toAppView
import com.stremio.mobile.presentation.navigation.toSection
import com.stremio.mobile.presentation.state.MainSection
import com.stremio.mobile.presentation.viewmodel.MainViewModel

@Composable
fun StremioMobileApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val streamsState by viewModel.streamsState.collectAsStateWithLifecycle()
    val isPlayerOpen by viewModel.isPlayerOpen.collectAsStateWithLifecycle()
    val rootGlobalTheme = remember(
        state.globalUiStyle,
        state.glassEffectsMode,
        state.globalGlassAlpha,
        state.glassHapticsEnabled,
        state.hapticsIntensity,
        state.liquidGlassTuning,
        state.adaptiveGlassContrast,
    ) {
        GlobalUiTheme(
            style = state.globalUiStyle,
            glassEffectsMode = state.glassEffectsMode,
            glassAlpha = state.globalGlassAlpha,
            hapticsEnabled = state.glassHapticsEnabled,
            hapticsIntensity = state.hapticsIntensity,
            liquidGlassTuning = state.liquidGlassTuning,
            adaptiveGlassContrast = state.adaptiveGlassContrast,
        )
    }

    StremioMobileTheme {
        CompositionLocalProvider(
            LocalGlobalUiTheme provides rootGlobalTheme,
            LocalGlassAlpha provides state.globalGlassAlpha,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(StremioBackgroundBrush),
            ) {
            if (state.account.isAuthenticated) {
                BoardScreen(
                    state = state,
                    onRefresh = viewModel::refreshCatalogs,
                    onOpenSearch = viewModel::openSearch,
                    onSelectSection = viewModel::selectSection,
                    onOpenDetails = viewModel::openDetails,
                    onCloseDetails = viewModel::closeDetails,
                    onToggleLibrary = viewModel::toggleLibrary,
                    onOpenStreams = viewModel::openStreams,
                    onStartServer = viewModel::startServer,
                    onStopServer = viewModel::stopServer,
                    onCheckServer = viewModel::checkServerWorking,
                    onLogout = viewModel::logout,
                    onCloseDiscoverCatalog = viewModel::closeDiscoverCatalog,
                    onOpenDiscoverCatalog = viewModel::openDiscoverCatalog,
                    onShelfVisible = viewModel::onShelfVisible,
                    onSelectLibraryFilter = viewModel::selectLibraryFilter,
                    onSelectDiscoverFilter = viewModel::selectDiscoverFilter,
                    onUpdateProfileSettings = viewModel::updateProfileSettings,
                    onUpdateServerSettings = viewModel::updateStreamingServerSettings,
                    onAuthenticateTrakt = viewModel::authenticateTrakt,
                    onLogoutTrakt = viewModel::logoutTrakt,
                    onInstallTraktAddon = viewModel::installTraktAddon,
                    onSetAutoStartOnBoot = viewModel::setAutoStartOnBoot,
                    onSetServerInForeground = viewModel::setServerInForeground,
                    onSetMobileDataWarning = viewModel::setMobileDataWarning,
                    onSetKeepScreenOn = viewModel::setKeepScreenOn,
                    onSetSeedingEnabled = viewModel::setSeedingEnabled,
                    onSelectAddonsFilter = viewModel::selectAddonsFilter,
                    onOpenAddonDetails = viewModel::openAddonDetails,
                    onInstallAddon = viewModel::installAddon,
                    onUninstallAddon = viewModel::uninstallAddon,
                    onInstallAddonByUrl = viewModel::installAddonByUrl,
                    onSetMinSeedsThreshold = viewModel::setMinSeedsThreshold,
                    onSetMinDownloadSpeedBps = viewModel::setMinDownloadSpeedBps,
                    onSetPreferredQuality = viewModel::setPreferredQuality,
                    onSetGlobalUiStyle = viewModel::setGlobalUiStyle,
                    onSetGlassEffectsMode = viewModel::setGlassEffectsMode,
                    onSetAutoSwitchOnDeadStream = viewModel::setAutoSwitchOnDeadStream,
                    onSetGlobalGlassAlpha = viewModel::setGlobalGlassAlpha,
                    onSetAdaptiveGlassContrastEnabled = viewModel::setAdaptiveGlassContrastEnabled,
                    onSetGlassHapticsEnabled = viewModel::setGlassHapticsEnabled,
                    onSetHapticsIntensity = viewModel::setHapticsIntensity,
                    onSetLiquidGlassTuning = viewModel::setLiquidGlassTuning,
                    onResetLiquidGlassTuning = viewModel::resetLiquidGlassTuning,
                )
            } else {
                AuthFlow(
                    isLoading = state.account.isLoading,
                    error = state.account.error,
                    onLogin = viewModel::login,
                    onFacebookLogin = viewModel::loginWithFacebook,
                    onFacebookLoginError = viewModel::setAccountError,
                    onSignup = viewModel::signup,
                    onClearError = viewModel::clearAccountError,
                )
            }

            if (state.account.isAuthenticated && state.isSearchOpen) {
                SearchResultsScreen(
                    query = state.searchQuery,
                    results = state.searchResults,
                    shelves = state.searchShelves,
                    onQueryChange = viewModel::search,
                    onOpenDetails = viewModel::openDetails,
                    onOpenDiscoverCatalog = viewModel::openDiscoverCatalog,
                    onBack = viewModel::clearSearch,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            state.selectedDetails?.let { details ->
                BackHandler(onBack = viewModel::closeDetails)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { viewModel.closeDetails() }
                ) {
                    DetailSheet(
                        details = details,
                        inLibrary = state.library.items.any { it.id == details.item.id && it.type == details.item.type },
                        onBack = viewModel::closeDetails,
                        onToggleLibrary = { viewModel.toggleLibrary(details.item) },
                        onOpenStreams = { viewModel.openStreams(details.item) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {} // Block click propagation
                    )
                }
            }

            state.selectedAddonDetails?.let { details ->
                BackHandler(onBack = viewModel::closeAddonDetails)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { viewModel.closeAddonDetails() }
                ) {
                    AddonDetailsSheet(
                        details = details,
                        onBack = viewModel::closeAddonDetails,
                        onInstall = viewModel::installAddon,
                        onUninstall = viewModel::uninstallAddon,
                        onUpgrade = viewModel::upgradeAddon,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {} // Block click propagation
                    )
                }
            }

            if (streamsState.isOpen) {
                StreamsSheet(
                    state = streamsState,
                    preferredQuality = state.preferredQuality,
                    onBack = {
                        if (streamsState.isSeries && streamsState.selectedVideoId != null) {
                            viewModel.backToEpisodes()
                        } else {
                            viewModel.closeStreams()
                        }
                    },
                    onSelect = viewModel::playStream,
                    onSelectEpisode = viewModel::selectEpisode,
                    onSelectSeason = viewModel::selectSeason,
                    onSelectProvider = viewModel::selectStreamProvider,
                    onSelectSortCriterion = viewModel::selectStreamSortCriterion,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (isPlayerOpen) {
                val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
                PlayerScreen(
                    player = viewModel.getPlayer(),
                    activeUri = playbackState.activeUri,
                    title = playbackState.title ?: "Stream",
                    onAttachView = viewModel::attachPlayerView,
                    onDetachView = viewModel::detachPlayerView,
                    onBack = viewModel::closePlayer,
                    getSubtitlePrefs = viewModel::getSubtitlePrefs,
                    onSubtitlePrefsChanged = viewModel::updateSubtitlePrefs,
                    profileSettings = state.profileSettings,
                    getPlayerStreamState = viewModel::getPlayerStreamState,
                    onAudioTrackSelected = viewModel::rememberAudioTrack,
                    onSubtitleTrackSelected = viewModel::rememberSubtitleTrack,
                    onSubtitlesDisabled = viewModel::rememberSubtitlesDisabled,
                    onSubtitleStyleChanged = viewModel::rememberSubtitleStyle,
                    onLocalSubtitlePicked = viewModel::importLocalSubtitle,
                    nextVideo = state.nextVideo,
                    showNextVideoPopup = state.showNextVideoPopup,
                    onDismissNextVideoPopup = viewModel::dismissNextVideoPopup,
                    onPlayNext = { state.nextVideo?.let(viewModel::playNextVideo) },
                    onTick = viewModel::onPlayerTick,
                    onSeekReported = viewModel::onPlayerSeek,
                    onPausedChanged = viewModel::onPlayerPausedChanged,
                    onEnded = viewModel::onPlaybackEnded,
                    showNoSeedsBanner = state.showNoSeedsBanner,
                    noSeedsReason = state.noSeedsReason,
                    onPlayNextBestStream = { viewModel.playNextBestStream() },
                    globalUiStyle = state.globalUiStyle,
                    glassEffectsMode = state.glassEffectsMode,
                    globalGlassAlpha = state.globalGlassAlpha,
                    adaptiveGlassContrast = state.adaptiveGlassContrast,
                    glassHapticsEnabled = state.glassHapticsEnabled,
                    hapticsIntensity = state.hapticsIntensity,
                    liquidGlassTuning = state.liquidGlassTuning,
                )
            }
        }
    }
}
}

@Composable
private fun BoardScreen(
    state: com.stremio.mobile.presentation.state.MainUiState,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onSelectSection: (MainSection) -> Unit,
    onOpenDetails: (CatalogItem) -> Unit,
    onCloseDetails: () -> Unit,
    onToggleLibrary: (CatalogItem) -> Unit,
    onOpenStreams: (CatalogItem) -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onCheckServer: () -> Unit,
    onLogout: () -> Unit,
    onCloseDiscoverCatalog: () -> Unit,
    onOpenDiscoverCatalog: (com.stremio.core.types.addon.ResourceRequest, String) -> Unit,
    onShelfVisible: (Int) -> Unit,
    onSelectLibraryFilter: (com.stremio.core.models.LibraryWithFilters.LibraryRequest) -> Unit,
    onSelectDiscoverFilter: (com.stremio.core.types.addon.ResourceRequest) -> Unit,
    onUpdateProfileSettings: (com.stremio.core.types.profile.Profile.Settings) -> Unit,
    onUpdateServerSettings: (com.stremio.core.models.StreamingServer.Settings) -> Unit,
    onAuthenticateTrakt: (Context) -> Unit,
    onLogoutTrakt: () -> Unit,
    onInstallTraktAddon: () -> Unit,
    onSetAutoStartOnBoot: (Boolean) -> Unit,
    onSetServerInForeground: (Boolean) -> Unit,
    onSetMobileDataWarning: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetSeedingEnabled: (Boolean) -> Unit,
    onSelectAddonsFilter: (com.stremio.core.types.addon.ResourceRequest) -> Unit,
    onOpenAddonDetails: (String) -> Unit,
    onInstallAddon: (com.stremio.mobile.data.model.AddonItem) -> Unit,
    onUninstallAddon: (com.stremio.mobile.data.model.AddonItem) -> Unit,
    onInstallAddonByUrl: (String) -> Boolean,
    onSetMinSeedsThreshold: (Int) -> Unit,
    onSetMinDownloadSpeedBps: (Long) -> Unit,
    onSetPreferredQuality: (String) -> Unit,
    onSetGlobalUiStyle: (String) -> Unit,
    onSetGlassEffectsMode: (String) -> Unit,
    onSetAutoSwitchOnDeadStream: (Boolean) -> Unit,
    onSetGlobalGlassAlpha: (Float) -> Unit,
    onSetAdaptiveGlassContrastEnabled: (Boolean) -> Unit,
    onSetGlassHapticsEnabled: (Boolean) -> Unit,
    onSetHapticsIntensity: (String) -> Unit,
    onSetLiquidGlassTuning: (LiquidGlassTuning) -> Unit,
    onResetLiquidGlassTuning: () -> Unit,
) {
    var settingsSubScreen by rememberSaveable { mutableStateOf(SettingsSubScreen.Main) }

    LaunchedEffect(state.selectedSection) {
        if (state.selectedSection != MainSection.Settings) {
            settingsSubScreen = SettingsSubScreen.Main
        }
    }

    BackHandler(
        enabled = (state.selectedSection == MainSection.Settings && settingsSubScreen != SettingsSubScreen.Main) ||
                (state.selectedSection == MainSection.Discover && state.discoverCatalogRequest != null)
    ) {
        if (state.selectedSection == MainSection.Settings && settingsSubScreen != SettingsSubScreen.Main) {
            settingsSubScreen = SettingsSubScreen.Main
        } else if (state.selectedSection == MainSection.Discover && state.discoverCatalogRequest != null) {
            onCloseDiscoverCatalog()
        }
    }

    val globalTheme = remember(
        state.globalUiStyle,
        state.glassEffectsMode,
        state.globalGlassAlpha,
        state.glassHapticsEnabled,
        state.hapticsIntensity,
        state.liquidGlassTuning,
        state.adaptiveGlassContrast,
    ) {
        GlobalUiTheme(
            style = state.globalUiStyle,
            glassEffectsMode = state.glassEffectsMode,
            glassAlpha = state.globalGlassAlpha,
            hapticsEnabled = state.glassHapticsEnabled,
            hapticsIntensity = state.hapticsIntensity,
            liquidGlassTuning = state.liquidGlassTuning,
            adaptiveGlassContrast = state.adaptiveGlassContrast,
        )
    }

    val realGlassEnabled = state.globalUiStyle == "modern" && state.glassEffectsMode != "static"
    val appBackdrop = if (realGlassEnabled) {
        rememberLayerBackdrop {
            drawContent()
        }
    } else {
        null
    }
    val contentBackdrop = if (realGlassEnabled) {
        rememberLayerBackdrop {
            drawContent()
        }
    } else {
        null
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isAddonsSettingsPage = state.selectedSection == MainSection.Settings &&
            settingsSubScreen == SettingsSubScreen.Addons
    CompositionLocalProvider(
        LocalGlobalUiTheme provides globalTheme,
        LocalGlassAlpha provides state.globalGlassAlpha,
        LocalGlobalBackdrop provides appBackdrop
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (appBackdrop != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(appBackdrop)
                        .background(StremioBackgroundBrush)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (contentBackdrop != null) Modifier.layerBackdrop(contentBackdrop) else Modifier)
                    .windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(bottom = BottomBarSpace + navBottom),
            verticalArrangement = Arrangement.spacedBy(if (isAddonsSettingsPage) 10.dp else 27.dp),
        ) {
            item {
                BoardHeader(
                    onOpenSearch = onOpenSearch,
                )
            }
            when (state.selectedSection) {
                MainSection.Home -> {
                    val heroItem = state.boardShelves.firstOrNull()?.items?.firstOrNull()
                    if (heroItem != null) {
                        item(contentType = "hero") {
                            FeaturedHero(
                                item = heroItem,
                                onClick = { item -> onOpenDetails(item) },
                            )
                        }
                    }
                    if (state.continueWatching.items.isNotEmpty() || state.continueWatching.isLoading) {
                        item(contentType = "shelf") {
                            PosterShelf(
                                shelf = state.continueWatching,
                                mode = ShelfMode.Continue,
                                onItemClick = onOpenDetails
                            )
                        }
                    }
                    state.boardShelves.forEachIndexed { index, shelf ->
                        item(key = shelf.seeAllRequest?.toString() ?: shelf.title, contentType = "shelf") {
                            val shelfKey = shelf.seeAllRequest ?: shelf.title
                            LaunchedEffect(shelfKey) {
                                onShelfVisible(index)
                            }
                            PosterShelf(
                                shelf = shelf,
                                mode = if (shelf.type == "series") ShelfMode.Series else ShelfMode.Movie,
                                onItemClick = onOpenDetails,
                                onSeeAllClick = {
                                    shelf.seeAllRequest?.let { req ->
                                        onOpenDiscoverCatalog(req, shelf.title)
                                    }
                                }
                            )
                        }
                    }
                }

                MainSection.Discover -> {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                        ) {
                            if (state.isDiscoverSeeAll) {
                                ThemedIconButton(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back",
                                    onClick = { onCloseDiscoverCatalog() },
                                    modifier = Modifier
                                        .size(36.dp),
                                    containerColor = GlassSurface,
                                )
                            }
                            Text(
                                text = if (state.isDiscoverSeeAll) state.discoverCatalogTitle ?: "Discover" else "Discover",
                                color = Color.White,
                                fontSize = 22.sp,
                                lineHeight = 27.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                    item {
                        DiscoverFiltersRow(
                            discoverCatalogWithFilters = state.discoverCatalogWithFilters,
                            backdrop = appBackdrop,
                            onSelectRequest = onSelectDiscoverFilter
                        )
                    }
                    if (state.discoverCatalog.isLoading) {
                        item { LoadingRow() }
                    } else if (state.discoverCatalog.error != null) {
                        item { EmptyState(state.discoverCatalog.error) }
                    } else {
                        val items = state.discoverCatalog.items
                        val chunks = items.chunked(3)
                        chunks.forEach { rowItems ->
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            PosterTile(
                                                item = item,
                                                mode = if (item.type == "series") ShelfMode.Series else ShelfMode.Movie,
                                                onClick = { onOpenDetails(item) }
                                            )
                                        }
                                    }
                                    repeat(3 - rowItems.size) {
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                MainSection.Library -> {
                    item { SectionTitle("Library") }
                    item {
                        LibraryFiltersRow(
                            libraryWithFilters = state.libraryWithFilters,
                            backdrop = appBackdrop,
                            onSelectRequest = onSelectLibraryFilter
                        )
                    }
                    val items = state.library.items
                    if (items.isEmpty()) {
                        item { EmptyState("Add movies and series from details to build your library.") }
                    } else {
                        val chunks = items.chunked(3)
                        chunks.forEach { rowItems ->
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            PosterTile(
                                                item = item,
                                                mode = if (item.type == "series") ShelfMode.Series else ShelfMode.Movie,
                                                onClick = { onOpenDetails(item) }
                                            )
                                        }
                                    }
                                    repeat(3 - rowItems.size) {
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                MainSection.Settings -> {
                    when (settingsSubScreen) {
                        SettingsSubScreen.Main -> {
                            item {
                                SettingsPanel(
                                    email = state.account.email,
                                    onLogout = onLogout,
                                    onNavigateTo = { settingsSubScreen = it },
                                )
                            }
                        }
                        SettingsSubScreen.Addons -> {
                            item {
                                AddonsHeaderAndControls(
                                    state = state.addons,
                                    backdrop = appBackdrop,
                                    onBack = { settingsSubScreen = SettingsSubScreen.Main },
                                    onSelectFilter = onSelectAddonsFilter,
                                    onInstallByUrl = onInstallAddonByUrl,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            when {
                                state.addons.isLoading -> item { LoadingRow() }
                                state.addons.error != null -> item { EmptyState(state.addons.error) }
                                state.addons.items.isEmpty() -> item {
                                    EmptyState(
                                        if (state.addons.isBrowsingRemote) "No addons found in this catalog." else "No addons installed."
                                    )
                                }
                                else -> {
                                    items(
                                        items = state.addons.items,
                                        key = { addon -> addon.transportUrl },
                                        contentType = { "addon-row" },
                                    ) { addon ->
                                        AddonRow(
                                            addon = addon,
                                            onClick = { onOpenAddonDetails(addon.transportUrl) },
                                            onInstall = { onInstallAddon(addon) },
                                            onUninstall = { onUninstallAddon(addon) },
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                        )
                                    }
                                }
                            }
                        }
                        SettingsSubScreen.General -> {
                            item {
                                GeneralSettingsScreen(
                                    isAuthenticated = state.account.isAuthenticated,
                                    isTraktAuthenticated = state.isTraktAuthenticated,
                                    onAuthenticateTrakt = onAuthenticateTrakt,
                                    onLogoutTrakt = onLogoutTrakt,
                                    onInstallTraktAddon = onInstallTraktAddon,
                                    onBack = { settingsSubScreen = SettingsSubScreen.Main }
                                )
                            }
                        }
                        SettingsSubScreen.Interface -> {
                            item {
                                InterfaceSettingsScreen(
                                    settings = state.profileSettings,
                                    globalUiStyle = state.globalUiStyle,
                                    glassEffectsMode = state.glassEffectsMode,
                                    globalGlassAlpha = state.globalGlassAlpha,
                                    adaptiveGlassContrast = state.adaptiveGlassContrast,
                                    glassHapticsEnabled = state.glassHapticsEnabled,
                                    hapticsIntensity = state.hapticsIntensity,
                                    onUpdateSettings = onUpdateProfileSettings,
                                    onSetGlobalUiStyle = onSetGlobalUiStyle,
                                    onSetGlassEffectsMode = onSetGlassEffectsMode,
                                    onSetGlobalGlassAlpha = onSetGlobalGlassAlpha,
                                    onSetAdaptiveGlassContrastEnabled = onSetAdaptiveGlassContrastEnabled,
                                    onSetGlassHapticsEnabled = onSetGlassHapticsEnabled,
                                    onSetHapticsIntensity = onSetHapticsIntensity,
                                    onBack = { settingsSubScreen = SettingsSubScreen.Main }
                                )
                            }
                        }
                        SettingsSubScreen.Player -> {
                            item {
                                PlayerSettingsScreen(
                                    settings = state.profileSettings,
                                    onUpdateSettings = onUpdateProfileSettings,
                                    onBack = { settingsSubScreen = SettingsSubScreen.Main }
                                )
                            }
                        }
                        SettingsSubScreen.Streaming -> {
                            item {
                                StreamingSettingsScreen(
                                    serverState = state.server,
                                    serverPingStatus = state.serverPingStatus,
                                    isPingLoading = state.isPingLoading,
                                    serverSettings = state.serverSettings,
                                    isSeedingEnabled = state.isSeedingEnabled,
                                    minSeedsThreshold = state.minSeedsThreshold,
                                    minDownloadSpeedBps = state.minDownloadSpeedBps,
                                    preferredQuality = state.preferredQuality,
                                    isAutoSwitchOnDeadStream = state.isAutoSwitchOnDeadStream,
                                    onStartServer = onStartServer,
                                    onStopServer = onStopServer,
                                    onCheckServer = onCheckServer,
                                    onUpdateServerSettings = onUpdateServerSettings,
                                    onSetSeedingEnabled = onSetSeedingEnabled,
                                    onSetMinSeedsThreshold = onSetMinSeedsThreshold,
                                    onSetMinDownloadSpeedBps = onSetMinDownloadSpeedBps,
                                    onSetPreferredQuality = onSetPreferredQuality,
                                    onSetAutoSwitchOnDeadStream = onSetAutoSwitchOnDeadStream,
                                    onBack = { settingsSubScreen = SettingsSubScreen.Main }
                                )
                            }
                        }
                        SettingsSubScreen.Android -> {
                            item {
                                AndroidSettingsScreen(
                                    isAutoStartOnBoot = state.isAutoStartOnBoot,
                                    onSetAutoStartOnBoot = onSetAutoStartOnBoot,
                                    isServerInForeground = state.isServerInForeground,
                                    onSetServerInForeground = onSetServerInForeground,
                                    isMobileDataWarning = state.isMobileDataWarning,
                                    onSetMobileDataWarning = onSetMobileDataWarning,
                                    isKeepScreenOn = state.isKeepScreenOn,
                                    onSetKeepScreenOn = onSetKeepScreenOn,
                                    onBack = { settingsSubScreen = SettingsSubScreen.Main }
                                )
                            }
                        }
                        SettingsSubScreen.LiquidGlassLab -> {
                            item {
                                LiquidGlassDevScreen(
                                    globalUiStyle = state.globalUiStyle,
                                    glassEffectsMode = state.glassEffectsMode,
                                    globalGlassAlpha = state.globalGlassAlpha,
                                    tuning = state.liquidGlassTuning,
                                    onSetGlobalUiStyle = onSetGlobalUiStyle,
                                    onSetGlassEffectsMode = onSetGlassEffectsMode,
                                    onSetGlobalGlassAlpha = onSetGlobalGlassAlpha,
                                    onSetLiquidGlassTuning = onSetLiquidGlassTuning,
                                    onResetLiquidGlassTuning = onResetLiquidGlassTuning,
                                    onBack = { settingsSubScreen = SettingsSubScreen.Main }
                                )
                            }
                        }
                        SettingsSubScreen.Info -> {
                            item {
                                InfoSettingsScreen(
                                    serverVersion = state.serverVersion,
                                    serverConfigPath = state.serverConfigPath,
                                    serverCachePath = state.serverCachePath,
                                    onBack = { settingsSubScreen = SettingsSubScreen.Main }
                                )
                            }
                        }
                    }
                }
            }
        }

        StremioBottomBar(
            selectedView = state.selectedSection.toAppView(),
            backdrop = contentBackdrop ?: appBackdrop,
            onSelect = { onSelectSection(it.toSection()) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) AccentPurple else GlassSurface
    val textColor = if (selected) Color.White else MutedText
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private fun typeLabel(type: String?): String {
    return when (val t = type?.lowercase()) {
        null -> "All Types"
        "movie" -> "Movies"
        "series" -> "Series"
        "anime" -> "Anime"
        "channel" -> "Channels"
        else -> t.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
    }
}

@Composable
fun DropdownFilter(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val theme = LocalGlobalUiTheme.current
    val useRealGlass = theme.style == "modern" && theme.glassEffectsMode == "full" && backdrop != null
    Box(modifier = modifier) {
        val backgroundColor = GlassSurface
        val textColor = Color.White
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(backgroundColor)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MutedText,
                modifier = Modifier.size(16.dp)
            )
        }

        val menuModifier = if (useRealGlass) {
            Modifier
                .width(180.dp)
                .drawBackdropSafe(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(12.dp) },
                    effects = {
                        vibrancy()
                        blur(16.dp.toPx())
                        lens(
                            refractionHeight = 8.dp.toPx(),
                            refractionAmount = 16.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    },
                    highlight = { Highlight.Ambient.copy(alpha = 0.5f) },
                    shadow = {
                        Shadow(
                            radius = 16.dp,
                            offset = DpOffset(0.dp, 4.dp),
                            color = Color.Black.copy(alpha = 0.3f)
                        )
                    },
                    onDrawSurface = {
                        drawRoundRect(Color(0x85131220))
                    }
                )
        } else {
            Modifier
                .width(180.dp)
                .background(if (theme.style == "modern") Color(0xEE141422) else GlassSurface)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = menuModifier
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (isSelected) Color.White else MutedText,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    },
                    onClick = {
                        onSelectIndex(index)
                        expanded = false
                    },
                    modifier = Modifier.background(if (isSelected) AccentPurple.copy(alpha = 0.15f) else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun LibraryFiltersRow(
    libraryWithFilters: com.stremio.core.models.LibraryWithFilters?,
    backdrop: LayerBackdrop?,
    onSelectRequest: (com.stremio.core.models.LibraryWithFilters.LibraryRequest) -> Unit
) {
    if (libraryWithFilters == null) return

    val selectable = libraryWithFilters.selectable
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Types dropdown
        if (selectable.types.isNotEmpty()) {
            item {
                val options = selectable.types.map { typeLabel(it.type) }
                val selectedIndex = selectable.types.indexOfFirst { it.selected }
                val selectedLabel = if (selectedIndex >= 0) options[selectedIndex] else "All Types"
                DropdownFilter(
                    label = selectedLabel,
                    options = options,
                    selectedIndex = selectedIndex,
                    onSelectIndex = { index -> onSelectRequest(selectable.types[index].request) },
                    backdrop = backdrop
                )
            }
        }

        // Sorts dropdown
        if (selectable.sorts.isNotEmpty()) {
            item {
                val options = selectable.sorts.map { selectableSort ->
                    when (selectableSort.sort) {
                        com.stremio.core.models.LibraryWithFilters.Sort.LAST_WATCHED -> "Last Watched"
                        com.stremio.core.models.LibraryWithFilters.Sort.NAME -> "Name (A-Z)"
                        com.stremio.core.models.LibraryWithFilters.Sort.NAME_REVERSE -> "Name (Z-A)"
                        com.stremio.core.models.LibraryWithFilters.Sort.TIMES_WATCHED -> "Times Watched"
                        com.stremio.core.models.LibraryWithFilters.Sort.WATCHED -> "Watched"
                        com.stremio.core.models.LibraryWithFilters.Sort.NOT_WATCHED -> "Not Watched"
                        else -> selectableSort.sort.name ?: "Unknown"
                    }
                }
                val selectedIndex = selectable.sorts.indexOfFirst { it.selected }
                val selectedLabel = if (selectedIndex >= 0) options[selectedIndex] else "Sort"
                DropdownFilter(
                    label = selectedLabel,
                    options = options,
                    selectedIndex = selectedIndex,
                    onSelectIndex = { index -> onSelectRequest(selectable.sorts[index].request) },
                    backdrop = backdrop
                )
            }
        }
    }
}

@Composable
private fun DiscoverFiltersRow(
    discoverCatalogWithFilters: com.stremio.core.models.CatalogWithFilters?,
    backdrop: LayerBackdrop?,
    onSelectRequest: (com.stremio.core.types.addon.ResourceRequest) -> Unit
) {
    if (discoverCatalogWithFilters == null) return

    val selectable = discoverCatalogWithFilters.selectable
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Types dropdown
        if (selectable.types.isNotEmpty()) {
            item {
                val options = selectable.types.map { typeLabel(it.type) }
                val selectedIndex = selectable.types.indexOfFirst { it.selected }
                val selectedLabel = if (selectedIndex >= 0) options[selectedIndex] else "Type"
                DropdownFilter(
                    label = selectedLabel,
                    options = options,
                    selectedIndex = selectedIndex,
                    onSelectIndex = { index -> onSelectRequest(selectable.types[index].request) },
                    backdrop = backdrop
                )
            }
        }

        // Catalogs dropdown
        if (selectable.catalogs.isNotEmpty()) {
            item {
                val options = selectable.catalogs.map { it.name }
                val selectedIndex = selectable.catalogs.indexOfFirst { it.selected }
                val selectedLabel = if (selectedIndex >= 0) options[selectedIndex] else "Catalog"
                DropdownFilter(
                    label = selectedLabel,
                    options = options,
                    selectedIndex = selectedIndex,
                    onSelectIndex = { index -> onSelectRequest(selectable.catalogs[index].request) },
                    backdrop = backdrop
                )
            }
        }

        // Extra Options dropdowns (usually Genre)
        selectable.extra.forEach { selectableExtra ->
            if (selectableExtra.options.isNotEmpty()) {
                item {
                    val options = selectableExtra.options.map { it.value ?: "All ${selectableExtra.name}s" }
                    val selectedIndex = selectableExtra.options.indexOfFirst { it.selected }
                    val selectedLabel = if (selectedIndex >= 0) options[selectedIndex] else "All ${selectableExtra.name}s"
                    DropdownFilter(
                        label = selectedLabel,
                        options = options,
                        selectedIndex = selectedIndex,
                        onSelectIndex = { index -> onSelectRequest(selectableExtra.options[index].request) },
                        backdrop = backdrop
                    )
                }
            }
        }
    }
}
