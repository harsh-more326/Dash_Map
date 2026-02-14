package com.example.dash_map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.URL
import com.example.dash_map.supabase.models.FriendLocation
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.dash_map.supabase.models.UserProfile
import com.example.dash_map.supabase.ui.FriendsScreen
import com.example.dash_map.supabase.viewmodels.LocationSharingViewModel
import com.mapbox.common.MapboxOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.AnnotationPlugin
import java.net.URLEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.*



private const val TAG = "MapWidget"

// ============================================================================
// DATA CLASSES
// ============================================================================

data class NavigationStep(
    val instruction: String,
    val distance: Double,
    val duration: Double,
    val maneuverType: String,
    val maneuverModifier: String?,
    val location: Point,
    val name: String?
)

data class NavigationRoute(
    val steps: List<NavigationStep>,
    val totalDistance: Double,
    val totalDuration: Double,
    val routePoints: List<Point>
)

/**
 * Trip progress data for display
 */
data class TripProgressData(
    val distanceRemaining: String,
    val timeRemaining: String,
    val estimatedTimeOfArrival: String
)

// ============================================================================
// MAIN COMPOSABLE
// ============================================================================

@SuppressLint("MissingPermission")
@Composable
fun MapWidget(
    location: GeoPoint? = null,
    destination: GeoPoint? = null,
    onOpenMaps: () -> Unit = {},
    onSwitchToClock: () -> Unit = {},
    locationViewModel: LocationSharingViewModel,
    onOpenFriendsScreen: () -> Unit = {},
    friendLocations: List<FriendLocation> = emptyList(),
    currentUser: UserProfile? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State management
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var mapDestination by remember { mutableStateOf(destination) }
    var isNavigating by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showRouteOverview by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showTraffic by remember { mutableStateOf(false) }
    var showNavigationCards by remember { mutableStateOf(true) }
    var mapInteractionEnabled by remember { mutableStateOf(true) }
    var route by remember { mutableStateOf<NavigationRoute?>(null) }
    var currentStepIndex by remember { mutableStateOf(0) }
    var currentSpeed by remember { mutableStateOf(0f) }
    var distanceToNextStep by remember { mutableStateOf(0.0) }
    var tripProgress by remember { mutableStateOf<TripProgressData?>(null) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var initialCameraSet by remember { mutableStateOf(false) }
    var styleLoaded by remember { mutableStateOf(false) }
    var needsRecenter by remember { mutableStateOf(false) }


    // Load last saved location
    val sharedPrefs = remember { context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE) }
    var lastSavedLocation by remember {
        mutableStateOf(
            try {
                val lat = sharedPrefs.getFloat("last_lat", 0f).toDouble()
                val lon = sharedPrefs.getFloat("last_lon", 0f).toDouble()
                if (lat != 0.0 && lon != 0.0) Location("").apply {
                    latitude = lat
                    longitude = lon
                } else null
            } catch (e: Exception) {
                null
            }
        )
    }

    // Location services
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { loc ->
                    Log.d(TAG, "Location: ${loc.latitude}, ${loc.longitude}, bearing: ${loc.bearing}")
                    currentLocation = loc
                    currentSpeed = loc.speed

                    // Save location
                    sharedPrefs.edit()
                        .putFloat("last_lat", loc.latitude.toFloat())
                        .putFloat("last_lon", loc.longitude.toFloat())
                        .apply()

                    if (isNavigating && route != null) {
                        val currentStep = route!!.steps.getOrNull(currentStepIndex)
                        currentStep?.let { step ->
                            distanceToNextStep = calculateDistance(
                                loc.latitude, loc.longitude,
                                step.location.latitude(), step.location.longitude()
                            )

                            // Update trip progress
                            tripProgress = calculateTripProgress(
                                route!!,
                                currentStepIndex,
                                distanceToNextStep
                            )

                            // Auto-advance to next step when close enough (20 meters threshold)
                            if (distanceToNextStep < 20.0 && currentStepIndex < route!!.steps.size - 1) {
                                currentStepIndex++
                                Log.d(TAG, "Advanced to step ${currentStepIndex + 1}")
                            }
                        }
                    }
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    // Effects
    LaunchedEffect(destination) {
        if (destination != null && destination != mapDestination) {
            mapDestination = destination
            currentLocation?.let { loc ->
                Log.d(TAG, "Fetching route from ${loc.latitude},${loc.longitude}")
                route = fetchNavigationRoute(
                    loc.longitude, loc.latitude,
                    destination.longitude, destination.latitude
                )
                Log.d(TAG, "Route fetched: ${route?.steps?.size} steps")
            }
        }
    }

    LaunchedEffect(currentLocation, initialCameraSet) {
        if (currentLocation != null && !initialCameraSet) {
            initialCameraSet = true
            Log.d(TAG, "Initial camera set")
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000L
            ).build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates started")
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d(TAG, "Location updates stopped")
        }
    }

    // UI
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
    ) {
        if (!hasLocationPermission) {
            LocationPermissionScreen(
                onRequestPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )
        } else {
            MapViewComponent(
                currentLocation = currentLocation ?: lastSavedLocation,
                mapDestination = mapDestination,
                route = route,
                isNavigating = isNavigating,
                showRouteOverview = showRouteOverview,
                styleLoaded = styleLoaded,
                currentStepIndex = currentStepIndex,
                showTraffic = showTraffic,
                mapInteractionEnabled = mapInteractionEnabled,
                needsRecenter = needsRecenter,
                friendLocations = friendLocations,
                currentUser = currentUser,
                onStyleLoaded = { styleLoaded = true },
                onRecenterComplete = { needsRecenter = false }
            )

            // Navigation UI
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // TOP ROW
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    if (!isNavigating) {
                        // NOT NAVIGATING UI
                        FloatingActionButton(
                            onClick = {
                                if (mapInteractionEnabled) {
                                    needsRecenter = true
                                    mapInteractionEnabled = false
                                } else {
                                    mapInteractionEnabled = true
                                }
                            },
                            modifier = Modifier.size(48.dp),
                            containerColor = if (mapInteractionEnabled) {
                                Color(0xFF1C1C1E)
                            } else {
                                androidx.compose.ui.graphics.Color(0xFF007AFF)
                            },
                            contentColor = Color.White
                        ) {
                            Icon(
                                imageVector = if (mapInteractionEnabled) {
                                    Icons.Default.MyLocation
                                } else {
                                    Icons.Default.LockOpen
                                },
                                contentDescription = if (mapInteractionEnabled) "Recenter" else "Unlock Map",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Search bar
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .shadow(8.dp, RoundedCornerShape(28.dp))
                                .clip(RoundedCornerShape(28.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFF1C1C1E))
                                .clickable { showSearchDialog = true }
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = androidx.compose.ui.graphics.Color(0xFF8E8E93)
                                )
                                Text(
                                    text = mapDestination?.let { "Destination set" }
                                        ?: "Search for a place",
                                    color = androidx.compose.ui.graphics.Color(0xFF8E8E93),
                                    fontSize = 17.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        FloatingActionButton(
                            onClick = { showSettings = true },
                            modifier = Modifier.size(48.dp),
                            containerColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
                            contentColor = Color.White
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        // NAVIGATING: Show enhanced navigation cards with trip progress
                        AnimatedVisibility(
                            visible = !showRouteOverview && showNavigationCards,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Current step card ONLY (no separate trip progress card)
                                route?.steps?.getOrNull(currentStepIndex)?.let { step ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(0.40f), // ✅ Reduced from 0.75f to 0.5f
                                        colors = CardDefaults.cardColors(
                                            containerColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E)
                                        ),
                                        shape = RoundedCornerShape(12.dp) // ✅ Smaller corner radius
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp), // ✅ Reduced from 16.dp
                                            verticalArrangement = Arrangement.spacedBy(6.dp) // ✅ Reduced spacing
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp) // ✅ Reduced spacing
                                            ) {
                                                Icon(
                                                    getManeuverIcon(step.maneuverType, step.maneuverModifier),
                                                    contentDescription = null,
                                                    tint = androidx.compose.ui.graphics.Color(0xFF00B3FF),
                                                    modifier = Modifier.size(28.dp) // ✅ Reduced from 32.dp
                                                )
                                                Column {
                                                    Text(
                                                        text = formatDistance(distanceToNextStep),
                                                        fontSize = 22.sp, // ✅ Reduced from 24.sp
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = getManeuverInstruction(step.maneuverType, step.maneuverModifier),
                                                        fontSize = 13.sp, // ✅ Reduced from 15.sp
                                                        color = androidx.compose.ui.graphics.Color(0xFFB0B0B0)
                                                    )
                                                }
                                            }

                                            step.name?.let {
                                                Text(
                                                    text = it,
                                                    fontSize = 14.sp, // ✅ Reduced from 16.sp
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // BOTTOM: Navigation controls
                if (!isNavigating) {
                    if (mapDestination != null && route != null) {
                        Button(
                            onClick = {
                                Log.d(TAG, "Starting navigation")
                                isNavigating = true
                                currentStepIndex = 0
                                showRouteOverview = false
                                mapInteractionEnabled = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.ui.graphics.Color(0xFF007AFF)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = "Start Navigation",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Go", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (mapDestination != null && route == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = androidx.compose.ui.graphics.Color(0xFF007AFF),
                                strokeWidth = 3.dp
                            )
                        }
                    }
                } else {
                    // Navigation control card
                    NavigationControlCard(
                        tripProgress = tripProgress,
                        onOverviewToggle = { showRouteOverview = !showRouteOverview },
                        onEndNavigation = {
                            isNavigating = false
                            currentStepIndex = 0
                            route = null
                            mapDestination = null
                            showRouteOverview = false
                            mapInteractionEnabled = true
                            tripProgress = null
                        }
                    )
                }
            }

            // Dialogs
            if (showSearchDialog) {
                DestinationSearchDialog(
                    onDismiss = { showSearchDialog = false },
                    onDestinationSelected = { lat, lon ->
                        mapDestination = GeoPoint(lat, lon)
                        showSearchDialog = false

                        scope.launch {
                            currentLocation?.let { loc ->
                                Log.d(TAG, "Fetching route after search")
                                route = fetchNavigationRoute(
                                    loc.longitude, loc.latitude,
                                    lon, lat
                                )
                                Log.d(TAG, "Route fetched: ${route?.steps?.size} steps")
                            }
                        }
                    }
                )
            }

            if (showSettings) {
                MapSettingsDialog(
                    showTraffic = showTraffic,
                    showNavigationCards = showNavigationCards,
                    onTrafficToggle = { showTraffic = it },
                    onNavigationCardsToggle = { showNavigationCards = it },
                    onSwitchToClock = onSwitchToClock,
                    locationViewModel = locationViewModel,
                    onDismiss = { showSettings = false },
                    onOpenFriendsScreen = onOpenFriendsScreen,
                )
            }
        }
    }
}
// ============================================================================
// UI COMPONENTS
// ============================================================================


/**
 * Enhanced navigation control card with trip progress integrated
 */
@Composable
fun NavigationControlCard(
    tripProgress: TripProgressData?,
    onOverviewToggle: () -> Unit,
    onEndNavigation: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        // Single row with button - progress info - button layout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // LEFT: Route overview button
            IconButton(
                onClick = onOverviewToggle,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Route,
                    "Route Overview",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // CENTER: Trip progress info
            tripProgress?.let { progress ->
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Distance
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = progress.distanceRemaining,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Distance",
                            fontSize = 10.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF8E8E93)
                        )
                    }

                    Divider(
                        modifier = Modifier
                            .height(28.dp)
                            .width(1.dp),
                        color = androidx.compose.ui.graphics.Color(0xFF3A3A3C)
                    )

                    // Time
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = progress.timeRemaining,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Time",
                            fontSize = 10.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF8E8E93)
                        )
                    }
                }
            } ?: Spacer(Modifier.weight(1f)) // If no progress, just add spacer

            Divider(
                modifier = Modifier
                    .height(28.dp)
                    .width(1.dp),
                color = androidx.compose.ui.graphics.Color(0xFF3A3A3C)
            )
            // RIGHT: End navigation button
            Button(
                onClick = onEndNavigation,
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFFF3B30)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    "End Navigation",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("End", color = Color.White, fontSize = 15.sp)
            }
        }
    }
}
// ============================================================================
// MAPBOX NAVIGATION SDK HELPERS
// ============================================================================

