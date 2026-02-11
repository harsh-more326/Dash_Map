package com.example.dash_map

import android.app.Application
import android.util.Log
import com.example.dash_map.supabase.SupabaseConfig

class DashMapApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Log.d("DashMapApplication", "=====================================")
        Log.d("DashMapApplication", "Application onCreate() started")
        Log.d("DashMapApplication", "=====================================")

        // Initialize Supabase SYNCHRONOUSLY - this MUST complete before onCreate returns
        SupabaseConfig.initialize(this)

        Log.d("DashMapApplication", "✓ Application initialization complete")
        Log.d("DashMapApplication", "=====================================")
    }
}