package com.example.luminawallpapers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.luminawallpapers.ui.main.MainScreen
import com.example.luminawallpapers.ui.main.MainScreenViewModel
import com.example.luminawallpapers.ui.screens.DetailScreen

@Composable
fun MainNavigation(viewModel: MainScreenViewModel) {
    val backStack = rememberNavBackStack(MainRoute)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<MainRoute> {
                MainScreen(
                    viewModel = viewModel,
                    onNavigate = { navKey -> backStack.add(navKey) }
                )
            }
            entry<LiveWallpaperRoute> { key ->
                com.example.luminawallpapers.ui.screens.LiveWallpaperPreviewScreen(
                    wallpaperId = key.wallpaperId,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<DetailRoute> { key ->
                val wallpaper = viewModel.getWallpaper(key.wallpaperId)
                if (wallpaper != null) {
                    DetailScreen(
                        wallpaper = wallpaper,
                        onBack = { backStack.removeLastOrNull() },
                        onFavoriteToggle = { id -> viewModel.toggleFavorite(id) }
                    )
                }
            }
        }
    )
}
