package com.example.dash_map

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.osmdroid.util.GeoPoint

@Composable
fun DashMapScreen(
    location: GeoPoint?,
    weather: WeatherData?,
    context: Context,
    isPlaying: Boolean,
    songTitle: String,
    songArtist: String,
    albumArtBitmap: android.graphics.Bitmap?,
    onEnableDND: () -> Unit,
    onDisableDND: () -> Unit,
    onOpenGoogleMaps: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
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

            MapWidget(
                location = location,
                destination = mapDestination,
                onOpenMaps = onOpenGoogleMaps,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }


    }
}