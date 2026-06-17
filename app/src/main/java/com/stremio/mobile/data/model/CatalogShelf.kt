package com.stremio.mobile.data.model

import com.stremio.core.types.addon.ResourceRequest

data class CatalogShelf(
    val title: String,
    val items: List<CatalogItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val seeAllRequest: ResourceRequest? = null,
    val type: String? = null,
)
