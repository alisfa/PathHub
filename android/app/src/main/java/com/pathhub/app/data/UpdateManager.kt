package com.pathhub.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val tagName: String,
    val releaseName: String,
    val changelog: String,
    val downloadUrl: String,
    val fileName: String,
    val fileSize: Long,
    val htmlUrl: String
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Completed(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class UpdateManager private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences("pathhub_updates", Context.MODE_PRIVATE)

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    companion object {
        const val DEFAULT_GITHUB_REPO = "alisfa/PathHub"
        const val COMPILE_VERSION = "1.0.4"
        private const val PREF_REPO_KEY = "github_repo"

        @Volatile
        private var instance: UpdateManager? = null

        fun getInstance(context: Context): UpdateManager {
            return instance ?: synchronized(this) {
                instance ?: UpdateManager(context.applicationContext).also { instance = it }
            }
        }

        fun compareVersions(remoteVer: String, currentVer: String): Int {
            fun normalize(v: String): List<Int> {
                return v.trim()
                    .removePrefix("v")
                    .removePrefix("V")
                    .split(".", "-", "_")
                    .mapNotNull { it.toIntOrNull() }
            }

            val rParts = normalize(remoteVer)
            val cParts = normalize(currentVer)
            val maxLen = maxOf(rParts.size, cParts.size)

            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (r != c) {
                    return r.compareTo(c)
                }
            }
            return 0
        }
    }

    fun getGitHubRepo(): String {
        return prefs.getString(PREF_REPO_KEY, DEFAULT_GITHUB_REPO) ?: DEFAULT_GITHUB_REPO
    }

    fun setGitHubRepo(repo: String) {
        prefs.edit().putString(PREF_REPO_KEY, repo.trim()).apply()
    }

    fun getCurrentVersionName(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName?.ifEmpty { COMPILE_VERSION } ?: COMPILE_VERSION
        } catch (_: Exception) {
            COMPILE_VERSION
        }
    }

    suspend fun checkForUpdates(force: Boolean = false): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val repo = getGitHubRepo().trim().ifEmpty { DEFAULT_GITHUB_REPO }
            val currentVer = getCurrentVersionName()
            val apiUrl = "https://api.github.com/repos/$repo/releases/latest"
            val url = URL(apiUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "PathHub-Android/$currentVer")
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val releaseJson = JSONObject(jsonStr)

                val tagName = releaseJson.optString("tag_name", "")
                val releaseName = releaseJson.optString("name", tagName)
                val changelog = releaseJson.optString("body", "No release notes provided.")
                val htmlUrl = releaseJson.optString("html_url", "https://github.com/$repo/releases")

                if (!force && compareVersions(tagName, currentVer) <= 0) {
                    // Up to date
                    return@withContext Result.success(null)
                }

                // Find APK asset
                val assetsArray = releaseJson.optJSONArray("assets")
                var apkUrl: String? = null
                var apkName: String? = null
                var apkSize: Long = 0

                if (assetsArray != null) {
                    for (i in 0 until assetsArray.length()) {
                        val asset = assetsArray.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            apkName = name
                            apkSize = asset.optLong("size", 0)
                            break
                        }
                    }
                }

                if (apkUrl.isNullOrEmpty()) {
                    apkUrl = "https://github.com/$repo/releases/download/$tagName/PathHub-debug.apk"
                    apkName = "PathHub-debug.apk"
                }

                val updateInfo = UpdateInfo(
                    tagName = tagName,
                    releaseName = releaseName,
                    changelog = changelog,
                    downloadUrl = apkUrl,
                    fileName = apkName ?: "PathHub-debug.apk",
                    fileSize = apkSize,
                    htmlUrl = htmlUrl
                )
                return@withContext Result.success(updateInfo)
            } else {
                // Fallback: GitHub Releases redirect resolution (bypass API rate-limit)
                val webConn = (URL("https://github.com/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                val redirectUrl = webConn.getHeaderField("Location") ?: ""
                if (redirectUrl.contains("/releases/tag/")) {
                    val tag = redirectUrl.substringAfterLast("/")
                    if (force || compareVersions(tag, currentVer) > 0) {
                        val updateInfo = UpdateInfo(
                            tagName = tag,
                            releaseName = "Path Hub $tag",
                            changelog = "Latest official release from GitHub.",
                            downloadUrl = "https://github.com/$repo/releases/download/$tag/PathHub-debug.apk",
                            fileName = "PathHub-debug.apk",
                            fileSize = 17500000L,
                            htmlUrl = "https://github.com/$repo/releases/tag/$tag"
                        )
                        return@withContext Result.success(updateInfo)
                    } else {
                        return@withContext Result.success(null)
                    }
                }
                return@withContext Result.failure(Exception("GitHub status $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadApk(updateInfo: UpdateInfo): File? = withContext(Dispatchers.IO) {
        try {
            _downloadState.value = DownloadState.Downloading(0f, 0, updateInfo.fileSize)

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outputFile = File(updatesDir, updateInfo.fileName)

            val url = URL(updateInfo.downloadUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "PathHub-Android")
            }

            val totalBytes = if (conn.contentLengthLong > 0) conn.contentLengthLong else updateInfo.fileSize
            var downloadedBytes = 0L

            conn.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0.5f
                        _downloadState.value = DownloadState.Downloading(progress, downloadedBytes, totalBytes)
                    }
                }
            }

            _downloadState.value = DownloadState.Completed(outputFile)
            outputFile
        } catch (e: Exception) {
            _downloadState.value = DownloadState.Error(e.message ?: "Failed to download update")
            null
        }
    }

    fun installApk(file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
    }
}
