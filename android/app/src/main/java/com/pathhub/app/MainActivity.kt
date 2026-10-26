package com.pathhub.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pathhub.app.data.PlaylistStore
import com.pathhub.app.data.SiteStore
import com.pathhub.app.data.UpdateInfo
import com.pathhub.app.data.UpdateManager
import com.pathhub.app.ui.components.UpdateDialog
import com.pathhub.app.ui.screens.BrowserScreen
import com.pathhub.app.ui.screens.HomeScreen
import com.pathhub.app.ui.screens.PlaylistScreen
import com.pathhub.app.ui.theme.PathHubTheme
import com.pathhub.app.ui.theme.Slate900

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val siteStore = SiteStore.getInstance(applicationContext)
        val playlistStore = PlaylistStore.getInstance(applicationContext)

        setContent {
            PathHubTheme {
                val navController = rememberNavController()
                val updateManager = remember { UpdateManager.getInstance(applicationContext) }
                var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

                LaunchedEffect(Unit) {
                    updateManager.checkForUpdates().onSuccess { update ->
                        availableUpdate = update
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Slate900)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(
                                siteStore = siteStore,
                                onOpenBrowser = { url, siteId, name ->
                                    val encodedUrl = Uri.encode(url)
                                    val encodedName = Uri.encode(name)
                                    navController.navigate("browser?url=$encodedUrl&siteId=$siteId&name=$encodedName")
                                },
                                onOpenPlaylist = {
                                    navController.navigate("playlist")
                                }
                            )
                        }

                        composable(
                            route = "browser?url={url}&siteId={siteId}&name={name}",
                            arguments = listOf(
                                navArgument("url") { type = NavType.StringType; defaultValue = "https://www.pornhub.com" },
                                navArgument("siteId") { type = NavType.StringType; defaultValue = "" },
                                navArgument("name") { type = NavType.StringType; defaultValue = "Browser" }
                            )
                        ) { backStackEntry ->
                            val url = Uri.decode(backStackEntry.arguments?.getString("url") ?: "https://www.pornhub.com")
                            val siteId = backStackEntry.arguments?.getString("siteId")?.ifEmpty { null }
                            val name = Uri.decode(backStackEntry.arguments?.getString("name") ?: "Browser")

                            BrowserScreen(
                                initialUrl = url,
                                siteId = siteId,
                                siteName = name,
                                playlistStore = playlistStore,
                                onGoHome = {
                                    navController.popBackStack("home", inclusive = false)
                                },
                                onOpenPlaylist = {
                                    navController.navigate("playlist")
                                }
                            )
                        }

                        composable("playlist") {
                            PlaylistScreen(
                                playlistStore = playlistStore,
                                onBack = {
                                    navController.popBackStack()
                                },
                                onPlayVideo = { targetUrl, title ->
                                    val encodedUrl = Uri.encode(targetUrl)
                                    val encodedTitle = Uri.encode(title)
                                    navController.navigate("browser?url=$encodedUrl&siteId=&name=$encodedTitle")
                                }
                            )
                        }
                    }

                    availableUpdate?.let { info ->
                        UpdateDialog(
                            updateInfo = info,
                            onDismiss = { availableUpdate = null }
                        )
                    }
                }
            }
        }
    }
}
