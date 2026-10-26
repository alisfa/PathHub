package com.pathhub.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Intelligent In-App Censorship & Location-Blocking Bypass Engine.
 * Transparently intercepts web requests to blocked domains and resolves
 * them through Encrypted DNS (DoH) at 100% full ISP line speed with 0 speed loss.
 */
object AntiBlockEngine {
    private const val TAG = "AntiBlockEngine"
    private const val PREFS_NAME = "pathhub_antiblock_prefs"
    private const val KEY_ENABLED = "antiblock_enabled"

    // Domains frequently subjected to ISP / regulatory blocking in Iraq & Middle East
    private val CENSORED_DOMAINS = setOf(
        "pornhub.com",
        "phncdn.com",
        "fourten.geekadm.net",
        "pix.geekadm.net",
        "xvideos.com",
        "xvideos-cdn.com",
        "xvideos.es",
        "xnxx.com",
        "xnxx-cdn.com",
        "xnxx.net",
        "redtube.com",
        "youporn.com",
        "spankbang.com",
        "xhamster.com",
        "eporner.com",
        "tube8.com",
        "rule34video.com"
    )

    // Shared high-performance OkHttpClient powered by DoH
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(DnsOverHttpsResolver.getInstance())
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Checks whether a given URI belongs to a known blocked domain or its CDN.
     */
    fun shouldIntercept(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return CENSORED_DOMAINS.any { host == it || host.endsWith(".$it") }
    }

    /**
     * Intercepts WebView resource requests and fetches them directly via Encrypted DoH,
     * maintaining cookies and streaming responses directly with zero memory buffering.
     */
    fun interceptRequest(context: Context, request: WebResourceRequest): WebResourceResponse? {
        if (!isEnabled(context)) return null

        // If device-wide VPN (such as Cloudflare 1.1.1.1 / WARP) is active on the device,
        // let Android's native WebView connect through the VPN interface with zero interference
        if (VpnManager.isSystemVpnActive(context)) return null

        val uri = request.url ?: return null
        if (!shouldIntercept(uri)) return null

        val method = request.method
        // We handle GET and HEAD requests via DoH; let POST proceed natively if needed
        if (!method.equals("GET", ignoreCase = true) && !method.equals("HEAD", ignoreCase = true)) {
            return null
        }

        val urlString = uri.toString()

        try {
            val reqBuilder = Request.Builder()
                .url(urlString)
                .method(method, null)

            // Copy browser request headers (User-Agent, Referer, Accept, Range, etc.)
            // Skip Host, Connection, and Accept-Encoding so OkHttp handles transparent decompression
            request.requestHeaders?.forEach { (key, value) ->
                if (!key.equals("Host", ignoreCase = true) && 
                    !key.equals("Connection", ignoreCase = true) &&
                    !key.equals("Accept-Encoding", ignoreCase = true)) {
                    reqBuilder.header(key, value)
                }
            }

            // Synchronize cookies from Android WebView CookieManager
            val cookieManager = CookieManager.getInstance()
            val cookieStr = cookieManager.getCookie(urlString)
            if (!cookieStr.isNullOrBlank()) {
                reqBuilder.header("Cookie", cookieStr)
            }

            val response: Response = client.newCall(reqBuilder.build()).execute()

            // Synchronize response Set-Cookie headers back to WebView
            val responseHeaders = response.headers
            var hasNewCookies = false
            for (headerName in responseHeaders.names()) {
                if (headerName.equals("Set-Cookie", ignoreCase = true)) {
                    val cookieValues = responseHeaders.values(headerName)
                    for (cookieVal in cookieValues) {
                        cookieManager.setCookie(urlString, cookieVal)
                        hasNewCookies = true
                    }
                }
            }
            if (hasNewCookies) {
                cookieManager.flush()
            }

            // Extract MIME type and character encoding
            val contentType = response.header("Content-Type", "text/html; charset=UTF-8") ?: "text/html; charset=UTF-8"
            val mimeType = contentType.substringBefore(";").trim().ifEmpty { "text/html" }
            val encoding = if (contentType.contains("charset=", ignoreCase = true)) {
                contentType.substringAfter("charset=", "UTF-8").substringBefore(";").trim()
            } else {
                "UTF-8"
            }

            val responseHeadersMap = mutableMapOf<String, String>()
            for (i in 0 until responseHeaders.size) {
                val hName = responseHeaders.name(i)
                // Filter out content-encoding since OkHttp decompresses the byteStream
                if (!hName.equals("Content-Encoding", ignoreCase = true)) {
                    responseHeadersMap[hName] = responseHeaders.value(i)
                }
            }

            val responseBody = response.body
            val inputStream: InputStream = responseBody?.byteStream() ?: "".byteInputStream()
            val statusCode = response.code
            val message = response.message.ifBlank { "OK" }

            return WebResourceResponse(
                mimeType,
                encoding,
                statusCode,
                message,
                responseHeadersMap,
                inputStream
            )
        } catch (e: Exception) {
            Log.w(TAG, "AntiBlock intercept error for $urlString: ${e.message}")
            return null
        }
    }
}
