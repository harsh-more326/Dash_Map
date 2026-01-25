package com.example.dash_map

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.mapbox.common.MapboxOptions
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint

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
    private var albumArtBitmap = mutableStateOf<android.graphics.Bitmap?>(null)

    private var mediaPollingRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

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
            // Set Mapbox access token
            MapboxOptions.accessToken = getString(R.string.mapbox_access_token)

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
                        albumArtBitmap = albumArtBitmap.value,
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
            startMediaPolling()

            window.decorView.post {
                enableImmersiveMode()
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        } catch (e: Exception) {
            e.printStackTrace()
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

                                    if (title != currentSong.value || artist != currentArtist.value) {
                                        currentSong.value = title
                                        currentArtist.value = artist
                                        albumArtBitmap.value = bitmap
                                    }
                                }

                                controller.playbackState?.let { state ->
                                    val newIsPlaying = state.state == android.media.session.PlaybackState.STATE_PLAYING
                                    if (newIsPlaying != isPlaying.value) {
                                        isPlaying.value = newIsPlaying
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                handler.postDelayed(this, 1000)
            }
        }

        handler.postDelayed(mediaPollingRunnable!!, 1000)
    }

    private fun stopMediaPolling() {
        mediaPollingRunnable?.let {
            handler.removeCallbacks(it)
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
            e.printStackTrace()
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
                    WeatherService.fetchWeather(it.latitude, it.longitude) { data ->
                        weatherData.value = data
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            WeatherService.fetchWeather(location.latitude, location.longitude) { data ->
                weatherData.value = data
            }
        }
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
            e.printStackTrace()
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
            e.printStackTrace()
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
            e.printStackTrace()
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
            e.printStackTrace()
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
            e.printStackTrace()
        }
    }
}