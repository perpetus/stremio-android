package com.stremio.mobile.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.stremio.mobile.presentation.state.MainSection

enum class AppView(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Discover("Discover", Icons.Outlined.Explore),
    Library("Library", Icons.Outlined.VideoLibrary),
    Settings("Settings", Icons.Outlined.Settings),
}

fun AppView.toSection(): MainSection = when (this) {
    AppView.Home -> MainSection.Home
    AppView.Discover -> MainSection.Discover
    AppView.Library -> MainSection.Library
    AppView.Settings -> MainSection.Settings
}

fun MainSection.toAppView(): AppView = when (this) {
    MainSection.Home -> AppView.Home
    MainSection.Discover -> AppView.Discover
    MainSection.Library -> AppView.Library
    MainSection.Settings -> AppView.Settings
}
