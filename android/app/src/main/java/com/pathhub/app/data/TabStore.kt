package com.pathhub.app.data

import android.content.Context
import android.content.SharedPreferences
import com.pathhub.app.model.WebTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TabStore private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pathhub_tabs_v2", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _tabs = MutableStateFlow<List<WebTab>>(emptyList())
    val tabs: StateFlow<List<WebTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    init {
        loadTabs()
    }

    fun getActiveTab(): WebTab? {
        val currentId = _activeTabId.value
        val list = _tabs.value
        if (currentId == null) return list.firstOrNull()
        return list.firstOrNull { it.id == currentId } ?: list.firstOrNull()
    }

    fun openTab(url: String, title: String = "New Tab", siteId: String? = null): WebTab {
        val list = _tabs.value.toMutableList()
        val existing = list.firstOrNull { it.url == url }
        if (existing != null) {
            _activeTabId.value = existing.id
            saveTabs()
            return existing
        }

        val tabTitle = if (title.isBlank() || title.startsWith("http")) {
            try { android.net.Uri.parse(url).host?.replace("www.", "") ?: "New Tab" } catch (e: Exception) { "New Tab" }
        } else {
            title
        }

        val newTab = WebTab(url = url, title = tabTitle, siteId = siteId)
        list.add(newTab)
        _tabs.value = list
        _activeTabId.value = newTab.id
        saveTabs()
        return newTab
    }

    fun selectTab(id: String) {
        val list = _tabs.value.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(lastActiveAt = System.currentTimeMillis())
            _tabs.value = list
            _activeTabId.value = id
            saveTabs()
        }
    }

    fun updateActiveTab(url: String, title: String) {
        val currentId = _activeTabId.value
        val list = _tabs.value.toMutableList()
        if (currentId == null || list.none { it.id == currentId }) {
            if (list.isEmpty() && url.isNotBlank()) {
                openTab(url, title)
            }
            return
        }

        val idx = list.indexOfFirst { it.id == currentId }
        if (idx >= 0) {
            var updated = list[idx]
            var changed = false
            if (url.isNotBlank() && updated.url != url) {
                updated = updated.copy(url = url)
                changed = true
            }
            if (title.isNotBlank() && !title.startsWith("http") && updated.title != title) {
                updated = updated.copy(title = title)
                changed = true
            }
            if (changed) {
                list[idx] = updated.copy(lastActiveAt = System.currentTimeMillis())
                _tabs.value = list
                saveTabs()
            }
        }
    }

    fun closeTab(id: String) {
        val list = _tabs.value.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list.removeAt(idx)
            _tabs.value = list

            if (_activeTabId.value == id) {
                if (idx < list.size) {
                    _activeTabId.value = list[idx].id
                } else if (list.isNotEmpty()) {
                    _activeTabId.value = list.last().id
                } else {
                    _activeTabId.value = null
                }
            }
            saveTabs()
        }
    }

    fun closeAll() {
        _tabs.value = emptyList()
        _activeTabId.value = null
        saveTabs()
    }

    private fun loadTabs() {
        val raw = prefs.getString("saved_tabs", null)
        if (!raw.isNullOrBlank()) {
            try {
                val list = json.decodeFromString<List<WebTab>>(raw)
                _tabs.value = list
                val activeId = prefs.getString("active_tab_id", null)
                _activeTabId.value = if (activeId != null && list.any { it.id == activeId }) activeId else list.firstOrNull()?.id
            } catch (e: Exception) {
                _tabs.value = emptyList()
                _activeTabId.value = null
            }
        }
    }

    private fun saveTabs() {
        try {
            val raw = json.encodeToString(_tabs.value)
            prefs.edit()
                .putString("saved_tabs", raw)
                .putString("active_tab_id", _activeTabId.value)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        @Volatile
        private var instance: TabStore? = null

        fun getInstance(context: Context): TabStore {
            return instance ?: synchronized(this) {
                instance ?: TabStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
