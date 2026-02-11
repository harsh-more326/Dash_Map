package com.example.dash_map.supabase.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onClearError: () -> Unit
) {
    var isSignUpMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = if (isSignUpMode) "Create Account" else "Welcome Back",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isSignUpMode)
                    "Sign up to share your location with friends"
                else
                    "Sign in to continue",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Display Name (Sign Up only)
            if (isSignUpMode) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, "Display Name")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF007AFF),
                        unfocusedBorderColor = Color(0xFF8E8E93),
                        focusedLabelColor = Color(0xFF007AFF),
                        unfocusedLabelColor = Color(0xFF8E8E93),
                        focusedLeadingIconColor = Color(0xFF007AFF),
                        unfocusedLeadingIconColor = Color(0xFF8E8E93)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    if (errorMessage != null) onClearError()
                },
                label = { Text("Email") },
                leadingIcon = {
                    Icon(Icons.Default.Email, "Email")
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF007AFF),
                    unfocusedBorderColor = Color(0xFF8E8E93),
                    focusedLabelColor = Color(0xFF007AFF),
                    unfocusedLabelColor = Color(0xFF8E8E93),
                    focusedLeadingIconColor = Color(0xFF007AFF),
                    unfocusedLeadingIconColor = Color(0xFF8E8E93)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    if (errorMessage != null) onClearError()
                },
                label = { Text("Password") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, "Password")
                },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (isSignUpMode && displayName.isNotBlank() && email.isNotBlank() && password.isNotBlank()) {
                            onSignUp(email, password, displayName)
                        } else if (!isSignUpMode && email.isNotBlank() && password.isNotBlank()) {
                            onSignIn(email, password)
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF007AFF),
                    unfocusedBorderColor = Color(0xFF8E8E93),
                    focusedLabelColor = Color(0xFF007AFF),
                    unfocusedLabelColor = Color(0xFF8E8E93),
                    focusedLeadingIconColor = Color(0xFF007AFF),
                    unfocusedLeadingIconColor = Color(0xFF8E8E93)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Error message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = Color(0xFFFF3B30),
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    if (isSignUpMode) {
                        if (displayName.isNotBlank() && email.isNotBlank() && password.isNotBlank()) {
                            onSignUp(email, password, displayName)
                        }
                    } else {
                        if (email.isNotBlank() && password.isNotBlank()) {
                            onSignIn(email, password)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoading &&
                        email.isNotBlank() &&
                        password.isNotBlank() &&
                        (!isSignUpMode || displayName.isNotBlank()),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF007AFF),
                    disabledContainerColor = Color(0xFF007AFF).copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isSignUpMode) "Sign Up" else "Sign In",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Toggle Sign Up/Sign In
            TextButton(
                onClick = { isSignUpMode = !isSignUpMode }
            ) {
                Text(
                    text = if (isSignUpMode)
                        "Already have an account? Sign In"
                    else
                        "Don't have an account? Sign Up",
                    color = Color(0xFF007AFF),
                    fontSize = 15.sp
                )
            }
        }
    }
}