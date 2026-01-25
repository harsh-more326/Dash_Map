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

@Composable
fun WeatherWidget(
    weather: WeatherData?,
    modifier: Modifier = Modifier
) {
    val displayWeather = weather ?: WeatherData(14, "Locating...", "Cloudy", 0, 0, "Good", 0)

    val currentDate = remember {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
        dateFormat.format(calendar.time)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF5E72A8).copy(alpha = 0.5f),
                        Color(0xFF4A5C8C).copy(alpha = 0.4f)
                    )
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
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Middle - Wind speed, AQI, and Rain chance
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
                        imageVector = Icons.Default.Air,
                        contentDescription = "Wind",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "${displayWeather.windSpeed} km/h",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (displayWeather.aqi > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
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
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "AQI ${displayWeather.aqi}",
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = "Rain",
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "${displayWeather.rainChance}% rain",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Right side - Icon and Condition
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
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

// TODO : after dark the sunny should be switched to the current moon phase with the condition on top like if its raining with a full moon aur cloudy with a half moon