package com.example.dash_map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.floor

// Moon phase calculation
fun getMoonPhase(calendar: Calendar): String {
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    var y = year
    var m = month

    if (m < 3) {
        y--
        m += 12
    }

    val a = floor(y / 100.0)
    val b = floor(a / 4.0)
    val c = 2 - a + b
    val e = floor(365.25 * (y + 4716))
    val f = floor(30.6001 * (m + 1))
    val jd = c + day + e + f - 1524.5

    val daysSinceNew = (jd - 2451549.5) % 29.53

    return when {
        daysSinceNew < 1.84566 -> "New Moon"
        daysSinceNew < 7.38264 -> "Waxing Crescent"
        daysSinceNew < 9.22830 -> "First Quarter"
        daysSinceNew < 14.76528 -> "Waxing Gibbous"
        daysSinceNew < 16.61094 -> "Full Moon"
        daysSinceNew < 22.14792 -> "Waning Gibbous"
        daysSinceNew < 23.99358 -> "Last Quarter"
        daysSinceNew < 29.53 -> "Waning Crescent"
        else -> "New Moon"
    }
}

fun getMoonEmoji(phase: String): String {
    return when (phase) {
        "New Moon" -> "🌑"
        "Waxing Crescent" -> "🌒"
        "First Quarter" -> "🌓"
        "Waxing Gibbous" -> "🌔"
        "Full Moon" -> "🌕"
        "Waning Gibbous" -> "🌖"
        "Last Quarter" -> "🌗"
        "Waning Crescent" -> "🌘"
        else -> "🌑"
    }
}

@Composable
fun WeatherWidget(
    weather: WeatherData?,
    modifier: Modifier = Modifier
) {
    val displayWeather = weather ?: WeatherData(14, "Locating...", "Cloudy", 0, 0, "Good", 0, 10)

    val (currentDate, isNightTime, moonPhase) = remember {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
        val date = dateFormat.format(calendar.time)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val isNight = hour >= 19 || hour < 6 // After 7 PM (19:00) or before 6 AM
        val phase = getMoonPhase(calendar)
        Triple(date, isNight, phase)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = if (isNightTime) {
                        listOf(
                            Color(0xFF1A237E).copy(alpha = 0.6f),
                            Color(0xFF0D1B4D).copy(alpha = 0.5f)
                        )
                    } else {
                        listOf(
                            Color(0xFF5E72A8).copy(alpha = 0.5f),
                            Color(0xFF4A5C8C).copy(alpha = 0.4f)
                        )
                    }
                )
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left side - City, Date and Temperature
            Column(
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = displayWeather.city,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = currentDate,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                )

                Text(
                    text = "${displayWeather.temperature}°",
                    color = Color.White,
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Middle - Wind speed, AQI, Humidity, and Visibility
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = "AQI",
                            tint = when (displayWeather.aqiCategory) {
                                "Good" -> Color(0xFF00E676)
                                "Moderate" -> Color(0xFFFFEB3B)
                                "Unhealthy for Sensitive" -> Color(0xFFFF9800)
                                "Unhealthy" -> Color(0xFFF44336)
                                "Very Unhealthy" -> Color(0xFFE91E63)
                                else -> Color(0xFF9C27B0)
                            },
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "AQI ${displayWeather.aqi}",
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                // Humidity
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = "Humidity",
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "${displayWeather.humidity}%",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Visibility
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Visibility",
                        tint = Color(0xFFAB47BC),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "${displayWeather.visibility} km",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Right side - Icon and Condition (with moon phase at night)
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                if (isNightTime) {
                  Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.Center
                  ){
                      // Show moon phase and weather condition
                      Text(
                          text = getMoonEmoji(moonPhase),
                          fontSize = 60.sp
                      )
                  }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ){
                        // Show weather condition overlay icon if not clear
                        if (displayWeather.condition != "Clear") {
                            Icon(
                                imageVector = when (displayWeather.condition) {
                                    "Rainy" -> Icons.Default.WaterDrop
                                    "Snowy" -> Icons.Default.AcUnit
                                    "Stormy" -> Icons.Default.Thunderstorm
                                    "Foggy" -> Icons.Default.Cloud
                                    else -> Icons.Default.Cloud
                                },
                                contentDescription = displayWeather.condition,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                } else {
                    // Daytime - show sun and weather
                    Icon(
                        imageVector = when (displayWeather.condition) {
                            "Clear" -> Icons.Default.WbSunny
                            "Rainy" -> Icons.Default.WaterDrop
                            "Snowy" -> Icons.Default.AcUnit
                            "Stormy" -> Icons.Default.Thunderstorm
                            "Foggy" -> Icons.Default.Cloud
                            else -> Icons.Default.Cloud
                        },
                        contentDescription = displayWeather.condition,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(64.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = displayWeather.condition,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

