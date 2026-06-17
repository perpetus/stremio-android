package com.stremio.mobile.presentation.state

import com.stremio.mobile.data.model.CatalogShelf
import com.stremio.mobile.data.model.MetaDetails
import com.stremio.mobile.server.StreamingServerState

data class MainUiState(
    val server: StreamingServerState = StreamingServerState.Stopped,
    val serverPingStatus: String = "Not checked",
    val isPingLoading: Boolean = false,
    val serverVersion: String = "0.1.5",
    val serverConfigPath: String = "",
    val serverCachePath: String = "",
    val latestIntentUri: String? = null,
    val account: AccountUiState = AccountUiState(),
    val selectedSection: MainSection = MainSection.Home,
    val searchQuery: String = "",
    val searchResults: CatalogShelf = CatalogShelf(title = "Search", isLoading = false),
    val searchShelves: List<CatalogShelf> = emptyList(),
    val library: CatalogShelf = CatalogShelf(title = "Library", isLoading = false),
    val addons: AddonsUiState = AddonsUiState(),
    val selectedDetails: MetaDetails? = null,
    val continueWatching: CatalogShelf = CatalogShelf(title = "Continue Watching"),
    val boardShelves: List<CatalogShelf> = emptyList(),
    val discoverCatalogRequest: com.stremio.core.types.addon.ResourceRequest? = null,
    val discoverCatalogTitle: String? = null,
    val discoverCatalog: CatalogShelf = CatalogShelf(title = "Discover", isLoading = false),
    val discoverCatalogWithFilters: com.stremio.core.models.CatalogWithFilters? = null,
    val libraryWithFilters: com.stremio.core.models.LibraryWithFilters? = null,
    val isDiscoverSeeAll: Boolean = false,
    val profileSettings: com.stremio.core.types.profile.Profile.Settings? = null,
    val serverSettings: com.stremio.core.models.StreamingServer.Settings? = null,
    val isTraktAuthenticated: Boolean = false,
    val isAutoStartOnBoot: Boolean = false,
    val isServerInForeground: Boolean = true,
    val isMobileDataWarning: Boolean = true,
    val isKeepScreenOn: Boolean = true,
    val isSeedingEnabled: Boolean = true,
    val isSearchOpen: Boolean = false,
)
