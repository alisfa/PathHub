package com.pathhub.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Site(
    val id: String = "",
    @SerialName("user_id") val userId: String? = null,
    val name: String,
    val url: String,
    @SerialName("icon_url") val iconUrl: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Playlist(
    val id: String = "",
    val name: String = "My Saved Videos",
    @SerialName("invite_code") val inviteCode: String = "",
    @SerialName("owner_id") val ownerId: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class PlaylistItem(
    val id: String = "",
    @SerialName("playlist_id") val playlistId: String = "",
    @SerialName("site_id") val siteId: String? = null,
    @SerialName("video_url") val videoUrl: String,
    @SerialName("page_url") val pageUrl: String,
    val title: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String = "",
    @SerialName("notes") val notes: String = "",
    @SerialName("added_by") val addedBy: String? = null,
    val seenBy: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String = ""
) {
    val cleanPageUrl: String
        get() = com.pathhub.app.data.NoteSync.cleanPageUrl(pageUrl)

    val isReel: Boolean
        get() {
            val lower = "$cleanPageUrl $videoUrl $title".lowercase()
            return lower.contains("/shorties/") || lower.contains("/shorts/") || lower.contains("/reel") || lower.contains("shorties")
        }

    val cleanThumbnailUrl: String
        get() {
            val trimmed = thumbnailUrl.trim()
            val host = try {
                android.net.Uri.parse(cleanPageUrl.ifBlank { videoUrl }).host?.replace("www.", "") ?: "web"
            } catch (e: Exception) {
                "web"
            }
            if (trimmed.isEmpty() || trimmed.contains("/view_video.php") || trimmed.contains("/shorties/") ||
                trimmed.contains("logo") || trimmed.contains(".svg") ||
                (!trimmed.startsWith("data:image") && !trimmed.contains(".jpg") && !trimmed.contains(".jpeg") &&
                 !trimmed.contains(".png") && !trimmed.contains(".webp") && !trimmed.contains(".gif") &&
                 !trimmed.contains("phncdn") && !trimmed.contains("image") && !trimmed.contains("pics") &&
                 !trimmed.contains("thumb") && !trimmed.contains("original") && !trimmed.contains("vts:"))) {
                if (isReel) {
                    return "https://di.phncdn.com/pics/categories/(m=e-SaK8daaaa)(mh=lq2c-4G)32.jpg"
                }
                return "https://di.phncdn.com/pics/categories/(m=e-SaK8daaaa)(mh=lq2c-4G)105.jpg"
            }
            return trimmed
        }
}

@Serializable
data class VideoSavePayload(
    val videoUrl: String,
    val pageUrl: String,
    val title: String,
    val thumbnailUrl: String = "",
    val siteId: String? = null,
    val timestamp: Long = 0L
)

@Serializable
data class HistoryItem(
    val id: String = "",
    val url: String = "",
    val title: String = "",
    @SerialName("device_name") val deviceName: String = "",
    @SerialName("visited_at") val visitedAt: String = "",
    @SerialName("created_at") val createdAt: String = ""
)
