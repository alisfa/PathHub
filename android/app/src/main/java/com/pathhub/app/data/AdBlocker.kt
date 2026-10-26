package com.pathhub.app.data

import android.net.Uri

object AdBlocker {
    private val adDomains = setOf(
        // High-frequency adult & popunder ad networks
        "trafficjunky.com",
        "trafficfactory.biz",
        "trafficstars.com",
        "exoclick.com",
        "exosrv.com",
        "tsyndicate.com",
        "popads.net",
        "popcash.net",
        "adsterra.com",
        "clickadu.com",
        "propellerads.com",
        "juicyads.com",
        "plugrush.com",
        "ero-advertising.com",
        "realsrv.com",
        "etahub.com",
        "pt167.com",
        "adk2x.com",
        "adnium.com",
        "adxadserv.com",
        "onclickalgo.com",
        "onclickbright.com",
        "onclickperformance.com",
        "onclicksuper.com",
        "wpadmngr.com",
        "adxpansion.com",
        "hubtraffic.com",
        "doubleclick.net",
        "googleads.g.doubleclick.net",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "ad-maven.com",
        "yllix.com",
        "hilltopads.com",
        "monetag.com",
        "adnxs.com",
        "adform.net",
        "bidswitch.net",
        "rubiconproject.com",
        "casalemedia.com",
        "openx.net",
        "pubmatic.com",
        "smartadserver.com",
        "outbrain.com",
        "taboola.com",
        "mgid.com",
        "revcontent.com",
        "adsupply.com",
        "cpamatica.com",
        "zeroredirect.com",
        // Affiliate cam & popunder landing domains
        "fapster.xxx",
        "strp.stream",
        "stripcdn.com",
        "bngpt.com",
        "bongacams.com",
        "chaturbate.com",
        "stripchat.com",
        "livejasmin.com",
        "cam4.com",
        "flirt4free.com",
        "jerkmate.com",
        "camsoda.com",
        "imlive.com",
        "myfreecams.com",
        "streamate.com",
        "tubecup.com",
        "awempire.com",
        "cams.xxx",
        "cam-chat.com",
        "webcam.xxx"
    )

    fun getRootDomain(host: String): String {
        val parts = host.lowercase().split(".")
        if (parts.size <= 2) return host.lowercase()
        val last = parts.last()
        val secondLast = parts[parts.size - 2]
        val twoPartTLDs = setOf("co.uk", "com.br", "co.jp", "com.au", "co.nz")
        val combined = "$secondLast.$last"
        if (twoPartTLDs.contains(combined) && parts.size >= 3) {
            return "${parts[parts.size - 3]}.$combined"
        }
        return combined
    }

    fun isAdUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = try { Uri.parse(url) } catch (e: Exception) { return false }
        val host = uri.host?.lowercase() ?: return false

        for (domain in adDomains) {
            if (host == domain || host.endsWith(".$domain")) {
                return true
            }
        }

        val full = url.lowercase()
        return full.contains("/interstitial") ||
               full.contains("/popunder") ||
               full.contains("/pop_up") ||
               full.contains("/popup") ||
               full.contains("/click.php") ||
               full.contains("/delivery/afr.php") ||
               full.contains("/delivery/ajs.php") ||
               full.contains("ad_type=pop") ||
               full.contains("trafficjunky") ||
               full.contains("exoclick") ||
               full.contains("syndication") ||
               full.contains("ad_click") ||
               full.contains("adclick") ||
               full.contains("clicktag") ||
               full.contains("target=ad") ||
               full.contains("hasad=1") ||
               full.contains("has_ad=1") ||
               full.contains("mgp_click") ||
               full.contains("zoneid=") ||
               full.contains("zone_id=") ||
               (full.contains("/affiliate/") && !full.contains("pornhub.com"))
    }

    /**
     * Checks if a navigation from currentUrl to targetUrl is an ad or unwanted cross-domain popunder.
     * When browsing Pornhub, clicking a video should never redirect to an external 3rd party domain.
     */
    fun isCrossDomainAdRedirect(currentUrl: String?, targetUrl: String?): Boolean {
        if (targetUrl.isNullOrBlank()) return false
        if (isAdUrl(targetUrl)) return true
        if (currentUrl.isNullOrBlank()) return false

        val currentHost = try { Uri.parse(currentUrl).host?.lowercase() } catch (e: Exception) { null } ?: return false
        val targetHost = try { Uri.parse(targetUrl).host?.lowercase() } catch (e: Exception) { null } ?: return false

        val currentRoot = getRootDomain(currentHost)
        val targetRoot = getRootDomain(targetHost)

        if (currentRoot.isEmpty() || targetRoot.isEmpty()) return false
        if (currentRoot == targetRoot) return false

        // Known friendly domains for Pornhub
        if (currentRoot == "pornhub.com") {
            if (targetRoot == "phncdn.com" || targetRoot == "pornhubpremium.com") {
                return false
            }
            // Any other domain redirect when browsing Pornhub is an ad/interstitial
            return true
        }

        // Generic check: if destination URL has ad query parameters or typical popunder paths
        val targetLower = targetUrl.lowercase()
        if (targetLower.contains("campaign") || targetLower.contains("affiliate") || targetLower.contains("zoneid") || targetLower.contains("track")) {
            return true
        }

        return false
    }
}
