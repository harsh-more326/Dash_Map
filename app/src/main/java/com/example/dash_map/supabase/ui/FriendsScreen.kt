package com.example.dash_map.supabase.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.dash_map.supabase.models.UserProfile
import com.example.dash_map.supabase.models.Connection
import java.time.Instant
import java.time.temporal.ChronoUnit


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    currentUser: UserProfile?,
    friends: List<UserProfile>,
    pendingRequests: List<Pair<Connection, UserProfile>> = emptyList(),
    sentRequests: List<Pair<Connection, UserProfile>>,
    isLocationSharingEnabled: Boolean,
    onToggleLocationSharing: (Boolean) -> Unit,
    onSendFriendRequest: (String) -> Unit,
    onAcceptFriendRequest: (String) -> Unit = {},
    onRejectFriendRequest: (String) -> Unit = {},
    onCancelSentRequest: (String) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onUpdateMarkerColor: (String) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit = {},
    onManualRefresh: () -> Unit = {}
) {
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }

    // DEBUG: Log the current state whenever it changes
    LaunchedEffect(friends, pendingRequests, sentRequests) {
        Log.d("FriendsScreen", "========================================")
        Log.d("FriendsScreen", "UI STATE UPDATE:")
        Log.d("FriendsScreen", "  Friends: ${friends.size}")
        friends.forEach { Log.d("FriendsScreen", "    - ${it.displayName}") }
        Log.d("FriendsScreen", "  Pending Requests (received): ${pendingRequests.size}")
        pendingRequests.forEach { (_, user) -> Log.d("FriendsScreen", "    - From: ${user.displayName}") }
        Log.d("FriendsScreen", "  Sent Requests (outgoing): ${sentRequests.size}")
        sentRequests.forEach { (_, user) -> Log.d("FriendsScreen", "    - To: ${user.displayName}") }
        Log.d("FriendsScreen", "========================================")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Friends & Settings")
                        Text(
                            "F:${friends.size} P:${pendingRequests.size} S:${sentRequests.size}",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    // DEBUG REFRESH BUTTON
                    IconButton(onClick = {
                        Log.d("FriendsScreen", "🔄 Manual refresh button clicked")
                        onManualRefresh()

                    }) {

                        Icon(Icons.Default.Refresh, "Refresh", tint = Color(0xFF34C759))
                    }

                    IconButton(onClick = { showSettingsMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Menu", tint = Color.White)
                    }

                    DropdownMenu(
                        expanded = showSettingsMenu,
                        onDismissRequest = { showSettingsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sign Out") },
                            onClick = {
                                showSettingsMenu = false
                                onSignOut()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ExitToApp, "Sign Out")
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddFriendDialog = true },
                containerColor = androidx.compose.ui.graphics.Color(0xFF007AFF)
            ) {
                Icon(Icons.Default.PersonAdd, "Add Friend", tint = Color.White)
            }
        },
        containerColor = androidx.compose.ui.graphics.Color(0xFF000000)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current user card
            item {
                currentUser?.let { user ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(user.markerColor)))
                                        .clickable { showColorPicker = true }
                                        .border(2.dp, Color.White, CircleShape)
                                )

                                Column {
                                    Text(
                                        text = user.displayName,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = user.email,
                                        fontSize = 14.sp,
                                        color = androidx.compose.ui.graphics.Color(0xFF8E8E93)
                                    )
                                    // DEBUG: Show user ID
                                    Text(
                                        text = "ID: ${user.id.take(8)}...",
                                        fontSize = 10.sp,
                                        color = androidx.compose.ui.graphics.Color(0xFF636366)
                                    )
                                }
                            }

                            Divider(color = androidx.compose.ui.graphics.Color(0xFF3A3A3C))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Share My Location",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (isLocationSharingEnabled) "Friends can see you" else "Location hidden",
                                        fontSize = 12.sp,
                                        color = androidx.compose.ui.graphics.Color(0xFF8E8E93)
                                    )
                                }

                                Switch(
                                    checked = isLocationSharingEnabled,
                                    onCheckedChange = onToggleLocationSharing,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = androidx.compose.ui.graphics.Color(0xFF34C759),
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = androidx.compose.ui.graphics.Color(0xFF3A3A3C)
                                    )
                                )
                            }

                            Text(
                                text = "Tap the colored circle to change your marker color",
                                fontSize = 12.sp,
                                color = androidx.compose.ui.graphics.Color(0xFF8E8E93)
                            )
                        }
                    }
                }
            }

            // Pending friend requests section (RECEIVED)
            if (pendingRequests.isNotEmpty()) {
                item {
                    Text(
                        text = "Pending Requests (${pendingRequests.size}) - Received",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(pendingRequests) { (connection, requester) ->
                    PendingRequestItem(
                        requester = requester,
                        connectionId = connection.id,
                        onAccept = { onAcceptFriendRequest(connection.id) },
                        onReject = { onRejectFriendRequest(connection.id) }
                    )
                }
            } else {
                // Show empty state for pending requests
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF2C2C2E).copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Inbox,
                                null,
                                tint = androidx.compose.ui.graphics.Color(0xFF636366)
                            )
                            Text(
                                "No pending friend requests",
                                color = androidx.compose.ui.graphics.Color(0xFF636366),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Sent requests section (OUTGOING)
            if (sentRequests.isNotEmpty()) {
                item {
                    Text(
                        text = "Sent Requests (${sentRequests.size}) - Waiting",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(sentRequests) { (connection, receiverProfile) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF2C2C2E)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(receiverProfile.markerColor)))
                                        .border(2.dp, Color.White, CircleShape)
                                )

                                Column {
                                    Text(
                                        text = receiverProfile.displayName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Waiting for response...",
                                        fontSize = 12.sp,
                                        color = Color(0xFFFFA500)
                                    )
                                    // DEBUG: Show connection ID
                                    Text(
                                        text = "ID: ${connection.id.take(8)}...",
                                        fontSize = 10.sp,
                                        color = Color(0xFF636366)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    Log.d("FriendsScreen", "Cancelling request: ${connection.id}")
                                    onCancelSentRequest(connection.id)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF3B30)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Cancel", fontSize = 14.sp)
                            }
                        }
                    }
                }
            } else {
                // Show empty state for sent requests
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF2C2C2E).copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Send,
                                null,
                                tint = androidx.compose.ui.graphics.Color(0xFF636366)
                            )
                            Text(
                                "No pending sent requests",
                                color = androidx.compose.ui.graphics.Color(0xFF636366),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Friends section
            item {
                Text(
                    text = "Friends (${friends.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (friends.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.PersonAdd,
                                "No friends",
                                tint = androidx.compose.ui.graphics.Color(0xFF8E8E93),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No friends yet",
                                fontSize = 16.sp,
                                color = androidx.compose.ui.graphics.Color(0xFF8E8E93)
                            )
                            Text(
                                text = "Tap + to add friends",
                                fontSize = 14.sp,
                                color = androidx.compose.ui.graphics.Color(0xFF636366)
                            )
                        }
                    }
                }
            } else {
                items(friends) { friend ->
                    FriendItem(
                        friend = friend,
                        onRemove = { onRemoveFriend(friend.id) }
                    )
                }
            }
        }
    }

    if (showAddFriendDialog) {
        AddFriendDialog(
            onDismiss = { showAddFriendDialog = false },
            onSendRequest = { email ->
                Log.d("FriendsScreen", "Sending friend request to: $email")
                onSendFriendRequest(email)
                showAddFriendDialog = false
            }
        )
    }

    if (showColorPicker) {
        currentUser?.let { user ->
            ColorPickerDialog(
                currentColor = user.markerColor,
                onDismiss = { showColorPicker = false },
                onColorSelected = { color ->
                    onUpdateMarkerColor(color)
                    showColorPicker = false
                }
            )
        }
    }
}

