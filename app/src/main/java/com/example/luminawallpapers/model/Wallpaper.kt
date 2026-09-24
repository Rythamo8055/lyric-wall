package com.example.luminawallpapers.model

data class Wallpaper(
    val id: String,
    val title: String,
    val author: String,
    val category: String,
    val thumbnailUrl: String,
    val fullUrl: String,
    val resolution: String,
    val tags: List<String>,
    val colorHex: Long = 0xFF1E1E2E,
    val isFavorite: Boolean = false,
    val views: Int = 1240,
    val downloads: Int = 340
)

data class Category(
    val id: String,
    val name: String,
    val coverUrl: String,
    val wallpaperCount: Int,
    val description: String
)
