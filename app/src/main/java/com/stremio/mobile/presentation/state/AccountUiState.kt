package com.stremio.mobile.presentation.state

data class AccountUiState(
    val isAuthenticated: Boolean = false,
    val email: String? = null,
    val authKey: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)
