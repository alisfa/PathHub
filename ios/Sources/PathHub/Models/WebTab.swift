import Foundation

public struct WebTab: Identifiable, Codable, Equatable, Hashable, Sendable {
    public let id: String
    public var url: String
    public var title: String
    public var siteId: String?
    public var createdAt: Date
    public var lastActiveAt: Date

    public init(
        id: String = UUID().uuidString,
        url: String,
        title: String = "New Tab",
        siteId: String? = nil,
        createdAt: Date = Date(),
        lastActiveAt: Date = Date()
    ) {
        self.id = id
        self.url = url
        self.title = title
        self.siteId = siteId
        self.createdAt = createdAt
        self.lastActiveAt = lastActiveAt
    }

    public var hostDomain: String {
        guard let parsed = URL(string: url), let host = parsed.host else {
            return "Web"
        }
        return host.replacingOccurrences(of: "www.", with: "")
    }
}
