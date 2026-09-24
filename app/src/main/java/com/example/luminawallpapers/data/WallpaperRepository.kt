package com.example.luminawallpapers.data

import android.content.Context
import android.content.SharedPreferences
import com.example.luminawallpapers.model.Category
import com.example.luminawallpapers.model.Wallpaper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WallpaperRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("lumina_prefs", Context.MODE_PRIVATE)
    private val favoriteIds = MutableStateFlow(loadFavoriteIds())

    private val liveWallpapers = listOf(
        Wallpaper(
            id = "w1",
            title = "LY01: Celestial Night",
            author = "Lumina Engine",
            category = "8-Bit AMOLED",
            thumbnailUrl = "local://ly01_celestial",
            fullUrl = "local://ly01_celestial",
            resolution = "8-Bit Procedural HD",
            tags = listOf("8Bit", "AMOLED", "Live", "Moon", "Stars", "LoFi", "Interactive"),
            colorHex = 0xFF000000,
            views = 14820,
            downloads = 6840
        ),
        Wallpaper(
            id = "w2",
            title = "LY01: Varsha Monsoon",
            author = "Lumina Engine",
            category = "Indian Seasons",
            thumbnailUrl = "local://ly01_varsha",
            fullUrl = "local://ly01_varsha",
            resolution = "8-Bit Procedural HD",
            tags = listOf("8Bit", "Monsoon", "Varsha", "Rain", "Storm", "Indian Season"),
            colorHex = 0xFF050811,
            views = 9420,
            downloads = 4120
        ),
        Wallpaper(
            id = "w3",
            title = "LY01: Sharad Moonlight",
            author = "Lumina Engine",
            category = "Indian Seasons",
            thumbnailUrl = "local://ly01_sharad",
            fullUrl = "local://ly01_sharad",
            resolution = "8-Bit Procedural HD",
            tags = listOf("8Bit", "Sharad", "Autumn", "Harvest Moon", "OLED", "Indian Season"),
            colorHex = 0xFF000000,
            views = 11200,
            downloads = 5430
        ),
        Wallpaper(
            id = "w4",
            title = "LY01: Shishira Frost",
            author = "Lumina Engine",
            category = "Indian Seasons",
            thumbnailUrl = "local://ly01_shishira",
            fullUrl = "local://ly01_shishira",
            resolution = "8-Bit Procedural HD",
            tags = listOf("8Bit", "Winter", "Shishira", "Snow", "Frost", "Indian Season"),
            colorHex = 0xFF060B14,
            views = 7620,
            downloads = 3290
        ),
        Wallpaper(
            id = "w5",
            title = "LY01: Vasanta Twilight",
            author = "Lumina Engine",
            category = "Indian Seasons",
            thumbnailUrl = "local://ly01_vasanta",
            fullUrl = "local://ly01_vasanta",
            resolution = "8-Bit Procedural HD",
            tags = listOf("8Bit", "Spring", "Vasanta", "Twilight", "Gentle Breeze", "Indian Season"),
            colorHex = 0xFF080612,
            views = 8350,
            downloads = 3910
        ),
        Wallpaper(
            id = "w6",
            title = "LY02: Cosmic Wilderness",
            author = "Lumina Engine",
            category = "Productivity & AMOLED",
            thumbnailUrl = "local://ly02_cosmic",
            fullUrl = "local://ly02_cosmic",
            resolution = "Procedural Vector HD",
            tags = listOf("AMOLED", "Productivity", "ScreenTime", "Minimalist", "OLED", "Live", "Curved Pixel"),
            colorHex = 0xFF000000,
            views = 16420,
            downloads = 8910
        )
    )

    private val categories = listOf(
        Category("cat_all", "All", "local://cat_all", 6, "All procedural code-driven wallpapers"),
        Category("cat_prod", "Productivity & AMOLED", "local://cat_prod", 1, "True OLED #000000 with live screen-time & app HUD"),
        Category("cat_amoled", "8-Bit AMOLED", "local://cat_amoled", 2, "True OLED #000000 battery-saving art"),
        Category("cat_seasons", "Indian Seasons", "local://cat_seasons", 4, "Vasanta, Varsha, Sharad, Shishira Ritus"),
        Category("cat_pixel", "Lo-Fi Pixel", "local://cat_pixel", 2, "Retro pixel aesthetic & future creations")
    )

    private fun loadFavoriteIds(): Set<String> {
        return prefs.getStringSet("fav_ids", emptySet()) ?: emptySet()
    }

    fun getFavoritesFlow(): StateFlow<Set<String>> = favoriteIds.asStateFlow()

    fun toggleFavorite(id: String) {
        val current = favoriteIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        prefs.edit().putStringSet("fav_ids", current).apply()
        favoriteIds.value = current
    }

    fun isFavorite(id: String): Boolean = favoriteIds.value.contains(id)

    fun getAllWallpapers(): List<Wallpaper> {
        val favs = favoriteIds.value
        return liveWallpapers.map { it.copy(isFavorite = favs.contains(it.id)) }
    }

    fun getWallpaperById(id: String): Wallpaper? {
        val w = liveWallpapers.find { it.id == id } ?: return null
        return w.copy(isFavorite = favoriteIds.value.contains(id))
    }

    fun getCategories(): List<Category> = categories

    fun filterWallpapers(category: String?, query: String?): List<Wallpaper> {
        val favs = favoriteIds.value
        return liveWallpapers
            .filter { w ->
                val matchesCategory = category.isNullOrEmpty() || category == "All" || w.category.equals(category, ignoreCase = true)
                val matchesQuery = query.isNullOrBlank() ||
                        w.title.contains(query, ignoreCase = true) ||
                        w.author.contains(query, ignoreCase = true) ||
                        w.category.contains(query, ignoreCase = true) ||
                        w.tags.any { it.contains(query, ignoreCase = true) }
                matchesCategory && matchesQuery
            }
            .map { it.copy(isFavorite = favs.contains(it.id)) }
    }

    fun getFavoriteWallpapers(): List<Wallpaper> {
        val favs = favoriteIds.value
        return liveWallpapers
            .filter { favs.contains(it.id) }
            .map { it.copy(isFavorite = true) }
    }
}
