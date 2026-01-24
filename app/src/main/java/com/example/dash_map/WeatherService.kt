package com.example.dash_map

import org.json.JSONObject
import java.net.URL
import kotlin.math.roundToInt

object WeatherService {
    fun fetchWeather(lat: Double, lon: Double, onResult: (WeatherData) -> Unit) {
        Thread {
            try {
                val weatherUrl =
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&hourly=precipitation_probability&temperature_unit=celsius&daily=sunrise,sunset&timezone=auto"
                val weatherResponse = URL(weatherUrl).readText()
                val weatherJson = JSONObject(weatherResponse)

                val currentWeather = weatherJson.getJSONObject("current_weather")
                val temp = currentWeather.getDouble("temperature").roundToInt()
                val weatherCode = currentWeather.getInt("weathercode")
                val windSpeed = currentWeather.getDouble("windspeed").roundToInt()

                val condition = when (weatherCode) {
                    0 -> "Clear"
                    1, 2, 3 -> "Cloudy"
                    45, 48 -> "Foggy"
                    51, 53, 55, 61, 63, 65, 80, 81, 82 -> "Rainy"
                    71, 73, 75, 77, 85, 86 -> "Snowy"
                    95, 96, 99 -> "Stormy"
                    else -> "Partly Cloudy"
                }

                var rainChance = 0
                try {
                    val hourly = weatherJson.getJSONObject("hourly")
                    val precipArray = hourly.getJSONArray("precipitation_probability")
                    if (precipArray.length() > 0) {
                        rainChance = precipArray.getInt(0)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                var aqi = 0
                var aqiCategory = "Good"
                try {
                    val aqiUrl =
                        "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=us_aqi"
                    val aqiResponse = URL(aqiUrl).readText()
                    val aqiJson = JSONObject(aqiResponse)
                    val currentAqi = aqiJson.getJSONObject("current")
                    aqi = currentAqi.getInt("us_aqi")

                    aqiCategory = when {
                        aqi <= 50 -> "Good"
                        aqi <= 100 -> "Moderate"
                        aqi <= 150 -> "Unhealthy for Sensitive"
                        aqi <= 200 -> "Unhealthy"
                        aqi <= 300 -> "Very Unhealthy"
                        else -> "Hazardous"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                var city = "Your Location"
                try {
                    val geocodeUrl =
                        "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=en"
                    val geocodeResponse = URL(geocodeUrl).readText()
                    val geocodeJson = JSONObject(geocodeResponse)

                    city = geocodeJson.optString("city")
                    if (city.isEmpty() || city == "null") {
                        city = geocodeJson.optString("locality")
                    }
                    if (city.isEmpty() || city == "null") {
                        city = geocodeJson.optString("principalSubdivision", "Your Location")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                onResult(
                    WeatherData(
                        temp,
                        city,
                        condition,
                        windSpeed,
                        aqi,
                        aqiCategory,
                        rainChance
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}

data class WeatherData(
    val temperature: Int,
    val city: String,
    val condition: String,
    val windSpeed: Int,
    val aqi: Int = 0,
    val aqiCategory: String = "Good",
    val rainChance: Int = 0
)