package com.stremio.mobile.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.GlassSurface
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.core.theme.ScreenGutter
import com.stremio.mobile.core.theme.SearchBackground
import com.stremio.mobile.data.model.AddonItem

@Composable
fun AddonShelf(
    title: String,
    addons: List<AddonItem>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(start = ScreenGutter),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        LazyRow(
            contentPadding = PaddingValues(start = ScreenGutter, end = ScreenGutter),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(addons, key = { it.id }) { addon ->
                AddonCard(addon)
            }
        }
    }
}

@Composable
fun AddonCard(addon: AddonItem) {
    Column(
        modifier = Modifier
            .width(210.dp)
            .height(156.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(GlassSurface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SearchBackground),
                contentAlignment = Alignment.Center,
            ) {
                if (addon.logo != null) {
                    AsyncImage(
                        model = addon.logo,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Extension,
                        contentDescription = null,
                        tint = MutedText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column {
                Text(
                    text = addon.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = addon.version ?: "Addon",
                    color = MutedText,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = addon.description ?: "No description available",
            color = MutedText,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = addon.types.joinToString(" / ").ifBlank { "all" },
            color = AccentPurple,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
