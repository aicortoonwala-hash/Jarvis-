package com.aicortoonwala.jarvis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class WeatherClient {
    suspend fun current(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 10000
            val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val current = json.getJSONObject("current")
            val temp = current.getDouble("temperature_2m")
            val humidity = current.getInt("relative_humidity_2m")
            val wind = current.getDouble("wind_speed_10m")
            "Current weather: $temp°C, humidity $humidity%, wind $wind km/h."
        } catch (e: Exception) {
            "Weather service is unavailable right now."
        }
    }
}
