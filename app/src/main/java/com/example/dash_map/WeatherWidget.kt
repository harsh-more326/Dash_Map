package com.example.dash_map

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeatherWidget(
    weather: WeatherData?,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(200.dp)

) {
    val displayWeather = weather ?: WeatherData(
        14, "Locating...", "Cloudy", 0, 0, "Good", 0, 10
    )

    var showSettings by remember { mutableStateOf(false) }
    var prefs by remember {
        mutableStateOf(
            WeatherDisplayPrefs()
        )
    }

    val calendar = remember { Calendar.getInstance() }
    val date = remember {
        SimpleDateFormat("MMM dd", Locale.getDefault()).format(calendar.time)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF35436B),
                        Color(0xFF1C2748)
                    )
                )
            )
            .combinedClickable(
                onClick = {},
                onLongClick = { showSettings = true }
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ─── TOP ROW ──────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = displayWeather.city,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = date,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }

                Icon(
                    imageVector = when (displayWeather.condition) {
                        "Clear" -> Icons.Default.WbSunny
                        "Rainy" -> Icons.Default.WaterDrop
                        "Snowy" -> Icons.Default.AcUnit
                        "Stormy" -> Icons.Default.Thunderstorm
                        else -> Icons.Default.Cloud
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }

            // ─── TEMPERATURE ──────────────────────────
            Text(
                text = "${displayWeather.temperature}°",
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = displayWeather.condition,
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.9f)
            )

            // PUSH INFO ROW DOWN SAFELY
            Spacer(modifier = Modifier.weight(1f))

            // ─── INFO ROW ─────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                if (prefs.showWind) {
                    WeatherInfoItem(
                        icon = Icons.Default.Air,
                        value = "${displayWeather.windSpeed} km/h"
                    )
                }

                if (prefs.showAQI && displayWeather.aqi > 0) {
                    WeatherInfoItem(
                        icon = Icons.Default.Eco,
                        value = "AQI ${displayWeather.aqi}"
                    )
                }

                if (prefs.showHumidity) {
                    WeatherInfoItem(
                        icon = Icons.Default.WaterDrop,
                        value = "${displayWeather.humidity}%"
                    )
                }

                if (prefs.showVisibility) {
                    WeatherInfoItem(
                        icon = Icons.Default.Visibility,
                        value = "${displayWeather.visibility} km"
                    )
                }
            }
        }
}
        if (showSettings) {
        WeatherSettingsDialog(
            prefs = prefs,
            onPrefsChange = { prefs = it },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun WeatherInfoItem(
    icon: ImageVector,
    value: String
) {
    Column(
        modifier = Modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}


@Composable
fun WeatherSettingsDialog(
    prefs: WeatherDisplayPrefs,
    onPrefsChange: (WeatherDisplayPrefs) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1C1C1E))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                Text(
                    text = "Weather Settings",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                SettingToggle("Wind Speed", prefs.showWind) {
                    onPrefsChange(prefs.copy(showWind = it))
                }

                SettingToggle("Air Quality (AQI)", prefs.showAQI) {
                    onPrefsChange(prefs.copy(showAQI = it))
                }

                SettingToggle("Humidity", prefs.showHumidity) {
                    onPrefsChange(prefs.copy(showHumidity = it))
                }

                SettingToggle("Visibility", prefs.showVisibility) {
                    onPrefsChange(prefs.copy(showVisibility = it))
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF007AFF)
                    )
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun SettingToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

data class WeatherDisplayPrefs(
    val showWind: Boolean = true,
    val showAQI: Boolean = true,
    val showHumidity: Boolean = true,
    val showVisibility: Boolean = true
)
