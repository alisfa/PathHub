package com.pathhub.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.pathhub.app.model.Playlist
import com.pathhub.app.model.PlaylistItem
import com.pathhub.app.model.VideoSavePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class QueuedPlaylistMutation(
    val type: String,
    val item: PlaylistItem,
    val timestamp: Long = System.currentTimeMillis()
)

class PlaylistStore(private val context: Context) : RealtimeListener {
    companion object {
        private const val TAG = "PlaylistStore"
        private const val PREFS_NAME = "path_hub_playlist_cache"
        private const val KEY_CACHED_ITEMS = "cached_playlist_items"
        private const val KEY_ACTIVE_PLAYLIST = "active_playlist"
        private const val KEY_PLAYLIST_MUTATION_QUEUE = "playlist_mutation_queue"

        @Volatile
        private var instance: PlaylistStore? = null

        fun getInstance(context: Context): PlaylistStore {
            return instance ?: synchronized(this) {
                instance ?: PlaylistStore(context.applicationContext).also { instance = it }
            }
        }
    }

    private val restClient = SupabaseRestClient(context)
    private val realtimeClient = SupabaseRealtimeClient.getInstance(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _items = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val items: StateFlow<List<PlaylistItem>> = _items.asStateFlow()

    private val _activePlaylist = MutableStateFlow<Playlist?>(null)
    val activePlaylist: StateFlow<Playlist?> = _activePlaylist.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadFromCache()
        if (_activePlaylist.value == null) {
            _activePlaylist.value = Playlist(
                id = "00000000-0000-0000-0000-000000000001",
                name = "Shared Playlist",
                inviteCode = "HUB777",
                ownerId = "00000000-0000-0000-0000-000000000000"
            )
            saveToCache()
        }
        realtimeClient.addListener(this)
        realtimeClient.connect()

        scope.launch {
            syncItems("00000000-0000-0000-0000-000000000001")
            processMutationQueue()
        }
    }

    private fun loadFromCache() {
        val cachedPlaylist = prefs.getString(KEY_ACTIVE_PLAYLIST, null)
        if (!cachedPlaylist.isNullOrEmpty()) {
            try {
                _activePlaylist.value = json.decodeFromString<Playlist>(cachedPlaylist)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode cached playlist", e)
            }
        }

        val cachedItems = prefs.getString(KEY_CACHED_ITEMS, null)
        if (!cachedItems.isNullOrEmpty()) {
            try {
                val decoded = json.decodeFromString<List<PlaylistItem>>(cachedItems)
                _items.value = decoded.filterNot { it.videoUrl.startsWith("urn:balaboo:") || it.playlistId == "00000000-0000-0000-0000-000000000099" }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode cached playlist items", e)
            }
        }
    }

