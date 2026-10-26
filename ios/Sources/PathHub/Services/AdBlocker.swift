import Foundation
import WebKit

public enum AdBlocker {
    private static let adDomains: Set<String> = [
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
    ]

    public static func getRootDomain(for host: String) -> String {
        let parts = host.lowercased().components(separatedBy: ".")
        guard parts.count > 2 else { return host.lowercased() }
        let last = parts[parts.count - 1]
        let secondLast = parts[parts.count - 2]
        let combined = "\(secondLast).\(last)"
        let twoPartTLDs: Set<String> = ["co.uk", "com.br", "co.jp", "com.au", "co.nz"]
        if twoPartTLDs.contains(combined) && parts.count >= 3 {
            return "\(parts[parts.count - 3]).\(combined)"
        }
        return combined
    }

    public static func isAdUrl(_ url: URL?) -> Bool {
        guard let url = url, let host = url.host?.lowercased() else { return false }

        // 1. Direct host match or subdomain
        for domain in adDomains {
            if host == domain || host.hasSuffix("." + domain) {
                return true
            }
        }

        // 2. Suspicious popup redirect query patterns
        let fullString = url.absoluteString.lowercased()
        if fullString.contains("/interstitial") ||
           fullString.contains("/popunder") ||
           fullString.contains("/pop_up") ||
           fullString.contains("/popup") ||
           fullString.contains("/click.php") ||
           fullString.contains("/delivery/afr.php") ||
           fullString.contains("/delivery/ajs.php") ||
           fullString.contains("ad_type=pop") ||
           fullString.contains("trafficjunky") ||
           fullString.contains("exoclick") ||
           fullString.contains("syndication") ||
           fullString.contains("ad_click") ||
           fullString.contains("adclick") ||
           fullString.contains("clicktag") ||
           fullString.contains("target=ad") ||
           fullString.contains("hasad=1") ||
           fullString.contains("has_ad=1") ||
           fullString.contains("mgp_click") ||
           fullString.contains("zoneid=") ||
           fullString.contains("zone_id=") ||
           (fullString.contains("/affiliate/") && !fullString.contains("pornhub.com")) {
            return true
        }

        return false
    }

    /**
     * Checks if a navigation from currentUrl to targetUrl is a rogue cross-domain redirect / popunder.
     * When browsing Pornhub, clicking a video should never redirect to an external 3rd party domain.
     */
    public static func isCrossDomainAdRedirect(currentUrl: URL?, targetUrl: URL?) -> Bool {
        guard let target = targetUrl else { return false }
        if isAdUrl(target) { return true }
        guard let current = currentUrl,
              let currentHost = current.host?.lowercased(),
              let targetHost = target.host?.lowercased() else {
            return false
        }

        let currentRoot = getRootDomain(for: currentHost)
        let targetRoot = getRootDomain(for: targetHost)

        if currentRoot.isEmpty || targetRoot.isEmpty { return false }
        if currentRoot == targetRoot { return false }

        // Known friendly domains for Pornhub
        if currentRoot == "pornhub.com" {
            if targetRoot == "phncdn.com" || targetRoot == "pornhubpremium.com" {
                return false
            }
            // Any other domain redirect when browsing Pornhub is an ad/interstitial
            return true
        }

        let targetLower = target.absoluteString.lowercased()
        if targetLower.contains("campaign") || targetLower.contains("affiliate") || targetLower.contains("zoneid") || targetLower.contains("track") {
            return true
        }

        return false
    }
}
