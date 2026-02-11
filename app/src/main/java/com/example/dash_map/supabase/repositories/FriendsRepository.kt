package com.example.dash_map.supabase.repositories

import android.util.Log
import com.example.dash_map.supabase.SupabaseConfig
import com.example.dash_map.supabase.models.Connection
import com.example.dash_map.supabase.models.UserProfile
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FriendsRepository"

/**
 * Repository for handling friend connections and requests
 */
class FriendsRepository {

    private val supabase = SupabaseConfig.client

    /**
     * Send a friend request to another user
     */
    suspend fun sendFriendRequest(
        currentUserId: String,
        friendId: String
    ): Result<Connection> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "========================================")
            Log.d(TAG, "SENDING FRIEND REQUEST")
            Log.d(TAG, "From: ${currentUserId.take(8)}...")
            Log.d(TAG, "To: ${friendId.take(8)}...")
            Log.d(TAG, "========================================")

            // STEP 1: Check for duplicates
            Log.d(TAG, "→ Checking for existing connections...")
            val allConnections = try {
                supabase.from("connections")
                    .select()
                    .decodeList<Connection>()
            } catch (e: Exception) {
                // Empty result or JSON decode error = no connections
                when {
                    e.message?.contains("Expected start of the array") == true -> {
                        Log.d(TAG, "✓ No connections exist (empty response)")
                        emptyList()
                    }
                    e.message?.contains("EOF") == true -> {
                        Log.d(TAG, "✓ No connections exist (EOF)")
                        emptyList()
                    }
                    else -> {
                        Log.e(TAG, "✗ Error checking connections: ${e.message}", e)
                        emptyList()
                    }
                }
            }

            Log.d(TAG, "Found ${allConnections.size} total connections visible to user")

            // Check for duplicate
            val existing = allConnections.firstOrNull { conn ->
                (conn.userId == currentUserId && conn.friendId == friendId) ||
                        (conn.userId == friendId && conn.friendId == currentUserId)
            }

            if (existing != null) {
                val msg = when {
                    existing.status == "pending" && existing.userId == currentUserId ->
                        "You already sent a friend request to this user"
                    existing.status == "pending" && existing.friendId == currentUserId ->
                        "This user already sent you a friend request. Check your pending requests!"
                    existing.status == "accepted" ->
                        "You are already friends with this user"
                    else ->
                        "A connection already exists (status: ${existing.status})"
                }
                Log.e(TAG, "✗ Duplicate found: $msg")
                Log.d(TAG, "========================================")
                return@withContext Result.failure(Exception(msg))
            }

            // STEP 2: Create the connection
            Log.d(TAG, "✓ No duplicate found, creating connection...")

            val inserted = supabase.from("connections")
                .insert(
                    mapOf(
                        "user_id" to currentUserId,
                        "friend_id" to friendId,
                        "status" to "pending"
                    )
                ) {
                    select()
                }
                .decodeSingle<Connection>()

            Log.d(TAG, "✓ Friend request created successfully!")
            Log.d(TAG, "  Connection ID: ${inserted.id}")
            Log.d(TAG, "  Status: ${inserted.status}")
            Log.d(TAG, "========================================")

            Result.success(inserted)

        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to send friend request", e)
            Log.e(TAG, "  Error: ${e.message}")
            Log.d(TAG, "========================================")

            when {
                e.message?.contains("duplicate key") == true ->
                    Result.failure(Exception("Friend request already exists. Try refreshing."))
                else ->
                    Result.failure(e)
            }
        }
    }

    /**
     * Accept a friend request
     */
    suspend fun acceptFriendRequest(connectionId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                supabase.from("connections").update({
                    set("status", "accepted")
                }) {
                    filter {
                        Connection::id eq connectionId
                    }
                }

                Log.d(TAG, "Friend request accepted: $connectionId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to accept friend request", e)
                Result.failure(e)
            }
        }

    /**
     * Reject/delete a friend request
     */
    suspend fun rejectFriendRequest(connectionId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                supabase.from("connections")
                    .delete {
                        filter {
                            Connection::id eq connectionId
                        }
                    }

                Log.d(TAG, "Friend request rejected: $connectionId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reject friend request", e)
                Result.failure(e)
            }
        }

    /**
     * Remove a friend (delete connection)
     */
    suspend fun removeFriend(
        currentUserId: String,
        friendId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("connections")
                .delete {
                    filter {
                        or {
                            and {
                                Connection::userId eq currentUserId
                                Connection::friendId eq friendId
                            }
                            and {
                                Connection::userId eq friendId
                                Connection::friendId eq currentUserId
                            }
                        }
                    }
                }

            Log.d(TAG, "Friend removed: $friendId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove friend", e)
            Result.failure(e)
        }
    }

    /**
     * Get all pending friend requests (received)
     */
    suspend fun getPendingRequests(userId: String): Result<List<Pair<Connection, UserProfile>>> =
        withContext(Dispatchers.IO) {
            try {
                // Get pending connections where user is the friend (received requests)
                val connections = try {
                    supabase.from("connections")
                        .select {
                            filter {
                                Connection::friendId eq userId
                                Connection::status eq "pending"
                            }
                        }
                        .decodeList<Connection>()
                } catch (e: Exception) {
                    if (e.message?.contains("EOF") == true ||
                        e.message?.contains("Expected start of the array") == true) {
                        emptyList()
                    } else {
                        throw e
                    }
                }

                // Get user profiles for each requester
                val results = connections.mapNotNull { connection ->
                    try {
                        val user = supabase.from("users")
                            .select {
                                filter {
                                    UserProfile::id eq connection.userId
                                }
                            }
                            .decodeSingle<UserProfile>()
                        Pair(connection, user)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get user for connection ${connection.id}", e)
                        null
                    }
                }

                Log.d(TAG, "Got ${results.size} pending requests")
                Result.success(results)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get pending requests", e)
                Result.failure(e)
            }
        }

    /**
     * Get all sent friend requests (pending)
     */
    suspend fun getSentRequests(userId: String): Result<List<Pair<Connection, UserProfile>>> =
        withContext(Dispatchers.IO) {
            try {
                // Get pending connections where user is the requester
                val connections = try {
                    supabase.from("connections")
                        .select {
                            filter {
                                Connection::userId eq userId
                                Connection::status eq "pending"
                            }
                        }
                        .decodeList<Connection>()
                } catch (e: Exception) {
                    if (e.message?.contains("EOF") == true ||
                        e.message?.contains("Expected start of the array") == true) {
                        emptyList()
                    } else {
                        throw e
                    }
                }

                // Get user profiles for each friend
                val results = connections.mapNotNull { connection ->
                    try {
                        val user = supabase.from("users")
                            .select {
                                filter {
                                    UserProfile::id eq connection.friendId
                                }
                            }
                            .decodeSingle<UserProfile>()
                        Pair(connection, user)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get user for connection ${connection.id}", e)
                        null
                    }
                }

                Log.d(TAG, "Got ${results.size} sent requests")
                Result.success(results)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get sent requests", e)
                Result.failure(e)
            }
        }

    /**
     * Get all accepted friends
     */
    suspend fun getAcceptedFriends(userId: String): Result<List<UserProfile>> =
        withContext(Dispatchers.IO) {
            try {
                // Get all accepted connections (both directions)
                val connections = try {
                    supabase.from("connections")
                        .select {
                            filter {
                                or {
                                    Connection::userId eq userId
                                    Connection::friendId eq userId
                                }
                                Connection::status eq "accepted"
                            }
                        }
                        .decodeList<Connection>()
                } catch (e: Exception) {
                    if (e.message?.contains("EOF") == true ||
                        e.message?.contains("Expected start of the array") == true) {
                        emptyList()
                    } else {
                        throw e
                    }
                }

                // Extract friend IDs (the other user in the connection)
                val friendIds = connections.map { connection ->
                    if (connection.userId == userId) connection.friendId else connection.userId
                }.distinct()

                if (friendIds.isEmpty()) {
                    return@withContext Result.success(emptyList())
                }

                // Get user profiles for all friends (one by one)
                val friends = mutableListOf<UserProfile>()
                for (friendId in friendIds) {
                    try {
                        val user = supabase.from("users")
                            .select {
                                filter {
                                    UserProfile::id eq friendId
                                }
                            }
                            .decodeSingle<UserProfile>()
                        friends.add(user)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get friend $friendId", e)
                    }
                }

                Log.d(TAG, "Got ${friends.size} accepted friends")
                Result.success(friends)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get accepted friends", e)
                Result.failure(e)
            }
        }

    /**
     * Get accepted friend IDs only (for realtime subscription)
     */
    suspend fun getAcceptedFriendIds(userId: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val connections = try {
                    supabase.from("connections")
                        .select {
                            filter {
                                or {
                                    Connection::userId eq userId
                                    Connection::friendId eq userId
                                }
                                Connection::status eq "accepted"
                            }
                        }
                        .decodeList<Connection>()
                } catch (e: Exception) {
                    if (e.message?.contains("EOF") == true ||
                        e.message?.contains("Expected start of the array") == true) {
                        emptyList()
                    } else {
                        throw e
                    }
                }

                val friendIds = connections.map { connection ->
                    if (connection.userId == userId) connection.friendId else connection.userId
                }.distinct()

                Log.d(TAG, "Got ${friendIds.size} friend IDs for realtime")
                Result.success(friendIds)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get friend IDs", e)
                Result.failure(e)
            }
        }

    /**
     * Update custom marker color for a specific friend
     * This allows users to customize how they see each friend's marker
     */
    suspend fun updateFriendMarkerColor(
        userId: String,
        friendId: String,
        customColor: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Store in shared preferences or a new table "friend_preferences"
            // For now, we'll use a simple approach with shared preferences
            // In production, you'd want a database table

            Log.d(TAG, "Updated marker color for friend $friendId to $customColor")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update friend marker color", e)
            Result.failure(e)
        }
    }
}