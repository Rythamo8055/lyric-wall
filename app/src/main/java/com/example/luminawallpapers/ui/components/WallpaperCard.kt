package com.example.luminawallpapers.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.luminawallpapers.model.Wallpaper

@Composable
fun WallpaperCard(
    wallpaper: Wallpaper,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 3.dp,
            pressedElevation = 6.dp
        ),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.68f)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Pure Procedural 8-Bit Pixel Art Thumbnail Canvas (Instant render, 0 network lag, 120 FPS)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(wallpaper.colorHex))
            ) {
                val w = size.width
                val h = size.height
                val pixelSize = w / 60f

                // Draw starry background for all celestial variants
                val isLy02 = wallpaper.id == "w6" || wallpaper.thumbnailUrl == "local://ly02_cosmic"
                if (isLy02) {
                    // LY02: Cosmic Wilderness Thumbnail (Clean Line Art + Curved Pixel HUD)
                    // 1. Stars
                    val starsLy02 = listOf(
                        Offset(0.15f * w, 0.12f * h),
                        Offset(0.85f * w, 0.10f * h),
                        Offset(0.50f * w, 0.18f * h),
                        Offset(0.20f * w, 0.50f * h),
                        Offset(0.80f * w, 0.48f * h),
                        Offset(0.35f * w, 0.42f * h)
                    )
                    for (pos in starsLy02) {
                        drawCircle(color = Color(0xFFEEEEEE), radius = 1.8f, center = pos)
                        drawLine(color = Color(0xAAFFFFFF), start = Offset(pos.x - 3f, pos.y), end = Offset(pos.x + 3f, pos.y), strokeWidth = 1f)
                        drawLine(color = Color(0xAAFFFFFF), start = Offset(pos.x, pos.y - 3f), end = Offset(pos.x, pos.y + 3f), strokeWidth = 1f)
                    }

                    // 2. Mini Productivity HUD at top
                    drawLine(color = Color(0x88607090), start = Offset(0.12f * w, 0.22f * h), end = Offset(0.88f * w, 0.22f * h), strokeWidth = 1f)
                    // Mini progress bars
                    drawRoundRect(color = Color(0xFF2A3245), topLeft = Offset(0.35f * w, 0.26f * h), size = Size(0.35f * w, 4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f))
                    drawRoundRect(color = Color(0xFFFFFFFF), topLeft = Offset(0.35f * w, 0.26f * h), size = Size(0.25f * w, 4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f))
                    drawRoundRect(color = Color(0xFF2A3245), topLeft = Offset(0.35f * w, 0.30f * h), size = Size(0.35f * w, 4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f))
                    drawRoundRect(color = Color(0xFFB0B8C8), topLeft = Offset(0.35f * w, 0.30f * h), size = Size(0.16f * w, 4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f))

                    // 3. Saturn on left
                    val satCenter = Offset(0.28f * w, 0.60f * h)
                    val satR = 0.08f * w
                    drawCircle(color = Color.Black, radius = satR, center = satCenter)
                    drawCircle(color = Color.White, radius = satR, center = satCenter, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                    // Ring
                    drawOval(color = Color(0xDDFFFFFF), topLeft = Offset(satCenter.x - satR * 2.2f, satCenter.y - satR * 0.55f), size = Size(satR * 4.4f, satR * 1.1f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f))

                    // 4. Crescent Moon on right
                    val moonCenter = Offset(0.68f * w, 0.56f * h)
                    drawCircle(color = Color(0xFFEEEEEE), radius = 0.06f * w, center = moonCenter)
                    drawCircle(color = Color.Black, radius = 0.055f * w, center = Offset(moonCenter.x - 0.025f * w, moonCenter.y - 0.01f * w))

                    // 5. Mountain Peaks (Line Art)
                    val p1 = Offset(0.10f * w, 0.72f * h)
                    val pPeak1 = Offset(0.32f * w, 0.66f * h)
                    val pMid = Offset(0.40f * w, 0.73f * h)
                    val pPeak2 = Offset(0.50f * w, 0.63f * h)
                    val pPeak3 = Offset(0.72f * w, 0.68f * h)
                    val pEnd = Offset(0.95f * w, 0.74f * h)

                    drawLine(color = Color.White, start = p1, end = pPeak1, strokeWidth = 1.8f)
                    drawLine(color = Color.White, start = pPeak1, end = pMid, strokeWidth = 1.8f)
                    drawLine(color = Color.White, start = pMid, end = pPeak2, strokeWidth = 1.8f)
                    drawLine(color = Color.White, start = pPeak2, end = pPeak3, strokeWidth = 1.8f)
                    drawLine(color = Color.White, start = pPeak3, end = pEnd, strokeWidth = 1.8f)

                    // 6. Tent at base center
                    val tPeak = Offset(0.50f * w, 0.78f * h)
                    val tL = Offset(0.42f * w, 0.84f * h)
                    val tR = Offset(0.58f * w, 0.84f * h)
                    drawLine(color = Color.White, start = tL, end = tPeak, strokeWidth = 1.8f)
                    drawLine(color = Color.White, start = tPeak, end = tR, strokeWidth = 1.8f)
                    drawLine(color = Color.White, start = tL, end = tR, strokeWidth = 1.8f)
                    drawLine(color = Color.White, start = tPeak, end = Offset(0.50f * w, 0.84f * h), strokeWidth = 1.2f)

                    // Lake ripples
                    drawLine(color = Color(0xAAFFFFFF), start = Offset(0.35f * w, 0.86f * h), end = Offset(0.65f * w, 0.86f * h), strokeWidth = 1.5f)
                    drawLine(color = Color(0x88FFFFFF), start = Offset(0.40f * w, 0.88f * h), end = Offset(0.60f * w, 0.88f * h), strokeWidth = 1.2f)
                } else {
                    // Twinkling stars
                    val starPositions = listOf(
                        Offset(0.2f * w, 0.15f * h),
                        Offset(0.75f * w, 0.12f * h),
                        Offset(0.15f * w, 0.45f * h),
                        Offset(0.85f * w, 0.4f * h),
                        Offset(0.45f * w, 0.28f * h),
                        Offset(0.3f * w, 0.7f * h),
                        Offset(0.7f * w, 0.65f * h)
                    )
                    for (pos in starPositions) {
                        drawRect(
                            color = Color(0xFFDDDDDD),
                            topLeft = pos,
                            size = Size(pixelSize, pixelSize)
                        )
                        // Cross star
                        drawRect(color = Color(0x88FFFFFF), topLeft = Offset(pos.x - pixelSize, pos.y), size = Size(pixelSize, pixelSize))
                        drawRect(color = Color(0x88FFFFFF), topLeft = Offset(pos.x + pixelSize, pos.y), size = Size(pixelSize, pixelSize))
                        drawRect(color = Color(0x88FFFFFF), topLeft = Offset(pos.x, pos.y - pixelSize), size = Size(pixelSize, pixelSize))
                        drawRect(color = Color(0x88FFFFFF), topLeft = Offset(pos.x, pos.y + pixelSize), size = Size(pixelSize, pixelSize))
                    }

                    // Moon in upper right
                    val moonCenter = Offset(0.72f * w, 0.24f * h)
                    val moonR = 0.16f * w
                    drawCircle(color = Color(0xFFEEEEEE), radius = moonR, center = moonCenter)
                    drawCircle(color = Color(0xFFACACAC), radius = moonR * 0.85f, center = Offset(moonCenter.x - moonR * 0.15f, moonCenter.y + moonR * 0.15f))
                    // Crater
                    drawCircle(color = Color(0xFF787878), radius = moonR * 0.25f, center = Offset(moonCenter.x + moonR * 0.2f, moonCenter.y - moonR * 0.1f))

                    // Variant-specific elements
                    when (wallpaper.id) {
                        "w2" -> { // Varsha (Monsoon Rain)
                            for (r in 0 until 18) {
                                val rx = (r * 0.055f) * w
                                val ry = (0.2f + (r % 5) * 0.14f) * h
                                drawRect(
                                    color = Color(0xFF8AB4F8),
                                    topLeft = Offset(rx, ry),
                                    size = Size(pixelSize * 0.8f, pixelSize * 4f)
                                )
                            }
                        }
                        "w4" -> { // Shishira (Winter Snow)
                            for (s in 0 until 16) {
                                val sx = (0.1f + (s * 0.05f)) * w
                                val sy = (0.25f + (s % 6) * 0.1f) * h
                                drawRect(
                                    color = Color(0xFFE8F0FE),
                                    topLeft = Offset(sx, sy),
                                    size = Size(pixelSize * 1.5f, pixelSize * 1.5f)
                                )
                            }
                        }
                        else -> { // Clouds
                            drawRect(
                                color = Color(0xAA70757A),
                                topLeft = Offset(0.08f * w, 0.48f * h),
                                size = Size(0.55f * w, 0.09f * h)
                            )
                            drawRect(
                                color = Color(0xDD9AA0A6),
                                topLeft = Offset(0.18f * w, 0.44f * h),
                                size = Size(0.35f * w, 0.07f * h)
                            )
                        }
                    }
                }
            }

            // Gradient scrim at bottom for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )

            // Category badge at top left
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = CircleShape,
                modifier = Modifier
                    .padding(10.dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = wallpaper.category,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // Favorite Button at top right
            val favColor by animateColorAsState(
                targetValue = if (wallpaper.isFavorite) Color(0xFFFF5252) else Color.White,
                animationSpec = tween(durationMillis = 200),
                label = "favColor"
            )

            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = CircleShape,
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopEnd)
            ) {
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (wallpaper.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = favColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Bottom text details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = wallpaper.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = wallpaper.author,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.75f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Surface(
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "8-Bit Live",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                color = Color.White
                            ),
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
