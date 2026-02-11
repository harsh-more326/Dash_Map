package com.example.dash_map.supabase.models

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

private const val TAG = "FriendColorPrefs"
private const val PREFS_NAME = "friend_color_preferences"

/**
 * Manages custom marker colors for friends
 * Stores preferences locally using SharedPreferences
 */
class FriendColorPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Set custom marker color for a friend
     */
    fun setFriendColor(userId: String, friendId: String, color: String) {
        val key = "${userId}_${friendId}"
        prefs.edit().putString(key, color).apply()
        Log.d(TAG, "Set color for friend $friendId: $color")
    }

    /**
     * Get custom marker color for a friend
     * Returns null if no custom color is set (use friend's default color)
     */
    fun getFriendColor(userId: String, friendId: String): String? {
        val key = "${userId}_${friendId}"
        return prefs.getString(key, null)
    }

    /**
     * Remove custom color for a friend (revert to their default)
     */
    fun removeFriendColor(userId: String, friendId: String) {
        val key = "${userId}_${friendId}"
        prefs.edit().remove(key).apply()
        Log.d(TAG, "Removed custom color for friend $friendId")
    }

    /**
     * Get all custom friend colors for a user
     */
    fun getAllFriendColors(userId: String): Map<String, String> {
        val prefix = "${userId}_"
        return prefs.all
            .filterKeys { it.startsWith(prefix) }
            .mapKeys { it.key.removePrefix(prefix) }
            .mapValues { it.value as String }
    }

    /**
     * Clear all custom colors for a user
     */
    fun clearAllForUser(userId: String) {
        val prefix = "${userId}_"
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(prefix) }
            .forEach { editor.remove(it) }
        editor.apply()
        Log.d(TAG, "Cleared all custom colors for user $userId")
    }
}