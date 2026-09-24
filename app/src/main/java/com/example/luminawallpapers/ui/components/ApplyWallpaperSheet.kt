package com.example.luminawallpapers.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.luminawallpapers.util.WallpaperTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplyWallpaperSheet(
    onDismissRequest: () -> Unit,
    onSelectTarget: (WallpaperTarget) -> Unit,
    onLiveWallpaperClick: ((WallpaperTarget) -> Unit)? = null,
    isLoading: Boolean = false
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Apply Wallpaper",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Choose which screen you want to apply this wallpaper to",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Applying wallpaper to device...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (onLiveWallpaperClick != null) {
                        Text(
                            text = "Interactive Live Wallpaper (Touch & Motion)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp, start = 4.dp)
                        )

                        ApplyOptionRow(
                            title = "✨ Live: Home Screen Only",
                            subtitle = "Interactive live wallpaper on Home screen (Lock screen untouched)",
                            icon = Icons.Rounded.Home,
                            isPrimary = true,
                            onClick = { onLiveWallpaperClick(WallpaperTarget.HOME) }
                        )

                        ApplyOptionRow(
                            title = "✨ Live: Both Home & Lock",
                            subtitle = "Interactive live wallpaper active on both screens",
                            icon = Icons.Rounded.Smartphone,
                            isPrimary = false,
                            onClick = { onLiveWallpaperClick(WallpaperTarget.BOTH) }
                        )

                        ApplyOptionRow(
                            title = "✨ Live: Lock Screen Only",
                            subtitle = "Interactive live wallpaper active on Lock screen only",
                            icon = Icons.Rounded.Lock,
                            isPrimary = false,
                            onClick = { onLiveWallpaperClick(WallpaperTarget.LOCK) }
                        )

                        Text(
                            text = "Or Apply as Static Snapshot (0% Battery)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                        )
                    }

                    ApplyOptionRow(
                        title = "Static: Home Screen Only",
                        subtitle = "Frozen image snapshot for Home Screen only",
                        icon = Icons.Rounded.Home,
                        isPrimary = false,
                        onClick = { onSelectTarget(WallpaperTarget.HOME) }
                    )
                    ApplyOptionRow(
                        title = "Static: Lock Screen Only",
                        subtitle = "Frozen image snapshot for Lock Screen only",
                        icon = Icons.Rounded.Lock,
                        isPrimary = false,
                        onClick = { onSelectTarget(WallpaperTarget.LOCK) }
                    )
                    ApplyOptionRow(
                        title = "Static: Both Screens",
                        subtitle = "Frozen image snapshot for both Home and Lock screens",
                        icon = Icons.Rounded.Smartphone,
                        isPrimary = false,
                        onClick = { onSelectTarget(WallpaperTarget.BOTH) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ApplyOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = if (isPrimary) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val iconContainerColor = if (isPrimary) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val iconColor = if (isPrimary) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = iconContainerColor,
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
