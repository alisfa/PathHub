package com.pathhub.app.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class WebTab(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String = "New Tab",
    val siteId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis()
) {
    val hostDomain: String
        get() {
            return try {
                android.net.Uri.parse(url).host?.replace("www.", "") ?: "Web Tab"
            } catch (e: Exception) {
                "Web Tab"
            }
        }
}
