package com.example.luminawallpapers.ui.main

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.example.luminawallpapers.DetailRoute
import com.example.luminawallpapers.ui.screens.CategoriesScreen
import com.example.luminawallpapers.ui.screens.ExploreScreen
import com.example.luminawallpapers.ui.screens.FavoritesScreen
import com.example.luminawallpapers.ui.screens.SettingsScreen

data class NavTab(
    val title: String,
    val icon: ImageVector
)

@Composable
fun MainScreen(
    viewModel: MainScreenViewModel,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val tabs = listOf(
        NavTab("Explore", Icons.Rounded.Explore),
        NavTab("Collections", Icons.Rounded.Category),
        NavTab("Favorites", Icons.Rounded.Favorite),
        NavTab("Settings", Icons.Rounded.Settings)
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = uiState.currentTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.selectTab(index) },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = uiState.currentTab,
            label = "tab_transition",
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) { tabIndex ->
            when (tabIndex) {
                0 -> ExploreScreen(
                    wallpapers = uiState.wallpapers,
                    selectedCategory = uiState.selectedCategory,
                    searchQuery = uiState.searchQuery,
                    onCategorySelected = { viewModel.selectCategory(it) },
                    onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    onWallpaperClick = { id -> onNavigate(com.example.luminawallpapers.LiveWallpaperRoute(id)) },
                    onFavoriteToggle = { id -> viewModel.toggleFavorite(id) },
                    onOpenLiveWallpaper = { onNavigate(com.example.luminawallpapers.LiveWallpaperRoute("w1")) }
                )
                1 -> CategoriesScreen(
                    categories = uiState.categories,
                    onCategoryClick = { categoryName ->
                        viewModel.selectCategory(categoryName)
                        viewModel.selectTab(0)
                    }
                )
                2 -> FavoritesScreen(
                    favorites = uiState.favorites,
                    onWallpaperClick = { id -> onNavigate(com.example.luminawallpapers.LiveWallpaperRoute(id)) },
                    onFavoriteToggle = { id -> viewModel.toggleFavorite(id) },
                    onNavigateToExplore = { viewModel.selectTab(0) }
                )
                3 -> SettingsScreen(
                    themeMode = uiState.themeMode,
                    isDynamicColorEnabled = uiState.isDynamicColor,
                    onThemeModeChanged = { viewModel.setThemeMode(it) },
                    onDynamicColorToggled = { viewModel.setDynamicColor(it) }
                )
            }
        }
    }
}
