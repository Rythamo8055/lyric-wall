package com.example.luminawallpapers

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object MainRoute : NavKey

@Serializable
data class LiveWallpaperRoute(val wallpaperId: String = "w1") : NavKey

@Serializable
data class DetailRoute(val wallpaperId: String) : NavKey
