package com.example.dash_map

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.collectAsState
import com.example.dash_map.supabase.viewmodels.LocationSharingViewModel
import com.example.dash_map.supabase.models.FriendLocation

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DashMapScreen(
    location: GeoPoint?,
    weather: WeatherData?,
    context: Context,
    isPlaying: Boolean,
    songTitle: String,
    songArtist: String,
    albumArtBitmap: Bitmap?,
    onEnableDND: () -> Unit,
    onOpenGoogleMaps: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    showClock: MutableState<Boolean>,
    onToggleClock: () -> Unit,
    locationViewModel: LocationSharingViewModel,
    friendLocations: List<FriendLocation> = emptyList(), // ADD THIS
    onOpenFriendsScreen: () -> Unit = {}
) {
    var isDNDEnabled by remember { mutableStateOf(true) }
    var mapDestination by remember { mutableStateOf<GeoPoint?>(null) }

    LaunchedEffect(Unit) {
        onEnableDND()
        isDNDEnabled = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
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
                WeatherWidget(
                    weather = weather,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                MusicWidget(
                    context = context,
                    isPlaying = isPlaying,
                    songTitle = songTitle,
                    songArtist = songArtist,
                    albumArtBitmap = albumArtBitmap,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
            ) {
                AnimatedContent(
                    targetState = showClock.value,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) with
                                fadeOut(animationSpec = tween(300))
                    }
                ) { showingClock ->
                    if (showingClock) {
                        FlipClockWidget(modifier = Modifier.fillMaxSize(),
                            onBackToMap = {
                                showClock.value = false
                            })
                    } else {
                        val currentUser by locationViewModel.currentUser.collectAsState()
                        MapWidget(
                            location = location,
                            destination = mapDestination,
                            onOpenMaps = onOpenGoogleMaps,
                            onSwitchToClock = onToggleClock,
                            modifier = Modifier.fillMaxSize(),
                            locationViewModel = locationViewModel,
                            friendLocations = friendLocations,
                            currentUser = currentUser,// PASS IT HERE
                            onOpenFriendsScreen = onOpenFriendsScreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FlipClockWidget(
    modifier: Modifier = Modifier,
    onBackToMap: () -> Unit
) {
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance()
            delay(1000L)
        }
    }

    val hourFormat = SimpleDateFormat("hh", Locale.getDefault()) // 12-hour format
    val minuteFormat = SimpleDateFormat("mm", Locale.getDefault())
    val amPmFormat = SimpleDateFormat("a", Locale.getDefault())
    val dateFormat = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())

    val hours = hourFormat.format(currentTime.time)
    val minutes = minuteFormat.format(currentTime.time)
    val amPm = amPmFormat.format(currentTime.time)
    val date = dateFormat.format(currentTime.time)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1C1C1E),
                        Color(0xFF2C2C2E)
                    )
                )
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF121212))
                .clickable { onBackToMap() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = "Back to Map",
                tint = Color.White
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Time display with AM/PM
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SlideDigitPair(hours)
                Text(
                    text = ":",
                    fontSize = 70.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                SlideDigitPair(minutes)

                Spacer(modifier = Modifier.width(5.dp))

                // AM/PM indicator
                Text(
                    text = amPm,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Date display
            Text(
                text = date,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun SlideDigitPair(
    value: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SlideDigit(value.getOrNull(0)?.toString() ?: "0")
        SlideDigit(value.getOrNull(1)?.toString() ?: "0")
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SlideDigit(
    digit: String
) {
    Box(
        modifier = Modifier
            .size(width = 60.dp, height = 130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF3A3A3C)),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = digit,
            transitionSpec = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(400)) with
                        slideOutVertically(
                            targetOffsetY = { -it },
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(400))
            }
        ) { targetDigit ->
            Text(
                text = targetDigit,
                fontSize = 70.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}