import Foundation

/// High-performance, zero-speed-loss DNS-over-HTTPS (DoH) resolver for iOS.
/// Bypasses ISP DNS tampering and poisoning in Iraq (Earthlink, Zain, Asiacell)
/// by directly querying Cloudflare (1.1.1.1) and Google (8.8.8.8) Anycast resolvers.
public actor DnsOverHttpsResolver {
    public static let shared = DnsOverHttpsResolver()

    private static let cloudflareIPs = ["1.1.1.1", "1.0.0.1"]
    private static let googleIPs = ["8.8.8.8", "8.8.4.4"]
    private static let wgczAnycastIP = "89.222.127.15"
    private static let pornhubAnycastIP = "66.254.114.41"

    private struct CachedRecord {
        let ip: String
        let expiresAt: Date
    }

    private var cache: [String: CachedRecord] = [:]

    private init() {}

    /// Resolves hostname to an IPv4 address using DoH, with in-memory caching.
    public func resolve(hostname: String) async -> String? {
        if hostname.isEmpty { return nil }
        if isIPv4(hostname) { return hostname }

        let lower = hostname.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        if lower == "xnxx.com" || lower.hasSuffix(".xnxx.com") || lower == "xvideos.com" || lower.hasSuffix(".xvideos.com") {
            if !lower.contains("-cdn.") {
                return Self.wgczAnycastIP
            }
        }
        if lower == "pornhub.com" || lower.hasSuffix(".pornhub.com") {
            if !lower.contains("phncdn") {
                return Self.pornhubAnycastIP
            }
        }

        if let record = cache[hostname], record.expiresAt > Date() {
            return record.ip
        }

        // 1. Try Cloudflare Anycast DoH
        if let ip = await queryCloudflare(hostname: hostname) {
            cache[hostname] = CachedRecord(ip: ip, expiresAt: Date().addingTimeInterval(15 * 60))
            return ip
        }

        // 2. Try Google Anycast DoH
        if let ip = await queryGoogle(hostname: hostname) {
            cache[hostname] = CachedRecord(ip: ip, expiresAt: Date().addingTimeInterval(15 * 60))
            return ip
        }

        return nil
    }

    private func queryCloudflare(hostname: String) async -> String? {
        for ip in Self.cloudflareIPs {
            guard let url = URL(string: "https://\(ip)/dns-query?name=\(hostname)&type=A") else { continue }
            var request = URLRequest(url: url)
            request.setValue("application/dns-json", forHTTPHeaderField: "Accept")
            request.setValue("cloudflare-dns.com", forHTTPHeaderField: "Host")
            request.timeoutInterval = 4

            do {
                let (data, response) = try await URLSession.shared.data(for: request)
                if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                    if let resolved = parseDnsJson(data) {
                        return resolved
                    }
                }
            } catch {
                // Continue to next IP
            }
        }
        return nil
    }

    private func queryGoogle(hostname: String) async -> String? {
        for ip in Self.googleIPs {
            guard let url = URL(string: "https://\(ip)/resolve?name=\(hostname)&type=A") else { continue }
            var request = URLRequest(url: url)
            request.setValue("application/json", forHTTPHeaderField: "Accept")
            request.setValue("dns.google", forHTTPHeaderField: "Host")
            request.timeoutInterval = 4

            do {
                let (data, response) = try await URLSession.shared.data(for: request)
                if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                    if let resolved = parseDnsJson(data) {
                        return resolved
                    }
                }
            } catch {
                // Continue to next IP
            }
        }
        return nil
    }

    private func parseDnsJson(_ data: Data) -> String? {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let answers = json["Answer"] as? [[String: Any]] else {
            return nil
        }
        for item in answers {
            if let type = item["type"] as? Int, type == 1, let ip = item["data"] as? String {
                if isIPv4(ip) {
                    return ip
                }
            }
        }
        return nil
    }

    private func isIPv4(_ string: String) -> Bool {
        let parts = string.split(separator: ".")
        guard parts.count == 4 else { return false }
        for part in parts {
            guard let num = Int(part), num >= 0 && num <= 255 else { return false }
        }
        return true
    }
}