/**
 * Calculate trip progress data
 */
fun calculateTripProgress(
    route: NavigationRoute,
    currentStepIndex: Int,
    distanceToNextStep: Double
): TripProgressData {
    val currentStep = route.steps.getOrNull(currentStepIndex)

    // Calculate remaining distance
    val remainingDistance = if (currentStep != null) {
        distanceToNextStep + route.steps.drop(currentStepIndex + 1).sumOf { it.distance }
    } else {
        route.totalDistance
    }

    // Calculate remaining duration
    val remainingDuration = if (currentStep != null) {
        val timeToNextStep = if (currentStep.distance > 0) {
            (distanceToNextStep / currentStep.distance) * currentStep.duration
        } else {
            0.0
        }
        timeToNextStep + route.steps.drop(currentStepIndex + 1).sumOf { it.duration }
    } else {
        route.totalDuration
    }

    // Calculate ETA
    val currentTime = System.currentTimeMillis()
    val etaMillis = currentTime + (remainingDuration * 1000).toLong()
    val eta = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        .format(java.util.Date(etaMillis))

    return TripProgressData(
        distanceRemaining = formatDistance(remainingDistance),
        timeRemaining = formatDuration(remainingDuration),
        estimatedTimeOfArrival = eta
    )
}

@Composable
fun MapSettingsDialog(
    showTraffic: Boolean,
    showNavigationCards: Boolean,
    onTrafficToggle: (Boolean) -> Unit,
    onNavigationCardsToggle: (Boolean) -> Unit,
    onSwitchToClock: () -> Unit,
    onOpenFriendsScreen: () -> Unit,
    locationViewModel: LocationSharingViewModel,
    onDismiss: () -> Unit
) {
    var showFriendsScreen by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(androidx.compose.ui.graphics.Color(0xFF1C1C1E))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Map Settings",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            "Close",
                            tint = Color.White
                        )
                    }
                }

                // Traffic toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show Traffic",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Switch(
                        checked = showTraffic,
                        onCheckedChange = onTrafficToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = androidx.compose.ui.graphics.Color(0xFF007AFF),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = androidx.compose.ui.graphics.Color(0xFF3A3A3C)
                        )
                    )
                }

                // Navigation cards toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show Navigation Cards",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Switch(
                        checked = showNavigationCards,
                        onCheckedChange = onNavigationCardsToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = androidx.compose.ui.graphics.Color(0xFF007AFF),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = androidx.compose.ui.graphics.Color(0xFF3A3A3C)
                        )
                    )
                }
                // Friends & Location Sharing button - opens full screen
                Button(
                    onClick = {
                        onOpenFriendsScreen()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF007AFF)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.People, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Friends & Location Sharing", fontSize = 16.sp)
                }

                // Switch to clock button
                Button(
                    onClick = {
                        onSwitchToClock()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF007AFF)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Clock",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Switch to Clock", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun LocationPermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF000000))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.LocationOff,
            contentDescription = "Location Permission",
            tint = Color.White,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Location Permission Required",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Navigation requires access to your location",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = androidx.compose.ui.graphics.Color(0xFF007AFF)
            )
        ) {
            Text("Grant Permission")
        }
    }
}

