package com.example.dash_map.supabase.viewmodels

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dash_map.supabase.models.Connection
import com.example.dash_map.supabase.models.FriendLocation
import com.example.dash_map.supabase.models.UserProfile
import com.example.dash_map.supabase.models.FriendColorPreferences
import com.example.dash_map.supabase.repositories.AuthRepository
import com.example.dash_map.supabase.repositories.FriendsRepository
import com.example.dash_map.supabase.repositories.LocationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "LocationSharingViewModel"

class LocationSharingViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository()
    private val friendsRepository = FriendsRepository()
    private val locationRepository = LocationRepository()
    private val friendColorPreferences = FriendColorPreferences(application)

    // Current user state
    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    // Authentication state
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    // Location sharing state
    private val _isLocationSharingEnabled = MutableStateFlow(false)
    val isLocationSharingEnabled: StateFlow<Boolean> = _isLocationSharingEnabled.asStateFlow()

    // Friends list
    private val _friends = MutableStateFlow<List<UserProfile>>(emptyList())
    val friends: StateFlow<List<UserProfile>> = _friends.asStateFlow()

    // Friend locations (realtime) - with custom colors applied
    private val _friendLocations = MutableStateFlow<List<FriendLocation>>(emptyList())
    val friendLocations: StateFlow<List<FriendLocation>> = _friendLocations.asStateFlow()

    // Custom friend colors (friendId -> color)
    private val _friendColors = MutableStateFlow<Map<String, String>>(emptyMap())
    val friendColors: StateFlow<Map<String, String>> = _friendColors.asStateFlow()

    // Pending friend requests (received)
    private val _pendingRequests = MutableStateFlow<List<Pair<Connection, UserProfile>>>(emptyList())
    val pendingRequests: StateFlow<List<Pair<Connection, UserProfile>>> = _pendingRequests.asStateFlow()

    // Sent friend requests (outgoing)
    private val _sentRequests = MutableStateFlow<List<Pair<Connection, UserProfile>>>(emptyList())
    val sentRequests: StateFlow<List<Pair<Connection, UserProfile>>> = _sentRequests.asStateFlow()

    // Loading states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var realtimeJob: Job? = null

    init {
        Log.d(TAG, "========================================")
        Log.d(TAG, "ViewModel initialized - Starting checks")
        Log.d(TAG, "========================================")
        checkAuthentication()
    }

    /**
     * Check if user is authenticated (auto-login on app restart)
     */
    fun checkAuthentication() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "→ Checking authentication...")
                val user = authRepository.getCurrentUser()

                if (user != null) {
                    Log.d(TAG, "✓ User session found: ${user.id}")
                    Log.d(TAG, "  Email: ${user.email}")
                    _isAuthenticated.value = true

                    // IMPORTANT: Wait for profile to load FIRST
                    loadUserProfile(user.id)

                    // Load custom friend colors
                    loadFriendColors(user.id)

                    // Then load friends (this was happening too early before)
                    Log.d(TAG, "→ Loading friends and requests...")
                    loadFriends()

                    startRealtimeUpdates()
                } else {
                    Log.d(TAG, "✗ No active session - user needs to login")
                    _isAuthenticated.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error checking authentication", e)
                _isAuthenticated.value = false
            }
        }
    }

    /**
     * Sign in with email and password
     */
    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            Log.d(TAG, "→ Attempting sign in for: $email")

            authRepository.signIn(email, password).fold(
                onSuccess = { user ->
                    Log.d(TAG, "✓ Sign in successful: ${user.id}")
                    _isAuthenticated.value = true
                    loadUserProfile(user.id)
                    loadFriendColors(user.id)
                    loadFriends() // Load friends after sign in
                    startRealtimeUpdates()
                    _isLoading.value = false
                },
                onFailure = { error ->
                    Log.e(TAG, "✗ Sign in failed: ${error.message}", error)
                    _errorMessage.value = error.message ?: "Sign in failed"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Sign up with email, password, and display name
     */
    fun signUp(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            Log.d(TAG, "→ Attempting sign up for: $email")

            authRepository.signUp(email, password, displayName).fold(
                onSuccess = { user ->
                    Log.d(TAG, "✓ Sign up successful: ${user.id}")
                    _isAuthenticated.value = true
                    loadUserProfile(user.id)
                    loadFriendColors(user.id)
                    _isLoading.value = false
                },
                onFailure = { error ->
                    Log.e(TAG, "✗ Sign up failed: ${error.message}", error)
                    _errorMessage.value = error.message ?: "Sign up failed"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Sign out current user
     */
    fun signOut() {
        viewModelScope.launch {
            Log.d(TAG, "→ Signing out...")

            // Stop location sharing first
            _currentUser.value?.id?.let { userId ->
                locationRepository.stopLocationSharing(userId)
            }

            // Stop realtime updates
            stopRealtimeUpdates()

            // Sign out
            authRepository.signOut()

            // Clear state
            _isAuthenticated.value = false
            _currentUser.value = null
            _friends.value = emptyList()
            _friendLocations.value = emptyList()
            _friendColors.value = emptyMap()
            _pendingRequests.value = emptyList()
            _sentRequests.value = emptyList()
            _isLocationSharingEnabled.value = false

            Log.d(TAG, "✓ Signed out successfully")
        }
    }

    /**
     * Load user profile
     */
    private suspend fun loadUserProfile(userId: String) {
        Log.d(TAG, "→ Loading user profile for: $userId")

        authRepository.getUserProfile(userId).fold(
            onSuccess = { profile ->
                _currentUser.value = profile
                _isLocationSharingEnabled.value = profile.isSharingLocation
                Log.d(TAG, "✓ Profile loaded:")
                Log.d(TAG, "  Name: ${profile.displayName}")
                Log.d(TAG, "  Email: ${profile.email}")
                Log.d(TAG, "  Color: ${profile.markerColor}")
                Log.d(TAG, "  Sharing: ${profile.isSharingLocation}")
            },
            onFailure = { error ->
                Log.e(TAG, "✗ Failed to load profile: ${error.message}", error)
                _errorMessage.value = error.message
            }
        )
    }

    /**
     * Load custom friend colors from preferences
     */
    private fun loadFriendColors(userId: String) {
        _friendColors.value = friendColorPreferences.getAllFriendColors(userId)
        Log.d(TAG, "✓ Loaded ${_friendColors.value.size} custom friend colors")
    }

    /**
     * Update user's marker color
     */
    fun updateMarkerColor(color: String) {
        viewModelScope.launch {
            _currentUser.value?.id?.let { userId ->
                Log.d(TAG, "→ Updating marker color to: $color")

                // Update UI immediately
                _currentUser.value = _currentUser.value?.copy(markerColor = color)

                authRepository.updateUserProfile(userId, markerColor = color).fold(
                    onSuccess = {
                        Log.d(TAG, "✓ Marker color updated in database")
                        loadFriendLocations()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "✗ Failed to update marker color: ${error.message}", error)
                        _errorMessage.value = error.message
                        loadUserProfile(userId)
                    }
                )
            }
        }
    }

    /**
     * Update custom marker color for a friend
     */
    fun updateFriendMarkerColor(friendId: String, color: String) {
        _currentUser.value?.id?.let { userId ->
            friendColorPreferences.setFriendColor(userId, friendId, color)
            loadFriendColors(userId)

            // Refresh friend locations to apply new color
            loadFriendLocations()

            Log.d(TAG, "✓ Updated friend $friendId marker color to $color")
        }
    }

    /**
     * Reset friend marker color to default
     */
    fun resetFriendMarkerColor(friendId: String) {
        _currentUser.value?.id?.let { userId ->
            friendColorPreferences.removeFriendColor(userId, friendId)
            loadFriendColors(userId)

            // Refresh friend locations to apply default color
            loadFriendLocations()

            Log.d(TAG, "✓ Reset friend $friendId marker color to default")
        }
    }

    /**
     * Get effective marker color for a friend (custom or default)
     */
    fun getFriendMarkerColor(friendId: String, defaultColor: String): String {
        return _currentUser.value?.id?.let { userId ->
            friendColorPreferences.getFriendColor(userId, friendId) ?: defaultColor
        } ?: defaultColor
    }

    /**
     * Toggle location sharing on/off
     */
    fun toggleLocationSharing(enabled: Boolean) {
        viewModelScope.launch {
            _currentUser.value?.id?.let { userId ->
                Log.d(TAG, "→ Toggling location sharing: $enabled")

                authRepository.toggleLocationSharing(userId, enabled).fold(
                    onSuccess = {
                        _isLocationSharingEnabled.value = enabled
                        Log.d(TAG, "✓ Location sharing: $enabled")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "✗ Failed to toggle location sharing: ${error.message}", error)
                        _errorMessage.value = error.message
                    }
                )
            }
        }
    }

    /**
     * Update user's current location
     */
    fun updateLocation(location: Location) {
        if (!_isLocationSharingEnabled.value) return

        viewModelScope.launch {
            _currentUser.value?.id?.let { userId ->
                locationRepository.updateLocation(userId, location)
                Log.d(TAG, "✓ Location updated: ${location.latitude}, ${location.longitude}")
            }
        }
    }

    /**
     * Load accepted friends, pending requests, AND sent requests
     */
    fun loadFriends() {
        viewModelScope.launch {
            _currentUser.value?.id?.let { userId ->
                Log.d(TAG, "========================================")
                Log.d(TAG, "LOADING FRIENDS DATA FOR USER: $userId")
                Log.d(TAG, "========================================")

                // 1. Load accepted friends
                Log.d(TAG, "→ Loading accepted friends...")
                friendsRepository.getAcceptedFriends(userId).fold(
                    onSuccess = { friendsList ->
                        _friends.value = friendsList
                        Log.d(TAG, "✓ Loaded ${friendsList.size} accepted friends:")
                        friendsList.forEach { friend ->
                            Log.d(TAG, "  - ${friend.displayName} (${friend.email})")
                        }
                        loadFriendLocations()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "✗ Failed to load friends: ${error.message}", error)
                        _errorMessage.value = error.message
                    }
                )

                // 2. Load pending requests (RECEIVED)
                Log.d(TAG, "→ Loading pending friend requests (received)...")
                friendsRepository.getPendingRequests(userId).fold(
                    onSuccess = { requests ->
                        _pendingRequests.value = requests
                        Log.d(TAG, "✓ Loaded ${requests.size} PENDING REQUESTS (received):")
                        requests.forEach { (connection, requester) ->
                            Log.d(TAG, "  - From: ${requester.displayName} (${requester.email})")
                            Log.d(TAG, "    Connection ID: ${connection.id}")
                            Log.d(TAG, "    Status: ${connection.status}")
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "✗ Failed to load pending requests: ${error.message}", error)
                    }
                )

                // 3. Load sent requests (OUTGOING)
                Log.d(TAG, "→ Loading sent friend requests (outgoing)...")
                friendsRepository.getSentRequests(userId).fold(
                    onSuccess = { requests ->
                        _sentRequests.value = requests
                        Log.d(TAG, "✓ Loaded ${requests.size} SENT REQUESTS (outgoing):")
                        requests.forEach { (connection, receiver) ->
                            Log.d(TAG, "  - To: ${receiver.displayName} (${receiver.email})")
                            Log.d(TAG, "    Connection ID: ${connection.id}")
                            Log.d(TAG, "    Status: ${connection.status}")
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "✗ Failed to load sent requests: ${error.message}", error)
                    }
                )

                Log.d(TAG, "========================================")
                Log.d(TAG, "SUMMARY:")
                Log.d(TAG, "  Friends: ${_friends.value.size}")
                Log.d(TAG, "  Pending (received): ${_pendingRequests.value.size}")
                Log.d(TAG, "  Sent (outgoing): ${_sentRequests.value.size}")
                Log.d(TAG, "========================================")
            } ?: run {
                Log.e(TAG, "✗ Cannot load friends - no current user")
            }
        }
    }

    /**
     * Load friend locations WITH CUSTOM COLORS APPLIED
     */
    private fun loadFriendLocations() {
        viewModelScope.launch {
            val friendIds = _friends.value.map { it.id }

            if (friendIds.isEmpty()) {
                Log.d(TAG, "→ No friends to load locations for")
                _friendLocations.value = emptyList()
                return@launch
            }

            Log.d(TAG, "→ Loading locations for ${friendIds.size} friends...")

            locationRepository.getFriendLocations(friendIds).fold(
                onSuccess = { locations ->
                    // Apply custom colors to friend locations
                    val locationsWithCustomColors = locations.map { location ->
                        val customColor = _currentUser.value?.id?.let { userId ->
                            friendColorPreferences.getFriendColor(userId, location.friendId)
                        }
                        if (customColor != null) {
                            location.copy(markerColor = customColor)
                        } else {
                            location
                        }
                    }

                    _friendLocations.value = locationsWithCustomColors
                    Log.d(TAG, "✓ Loaded ${locationsWithCustomColors.size} friend locations (with custom colors)")
                    locationsWithCustomColors.forEach { loc ->
                        Log.d(TAG, "  - ${loc.displayName}: (${loc.latitude}, ${loc.longitude}) color=${loc.markerColor}")
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "✗ Failed to load friend locations: ${error.message}", error)
                }
            )
        }
    }

    /**
     * Start realtime location updates
     */
    private fun startRealtimeUpdates() {
        stopRealtimeUpdates()

        val friendIds = _friends.value.map { it.id }
        if (friendIds.isEmpty()) {
            Log.d(TAG, "→ No friends to subscribe to realtime updates")
            return
        }

        Log.d(TAG, "→ Starting realtime updates for ${friendIds.size} friends")

        realtimeJob = viewModelScope.launch {
            try {
                locationRepository.subscribeFriendLocations(friendIds)
                    .collect { friendLocation ->
                        // Apply custom color if exists
                        val customColor = _currentUser.value?.id?.let { userId ->
                            friendColorPreferences.getFriendColor(userId, friendLocation.friendId)
                        }

                        val locationWithColor = if (customColor != null) {
                            friendLocation.copy(markerColor = customColor)
                        } else {
                            friendLocation
                        }

                        val currentLocations = _friendLocations.value.toMutableList()
                        currentLocations.removeAll { it.friendId == locationWithColor.friendId }
                        currentLocations.add(locationWithColor)
                        _friendLocations.value = currentLocations

                        Log.d(TAG, "✓ Realtime update: ${locationWithColor.displayName} at (${locationWithColor.latitude}, ${locationWithColor.longitude}) color=${locationWithColor.markerColor}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Realtime subscription error: ${e.message}", e)
            }
        }
    }

    /**
     * Stop realtime updates
     */
    private fun stopRealtimeUpdates() {
        realtimeJob?.cancel()
        realtimeJob = null
        Log.d(TAG, "→ Stopped realtime updates")
    }

    /**
     * Send friend request by email
     */
    fun sendFriendRequest(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            Log.d(TAG, "→ Searching for user with email: $email")

            authRepository.searchUserByEmail(email).fold(
                onSuccess = { targetUser ->
                    if (targetUser == null) {
                        val msg = "User not found with email: $email"
                        Log.e(TAG, "✗ $msg")
                        _errorMessage.value = msg
                        _isLoading.value = false
                        return@fold
                    }

                    Log.d(TAG, "✓ Found user: ${targetUser.displayName} (${targetUser.id})")

                    if (targetUser.id == _currentUser.value?.id) {
                        val msg = "You can't add yourself as a friend"
                        Log.e(TAG, "✗ $msg")
                        _errorMessage.value = msg
                        _isLoading.value = false
                        return@fold
                    }

                    _currentUser.value?.id?.let { userId ->
                        Log.d(TAG, "→ Sending friend request from $userId to ${targetUser.id}")

                        friendsRepository.sendFriendRequest(userId, targetUser.id).fold(
                            onSuccess = { connection ->
                                Log.d(TAG, "✓ Friend request sent successfully!")
                                Log.d(TAG, "  Connection ID: ${connection.id}")
                                Log.d(TAG, "  To: ${targetUser.displayName}")
                                _errorMessage.value = null
                                loadFriends() // Reload to show in sent requests
                            },
                            onFailure = { error ->
                                Log.e(TAG, "✗ Failed to send friend request: ${error.message}", error)
                                _errorMessage.value = error.message
                            }
                        )
                    }
                    _isLoading.value = false
                },
                onFailure = { error ->
                    Log.e(TAG, "✗ Failed to search user: ${error.message}", error)
                    _errorMessage.value = error.message
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Cancel a sent friend request
     */
    fun cancelSentRequest(connectionId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            Log.d(TAG, "→ Cancelling sent request: $connectionId")

            friendsRepository.rejectFriendRequest(connectionId).fold(
                onSuccess = {
                    Log.d(TAG, "✓ Sent request cancelled")
                    loadFriends()
                },
                onFailure = { error ->
                    Log.e(TAG, "✗ Failed to cancel sent request: ${error.message}", error)
                    _errorMessage.value = "Failed to cancel: ${error.message}"
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * Accept a friend request
     */
    fun acceptFriendRequest(connectionId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            Log.d(TAG, "→ Accepting friend request: $connectionId")

            friendsRepository.acceptFriendRequest(connectionId).fold(
                onSuccess = {
                    Log.d(TAG, "✓ Friend request accepted")
                    loadFriends()
                    startRealtimeUpdates()
                },
                onFailure = { error ->
                    Log.e(TAG, "✗ Failed to accept friend request: ${error.message}", error)
                    _errorMessage.value = "Failed to accept: ${error.message}"
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * Reject a friend request
     */
    fun rejectFriendRequest(connectionId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            Log.d(TAG, "→ Rejecting friend request: $connectionId")

            friendsRepository.rejectFriendRequest(connectionId).fold(
                onSuccess = {
                    Log.d(TAG, "✓ Friend request rejected")
                    loadFriends()
                },
                onFailure = { error ->
                    Log.e(TAG, "✗ Failed to reject friend request: ${error.message}", error)
                    _errorMessage.value = "Failed to reject: ${error.message}"
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * Remove a friend
     */
    fun removeFriend(friendId: String) {
        viewModelScope.launch {
            _currentUser.value?.id?.let { userId ->
                Log.d(TAG, "→ Removing friend: $friendId")

                friendsRepository.removeFriend(userId, friendId).fold(
                    onSuccess = {
                        Log.d(TAG, "✓ Friend removed")
                        _friends.value = _friends.value.filter { it.id != friendId }
                        _friendLocations.value = _friendLocations.value.filter { it.friendId != friendId }
                        startRealtimeUpdates()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "✗ Failed to remove friend: ${error.message}", error)
                        _errorMessage.value = error.message
                    }
                )
            }
        }
    }

    /**
     * Manual refresh - for debugging
     */
    fun manualRefresh() {
        Log.d(TAG, "========================================")
        Log.d(TAG, "MANUAL REFRESH TRIGGERED")
        Log.d(TAG, "========================================")
        loadFriends()
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopRealtimeUpdates()
        Log.d(TAG, "ViewModel cleared")
    }
}