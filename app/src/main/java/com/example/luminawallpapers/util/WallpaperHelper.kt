package com.example.luminawallpapers.util

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.luminawallpapers.data.LiveWallpaperSettings
import com.example.luminawallpapers.wallpaper.CelestialPixelRenderer
import com.example.luminawallpapers.wallpaper.CosmicWildernessRenderer
import com.example.luminawallpapers.wallpaper.RY01Renderer
import com.example.luminawallpapers.wallpaper.WeatherMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class WallpaperTarget {
    HOME,
    LOCK,
    BOTH
}

object WallpaperHelper {

    fun isLy02(wallpaperId: String): Boolean {
        val lower = wallpaperId.lowercase()
        return lower == "w6" || lower.contains("ly02") || lower.contains("cosmic")
    }

    fun isRy01(wallpaperId: String): Boolean {
        val lower = wallpaperId.lowercase()
        return lower == "w7" || lower.contains("ry01") || lower.contains("dawn")
    }

    fun resolveUri(wallpaperId: String): String {
        val lower = wallpaperId.lowercase()
        return when {
            isLy02(wallpaperId) -> "local://ly02_cosmic"
            isRy01(wallpaperId) -> "local://ry01_dawn"
            lower.contains("varsha") || lower == "w2" -> "local://ly01_varsha"
            lower.contains("sharad") || lower == "w3" -> "local://ly01_sharad"
            lower.contains("shishira") || lower == "w4" -> "local://ly01_shishira"
            lower.contains("vasanta") || lower == "w5" -> "local://ly01_vasanta"
            wallpaperId.startsWith("local://") -> wallpaperId
            else -> "local://ly01_celestial"
        }
    }

