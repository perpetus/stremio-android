package com.stremio.mobile.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.stremio.mobile.core.StremioCore
import com.stremio.mobile.presentation.state.AccountUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthRepository(
    private val core: StremioCore,
    private val context: Context
) {
    private val preferences = context.getSharedPreferences("stremio_account", Context.MODE_PRIVATE)

    fun getAccountStateFlow(): Flow<AccountUiState> {
        return core.ctx().map { ctx ->
            val auth = ctx.profile.auth
            if (auth != null) {
                AccountUiState(isAuthenticated = true, email = auth.user.email, authKey = auth.key)
            } else {
                AccountUiState()
            }
        }
    }

    fun getProfileSettingsFlow() = core.ctx().map { it.profile.settings }

    fun getTraktAuthFlow() = core.ctx().map { ctx ->
        val auth = ctx.profile.auth
        if (auth != null) {
            val trakt = auth.user.trakt
            if (trakt != null) {
                val expiresAtSeconds = trakt.expiresIn.seconds
                val createdAtSeconds = trakt.createdAt.seconds
                (System.currentTimeMillis() / 1000) < (createdAtSeconds + expiresAtSeconds)
            } else {
                false
            }
        } else {
            false
        }
    }

    fun accountFromCore(): AccountUiState {
        val auth = runCatching { core.getCtx().profile.auth }.getOrNull()
        return if (auth != null) {
            AccountUiState(isAuthenticated = true, email = auth.user.email, authKey = auth.key)
        } else {
            AccountUiState()
        }
    }

    fun isAutoStartOnBoot(): Boolean = preferences.getBoolean("auto_start_on_boot", false)
    fun setAutoStartOnBoot(enabled: Boolean) {
        preferences.edit().putBoolean("auto_start_on_boot", enabled).apply()
    }

    fun isServerInForeground(): Boolean = preferences.getBoolean("server_in_foreground", true)
    fun setServerInForeground(enabled: Boolean) {
        preferences.edit().putBoolean("server_in_foreground", enabled).apply()
    }

    fun isMobileDataWarning(): Boolean = preferences.getBoolean("mobile_data_warning", true)
    fun setMobileDataWarning(enabled: Boolean) {
        preferences.edit().putBoolean("mobile_data_warning", enabled).apply()
    }

    fun isKeepScreenOn(): Boolean = preferences.getBoolean("keep_screen_on", true)
    fun setKeepScreenOn(enabled: Boolean) {
        preferences.edit().putBoolean("keep_screen_on", enabled).apply()
    }

    fun isTraktAuthenticated(): Boolean {
        val user = runCatching { core.getCtx().profile.auth?.user }.getOrNull() ?: return false
        val trakt = user.trakt ?: return false
        val expiresAtSeconds = trakt.expiresIn.seconds
        val createdAtSeconds = trakt.createdAt.seconds
        return (System.currentTimeMillis() / 1000) < (createdAtSeconds + expiresAtSeconds)
    }

    fun authenticateTrakt(context: Context) {
        val user = runCatching { core.getCtx().profile.auth?.user }.getOrNull() ?: return
        val url = "https://www.strem.io/trakt/auth/${user.id}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    suspend fun logoutTrakt() {
        core.logoutTrakt()
    }

    suspend fun installTraktAddon() {
        core.installTraktAddon()
    }

    fun getSavedAuthKey(): String? = preferences.getString("authKey", null)

    fun saveAuthKeyAndEmail(authKey: String, email: String) {
        preferences.edit()
            .putString("authKey", authKey)
            .putString("email", email)
            .apply()
    }

    fun clearSavedSession() {
        preferences.edit().clear().apply()
    }

    suspend fun loginWithToken(authKey: String) {
        core.loginWithToken(authKey)
    }

    suspend fun login(email: String, password: String) {
        core.login(email.trim(), password)
    }

    suspend fun register(email: String, password: String, marketingConsent: Boolean) {
        core.register(email.trim(), password, marketingConsent)
    }

    suspend fun logout() {
        core.logout()
    }

    fun isAuthenticated(): Boolean = core.isAuthenticated()

    suspend fun updateSettings(newSettings: com.stremio.core.types.profile.Profile.Settings) {
        core.updateSettings(newSettings)
    }
}