@Composable
fun MapViewComponent(
    currentLocation: Location?,
    mapDestination: GeoPoint?,
    route: NavigationRoute?,
    isNavigating: Boolean,
    showRouteOverview: Boolean,
    styleLoaded: Boolean,
    currentStepIndex: Int,
    friendLocations: List<FriendLocation> = emptyList(),
    currentUser: UserProfile? = null,
    showTraffic: Boolean,
    mapInteractionEnabled: Boolean,
    needsRecenter: Boolean,
    onStyleLoaded: () -> Unit,
    onRecenterComplete: () -> Unit
) {
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var lastRouteHash by remember { mutableStateOf(0) }
    var lastLocationHash by remember { mutableStateOf(0) }

    AndroidView(
        factory = { ctx ->
            Log.d(TAG, "Creating MapView")
            MapView(ctx).apply {
                mapView = this

                compass.enabled = false
                scalebar.enabled = false
                logo.updateSettings { enabled = false }
                attribution.updateSettings { enabled = false }

                // Enable/disable gestures based on navigation state
                gestures.updateSettings {
                    rotateEnabled = mapInteractionEnabled
                    pitchEnabled = mapInteractionEnabled
                    scrollEnabled = mapInteractionEnabled
                    simultaneousRotateAndPinchToZoomEnabled = mapInteractionEnabled
                    pinchToZoomEnabled = mapInteractionEnabled
                    doubleTapToZoomInEnabled = mapInteractionEnabled
                    doubleTouchToZoomOutEnabled = mapInteractionEnabled
                    quickZoomEnabled = mapInteractionEnabled
                }

                getMapboxMap().loadStyleUri("mapbox://styles/mapbox/navigation-night-v1") { style ->
                    Log.d(TAG, "Map style loaded")
                    onStyleLoaded()

                    // Darker background and roads
                    style.setStyleLayerProperty(
                        "land",
                        "background-color",
                        Expression.rgb(5.0, 5.0, 5.0)
                    )

                    listOf(
                        "road-primary",
                        "road-secondary",
                        "road-tertiary",
                        "road-street",
                        "road-minor"
                    ).forEach { layerId ->
                        if (style.styleLayerExists(layerId)) {
                            style.setStyleLayerProperty(
                                layerId,
                                "line-color",
                                Expression.rgb(12.0, 12.0, 12.0)
                            )
                            style.setStyleLayerProperty(
                                layerId,
                                "line-opacity",
                                Expression.literal(0.2)
                            )
                        }
                    }

                    style.setStyleLayerProperty(
                        "road-label",
                        "text-opacity",
                        Expression.literal(0.0)
                    )
                    style.setStyleLayerProperty("poi-label", "text-opacity", Expression.literal(0.0))
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { mapViewInstance ->
            if (!styleLoaded) return@AndroidView

            // Update gestures based on interaction mode
            mapViewInstance.gestures.updateSettings {
                rotateEnabled = mapInteractionEnabled
                pitchEnabled = mapInteractionEnabled
                scrollEnabled = mapInteractionEnabled
                simultaneousRotateAndPinchToZoomEnabled = mapInteractionEnabled
                pinchToZoomEnabled = mapInteractionEnabled
                doubleTapToZoomInEnabled = mapInteractionEnabled
                doubleTouchToZoomOutEnabled = mapInteractionEnabled
                quickZoomEnabled = mapInteractionEnabled
            }

            mapViewInstance.getMapboxMap().getStyle { style ->
                try {
                    // ============================================
                    // CRITICAL DEBUGGING SECTION
                    // ============================================
                    Log.d(TAG, "========================================")
                    Log.d(TAG, "MAP UPDATE TRIGGERED")
                    Log.d(TAG, "Current location: $currentLocation")
                    Log.d(TAG, "Friend locations count: ${friendLocations.size}")
                    Log.d(TAG, "Current user: ${currentUser?.displayName} (${currentUser?.id})")

                    friendLocations.forEach { friend ->
                        Log.d(TAG, "----------------------------------------")
                        Log.d(TAG, "Friend: ${friend.displayName} (${friend.friendId})")
                        Log.d(TAG, "  Sharing: ${friend.isSharingLocation}")
                        Log.d(TAG, "  Location: ${friend.latitude}, ${friend.longitude}")
                        Log.d(TAG, "  Color: ${friend.markerColor}")
                        Log.d(TAG, "  Last Updated: ${friend.lastUpdated}")

                        // Calculate time since last update
                        try {
                            val lastUpdate = Instant.parse(friend.lastUpdated)
                            val secondsAgo = ChronoUnit.SECONDS.between(lastUpdate, Instant.now())
                            Log.d(TAG, "  Time since update: ${secondsAgo}s ago")

                            // Log decision
                            if (friend.isSharingLocation && secondsAgo <= 600) { // Increased to 10 minutes for testing
                                Log.d(TAG, "  ✓ WILL DRAW MARKER")
                            } else {
                                Log.d(TAG, "  ✗ WILL NOT DRAW - sharing=${friend.isSharingLocation}, age=${secondsAgo}s")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "  ✗ Error parsing timestamp: ${e.message}")
                        }
                    }
                    Log.d(TAG, "========================================")

                    val annotationApi = mapViewInstance.annotations

                    // Create hash codes to detect actual changes
                    val currentRouteHash = route?.routePoints?.hashCode() ?: 0
                    val currentLocationHash = currentLocation?.let {
                        "${it.latitude.hashCode()}_${it.longitude.hashCode()}_${(it.bearing / 5).toInt()}"
                    }?.hashCode() ?: 0

                    // Only update if something actually changed OR we need to recenter
                    val routeChanged = currentRouteHash != lastRouteHash
                    val locationChanged = currentLocationHash != lastLocationHash

                    if (routeChanged || locationChanged || needsRecenter) {
                        // Clear and redraw
                        annotationApi.cleanup()

                        // 1. First draw route (behind everything)
                        route?.let { navRoute ->
                            if (navRoute.routePoints.isNotEmpty()) {
                                val remainingPoints = if (isNavigating && currentLocation != null) {
                                    getRemainingRoutePoints(
                                        navRoute.routePoints,
                                        currentLocation,
                                        currentStepIndex
                                    )
                                } else {
                                    navRoute.routePoints
                                }

                                if (remainingPoints.isNotEmpty()) {
                                    drawRoute(annotationApi, remainingPoints)
                                }
                            }
                        }

                        // 2. Then draw destination marker
                        mapDestination?.let { dest ->
                            addDestinationMarker(style, annotationApi, dest)
                        }

                        // 3. Draw friend markers BEFORE user marker
                        Log.d(TAG, "→ Drawing friend markers...")
                        var friendMarkersDrawn = 0
                        friendLocations.forEach { friend ->
                            // Only show if sharing and recent
                            val lastUpdate = try {
                                Instant.parse(friend.lastUpdated)
                            } catch (e: Exception) {
                                Log.e(TAG, "Invalid timestamp for friend ${friend.friendId}", e)
                                return@forEach
                            }

                            val secondsAgo = ChronoUnit.SECONDS.between(
                                lastUpdate,
                                Instant.now()
                            )

                            // INCREASED TO 5 MINUTES FOR TESTING
                            if (friend.isSharingLocation && secondsAgo <= 300) {
                                Log.d(TAG, "→ Drawing marker for ${friend.displayName}")
                                addFriendMarker(
                                    style = style,
                                    annotationApi = annotationApi,
                                    friendId = friend.friendId,
                                    friendName = friend.displayName,
                                    latitude = friend.latitude,
                                    longitude = friend.longitude,
                                    markerColor = friend.markerColor
                                )
                                friendMarkersDrawn++
                            } else {
                                Log.d(TAG, "✗ NOT drawing marker for ${friend.displayName} - sharing=${friend.isSharingLocation}, age=${secondsAgo}s")
                            }
                        }
                        Log.d(TAG, "✓ Drew $friendMarkersDrawn friend markers")

                        // 4. Finally draw navigation pointer (on top)
                        currentLocation?.let { loc ->
                            val userColor = currentUser?.markerColor ?: "#39FF14"
                            addNavigationPointer(style, annotationApi, loc, userColor)

                            // Update camera - centered when not in interaction mode OR recentering
                            if (!mapInteractionEnabled || needsRecenter) {
                                updateCamera(
                                    mapViewInstance,
                                    loc,
                                    isNavigating,
                                    showRouteOverview,
                                    route
                                )
                                if (needsRecenter) {
                                    onRecenterComplete()
                                }
                            }
                        }

                        // Update traffic visibility
                        style.styleLayers.forEach { layer ->
                            if (layer.id.contains("traffic", ignoreCase = true)) {
                                style.setStyleLayerProperty(
                                    layer.id,
                                    "line-opacity",
                                    Expression.literal(if (showTraffic) 1.0 else 0.0)
                                )
                            }
                        }

                        lastRouteHash = currentRouteHash
                        lastLocationHash = currentLocationHash
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Map update error", e)
                    e.printStackTrace()
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationSearchDialog(
    onDismiss: () -> Unit,
    onDestinationSelected: (Double, Double) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var latInput by remember { mutableStateOf("") }
    var lonInput by remember { mutableStateOf("") }
    var useCoordinates by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(androidx.compose.ui.graphics.Color(0xFF1C1C1E))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Set Destination",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            "Close",
                            tint = Color.White
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !useCoordinates,
                        onClick = { useCoordinates = false },
                        label = { Text("Search") }
                    )
                    FilterChip(
                        selected = useCoordinates,
                        onClick = { useCoordinates = true },
                        label = { Text("Coordinates") }
                    )
                }

                if (!useCoordinates) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search location") },
                        placeholder = { Text("Enter place name") },
                        leadingIcon = { Icon(Icons.Default.Search, "Search") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                isSearching = true
                                searchError = null
                                scope.launch {
                                    val result = geocodeLocation(searchQuery)
                                    isSearching = false
                                    if (result != null) {
                                        onDestinationSelected(result.first, result.second)
                                    } else {
                                        searchError = "Location not found"
                                    }
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = androidx.compose.ui.graphics.Color(0xFF007AFF),
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color(0xFF8E8E93),
                            focusedLabelColor = androidx.compose.ui.graphics.Color(0xFF007AFF),
                            unfocusedLabelColor = androidx.compose.ui.graphics.Color(0xFF8E8E93)
                        )
                    )

                    searchError?.let { error ->
                        Text(
                            text = error,
                            color = androidx.compose.ui.graphics.Color(0xFFFF3B30),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = latInput,
                        onValueChange = { latInput = it },
                        label = { Text("Latitude") },
                        placeholder = { Text("19.0760") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = androidx.compose.ui.graphics.Color(0xFF007AFF),
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color(0xFF8E8E93),
                            focusedLabelColor = androidx.compose.ui.graphics.Color(0xFF007AFF),
                            unfocusedLabelColor = androidx.compose.ui.graphics.Color(0xFF8E8E93)
                        )
                    )

                    OutlinedTextField(
                        value = lonInput,
                        onValueChange = { lonInput = it },
                        label = { Text("Longitude") },
                        placeholder = { Text("72.8877") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val lat = latInput.toDoubleOrNull()
                                val lon = lonInput.toDoubleOrNull()
                                if (lat != null && lon != null) {
                                    onDestinationSelected(lat, lon)
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = androidx.compose.ui.graphics.Color(0xFF007AFF),
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color(0xFF8E8E93),
                            focusedLabelColor = androidx.compose.ui.graphics.Color(0xFF007AFF),
                            unfocusedLabelColor = androidx.compose.ui.graphics.Color(0xFF8E8E93)
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF2C2C2E)
                        )
                    ) {
                        Text("Cancel", color = Color.White)
                    }

                    Button(
                        onClick = {
                            if (useCoordinates) {
                                val lat = latInput.toDoubleOrNull()
                                val lon = lonInput.toDoubleOrNull()
                                if (lat != null && lon != null) {
                                    onDestinationSelected(lat, lon)
                                }
                            } else {
                                isSearching = true
                                searchError = null
                                scope.launch {
                                    val result = geocodeLocation(searchQuery)
                                    isSearching = false
                                    if (result != null) {
                                        onDestinationSelected(result.first, result.second)
                                    } else {
                                        searchError = "Location not found"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = if (useCoordinates) {
                            latInput.toDoubleOrNull() != null && lonInput.toDoubleOrNull() != null
                        } else {
                            searchQuery.isNotEmpty() && !isSearching
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF007AFF)
                        )
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Set Destination")
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// MAP HELPER FUNCTIONS
// ============================================================================

fun addNavigationPointer(
    style: Style,
    annotationApi: AnnotationPlugin,
    loc: Location,
    markerColor: String = "#39FF14" // ← ADD COLOR PARAMETER
) {
    try {
        val pointerBitmap = createNavigationPointer(markerColor) // ← Pass color

        if (style.getStyleImage("navigation-pointer") != null) {
            style.removeStyleImage("navigation-pointer")
        }

        style.addImage("navigation-pointer", pointerBitmap)

        val pointManager = annotationApi.createPointAnnotationManager()

        val locationPoint = PointAnnotationOptions()
            .withPoint(Point.fromLngLat(loc.longitude, loc.latitude))
            .withIconImage("navigation-pointer")
            .withIconRotate(0.0)
            .withIconSize(0.7)

        pointManager.create(locationPoint)
    } catch (e: Exception) {
        Log.e(TAG, "Error adding navigation pointer", e)
    }
}

fun addDestinationMarker(
    style: Style,
    annotationApi: AnnotationPlugin,
    dest: GeoPoint
) {
    try {
        val destBitmap = createDestinationMarker()

        if (style.getStyleImage("dest-marker") != null) {
            style.removeStyleImage("dest-marker")
        }

        style.addImage("dest-marker", destBitmap)

        val pointManager = annotationApi.createPointAnnotationManager()
        val destPoint = PointAnnotationOptions()
            .withPoint(Point.fromLngLat(dest.longitude, dest.latitude))
            .withIconImage("dest-marker")

        pointManager.create(destPoint)
    } catch (e: Exception) {
        Log.e(TAG, "Error adding destination marker", e)
    }
}

fun drawRoute(
    annotationApi: AnnotationPlugin,
    routePoints: List<Point>
) {
    try {
        val polylineManager = annotationApi.createPolylineAnnotationManager()
        polylineManager.create(
            PolylineAnnotationOptions()
                .withPoints(routePoints)
                .withLineWidth(7.0)
                .withLineColor(android.graphics.Color.parseColor("#00B3FF"))
        )
    } catch (e: Exception) {
        Log.e(TAG, "Error drawing route", e)
    }
}

fun updateCamera(
    mapView: MapView,
    loc: Location,
    isNavigating: Boolean,
    showRouteOverview: Boolean,
    route: NavigationRoute?
) {
    // ALWAYS CENTER ON USER LOCATION - map rotates to match heading
    if (isNavigating && !showRouteOverview) {
        // Navigation mode: centered, high zoom, tilted, rotating with bearing
        mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(loc.longitude, loc.latitude)) // Always centered
                .zoom(17.5)
                .pitch(60.0) // More tilted for better navigation view
                .bearing(loc.bearing.toDouble()) // Map rotates to match heading
                .build()
        )
    } else if (showRouteOverview && route != null) {
        // Overview mode: centered, lower zoom, less tilt, rotating with bearing
        mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(loc.longitude, loc.latitude)) // Still centered
                .zoom(15.0)
                .bearing(loc.bearing.toDouble()) // Map still rotates
                .pitch(30.0)
                .build()
        )
    } else {
        // Exploration/recenter mode: centered, medium zoom, some tilt
        mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(loc.longitude, loc.latitude)) // Centered
                .zoom(17.0)
                .pitch(45.0)
                .bearing(loc.bearing.toDouble()) // Match current bearing when recentering
                .build()
        )
    }
}

// ============================================================================
// GRAPHICS FUNCTIONS
// ============================================================================

fun createNavigationPointer(colorHex: String = "#39FF14"): Bitmap {
    val size = 120
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Parse the color
    var color = try {
        val cleanHex = colorHex.removePrefix("#")
        android.graphics.Color.parseColor("#$cleanHex")
    } catch (e: Exception) {
        Log.e(TAG, "Invalid color: $colorHex", e)
        android.graphics.Color.parseColor("#39FF14") // Default green
    }

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color // ← Use the passed color
        style = Paint.Style.FILL
    }

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 20f
    }

    // Arrow pointing UP
    val path = Path().apply {
        moveTo(size / 2f, 8f)
        lineTo(size - 22f, size - 28f)
        lineTo(size / 2f, size - 42f)
        lineTo(22f, size - 28f)
        close()
    }

    canvas.drawPath(path, strokePaint)
    canvas.drawPath(path, fillPaint)

    return bitmap
}
fun createDestinationMarker(): Bitmap {
    val size = 100
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    val centerX = size / 2f
    val centerY = size / 2f

    paint.color = android.graphics.Color.parseColor("#FF3B30")
    paint.setShadowLayer(10f, 0f, 0f, android.graphics.Color.parseColor("#FF3B30"))

    canvas.drawCircle(centerX, centerY - 15f, 18f, paint)

    val pinPath = Path()
    pinPath.moveTo(centerX, centerY + 25f)
    pinPath.lineTo(centerX - 15f, centerY - 5f)
    pinPath.arcTo(centerX - 18f, centerY - 33f, centerX + 18f, centerY + 3f, 180f, -180f, false)
    pinPath.lineTo(centerX + 15f, centerY - 5f)
    pinPath.close()

    canvas.drawPath(pinPath, paint)

    paint.color = android.graphics.Color.WHITE
    paint.clearShadowLayer()
    canvas.drawCircle(centerX, centerY - 15f, 8f, paint)

    return bitmap
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

fun getManeuverIcon(
    type: String,
    modifier: String?
): ImageVector {
    return when (type) {
        "turn" -> when (modifier) {
            "left" -> Icons.Default.TurnLeft
            "right" -> Icons.Default.TurnRight
            "slight left" -> Icons.Default.TurnSlightLeft
            "slight right" -> Icons.Default.TurnSlightRight
            "sharp left" -> Icons.Default.TurnSharpLeft
            "sharp right" -> Icons.Default.TurnSharpRight
            else -> Icons.Default.ArrowUpward
        }
        "merge" -> Icons.Default.MergeType
        "roundabout", "rotary" -> Icons.Default.RotateRight
        "arrive" -> Icons.Default.Place
        "depart" -> Icons.Default.DirectionsCar
        "fork" -> Icons.Default.ForkLeft
        "off ramp", "ramp" -> Icons.Default.RampRight
        else -> Icons.Default.ArrowUpward
    }
}

fun getManeuverInstruction(type: String, modifier: String?): String {
    return when (type) {
        "turn" -> when (modifier) {
            "left" -> "Turn left"
            "right" -> "Turn right"
            "slight left" -> "Slight left"
            "slight right" -> "Slight right"
            "sharp left" -> "Sharp left"
            "sharp right" -> "Sharp right"
            else -> "Continue"
        }
        "merge" -> "Merge"
        "roundabout", "rotary" -> "Roundabout"
        "arrive" -> "Arrive at destination"
        "depart" -> "Depart"
        "fork" -> when (modifier) {
            "left" -> "Keep left"
            "right" -> "Keep right"
            else -> "Fork"
        }
        "off ramp", "ramp" -> "Take ramp"
        "new name" -> "Continue"
        else -> "Continue"
    }
}

fun formatDistance(meters: Double): String {
    return if (meters >= 1000) {
        String.format("%.1f km", meters / 1000)
    } else {
        String.format("%.0f m", meters)
    }
}

fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60).toInt()
    return if (minutes >= 60) {
        val hours = minutes / 60
        val mins = minutes % 60
        if (mins > 0) "$hours hr $mins min" else "$hours hr"
    } else {
        "$minutes min"
    }
}

fun getRemainingRoutePoints(
    routePoints: List<Point>,
    currentLocation: Location,
    currentStepIndex: Int
): List<Point> {
    if (routePoints.isEmpty()) return emptyList()

    // Find closest point on route to current location
    var closestIndex = 0
    var minDistance = Double.MAX_VALUE

    routePoints.forEachIndexed { index, point ->
        val distance = calculateDistance(
            currentLocation.latitude, currentLocation.longitude,
            point.latitude(), point.longitude()
        )
        if (distance < minDistance) {
            minDistance = distance
            closestIndex = index
        }
    }

    // Return points from closest index onwards (removes passed route)
    return if (closestIndex < routePoints.size - 1) {
        routePoints.subList(closestIndex, routePoints.size)
    } else {
        emptyList()
    }
}

fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

// ============================================================================
// API FUNCTIONS
// ============================================================================

suspend fun fetchNavigationRoute(
    startLon: Double,
    startLat: Double,
    endLon: Double,
    endLat: Double
): NavigationRoute? {
    return withContext(Dispatchers.IO) {
        try {
            val accessToken = MapboxOptions.accessToken
            val url = "https://api.mapbox.com/directions/v5/mapbox/driving/" +
                    "$startLon,$startLat;$endLon,$endLat?" +
                    "steps=true&geometries=geojson&overview=full&" +
                    "banner_instructions=true&voice_instructions=true&" +
                    "access_token=$accessToken"

            val response = URL(url).readText()
            val json = JSONObject(response)
            val routes = json.getJSONArray("routes")

            if (routes.length() > 0) {
                val route = routes.getJSONObject(0)
                val totalDistance = route.getDouble("distance")
                val totalDuration = route.getDouble("duration")

                val geometry = route.getJSONObject("geometry")
                val coordinates = geometry.getJSONArray("coordinates")
                val routePoints = mutableListOf<Point>()

                for (i in 0 until coordinates.length()) {
                    val coord = coordinates.getJSONArray(i)
                    routePoints.add(Point.fromLngLat(coord.getDouble(0), coord.getDouble(1)))
                }

                val legs = route.getJSONArray("legs")
                val steps = mutableListOf<NavigationStep>()

                for (i in 0 until legs.length()) {
                    val leg = legs.getJSONObject(i)
                    val legSteps = leg.getJSONArray("steps")

                    for (j in 0 until legSteps.length()) {
                        val step = legSteps.getJSONObject(j)
                        val maneuver = step.getJSONObject("maneuver")

                        val instruction = maneuver.optString("instruction", "Continue")
                        val distance = step.getDouble("distance")
                        val duration = step.getDouble("duration")
                        val maneuverType = maneuver.getString("type")
                        val maneuverModifier = maneuver.optString("modifier", null)
                        val roadName = step.optString("name", null)

                        val location = maneuver.getJSONArray("location")
                        val stepLocation =
                            Point.fromLngLat(location.getDouble(0), location.getDouble(1))

                        steps.add(
                            NavigationStep(
                                instruction = instruction,
                                distance = distance,
                                duration = duration,
                                maneuverType = maneuverType,
                                maneuverModifier = maneuverModifier,
                                location = stepLocation,
                                name = roadName
                            )
                        )
                    }
                }

                NavigationRoute(
                    steps = steps,
                    totalDistance = totalDistance,
                    totalDuration = totalDuration,
                    routePoints = routePoints
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching route", e)
            null
        }
    }
}

suspend fun geocodeLocation(query: String): Pair<Double, Double>? {
    return withContext(Dispatchers.IO) {
        try {
            val accessToken = MapboxOptions.accessToken
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.mapbox.com/geocoding/v5/mapbox.places/" +
                    "$encodedQuery.json?access_token=$accessToken&limit=1"

            val response = URL(url).readText()
            val json = JSONObject(response)
            val features = json.getJSONArray("features")

            if (features.length() > 0) {
                val feature = features.getJSONObject(0)
                val geometry = feature.getJSONObject("geometry")
                val coordinates = geometry.getJSONArray("coordinates")
                Pair(coordinates.getDouble(1), coordinates.getDouble(0))
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error geocoding", e)
            null
        }
    }
}

@Composable
fun LocationSharingToggle(
    isSharing: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Share My Location", fontWeight = FontWeight.Bold)
            Text("Let friends see where you are", fontSize = 12.sp, color = Color.Gray)
        }

        Switch(
            checked = isSharing,
            onCheckedChange = { enabled ->
                onToggle(enabled)
            }
        )
    }
}

/**
 * Draw a friend's location marker with custom color
 */
fun addFriendMarker(
    style: Style,
    annotationApi: AnnotationPlugin,
    friendId: String,
    friendName: String,
    latitude: Double,
    longitude: Double,
    markerColor: String
) {
    try {
        val markerBitmap = createFriendMarkerBitmap(markerColor, friendName)
        val imageId = "friend-marker-$friendId"

        // Remove old marker if exists
        if (style.getStyleImage(imageId) != null) {
            style.removeStyleImage(imageId)
        }

        style.addImage(imageId, markerBitmap)

        val pointManager = annotationApi.createPointAnnotationManager()
        val markerPoint = PointAnnotationOptions()
            .withPoint(Point.fromLngLat(longitude, latitude))
            .withIconImage(imageId)
            .withIconSize(0.6)

        pointManager.create(markerPoint)
    } catch (e: Exception) {
        Log.e(TAG, "Error adding friend marker", e)
    }
}

/**
 * Create friend marker bitmap with custom color
 */
fun createFriendMarkerBitmap(colorHex: String, initials: String = ""): Bitmap {
    val size = 120
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = try {
            // Make sure to remove any # prefix if it's there
            val cleanHex = colorHex.removePrefix("#")
            android.graphics.Color.parseColor("#$cleanHex")
        } catch (e: Exception) {
            Log.e(TAG, "Invalid color: $colorHex", e)
            android.graphics.Color.parseColor("#007AFF") // Default blue
        }
        style = Paint.Style.FILL
    }

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

// Arrow pointing UP
    val path = Path().apply {
        moveTo(size / 2f, 8f)
        lineTo(size - 22f, size - 28f)
        lineTo(size / 2f, size - 42f)
        lineTo(22f, size - 28f)
        close()
    }

    canvas.drawPath(path, strokePaint)
    canvas.drawPath(path, fillPaint)

// Arrow center
    val arrowCenterX = size / 2f
    val arrowCenterY = (8f + (size - 28f)) / 2f

// Draw initials
//    if (initials.isNotEmpty()) {
//
//        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//            color = android.graphics.Color.WHITE
//            textSize = 28f
//            textAlign = Paint.Align.CENTER
//            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
//        }
//
//        val textY = arrowCenterY - ((textPaint.descent() + textPaint.ascent()) / 2)
//
//        canvas.drawText(
//            initials.take(2).uppercase(),
//            arrowCenterX,
//            textY,
//            textPaint
//        )
//    }

    return bitmap
}