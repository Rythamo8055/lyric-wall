package com.example.luminawallpapers.data

import android.content.Context
import android.content.SharedPreferences
import com.example.luminawallpapers.util.IndianSeason
import com.example.luminawallpapers.util.IndianSeasonHelper
import com.example.luminawallpapers.util.LunarPhaseHelper
import com.example.luminawallpapers.wallpaper.WeatherMode

class LiveWallpaperSettings(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("lumina_live_settings", Context.MODE_PRIVATE)

    var showClock: Boolean
        get() = prefs.getBoolean("show_clock", true)
        set(value) = prefs.edit().putBoolean("show_clock", value).apply()

    var showDate: Boolean
        get() = prefs.getBoolean("show_date", true)
        set(value) = prefs.edit().putBoolean("show_date", value).apply()

    var showBattery: Boolean
        get() = prefs.getBoolean("show_battery", true)
        set(value) = prefs.edit().putBoolean("show_battery", value).apply()

    var showWeatherTag: Boolean
        get() = prefs.getBoolean("show_weather_tag", true)
        set(value) = prefs.edit().putBoolean("show_weather_tag", value).apply()

    var showIndianSeason: Boolean
        get() = prefs.getBoolean("show_indian_season", true)
        set(value) = prefs.edit().putBoolean("show_indian_season", value).apply()

    var indianSeasonMode: String // "AUTO", "VASANTA", "GRISHMA", "VARSHA", "SHARAD", "HEMANTA", "SHISHIRA"
        get() = prefs.getString("indian_season_mode", "AUTO") ?: "AUTO"
        set(value) = prefs.edit().putString("indian_season_mode", value).apply()

    val currentIndianSeason: IndianSeason
        get() {
            val mode = indianSeasonMode
            if (mode == "AUTO") {
                return IndianSeasonHelper.getCurrentSeason()
            }
            return try {
                IndianSeason.valueOf(mode)
            } catch (e: Exception) {
                IndianSeasonHelper.getCurrentSeason()
            }
        }

    var moonSizeScale: Float
        get() = prefs.getFloat("moon_size_scale", 1.0f)
        set(value) = prefs.edit().putFloat("moon_size_scale", value).apply()

    var autoLunarPhase: Boolean
        get() = prefs.getBoolean("auto_lunar_phase", true)
        set(value) = prefs.edit().putBoolean("auto_lunar_phase", value).apply()

    var lunarPhaseFraction: Float
        get() = if (autoLunarPhase) LunarPhaseHelper.getCurrentLunarPhase() else prefs.getFloat("lunar_phase_frac", 0.5f)
        set(value) = prefs.edit().putFloat("lunar_phase_frac", value).apply()

    var autoWeatherEnabled: Boolean
        get() = prefs.getBoolean("auto_weather", true)
        set(value) = prefs.edit().putBoolean("auto_weather", value).apply()

    var useGpsLocation: Boolean
        get() = prefs.getBoolean("use_gps_location", true)
        set(value) = prefs.edit().putBoolean("use_gps_location", value).apply()

    var cityName: String
        get() = prefs.getString("city_name", "Hyderabad") ?: "Hyderabad"
        set(value) = prefs.edit().putString("city_name", value).apply()

    var currentTemp: String
        get() = prefs.getString("current_temp", "22°C") ?: "22°C"
        set(value) = prefs.edit().putString("current_temp", value).apply()

    var weatherMode: WeatherMode
        get() {
            val name = prefs.getString("weather_mode", WeatherMode.CLEAR_NIGHT.name)
            return try {
                WeatherMode.valueOf(name ?: WeatherMode.CLEAR_NIGHT.name)
            } catch (e: Exception) {
                WeatherMode.CLEAR_NIGHT
            }
        }
        set(value) = prefs.edit().putString("weather_mode", value.name).apply()

    var cloudSpeedScale: Float
        get() = prefs.getFloat("cloud_speed", 1.0f)
        set(value) = prefs.edit().putFloat("cloud_speed", value).apply()

    var starSpeedScale: Float
        get() = prefs.getFloat("star_speed", 1.0f)
        set(value) = prefs.edit().putFloat("star_speed", value).apply()

    var hudPosition: String
        get() = prefs.getString("hud_pos", "LEFT_RAIL") ?: "LEFT_RAIL"
        set(value) = prefs.edit().putString("hud_pos", value).apply()

    var activeWallpaperId: String
        get() = prefs.getString("active_wallpaper_id", "ly01_celestial") ?: "ly01_celestial"
        set(value) = prefs.edit().putString("active_wallpaper_id", value).apply()

    var homeWallpaperId: String
        get() = prefs.getString("home_wallpaper_id", activeWallpaperId) ?: activeWallpaperId
        set(value) = prefs.edit().putString("home_wallpaper_id", value).apply()

    var lockWallpaperId: String
        get() = prefs.getString("lock_wallpaper_id", "ly01_celestial") ?: "ly01_celestial"
        set(value) = prefs.edit().putString("lock_wallpaper_id", value).apply()

    var showProductivityHud: Boolean
        get() = prefs.getBoolean("show_productivity_hud", true)
        set(value) = prefs.edit().putBoolean("show_productivity_hud", value).apply()

    var showScreenTime: Boolean
        get() = prefs.getBoolean("show_screen_time", true)
        set(value) = prefs.edit().putBoolean("show_screen_time", value).apply()

    var showUnlocks: Boolean
        get() = prefs.getBoolean("show_unlocks", true)
        set(value) = prefs.edit().putBoolean("show_unlocks", value).apply()

    var showTopApps: Boolean
        get() = prefs.getBoolean("show_top_apps", true)
        set(value) = prefs.edit().putBoolean("show_top_apps", value).apply()

    var ultraBatterySaver: Boolean
        get() = prefs.getBoolean("ultra_battery_saver", false)
        set(value) = prefs.edit().putBoolean("ultra_battery_saver", value).apply()

    // RY01 Radiant Dawn & Productivity settings
    var ry01Theme: String
        get() = prefs.getString("ry01_theme", "COZY_PEACH") ?: "COZY_PEACH"
        set(value) = prefs.edit().putString("ry01_theme", value).apply()

    var ry01WaterGlasses: Int
        get() = prefs.getInt("ry01_water_glasses", 4)
        set(value) = prefs.edit().putInt("ry01_water_glasses", value).apply()

    var ry01WaterGoal: Int
        get() = prefs.getInt("ry01_water_goal", 8)
        set(value) = prefs.edit().putInt("ry01_water_goal", value).apply()

    var ry01WaterDate: String
        get() = prefs.getString("ry01_water_date", "") ?: ""
        set(value) = prefs.edit().putString("ry01_water_date", value).apply()

    var ry01CountdownLabel: String
        get() = prefs.getString("ry01_countdown_label", "BIRTHDAY") ?: "BIRTHDAY"
        set(value) = prefs.edit().putString("ry01_countdown_label", value).apply()

    var ry01CountdownDays: Int
        get() = prefs.getInt("ry01_countdown_days", 14)
        set(value) = prefs.edit().putInt("ry01_countdown_days", value).apply()

    var ry01TelemetryMode: Int // 0: UV Index, 1: AQI, 2: Rain %
        get() = prefs.getInt("ry01_telemetry_mode", 0)
        set(value) = prefs.edit().putInt("ry01_telemetry_mode", value).apply()

    fun checkAndResetRy01Water() {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        if (ry01WaterDate != today) {
            ry01WaterDate = today
            ry01WaterGlasses = 0
        }
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}

