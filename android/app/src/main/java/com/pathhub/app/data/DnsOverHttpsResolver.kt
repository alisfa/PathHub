package com.pathhub.app.data

import android.util.Log
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * High-performance, zero-speed-loss DNS-over-HTTPS (DoH) resolver.
 * Bypasses ISP DNS hijacking and poisoning (Earthlink, Zain, Asiacell)
 * by querying Cloudflare (1.1.1.1) and Google (8.8.8.8) directly via raw IPs.
 */
class DnsOverHttpsResolver private constructor() : Dns {

    companion object {
        private const val TAG = "PathHubDoH"

        @Volatile
        private var instance: DnsOverHttpsResolver? = null

        fun getInstance(): DnsOverHttpsResolver {
            return instance ?: synchronized(this) {
                instance ?: DnsOverHttpsResolver().also { instance = it }
            }
        }

        // Direct IP endpoints so no system DNS is needed to bootstrap
        private val CLOUDFLARE_IPS = listOf("1.1.1.1", "1.0.0.1")
        private val GOOGLE_IPS = listOf("8.8.8.8", "8.8.4.4")

        // Unblocked European Anycast edge nodes for WGCZ network (XNXX & XVideos)
        // Tested live from Iraq: All nodes respond HTTP 200 OK with full SSL verification
        private val WGCZ_ANYCAST_IPS = listOf(
            "89.222.127.15",
            "89.222.127.13",
            "89.222.127.11",
            "89.222.127.9",
            "89.222.127.5",
            "89.222.127.1"
        )

        private val PORNHUB_ANYCAST_IPS = listOf("66.254.114.41")
    }

    private data class CachedRecord(
        val addresses: List<InetAddress>,
        val expiresAt: Long
    )

    private val cache = ConcurrentHashMap<String, CachedRecord>()

    // Dedicated bootstrap client with direct IP routing and fast timeouts
    private val bootstrapHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .dns(Dns.SYSTEM) // Only used for bootstrap if needed
        .build()

    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.isBlank()) {
            throw UnknownHostException("Hostname is blank")
        }

        // Return immediately if it's already an IP address
        if (isIpAddress(hostname)) {
            return listOf(InetAddress.getByName(hostname))
        }

        val lowerHost = hostname.lowercase().trim()

        // 1. Direct unblocked Anycast route for XNXX & XVideos origin servers
        // Bypasses Iraqi ISP null-routing of the 152.233.100.0/24 subnet
        if (lowerHost == "xnxx.com" || lowerHost.endsWith(".xnxx.com") ||
            lowerHost == "xvideos.com" || lowerHost.endsWith(".xvideos.com")) {
            // Let dedicated CDNs like assets-cdn77.xnxx-cdn.com resolve via standard DoH below
            if (!lowerHost.contains("-cdn.")) {
                return WGCZ_ANYCAST_IPS.map { InetAddress.getByName(it) }
            }
        }

        // 2. Direct unblocked Anycast route for Pornhub origin servers
        if (lowerHost == "pornhub.com" || lowerHost.endsWith(".pornhub.com")) {
            if (!lowerHost.contains("phncdn")) {
                return PORNHUB_ANYCAST_IPS.map { InetAddress.getByName(it) }
            }
        }

        val now = System.currentTimeMillis()
        val cached = cache[hostname]
        if (cached != null && cached.expiresAt > now) {
            return cached.addresses
        }

        // 1. Try Cloudflare Anycast DoH via direct IP
        val cfAddresses = queryCloudflare(hostname)
        if (!cfAddresses.isNullOrEmpty()) {
            cache[hostname] = CachedRecord(cfAddresses, now + TimeUnit.MINUTES.toMillis(15))
            return cfAddresses
        }

        // 2. Fallback to Google DoH via direct IP
        val googleAddresses = queryGoogle(hostname)
        if (!googleAddresses.isNullOrEmpty()) {
            cache[hostname] = CachedRecord(googleAddresses, now + TimeUnit.MINUTES.toMillis(15))
            return googleAddresses
        }

        // 3. Last resort: fall back to system DNS
        return try {
            val systemResult = Dns.SYSTEM.lookup(hostname)
            cache[hostname] = CachedRecord(systemResult, now + TimeUnit.MINUTES.toMillis(2))
            systemResult
        } catch (e: Exception) {
            Log.w(TAG, "All DNS lookups failed for $hostname: ${e.message}")
            throw UnknownHostException("Could not resolve $hostname via DoH or System DNS")
        }
    }

    private fun queryCloudflare(hostname: String): List<InetAddress>? {
        for (ip in CLOUDFLARE_IPS) {
            try {
                val url = "https://$ip/dns-query?name=$hostname&type=A"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .header("Host", "cloudflare-dns.com")
                    .build()

                bootstrapHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val parsed = parseDnsJsonResponse(body)
                        if (parsed.isNotEmpty()) return parsed
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Cloudflare DoH ($ip) error for $hostname: ${e.message}")
            }
        }
        return null
    }

    private fun queryGoogle(hostname: String): List<InetAddress>? {
        for (ip in GOOGLE_IPS) {
            try {
                val url = "https://$ip/resolve?name=$hostname&type=A"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("Host", "dns.google")
                    .build()

                bootstrapHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val parsed = parseDnsJsonResponse(body)
                        if (parsed.isNotEmpty()) return parsed
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Google DoH ($ip) error for $hostname: ${e.message}")
            }
        }
        return null
    }

    private fun parseDnsJsonResponse(jsonString: String): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        try {
            val root = JSONObject(jsonString)
            val answer = root.optJSONArray("Answer") ?: return emptyList()
            for (i in 0 until answer.length()) {
                val item = answer.optJSONObject(i) ?: continue
                val type = item.optInt("type", 0)
                // Type 1 is A record (IPv4)
                if (type == 1) {
                    val data = item.optString("data", "").trim()
                    if (isIpAddress(data)) {
                        list.add(InetAddress.getByName(data))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse DoH JSON response", e)
        }
        return list
    }

    private fun isIpAddress(str: String): Boolean {
        val parts = str.split(".")
        if (parts.size != 4) return false
        return parts.all { p ->
            val num = p.toIntOrNull() ?: return false
            num in 0..255
        }
    }
}
