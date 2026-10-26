import Foundation
import Combine
import UIKit

/// Cloudflare 1.1.1.1 Anycast Device VPN & Location Bypass Manager for iOS.
/// Detects device-wide VPN protection, launches Cloudflare 1.1.1.1 (WARP),
/// and provides 1-tap mobileconfig DNS profile install for system-wide bypass with 0 speed loss.
public final class VpnManager: ObservableObject, @unchecked Sendable {
    public static let shared = VpnManager()

    private let userDefaultsKey = "pathhub_vpn_enabled"
    public static let vpnGatewayUrl = "https://pathhub-vpn.malekkaraso.workers.dev"

    public static let cloudflare1111Scheme = "one.one.one.one://"
    public static let cloudflareAppStoreUrl = "https://apps.apple.com/app/1-1-1-1-faster-internet/id1423538627"
    public static let cloudflareProfileUrl = "https://1.1.1.1/1.1.1.1.mobileconfig"

    @Published public var isVpnEnabled: Bool {
        didSet {
            UserDefaults.standard.set(isVpnEnabled, forKey: userDefaultsKey)
        }
    }

    private init() {
        if UserDefaults.standard.object(forKey: userDefaultsKey) == nil {
            self.isVpnEnabled = true // Enabled by default
        } else {
            self.isVpnEnabled = UserDefaults.standard.bool(forKey: userDefaultsKey)
        }
    }

    /// Checks if ANY system-wide VPN is currently connected on the iOS device (tun, ppp, ipsec).
    public func isSystemVpnActive() -> Bool {
        guard let cfDict = CFNetworkCopySystemProxySettings()?.takeRetainedValue() as? [String: Any],
              let scopes = cfDict["__SCOPED__"] as? [String: Any] else {
            return false
        }
        for key in scopes.keys {
            let lower = key.lowercased()
            if lower.contains("tap") || lower.contains("tun") || lower.contains("ppp") || lower.contains("ipsec") {
                return true
            }
        }
        return false
    }

    /// Checks if the Cloudflare 1.1.1.1 app is installed on this iOS device.
    @MainActor
    public func is1111Installed() -> Bool {
        guard let url = URL(string: Self.cloudflare1111Scheme) else { return false }
        return UIApplication.shared.canOpenURL(url)
    }

    /// Launches Cloudflare 1.1.1.1 to connect, or opens the App Store page.
    @MainActor
    public func launch1111App() {
        if let url = URL(string: Self.cloudflare1111Scheme), UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
        } else if let appStore = URL(string: Self.cloudflareAppStoreUrl) {
            UIApplication.shared.open(appStore)
        }
    }

    /// Opens Cloudflare's official iOS 1.1.1.1 Encrypted DNS configuration profile.
    @MainActor
    public func open1111ProfileInstall() {
        if let url = URL(string: Self.cloudflareProfileUrl) {
            UIApplication.shared.open(url)
        }
    }

    /// Clean URL resolver.
    /// With device-wide VPN (Cloudflare 1.1.1.1) active, direct clean URLs load natively
    /// without proxy wrapper interference or video player breakages.
    public func resolveUrl(_ targetUrl: String) -> String {
        let clean = targetUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.isEmpty { return clean }

        // If the URL was tunneled with an older proxy link, restore it to the authentic clean URL
        if isTunneled(clean) {
            return getDisplayUrl(clean)
        }

        return clean
    }

    /// Checks if a URL is currently tunneled through the legacy VPN gateway.
    public func isTunneled(_ url: String) -> Bool {
        return url.hasPrefix(Self.vpnGatewayUrl)
    }

    /// Extracts clean target URL for display in UI address bars.
    public func getDisplayUrl(_ url: String) -> String {
        if isTunneled(url), let range = url.range(of: "?url=") {
            let encoded = String(url[range.upperBound...])
            return encoded.removingPercentEncoding ?? encoded
        }
        return url
    }
}
