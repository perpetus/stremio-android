package com.stremio.mobile.data.repository

import com.stremio.core.models.AddonDetails
import com.stremio.core.models.AddonsWithFilters
import com.stremio.core.models.LoadableAddonCatalog
import com.stremio.core.models.LoadableDescriptor
import com.stremio.core.types.addon.AddonDescriptor
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.mobile.core.StremioCore
import com.stremio.mobile.data.model.AddonItem
import com.stremio.mobile.presentation.state.AddonDetailsUiState
import com.stremio.mobile.presentation.state.AddonSelectableOption
import com.stremio.mobile.presentation.state.AddonsUiState
import kotlinx.coroutines.flow.Flow

class AddonRepository(private val core: StremioCore) {

    fun getAddonsFlow(): Flow<AddonsWithFilters> = core.addons()

    fun getAddonDetailsFlow(transportUrl: String): Flow<AddonDetails> = core.addonDetails(transportUrl)

    fun loadInstalledAddons(type: String? = null) = core.loadInstalledAddons(type)

    /** Applies a [request] taken from `selectableTypes`/`selectableCatalogs` (covers both installed and remote browsing). */
    fun selectFilter(request: ResourceRequest) = core.loadAddons(request)

    fun installAddon(item: AddonItem) = core.installAddon(item.descriptor)

    fun uninstallAddon(item: AddonItem) = core.uninstallAddon(item.descriptor)

    fun upgradeAddon(item: AddonItem) = core.upgradeAddon(item.descriptor)

    fun extractAddonsUiState(addons: AddonsWithFilters): AddonsUiState {
        val isBrowsingRemote = addons.selected?.request?.base?.isNotEmpty() == true
        val catalog = addons.catalog
        val content = catalog?.content
        val items = (content as? LoadableAddonCatalog.Content.Ready)?.value?.items.orEmpty().map { it.toAddonItem() }
        val isLoading = catalog == null || content is LoadableAddonCatalog.Content.Loading
        val error = (content as? LoadableAddonCatalog.Content.Error)?.value?.message

        return AddonsUiState(
            isBrowsingRemote = isBrowsingRemote,
            items = items,
            selectableTypes = addons.selectable.types.map { selectableType ->
                AddonSelectableOption(
                    label = selectableType.type.ifBlank { "All" },
                    selected = selectableType.selected,
                    request = selectableType.request,
                )
            },
            selectableCatalogs = addons.selectable.catalogs.map { selectableCatalog ->
                AddonSelectableOption(
                    label = selectableCatalog.name,
                    selected = selectableCatalog.selected,
                    request = selectableCatalog.request,
                )
            },
            isLoading = isLoading,
            error = error,
        )
    }

    fun extractAddonDetailsUiState(details: AddonDetails): AddonDetailsUiState? {
        val selected = details.selected ?: return null
        val remoteContent = details.remoteAddon?.content
        val isLoading = details.remoteAddon == null || remoteContent is LoadableDescriptor.Content.Loading

        return AddonDetailsUiState(
            transportUrl = selected.transportUrl,
            localAddon = details.localAddon?.toAddonItem(),
            remoteAddon = (remoteContent as? LoadableDescriptor.Content.Ready)?.value?.toAddonItem(),
            isLoading = isLoading,
            error = (remoteContent as? LoadableDescriptor.Content.Error)?.value?.message,
        )
    }
}

private fun AddonDescriptor.toAddonItem(): AddonItem = AddonItem(
    descriptor = this,
    id = manifest.id,
    name = manifest.name,
    description = manifest.description,
    logo = manifest.logo,
    version = manifest.version,
    types = manifest.types,
    transportUrl = transportUrl,
    official = flags.official,
    protected = flags.protected,
    installed = installed,
    installable = installable,
    upgradeable = upgradeable,
    uninstallable = uninstallable,
    configurable = manifest.behaviorHints.configurable,
    configurationRequired = manifest.behaviorHints.configurationRequired,
)