@Composable
fun PendingRequestItem(
    requester: UserProfile,
    connectionId: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(requester.markerColor)))
                        .border(2.dp, Color.White, CircleShape)
                )

                Column {
                    Text(
                        text = requester.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Text(
                        text = "wants to be friends",
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color(0xFF8E8E93)
                    )
                    // DEBUG: Show connection ID
                    Text(
                        text = "ID: ${connectionId.take(8)}...",
                        fontSize = 10.sp,
                        color = androidx.compose.ui.graphics.Color(0xFF636366)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = {
                    Log.d("PendingRequestItem", "Rejecting: $connectionId")
                    onReject()
                }) {
                    Icon(
                        Icons.Default.Close,
                        "Reject",
                        tint = androidx.compose.ui.graphics.Color(0xFFFF3B30)
                    )
                }
                IconButton(onClick = {
                    Log.d("PendingRequestItem", "Accepting: $connectionId")
                    onAccept()
                }) {
                    Icon(
                        Icons.Default.Check,
                        "Accept",
                        tint = androidx.compose.ui.graphics.Color(0xFF34C759)
                    )
                }
            }
        }
    }
}

@Composable
fun FriendItem(
    friend: UserProfile,
    onRemove: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Friend's marker color
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(friend.markerColor)))
                        .border(2.dp, Color.White, CircleShape)
                )

                Column {
                    Text(
                        text = friend.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (friend.isSharingLocation)
                                Icons.Default.LocationOn
                            else
                                Icons.Default.LocationOff,
                            contentDescription = null,
                            tint = if (friend.isSharingLocation)
                                androidx.compose.ui.graphics.Color(0xFF34C759)
                            else
                                androidx.compose.ui.graphics.Color(0xFF8E8E93),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (friend.isSharingLocation) "Sharing" else "Not sharing",
                            fontSize = 12.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF8E8E93)
                        )
                    }

                    // Show last update time
                    friend.lastLocationUpdated?.let { timestamp ->
                        val lastUpdateText = remember(timestamp) {
                            try {
                                val lastUpdate = Instant.parse(timestamp)
                                val minutesAgo = ChronoUnit.MINUTES.between(lastUpdate, Instant.now())
                                "Last seen: $minutesAgo min ago"
                            } catch (e: Exception) {
                                "Last seen: Unknown"
                            }
                        }
                        Text(
                            text = lastUpdateText,
                            fontSize = 12.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF636366)
                        )
                    }
                }
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.PersonRemove,
                    "Remove friend",
                    tint = androidx.compose.ui.graphics.Color(0xFFFF3B30)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove Friend?") },
            text = { Text("Are you sure you want to remove ${friend.displayName}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = androidx.compose.ui.graphics.Color(0xFFFF3B30)
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AddFriendDialog(
    onDismiss: () -> Unit,
    onSendRequest: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Add Friend",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Friend's Email") },
                    placeholder = { Text("friend@example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = androidx.compose.ui.graphics.Color(0xFF007AFF),
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color(0xFF8E8E93)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = Color.White)
                    }

                    Button(
                        onClick = {
                            if (email.isNotBlank()) {
                                onSendRequest(email)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = email.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF007AFF)
                        )
                    ) {
                        Text("Send Request")
                    }
                }
            }
        }
    }
}

@Composable
fun ColorPickerDialog(
    currentColor: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val colors = listOf(
        "#001EFF" to "Blue",
        "#39FF14" to "Green",
        "#FE0000" to "Red",
        "#FF8F00" to "Orange",
        "#FFE700" to "Yellow",
        "#F000FF" to "Pink",
        "#4DEEEA" to "Turquoise",
        "#7D12FF" to "Purple"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Choose Marker Color",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Color grid
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    colors.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { (hex, name) ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(hex)))
                                        .clickable { onColorSelected(hex) }
                                        .border(
                                            width = if (hex == currentColor) 3.dp else 0.dp,
                                            color = Color.Black,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = Color.White)
                }
            }
        }
    }
}