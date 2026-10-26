package com.pathhub.app.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import android.content.ClipboardManager
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.pathhub.app.data.TabStore
import com.pathhub.app.data.AdBlocker
import com.pathhub.app.data.AntiBlockEngine
import com.pathhub.app.data.VpnManager
import com.pathhub.app.model.WebTab
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextOverflow
import com.pathhub.app.ui.theme.Emerald400
import com.pathhub.app.ui.theme.GlassBorderGradient
import com.pathhub.app.ui.theme.GlassCardGradient
import com.pathhub.app.ui.theme.Obsidian850
import com.pathhub.app.ui.theme.Obsidian900
import com.pathhub.app.ui.theme.Obsidian950
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pathhub.app.data.PlaylistStore
import com.pathhub.app.model.VideoSavePayload
import com.pathhub.app.ui.theme.Emerald500
import com.pathhub.app.ui.theme.Rose500
import com.pathhub.app.ui.theme.Slate400
import com.pathhub.app.ui.theme.Slate800
import com.pathhub.app.ui.theme.Slate900
import kotlinx.serialization.json.Json

class PathHubAndroidBridge(
    private val context: Context,
    private val playlistStore: PlaylistStore,
    private val siteId: String?,
    private val onSavedStateChanged: (Boolean) -> Unit,
    private val onFullscreenChanged: (Boolean) -> Unit
) {
    private val json = Json { ignoreUnknownKeys = true }

    @JavascriptInterface
    fun saveVideo(jsonStr: String) {
        try {
            val payload = json.decodeFromString<VideoSavePayload>(jsonStr)
            val withSite = if (payload.siteId == null && siteId != null) payload.copy(siteId = siteId) else payload
            playlistStore.saveVideoFromBridge(withSite)
            onSavedStateChanged(true)
            showToast("Saved to Playlist ❤️")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Failed to save video")
        }
    }

    @JavascriptInterface
    fun isSaved(videoUrl: String): Boolean {
        return playlistStore.isSaved(videoUrl)
    }

    @JavascriptInterface
    fun setBrightness(value: Float) {}

    @JavascriptInterface
    fun setVolume(value: Float) {}

    @JavascriptInterface
    fun enterFullscreen() {
        onFullscreenChanged(true)
    }

    @JavascriptInterface
    fun exitFullscreen() {
        onFullscreenChanged(false)
    }

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    initialUrl: String,
    siteId: String?,
    siteName: String,
    playlistStore: PlaylistStore,
    onGoHome: () -> Unit,
    onOpenPlaylist: () -> Unit
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var isCurrentVideoSaved by remember { mutableStateOf(false) }
    var isFullscreenVideo by remember { mutableStateOf(false) }
    var userDismissedBar by remember { mutableStateOf(false) }

    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val playlistItems by playlistStore.items.collectAsState()
    val tabStore = remember { TabStore.getInstance(context) }
    val tabs by tabStore.tabs.collectAsState()
    val activeTabId by tabStore.activeTabId.collectAsState()
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInputText by remember { mutableStateOf("") }
    var isVpnActive by remember { mutableStateOf(VpnManager.isVpnEnabled(context)) }

    fun navigateToUrl(rawUrl: String) {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return
        val formatted = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else if (trimmed.contains(".") && !trimmed.contains(" ")) {
            "https://$trimmed"
        } else {
            "https://www.google.com/search?q=" + java.net.URLEncoder.encode(trimmed, "UTF-8")
        }
        val resolved = VpnManager.resolveUrl(context, formatted)
        webViewInstance?.loadUrl(resolved)
        tabStore.updateActiveTab(url = resolved, title = "Loading...")
    }

    fun pasteAndGo() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
        if (!clipText.isNullOrBlank()) {
            navigateToUrl(clipText)
            Toast.makeText(context, "Navigating to link...", Toast.LENGTH_SHORT).show()
            showUrlDialog = false
        } else {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
    }

    val currentUrl = webViewInstance?.url ?: initialUrl
    val currentHostDomain = remember(currentUrl) {
        try {
            android.net.Uri.parse(currentUrl).host?.removePrefix("www.") ?: "Web"
        } catch (e: Exception) {
            "Web"
        }
    }

    // Intercept back button to navigate in history or exit fullscreen/restore bar
    BackHandler {
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            customView = null
            customViewCallback = null
            isFullscreenVideo = false
        } else if (isFullscreenVideo) {
            isFullscreenVideo = false
        } else if (userDismissedBar) {
            userDismissedBar = false
        } else if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onGoHome()
        }
    }

    DisposableEffect(Unit) {
        if (tabStore.tabs.value.isEmpty()) {
            val resolved = VpnManager.resolveUrl(context, initialUrl)
            tabStore.openTab(url = resolved, title = siteName, siteId = siteId)
        }
        onDispose {
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }

    val scriptContent = remember {
        try {
            context.assets.open("player.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }

    val bridge = remember {
        PathHubAndroidBridge(
            context = context,
            playlistStore = playlistStore,
            siteId = siteId,
            onSavedStateChanged = { isCurrentVideoSaved = it },
            onFullscreenChanged = { isFullscreenVideo = it }
        )
    }

    val isControlsHidden = isFullscreenVideo || customView != null || userDismissedBar

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Main WebView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        userAgentString = "$userAgentString PathHub/1.0"
                        // Popup Ad Blocking at WebSettings level
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                    }

                    addJavascriptInterface(bridge, "AndroidBridge")

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val target = request?.url?.toString() ?: return false
                            val current = view?.url ?: initialUrl
                            if (AdBlocker.isAdUrl(target) || AdBlocker.isCrossDomainAdRedirect(current, target)) {
                                android.util.Log.d("PathHubAdBlocker", "Blocked ad navigation: $target")
                                return true // Cancel ad navigation
                            }
                            val resolved = VpnManager.resolveUrl(context, target)
                            if (resolved != target) {
                                view?.loadUrl(resolved)
                                return true
                            }
                            return false
                        }

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val target = request?.url?.toString()
                            if (AdBlocker.isAdUrl(target)) {
                                return WebResourceResponse(
                                    "text/plain",
                                    "UTF-8",
                                    ByteArrayInputStream(ByteArray(0))
                                )
                            }
                            if (request != null && AntiBlockEngine.shouldIntercept(request.url)) {
                                val bypassedResponse = AntiBlockEngine.interceptRequest(context, request)
                                if (bypassedResponse != null) {
                                    return bypassedResponse
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                            if (url != null) {
                                tabStore.updateActiveTab(url = url, title = view?.title ?: siteName)
                            }
                            if (scriptContent.isNotBlank()) {
                                view?.evaluateJavascript(scriptContent, null)
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                            if (url != null && url.isNotBlank() && !url.startsWith("about:")) {
                                val pageTitle = view?.title?.ifBlank { siteName } ?: siteName
                                tabStore.updateActiveTab(url = url, title = pageTitle)
                                com.pathhub.app.data.HistoryStore.getInstance(ctx).recordVisit(url, pageTitle)
                            }
                            if (scriptContent.isNotBlank()) {
                                view?.evaluateJavascript(scriptContent, null)
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?
                        ): Boolean {
                            // Block intrusive popup windows completely
                            return false
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadingProgress = newProgress
                        }

                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            customView = view
                            customViewCallback = callback
                            isFullscreenVideo = true
                        }

                        override fun onHideCustomView() {
                            customView = null
                            customViewCallback = null
                            isFullscreenVideo = false
                        }
                    }

                    val resolvedInitial = VpnManager.resolveUrl(ctx, initialUrl)
                    loadUrl(resolvedInitial)
                    webViewInstance = this
                }
            },
            update = {
                webViewInstance = it
            }
        )

        // Native Fullscreen Video Overlay (e.g. HTML5 native fullscreen view)
        customView?.let { fullView ->
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                factory = { fullView }
            )
        }

        // Top Liquid Loading Progress Bar
        if (loadingProgress in 1..99 && !isControlsHidden) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .height(3.5.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF10B981),
                                Color(0xFF06B6D4),
                                Color(0xFF3B82F6)
                            )
                        )
                    )
            )
        }

        // Top Floating Address / Domain Pill (Tap to paste link or enter URL)
        if (!isControlsHidden) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .shadow(16.dp, CircleShape, ambientColor = Color.Black)
                    .clip(CircleShape)
                    .background(Color(0xD90B1120))
                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
                    .clickable {
                        urlInputText = webViewInstance?.url ?: initialUrl
                        showUrlDialog = true
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = currentHostDomain,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste Link",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // Floating Liquid Glass Bottom Navbar (Hidden on Fullscreen or User Swipe-Down)
        AnimatedVisibility(
            visible = !isControlsHidden,
            enter = slideInVertically(initialOffsetY = { it * 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it * 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 20.dp, end = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .shadow(elevation = 24.dp, shape = CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(CircleShape)
                    .background(Color(0xEB0B1120))
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x66FFFFFF),
                                Color(0x1AFFFFFF),
                                Color(0x05FFFFFF)
                            )
                        ),
                        shape = CircleShape
                    )
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 20f) {
                                userDismissedBar = true
                            }
                        }
                    }
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back
                IconButton(
                    onClick = { webViewInstance?.goBack() },
                    enabled = canGoBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) Color.White else Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Forward
                IconButton(
                    onClick = { webViewInstance?.goForward() },
                    enabled = canGoForward,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) Color.White else Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Home
                IconButton(
                    onClick = onGoHome,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Save Video (Glowing Jewel Center Button)
                IconButton(
                    onClick = {
                        webViewInstance?.evaluateJavascript("window.__pathHubSaveCurrentVideo && window.__pathHubSaveCurrentVideo();") { res ->
                            if (res == "true") {
                                isCurrentVideoSaved = true
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = CircleShape,
                                ambientColor = if (isCurrentVideoSaved) Rose500.copy(alpha = 0.6f) else Emerald500.copy(alpha = 0.5f)
                            )
                            .clip(CircleShape)
                            .background(
                                if (isCurrentVideoSaved)
                                    Brush.linearGradient(listOf(Color(0xFFF43F5E), Color(0xFFBE123C)))
                                else
                                    Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF047857)))
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.15f))),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCurrentVideoSaved) Icons.Default.Favorite else Icons.Default.Add,
                            contentDescription = "Save Video",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Paste Link / Enter URL Button
                IconButton(
                    onClick = {
                        urlInputText = webViewInstance?.url ?: initialUrl
                        showUrlDialog = true
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Paste Link or Enter URL",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Playlist
                IconButton(
                    onClick = onOpenPlaylist,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = "Playlist",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(20.dp)
                        )
                        if (playlistItems.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Rose500)
                            )
                        }
                    }
                }

                // Tabs Switcher Button
                IconButton(
                    onClick = { showTabSwitcher = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Tabs",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(20.dp)
                        )
                        if (tabs.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Emerald500)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "${tabs.size}",
                                    color = Obsidian950,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                // Collapse Bar Button
                IconButton(
                    onClick = { userDismissedBar = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Hide bar",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Restore Lower Compact Mini-Pill Button when bar is collapsed
        AnimatedVisibility(
            visible = userDismissedBar && !isFullscreenVideo && customView == null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .shadow(16.dp, CircleShape, ambientColor = Color.Black)
                    .clip(CircleShape)
                    .background(Color(0xE6080E1A))
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF))),
                        CircleShape
                    )
                    .clickable { userDismissedBar = false }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Show controls",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(14.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isCurrentVideoSaved) Rose500 else Emerald500)
                    )
                    Text(
                        text = "Controls",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Tab Switcher Dialog
        if (showTabSwitcher) {
            AlertDialog(
                containerColor = Obsidian900,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.border(1.dp, GlassBorderGradient, RoundedCornerShape(26.dp)),
                onDismissRequest = { showTabSwitcher = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Tabs",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "${tabs.size} active ${if (tabs.size == 1) "tab" else "tabs"}",
                                fontSize = 12.sp,
                                color = Slate400
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FFFFFF))
                                    .clickable { showSettingsDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    val target = VpnManager.resolveUrl(context, "https://www.pornhub.com")
                                    val newTab = tabStore.openTab(url = target, title = "Pornhub")
                                    webViewInstance?.loadUrl(newTab.url)
                                    showTabSwitcher = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Obsidian950, modifier = Modifier.size(14.dp))
                                    Text("New", color = Obsidian950, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                },
                text = {
                    if (tabs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No open tabs", color = Slate400, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(items = tabs, key = { it.id }) { tab ->
                                val isActive = tab.id == activeTabId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isActive) Color(0x3310B981) else Color(0x22FFFFFF))
                                        .border(
                                            width = 1.dp,
                                            brush = if (isActive) Brush.linearGradient(listOf(Emerald400, Emerald500.copy(alpha = 0.3f))) else GlassBorderGradient,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable {
                                            tabStore.selectTab(tab.id)
                                            webViewInstance?.loadUrl(tab.url)
                                            showTabSwitcher = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = tab.title,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isActive) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(Color(0x3310B981))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("ACTIVE", color = Emerald400, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                                }
                                            }
                                        }
                                        Text(
                                            text = tab.hostDomain,
                                            color = Slate400,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }

                                    IconButton(
                                        onClick = { tabStore.closeTab(tab.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close Tab",
                                            tint = Slate400,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTabSwitcher = false }) {
                        Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Enter / Paste URL Dialog
        if (showUrlDialog) {
            AlertDialog(
                containerColor = Obsidian900,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.border(1.dp, GlassBorderGradient, RoundedCornerShape(26.dp)),
                onDismissRequest = { showUrlDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Emerald500.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = Emerald400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "Enter or Paste Link",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { showUrlDialog = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Slate400,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Quick 1-Tap "Paste from Clipboard & Go" Button
                        Button(
                            onClick = { pasteAndGo() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    tint = Obsidian950,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Paste from Clipboard & Go",
                                    color = Obsidian950,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Editable URL Field
                        OutlinedTextField(
                            value = urlInputText,
                            onValueChange = { urlInputText = it },
                            placeholder = {
                                Text(
                                    text = "https://example.com/video...",
                                    color = Slate400.copy(alpha = 0.5f),
                                    fontSize = 13.sp
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Go
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onGo = {
                                    if (urlInputText.isNotBlank()) {
                                        navigateToUrl(urlInputText)
                                        showUrlDialog = false
                                    }
                                }
                            ),
                            trailingIcon = {
                                if (urlInputText.isNotEmpty()) {
                                    IconButton(onClick = { urlInputText = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Slate400,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = Obsidian950,
                                unfocusedContainerColor = Obsidian950,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Emerald400,
                                unfocusedIndicatorColor = Color(0x33FFFFFF)
                            )
                        )

                        // Quick site shortcuts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Pornhub" to "https://www.pornhub.com", "XNXX" to "https://www.xnxx.com", "Xvideos" to "https://www.xvideos.com").forEach { (name, link) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x1AFFFFFF))
                                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                                        .clickable {
                                            navigateToUrl(link)
                                            showUrlDialog = false
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (urlInputText.isNotBlank()) {
                                navigateToUrl(urlInputText)
                                showUrlDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Go", color = Obsidian950, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUrlDialog = false }) {
                        Text("Cancel", color = Slate400)
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
    }
}

// Extension to adjust color opacity
private fun Color.opacity(ratio: Float): Color = this.copy(alpha = ratio)
