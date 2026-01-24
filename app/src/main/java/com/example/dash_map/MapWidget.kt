package com.example.dash_map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun MapWidget(
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