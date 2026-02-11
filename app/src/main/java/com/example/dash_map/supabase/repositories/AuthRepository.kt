package com.example.dash_map.supabase.repositories

import android.util.Log
import com.example.dash_map.supabase.SupabaseConfig
import com.example.dash_map.supabase.models.UserProfile
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AuthRepository"

/**
 * Repository for handling authentication and user profile operations
 */
class AuthRepository {

    private val auth = SupabaseConfig.auth
    private val supabase = SupabaseConfig.client

    /**
     * Sign up a new user with email and password
     */
    suspend fun signUp(
        email: String,
        password: String,
        displayName: String
    ): Result<UserInfo> = withContext(Dispatchers.IO) {
        try {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
                // Note: Display name will be set in user profile after signup
            }

            // Get current user
            val user = auth.currentUserOrNull()
            if (user != null) {
                Log.d(TAG, "Sign up successful: ${user.id}")

                // Create user profile with display name
                try {
                    supabase.from("users").insert(
                        mapOf(
                            "id" to user.id,
                            "email" to email,
                            "display_name" to displayName
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Profile might already exist", e)
                }

                Result.success(user)
            } else {
                Result.failure(Exception("Failed to get user after signup"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sign up failed", e)
            Result.failure(e)
        }
    }

    /**
     * Sign in an existing user
     */
    suspend fun signIn(email: String, password: String): Result<UserInfo> =
        withContext(Dispatchers.IO) {
            try {
                auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                val user = auth.currentUserOrNull()
                if (user != null) {
                    Log.d(TAG, "Sign in successful: ${user.id}")
                    Result.success(user)
                } else {
                    Result.failure(Exception("Failed to get user after signin"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sign in failed", e)
                Result.failure(e)
            }
        }

    /**
     * Sign out the current user
     */
    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.signOut()
            Log.d(TAG, "Sign out successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sign out failed", e)
            Result.failure(e)
        }
    }

    /**
     * Get current user info
     */
    fun getCurrentUser(): UserInfo? {
        return auth.currentUserOrNull()
    }

    /**
     * Check if user is signed in
     */
    fun isSignedIn(): Boolean {
        return auth.currentUserOrNull() != null
    }

    /**
     * Get or create user profile
     */
    suspend fun getUserProfile(userId: String): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            try {
                val response = supabase.from("users")
                    .select {
                        filter {
                            UserProfile::id eq userId
                        }
                    }
                    .decodeSingle<UserProfile>()

                Log.d(TAG, "Got user profile: ${response.displayName}")
                Result.success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get user profile", e)
                Result.failure(e)
            }
        }

    /**
     * Update user profile
     */
    suspend fun updateUserProfile(
        userId: String,
        displayName: String? = null,
        markerColor: String? = null,
        phone: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("users").update({
                displayName?.let { set("display_name", it) }
                markerColor?.let { set("marker_color", it) }
                phone?.let { set("phone", it) }
            }) {
                filter {
                    UserProfile::id eq userId
                }
            }

            Log.d(TAG, "Updated user profile")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update user profile", e)
            Result.failure(e)
        }
    }

    /**
     * Toggle location sharing
     */
    suspend fun toggleLocationSharing(
        userId: String,
        isSharing: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("users").update({
                set("is_sharing_location", isSharing)
            }) {
                filter {
                    UserProfile::id eq userId
                }
            }

            Log.d(TAG, "Location sharing: $isSharing")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle location sharing", e)
            Result.failure(e)
        }
    }

    /**
     * Search for user by email (for adding friends)
     */
    suspend fun searchUserByEmail(email: String): Result<UserProfile?> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "========================================")
                Log.d(TAG, "SEARCHING FOR USER: $email")
                Log.d(TAG, "========================================")

                // Get ALL users to debug RLS issues
                val allUsers = try {
                    supabase.from("users")
                        .select()
                        .decodeList<UserProfile>()
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Failed to fetch users (RLS blocking?): ${e.message}", e)
                    emptyList()
                }

                Log.d(TAG, "Total users visible: ${allUsers.size}")
                if (allUsers.isEmpty()) {
                    Log.e(TAG, "⚠️  WARNING: Cannot see any users!")
                    Log.e(TAG, "⚠️  This is likely a Row Level Security (RLS) issue!")
                    Log.e(TAG, "⚠️  Check Supabase RLS policies on 'users' table")
                } else {
                    Log.d(TAG, "Users found:")
                    allUsers.forEach { user ->
                        Log.d(TAG, "  - ${user.email} → ${user.displayName} (${user.id.take(8)}...)")
                    }
                }

                // Search for matching email (case-insensitive)
                val matchedUser = allUsers.firstOrNull { user ->
                    user.email.equals(email, ignoreCase = true)
                }

                if (matchedUser != null) {
                    Log.d(TAG, "✓ FOUND: ${matchedUser.displayName} (${matchedUser.email})")
                    Log.d(TAG, "========================================")
                    Result.success(matchedUser)
                } else {
                    Log.e(TAG, "✗ NOT FOUND: No user with email $email")
                    Log.d(TAG, "========================================")
                    Result.success(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search error", e)
                Result.failure(e)
            }
        }

    /**
     * Search for user by phone (for adding friends)
     */
    suspend fun searchUserByPhone(phone: String): Result<UserProfile?> =
        withContext(Dispatchers.IO) {
            try {
                val response = supabase.from("users")
                    .select {
                        filter {
                            UserProfile::phone eq phone
                        }
                        limit(1)
                    }
                    .decodeList<UserProfile>()

                val user = response.firstOrNull()
                Log.d(TAG, "Search result: ${user?.displayName ?: "not found"}")
                Result.success(user)
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                Result.failure(e)
            }
        }
}