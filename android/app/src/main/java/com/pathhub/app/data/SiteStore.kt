package com.pathhub.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.pathhub.app.model.Site
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

@Serializable
data class QueuedSiteMutation(
    val type: String, // "INSERT", "UPDATE", "DELETE"
    val site: Site,
    val timestamp: Long = System.currentTimeMillis()
)

class SiteStore(private val context: Context) : RealtimeListener {
    companion object {
        private const val TAG = "SiteStore"
        private const val PREFS_NAME = "path_hub_sites_cache"
        private const val KEY_CACHED_SITES = "cached_sites"
        private const val KEY_MUTATION_QUEUE = "mutation_queue"

        @Volatile
        private var instance: SiteStore? = null

        fun getInstance(context: Context): SiteStore {
            return instance ?: synchronized(this) {
                instance ?: SiteStore(context.applicationContext).also { instance = it }
            }
        }
    }

    private val restClient = SupabaseRestClient(context)
    private val realtimeClient = SupabaseRealtimeClient.getInstance(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val httpClient = OkHttpClient()

    private val _sites = MutableStateFlow<List<Site>>(emptyList())
    val sites: StateFlow<List<Site>> = _sites.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastDeletedSite = MutableStateFlow<Site?>(null)
    val lastDeletedSite: StateFlow<Site?> = _lastDeletedSite.asStateFlow()

    init {
        loadFromCache()
        realtimeClient.addListener(this)
        realtimeClient.connect()

        scope.launch {
            syncSites()
            processMutationQueue()
        }
    }

    private fun loadFromCache() {
        val cached = prefs.getString(KEY_CACHED_SITES, null)
        if (!cached.isNullOrEmpty()) {
            try {
                val list = json.decodeFromString<List<Site>>(cached)
                _sites.value = list.filter { !isDisallowed(it) }.sortedBy { it.sortOrder }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode cached sites", e)
            }
        }

        if (_sites.value.isEmpty()) {
            _sites.value = defaultSeedSites()
            saveToCache(_sites.value)
        }
    }

    private fun saveToCache(list: List<Site>) {
        try {
            val str = json.encodeToString(list)
            prefs.edit().putString(KEY_CACHED_SITES, str).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save sites cache", e)
        }
    }

    private fun isDisallowed(site: Site): Boolean {
        val u = site.url.lowercase()
        val n = site.name.lowercase()
        return u.contains("pathhub.internal") ||
               u.contains("commondatastorage.googleapis.com") ||
               u.contains("archive.org") ||
               u.contains("w3schools.com") ||
               n.contains("big buck bunny") ||
               n.contains("internet archive") ||
               n.contains("html5 video") ||
               n.contains("version_code")
    }

    private fun defaultSeedSites(): List<Site> {
        return listOf(
            Site(
                id = UUID.randomUUID().toString(),
                name = "Pornhub",
                url = "https://www.pornhub.com",
                iconUrl = "https://www.google.com/s2/favicons?domain=pornhub.com&sz=128",
                sortOrder = 0
            ),
            Site(
                id = UUID.randomUUID().toString(),
                name = "XNXX",
                url = "https://www.xnxx.com",
                iconUrl = "https://www.google.com/s2/favicons?domain=xnxx.com&sz=128",
                sortOrder = 1
            ),
            Site(
                id = UUID.randomUUID().toString(),
                name = "XVideos",
                url = "https://www.xvideos.com",
                iconUrl = "https://www.google.com/s2/favicons?domain=xvideos.com&sz=128",
                sortOrder = 2
            )
        )
    }

    fun syncSites() {
        scope.launch {
            _isLoading.value = true
            val result = restClient.fetchSites()
            _isLoading.value = false

            result.onSuccess { rawRemoteList ->
                val remoteList = rawRemoteList.filter { !isDisallowed(it) }
                if (remoteList.isNotEmpty()) {
                    // Smart Reconcile: Preserve any local custom sites that haven't synced to remote yet
                    val remoteUrls = remoteList.map { it.url.lowercase().trim().removeSuffix("/") }.toSet()
                    val unsyncedLocal = _sites.value.filter { localSite ->
                        val localNorm = localSite.url.lowercase().trim().removeSuffix("/")
                        localNorm !in remoteUrls
                    }

                    val merged = (remoteList + unsyncedLocal).sortedBy { it.sortOrder }
                    _sites.value = merged
                    saveToCache(merged)

                    // Push any previously unsynced local sites to Supabase
                    unsyncedLocal.forEach { siteToPush ->
                        launch {
                            val ins = restClient.insertSite(siteToPush)
                            Log.d(TAG, "Reconcile pushed unsynced site ${siteToPush.name}: ${ins.isSuccess}")
                        }
                    }
                } else if (_sites.value.isEmpty()) {
                    // Seed initial sites on remote
                    val defaults = defaultSeedSites()
                    _sites.value = defaults
                    saveToCache(defaults)
                    defaults.forEach { restClient.insertSite(it) }
                }
            }.onFailure { err ->
                Log.w(TAG, "Offline or fetch error: ${err.message}. Using cached sites.")
            }
        }
    }

    suspend fun addSite(rawUrl: String, customName: String? = null): Result<Site> {
        val normalizedUrl = normalizeUrl(rawUrl)

        // Prevent duplicate URLs
        if (_sites.value.any { it.url.equals(normalizedUrl, ignoreCase = true) }) {
            return Result.failure(Exception("Site already exists in your list"))
        }

        val domain = Uri.parse(normalizedUrl).host ?: normalizedUrl
        val iconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=128"
        val resolvedName = if (!customName.isNullOrBlank()) customName else fetchTitle(normalizedUrl) ?: domain

        val maxSort = _sites.value.maxOfOrNull { it.sortOrder } ?: -1
        val newSite = Site(
            id = UUID.randomUUID().toString(),
            name = resolvedName,
            url = normalizedUrl,
            iconUrl = iconUrl,
            sortOrder = maxSort + 1
        )

        // Optimistic local update
        val updatedList = (_sites.value + newSite).sortedBy { it.sortOrder }
        _sites.value = updatedList
        saveToCache(updatedList)

        // Sync with backend / queue if offline
        scope.launch {
            val res = restClient.insertSite(newSite)
            if (res.isFailure) {
                queueMutation("INSERT", newSite)
            }
        }

        return Result.success(newSite)
    }

    fun deleteSite(site: Site) {
        _lastDeletedSite.value = site
        val updatedList = _sites.value.filter { it.id != site.id }
        _sites.value = updatedList
        saveToCache(updatedList)

        scope.launch {
            val res = restClient.deleteSite(site.id)
            if (res.isFailure) {
                queueMutation("DELETE", site)
            }
        }
    }

    fun undoDelete() {
        val site = _lastDeletedSite.value ?: return
        _lastDeletedSite.value = null
        val updatedList = (_sites.value + site).sortedBy { it.sortOrder }
        _sites.value = updatedList
        saveToCache(updatedList)

        scope.launch {
            restClient.insertSite(site)
        }
    }

    fun updateSite(site: Site, newName: String, newUrl: String) {
        val normalized = normalizeUrl(newUrl)
        val domain = Uri.parse(normalized).host ?: normalized
        val updated = site.copy(
            name = newName.ifBlank { site.name },
            url = normalized,
            iconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=128"
        )

        val updatedList = _sites.value.map { if (it.id == site.id) updated else it }
        _sites.value = updatedList
        saveToCache(updatedList)

        scope.launch {
            val res = restClient.updateSite(updated.id, updated.name, updated.url, updated.sortOrder)
            if (res.isFailure) {
                queueMutation("UPDATE", updated)
            }
        }
    }

    fun reorderSites(fromIndex: Int, toIndex: Int) {
        val list = _sites.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)

        val reindexed = list.mapIndexed { index, s -> s.copy(sortOrder = index) }
        _sites.value = reindexed
        saveToCache(reindexed)

        scope.launch {
            reindexed.forEach { s ->
                restClient.updateSite(s.id, s.name, s.url, s.sortOrder)
            }
        }
    }

