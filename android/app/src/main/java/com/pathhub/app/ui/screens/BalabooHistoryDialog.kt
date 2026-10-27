package com.pathhub.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pathhub.app.data.HistoryStore
import com.pathhub.app.model.HistoryItem
import com.pathhub.app.ui.theme.Emerald400
import com.pathhub.app.ui.theme.Emerald500
import com.pathhub.app.ui.theme.Obsidian900
import com.pathhub.app.ui.theme.Obsidian950
import com.pathhub.app.ui.theme.Rose500
import com.pathhub.app.ui.theme.Slate200
import com.pathhub.app.ui.theme.Slate400
import com.pathhub.app.ui.theme.Slate700
import com.pathhub.app.ui.theme.Slate800
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

enum class HistorySortOrder(val title: String) {
    NEWEST("⚡ Newest"),
    OLDEST("⏳ Oldest"),
    DEVICE("📱 By Device"),
    SITE("🌐 By Site")
}

@Composable
fun BalabooHistoryDialog(
    onDismiss: () -> Unit,
    onOpenUrl: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val historyStore = remember { HistoryStore.getInstance(context) }
    val historyItems by historyStore.items.collectAsState()
    val isLoading by historyStore.isLoading.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedDevice by remember { mutableStateOf("All Devices") }
    var sortOrder by remember { mutableStateOf(HistorySortOrder.NEWEST) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Unique devices list
    val devices = remember(historyItems) {
        val unique = historyItems.map { it.deviceName.ifBlank { "Unknown Device" } }.distinct()
        listOf("All Devices") + unique
    }

    // Filter and Sort
    val processedItems = remember(historyItems, searchQuery, selectedDevice, sortOrder) {
        var list = historyItems

        // 1. Filter by Device
        if (selectedDevice != "All Devices") {
            list = list.filter { it.deviceName.equals(selectedDevice, ignoreCase = true) }
        }

        // 2. Filter by Search Query
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                it.url.lowercase().contains(q) ||
                it.deviceName.lowercase().contains(q)
            }
        }

        // 3. Sort
        when (sortOrder) {
            HistorySortOrder.NEWEST -> list.sortedByDescending { it.visitedAt.ifBlank { it.createdAt } }
            HistorySortOrder.OLDEST -> list.sortedBy { it.visitedAt.ifBlank { it.createdAt } }
            HistorySortOrder.DEVICE -> list.sortedWith(compareBy({ it.deviceName.lowercase() }, { it.visitedAt }))
            HistorySortOrder.SITE -> list.sortedBy { it.title.lowercase().ifBlank { it.url.lowercase() } }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Slate800, RoundedCornerShape(24.dp)),
            color = Obsidian900
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Balaboo History",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                            color = Color.White
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Emerald500.copy(alpha = 0.2f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${processedItems.size}/${historyItems.size}",
                                color = Emerald400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (processedItems.isNotEmpty()) {
                            TextButton(
                                onClick = { showClearConfirm = true }
                            ) {
                                Text(
                                    text = if (selectedDevice == "All Devices") "Clear All" else "Clear Device",
                                    color = Rose500,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Obsidian950)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Slate400,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search site, URL, or device...", color = Slate400, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Slate400,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Obsidian950,
                        unfocusedContainerColor = Obsidian950,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Slate200,
                        focusedIndicatorColor = Emerald500,
                        unfocusedIndicatorColor = Slate700
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Device Filter Pills Bar (Horizontal Scroll)
                if (devices.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "FILTER BY DEVICE",
                            color = Slate400,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            devices.forEach { dev ->
                                val isSelected = dev == selectedDevice
                                val devCount = if (dev == "All Devices") historyItems.size
                                else historyItems.count { it.deviceName.equals(dev, ignoreCase = true) }

                                val icon = if (dev == "All Devices") "🌐"
                                else if (dev.lowercase().contains("iphone") || dev.lowercase().contains("ipad")) "📱"
                                else "🤖"

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Emerald500 else Color(0x301E293B))
                                        .border(
                                            1.dp,
                                            if (isSelected) Emerald400 else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedDevice = dev }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "$icon $dev ($devCount)",
                                        color = if (isSelected) Obsidian950 else Slate200,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Sort Options Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SORT:",
                        color = Slate400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    HistorySortOrder.values().forEach { so ->
                        val isSelected = so == sortOrder
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Slate700 else Obsidian950)
                                .border(
                                    1.dp,
                                    if (isSelected) Emerald500 else Slate800,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { sortOrder = so }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = so.title,
                                color = if (isSelected) Emerald400 else Slate400,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // List Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 400.dp)
                ) {
                    if (processedItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Emerald500, modifier = Modifier.size(30.dp))
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("🕵️", fontSize = 26.sp)
                                    Text(
                                        text = if (searchQuery.isNotBlank() || selectedDevice != "All Devices")
                                            "No records matching filter"
                                        else
                                            "No browsing history yet",
                                        color = Slate400,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Visits synced across all devices will appear here in realtime",
                                        color = Slate700,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            items(processedItems, key = { it.id.ifBlank { it.url + it.visitedAt } }) { item ->
                                HistoryRow(
                                    item = item,
                                    onDelete = { historyStore.deleteItem(item.id) },
                                    onClick = {
                                        if (onOpenUrl != null) {
                                            onOpenUrl(item.url)
                                            onDismiss()
                                        } else {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("URL", item.url))
                                            Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Close Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Done",
                        color = Emerald400,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        Dialog(onDismissRequest = { showClearConfirm = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Slate800, RoundedCornerShape(20.dp)),
                color = Obsidian900
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (selectedDevice == "All Devices") "Clear All History?" else "Clear $selectedDevice History?",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (selectedDevice == "All Devices")
                            "This will permanently delete all browsing history across all devices."
                        else
                            "This will delete history recorded on $selectedDevice across all devices.",
                        color = Slate400,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showClearConfirm = false }) {
                            Text("Cancel", color = Slate400)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            historyStore.clearHistory(if (selectedDevice == "All Devices") null else selectedDevice)
                            showClearConfirm = false
                        }) {
                            Text("Clear", color = Rose500, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: HistoryItem,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val formattedTime = remember(item.visitedAt) { formatTimestamp(item.visitedAt) }
    val deviceIcon = if (item.deviceName.lowercase().contains("iphone") || item.deviceName.lowercase().contains("ipad")) "📱" else "🤖"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x601E293B))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = item.title.ifBlank { item.url },
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = item.url,
                color = Slate400,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                // Device badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Emerald500.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$deviceIcon ${item.deviceName}",
                        color = Emerald400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Timestamp
                Text(
                    text = formattedTime,
                    color = Slate400.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = Slate400,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

private fun formatTimestamp(isoString: String): String {
    if (isoString.isBlank()) return ""
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val clean = isoString.substringBefore('.').substringBefore('+').substringBefore('Z')
        val date = sdf.parse(clean) ?: return isoString
        val displayFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        displayFormat.format(date)
    } catch (e: Exception) {
        isoString.take(19).replace('T', ' ')
    }
}
