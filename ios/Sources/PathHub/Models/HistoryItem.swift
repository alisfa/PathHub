import Foundation

public struct HistoryItem: Identifiable, Codable, Equatable, Sendable {
    public let id: String
    public let url: String
    public let title: String
    public let deviceName: String
    public let visitedAt: String
    public let createdAt: String

    public init(
        id: String = UUID().uuidString,
        url: String,
        title: String,
        deviceName: String,
        visitedAt: String = ISO8601DateFormatter().string(from: Date()),
        createdAt: String = ISO8601DateFormatter().string(from: Date())
    ) {
        self.id = id
        self.url = url
        self.title = title
        self.deviceName = deviceName
        self.visitedAt = visitedAt
        self.createdAt = createdAt
    }

    enum CodingKeys: String, CodingKey {
        case id
        case url
        case title
        case deviceName = "device_name"
        case visitedAt = "visited_at"
        case createdAt = "created_at"
    }
}
