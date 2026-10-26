package com.pathhub.app.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import com.pathhub.app.data.TabStore
import com.pathhub.app.data.VpnManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pathhub.app.data.SiteStore
import com.pathhub.app.model.Site
import com.pathhub.app.ui.components.FaviconView
import com.pathhub.app.ui.theme.AuroraCyan
import com.pathhub.app.ui.theme.Emerald400
import com.pathhub.app.ui.theme.Emerald500
import com.pathhub.app.ui.theme.EmeraldGradient
import com.pathhub.app.ui.theme.GlassBackground
import com.pathhub.app.ui.theme.GlassBorderGradient
import com.pathhub.app.ui.theme.GlassCardGradient
import com.pathhub.app.ui.theme.Obsidian850
import com.pathhub.app.ui.theme.Obsidian900
import com.pathhub.app.ui.theme.Obsidian950
import com.pathhub.app.ui.theme.Rose500
import com.pathhub.app.ui.theme.Slate200
import com.pathhub.app.ui.theme.Slate400
import com.pathhub.app.ui.theme.Slate700
import com.pathhub.app.ui.theme.Slate800
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    siteStore: SiteStore,
    onOpenBrowser: (url: String, siteId: String, name: String) -> Unit,
    onOpenPlaylist: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tabStore = remember { TabStore.getInstance(context) }
    val tabs by tabStore.tabs.collectAsState()

    val sites by siteStore.sites.collectAsState()
    val isLoading by siteStore.isLoading.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var editingSite by remember { mutableStateOf<Site?>(null) }
    var deletingSite by remember { mutableStateOf<Site?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        siteStore.syncSites()
    }

    Scaffold(
        containerColor = Obsidian950,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Million-Dollar Liquid Jewel Floating Orb
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp, end = 4.dp)
                    .size(62.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = CircleShape,
                        ambientColor = Emerald500.copy(alpha = 0.6f),
                        spotColor = Emerald400
                    )
                    .clip(CircleShape)
                    .background(EmeraldGradient)
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.1f))
                        ),
                        shape = CircleShape
                    )
                    .clickable { showAddDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Website",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { padding ->
        // Cosmic Liquid Mesh Backdrop
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
                        colors = listOf(Color(0x1806B6D4), Color.Transparent),
                        center = Offset(900f, 300f),
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
                // Bespoke Flagship Glass Header
                HomeHeroHeader(
                    siteCount = sites.size,
                    tabStore = tabStore,
                    tabsCount = tabs.size,
                    onResumeTab = {
                        val active = tabStore.getActiveTab() ?: tabs.firstOrNull()
                        if (active != null) {
                            onOpenBrowser(active.url, active.siteId ?: "", active.title)
                        }
                    },
                    onOpenPlaylist = onOpenPlaylist,
                    onOpenSettings = { showSettingsDialog = true }
                )

                // 1.1.1.1 Device VPN Status & Quick Connect Card
                val isSystemVpn = remember { mutableStateOf(VpnManager.isSystemVpnActive(context)) }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSystemVpn.value) Color(0x33064E3B) else Color(0x55111827),
                    border = BorderStroke(1.dp, if (isSystemVpn.value) Color(0x4410B981) else Color(0x33F59E0B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clickable {
                            if (!isSystemVpn.value) {
                                VpnManager.launch1111App(context)
                            } else {
                                showSettingsDialog = true
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isSystemVpn.value) Emerald400 else Color(0xFFF59E0B))
                            )
                            Column {
                                Text(
                                    text = if (isSystemVpn.value) "1.1.1.1 Device VPN Active" else "Cloudflare 1.1.1.1 Disconnected",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isSystemVpn.value) "Entire device protected • Zero speed loss" else "Tap to connect 1.1.1.1 & unblock all sites",
                                    color = if (isSystemVpn.value) Emerald400 else Slate400,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        if (!isSystemVpn.value) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Emerald500)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("CONNECT", color = Obsidian950, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                // Site List or Luxury Empty State
                if (sites.isEmpty() && !isLoading) {
                    EmptyHeroState(onAddFirst = { showAddDialog = true })
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(items = sites, key = { _, s -> s.id }) { index, site ->
                            LuxurySiteCard(
                                site = site,
                                index = index,
                                totalCount = sites.size,
                                onClick = {
                                    tabStore.openTab(url = site.url, title = site.name, siteId = site.id)
                                    onOpenBrowser(site.url, site.id, site.name)
                                },
                                onEdit = { editingSite = site },
                                onDelete = { deletingSite = site },
                                onMoveUp = { if (index > 0) siteStore.reorderSites(index, index - 1) },
                                onMoveDown = { if (index < sites.size - 1) siteStore.reorderSites(index, index + 1) }
                            )
                        }
                    }
                }
            }

            // Discreet top loading indicator
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

    // Add Site Dialog
    if (showAddDialog) {
        LuxuryAddSiteDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { url, name ->
                scope.launch {
                    val res = siteStore.addSite(url, name)
                    if (res.isFailure) {
                        snackbarHostState.showSnackbar(res.exceptionOrNull()?.message ?: "Failed to add site")
                    } else {
                        showAddDialog = false
                    }
                }
            }
        )
    }

    // App Settings Dialog
    if (showSettingsDialog) {
        AppSettingsDialog(
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Edit Site Dialog
    editingSite?.let { site ->
        LuxuryEditSiteDialog(
            site = site,
            onDismiss = { editingSite = null },
            onSave = { newName, newUrl ->
                siteStore.updateSite(site, newName, newUrl)
                editingSite = null
            }
        )
    }

    // Delete Confirmation Dialog
    deletingSite?.let { site ->
        AlertDialog(
            containerColor = Obsidian900,
            titleContentColor = Color.White,
            textContentColor = Slate400,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.dp, GlassCardGradient, RoundedCornerShape(24.dp)),
            onDismissRequest = { deletingSite = null },
            title = {
                Text(
                    text = "Remove Site?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove \"${site.name}\"? It will be removed across all connected devices.",
                    color = Slate400,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        siteStore.deleteSite(site)
                        deletingSite = null
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "\"${site.name}\" deleted",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                siteStore.undoDelete()
                            }
                        }
                    }
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSite = null }) {
                    Text("Cancel", color = Slate400)
                }
            }
        )
    }
}

