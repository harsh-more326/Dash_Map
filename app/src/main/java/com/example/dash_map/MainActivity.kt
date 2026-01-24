package com.example.dash_map

import android.Manifest
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.net.URL
import kotlin.math.roundToInt

class MainActivity : ComponentActivity(), LocationListener {
    private lateinit var notificationManager: NotificationManager
    private lateinit var locationManager: LocationManager
    private lateinit var audioManager: AudioManager
    private var currentSpeed = mutableStateOf(0f)
    private var currentLocation = mutableStateOf<GeoPoint?>(null)
    private var weatherData = mutableStateOf<WeatherData?>(null)
    private var isPlaying = mutableStateOf(false)
    private var currentSong = mutableStateOf("No song playing")
    private var currentArtist = mutableStateOf("")
    private var albumArtUrl = mutableStateOf<String?>(null)
    private var albumArtBitmap = mutableStateOf<android.graphics.Bitmap?>(null)
    private var mediaPollingRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startLocationUpdates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Configuration.getInstance().load(
                applicationContext,
                getSharedPreferences("osm_prefs", MODE_PRIVATE)
            )
            Configuration.getInstance().userAgentValue = packageName

            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            requestDNDPermission()

            setContent {
                DashMapTheme {
                    DashMapScreen(
                        location = currentLocation.value,
                        weather = weatherData.value,
                        context = this,
                        isPlaying = isPlaying.value,
                        songTitle = currentSong.value,
                        songArtist = currentArtist.value,
                        albumArtBitmap = albumArtBitmap.value,  // ADD THIS
                        onEnableDND = { enableDND() },
                        onDisableDND = { disableDND() },
                        onOpenGoogleMaps = { openGoogleMaps() },
                        onPlayPause = { togglePlayPause() },
                        onNext = { sendMediaButton(KeyEvent.KEYCODE_MEDIA_NEXT) },
                        onPrevious = { sendMediaButton(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
                    )
                }
            }

            requestLocationPermissions()
            enableImmersiveMode()

            // Start media polling
            startMediaPolling()

            window.decorView.post {
                enableImmersiveMode()
            }

        } catch (e: Exception) {
            Log.e("DashMap", "Error in onCreate", e)
        }
    }

