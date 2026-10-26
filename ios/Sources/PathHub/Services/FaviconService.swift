import Foundation

public struct SiteMetadataResult: Sendable {
    public let normalizedUrl: String
    public let title: String
    public let iconUrl: String
}

public enum SiteMetadataError: LocalizedError, Sendable {
    case invalidURL
    case networkError(String)

    public var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Please enter a valid web address (e.g., youtube.com or https://example.com)."
        case .networkError(let msg):
            return "Failed to load site details: \(msg)"
        }
    }
}

public final class FaviconService: Sendable {
    public static let shared = FaviconService()

    private init() {}

    /// Normalizes raw user input into a validated https:// URL
    public func normalizeUrl(_ input: String) throws -> URL {
        var trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            throw SiteMetadataError.invalidURL
        }

        if !trimmed.lowercased().hasPrefix("http://") && !trimmed.lowercased().hasPrefix("https://") {
            trimmed = "https://" + trimmed
        }

        guard let url = URL(string: trimmed), let host = url.host, host.contains(".") else {
            throw SiteMetadataError.invalidURL
        }

        return url
    }

    /// Fetches site title and favicon asynchronously
    public func fetchMetadata(for url: URL, customName: String? = nil) async -> SiteMetadataResult {
        let domain = (url.host ?? "").replacingOccurrences(of: "www.", with: "")
        let defaultIcon = "https://www.google.com/s2/favicons?domain=\(domain)&sz=128"

        var title = customName?.trimmingCharacters(in: .whitespacesAndNewlines)
        if title == nil || title!.isEmpty {
            title = domain.capitalized
        }

        // Attempt HTML extraction for clean <title> and favicon tags
        var request = URLRequest(url: url)
        request.timeoutInterval = 6
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15", forHTTPHeaderField: "User-Agent")

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let httpResp = response as? HTTPURLResponse, (200...299).contains(httpResp.statusCode),
               let html = String(data: data, encoding: .utf8) {

                if customName == nil || customName!.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    if let extractedTitle = extractTitle(from: html) {
                        title = extractedTitle
                    }
                }

                if let extractedIcon = extractFavicon(from: html, baseUrl: url) {
                    return SiteMetadataResult(normalizedUrl: url.absoluteString, title: title ?? domain, iconUrl: extractedIcon)
                }
            }
        } catch {
            // Silently fall back to Google favicon service
        }

        return SiteMetadataResult(normalizedUrl: url.absoluteString, title: title ?? domain, iconUrl: defaultIcon)
    }

    private func extractTitle(from html: String) -> String? {
        let titleRegex = try? NSRegularExpression(pattern: "<title[^>]*>([^<]+)</title>", options: .caseInsensitive)
        let range = NSRange(html.startIndex..<html.endIndex, in: html)
        if let match = titleRegex?.firstMatch(in: html, options: [], range: range),
           let titleRange = Range(match.range(at: 1), in: html) {
            let rawTitle = String(html[titleRange]).trimmingCharacters(in: .whitespacesAndNewlines)
            return rawTitle.replacingOccurrences(of: "\n", with: " ")
        }
        return nil
    }

    private func extractFavicon(from html: String, baseUrl: URL) -> String? {
        let iconRegex = try? NSRegularExpression(pattern: "<link[^>]+rel=[\"'](apple-touch-icon|shortcut icon|icon)[\"'][^>]+href=[\"']([^\"']+)[\"']", options: .caseInsensitive)
        let range = NSRange(html.startIndex..<html.endIndex, in: html)
        if let match = iconRegex?.firstMatch(in: html, options: [], range: range),
           let hrefRange = Range(match.range(at: 2), in: html) {
            let href = String(html[hrefRange])
            if let resolved = URL(string: href, relativeTo: baseUrl)?.absoluteString {
                return resolved
            }
        }
        return nil
    }
}
