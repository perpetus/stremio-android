package com.stremio.mobile.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.GlassSurface
import com.stremio.mobile.core.theme.MutedText

@Composable
fun InfoSettingsScreen(
    serverVersion: String,
    serverConfigPath: String,
    serverCachePath: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsHeader(title = "Info & About", onBack = onBack)

        Text(
            text = "APPLICATION DIAGNOSTICS",
            color = MutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(GlassSurface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ServerDetailRow(label = "Stremio Mobile Client", value = "1.0.0 (Alpha)")
            ServerDetailRow(label = "Streaming Server Version", value = serverVersion)
            ServerDetailRow(label = "Database Config Path", value = serverConfigPath)
            ServerDetailRow(label = "Server Cache Directory", value = serverCachePath)
        }
    }
}

@Composable
private fun ServerDetailRow(label: String, value: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = MutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            lineHeight = 16.sp,
        )
    }
}
