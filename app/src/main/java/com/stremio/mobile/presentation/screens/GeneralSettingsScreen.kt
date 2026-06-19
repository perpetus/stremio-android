package com.stremio.mobile.presentation.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.GlassSurface
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.presentation.components.ThemedCard
import com.stremio.mobile.presentation.components.ThemedButton
import com.stremio.mobile.presentation.components.rememberGlobalHapticFeedback

@Composable
fun GeneralSettingsScreen(
    isAuthenticated: Boolean,
    isTraktAuthenticated: Boolean,
    onAuthenticateTrakt: (Context) -> Unit,
    onLogoutTrakt: () -> Unit,
    onInstallTraktAddon: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsHeader(title = "General Settings", onBack = onBack)

        Text(
            text = "INTEGRATIONS",
            color = MutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )

        // Trakt.tv Integration Row
        ThemedCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Trakt Integration",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (isTraktAuthenticated) "Authenticated" else "Not Authenticated",
                        color = if (isTraktAuthenticated) Color(0xFF4CAF50) else MutedText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "Sync what you watch on Stremio directly to your Trakt profile history and watchlist.",
                    color = MutedText,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isTraktAuthenticated) {
                        ThemedButton(
                            text = "Log Out Trakt",
                            onClick = onLogoutTrakt,
                            containerColor = Color(0xFFD32F2F),
                            modifier = Modifier.weight(1f)
                        )
                        ThemedButton(
                            text = "Install Addon",
                            onClick = onInstallTraktAddon,
                            containerColor = AccentPurple,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        ThemedButton(
                            text = "Authenticate Trakt",
                            onClick = { onAuthenticateTrakt(context) },
                            enabled = isAuthenticated,
                            containerColor = AccentPurple,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Text(
            text = "LINKS",
            color = MutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsLinkRow(title = "Help / Support Center", url = "https://stremio.zendesk.com/hc/en-us")
            SettingsLinkRow(title = "Terms of Service", url = "https://www.stremio.com/tos")
            SettingsLinkRow(title = "Privacy Policy", url = "https://www.stremio.com/privacy")
        }
    }
}

@Composable
private fun SettingsLinkRow(
    title: String,
    url: String
) {
    val context = LocalContext.current
    val triggerHaptic = rememberGlobalHapticFeedback()
    ThemedCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    triggerHaptic()
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = MutedText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
