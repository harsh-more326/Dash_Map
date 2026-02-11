package com.example.dash_map.supabase

import android.content.Context
import android.util.Log
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.SupabaseClient

/**
 * Singleton object for Supabase configuration
 *
 * CRITICAL: This must be initialized BEFORE any ViewModel is created
 */
object SupabaseConfig {
    private const val TAG = "SupabaseConfig"

    // Replace these with your actual Supabase credentials
    private const val SUPABASE_URL = "https://ewnvwouvsoftgxhuekgj.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImV3bnZ3b3V2c29mdGd4aHVla2dqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk4NTkwNjQsImV4cCI6MjA4NTQzNTA2NH0.XLi1QXNEmCq8uCjTruJVQU8A-LnrjfqXFzmJg4iD0DU"

    private var _client: SupabaseClient? = null

    val client: SupabaseClient
        get() = _client ?: throw IllegalStateException(
            "SupabaseConfig not initialized. Call initialize() first in MainActivity.onCreate()"
        )

    val auth: Auth
        get() = client.auth

    val postgrest: Postgrest
        get() = client.postgrest

    val realtime: Realtime
        get() = client.realtime

    /**
     * Initialize Supabase client
     * MUST be called in MainActivity.onCreate() BEFORE creating any ViewModels
     */
    fun initialize(context: Context) {
        if (_client != null) {
            Log.d(TAG, "Supabase already initialized")
            return
        }

        try {
            Log.d(TAG, "Initializing Supabase client...")

            _client = createSupabaseClient(
                supabaseUrl = SUPABASE_URL,
                supabaseKey = SUPABASE_KEY
            ) {
                install(Auth) {
                    // ENABLE SESSION PERSISTENCE
                    autoLoadFromStorage = true
                    autoSaveToStorage = true
                    alwaysAutoRefresh = true
                }
                install(Postgrest)
                install(Realtime)
            }

            Log.d(TAG, "✓ Supabase client initialized successfully")
            Log.d(TAG, "✓ Auth module: ${_client?.auth != null}")
            Log.d(TAG, "✓ Postgrest module: ${_client?.postgrest != null}")
            Log.d(TAG, "✓ Realtime module: ${_client?.realtime != null}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Supabase", e)
            throw e
        }
    }

    /**
     * Check if Supabase is initialized
     */
    fun isInitialized(): Boolean = _client != null
}