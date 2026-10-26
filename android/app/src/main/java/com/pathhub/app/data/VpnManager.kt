package com.pathhub.app.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.Settings
import android.util.Log

/**
 * Cloudflare 1.1.1.1 Anycast Device VPN & Location Bypass Manager.
 * Detects device-wide VPN protection, integrates with official Cloudflare 1.1.1.1 (WARP),
 * and provides 1-tap shortcuts to Android Private DNS (one.one.one.one) for zero-speed-loss bypass.
 */
object VpnManager {
    private const val TAG = "PathHubVpn"
    private const val PREFS_NAME = "pathhub_vpn_prefs"
    private const val KEY_VPN_ENABLED = "pathhub_vpn_enabled"

    // Official Cloudflare 1.1.1.1 (WARP) Android Application Package
    const val CLOUDFLARE_1111_PKG = "com.cloudflare.onedotonedotonedotone"

    // Cloudflare Anycast Edge VPN Gateway (Fallback proxy)
    const val VPN_GATEWAY_URL = "https://pathhub-vpn.malekkaraso.workers.dev"

    /**
     * Checks if ANY system-wide VPN is currently active on the device (covering all apps).
     * Returns true when 1.1.1.1, WARP, WireGuard, or any system VPN tunnel is connected.
     */
    fun isSystemVpnActive(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking system VPN: ${e.message}")
            false
        }
    }

    /**
     * Checks if the official Cloudflare 1.1.1.1 app is installed on this device.
     */
    fun is1111Installed(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(CLOUDFLARE_1111_PKG, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Launches the Cloudflare 1.1.1.1 app to connect, or opens the installation page.
     */
    fun launch1111App(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(CLOUDFLARE_1111_PKG)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            open1111InstallPage(context)
        }
    }

    /**
     * Opens Google Play or browser to install Cloudflare 1.1.1.1 with WARP for free.
     */
    fun open1111InstallPage(context: Context) {
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$CLOUDFLARE_1111_PKG")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$CLOUDFLARE_1111_PKG")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    /**
     * Opens Android System Settings to configure Android Private DNS (one.one.one.one).
     * This provides system-wide encrypted Cloudflare DNS across all apps with zero battery drain.
     */
    fun openPrivateDnsSettings(context: Context) {
        try {
            val intent = Intent("android.settings.NETWORK_PROVIDER_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    fun isVpnEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_VPN_ENABLED, true)
    }

    fun setVpnEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_VPN_ENABLED, enabled).apply()
    }

    /**
     * Clean URL resolver.
     * With a system-wide VPN (like Cloudflare 1.1.1.1) active, URLs connect directly
     * to the authentic target domains without proxy wrapper interference or video breakage.
     */
    fun resolveUrl(context: Context, targetUrl: String): String {
        val clean = targetUrl.trim()
        if (clean.isEmpty()) return clean

        // If the URL was tunneled with an older proxy link, restore it to the authentic clean URL
        if (isTunneled(clean)) {
            return getDisplayUrl(clean)
        }

        return clean
    }

    /**
     * Checks if a URL is currently tunneled through the legacy VPN gateway.
     */
    fun isTunneled(url: String): Boolean {
        return url.startsWith(VPN_GATEWAY_URL)
    }

    /**
     * Extracts the real target destination from a tunneled URL for clean UI display.
     */
    fun getDisplayUrl(url: String): String {
        if (isTunneled(url)) {
            val idx = url.indexOf("?url=")
            if (idx != -1) {
                val raw = url.substring(idx + 5)
                return Uri.decode(raw)
            }
        }
        return url
    }
}
