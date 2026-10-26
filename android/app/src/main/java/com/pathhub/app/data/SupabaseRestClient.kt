package com.pathhub.app.data

import android.content.Context
import android.util.Log
import com.pathhub.app.model.Playlist
import com.pathhub.app.model.PlaylistItem
import com.pathhub.app.model.Site
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class SupabaseRestClient(private val context: Context) {
    companion object {
        private const val TAG = "SupabaseRestClient"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private fun buildRequest(path: String, method: String = "GET", body: okhttp3.RequestBody? = null): Request {
        val restUrl = SupabaseConfig.getRestUrl(context)
        val apiKey = SupabaseConfig.getKey(context)

        val builder = Request.Builder()
            .url("$restUrl/$path")
            .addHeader("apikey", apiKey)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Prefer", "return=representation")

        when (method.uppercase()) {
            "POST" -> builder.post(body ?: "".toRequestBody(JSON_MEDIA_TYPE))
            "PATCH" -> builder.patch(body ?: "".toRequestBody(JSON_MEDIA_TYPE))
            "DELETE" -> builder.delete(body)
            else -> builder.get()
        }

        return builder.build()
    }

    // ---------------- Sites API ----------------

    suspend fun fetchSites(): Result<List<Site>> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("sites?select=*&order=sort_order.asc")
            val resp = httpClient.newCall(req).execute()
            val text = resp.body?.string() ?: "[]"
            if (resp.isSuccessful) {
                val sites = json.decodeFromString<List<Site>>(text)
                Result.success(sites)
            } else {
                Log.w(TAG, "fetchSites failed: ${resp.code} $text")
                Result.failure(Exception("Failed to fetch sites: $text"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchSites exception", e)
            Result.failure(e)
        }
    }

    suspend fun insertSite(site: Site): Result<Site> = withContext(Dispatchers.IO) {
        try {
            val jsonObject = buildJsonObject {
                put("id", site.id.ifEmpty { UUID.randomUUID().toString() })
                put("name", site.name)
                put("url", site.url)
                put("icon_url", site.iconUrl)
                put("sort_order", site.sortOrder)
            }
            val payload = jsonObject.toString()
            val req = buildRequest("sites", "POST", payload.toRequestBody(JSON_MEDIA_TYPE))
            val resp = httpClient.newCall(req).execute()
            val text = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val created = json.decodeFromString<List<Site>>(text).firstOrNull() ?: site
                Result.success(created)
            } else {
                Log.e(TAG, "Failed to insert site (${resp.code}): $text")
                Result.failure(Exception("Failed to insert site: $text"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception inserting site", e)
            Result.failure(e)
        }
    }

    suspend fun updateSite(id: String, name: String, url: String, sortOrder: Int): Result<Site> = withContext(Dispatchers.IO) {
        try {
            val jsonObject = buildJsonObject {
                put("name", name)
                put("url", url)
                put("sort_order", sortOrder)
            }
            val payload = jsonObject.toString()
            val req = buildRequest("sites?id=eq.$id", "PATCH", payload.toRequestBody(JSON_MEDIA_TYPE))
            val resp = httpClient.newCall(req).execute()
            val text = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val updated = json.decodeFromString<List<Site>>(text).first()
                Result.success(updated)
            } else {
                Log.e(TAG, "Failed to update site (${resp.code}): $text")
                Result.failure(Exception("Failed to update site: $text"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception updating site", e)
            Result.failure(e)
        }
    }

    suspend fun deleteSite(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("sites?id=eq.$id", "DELETE")
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) Result.success(true)
            else Result.failure(Exception("Failed to delete site: ${resp.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------- Playlists API ----------------

    suspend fun fetchPlaylists(): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("playlists?select=*&order=created_at.asc")
            val resp = httpClient.newCall(req).execute()
            val text = resp.body?.string() ?: "[]"
            if (resp.isSuccessful) {
                val lists = json.decodeFromString<List<Playlist>>(text)
                Result.success(lists)
            } else {
                Result.failure(Exception("Failed to fetch playlists: $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDefaultPlaylist(ownerId: String): Result<Playlist> = withContext(Dispatchers.IO) {
        try {
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val inviteCode = (1..6).map { chars.random() }.joinToString("")
            val payload = """{"name":"My Saved Videos","invite_code":"$inviteCode","owner_id":"$ownerId"}"""
            val req = buildRequest("playlists", "POST", payload.toRequestBody(JSON_MEDIA_TYPE))
            val resp = httpClient.newCall(req).execute()
            val text = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val created = json.decodeFromString<List<Playlist>>(text).first()
                Result.success(created)
            } else {
                Result.failure(Exception("Failed to create default playlist: $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPlaylistItems(playlistId: String = "00000000-0000-0000-0000-000000000001"): Result<List<PlaylistItem>> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("playlist_items?select=*&order=created_at.desc")
            val resp = httpClient.newCall(req).execute()
            val text = resp.body?.string() ?: "[]"
            if (resp.isSuccessful) {
                val rawItems = json.decodeFromString<List<PlaylistItem>>(text)
                val items = rawItems.map { raw ->
                    val meta = NoteSync.extractMetadata(raw.pageUrl, raw.notes)
                    raw.copy(
                        notes = meta.note,
                        addedBy = meta.addedBy ?: raw.addedBy,
                        seenBy = meta.seenBy
                    )
                }
                Result.success(items)
            } else {
                Result.failure(Exception("Failed to fetch playlist items: $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertPlaylistItem(item: PlaylistItem): Result<PlaylistItem> = withContext(Dispatchers.IO) {
        try {
            val normalizedItem = if (item.playlistId.isEmpty()) {
                item.copy(playlistId = "00000000-0000-0000-0000-000000000001")
            } else {
                item
            }
            val meta = NoteSync.extractMetadata(normalizedItem.pageUrl, normalizedItem.notes)
            val finalPageUrl = NoteSync.buildPageUrl(
                cleanUrl = normalizedItem.pageUrl,
                note = meta.note.ifEmpty { normalizedItem.notes },
                addedBy = normalizedItem.addedBy ?: meta.addedBy,
                seenBy = normalizedItem.seenBy.ifEmpty { meta.seenBy }
            )
            val itemToInsert = normalizedItem.copy(pageUrl = finalPageUrl)
            val jsonObject = buildJsonObject {
                put("id", itemToInsert.id)
                put("playlist_id", itemToInsert.playlistId)
                put("video_url", itemToInsert.videoUrl)
                put("page_url", itemToInsert.pageUrl)
                put("title", itemToInsert.title)
                put("thumbnail_url", itemToInsert.thumbnailUrl)
                if (!itemToInsert.siteId.isNullOrEmpty()) {
                    put("site_id", itemToInsert.siteId)
                }
            }
            val payload = jsonObject.toString()
            val req = buildRequest("playlist_items", "POST", payload.toRequestBody(JSON_MEDIA_TYPE))
            val resp = httpClient.newCall(req).execute()
            val text = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val created = json.decodeFromString<List<PlaylistItem>>(text).firstOrNull() ?: itemToInsert
                val metaCreated = NoteSync.extractMetadata(created.pageUrl, created.notes)
                Result.success(created.copy(
                    notes = metaCreated.note,
                    addedBy = metaCreated.addedBy ?: created.addedBy,
                    seenBy = metaCreated.seenBy
                ))
            } else {
                Result.failure(Exception("Failed to insert playlist item: $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePlaylistItemNotes(id: String, newPageUrl: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = """{"page_url":${json.encodeToString(newPageUrl)}}"""
            val req = buildRequest("playlist_items?id=eq.$id", "PATCH", payload.toRequestBody(JSON_MEDIA_TYPE))
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) Result.success(true)
            else Result.failure(Exception("Failed to update notes: ${resp.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePlaylistItem(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("playlist_items?id=eq.$id", "DELETE")
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) Result.success(true)
            else Result.failure(Exception("Failed to delete playlist item: ${resp.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinPlaylistByCode(code: String): Result<Playlist> = withContext(Dispatchers.IO) {
        try {
            val payload = """{"code":"${code.trim()}"}"""
            val req = buildRequest("rpc/join_playlist_by_code", "POST", payload.toRequestBody(JSON_MEDIA_TYPE))
            val resp = httpClient.newCall(req).execute()
            val text = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val joined = json.decodeFromString<Playlist>(text)
                Result.success(joined)
            } else {
                Result.failure(Exception("Join failed: $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
