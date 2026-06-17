package com.stremio.mobile.presentation.state

import com.stremio.mobile.data.model.AddonItem

data class AddonsUiState(
    val official: List<AddonItem> = emptyList(),
    val community: List<AddonItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)