    private fun saveToCache() {
        try {
            _activePlaylist.value?.let {
                prefs.edit().putString(KEY_ACTIVE_PLAYLIST, json.encodeToString(it)).apply()
            }
            prefs.edit().putString(KEY_CACHED_ITEMS, json.encodeToString(_items.value)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save playlist cache", e)
        }
    }

    fun initPlaylist() {
        val defaultId = "00000000-0000-0000-0000-000000000001"
        scope.launch {
            _isLoading.value = true
            val playlistsRes = restClient.fetchPlaylists()
            playlistsRes.onSuccess { lists ->
                val pl = lists.firstOrNull { it.id == defaultId } ?: lists.firstOrNull() ?: Playlist(
                    id = defaultId,
                    name = "Shared Playlist",
                    inviteCode = "HUB777",
                    ownerId = "00000000-0000-0000-0000-000000000000"
                )
                _activePlaylist.value = pl
                saveToCache()
                syncItems(pl.id)
            }.onFailure { err ->
                Log.w(TAG, "Could not fetch playlists online: ${err.message}. Using cache.")
                if (_activePlaylist.value == null) {
                    _activePlaylist.value = Playlist(
                        id = defaultId,
                        name = "Shared Playlist",
                        inviteCode = "HUB777",
                        ownerId = "00000000-0000-0000-0000-000000000000"
                    )
                    saveToCache()
                }
            }
            _isLoading.value = false
        }
    }

    fun syncItems(playlistId: String? = _activePlaylist.value?.id) {
        val pid = playlistId ?: "00000000-0000-0000-0000-000000000001"
        scope.launch {
            _isLoading.value = true
            val res = restClient.fetchPlaylistItems(pid)
            _isLoading.value = false
            res.onSuccess { remoteItems ->
                _items.value = remoteItems.filterNot { it.videoUrl.startsWith("urn:balaboo:") || it.playlistId == "00000000-0000-0000-0000-000000000099" }
                saveToCache()
            }.onFailure { err ->
                Log.w(TAG, "Failed to sync playlist items: ${err.message}")
            }
        }
    }

    fun isSaved(videoUrl: String): Boolean {
        if (videoUrl.isBlank()) return false
        return _items.value.any { it.videoUrl.equals(videoUrl, ignoreCase = true) || it.pageUrl.equals(videoUrl, ignoreCase = true) }
    }

    fun saveVideoFromBridge(payload: VideoSavePayload): Boolean {
        val currentPid = _activePlaylist.value?.id ?: "00000000-0000-0000-0000-000000000001"

        if (isSaved(payload.videoUrl) || isSaved(payload.pageUrl)) {
            Log.d(TAG, "Video already saved: ${payload.videoUrl}")
            return true
        }

        val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

        val myUid = AuthService.getInstance(context).getUserId()
        val initialPageUrl = NoteSync.buildPageUrl(
            cleanUrl = payload.pageUrl,
            note = "",
            addedBy = myUid,
            seenBy = emptyList()
        )

        val newItem = PlaylistItem(
            id = UUID.randomUUID().toString(),
            playlistId = currentPid,
            siteId = payload.siteId,
            videoUrl = payload.videoUrl,
            pageUrl = initialPageUrl,
            title = payload.title.ifBlank { "Saved Video" },
            thumbnailUrl = payload.thumbnailUrl,
            notes = "",
            addedBy = myUid,
            seenBy = emptyList(),
            createdAt = nowIso
        )

        val updated = listOf(newItem) + _items.value
        _items.value = updated
        saveToCache()

        scope.launch {
            val res = restClient.insertPlaylistItem(newItem)
            if (res.isFailure) {
                queueMutation("INSERT", newItem)
            }
        }

        return true
    }

    fun deleteItem(item: PlaylistItem) {
        val updated = _items.value.filter { it.id != item.id }
        _items.value = updated
        saveToCache()

        scope.launch {
            val res = restClient.deletePlaylistItem(item.id)
            if (res.isFailure) {
                queueMutation("DELETE", item)
            }
        }
    }

    fun updateItemNotes(id: String, notes: String) {
        val item = _items.value.find { it.id == id } ?: return
        val cleanUrl = NoteSync.cleanPageUrl(item.pageUrl)
        val newPageUrl = NoteSync.buildPageUrl(
            cleanUrl = cleanUrl,
            note = notes,
            addedBy = item.addedBy,
            seenBy = item.seenBy
        )
        val updated = _items.value.map {
            if (it.id == id) it.copy(notes = notes, pageUrl = newPageUrl) else it
        }
        _items.value = updated
        saveToCache()

        scope.launch {
            val res = restClient.updatePlaylistItemNotes(id, newPageUrl)
            if (res.isFailure) {
                Log.e(TAG, "Failed to update item notes in remote: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun markItemSeen(id: String) {
        val item = _items.value.find { it.id == id } ?: return
        val myUid = AuthService.getInstance(context).getUserId()
        if (item.seenBy.contains(myUid)) return // Already marked seen

        val updatedSeenBy = (item.seenBy + myUid).distinct()
        val cleanUrl = NoteSync.cleanPageUrl(item.pageUrl)
        val newPageUrl = NoteSync.buildPageUrl(
            cleanUrl = cleanUrl,
            note = item.notes,
            addedBy = item.addedBy,
            seenBy = updatedSeenBy
        )
        val updated = _items.value.map {
            if (it.id == id) it.copy(seenBy = updatedSeenBy, pageUrl = newPageUrl) else it
        }
        _items.value = updated
        saveToCache()

        scope.launch {
            val res = restClient.updatePlaylistItemNotes(id, newPageUrl)
            if (res.isFailure) {
                Log.e(TAG, "Failed to update item notes in remote: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    suspend fun joinByCode(code: String): Result<Playlist> {
        val res = restClient.joinPlaylistByCode(code)
        res.onSuccess { pl ->
            _activePlaylist.value = pl
            saveToCache()
            syncItems(pl.id)
        }
        return res
    }

    // ---------------- Mutation Queue ----------------

    private fun queueMutation(type: String, item: PlaylistItem) {
        try {
            val raw = prefs.getString(KEY_PLAYLIST_MUTATION_QUEUE, "[]") ?: "[]"
            val queue = json.decodeFromString<List<QueuedPlaylistMutation>>(raw).toMutableList()
            queue.add(QueuedPlaylistMutation(type = type, item = item))
            prefs.edit().putString(KEY_PLAYLIST_MUTATION_QUEUE, json.encodeToString(queue)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing playlist mutation", e)
        }
    }

    private suspend fun processMutationQueue() {
        val raw = prefs.getString(KEY_PLAYLIST_MUTATION_QUEUE, "[]") ?: "[]"
        if (raw == "[]") return
        try {
            val queue = json.decodeFromString<List<QueuedPlaylistMutation>>(raw)
            val remaining = mutableListOf<QueuedPlaylistMutation>()

            for (m in queue) {
                val ok = when (m.type) {
                    "INSERT" -> restClient.insertPlaylistItem(m.item).isSuccess
                    "DELETE" -> restClient.deletePlaylistItem(m.item.id).isSuccess
                    else -> true
                }
                if (!ok) remaining.add(m)
            }

            prefs.edit().putString(KEY_PLAYLIST_MUTATION_QUEUE, json.encodeToString(remaining)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing playlist mutation queue", e)
        }
    }

    // ---------------- RealtimeListener ----------------

    override fun onTableChanged(event: RealtimeChangeEvent) {
        if (event.table != "playlist_items") return
        scope.launch {
            try {
                val currentPid = _activePlaylist.value?.id ?: return@launch
                when (event.eventType) {
                    RealtimeEventType.INSERT -> {
                        val rawItem = json.decodeFromString<PlaylistItem>(event.recordJson)
                        val meta = NoteSync.extractMetadata(rawItem.pageUrl, rawItem.notes)
                        val newItem = rawItem.copy(
                            notes = meta.note,
                            addedBy = meta.addedBy ?: rawItem.addedBy,
                            seenBy = meta.seenBy
                        )
                        if (newItem.playlistId == currentPid && !newItem.videoUrl.startsWith("urn:balaboo:") && _items.value.none { it.id == newItem.id }) {
                            val updated = listOf(newItem) + _items.value
                            _items.value = updated
                            saveToCache()
                        }
                    }
                    RealtimeEventType.UPDATE -> {
                        val rawItem = json.decodeFromString<PlaylistItem>(event.recordJson)
                        val meta = NoteSync.extractMetadata(rawItem.pageUrl, rawItem.notes)
                        val updatedItem = rawItem.copy(
                            notes = meta.note,
                            addedBy = meta.addedBy ?: rawItem.addedBy,
                            seenBy = meta.seenBy
                        )
                        if (updatedItem.playlistId == currentPid) {
                            val updated = _items.value.map { if (it.id == updatedItem.id) updatedItem else it }
                            _items.value = updated
                            saveToCache()
                        }
                    }
                    RealtimeEventType.DELETE -> {
                        val oldRecord = json.decodeFromString<PlaylistItem>(event.oldRecordJson)
                        val updated = _items.value.filter { it.id != oldRecord.id }
                        _items.value = updated
                        saveToCache()
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling realtime playlist change", e)
            }
        }
    }
}
