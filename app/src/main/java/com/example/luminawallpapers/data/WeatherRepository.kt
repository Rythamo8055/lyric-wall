package com.example.luminawallpapers.data

import com.example.luminawallpapers.wallpaper.WeatherMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WeatherResult(
    val city: String,
    val tempC: String,
    val mode: WeatherMode,
    val conditionDescription: String
)

object WeatherRepository {

    suspend fun fetchWeatherForCity(cityName: String): Result<WeatherResult> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedCity = URLEncoder.encode(cityName.trim(), "UTF-8")
            val geoUrl = URL("https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=en&format=json")
            val geoJsonStr = httpGet(geoUrl)
            val geoJson = JSONObject(geoJsonStr)

            val results = geoJson.optJSONArray("results")
            if (results == null || results.length() == 0) {
                throw IllegalArgumentException("City '$cityName' not found")
            }

            val cityObj = results.getJSONObject(0)
            val lat = cityObj.getDouble("latitude")
            val lon = cityObj.getDouble("longitude")
            val resolvedName = cityObj.optString("name", cityName)

            fetchWeatherForCoordinates(lat, lon, resolvedName).getOrThrow()
        }
    }

    suspend fun fetchWeatherForCoordinates(lat: Double, lon: Double, fallbackCityName: String): Result<WeatherResult> = withContext(Dispatchers.IO) {
        runCatching {
            val weatherUrl = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true")
            val weatherJsonStr = httpGet(weatherUrl)
            val weatherJson = JSONObject(weatherJsonStr)
            val current = weatherJson.getJSONObject("current_weather")

            val temp = current.getDouble("temperature").toInt()
            val weatherCode = current.getInt("weathercode")

            val (mode, desc) = mapWmoCode(weatherCode)

            WeatherResult(
                city = fallbackCityName,
                tempC = "$temp°C",
                mode = mode,
                conditionDescription = desc
            )
        }
    }

    private fun mapWmoCode(code: Int): Pair<WeatherMode, String> {
        return when (code) {
            0 -> Pair(WeatherMode.CLEAR_NIGHT, "Clear Night")
            1, 2, 3 -> Pair(WeatherMode.CLOUDY, "Partly Cloudy")
            45, 48 -> Pair(WeatherMode.CLOUDY, "Foggy Mist")
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> Pair(WeatherMode.RAIN, "Pixel Rain")
            71, 73, 75, 77, 85, 86 -> Pair(WeatherMode.SNOW, "Pixel Snow")
            95, 96, 99 -> Pair(WeatherMode.THUNDER, "Thunderstorm")
            else -> Pair(WeatherMode.CLEAR_NIGHT, "Clear Sky")
        }
    }

    private fun httpGet(url: URL): String {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.setRequestProperty("User-Agent", "LuminaWallpapers/1.0")

        return try {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            reader.close()
            sb.toString()
        } finally {
            conn.disconnect()
        }
    }
}
