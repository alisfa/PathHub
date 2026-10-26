package com.pathhub.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pathhub.app.data.DownloadState
import com.pathhub.app.data.UpdateInfo
import com.pathhub.app.data.UpdateManager
import com.pathhub.app.ui.theme.Emerald400
import com.pathhub.app.ui.theme.Emerald500
import com.pathhub.app.ui.theme.Obsidian900
import com.pathhub.app.ui.theme.Obsidian950
import com.pathhub.app.ui.theme.Rose500
import com.pathhub.app.ui.theme.Slate200
import com.pathhub.app.ui.theme.Slate400
import com.pathhub.app.ui.theme.Slate700
import com.pathhub.app.ui.theme.Slate800
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val updateManager = UpdateManager.getInstance(context)
    val downloadState by updateManager.downloadState.collectAsState()
    val scope = rememberCoroutineScope()
    val currentVersion = updateManager.getCurrentVersionName()

    AlertDialog(
        containerColor = Obsidian900,
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.border(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.15f),
            shape = RoundedCornerShape(26.dp)
        ),
        onDismissRequest = {
            if (downloadState !is DownloadState.Downloading) {
                onDismiss()
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Emerald500.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Update",
                        tint = Emerald400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "New Update Available",
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                        letterSpacing = (-0.5).sp,
                        color = Color.White
                    )
                    Text(
                        text = updateInfo.releaseName.ifEmpty { updateInfo.tagName },
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = Emerald400
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Version badge pill row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Obsidian950)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Installed",
                            fontSize = 10.sp,
                            color = Slate400,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "v$currentVersion",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "➔",
                        fontSize = 14.sp,
                        color = Emerald400,
                        fontWeight = FontWeight.Bold
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Latest Release",
                            fontSize = 10.sp,
                            color = Slate400,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = updateInfo.tagName,
                            fontSize = 13.sp,
                            color = Emerald400,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Changelog Card
                Text(
                    text = "Release Notes",
                    fontSize = 12.sp,
                    color = Slate400,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Obsidian950)
                        .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = updateInfo.changelog.ifBlank { "Performance improvements and bug fixes." },
                        color = Slate200,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }

                // Download Progress / Status section
                when (val state = downloadState) {
                    is DownloadState.Downloading -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            val percent = (state.progress * 100).toInt()
                            val mbFormat = DecimalFormat("#0.0")
                            val downloadedMb = mbFormat.format(state.bytesDownloaded / 1048576.0)
                            val totalMb = mbFormat.format(state.totalBytes / 1048576.0)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading APK...",
                                    fontSize = 11.sp,
                                    color = Slate200
                                )
                                Text(
                                    text = "$percent% ($downloadedMb / $totalMb MB)",
                                    fontSize = 11.sp,
                                    color = Emerald400,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Emerald500,
                                trackColor = Slate800
                            )
                        }
                    }
                    is DownloadState.Completed -> {
                        Text(
                            text = "✅ APK downloaded! Ready to install.",
                            fontSize = 12.sp,
                            color = Emerald400,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    is DownloadState.Error -> {
                        Text(
                            text = "❌ Error: ${state.message}",
                            fontSize = 12.sp,
                            color = Rose500,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    is DownloadState.Idle -> {
                        if (updateInfo.fileSize > 0) {
                            val mbFormat = DecimalFormat("#0.0")
                            val sizeMb = mbFormat.format(updateInfo.fileSize / 1048576.0)
                            Text(
                                text = "Package: ${updateInfo.fileName} (~$sizeMb MB)",
                                fontSize = 11.sp,
                                color = Slate400
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (val state = downloadState) {
                is DownloadState.Downloading -> {
                    // Disabled button while downloading
                    Button(
                        onClick = {},
                        enabled = false,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = Emerald500.copy(alpha = 0.3f),
                            disabledContentColor = Color.White
                        )
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Downloading...")
                    }
                }
                is DownloadState.Completed -> {
                    Button(
                        onClick = {
                            updateManager.installApk(state.file)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald500,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Install Update", fontWeight = FontWeight.Black)
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            scope.launch {
                                updateManager.downloadApk(updateInfo)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald500,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Update Now", fontWeight = FontWeight.Black)
                    }
                }
            }
        },
        dismissButton = {
            if (downloadState !is DownloadState.Downloading) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            // Option to open GitHub release page in browser
                            try {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.htmlUrl)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(browserIntent)
                            } catch (_: Exception) {}
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Release Page",
                            tint = Slate400,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Release Page", color = Slate400, fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = {
                            updateManager.resetDownloadState()
                            onDismiss()
                        }
                    ) {
                        Text("Later", color = Slate400)
                    }
                }
            }
        }
    )
}
