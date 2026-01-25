package com.example.dash_map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RampRight
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.URL
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "MapWidget"

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

@SuppressLint("MissingPermission")
@Composable
fun MapWidget(
    location: GeoPoint? = null,
    destination: GeoPoint? = null,
    onOpenMaps: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var mapDestination by remember { mutableStateOf(destination) }
    var isNavigating by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showRouteOverview by remember { mutableStateOf(false) }

    var route by remember { mutableStateOf<NavigationRoute?>(null) }
    var currentStepIndex by remember { mutableStateOf(0) }
    var currentSpeed by remember { mutableStateOf(0f) }
    var distanceToNextStep by remember { mutableStateOf(0.0) }

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

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { loc ->
                    Log.d(
                        TAG,
                        "Location update: ${loc.latitude}, ${loc.longitude}, bearing: ${loc.bearing}"
                    )
                    currentLocation = loc
                    currentSpeed = loc.speed

                    if (isNavigating && route != null) {
                        val currentStep = route!!.steps.getOrNull(currentStepIndex)
                        currentStep?.let { step ->
                            distanceToNextStep = calculateDistance(
                                loc.latitude,
                                loc.longitude,
                                step.location.latitude(),
                                step.location.longitude()
                            )

                            if (distanceToNextStep < 30.0 && currentStepIndex < route!!.steps.size - 1) {
                                currentStepIndex++
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

    LaunchedEffect(destination) {
        if (destination != null && destination != mapDestination) {
            mapDestination = destination
            currentLocation?.let { loc ->
                Log.d(
                    TAG,
                    "Fetching route from ${loc.latitude},${loc.longitude} to ${destination.latitude},${destination.longitude}"
                )
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
            Log.d(TAG, "Initial camera position set")
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
            Log.d(TAG, "Location updates requested")
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
            Log.d(TAG, "Location updates removed")
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
    ) {
        if (!hasLocationPermission) {
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
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Location Permission Required",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Navigation requires access to your location",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF007AFF)
                    )
                ) {
                    Text("Grant Permission")
                }
            }
        } else {
            var mapView by remember { mutableStateOf<MapView?>(null) }

            AndroidView(
                factory = { ctx ->
                    Log.d(TAG, "Creating MapView")
                    MapView(ctx).apply {
                        mapView = this

                        // Disable UI elements
                        compass.enabled = false
                        scalebar.enabled = false
                        logo.updateSettings { enabled = false }
                        attribution.updateSettings { enabled = false }

                        // Load style only once
                        loadStyle(getMapboxMap().style) { style ->
                            Log.d(TAG, "Map style loaded successfully")
                            styleLoaded = true
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { mapViewInstance ->
                    // Only update if style is loaded
                    if (!styleLoaded) {
                        Log.d(TAG, "Waiting for style to load...")
                        return@AndroidView
                    }

                    mapViewInstance.mapboxMap.getStyle { style ->
                        try {
                            Log.d(
                                TAG,
                                "Updating map - Location: $currentLocation, Route points: ${route?.routePoints?.size}"
                            )

                            // Clear existing annotations
                            val annotationApi = mapViewInstance.annotations
                            annotationApi.cleanup()

                            // Add navigation pointer if we have current location
                            currentLocation?.let { loc ->
                                try {
                                    Log.d(
                                        TAG,
                                        "Adding navigation pointer at ${loc.latitude}, ${loc.longitude}"
                                    )

                                    // Create and add pointer image
                                    val pointerBitmap = createNavigationPointer()

                                    // Remove old image if exists
                                    if (style.getStyleImage("navigation-pointer") != null) {
                                        style.removeStyleImage("navigation-pointer")
                                    }

                                    style.addImage("navigation-pointer", pointerBitmap)
                                    Log.d(TAG, "Navigation pointer image added to style")

                                    // Create point annotation
                                    val pointManager = annotationApi.createPointAnnotationManager()
                                    val locationPoint = PointAnnotationOptions()
                                        .withPoint(Point.fromLngLat(loc.longitude, loc.latitude))
                                        .withIconImage("navigation-pointer")
                                        .withIconRotate(loc.bearing.toDouble())
                                        .withIconSize(1.2)

                                    val annotation = pointManager.create(locationPoint)
                                    Log.d(TAG, "Navigation pointer annotation created: $annotation")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error adding navigation pointer", e)
                                }

                                // Update camera
                                if (isNavigating && !showRouteOverview) {
                                    mapViewInstance.mapboxMap.setCamera(
                                        CameraOptions.Builder()
                                            .center(Point.fromLngLat(loc.longitude, loc.latitude))
                                            .zoom(16.5)
                                            .bearing(loc.bearing.toDouble())
                                            .pitch(35.0)
                                            .build()
                                    )
                                    Log.d(TAG, "Camera set to navigation mode")
                                } else if (showRouteOverview && route != null) {
                                    val points = route!!.routePoints
                                    if (points.size > 1) {
                                        val minLat = points.minOf { it.latitude() }
                                        val maxLat = points.maxOf { it.latitude() }
                                        val minLon = points.minOf { it.longitude() }
                                        val maxLon = points.maxOf { it.longitude() }

                                        val centerLat = (minLat + maxLat) / 2
                                        val centerLon = (minLon + maxLon) / 2

                                        mapViewInstance.mapboxMap.setCamera(
                                            CameraOptions.Builder()
                                                .center(Point.fromLngLat(centerLon, centerLat))
                                                .zoom(12.0)
                                                .pitch(0.0)
                                                .build()
                                        )
                                        Log.d(TAG, "Camera set to route overview mode")
                                    } else {
                                        Log.d(TAG, "point.size < 1")
                                    }
                                } else {
                                    mapViewInstance.mapboxMap.setCamera(
                                        CameraOptions.Builder()
                                            .center(Point.fromLngLat(loc.longitude, loc.latitude))
                                            .zoom(14.0)
                                            .pitch(0.0)
                                            .build()
                                    )
                                    Log.d(TAG, "Camera set to normal mode")
                                }
                            }

                            // Draw route line
                            route?.let { navRoute ->
                                if (navRoute.routePoints.isNotEmpty()) {
                                    try {
                                        Log.d(
                                            TAG,
                                            "Drawing route with ${navRoute.routePoints.size} points"
                                        )
                                        val polylineManager =
                                            annotationApi.createPolylineAnnotationManager()
                                        val polyline = PolylineAnnotationOptions()
                                            .withPoints(navRoute.routePoints)
                                            .withLineColor(Color.parseColor("#5AC8FA"))
                                            .withLineWidth(6.0)

                                        val routeLine = polylineManager.create(polyline)
                                        Log.d(TAG, "Route polyline created: $routeLine")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error drawing route", e)
                                    }
                                }
                            }

                            // Draw destination marker
                            mapDestination?.let { dest ->
                                try {
                                    Log.d(
                                        TAG,
                                        "Adding destination marker at ${dest.latitude}, ${dest.longitude}"
                                    )

                                    val destBitmap = createDestinationMarker()

                                    if (style.getStyleImage("dest-marker") != null) {
                                        style.removeStyleImage("dest-marker")
                                    }
                                    style.addImage("dest-marker", destBitmap)
                                    Log.d(TAG, "Destination marker image added to style")

                                    val pointManager = annotationApi.createPointAnnotationManager()
                                    val destPoint = PointAnnotationOptions()
                                        .withPoint(Point.fromLngLat(dest.longitude, dest.latitude))
                                        .withIconImage("dest-marker")

                                    val annotation = pointManager.create(destPoint)
                                    Log.d(TAG, "Destination marker annotation created: $annotation")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error adding destination marker", e)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in map update", e)
                        }
                    }
                }
            )

            // UI Overlays
            if (!isNavigating) {
                // Search bar
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .fillMaxWidth(0.9f)
                        .height(56.dp)
                        .shadow(8.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(androidx.compose.ui.graphics.Color(0xFF1C1C1E))
                        .clickable { showSearchDialog = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
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

                // Start navigation button
                if (mapDestination != null && route != null) {
                    Button(
                        onClick = {
                            Log.d(TAG, "Starting navigation")
                            isNavigating = true
                            currentStepIndex = 0
                            showRouteOverview = false
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(24.dp)
                            .fillMaxWidth(0.9f)
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

                // Loading indicator
                if (mapDestination != null && route == null) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(40.dp),
                        color = androidx.compose.ui.graphics.Color(0xFF007AFF),
                        strokeWidth = 3.dp
                    )
                }
            } else {
                // Navigation UI (same as before)
                route?.let { navRoute ->
                    val currentStep = navRoute.steps.getOrNull(currentStepIndex)
                    val remainingDistance =
                        navRoute.steps.drop(currentStepIndex).sumOf { it.distance }
                    val remainingDuration =
                        navRoute.steps.drop(currentStepIndex).sumOf { it.duration }

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AnimatedVisibility(
                            visible = !showRouteOverview,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Column {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = androidx.compose.ui.graphics.Color(
                                            0xFF1C1C1E
                                        )
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    currentStep?.let { step ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(20.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Icon(
                                                imageVector = getManeuverIcon(
                                                    step.maneuverType,
                                                    step.maneuverModifier
                                                ),
                                                contentDescription = "Maneuver",
                                                tint = androidx.compose.ui.graphics.Color.White,
                                                modifier = Modifier.size(32.dp)
                                            )

                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = formatDistance(distanceToNextStep),
                                                    fontSize = 36.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = androidx.compose.ui.graphics.Color.White
                                                )
                                                step.name?.let { roadName ->
                                                    Text(
                                                        text = roadName,
                                                        fontSize = 20.sp,
                                                        color = androidx.compose.ui.graphics.Color.White,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                val nextStep = navRoute.steps.getOrNull(currentStepIndex + 1)
                                nextStep?.let { next ->
                                    Row(
                                        modifier = Modifier.padding(start = 24.dp, top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Then",
                                            fontSize = 14.sp,
                                            color = androidx.compose.ui.graphics.Color(0xFF8E8E93)
                                        )
                                        Icon(
                                            imageVector = getManeuverIcon(
                                                next.maneuverType,
                                                next.maneuverModifier
                                            ),
                                            contentDescription = "Next",
                                            tint = androidx.compose.ui.graphics.Color(0xFF8E8E93),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = formatDuration(remainingDuration),
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                    Text(
                                        text = formatDistance(remainingDistance),
                                        fontSize = 14.sp,
                                        color = androidx.compose.ui.graphics.Color(0xFF8E8E93)
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    IconButton(
                                        onClick = { showRouteOverview = !showRouteOverview },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(
                                                androidx.compose.ui.graphics.Color(0xFF2C2C2E),
                                                RoundedCornerShape(22.dp)
                                            )
                                    ) {
                                        Icon(
                                            imageVector = if (showRouteOverview) Icons.Default.Navigation else Icons.Default.Route,
                                            contentDescription = "Overview",
                                            tint = androidx.compose.ui.graphics.Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            Log.d(TAG, "Ending navigation")
                                            isNavigating = false
                                            mapDestination = null
                                            route = null
                                            currentStepIndex = 0
                                            showRouteOverview = false
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(
                                                androidx.compose.ui.graphics.Color(0xFFFF3B30),
                                                RoundedCornerShape(22.dp)
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "End",
                                            tint = androidx.compose.ui.graphics.Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
        }
    }
}

// Rest of the helper functions remain the same...
fun getManeuverIcon(
    type: String,
    modifier: String?
): androidx.compose.ui.graphics.vector.ImageVector {
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

// Create NFS-style navigation pointer
fun createNavigationPointer(): android.graphics.Bitmap {
    val size = 120
    val bitmap =
        android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
    }

    // Create chevron/arrow shape pointing upward (NFS style)
    val path = android.graphics.Path()
    val centerX = size / 2f
    val centerY = size / 2f

    // Outer glow (blue)
    paint.color = Color.parseColor("#5AC8FA")
    paint.setShadowLayer(12f, 0f, 0f, Color.parseColor("#5AC8FA"))

    // Main arrow shape
    path.moveTo(centerX, centerY - 40f) // Top point
    path.lineTo(centerX + 25f, centerY + 10f) // Right outer
    path.lineTo(centerX + 12f, centerY + 10f) // Right inner
    path.lineTo(centerX + 12f, centerY + 40f) // Right bottom
    path.lineTo(centerX - 12f, centerY + 40f) // Left bottom
    path.lineTo(centerX - 12f, centerY + 10f) // Left inner
    path.lineTo(centerX - 25f, centerY + 10f) // Left outer
    path.close()

    canvas.drawPath(path, paint)

    // Inner highlight
    paint.color = Color.WHITE
    paint.clearShadowLayer()
    val innerPath = android.graphics.Path()
    innerPath.moveTo(centerX, centerY - 35f)
    innerPath.lineTo(centerX + 20f, centerY + 8f)
    innerPath.lineTo(centerX + 10f, centerY + 8f)
    innerPath.lineTo(centerX + 10f, centerY + 35f)
    innerPath.lineTo(centerX - 10f, centerY + 35f)
    innerPath.lineTo(centerX - 10f, centerY + 8f)
    innerPath.lineTo(centerX - 20f, centerY + 8f)
    innerPath.close()

    canvas.drawPath(innerPath, paint)

    // Center dot for precision
    paint.color = Color.parseColor("#007AFF")
    canvas.drawCircle(centerX, centerY, 6f, paint)

    return bitmap
}

// Create destination marker
fun createDestinationMarker(): android.graphics.Bitmap {
    val size = 100
    val bitmap =
        android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
    }

    val centerX = size / 2f
    val centerY = size / 2f

    // Red pin with glow
    paint.color = Color.parseColor("#FF3B30")
    paint.setShadowLayer(10f, 0f, 0f, Color.parseColor("#FF3B30"))

    // Draw pin shape
    canvas.drawCircle(centerX, centerY - 15f, 18f, paint)

    val pinPath = android.graphics.Path()
    pinPath.moveTo(centerX, centerY + 25f) // Bottom point
    pinPath.lineTo(centerX - 15f, centerY - 5f) // Left
    pinPath.arcTo(centerX - 18f, centerY - 33f, centerX + 18f, centerY + 3f, 180f, -180f, false)
    pinPath.lineTo(centerX + 15f, centerY - 5f) // Right
    pinPath.close()

    canvas.drawPath(pinPath, paint)

    // White center
    paint.color = Color.WHITE
    paint.clearShadowLayer()
    canvas.drawCircle(centerX, centerY - 15f, 8f, paint)

    return bitmap
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

suspend fun fetchNavigationRoute(
    startLon: Double,
    startLat: Double,
    endLon: Double,
    endLat: Double
): NavigationRoute? {
    return withContext(Dispatchers.IO) {
        try {
            val accessToken = com.mapbox.common.MapboxOptions.accessToken

            val url = "https://api.mapbox.com/directions/v5/mapbox/driving/" +
                    "$startLon,$startLat;$endLon,$endLat?" +
                    "steps=true&" +
                    "geometries=geojson&" +
                    "overview=full&" +
                    "banner_instructions=true&" +
                    "voice_instructions=true&" +
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
                        val stepLocation = Point.fromLngLat(
                            location.getDouble(0),
                            location.getDouble(1)
                        )

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
            e.printStackTrace()
            null
        }
    }
}

suspend fun geocodeLocation(query: String): Pair<Double, Double>? {
    return withContext(Dispatchers.IO) {
        try {
            val accessToken = com.mapbox.common.MapboxOptions.accessToken
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")

            val url = "https://api.mapbox.com/geocoding/v5/mapbox.places/" +
                    "$encodedQuery.json?" +
                    "access_token=$accessToken&" +
                    "limit=1"

            val response = URL(url).readText()
            val json = JSONObject(response)

            val features = json.getJSONArray("features")
            if (features.length() > 0) {
                val feature = features.getJSONObject(0)
                val geometry = feature.getJSONObject("geometry")
                val coordinates = geometry.getJSONArray("coordinates")
                val lon = coordinates.getDouble(0)
                val lat = coordinates.getDouble(1)
                Pair(lat, lon)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
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
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Set Destination",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = androidx.compose.ui.graphics.Color.White
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
                        leadingIcon = {
                            Icon(Icons.Default.Search, "Search")
                        },
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
                            focusedTextColor = androidx.compose.ui.graphics.Color.White,
                            unfocusedTextColor = androidx.compose.ui.graphics.Color.White,
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
                            focusedTextColor = androidx.compose.ui.graphics.Color.White,
                            unfocusedTextColor = androidx.compose.ui.graphics.Color.White,
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
                        placeholder = { Text("72.8777") },
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
                            focusedTextColor = androidx.compose.ui.graphics.Color.White,
                            unfocusedTextColor = androidx.compose.ui.graphics.Color.White,
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
                        Text("Cancel", color = androidx.compose.ui.graphics.Color.White)
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
                                color = androidx.compose.ui.graphics.Color.White,
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