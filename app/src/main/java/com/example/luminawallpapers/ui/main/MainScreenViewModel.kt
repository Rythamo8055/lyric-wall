package com.example.luminawallpapers.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.luminawallpapers.data.WallpaperRepository
import com.example.luminawallpapers.model.Category
import com.example.luminawallpapers.model.Wallpaper
import com.example.luminawallpapers.ui.screens.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class WallpaperUiState(
    val currentTab: Int = 0,
    val selectedCategory: String = "All",
    val searchQuery: String = "",
    val wallpapers: List<Wallpaper> = emptyList(),
    val categories: List<Category> = emptyList(),
    val favorites: List<Wallpaper> = emptyList(),
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val isDynamicColor: Boolean = true
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WallpaperRepository(application)
    private val _uiState = MutableStateFlow(WallpaperUiState())
    val uiState: StateFlow<WallpaperUiState> = _uiState.asStateFlow()

    init {
        // Observe favorites changes
        repository.getFavoritesFlow()
            .onEach {
                refreshData()
            }
            .launchIn(viewModelScope)

        refreshData()
    }

    private fun refreshData() {
        val currentCategory = _uiState.value.selectedCategory
        val currentQuery = _uiState.value.searchQuery
        _uiState.value = _uiState.value.copy(
            wallpapers = repository.filterWallpapers(currentCategory, currentQuery),
            categories = repository.getCategories(),
            favorites = repository.getFavoriteWallpapers()
        )
    }

    fun selectTab(tab: Int) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    fun selectCategory(category: String) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            currentTab = 0 // jump to explore to see category results
        )
        refreshData()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        refreshData()
    }

    fun toggleFavorite(wallpaperId: String) {
        repository.toggleFavorite(wallpaperId)
        refreshData()
    }

    fun setThemeMode(mode: AppThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isDynamicColor = enabled)
    }

    fun getWallpaper(id: String): Wallpaper? {
        return repository.getWallpaperById(id)
    }
}
