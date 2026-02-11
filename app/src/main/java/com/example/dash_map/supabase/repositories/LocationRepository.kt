package com.example.dash_map.supabase.repositories

import android.location.Location
import android.util.Log
import com.example.dash_map.supabase.SupabaseConfig
import com.example.dash_map.supabase.models.FriendLocation
import com.example.dash_map.supabase.models.UserProfile
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val TAG = "LocationRepository"

/**
 * Repository for handling location updates and realtime tracking
 */
class LocationRepository {

    private val supabase = SupabaseConfig.client
    private val realtime = SupabaseConfig.realtime

    /**
     * Update user's current location
     */
    suspend fun updateLocation(userId: String, location: Location) {
        try {
            Log.d(TAG, "========================================")
            Log.d(TAG, "UPDATING LOCATION FOR USER: $userId")
            Log.d(TAG, "  Latitude: ${location.latitude}")
            Log.d(TAG, "  Longitude: ${location.longitude}")
            Log.d(TAG, "  Accuracy: ${location.accuracy}")
            Log.d(TAG, "  Bearing: ${location.bearing}")
            Log.d(TAG, "========================================")

            supabase.from("users").update({
                set("last_location_lat", location.latitude)
                set("last_location_lon", location.longitude)
                set("last_location_updated", Instant.now().toString())
            }) {
                filter {
                    UserProfile::id eq userId
                }
            }

            Log.d(TAG, "✓ Location updated successfully in database")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to update location in database: ${e.message}", e)
            e.printStackTrace()
        }
    }

    /**
     * Get current locations of all accepted friends
     */
    suspend fun getFriendLocations(friendIds: List<String>): Result<List<FriendLocation>> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "========================================")
                Log.d(TAG, "FETCHING FRIEND LOCATIONS")
                Log.d(TAG, "Friend IDs: $friendIds")
                Log.d(TAG, "========================================")

                if (friendIds.isEmpty()) {
                    Log.d(TAG, "No friend IDs provided")
                    return@withContext Result.success(emptyList())
                }

                // Query users table for friend profiles with location data
                val locations = try {
                    val result = supabase.from("users")
                        .select()
                        .decodeList<UserProfile>()

                    Log.d(TAG, "Raw query returned ${result.size} total users")

                    // Filter to only requested friends
                    val friendProfiles = result.filter { it.id in friendIds }
                    Log.d(TAG, "Filtered to ${friendProfiles.size} friend profiles")

                    // Log each friend's data
                    friendProfiles.forEach { profile ->
                        Log.d(TAG, "----------------------------------------")
                        Log.d(TAG, "Friend: ${profile.displayName} (${profile.id})")
                        Log.d(TAG, "  Email: ${profile.email}")
                        Log.d(TAG, "  Sharing: ${profile.isSharingLocation}")
                        Log.d(TAG, "  Lat: ${profile.lastLocationLat}")
                        Log.d(TAG, "  Lon: ${profile.lastLocationLon}")
                        Log.d(TAG, "  Color: ${profile.markerColor}")
                        Log.d(TAG, "  Updated: ${profile.lastLocationUpdated}")
                    }

                    // Convert to FriendLocation objects
                    friendProfiles.mapNotNull { profile ->
                        // Only include if they have valid coordinates
                        if (profile.lastLocationLat != null && profile.lastLocationLon != null) {
                            FriendLocation(
                                friendId = profile.id,
                                displayName = profile.displayName,
                                latitude = profile.lastLocationLat,
                                longitude = profile.lastLocationLon,
                                markerColor = profile.markerColor,
                                isSharingLocation = profile.isSharingLocation,
                                lastUpdated = profile.lastLocationUpdated ?: ""
                            ).also {
                                Log.d(TAG, "✓ Created FriendLocation for ${it.displayName}")
                            }
                        } else {
                            Log.d(TAG, "✗ Skipping ${profile.displayName} - no coordinates (lat=${profile.lastLocationLat}, lon=${profile.lastLocationLon})")
                            null
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error querying friend locations: ${e.message}", e)
                    e.printStackTrace()
                    emptyList()
                }

                Log.d(TAG, "========================================")
                Log.d(TAG, "RESULT: ${locations.size} friend locations")
                Log.d(TAG, "========================================")

                Result.success(locations)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get friend locations", e)
                e.printStackTrace()
                Result.failure(e)
            }
        }


    /**
     * Subscribe to realtime location updates for specific friends
     */
    fun subscribeFriendLocations(
        friendIds: List<String>
    ): Flow<FriendLocation> {
        if (friendIds.isEmpty()) {
            Log.w(TAG, "No friend IDs provided for realtime subscription")
            return kotlinx.coroutines.flow.emptyFlow()
        }

        Log.d(TAG, "Subscribing to ${friendIds.size} friends for realtime updates")

        // Create a channel for user updates
        val channel = realtime.channel("friend-locations")

        // Subscribe to changes in the users table for our friends
        return channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "users"
            // Only listen for updates to our friends
            filter = "id=in.(${friendIds.joinToString(",")})"
        }
            .filter { action ->
                // Only process updates to location fields
                val record = action.record as? Map<*, *> ?: return@filter false
                record.containsKey("last_location_lat") &&
                        record.containsKey("last_location_lon") &&
                        record["is_sharing_location"] == true
            }
            .map { action ->
                val record = action.record as Map<*, *>

                FriendLocation(
                    friendId = record["id"] as String,
                    displayName = record["display_name"] as String,
                    markerColor = record["marker_color"] as? String ?: "#007AFF",
                    latitude = (record["last_location_lat"] as Number).toDouble(),
                    longitude = (record["last_location_lon"] as Number).toDouble(),
                    lastUpdated = record["last_location_updated"] as String,
                    isSharingLocation = record["is_sharing_location"] as Boolean
                )
            }
    }

    /**
     * Stop location sharing
     */
    suspend fun stopLocationSharing(userId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                supabase.from("users").update({
                    set("is_sharing_location", false)
                }) {
                    filter {
                        UserProfile::id eq userId
                    }
                }

                Log.d(TAG, "Location sharing stopped")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop location sharing", e)
                Result.failure(e)
            }
        }

    /**
     * Clear user's location data
     */
    suspend fun clearLocation(userId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                supabase.from("users").update({
                    set("is_sharing_location", false)
                    set("last_location_lat", null as Double?)
                    set("last_location_lon", null as Double?)
                    set("last_location_updated", null as String?)
                }) {
                    filter {
                        UserProfile::id eq userId
                    }
                }

                Log.d(TAG, "Location data cleared")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear location", e)
                Result.failure(e)
            }
        }
}