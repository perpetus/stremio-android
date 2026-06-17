package com.stremio.mobile.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InterfaceSettingsScreen(
    settings: com.stremio.core.types.profile.Profile.Settings?,
    onUpdateSettings: (com.stremio.core.types.profile.Profile.Settings) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsHeader(title = "Interface Settings", onBack = onBack)

        if (settings != null) {
            val languages = listOf(
                "eng" to "English",
                "spa" to "Spanish",
                "fre" to "French",
                "ger" to "German",
                "ita" to "Italian",
                "por" to "Portuguese",
                "rus" to "Russian",
                "zho" to "Chinese"
            )

            SettingsDropdownRow(
                title = "Interface Language",
                selectedValue = settings.interfaceLanguage,
                options = languages,
                onSelect = { onUpdateSettings(settings.copy(interfaceLanguage = it)) },
                description = "Choose language for menus and catalogs"
            )

            SettingsToggleRow(
                title = "Hide Spoilers",
                checked = settings.hideSpoilers,
                onCheckedChange = { onUpdateSettings(settings.copy(hideSpoilers = it)) },
                description = "Blur unwatched movies and series episode posters"
            )

            SettingsToggleRow(
                title = "Gamepad Support",
                checked = settings.gamepadSupport,
                onCheckedChange = { onUpdateSettings(settings.copy(gamepadSupport = it)) },
                description = "Enable remote control and gamepad navigation support"
            )
        }
    }
}
