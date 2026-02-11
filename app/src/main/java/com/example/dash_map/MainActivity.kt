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
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.mapbox.common.MapboxOptions
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import androidx.lifecycle.ViewModelProvider
import com.example.dash_map.supabase.SupabaseConfig
import com.example.dash_map.supabase.viewmodels.LocationSharingViewModel
import com.example.dash_map.supabase.ui.AuthScreen
import com.example.dash_map.supabase.ui.FriendsScreen

class MainActivity : ComponentActivity(), LocationListener {
    private lateinit var notificationManager: NotificationManager
    private lateinit var locationManager: LocationManager
    private lateinit var audioManager: AudioManager

    private var currentSpeed = mutableStateOf(0f)
    private var currentLocation = mutableStateOf<GeoPoint?>(null)
    private var weatherData = mutableStateOf<WeatherData?>(null)
    private var isPlaying = mutableStateOf(false)
    private lateinit var locationViewModel: LocationSharingViewModel
    private var currentSong = mutableStateOf("No song playing")
    private var currentArtist = mutableStateOf("")
    private var albumArtBitmap = mutableStateOf<android.graphics.Bitmap?>(null)
    var showClock =  mutableStateOf(false)

    // Store last weather fetch location
    private var lastWeatherLat = 0.0
    private var lastWeatherLon = 0.0

