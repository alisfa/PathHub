package com.pathhub.app.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.pathhub.app.model.HistoryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

class HistoryStore(private val context: Context) : RealtimeListener {
    companion object {
        private const val TAG = "HistoryStore"
        private const val PREFS_NAME = "path_hub_history_cache"
        private const val KEY_CACHED_HISTORY = "cached_history_items"

        @Volatile
        private var instance: HistoryStore? = null

        fun getInstance(context: Context): HistoryStore {
            return instance ?: synchronized(this) {
                instance ?: HistoryStore(context.applicationContext).also { instance = it }
            }
        }

        fun getDeviceName(): String {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
            val model = Build.MODEL
            return if (model.startsWith(manufacturer, ignoreCase = true)) {
                model
            } else {
                "$manufacturer $model"
            }
        }
    }

    private val restClient = SupabaseRestClient(context)
    private val realtimeClient = SupabaseRealtimeClient.getInstance(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    val items: StateFlow<List<HistoryItem>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var lastRecordedUrl: String? = null
    private var lastRecordedTime: Long = 0L

    init {
        loadCache()
        realtimeClient.addListener(this)
        fetchHistory()
    }

    private fun loadCache() {
        try {
            val cachedJson = prefs.getString(KEY_CACHED_HISTORY, null)
            if (!cachedJson.isNullOrBlank()) {
                val cached = json.decodeFromString<List<HistoryItem>>(cachedJson)
                _items.value = cached
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached history: ${e.message}")
        }
    }

    private fun saveCache(items: List<HistoryItem>) {
        try {
            prefs.edit().putString(KEY_CACHED_HISTORY, json.encodeToString(items)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save history cache: ${e.message}")
        }
    }

    fun fetchHistory() {
        scope.launch {
            _isLoading.value = true
            val res = restClient.fetchHistory()
            _isLoading.value = false
            res.onSuccess { remoteItems ->
                _items.value = remoteItems
                saveCache(remoteItems)
            }.onFailure { err ->
                Log.e(TAG, "Failed to fetch history: ${err.message}")
            }
        }
    }

    fun recordVisit(url: String, title: String = "") {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || cleanUrl.startsWith("about:") || cleanUrl.startsWith("chrome:") || cleanUrl.startsWith("javascript:")) {
            return
        }

        val now = System.currentTimeMillis()
        if (cleanUrl == lastRecordedUrl && (now - lastRecordedTime) < 20_000L) {
            return
        }
        lastRecordedUrl = cleanUrl
        lastRecordedTime = now

        val deviceName = getDeviceName()
        val tempItem = HistoryItem(
            id = "local-" + java.util.UUID.randomUUID().toString(),
            url = cleanUrl,
            title = title.ifBlank { cleanUrl },
            deviceName = deviceName,
            visitedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date(now)),
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date(now))
        )

        // Optimistically prepend
        val updated = listOf(tempItem) + _items.value.filterNot { it.id == tempItem.id }
        _items.value = updated
        saveCache(updated)

        scope.launch {
            val res = restClient.insertHistoryItem(cleanUrl, title, deviceName)
            if (res.isSuccess) {
                fetchHistory()
            }
        }
    }

    fun deleteItem(id: String) {
        val updated = _items.value.filterNot { it.id == id }
        _items.value = updated
        saveCache(updated)

        scope.launch {
            restClient.deleteHistoryItem(id)
        }
    }

    fun clearHistory() {
        _items.value = emptyList()
        saveCache(emptyList())

        scope.launch {
            restClient.clearHistory()
        }
    }

    override fun onTableChanged(event: RealtimeChangeEvent) {
        if (event.table == "playlist_items") {
            try {
                if (event.recordJson.contains(SupabaseRestClient.BALABOO_PLAYLIST_ID) ||
                    event.oldRecordJson.contains(SupabaseRestClient.BALABOO_PLAYLIST_ID) ||
                    event.recordJson.contains("urn:balaboo:hist") ||
                    event.oldRecordJson.contains("urn:balaboo:hist")
                ) {
                    fetchHistory()
                }
            } catch (e: Exception) {
                fetchHistory()
            }
        }
    }
}