// MARK: - Bespoke Glass Header
@Composable
private fun HomeHeroHeader(
    siteCount: Int,
    tabStore: TabStore,
    tabsCount: Int,
    onResumeTab: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Path Hub",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.8).sp,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false
                )

                // Realtime Status Indicator (Concentric glowing dot, never stretched)
                Box(
                    modifier = Modifier.size(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0x3310B981))
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Emerald400)
                    )
                }
            }

            Text(
                text = if (siteCount == 1) "1 site available" else "$siteCount sites synced",
                color = Slate400,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Resume Session Pill if open tabs exist
            if (tabsCount > 0) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xDD111827),
                    border = BorderStroke(1.dp, Color(0x6606B6D4)),
                    modifier = Modifier
                        .clickable { onResumeTab() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Resume Tabs",
                            tint = AuroraCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Tabs",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(AuroraCyan)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "$tabsCount",
                                color = Obsidian950,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Luxury Floating Glass Playlist Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xDD111827),
                border = BorderStroke(1.dp, Color(0x5510B981)),
                modifier = Modifier
                    .clickable { onOpenPlaylist() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Saved Playlist",
                        tint = Emerald400,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Playlist",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Discreet Settings Icon Button
            Surface(
                shape = CircleShape,
                color = Color(0xDD111827),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier
                    .size(34.dp)
                    .clickable { onOpenSettings() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

// MARK: - Luxury Media Site Card
@Composable
private fun LuxurySiteCard(
    site: Site,
    index: Int,
    totalCount: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val domain = try { Uri.parse(site.url).host ?: site.url } catch (e: Exception) { site.url }

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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // High-definition Favicon squircle
        FaviconView(iconUrl = site.iconUrl, fallbackText = site.name, size = 46.dp)

        Spacer(modifier = Modifier.width(14.dp))

        // Titles and Domain
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = site.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = Emerald400.copy(alpha = 0.8f),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = domain,
                    color = Slate400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Action Menu
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Slate400,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier
                    .background(Obsidian900)
                    .border(1.dp, GlassBorderGradient, RoundedCornerShape(12.dp))
            ) {
                if (index > 0) {
                    DropdownMenuItem(
                        text = { Text("Move Up", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.ArrowUpward, null, tint = Slate400, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuExpanded = false
                            onMoveUp()
                        }
                    )
                }
                if (index < totalCount - 1) {
                    DropdownMenuItem(
                        text = { Text("Move Down", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.ArrowDownward, null, tint = Slate400, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuExpanded = false
                            onMoveDown()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Edit Site", color = Color.White, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = Emerald400, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Rose500, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Rose500, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Forward Chevron Pill
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.25f),
            modifier = Modifier.size(16.dp)
        )
    }
}

// MARK: - Empty State
@Composable
private fun EmptyHeroState(onAddFirst: () -> Unit) {
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
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = Emerald400,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Your Private Media Hub",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Add video websites to experience custom fluid gestures and realtime playlist sync.",
            color = Slate400,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(26.dp))

        Button(
            onClick = onAddFirst,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.height(50.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Obsidian950)
                Text(
                    text = "Add First Website",
                    color = Obsidian950,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

// MARK: - Luxury Add Site Modal
@Composable
fun LuxuryAddSiteDialog(
    onDismiss: () -> Unit,
    onAdd: (url: String, name: String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        containerColor = Obsidian900,
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.border(1.dp, GlassCardGradient, RoundedCornerShape(26.dp)),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Website",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Website URL (e.g. pornhub.com)") },
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

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name (optional)") },
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
                enabled = url.isNotBlank() && !isSubmitting,
                onClick = {
                    isSubmitting = true
                    onAdd(url.trim(), name.trim())
                }
            ) {
                Text("Add Website", color = Obsidian950, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Slate400)
            }
        }
    )
}

// MARK: - Luxury Edit Site Modal
@Composable
fun LuxuryEditSiteDialog(
    site: Site,
    onDismiss: () -> Unit,
    onSave: (newName: String, newUrl: String) -> Unit
) {
    var name by remember { mutableStateOf(site.name) }
    var url by remember { mutableStateOf(site.url) }

    AlertDialog(
        containerColor = Obsidian900,
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.border(1.dp, GlassCardGradient, RoundedCornerShape(26.dp)),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Website",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
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

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Website URL") },
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
                enabled = url.isNotBlank() && name.isNotBlank(),
                onClick = { onSave(name.trim(), url.trim()) }
            ) {
                Text("Save Changes", color = Obsidian950, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Slate400)
            }
        }
    )
}
