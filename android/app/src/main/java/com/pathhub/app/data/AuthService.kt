package com.pathhub.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

@Serializable
data class AuthUser(
    val id: String,
    val email: String? = null,
    val isAnonymous: Boolean = true
)

class AuthService(private val context: Context) {
    companion object {
        private const val TAG = "AuthService"
        private const val PREFS_NAME = "path_hub_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_IS_ANON = "is_anon"

        @Volatile
        private var instance: AuthService? = null

        fun getInstance(context: Context): AuthService {
            return instance ?: synchronized(this) {
                instance ?: AuthService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _user = MutableStateFlow<AuthUser?>(loadCachedUser())
    val user: StateFlow<AuthUser?> = _user.asStateFlow()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            if (_user.value == null) {
                signInAnonymously()
            }
        }
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getUserId(): String {
        val cached = _user.value?.id ?: prefs.getString(KEY_USER_ID, null)
        if (!cached.isNullOrBlank()) return cached
        val deviceId = prefs.getString("device_uuid", null) ?: run {
            val newId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_uuid", newId).apply()
            newId
        }
        return deviceId
    }

    private fun loadCachedUser(): AuthUser? {
        val uid = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null)
        val isAnon = prefs.getBoolean(KEY_IS_ANON, true)
        return AuthUser(id = uid, email = email, isAnonymous = isAnon)
    }

    suspend fun signInAnonymously(): Result<AuthUser> = withContext(Dispatchers.IO) {
        val authUrl = SupabaseConfig.getAuthUrl(context)
        val anonKey = SupabaseConfig.getKey(context)

        try {
            // Attempt Supabase anonymous sign-up
            val body = "{}".toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$authUrl/signup")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful && responseBody.isNotEmpty()) {
                val jsonObj = json.parseToJsonElement(responseBody).jsonObject
                val token = jsonObj["access_token"]?.jsonPrimitive?.content ?: anonKey
                val refreshToken = jsonObj["refresh_token"]?.jsonPrimitive?.content ?: ""
                val userObj = jsonObj["user"]?.jsonObject
                val uid = userObj?.get("id")?.jsonPrimitive?.content ?: UUID.randomUUID().toString()

                saveSession(token, refreshToken, uid, null, true)
                val newUser = AuthUser(id = uid, email = null, isAnonymous = true)
                _user.value = newUser
                Result.success(newUser)
            } else {
                Log.w(TAG, "Anonymous sign-up response ${response.code}: $responseBody. Falling back to persistent guest UUID.")
                // Local fallback guest ID when backend requires custom email/pwd
                val existingUid = prefs.getString(KEY_USER_ID, null) ?: UUID.randomUUID().toString()
                saveSession(anonKey, "", existingUid, null, true)
                val guestUser = AuthUser(id = existingUid, email = null, isAnonymous = true)
                _user.value = guestUser
                Result.success(guestUser)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous auth exception", e)
            val existingUid = prefs.getString(KEY_USER_ID, null) ?: UUID.randomUUID().toString()
            saveSession(anonKey, "", existingUid, null, true)
            val guestUser = AuthUser(id = existingUid, email = null, isAnonymous = true)
            _user.value = guestUser
            Result.success(guestUser)
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> = withContext(Dispatchers.IO) {
        val authUrl = SupabaseConfig.getAuthUrl(context)
        val anonKey = SupabaseConfig.getKey(context)

        try {
            val jsonPayload = """{"email":"$email","password":"$password"}"""
            val body = jsonPayload.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$authUrl/token?grant_type=password")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful && responseBody.isNotEmpty()) {
                val jsonObj = json.parseToJsonElement(responseBody).jsonObject
                val token = jsonObj["access_token"]?.jsonPrimitive?.content ?: ""
                val refreshToken = jsonObj["refresh_token"]?.jsonPrimitive?.content ?: ""
                val userObj = jsonObj["user"]?.jsonObject
                val uid = userObj?.get("id")?.jsonPrimitive?.content ?: ""

                saveSession(token, refreshToken, uid, email, false)
                val newUser = AuthUser(id = uid, email = email, isAnonymous = false)
                _user.value = newUser
                Result.success(newUser)
            } else {
                Result.failure(Exception("Login failed: $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, password: String): Result<AuthUser> = withContext(Dispatchers.IO) {
        val authUrl = SupabaseConfig.getAuthUrl(context)
        val anonKey = SupabaseConfig.getKey(context)

        try {
            val jsonPayload = """{"email":"$email","password":"$password"}"""
            val body = jsonPayload.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$authUrl/signup")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful && responseBody.isNotEmpty()) {
                val jsonObj = json.parseToJsonElement(responseBody).jsonObject
                val token = jsonObj["access_token"]?.jsonPrimitive?.content ?: anonKey
                val refreshToken = jsonObj["refresh_token"]?.jsonPrimitive?.content ?: ""
                val userObj = jsonObj["user"]?.jsonObject
                val uid = userObj?.get("id")?.jsonPrimitive?.content ?: ""

                saveSession(token, refreshToken, uid, email, false)
                val newUser = AuthUser(id = uid, email = email, isAnonymous = false)
                _user.value = newUser
                Result.success(newUser)
            } else {
                Result.failure(Exception("Sign up failed: $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        prefs.edit().clear().apply()
        _user.value = null
        CoroutineScope(Dispatchers.IO).launch {
            signInAnonymously()
        }
    }

    private fun saveSession(
        accessToken: String,
        refreshToken: String,
        userId: String,
        email: String?,
        isAnon: Boolean
    ) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, email)
            .putBoolean(KEY_IS_ANON, isAnon)
            .apply()
    }
}
