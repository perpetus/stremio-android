package com.stremio.mobile.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.stremio.mobile.core.theme.AccentGreen
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.CardFallback
import com.stremio.mobile.core.theme.GlassSurface
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.data.model.MetaDetails

@Composable
fun DetailSheet(
    details: MetaDetails,
    inLibrary: Boolean,
    onBack: () -> Unit,
    onToggleLibrary: () -> Unit,
    onOpenStreams: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(462.dp)
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .background(Color(0xF2141422))
            .pointerInput(Unit) {
                var dragAccumulator = 0f
                var hasTriggered = false
                detectVerticalDragGestures(
                    onDragStart = {
                        dragAccumulator = 0f
                        hasTriggered = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (!hasTriggered) {
                            dragAccumulator += dragAmount
                            if (dragAccumulator < -40f) {
                                hasTriggered = true
                                onOpenStreams()
                            }
                        }
                    }
                )
            }
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp)
                .background(CardFallback),
        ) {
            AsyncImage(
                model = details.item.background ?: details.item.poster,
                contentDescription = details.item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0x66000000), Color(0xFF10101D)),
                        ),
                    ),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .padding(14.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .clickable(onClick = onBack)
                    .padding(10.dp),
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = details.item.name,
                color = Color.White,
                fontSize = 22.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(details.year, details.runtime, details.item.imdbRating?.let { "IMDb $it" })
                    .joinToString("  "),
                color = MutedText,
                fontSize = 13.sp,
            )
            if (details.isLoading) {
                CircularProgressIndicator(color = AccentPurple, modifier = Modifier.size(24.dp))
            } else if (details.error != null) {
                Text(text = details.error, color = Color(0xFFFFC66D), fontSize = 13.sp)
            } else {
                Text(
                    text = details.description ?: "No summary available.",
                    color = Color(0xFFE4E0EE),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = (details.genres + details.cast.take(3)).joinToString("  "),
                    color = MutedText,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onToggleLibrary,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                ) {
                    Icon(
                        imageVector = if (inLibrary) Icons.Outlined.Check else Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (inLibrary) "In Library" else "Add")
                }
                Button(
                    onClick = onOpenStreams,
                    modifier = Modifier.weight(1f),
                    enabled = !details.isLoading,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        disabledContainerColor = GlassSurface,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play", color = Color.White)
                }
            }
        }
    }
}
