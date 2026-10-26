package com.pathhub.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pathhub.app.data.PlaylistStore
import com.pathhub.app.model.PlaylistItem
import com.pathhub.app.ui.theme.Emerald400
import com.pathhub.app.ui.theme.Emerald500
import com.pathhub.app.ui.theme.EmeraldGradient
import com.pathhub.app.ui.theme.GlassBorderGradient
import com.pathhub.app.ui.theme.GlassCardGradient
import com.pathhub.app.ui.theme.Obsidian850
import com.pathhub.app.ui.theme.Obsidian900
import com.pathhub.app.ui.theme.Obsidian950
import com.pathhub.app.ui.theme.Rose500
import com.pathhub.app.ui.theme.Slate400
import com.pathhub.app.ui.theme.Slate700
import com.pathhub.app.ui.theme.Slate800
import kotlinx.coroutines.launch

enum class AndroidTypeFilter(val label: String) {
    ALL("All"),
    VIDEOS("🎬 Videos"),
    REELS("⚡ Reels")
}

enum class AndroidSortOption(val label: String) {
    NEWEST("Newest"),
    REELS_FIRST("⚡ Reels First"),
    OLDEST("Oldest"),
    TITLE("Title (A-Z)")
}

@Composable
fun PlaylistScreen(
    playlistStore: PlaylistStore,
    onBack: () -> Unit,
    onPlayVideo: (url: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by playlistStore.items.collectAsState()
    val activePlaylist by playlistStore.activePlaylist.collectAsState()
    val isLoading by playlistStore.isLoading.collectAsState()

    val viewPrefs = remember { context.getSharedPreferences("pathhub_view_mode", Context.MODE_PRIVATE) }
    var isCardView by remember { mutableStateOf(viewPrefs.getBoolean("is_card_view", true)) }
    var typeFilter by remember { mutableStateOf(AndroidTypeFilter.ALL) }
    var sortOption by remember { mutableStateOf(AndroidSortOption.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var hideMyShares by remember { mutableStateOf(false) }
    val currentUserId = remember { com.pathhub.app.data.AuthService.getInstance(context).getUserId() }

    var selectedDomainFilter by remember { mutableStateOf<String?>(null) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var deletingItem by remember { mutableStateOf<PlaylistItem?>(null) }
    var editingNoteItem by remember { mutableStateOf<PlaylistItem?>(null) }
    var copiedToast by remember { mutableStateOf(false) }

    // Unique domains for filtering
    val domains = remember(items) {
        items.mapNotNull {
            try {
                Uri.parse(it.cleanPageUrl.ifBlank { it.videoUrl }).host
            } catch (e: Exception) {
                null
            }
        }.distinct().sorted()
    }

    val filteredAndSortedItems = remember(items, selectedDomainFilter, typeFilter, sortOption, hideMyShares) {
        var list = items.toList()
        if (hideMyShares) {
            list = list.filter { it.addedBy == null || it.addedBy != currentUserId }
        }
        if (selectedDomainFilter != null) {
            list = list.filter {
                val d = try { Uri.parse(it.cleanPageUrl.ifBlank { it.videoUrl }).host } catch (e: Exception) { null }
                d == selectedDomainFilter
            }
        }
        when (typeFilter) {
            AndroidTypeFilter.ALL -> {}
            AndroidTypeFilter.VIDEOS -> list = list.filter { !it.isReel }
            AndroidTypeFilter.REELS -> list = list.filter { it.isReel }
        }
        when (sortOption) {
            AndroidSortOption.REELS_FIRST -> {
                list = list.sortedWith(
                    compareByDescending<PlaylistItem> { it.isReel }
                        .thenByDescending { it.createdAt.ifBlank { "9999-99-99" } }
                )
            }
            AndroidSortOption.NEWEST -> {
                list = list.sortedByDescending { it.createdAt.ifBlank { "9999-99-99" } }
            }
            AndroidSortOption.OLDEST -> {
                list = list.sortedBy { it.createdAt.ifBlank { "0000-00-00" } }
            }
            AndroidSortOption.TITLE -> {
                list = list.sortedBy { it.title.lowercase() }
            }
        }
        list
    }

    Scaffold(
        containerColor = Obsidian950
    ) { padding ->
        // Atmospheric Liquid Mesh Backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x2210B981), Color.Transparent),
                        center = Offset(100f, 100f),
                        radius = 800f
                    )
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x18F43F5E), Color.Transparent),
                        center = Offset(900f, 400f),
                        radius = 700f
                    )
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Luxury Header
                PlaylistHeroHeader(
                    playlistName = activePlaylist?.name ?: "Saved Videos",
                    inviteCode = activePlaylist?.inviteCode ?: "HUB001",
                    itemCount = items.size,
                    copiedToast = copiedToast,
                    onBack = onBack,
                    onCopyCode = { code ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Playlist Code", code)
                        clipboard.setPrimaryClip(clip)
                        copiedToast = true
                        Toast.makeText(context, "Invite code $code copied!", Toast.LENGTH_SHORT).show()
                    },
                    onShareCode = { code ->
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Join my Path Hub playlist with invite code: $code")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Playlist Code"))
                    },
                    onOpenJoin = { showJoinDialog = true }
                )

                // Controls Bar: View Toggle, Type Filter & Sort
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Type Filter Chips (All / Videos / Reels)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AndroidTypeFilter.values().forEach { filter ->
                            val isSel = typeFilter == filter
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSel) EmeraldGradient else Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF))))
                                    .border(1.dp, if (isSel) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f), CircleShape)
                                    .clickable { typeFilter = filter }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = filter.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSel) Obsidian950 else Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    // Hide My Shares Toggle Chip
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (hideMyShares) EmeraldGradient else Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF))))
                            .border(1.dp, if (hideMyShares) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f), CircleShape)
                            .clickable { hideMyShares = !hideMyShares }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (hideMyShares) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Hide My Shares",
                                tint = if (hideMyShares) Obsidian950 else Emerald400,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Hide My Shares",
                                fontSize = 11.sp,
                                fontWeight = if (hideMyShares) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (hideMyShares) Obsidian950 else Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sort Menu Chip
                        Box {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0x26FFFFFF))
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                    .clickable { showSortMenu = true }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapVert,
                                        contentDescription = "Sort",
                                        tint = Emerald400,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (sortOption == AndroidSortOption.REELS_FIRST) "⚡ Reels" else sortOption.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(Obsidian900).border(1.dp, GlassBorderGradient, RoundedCornerShape(12.dp))
                            ) {
                                AndroidSortOption.values().forEach { opt ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = opt.label,
                                                    color = if (sortOption == opt) Emerald400 else Color.White,
                                                    fontWeight = if (sortOption == opt) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 13.sp
                                                )
                                                if (sortOption == opt) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Emerald400,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            sortOption = opt
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // View Selector Pill (List vs Card)
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0x26FFFFFF))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                .padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (!isCardView) Emerald500 else Color.Transparent)
                                    .clickable {
                                        isCardView = false
                                        viewPrefs.edit().putBoolean("is_card_view", false).apply()
                                    }
                                    .padding(horizontal = 7.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FormatListBulleted,
                                    contentDescription = "List View",
                                    tint = if (!isCardView) Obsidian950 else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isCardView) Emerald500 else Color.Transparent)
                                    .clickable {
                                        isCardView = true
                                        viewPrefs.edit().putBoolean("is_card_view", true).apply()
                                    }
                                    .padding(horizontal = 7.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "Card View",
                                    tint = if (isCardView) Obsidian950 else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Site filter chips
                if (domains.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            GlassFilterChip(
                                text = "All (${items.size})",
                                isSelected = selectedDomainFilter == null,
                                onClick = { selectedDomainFilter = null }
                            )
                        }
                        items(domains) { domain ->
                            val count = items.count {
                                try { Uri.parse(it.cleanPageUrl.ifBlank { it.videoUrl }).host == domain } catch (e: Exception) { false }
                            }
                            GlassFilterChip(
                                text = "$domain ($count)",
                                isSelected = selectedDomainFilter == domain,
                                onClick = {
                                    selectedDomainFilter = if (selectedDomainFilter == domain) null else domain
                                }
                            )
                        }
                    }
                }

                // Playlist Items
                Box(modifier = Modifier.weight(1f)) {
                    if (filteredAndSortedItems.isEmpty() && !isLoading) {
                        PlaylistEmptyHeroState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 40.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isCardView) 18.dp else 12.dp)
                        ) {
                            if (isCardView) {
                                items(items = filteredAndSortedItems, key = { it.id }) { item ->
                                    LuxuryPlaylistItemCardWide(
                                        item = item,
                                        currentUserId = currentUserId,
                                        onClick = {
                                            playlistStore.markItemSeen(item.id)
                                            val targetUrl = item.cleanPageUrl.ifBlank { item.videoUrl }
                                            onPlayVideo(targetUrl, item.title)
                                        },
                                        onEditNote = { editingNoteItem = item },
                                        onDelete = { deletingItem = item }
                                    )
                                }
                            } else {
                                items(items = filteredAndSortedItems, key = { it.id }) { item ->
                                    LuxuryPlaylistItemCard(
                                        item = item,
                                        currentUserId = currentUserId,
                                        onClick = {
                                            playlistStore.markItemSeen(item.id)
                                            val targetUrl = item.cleanPageUrl.ifBlank { item.videoUrl }
                                            onPlayVideo(targetUrl, item.title)
                                        },
                                        onEditNote = { editingNoteItem = item },
                                        onDelete = { deletingItem = item }
                                    )
                                }
                            }
                        }
                    }

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .size(22.dp),
                            color = Emerald400,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }

    // Join by Code Dialog
    if (showJoinDialog) {
        var codeInput by remember { mutableStateOf("") }
        var isJoining by remember { mutableStateOf(false) }

        AlertDialog(
            containerColor = Obsidian900,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.border(1.dp, GlassCardGradient, RoundedCornerShape(26.dp)),
            onDismissRequest = { showJoinDialog = false },
            title = {
                Text(
                    text = "Join Shared Playlist",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a 6-character playlist invite code to instantly sync with friends.",
                        color = Slate400,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.uppercase() },
                        label = { Text("Invite Code (e.g. HUB001)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Obsidian850,
                            unfocusedContainerColor = Obsidian850,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Emerald400,
                            unfocusedIndicatorColor = Color(0x33FFFFFF),
                            focusedLabelColor = Emerald400,
                            unfocusedLabelColor = Slate400
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                    shape = RoundedCornerShape(14.dp),
                    enabled = codeInput.isNotBlank() && !isJoining,
                    onClick = {
                        isJoining = true
                        scope.launch {
                            val res = playlistStore.joinByCode(codeInput.trim())
                            isJoining = false
                            if (res.isSuccess) {
                                Toast.makeText(context, "Joined playlist!", Toast.LENGTH_SHORT).show()
                                showJoinDialog = false
                            } else {
                                Toast.makeText(context, "Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Join", color = Obsidian950, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("Cancel", color = Slate400)
                }
            }
        )
    }

    // Delete Item Dialog
    deletingItem?.let { item ->
        AlertDialog(
            containerColor = Obsidian900,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.dp, GlassCardGradient, RoundedCornerShape(24.dp)),
            onDismissRequest = { deletingItem = null },
            title = {
                Text(
                    text = "Remove Video?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Remove \"${item.title}\" from the shared playlist?",
                    color = Slate400,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        playlistStore.deleteItem(item)
                        deletingItem = null
                    }
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) {
                    Text("Cancel", color = Slate400)
                }
            }
        )
    }

    // Edit Video Note Dialog
    editingNoteItem?.let { item ->
        EditNoteDialog(
            item = item,
            onDismiss = { editingNoteItem = null },
            onSave = { newNotes ->
                playlistStore.updateItemNotes(item.id, newNotes)
                editingNoteItem = null
            }
        )
    }
}

// MARK: - Playlist Hero Header
@Composable
private fun PlaylistHeroHeader(
    playlistName: String,
    inviteCode: String,
    itemCount: Int,
    copiedToast: Boolean,
    onBack: () -> Unit,
    onCopyCode: (String) -> Unit,
    onShareCode: (String) -> Unit,
    onOpenJoin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Glass Back Button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(10.dp, CircleShape, ambientColor = Color.Black)
                    .clip(CircleShape)
                    .background(Color(0xD9111827))
                    .border(1.dp, GlassBorderGradient, CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = playlistName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    color = Color.White
                )
                Text(
                    text = if (itemCount == 1) "1 video saved" else "$itemCount videos saved",
                    color = Slate400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Actions: Copy code pill & Join button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Copy code pill
            Box(
                modifier = Modifier
                    .shadow(8.dp, CircleShape, ambientColor = Color.Black)
                    .clip(CircleShape)
                    .background(Color(0x2210B981))
                    .border(1.dp, Color(0x5510B981), CircleShape)
                    .clickable { onCopyCode(inviteCode) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Code",
                        tint = Emerald400,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (copiedToast) "COPIED" else inviteCode,
                        color = Emerald400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Join Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(8.dp, CircleShape, ambientColor = Color.Black)
                    .clip(CircleShape)
                    .background(Color(0xD9111827))
                    .border(1.dp, GlassBorderGradient, CircleShape)
                    .clickable { onOpenJoin() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GroupAdd,
                    contentDescription = "Join",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

// MARK: - Glass Filter Chip
@Composable
private fun GlassFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .shadow(if (isSelected) 10.dp else 4.dp, CircleShape, ambientColor = if (isSelected) Emerald500.copy(alpha = 0.4f) else Color.Black)
            .clip(CircleShape)
            .background(if (isSelected) EmeraldGradient else Brush.linearGradient(listOf(Color(0xD9111827), Color(0x991E293B))))
            .border(
                width = 1.dp,
                brush = if (isSelected) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f))) else GlassBorderGradient,
                shape = CircleShape
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Obsidian950 else Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
        )
    }
}

// MARK: - Seen Status Badge
@Composable
private fun SeenStatusBadge(item: PlaylistItem, currentUserId: String) {
    val isSharedByMe = item.addedBy != null && item.addedBy == currentUserId
    val isSeenByOthers = item.seenBy.any { it != item.addedBy && it.isNotBlank() }
    val isSeenByMe = item.seenBy.contains(currentUserId)

    if (isSharedByMe) {
        if (isSeenByOthers) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x3310B981))
                    .border(0.8.dp, Color(0x8010B981), CircleShape)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "Seen by others",
                        tint = Emerald400,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "Seen by others",
                        color = Emerald400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .border(0.8.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Unseen by others",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = "Unseen by others",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    } else {
        if (!isSeenByMe) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x590EA5E9))
                    .border(0.8.dp, Color(0xBF38BDF8), CircleShape)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8))
                    )
                    Text(
                        text = "NEW",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x26FFFFFF))
                    .border(0.6.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RemoveRedEye,
                        contentDescription = "Seen",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(9.dp)
                    )
                    Text(
                        text = "Seen",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// MARK: - Luxury PlaylistItem Card
@Composable
private fun LuxuryPlaylistItemCard(
    item: PlaylistItem,
    currentUserId: String,
    onClick: () -> Unit,
    onEditNote: () -> Unit,
    onDelete: () -> Unit
) {
    val domain = try {
        Uri.parse(item.cleanPageUrl.ifBlank { item.videoUrl }).host?.replace("www.", "") ?: "web"
    } catch (e: Exception) {
        "web"
    }
    val context = LocalContext.current
    val referer = remember(item.cleanPageUrl, item.videoUrl) {
        try {
            val uri = Uri.parse(item.cleanPageUrl.ifBlank { item.videoUrl })
            "${uri.scheme ?: "https"}://${uri.host ?: "www.pornhub.com"}/"
        } catch (e: Exception) {
            "https://www.pornhub.com/"
        }
    }
    val imageRequest = remember(item.cleanThumbnailUrl, referer) {
        ImageRequest.Builder(context)
            .data(item.cleanThumbnailUrl)
            .setHeader("Referer", referer)
            .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .crossfade(true)
            .build()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = Color.Black.copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xD9111827))
            .border(
                width = 1.dp,
                brush = GlassCardGradient,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 16:9 Cinematic Thumbnail Box
        Box(
            modifier = Modifier
                .size(width = 98.dp, height = 64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Obsidian900),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Center translucent play orb
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title, Domain, and Attached Note Pill
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (item.isReel) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFFF7A00), Color(0xFFFF3D00))))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "⚡ REEL",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = domain,
                        color = Emerald400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                SeenStatusBadge(item = item, currentUserId = currentUserId)
            }

            // Attached Note snippet pill
            if (item.notes.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x28F59E0B))
                        .border(0.8.dp, Color(0x66F59E0B), CircleShape)
                        .clickable { onEditNote() }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = item.notes,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Action Buttons: Edit Note & Delete
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onEditNote,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Note",
                    tint = if (item.notes.isBlank()) Color.White.copy(alpha = 0.35f) else Color(0xFFFBBF24),
                    modifier = Modifier.size(17.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

// MARK: - Luxury PlaylistItem Card Wide (Card View)
@Composable
private fun LuxuryPlaylistItemCardWide(
    item: PlaylistItem,
    currentUserId: String,
    onClick: () -> Unit,
    onEditNote: () -> Unit,
    onDelete: () -> Unit
) {
    val domain = try {
        Uri.parse(item.cleanPageUrl.ifBlank { item.videoUrl }).host?.replace("www.", "") ?: "web"
    } catch (e: Exception) {
        "web"
    }
    val context = LocalContext.current
    val referer = remember(item.cleanPageUrl, item.videoUrl) {
        try {
            val uri = Uri.parse(item.cleanPageUrl.ifBlank { item.videoUrl })
            "${uri.scheme ?: "https"}://${uri.host ?: "www.pornhub.com"}/"
        } catch (e: Exception) {
            "https://www.pornhub.com/"
        }
    }
    val imageRequest = remember(item.cleanThumbnailUrl, referer) {
        ImageRequest.Builder(context)
            .data(item.cleanThumbnailUrl)
            .setHeader("Referer", referer)
            .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .crossfade(true)
            .build()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = Color.Black.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xD9111827))
            .border(
                width = 1.dp,
                brush = GlassCardGradient,
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        // 1. 16:9 Hero Thumbnail Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clickable { onClick() }
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Scrim gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )

            // Top Badges (⚡ REEL, Seen Status & Domain)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (item.isReel) {
                        Box(
                            modifier = Modifier
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFFFF7A00), Color(0xFFFF3D00))))
                                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "REEL",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    SeenStatusBadge(item = item, currentUserId = currentUserId)
                }

                // Domain pill
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = domain,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Center Play Orb
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(1.2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 2. Card Content (Title, Note, Actions)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 22.sp
            )

            // Expanded Note Box
            if (item.notes.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x1AF59E0B))
                        .border(1.dp, Color(0x4DF59E0B), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatQuote,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Note",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = onEditNote,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Text(
                        text = item.notes,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        .clickable { onEditNote() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Add personal note or timestamp...",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Bottom Actions: Play Button, Edit Note, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Obsidian950,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Play Video",
                            color = Obsidian950,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onEditNote,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Note",
                            tint = if (item.notes.isBlank()) Color.White.copy(alpha = 0.5f) else Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Edit Note Dialog
@Composable
private fun EditNoteDialog(
    item: PlaylistItem,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var noteText by remember { mutableStateOf(item.notes) }

    AlertDialog(
        containerColor = Obsidian900,
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.border(1.dp, GlassCardGradient, RoundedCornerShape(26.dp)),
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Video Notes",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = item.title,
                    color = Slate400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = {
                        Text(
                            text = "Add notes, timestamps (e.g. 05:22), or thoughts...",
                            color = Slate400.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Obsidian950,
                        unfocusedContainerColor = Obsidian950,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color(0xFFF59E0B),
                        unfocusedIndicatorColor = Color(0x33FFFFFF)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(noteText.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Note", color = Obsidian950, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.notes.isNotBlank()) {
                    TextButton(onClick = { onSave("") }) {
                        Text("Delete Note", color = Rose500)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Slate400)
                }
            }
        }
    )
}

// MARK: - Empty Hero State
@Composable
private fun PlaylistEmptyHeroState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .shadow(24.dp, CircleShape, ambientColor = Emerald500.copy(alpha = 0.5f))
                .clip(CircleShape)
                .background(Color(0x1F10B981))
                .border(1.5.dp, Color(0x4410B981), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                tint = Emerald400,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No Saved Videos Yet",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Browse your favorite video sites and tap the Save Video button on any video to curate your private playlist in realtime.",
            color = Slate400,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}
