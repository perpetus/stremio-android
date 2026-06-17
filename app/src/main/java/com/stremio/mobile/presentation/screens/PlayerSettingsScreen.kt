package com.stremio.mobile.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.MutedText

@Composable
fun PlayerSettingsScreen(
    settings: com.stremio.core.types.profile.Profile.Settings?,
    onUpdateSettings: (com.stremio.core.types.profile.Profile.Settings) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsHeader(title = "Player Settings", onBack = onBack)

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

            val subtitleSizes = listOf(
                50 to "50% (Very Small)",
                75 to "75% (Small)",
                100 to "100% (Normal)",
                120 to "120% (Medium)",
                150 to "150% (Large)",
                200 to "200% (Extra Large)"
            )

            val seekDurations = listOf(
                5000L to "5 seconds",
                10000L to "10 seconds",
                15000L to "15 seconds",
                30000L to "30 seconds",
                60000L to "1 minute"
            )

            val frameRates = listOf(
                com.stremio.core.types.profile.Profile.FrameRateMatchingStrategy.DISABLED to "Disabled",
                com.stremio.core.types.profile.Profile.FrameRateMatchingStrategy.FRAME_RATE_ONLY to "Frame Rate Only",
                com.stremio.core.types.profile.Profile.FrameRateMatchingStrategy.FRAME_RATE_AND_RESOLUTION to "Frame Rate & Resolution"
            )

            val colors = listOf(
                "#FFFFFF" to "White",
                "#FFFF00" to "Yellow",
                "#00FFFF" to "Cyan",
                "#FF00FF" to "Magenta",
                "#00FF00" to "Green",
                "#FF0000" to "Red",
                "#000000" to "Black"
            )

            val nextVideoDurations = listOf(
                0L to "Disabled",
                5000L to "5 seconds",
                10000L to "10 seconds",
                15000L to "15 seconds",
                30000L to "30 seconds"
            )

            Text(
                text = "SUBTITLES",
                color = MutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )

            SettingsToggleRow(
                title = "Auto-select Subtitles",
                checked = settings.subtitlesAutoSelect,
                onCheckedChange = { onUpdateSettings(settings.copy(subtitlesAutoSelect = it)) },
                description = "Automatically select subtitles during playback"
            )

            SettingsDropdownRow(
                title = "Default Subtitle Language",
                selectedValue = settings.subtitlesLanguage ?: "eng",
                options = languages,
                onSelect = { onUpdateSettings(settings.copy(subtitlesLanguage = it)) }
            )

            SettingsDropdownRow(
                title = "Subtitle Font Size",
                selectedValue = settings.subtitlesSize,
                options = subtitleSizes,
                onSelect = { onUpdateSettings(settings.copy(subtitlesSize = it)) }
            )

            SettingsDropdownRow(
                title = "Subtitle Text Color",
                selectedValue = settings.subtitlesTextColor,
                options = colors,
                onSelect = { onUpdateSettings(settings.copy(subtitlesTextColor = it)) }
            )

            SettingsDropdownRow(
                title = "Subtitle Background Color",
                selectedValue = settings.subtitlesBackgroundColor,
                options = colors,
                onSelect = { onUpdateSettings(settings.copy(subtitlesBackgroundColor = it)) }
            )

            SettingsDropdownRow(
                title = "Subtitle Outline Color",
                selectedValue = settings.subtitlesOutlineColor,
                options = colors,
                onSelect = { onUpdateSettings(settings.copy(subtitlesOutlineColor = it)) }
            )

            Text(
                text = "AUDIO",
                color = MutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )

            SettingsDropdownRow(
                title = "Default Audio Language",
                selectedValue = settings.audioLanguage ?: "eng",
                options = languages,
                onSelect = { onUpdateSettings(settings.copy(audioLanguage = it)) }
            )

            SettingsToggleRow(
                title = "Audio Passthrough",
                checked = settings.audioPassthrough,
                onCheckedChange = { onUpdateSettings(settings.copy(audioPassthrough = it)) },
                description = "Passthrough raw audio stream (AC3/DTS) directly to receiver"
            )

            SettingsToggleRow(
                title = "Surround Sound Support",
                checked = settings.surroundSound,
                onCheckedChange = { onUpdateSettings(settings.copy(surroundSound = it)) },
                description = "Enable multi-channel output for 5.1/7.1 home audio systems"
            )

            Text(
                text = "PLAYBACK CONTROLS",
                color = MutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )

            SettingsToggleRow(
                title = "Play in Background",
                checked = settings.playInBackground,
                onCheckedChange = { onUpdateSettings(settings.copy(playInBackground = it)) },
                description = "Continue playing audio when app is minimized"
            )

            SettingsToggleRow(
                title = "Hardware Decoding",
                checked = settings.hardwareDecoding,
                onCheckedChange = { onUpdateSettings(settings.copy(hardwareDecoding = it)) },
                description = "Use hardware acceleration for video decoding (ExoPlayer)"
            )

            SettingsDropdownRow(
                title = "Frame Rate Matching",
                selectedValue = settings.frameRateMatchingStrategy,
                options = frameRates,
                onSelect = { onUpdateSettings(settings.copy(frameRateMatchingStrategy = it)) },
                description = "Matches refresh rate of TV with video frame rate (for TV setups)"
            )

            SettingsDropdownRow(
                title = "Seek Duration",
                selectedValue = settings.seekTimeDuration,
                options = seekDurations,
                onSelect = { onUpdateSettings(settings.copy(seekTimeDuration = it)) },
                description = "Time to seek forward/backward on skip buttons"
            )

            SettingsToggleRow(
                title = "Auto Play Next Episode",
                checked = settings.bingeWatching,
                onCheckedChange = { onUpdateSettings(settings.copy(bingeWatching = it)) }
            )

            SettingsDropdownRow(
                title = "Next Video Notification",
                selectedValue = settings.nextVideoNotificationDuration,
                options = nextVideoDurations,
                onSelect = { onUpdateSettings(settings.copy(nextVideoNotificationDuration = it)) },
                description = "Overlay duration prompting you to play next episode"
            )
        }
    }
}
