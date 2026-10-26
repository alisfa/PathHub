import Foundation

public struct Site: Identifiable, Codable, Equatable, Hashable, Sendable {
    public let id: String
    public var name: String
    public var url: String
    public var iconUrl: String
    public var sortOrder: Int
    public let createdAt: Date

    public init(
        id: String = UUID().uuidString,
        name: String,
        url: String,
        iconUrl: String = "",
        sortOrder: Int = 0,
        createdAt: Date = Date()
    ) {
        self.id = id
        self.name = name
        self.url = url
        self.iconUrl = iconUrl
        self.sortOrder = sortOrder
        self.createdAt = createdAt
    }

    public var hostDomain: String {
        guard let parsed = URL(string: url), let host = parsed.host else {
            return url
        }
        return host.replacingOccurrences(of: "www.", with: "")
    }
}
