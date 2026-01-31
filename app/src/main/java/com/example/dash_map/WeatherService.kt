package com.example.dash_map

import org.json.JSONObject
import java.net.URL
import kotlin.math.roundToInt

object WeatherService {
    fun fetchWeather(lat: Double, lon: Double, onResult: (WeatherData) -> Unit) {
        Thread {
            try {
                // Fetch weather data with visibility
                val weatherUrl =
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,weathercode,windspeed_10m,visibility&temperature_unit=celsius&timezone=auto"
                val weatherResponse = URL(weatherUrl).readText()
                val weatherJson = JSONObject(weatherResponse)

                val current = weatherJson.getJSONObject("current")
                val temp = current.getDouble("temperature_2m").roundToInt()
                val weatherCode = current.getInt("weathercode")
                val windSpeed = current.getDouble("windspeed_10m").roundToInt()
                val humidity = current.getInt("relative_humidity_2m")

                // Get visibility in meters, convert to km
                val visibilityMeters = current.optDouble("visibility", 10000.0)
                val visibility = (visibilityMeters / 1000.0).roundToInt()

                val condition = when (weatherCode) {
                    0 -> "Clear"
                    1, 2, 3 -> "Cloudy"
                    45, 48 -> "Foggy"
                    51, 53, 55, 61, 63, 65, 80, 81, 82 -> "Rainy"
                    71, 73, 75, 77, 85, 86 -> "Snowy"
                    95, 96, 99 -> "Stormy"
                    else -> "Partly Cloudy"
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

                // Improved city name fetching with multiple fallbacks
                var city = "Your Location"
                try {
                    // Try Nominatim (OpenStreetMap) first - more accurate
                    val nominatimUrl =
                        "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=10&addressdetails=1"
                    val nominatimResponse = URL(nominatimUrl).apply {
                        openConnection().apply {
                            setRequestProperty("User-Agent", "DashMap/1.0")
                        }
                    }.readText()
                    val nominatimJson = JSONObject(nominatimResponse)

                    if (nominatimJson.has("address")) {
                        val address = nominatimJson.getJSONObject("address")

                        // Try to get city from various fields in order of preference
                        city = address.optString("city")
                        if (city.isEmpty() || city == "null") {
                            city = address.optString("town")
                        }
                        if (city.isEmpty() || city == "null") {
                            city = address.optString("village")
                        }
                        if (city.isEmpty() || city == "null") {
                            city = address.optString("municipality")
                        }
                        if (city.isEmpty() || city == "null") {
                            city = address.optString("suburb")
                        }
                        if (city.isEmpty() || city == "null") {
                            city = address.optString("county")
                        }
                        if (city.isEmpty() || city == "null") {
                            city = address.optString("state_district")
                        }
                        if (city.isEmpty() || city == "null") {
                            city = address.optString("state")
                        }
                    }

                    // If still empty, try display_name
                    if (city.isEmpty() || city == "null") {
                        val displayName = nominatimJson.optString("display_name", "")
                        if (displayName.isNotEmpty()) {
                            // Take first part of display name (usually the locality)
                            city = displayName.split(",").firstOrNull()?.trim() ?: "Your Location"
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()

                    // Fallback to BigDataCloud if Nominatim fails
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
                            city = geocodeJson.optString("principalSubdivision")
                        }
                        if (city.isEmpty() || city == "null") {
                            city = geocodeJson.optString("countryName", "Your Location")
                        }
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }

                // Final cleanup - if still empty or null, use default
                if (city.isEmpty() || city == "null") {
                    city = "Your Location"
                }

                onResult(
                    WeatherData(
                        temperature = temp,
                        city = city,
                        condition = condition,
                        windSpeed = windSpeed,
                        aqi = aqi,
                        aqiCategory = aqiCategory,
                        humidity = humidity,
                        visibility = visibility
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
                // Return default data on error
                onResult(
                    WeatherData(
                        temperature = 0,
                        city = "Location Error",
                        condition = "Unknown",
                        windSpeed = 0,
                        aqi = 0,
                        aqiCategory = "Good",
                        humidity = 0,
                        visibility = 0
                    )
                )
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
    val humidity: Int = 0,
    val visibility: Int = 10 // in kilometers
)