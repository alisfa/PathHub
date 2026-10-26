package com.pathhub.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pathhub.app.data.AuthService
import com.pathhub.app.data.SupabaseConfig
import com.pathhub.app.data.UpdateInfo
import com.pathhub.app.data.UpdateManager
import com.pathhub.app.data.VpnManager
import com.pathhub.app.ui.components.UpdateDialog
import com.pathhub.app.ui.theme.Emerald400
import com.pathhub.app.ui.theme.Emerald500
import com.pathhub.app.ui.theme.Obsidian900
import com.pathhub.app.ui.theme.Obsidian950
import com.pathhub.app.ui.theme.Rose500
import com.pathhub.app.ui.theme.Slate200
import com.pathhub.app.ui.theme.Slate400
import com.pathhub.app.ui.theme.Slate700
import com.pathhub.app.ui.theme.Slate800
import com.pathhub.app.ui.theme.Slate900
import kotlinx.coroutines.launch

@Composable
fun AppSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val authService = remember(context) { AuthService.getInstance(context) }
    val scope = rememberCoroutineScope()
    val user by authService.user.collectAsState()

    var isSystemVpnActive by remember { mutableStateOf(VpnManager.isSystemVpnActive(context)) }
    val is1111Installed = remember { VpnManager.is1111Installed(context) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: VPN, 1: Sync, 2: Updates, 3: Server
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    var customUrl by remember { mutableStateOf(SupabaseConfig.getUrl(context)) }
    var customKey by remember { mutableStateOf(SupabaseConfig.getKey(context)) }

    val updateManager = remember { UpdateManager.getInstance(context) }
    var githubRepo by remember { mutableStateOf(updateManager.getGitHubRepo()) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var detectedUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

    var showPinDialog by remember { mutableStateOf(false) }
    var showBalabooHistory by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(26.dp))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(26.dp)
                ),
            color = Obsidian900
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dialog Header: Title + Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "App Settings",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        letterSpacing = (-0.5).sp,
                        color = Color.White
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Obsidian950)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Slate400,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Modern Segmented Tab Pills Bar
                val tabs = listOf("🛡️ VPN", "☁️ Sync", "⚡ Updates", "⚙️ Server")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Obsidian950)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Emerald500 else Color.Transparent)
                                .clickable { selectedTab = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) Obsidian950 else Slate400,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Scrollable Content Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // Section 1: Cloudflare 1.1.1.1 Device-Wide VPN Hub
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x801E293B))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🛡️ Cloudflare 1.1.1.1 (WARP)",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Real-time Connection Status Badge: Never vertically wrapped
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSystemVpnActive) Color(0x3310B981) else Color(0x33F59E0B)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSystemVpnActive) Emerald400 else Color(0xFFF59E0B))
                                            )
                                            Text(
                                                text = if (isSystemVpnActive) "CONNECTED" else "DISCONNECTED",
                                                color = if (isSystemVpnActive) Emerald400 else Color(0xFFF59E0B),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = if (isSystemVpnActive)
                                        "Your entire device is currently protected by system VPN. All media & blocked sites load at 100% full wire speed with 0 buffering."
                                    else
                                        "Uses Cloudflare's official 1.1.1.1 Anycast network to cover the ENTIRE device, completely bypassing ISP blocking with zero speed loss.",
                                    color = Slate400,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )

                                // Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            VpnManager.launch1111App(context)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSystemVpnActive) Slate700 else Emerald500
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = if (is1111Installed) "⚡ Open 1.1.1.1 App" else "📥 Get 1.1.1.1 Free",
                                            color = if (isSystemVpnActive) Color.White else Obsidian950,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            VpnManager.openPrivateDnsSettings(context)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "⚙️ Private DNS",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Text(
                                    text = "💡 Tip: You can also set Private DNS to \"one.one.one.one\" in Android settings to cover the entire device without any VPN app.",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }

                            // Section 2: Ad Blocker Status
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x801E293B))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "🚫 Popup Ad Blocker",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Built-in heuristic blocker intercepts cross-domain popups, redirect advertisements, and malicious tracking links automatically.",
                                    color = Slate400,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0x3310B981))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Emerald400)
                                    )
                                    Text(
                                        text = "Status: Active & Guarding",
                                        color = Emerald400,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        1 -> {
                            // Account / Cloud Sync
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Slate800)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = if (user?.isAnonymous == false) "Signed In" else "Anonymous Guest",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = user?.email ?: "Guest ID: ${user?.id?.take(12) ?: "Local"}...",
                                    color = Slate400,
                                    fontSize = 12.sp
                                )
                            }

                            if (user?.isAnonymous != false) {
                                Text(
                                    text = "Sign in or create an account to sync your playlists and notes across iOS and Android.",
                                    color = Slate400,
                                    fontSize = 12.sp
                                )

                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("Email") },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Slate800,
                                        unfocusedContainerColor = Slate800,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedIndicatorColor = Emerald400,
                                        focusedLabelColor = Emerald400
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Slate800,
                                        unfocusedContainerColor = Slate800,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedIndicatorColor = Emerald400,
                                        focusedLabelColor = Emerald400
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                        enabled = email.isNotBlank() && password.isNotBlank() && !isSubmitting,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            isSubmitting = true
                                            scope.launch {
                                                val res = authService.signUpWithEmail(email.trim(), password.trim())
                                                isSubmitting = false
                                                if (res.isSuccess) {
                                                    Toast.makeText(context, "Account created & linked!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Sign up error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Text("Sign Up", color = Obsidian950, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                                        enabled = email.isNotBlank() && password.isNotBlank() && !isSubmitting,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            isSubmitting = true
                                            scope.launch {
                                                val res = authService.signInWithEmail(email.trim(), password.trim())
                                                isSubmitting = false
                                                if (res.isSuccess) {
                                                    Toast.makeText(context, "Signed in!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Sign in error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Text("Sign In", color = Color.White)
                                    }
                                }
                            } else {
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        authService.signOut()
                                        Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Sign Out", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        2 -> {
                            // Section 3: App Updates & GitHub Releases
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Obsidian950)
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Installed Version",
                                            fontSize = 11.sp,
                                            color = Slate400,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "v${updateManager.getCurrentVersionName()}",
                                            fontSize = 14.sp,
                                            color = Emerald400,
                                            fontWeight = FontWeight.Black
                                        )
                                    }

                                    Button(
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                        enabled = !isCheckingUpdate,
                                        shape = RoundedCornerShape(12.dp),
                                        onClick = {
                                            isCheckingUpdate = true
                                            updateManager.setGitHubRepo(githubRepo)
                                            scope.launch {
                                                val res = updateManager.checkForUpdates(force = true)
                                                isCheckingUpdate = false
                                                res.fold(
                                                    onSuccess = { update ->
                                                        if (update != null) {
                                                            detectedUpdate = update
                                                        } else {
                                                            Toast.makeText(context, "Path Hub is up to date!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    onFailure = { err ->
                                                        Toast.makeText(context, "Check error: ${err.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            }
                                        }
                                    ) {
                                        if (isCheckingUpdate) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                color = Obsidian950,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Checking...", color = Obsidian950, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        } else {
                                            Text("Check Updates", color = Obsidian950, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Text(
                                    text = "GitHub Repository for Releases:",
                                    color = Slate400,
                                    fontSize = 12.sp
                                )

                                OutlinedTextField(
                                    value = githubRepo,
                                    onValueChange = { githubRepo = it },
                                    label = { Text("Repository (owner/repo)") },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Slate800,
                                        unfocusedContainerColor = Slate800,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedIndicatorColor = Emerald400,
                                        focusedLabelColor = Emerald400
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        updateManager.setGitHubRepo(githubRepo)
                                        Toast.makeText(context, "Repository updated", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Save Repository Configuration", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }

                        3 -> {
                            // Section 4: Server Endpoint
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Supabase Database Endpoint:",
                                    color = Slate400,
                                    fontSize = 12.sp
                                )

                                OutlinedTextField(
                                    value = customUrl,
                                    onValueChange = { customUrl = it },
                                    label = { Text("Supabase URL") },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Slate800,
                                        unfocusedContainerColor = Slate800,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedIndicatorColor = Emerald400,
                                        focusedLabelColor = Emerald400
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = customKey,
                                    onValueChange = { customKey = it },
                                    label = { Text("Publishable Key") },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Slate800,
                                        unfocusedContainerColor = Slate800,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedIndicatorColor = Emerald400,
                                        focusedLabelColor = Emerald400
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        SupabaseConfig.saveConfig(context, customUrl, customKey)
                                        Toast.makeText(context, "Server config updated", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Save Server Config", color = Obsidian950, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Balaboo Secret Access Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x2A1E293B))
                        .clickable {
                            pinInput = ""
                            pinError = false
                            showPinDialog = true
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Balaboo",
                        color = Slate400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "›",
                        color = Slate700,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Dialog Bottom Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Close",
                        color = Emerald400,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    if (showPinDialog) {
        Dialog(
            onDismissRequest = { showPinDialog = false }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .widthIn(max = 340.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Slate800, RoundedCornerShape(20.dp)),
                color = Obsidian900
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Balaboo Verification",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = "Enter PIN to access secure history",
                        fontSize = 12.sp,
                        color = Slate400
                    )

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4) {
                                pinInput = it
                                pinError = false
                                if (it == "1991") {
                                    showPinDialog = false
                                    showBalabooHistory = true
                                } else if (it.length == 4) {
                                    pinError = true
                                }
                            }
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        isError = pinError,
                        placeholder = { Text("PIN", color = Slate700) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Obsidian950,
                            unfocusedContainerColor = Obsidian950,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Emerald500,
                            unfocusedIndicatorColor = if (pinError) Rose500 else Slate700,
                            errorIndicatorColor = Rose500
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(0.7f)
                    )

                    if (pinError) {
                        Text(
                            text = "Incorrect PIN",
                            color = Rose500,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showPinDialog = false }) {
                            Text("Cancel", color = Slate400)
                        }

                        Button(
                            onClick = {
                                if (pinInput == "1991") {
                                    showPinDialog = false
                                    showBalabooHistory = true
                                } else {
                                    pinError = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Unlock", color = Obsidian950, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showBalabooHistory) {
        BalabooHistoryDialog(
            onDismiss = { showBalabooHistory = false }
        )
    }

    detectedUpdate?.let { update ->
        UpdateDialog(
            updateInfo = update,
            onDismiss = { detectedUpdate = null }
        )
    }
}
