package com.pathhub.app.data

import android.util.Base64
import java.net.URLDecoder
import java.net.URLEncoder

data class ExtractedMeta(
    val note: String = "",
    val addedBy: String? = null,
    val seenBy: List<String> = emptyList()
)

object NoteSync {
    private const val NOTE_PREFIX = "#ph_note="
    private const val PH_PREFIX = "#ph_"

    /**
     * Extracts all metadata (note, addedBy, seenBy) from the URL fragment.
     */
    fun extractMetadata(pageUrl: String, columnNote: String? = null): ExtractedMeta {
        var note = if (!columnNote.isNullOrBlank()) columnNote.trim() else ""
        var addedBy: String? = null
        val seenBy = mutableListOf<String>()

        // Split by both '#' and '&' to handle any fragment format (#1#ph_note=..., &ph_note=..., etc.)
        val tokens = pageUrl.split('#', '&')
        for (rawToken in tokens) {
            val token = rawToken.trim()
            when {
                token.startsWith("ph_note=") -> {
                    if (note.isEmpty()) {
                        val raw = token.substring("ph_note=".length).trim()
                        note = decodeBase64OrUrl(raw)
                    }
                }
                token.startsWith("ph_by=") -> {
                    val by = token.substring("ph_by=".length).trim()
                    if (by.isNotEmpty()) addedBy = by
                }
                token.startsWith("ph_seen=") -> {
                    val seenRaw = token.substring("ph_seen=".length).trim()
                    if (seenRaw.isNotEmpty()) {
                        val items = seenRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        seenBy.addAll(items)
                    }
                }
            }
        }

        return ExtractedMeta(
            note = note,
            addedBy = addedBy,
            seenBy = seenBy.distinct()
        )
    }

    /**
     * Extracts note either from direct column (if available) or decodes it from the URL fragment.
     */
    fun extractNote(pageUrl: String, columnNote: String? = null): String {
        return extractMetadata(pageUrl, columnNote).note
    }

    /**
     * Returns pageUrl stripped of any #ph_ metadata fragment and webview tab anchors (#1, #2, #).
     */
    fun cleanPageUrl(pageUrl: String): String {
        var clean = pageUrl
        val phIdx = clean.indexOf(PH_PREFIX)
        if (phIdx != -1) {
            clean = clean.substring(0, phIdx)
        }
        val noteIdx = clean.indexOf(NOTE_PREFIX)
        if (noteIdx != -1) {
            clean = clean.substring(0, noteIdx)
        }
        while (clean.endsWith("#1") || clean.endsWith("#2") || clean.endsWith("#")) {
            clean = if (clean.endsWith("#1") || clean.endsWith("#2")) {
                clean.dropLast(2)
            } else {
                clean.dropLast(1)
            }
        }
        return clean
    }

    /**
     * Builds pageUrl with metadata (note, addedBy, seenBy) persisted in the URL fragment.
     */
    fun buildPageUrl(
        cleanUrl: String,
        note: String = "",
        addedBy: String? = null,
        seenBy: List<String> = emptyList()
    ): String {
        val clean = cleanPageUrl(cleanUrl)
        val parts = mutableListOf<String>()

        val trimmedNote = note.trim()
        if (trimmedNote.isNotEmpty()) {
            val encodedNote = try {
                Base64.encodeToString(
                    trimmedNote.toByteArray(Charsets.UTF_8),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                ).trim()
            } catch (e: Exception) {
                URLEncoder.encode(trimmedNote, "UTF-8")
            }
            parts.add("ph_note=$encodedNote")
        }

        if (!addedBy.isNullOrBlank()) {
            parts.add("ph_by=${addedBy.trim()}")
        }

        val cleanSeen = seenBy.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanSeen.isNotEmpty()) {
            parts.add("ph_seen=${cleanSeen.joinToString(",")}")
        }

        return if (parts.isEmpty()) clean else "$clean#${parts.joinToString("&")}"
    }

    private fun decodeBase64OrUrl(raw: String): String {
        if (raw.isEmpty()) return ""
        val unencoded = try { URLDecoder.decode(raw, "UTF-8") } catch (e: Exception) { raw }
        return try {
            val bytes = Base64.decode(unencoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            String(bytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            try {
                val bytes = Base64.decode(raw, Base64.DEFAULT)
                String(bytes, Charsets.UTF_8).trim()
            } catch (e2: Exception) {
                unencoded.trim()
            }
        }
    }
}
