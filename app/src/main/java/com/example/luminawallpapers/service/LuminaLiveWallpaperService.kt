package com.example.luminawallpapers.service

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.example.luminawallpapers.data.LiveWallpaperSettings
import com.example.luminawallpapers.data.WeatherRepository
import com.example.luminawallpapers.util.LocationHelper
import com.example.luminawallpapers.util.UsageStatsHelper
import com.example.luminawallpapers.util.WallpaperHelper
import com.example.luminawallpapers.wallpaper.CelestialPixelRenderer
import com.example.luminawallpapers.wallpaper.CosmicWildernessRenderer
import com.example.luminawallpapers.wallpaper.WeatherMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LuminaLiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return CelestialEngine()
    }

    inner class CelestialEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener, Choreographer.FrameCallback {

        private val celestialRenderer = CelestialPixelRenderer()
        private val cosmicRenderer = CosmicWildernessRenderer(this@LuminaLiveWallpaperService)
        private val choreographer = Choreographer.getInstance()
        private lateinit var settings: LiveWallpaperSettings
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        private var isVisible = false
        private var isFrameScheduled = false
        private var width = 1080
        private var height = 2400
        private var engineFlags: Int = WallpaperManager.FLAG_SYSTEM

        private val userPresentReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_USER_PRESENT -> {
                        context?.let {
                            UsageStatsHelper.incrementUnlockCount(it)
                            cosmicRenderer.refreshProductivityStats()
                            applySettingsToRenderer()
                            drawFrame()
                        }
                    }
                    Intent.ACTION_SCREEN_ON,
                    Intent.ACTION_SCREEN_OFF -> {
                        applySettingsToRenderer()
                        drawFrame()
                    }
                }
            }
        }

        private val timeMidnightReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                context?.let { ctx ->
                    when (intent?.action) {
                        Intent.ACTION_DATE_CHANGED,
                        Intent.ACTION_TIME_CHANGED,
                        Intent.ACTION_TIME_TICK -> {
                            val resetOccurred = UsageStatsHelper.checkAndResetDailyStatsAtMidnight(ctx)
                            if (resetOccurred || intent.action != Intent.ACTION_TIME_TICK) {
                                cosmicRenderer.refreshProductivityStats()
                                drawFrame()
                            }
                        }
                    }
                }
            }
        }

        private val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        celestialRenderer.batteryPercent = (level * 100) / scale
                    }
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    celestialRenderer.isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                try {
                    engineFlags = wallpaperFlags
                } catch (e: Throwable) {
                    // Fallback
                }
            }
            settings = LiveWallpaperSettings(this@LuminaLiveWallpaperService)
            settings.registerListener(this)
            applySettingsToRenderer()

            try {
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                registerReceiver(batteryReceiver, filter)
            } catch (e: Exception) {
                // Ignore
            }

            try {
                val presentFilter = IntentFilter().apply {
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }
                registerReceiver(userPresentReceiver, presentFilter)
            } catch (e: Exception) {
                // Ignore
            }

            try {
                val timeFilter = IntentFilter().apply {
                    addAction(Intent.ACTION_DATE_CHANGED)
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIME_TICK)
                }
                registerReceiver(timeMidnightReceiver, timeFilter)
            } catch (e: Exception) {
                // Ignore
            }

            syncRealWeather()
        }

        override fun onWallpaperFlagsChanged(which: Int) {
            super.onWallpaperFlagsChanged(which)
            engineFlags = which
            applySettingsToRenderer()
            drawFrame()
        }

        private fun getEffectiveWallpaperId(): String {
            if (isPreview) {
                return settings.activeWallpaperId
            }
            val isLock = (engineFlags and WallpaperManager.FLAG_LOCK) != 0
            val isSystem = (engineFlags and WallpaperManager.FLAG_SYSTEM) != 0
            return when {
                isLock && !isSystem -> settings.lockWallpaperId
                isSystem && !isLock -> settings.homeWallpaperId
                else -> {
                    // Unified engine or both flags set: check keyguard locked status
                    val km = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                    if (km?.isKeyguardLocked == true) {
                        settings.lockWallpaperId
                    } else {
                        settings.homeWallpaperId
                    }
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            stopAnimation()
            settings.unregisterListener(this)
            scope.cancel()
            try {
                unregisterReceiver(batteryReceiver)
            } catch (e: Exception) {
                // Ignore
            }
            try {
                unregisterReceiver(userPresentReceiver)
            } catch (e: Exception) {
                // Ignore
            }
            try {
                unregisterReceiver(timeMidnightReceiver)
            } catch (e: Exception) {
                // Ignore
            }
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            applySettingsToRenderer()
            drawFrame()
        }

        private fun applySettingsToRenderer() {
            val activeId = getEffectiveWallpaperId()
            if (WallpaperHelper.isLy02(activeId)) {
                // Configure LY02 Cosmic Wilderness
                cosmicRenderer.showProductivityHud = settings.showProductivityHud
                cosmicRenderer.showScreenTime = settings.showScreenTime
                cosmicRenderer.showUnlocks = settings.showUnlocks
                cosmicRenderer.showTopApps = settings.showTopApps
                cosmicRenderer.starSpeedScale = settings.starSpeedScale
                cosmicRenderer.refreshProductivityStats()
            } else {
                // Configure LY01 variants & seasons
                val lower = activeId.lowercase()
                when {
                    lower.contains("varsha") -> {
                        celestialRenderer.indianSeasonName = "VARSHA"
                        celestialRenderer.weatherMode = WeatherMode.RAIN
                    }
                    lower.contains("shishira") -> {
                        celestialRenderer.indianSeasonName = "SHISHIRA"
                        celestialRenderer.weatherMode = WeatherMode.SNOW
                    }
                    lower.contains("vasanta") -> {
                        celestialRenderer.indianSeasonName = "VASANTA"
                        celestialRenderer.weatherMode = WeatherMode.CLEAR_NIGHT
                    }
                    lower.contains("sharad") -> {
                        celestialRenderer.indianSeasonName = "SHARAD"
                        celestialRenderer.weatherMode = WeatherMode.CLEAR_NIGHT
                    }
                    else -> {
                        celestialRenderer.weatherMode = settings.weatherMode
                        celestialRenderer.indianSeasonName = settings.currentIndianSeason.name
                    }
                }
                celestialRenderer.moonScale = settings.moonSizeScale
                celestialRenderer.lunarPhase = settings.lunarPhaseFraction
                celestialRenderer.showClock = settings.showClock
                celestialRenderer.showDate = settings.showDate
                celestialRenderer.showBattery = settings.showBattery
                celestialRenderer.showWeatherTag = settings.showWeatherTag
                celestialRenderer.showIndianSeason = settings.showIndianSeason
                celestialRenderer.cityName = settings.cityName
                celestialRenderer.currentTemp = settings.currentTemp
                celestialRenderer.hudPosition = settings.hudPosition
                celestialRenderer.cloudSpeedScale = settings.cloudSpeedScale
                celestialRenderer.starSpeedScale = settings.starSpeedScale
            }
        }

        private fun syncRealWeather() {
            if (settings.autoWeatherEnabled) {
                scope.launch {
                    if (settings.useGpsLocation) {
                        val loc = LocationHelper.getBestLocation(this@LuminaLiveWallpaperService)
                        if (loc != null) {
                            val result = WeatherRepository.fetchWeatherForCoordinates(
                                loc.latitude,
                                loc.longitude,
                                settings.cityName
                            )
                            result.onSuccess { weather ->
                                settings.cityName = weather.city
                                settings.currentTemp = weather.tempC
                                settings.weatherMode = weather.mode
                                applySettingsToRenderer()
                                drawFrame()
                                return@launch
                            }
                        }
                    }

                    // Fallback to city name
                    val result = WeatherRepository.fetchWeatherForCity(settings.cityName)
                    result.onSuccess { weather ->
                        settings.cityName = weather.city
                        settings.currentTemp = weather.tempC
                        settings.weatherMode = weather.mode
                        applySettingsToRenderer()
                        drawFrame()
                    }
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            if (visible) {
                applySettingsToRenderer()
                syncRealWeather()
                startAnimation()
            } else {
                stopAnimation() // 0.0% CPU offscreen for ultra low power
            }
        }

        private var boostUntilTime = 0L

        private fun startAnimation() {
            if (!isFrameScheduled) {
                isFrameScheduled = true
                choreographer.postFrameCallback(this)
            }
        }

        private fun stopAnimation() {
            isFrameScheduled = false
            choreographer.removeFrameCallback(this)
            frameHandler.removeCallbacksAndMessages(null)
        }

        private val frameHandler = android.os.Handler(android.os.Looper.getMainLooper())

        override fun doFrame(frameTimeNanos: Long) {
            if (!isVisible) {
                isFrameScheduled = false
                return
            }

            val now = System.currentTimeMillis()
            val isTouchBoosted = now < boostUntilTime

            drawFrame()

            // Eco Pacing: ~30 FPS (33ms) for silky-smooth ambient live wallpaper drift, 60 FPS burst on direct touch
            if (isTouchBoosted) {
                choreographer.postFrameCallback(this)
            } else {
                frameHandler.postDelayed({
                    if (isVisible) {
                        choreographer.postFrameCallback(this)
                    } else {
                        isFrameScheduled = false
                    }
                }, 33L)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            drawFrame()
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            event?.let {
                if (it.action == MotionEvent.ACTION_DOWN) {
                    boostUntilTime = System.currentTimeMillis() + 3000L // 3s 60 FPS burst on touch
                    val effectiveId = getEffectiveWallpaperId()
                    if (WallpaperHelper.isLy02(effectiveId)) {
                        cosmicRenderer.onTouch(it.x, it.y, width, height)
                    } else {
                        val nx = it.x / width.toFloat()
                        val ny = it.y / height.toFloat()
                        celestialRenderer.onTouch(nx, ny)
                        settings.weatherMode = celestialRenderer.weatherMode
                    }
                    drawFrame()
                }
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            val canvas = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }
            } catch (e: Exception) {
                try { holder.lockCanvas() } catch (e2: Exception) { null }
            } ?: return

            try {
                val effectiveId = getEffectiveWallpaperId()
                if (WallpaperHelper.isLy02(effectiveId)) {
                    cosmicRenderer.update(0.033f)
                    cosmicRenderer.draw(canvas, width, height, System.currentTimeMillis())
                } else {
                    celestialRenderer.render(canvas, width, height)
                }
            } finally {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
}