    private fun togglePlayPause() {
        sendMediaButton(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    private fun sendMediaButton(keyCode: Int) {
        try {
            val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            audioManager.dispatchMediaKeyEvent(eventDown)

            val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(eventUp)

            Log.d("DashMap", "Media button sent: $keyCode")
        } catch (e: Exception) {
            Log.e("DashMap", "Error sending media button", e)
        }
    }

    private fun startMediaPolling() {
        stopMediaPolling()

        mediaPollingRunnable = object : Runnable {
            override fun run() {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        val notificationListenerString = Settings.Secure.getString(
                            contentResolver,
                            "enabled_notification_listeners"
                        )

                        if (notificationListenerString != null && notificationListenerString.contains(packageName)) {
                            val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                            val componentName = android.content.ComponentName(
                                this@MainActivity,
                                MediaNotificationListener::class.java
                            )

                            val controllers = try {
                                mediaSessionManager?.getActiveSessions(componentName)
                            } catch (e: Exception) {
                                null
                            }

                            controllers?.firstOrNull()?.let { controller ->
                                controller.metadata?.let { metadata ->
                                    val title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "No song playing"
                                    val artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                                    val bitmap = metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                                        ?: metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
                                    albumArtUrl.value = if (bitmap != null) "has_art" else null
                                    albumArtBitmap.value = bitmap  // ADD THIS LINE
                                    if (title != currentSong.value || artist != currentArtist.value) {
                                        currentSong.value = title
                                        currentArtist.value = artist

                                        val bitmap = metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                                            ?: metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
                                        albumArtUrl.value = if (bitmap != null) "has_art" else null

                                        Log.d("DashMap", "🎵 TRACK CHANGED - Song: '$title', Artist: '$artist', Has Art: ${albumArtUrl.value != null}")
                                    }
                                }


                                controller.playbackState?.let { state ->
                                    val newIsPlaying = state.state == android.media.session.PlaybackState.STATE_PLAYING
                                    if (newIsPlaying != isPlaying.value) {
                                        isPlaying.value = newIsPlaying
                                        Log.d("DashMap", "▶️ Playback: ${if (isPlaying.value) "PLAYING" else "PAUSED"}")
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DashMap", "Error polling media", e)
                }

                handler.postDelayed(this, 1000)
            }
        }

        handler.postDelayed(mediaPollingRunnable!!, 1000)
        Log.d("DashMap", "📻 Media polling started (every 1s)")
    }

    private fun stopMediaPolling() {
        mediaPollingRunnable?.let {
            handler.removeCallbacks(it)
            Log.d("DashMap", "📻 Media polling stopped")
        }
        mediaPollingRunnable = null
    }

    private fun openGoogleMaps() {
        try {
            val location = currentLocation.value
            if (location != null) {
                val uri = android.net.Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e("DashMap", "Error opening maps", e)
        }
    }

    private fun requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        try {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    this
                )

                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0f,
                    this
                )

                val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val lastLocation = gpsLocation ?: networkLocation

                lastLocation?.let {
                    currentLocation.value = GeoPoint(it.latitude, it.longitude)
                    fetchWeather(it.latitude, it.longitude)
                    Log.d("DashMap", "Initial location: ${it.latitude}, ${it.longitude}")
                }
            }
        } catch (e: Exception) {
            Log.e("DashMap", "Error starting location updates", e)
        }
    }

    override fun onLocationChanged(location: Location) {
        currentSpeed.value = location.speed * 3.6f
        val newLocation = GeoPoint(location.latitude, location.longitude)
        currentLocation.value = newLocation

        val shouldFetch = weatherData.value?.let {
            val lastLat = currentLocation.value?.latitude ?: 0.0
            val lastLon = currentLocation.value?.longitude ?: 0.0
            val distance = FloatArray(1)
            Location.distanceBetween(lastLat, lastLon, location.latitude, location.longitude, distance)
            distance[0] > 1000
        } ?: true

        if (shouldFetch) {
            fetchWeather(location.latitude, location.longitude)
        }
    }

    private fun fetchWeather(lat: Double, lon: Double) {
        Log.d("DashMap", "========================================")
        Log.d("DashMap", "🌦️ FETCHING WEATHER for: $lat, $lon")
        Log.d("DashMap", "========================================")

        Thread {
            try {
                val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&hourly=precipitation_probability&temperature_unit=celsius&daily=sunrise,sunset&timezone=auto"

                Log.d("DashMap", "Weather API URL: $weatherUrl")

                val weatherResponse = URL(weatherUrl).readText()
                val weatherJson = JSONObject(weatherResponse)

                Log.d("DashMap", "Weather API Response received")

                val currentWeather = weatherJson.getJSONObject("current_weather")
                val temp = currentWeather.getDouble("temperature").roundToInt()
                val weatherCode = currentWeather.getInt("weathercode")
                val windSpeed = currentWeather.getDouble("windspeed").roundToInt()

                Log.d("DashMap", "Temperature: $temp°C, Weather Code: $weatherCode, Wind: $windSpeed km/h")

                val condition = when(weatherCode) {
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

                    Log.d("DashMap", "Precipitation array length: ${precipArray.length()}")

                    if (precipArray.length() > 0) {
                        rainChance = precipArray.getInt(0)
                        Log.d("DashMap", "☔ RAIN CHANCE FOUND: $rainChance%")
                    } else {
                        Log.w("DashMap", "⚠️ Precipitation array is empty")
                    }
                } catch (e: Exception) {
                    Log.e("DashMap", "❌ ERROR parsing rain data: ${e.message}", e)
                }

                var aqi = 0
                var aqiCategory = "Good"
                try {
                    val aqiUrl = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=us_aqi"
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

                    Log.d("DashMap", "AQI: $aqi ($aqiCategory)")
                } catch (e: Exception) {
                    Log.e("DashMap", "Error fetching AQI", e)
                }

                var city = "Your Location"
                try {
                    val geocodeUrl = "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=en"
                    val geocodeResponse = URL(geocodeUrl).readText()
                    val geocodeJson = JSONObject(geocodeResponse)

                    city = geocodeJson.optString("city")
                    if (city.isEmpty() || city == "null") {
                        city = geocodeJson.optString("locality")
                    }
                    if (city.isEmpty() || city == "null") {
                        city = geocodeJson.optString("principalSubdivision", "Your Location")
                    }

                    Log.d("DashMap", "City: $city")
                } catch (e: Exception) {
                    Log.e("DashMap", "Error getting city name", e)
                }

                weatherData.value = WeatherData(temp, city, condition, windSpeed, aqi, aqiCategory, rainChance)

                Log.d("DashMap", "========================================")
                Log.d("DashMap", "✅ WEATHER DATA UPDATED SUCCESSFULLY")
                Log.d("DashMap", "Temperature: $temp°C")
                Log.d("DashMap", "City: $city")
                Log.d("DashMap", "Condition: $condition")
                Log.d("DashMap", "Wind Speed: $windSpeed km/h")
                Log.d("DashMap", "AQI: $aqi ($aqiCategory)")
                Log.d("DashMap", "🌧️ RAIN CHANCE: $rainChance%")
                Log.d("DashMap", "========================================")

            } catch (e: Exception) {
                Log.e("DashMap", "❌ FATAL ERROR fetching weather: ${e.message}", e)
            }
        }.start()
    }

    private fun enableImmersiveMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let { controller ->
                    controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        )
            }
        } catch (e: Exception) {
            Log.e("DashMap", "Error enabling immersive mode", e)
        }
    }

    private fun requestDNDPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!notificationManager.isNotificationPolicyAccessGranted) {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    startActivity(intent)
                }
            }
        } catch (e: Exception) {
            Log.e("DashMap", "Error requesting DND permission", e)
        }
    }

    private fun enableDND() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    notificationManager.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DashMap", "Error enabling DND", e)
        }
    }

    private fun disableDND() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    notificationManager.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DashMap", "Error disabling DND", e)
        }
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
        enableDND()
        startMediaPolling()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    override fun onPause() {
        super.onPause()
        disableDND()
        stopMediaPolling()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            locationManager.removeUpdates(this)
            stopMediaPolling()
        } catch (e: Exception) {
            Log.e("DashMap", "Error in onDestroy", e)
        }
    }
}

class MediaNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
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

@Composable
fun DashMapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFBB86FC),
            secondary = Color(0xFF03DAC6),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E)
        ),
        content = content
    )
}

@Composable
fun DashMapScreen(
    location: GeoPoint?,
    weather: WeatherData?,
    context: Context,
    isPlaying: Boolean,
    songTitle: String,
    songArtist: String,
    albumArtBitmap: android.graphics.Bitmap?,  // ADD THIS
    onEnableDND: () -> Unit,
    onDisableDND: () -> Unit,
    onOpenGoogleMaps: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
){
    var isDNDEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        onEnableDND()
        isDNDEnabled = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1a1a2e),
                        Color(0xFF16213e),
                        Color(0xFF0f3460)
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EnhancedWeatherWidget(
                    weather = weather,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                SpotifyWidget(
                    context = context,
                    isPlaying = isPlaying,
                    songTitle = songTitle,
                    songArtist = songArtist,
                    albumArtBitmap = albumArtBitmap,  // USE THE PARAMETER
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            OSMMapWidget(
                location = location,
                onOpenMaps = onOpenGoogleMaps,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        if (isDNDEnabled) {
            Icon(
                imageVector = Icons.Default.DoNotDisturb,
                contentDescription = "DND Active",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .size(28.dp)
            )
        }
    }
}

@Composable
fun EnhancedWeatherWidget(
    weather: WeatherData?,
    modifier: Modifier = Modifier
) {
    val displayWeather = weather ?: WeatherData(14, "Locating...", "Cloudy", 0, 0, "Good", 0)

    // Get current date
    val currentDate = remember {
        val calendar = java.util.Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
        dateFormat.format(calendar.time)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF5E72A8).copy(alpha = 0.5f),
                        Color(0xFF4A5C8C).copy(alpha = 0.4f)
                    )
                )
            )
            .padding(20.dp)
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
                // City and Date in one row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = displayWeather.city,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Temperature
                    Text(
                        text = currentDate,
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Temperature
                    Text(
                        text = "${displayWeather.temperature}°",
                        color = Color.White,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

            }

            // Middle - Wind speed, AQI, and Rain chance (centered)
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                // Wind speed
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

                // AQI
                if (displayWeather.aqi > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = "AQI",
                            tint = when(displayWeather.aqiCategory) {
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

                // Rain chance - always show for testing, or show if > 0
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
                // Weather icon
                Icon(
                    imageVector = when(displayWeather.condition) {
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

                // Weather condition
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

@Composable
fun SpotifyWidget(
    context: Context,
    isPlaying: Boolean,
    songTitle: String,
    songArtist: String,
    albumArtBitmap: android.graphics.Bitmap?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasSong = songTitle != "No song playing"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1DB954),
                        Color(0xFF1AA34A)
                    )
                )
            )
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Album Art Area
            Box(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0D7A3A)),
                contentAlignment = Alignment.Center
            ) {
                if (albumArtBitmap != null) {
                    // Display actual album art
                    androidx.compose.foundation.Image(
                        bitmap = albumArtBitmap.asImageBitmap(),
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else if (hasSong) {
                    // Show icon when no art available
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = "No Album Art",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(72.dp)
                    )
                } else {
                    // Show music note when no song
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "No Music",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            // Right side - Song Info and Controls
            Column(
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Song Info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = songTitle,
                        color = Color.White,
                        fontSize = if (hasSong) 18.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    if (songArtist.isNotEmpty()) {
                        Text(
                            text = songArtist,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    if (!hasSong) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Enable notification access",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            modifier = Modifier.clickable {
                                try {
                                    val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("DashMap", "Error opening settings", e)
                                }
                            }
                        )
                    }
                }

                // Music Controls
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.White, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun OSMMapWidget(
    location: GeoPoint?,
    onOpenMaps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultLocation = location ?: GeoPoint(19.003, 73.119)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable { onOpenMaps() }
    ) {
        AndroidView(
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    controller.setCenter(defaultLocation)

                    val marker = Marker(this).apply {
                        position = defaultLocation
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Current Location"
                    }
                    overlays.add(marker)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { mapView ->
                mapView.controller.setCenter(defaultLocation)
                mapView.overlays.clear()
                val marker = Marker(mapView).apply {
                    position = defaultLocation
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Current Location"
                }
                mapView.overlays.add(marker)
                mapView.invalidate()
            }
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.95f), CircleShape)
                .clickable { onOpenMaps() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = "Open Navigation",
                tint = Color(0xFF4285F4),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}