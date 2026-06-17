package com.stremio.mobile.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.core.theme.CardFallback
import com.stremio.mobile.core.theme.ScreenGutter

@Composable
fun FeaturedHero(
    item: CatalogItem?,
    onClick: (CatalogItem) -> Unit,
) {
    if (item == null) {
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .padding(start = ScreenGutter, end = ScreenGutter)
            .clip(RoundedCornerShape(26.dp))
            .background(CardFallback)
            .clickable { onClick(item) },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.background ?: item.poster)
                .size(800, 460)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
                .build(),
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xEE050515),
                            0.52f to Color(0x88050515),
                            1.0f to Color(0x22050515),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.68f to Color.Transparent,
                            1.0f to Color(0xFF050515),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassPill(text = "Featured now")
            Text(
                text = item.name,
                color = Color.White,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(item.releaseInfo, item.imdbRating?.let { "IMDb $it" })
                    .joinToString("  "),
                color = Color(0xFFE7E1FF),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
