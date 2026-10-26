package com.pathhub.app.data

import android.content.Context
import android.content.SharedPreferences

object SupabaseConfig {
    const val DEFAULT_URL = "https://ejobnyceertuvwkjfaxk.supabase.co"
    const val DEFAULT_KEY = "sb_publishable_tzwP5svHwAtTr3AnZSMOqg_EmStv2be"

    private const val PREFS_NAME = "path_hub_supabase_config"
    private const val KEY_URL = "supabase_url"
    private const val KEY_ANON_KEY = "supabase_anon_key"

    fun getUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    fun getKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ANON_KEY, DEFAULT_KEY) ?: DEFAULT_KEY
    }

    fun saveConfig(context: Context, url: String, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_URL, url.trim().removeSuffix("/"))
            .putString(KEY_ANON_KEY, key.trim())
            .apply()
    }

    fun getRestUrl(context: Context): String = "${getUrl(context)}/rest/v1"
    fun getAuthUrl(context: Context): String = "${getUrl(context)}/auth/v1"
    fun getRealtimeWsUrl(context: Context): String {
        val base = getUrl(context).replace("https://", "wss://").replace("http://", "ws://")
        return "$base/realtime/v1/websocket?apikey=${getKey(context)}&vsn=1.0.0"
    }
}