    fun activateLiveWallpaper(
        context: Context,
        wallpaperId: String,
        target: WallpaperTarget = WallpaperTarget.HOME
    ) {
        val settings = LiveWallpaperSettings(context)
        val resolvedId = when {
            isLy02(wallpaperId) -> "ly02_cosmic"
            isRy01(wallpaperId) -> "ry01_dawn"
            wallpaperId.contains("varsha") || wallpaperId == "w2" -> "ly01_varsha"
            wallpaperId.contains("sharad") || wallpaperId == "w3" -> "ly01_sharad"
            wallpaperId.contains("shishira") || wallpaperId == "w4" -> "ly01_shishira"
            wallpaperId.contains("vasanta") || wallpaperId == "w5" -> "ly01_vasanta"
            else -> "ly01_celestial"
        }

        settings.activeWallpaperId = resolvedId

        when (target) {
            WallpaperTarget.HOME -> {
                settings.homeWallpaperId = resolvedId
                // Preserve lock screen if static or if independent
                try {
                    val wm = WallpaperManager.getInstance(context)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val lockId = wm.getWallpaperId(WallpaperManager.FLAG_LOCK)
                        if (lockId < 0) {
                            val drawable = wm.drawable
                            if (drawable != null) {
                                val bmp = drawableToBitmap(drawable, 1080, 2400)
                                wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            WallpaperTarget.LOCK -> {
                settings.lockWallpaperId = resolvedId
            }
            WallpaperTarget.BOTH -> {
                settings.homeWallpaperId = resolvedId
                settings.lockWallpaperId = resolvedId
            }
        }

        val lower = resolvedId.lowercase()
        when {
            lower.contains("varsha") -> {
                settings.indianSeasonMode = "VARSHA"
                settings.weatherMode = WeatherMode.RAIN
            }
            lower.contains("sharad") -> {
                settings.indianSeasonMode = "SHARAD"
                settings.weatherMode = WeatherMode.CLEAR_NIGHT
            }
            lower.contains("shishira") -> {
                settings.indianSeasonMode = "SHISHIRA"
                settings.weatherMode = WeatherMode.SNOW
            }
            lower.contains("vasanta") -> {
                settings.indianSeasonMode = "VASANTA"
                settings.weatherMode = WeatherMode.CLEAR_NIGHT
            }
        }
    }

    suspend fun applyWallpaper(
        context: Context,
        imageUrl: String,
        target: WallpaperTarget
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = if (imageUrl.startsWith("local://")) {
                val dm = context.resources.displayMetrics
                val width = dm.widthPixels.coerceAtLeast(1080)
                val height = dm.heightPixels.coerceAtLeast(1920)
                renderProceduralBitmap(context, imageUrl, width, height)
            } else {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false)
                    .build()

                val result = loader.execute(request)
                if (result !is SuccessResult) {
                    throw IllegalStateException("Failed to download image from network")
                }

                val drawable = result.drawable
                (drawable as? BitmapDrawable)?.bitmap
                    ?: throw IllegalStateException("Could not decode image to bitmap")
            }

            applyBitmapWallpaper(context, bitmap, target).getOrThrow()
        }
    }

    fun renderProceduralBitmap(context: Context, uri: String, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val settings = LiveWallpaperSettings(context)

        if (uri.contains("ly02") || uri.contains("cosmic")) {
            val cosmic = CosmicWildernessRenderer(context)
            cosmic.showProductivityHud = settings.showProductivityHud
            cosmic.showScreenTime = settings.showScreenTime
            cosmic.showUnlocks = settings.showUnlocks
            cosmic.showTopApps = settings.showTopApps
            cosmic.lunarPhase = if (settings.autoLunarPhase) -1f else settings.lunarPhaseFraction
            cosmic.refreshProductivityStats()
            cosmic.update(0.1f)
            cosmic.draw(canvas, width, height, System.currentTimeMillis())
        } else if (isRy01(uri)) {
            val ry01 = RY01Renderer(context)
            ry01.render(canvas, width, height)
        } else {
            val celestial = CelestialPixelRenderer()
            celestial.moonScale = settings.moonSizeScale
            celestial.lunarPhase = settings.lunarPhaseFraction
            celestial.showClock = settings.showClock
            celestial.showDate = settings.showDate
            celestial.showBattery = settings.showBattery
            celestial.showWeatherTag = settings.showWeatherTag
            celestial.showIndianSeason = settings.showIndianSeason
            celestial.cityName = settings.cityName
            celestial.currentTemp = settings.currentTemp
            celestial.hudPosition = settings.hudPosition

            when {
                uri.contains("varsha") -> celestial.weatherMode = WeatherMode.RAIN
                uri.contains("shishira") -> celestial.weatherMode = WeatherMode.SNOW
                uri.contains("vasanta") -> celestial.weatherMode = WeatherMode.CLEAR_NIGHT
                uri.contains("sharad") -> celestial.weatherMode = WeatherMode.CLEAR_NIGHT
                else -> celestial.weatherMode = settings.weatherMode
            }

            celestial.render(canvas, width, height)
        }
        return bitmap
    }

    suspend fun applyBitmapWallpaper(
        context: Context,
        bitmap: Bitmap,
        target: WallpaperTarget
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val wallpaperManager = WallpaperManager.getInstance(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                when (target) {
                    WallpaperTarget.HOME -> {
                        // CRITICAL FIX FOR ANDROID WALLPAPER GLITCH:
                        // On Android, if the Lock Screen does not have an independent wallpaper (lockId < 0),
                        // setting FLAG_SYSTEM will automatically overwrite the Lock Screen too.
                        // To prevent this, we pin the current wallpaper onto FLAG_LOCK first before updating Home.
                        try {
                            val lockWallpaperId = wallpaperManager.getWallpaperId(WallpaperManager.FLAG_LOCK)
                            if (lockWallpaperId < 0) {
                                val currentDrawable = wallpaperManager.drawable
                                if (currentDrawable != null) {
                                    val currentBmp = drawableToBitmap(
                                        currentDrawable,
                                        bitmap.width,
                                        bitmap.height
                                    )
                                    wallpaperManager.setBitmap(
                                        currentBmp,
                                        null,
                                        true,
                                        WallpaperManager.FLAG_LOCK
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            // Fallback if OS restricts reading current wallpaper
                        }

                        // Safely set ONLY the Home screen wallpaper
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    }
                    WallpaperTarget.LOCK -> {
                        // Set ONLY the Lock screen wallpaper
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                    }
                    WallpaperTarget.BOTH -> {
                        wallpaperManager.setBitmap(
                            bitmap,
                            null,
                            true,
                            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                        )
                    }
                }
            } else {
                wallpaperManager.setBitmap(bitmap)
            }
            Unit
        }
    }

    private fun drawableToBitmap(
        drawable: android.graphics.drawable.Drawable,
        fallbackWidth: Int,
        fallbackHeight: Int
    ): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else fallbackWidth
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else fallbackHeight
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1080), h.coerceAtLeast(1920), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }

    fun shareWallpaper(context: Context, title: String, url: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Check out this wallpaper: $title")
            putExtra(
                Intent.EXTRA_TEXT,
                "Check out '$title' wallpaper from Lumina Wallpapers:\n$url"
            )
        }
        context.startActivity(Intent.createChooser(intent, "Share Wallpaper via"))
    }
}