    private var mediaPollingRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startLocationUpdates()
        }
    }

    /**
     * Set screen orientation based on current screen
     */
    private fun setOrientation(isPortrait: Boolean) {
        requestedOrientation = if (isPortrait) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // ============================================================
            // CRITICAL: Initialize Supabase FIRST before anything else
            // ============================================================
            Log.d("MainActivity", "Initializing Supabase...")
            SupabaseConfig.initialize(applicationContext)
            Log.d("MainActivity", "✓ Supabase initialized successfully")

            // Now initialize system services
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Set Mapbox access token
            MapboxOptions.accessToken = getString(R.string.mapbox_access_token)

            // Initialize OSMDroid
            Configuration.getInstance().load(
                applicationContext,
                getSharedPreferences("osm_prefs", MODE_PRIVATE)
            )
            Configuration.getInstance().userAgentValue = packageName

            // Initialize ViewModel (Supabase is now ready)
            Log.d("MainActivity", "Creating LocationSharingViewModel...")
            locationViewModel = ViewModelProvider(this)[LocationSharingViewModel::class.java]
            Log.d("MainActivity", "✓ ViewModel created successfully")

            requestDNDPermission()

            setContent {
                DashMapTheme {
                    val isAuthenticated by locationViewModel.isAuthenticated.collectAsState()
                    var showFriendsScreen by remember { mutableStateOf(false) }

                    // Handle orientation based on screen
                    if (!isAuthenticated || showFriendsScreen) {
                        setOrientation(true)  // Portrait for auth and friends
                    } else {
                        setOrientation(false) // Landscape for dashboard
                    }

                    if (!isAuthenticated) {
                        // PORTRAIT MODE for authentication
                        val isLoading by locationViewModel.isLoading.collectAsState()
                        val errorMessage by locationViewModel.errorMessage.collectAsState()

                        AuthScreen(
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onSignIn = { email, password ->
                                locationViewModel.signIn(email, password)
                            },
                            onSignUp = { email, password, displayName ->
                                locationViewModel.signUp(email, password, displayName)
                            },
                            onClearError = {
                                locationViewModel.clearError()
                            }
                        )
                    } else if (showFriendsScreen) {
                        // PORTRAIT MODE for friends screen
                        val currentUser by locationViewModel.currentUser.collectAsState()
                        val friends by locationViewModel.friends.collectAsState()
                        val isSharing by locationViewModel.isLocationSharingEnabled.collectAsState()
                        val pendingRequests by locationViewModel.pendingRequests.collectAsState()
                        val sentRequests by locationViewModel.sentRequests.collectAsState() // ← ADD THIS LINE

                        FriendsScreen(
                            currentUser = currentUser,
                            friends = friends,
                            pendingRequests = pendingRequests,
                            sentRequests = sentRequests,
                            isLocationSharingEnabled = isSharing,
                            onToggleLocationSharing = { enabled ->
                                locationViewModel.toggleLocationSharing(enabled)
                            },
                            onSendFriendRequest = { email ->
                                locationViewModel.sendFriendRequest(email)
                            },
                            onAcceptFriendRequest = { connectionId ->
                                locationViewModel.acceptFriendRequest(connectionId)
                            },
                            onRejectFriendRequest = { connectionId ->
                                locationViewModel.rejectFriendRequest(connectionId)
                            },
                            onCancelSentRequest = { connectionId ->
                                locationViewModel.cancelSentRequest(connectionId)
                            },
                            onRemoveFriend = { friendId ->
                                locationViewModel.removeFriend(friendId)
                            },
                            onUpdateMarkerColor = { color ->
                                locationViewModel.updateMarkerColor(color)
                            },
                            onSignOut = {
                                locationViewModel.signOut()
                            },
                            onBack = {
                                showFriendsScreen = false
                            },
                            onManualRefresh = {  // ← ADD THIS
                                locationViewModel.manualRefresh()
                            }
                        )
                    } else {
                        // LANDSCAPE MODE for main dashboard
                        val friendLocations by locationViewModel.friendLocations.collectAsState()

                        DashMapScreen(
                            showClock = showClock,
                            onToggleClock = { showClock.value = !showClock.value },
                            location = currentLocation.value,
                            weather = weatherData.value,
                            context = this,
                            isPlaying = isPlaying.value,
                            songTitle = currentSong.value,
                            songArtist = currentArtist.value,
                            albumArtBitmap = albumArtBitmap.value,
                            onEnableDND = { enableDND() },
                            onOpenGoogleMaps = { openGoogleMaps() },
                            onPlayPause = { togglePlayPause() },
                            onNext = { sendMediaButton(KeyEvent.KEYCODE_MEDIA_NEXT) },
                            onPrevious = { sendMediaButton(KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                            locationViewModel = locationViewModel,
                            friendLocations = friendLocations,
                            onOpenFriendsScreen = { showFriendsScreen = true }
                        )
                    }
                }
            }

            requestLocationPermissions()
            enableImmersiveMode()
            startMediaPolling()

            window.decorView.post {
                enableImmersiveMode()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        if (location.accuracy <= 100f || currentLocation.value == null) {
            currentSpeed.value = location.speed * 3.6f
            val newLocation = GeoPoint(location.latitude, location.longitude)
            currentLocation.value = newLocation

            // Update location in ViewModel for sharing
            locationViewModel.updateLocation(location)

            val distance = FloatArray(1)
            Location.distanceBetween(
                lastWeatherLat,
                lastWeatherLon,
                location.latitude,
                location.longitude,
                distance
            )

            val shouldFetch = weatherData.value == null || distance[0] > 5000

            if (shouldFetch) {
                lastWeatherLat = location.latitude
                lastWeatherLon = location.longitude

                WeatherService.fetchWeather(location.latitude, location.longitude) { data ->
                    weatherData.value = data
                }
            }
        }
    }

    // ... rest of the methods remain the same ...

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
                    500L,
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

                val lastLocation = if (gpsLocation != null &&
                    (System.currentTimeMillis() - gpsLocation.time) < 120000) {
                    gpsLocation
                } else {
                    gpsLocation ?: networkLocation
                }

                lastLocation?.let {
                    currentLocation.value = GeoPoint(it.latitude, it.longitude)
                    lastWeatherLat = it.latitude
                    lastWeatherLon = it.longitude

                    WeatherService.fetchWeather(it.latitude, it.longitude) { data ->
                        weatherData.value = data
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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