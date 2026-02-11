package com.example.dash_map.supabase.models

import android.annotation.SuppressLint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * User profile model - matches the 'users' table in Supabase
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class UserProfile(
    @SerialName("id")
    val id: String,

    @SerialName("email")
    val email: String,

    @SerialName("phone")
    val phone: String? = null,

    @SerialName("display_name")
    val displayName: String,

    @SerialName("marker_color")
    val markerColor: String = "#007AFF",

    @SerialName("is_sharing_location")
    val isSharingLocation: Boolean = false,

    @SerialName("last_location_lat")
    val lastLocationLat: Double? = null,

    @SerialName("last_location_lon")
    val lastLocationLon: Double? = null,

    @SerialName("last_location_updated")
    val lastLocationUpdated: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)

/**
 * Friend connection model - matches the 'connections' table
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class Connection(
    @SerialName("id")
    val id: String,

    @SerialName("user_id")
    val userId: String,

    @SerialName("friend_id")
    val friendId: String,

    @SerialName("status")
    val status: String = "pending", // Can be: "pending", "accepted", "rejected"

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)

/**
 * Friend location for realtime tracking
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class FriendLocation(
    val friendId: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val markerColor: String,
    val isSharingLocation: Boolean,
    val lastUpdated: String
)