    private fun normalizeUrl(url: String): String {
        var trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed = "https://$trimmed"
        }
        return trimmed
    }

    private fun fetchTitle(url: String): String? {
        return try {
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val response = httpClient.newCall(request).execute()
            val html = response.body?.string() ?: return null
            val match = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(html)
            match?.groupValues?.get(1)?.trim()
        } catch (e: Exception) {
            null
        }
    }

    // ---------------- Offline Mutation Queue ----------------

    private fun queueMutation(type: String, site: Site) {
        try {
            val raw = prefs.getString(KEY_MUTATION_QUEUE, "[]") ?: "[]"
            val queue = json.decodeFromString<List<QueuedSiteMutation>>(raw).toMutableList()
            queue.add(QueuedSiteMutation(type = type, site = site))
            prefs.edit().putString(KEY_MUTATION_QUEUE, json.encodeToString(queue)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing mutation", e)
        }
    }

    private suspend fun processMutationQueue() {
        val raw = prefs.getString(KEY_MUTATION_QUEUE, "[]") ?: "[]"
        if (raw == "[]") return
        try {
            val queue = json.decodeFromString<List<QueuedSiteMutation>>(raw)
            val remaining = mutableListOf<QueuedSiteMutation>()

            for (m in queue) {
                val ok = when (m.type) {
                    "INSERT" -> restClient.insertSite(m.site).isSuccess
                    "UPDATE" -> restClient.updateSite(m.site.id, m.site.name, m.site.url, m.site.sortOrder).isSuccess
                    "DELETE" -> restClient.deleteSite(m.site.id).isSuccess
                    else -> true
                }
                if (!ok) remaining.add(m)
            }

            prefs.edit().putString(KEY_MUTATION_QUEUE, json.encodeToString(remaining)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing mutation queue", e)
        }
    }

    // ---------------- RealtimeListener ----------------

    override fun onTableChanged(event: RealtimeChangeEvent) {
        if (event.table != "sites") return
        scope.launch {
            try {
                when (event.eventType) {
                    RealtimeEventType.INSERT -> {
                        val newSite = json.decodeFromString<Site>(event.recordJson)
                        val newNorm = newSite.url.lowercase().trim().removeSuffix("/")
                        if (_sites.value.none { it.id == newSite.id || it.url.lowercase().trim().removeSuffix("/") == newNorm }) {
                            val updated = (_sites.value + newSite).sortedBy { it.sortOrder }
                            _sites.value = updated
                            saveToCache(updated)
                        }
                    }
                    RealtimeEventType.UPDATE -> {
                        val updatedSite = json.decodeFromString<Site>(event.recordJson)
                        val updated = _sites.value.map { if (it.id == updatedSite.id) updatedSite else it }
                            .sortedBy { it.sortOrder }
                        _sites.value = updated
                        saveToCache(updated)
                    }
                    RealtimeEventType.DELETE -> {
                        val oldRecord = json.decodeFromString<Site>(event.oldRecordJson)
                        val updated = _sites.value.filter { it.id != oldRecord.id }
                        _sites.value = updated
                        saveToCache(updated)
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling realtime site change", e)
            }
        }
    }
}
