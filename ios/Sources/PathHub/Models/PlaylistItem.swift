import Foundation

public struct PlaylistItem: Identifiable, Codable, Equatable, Hashable, Sendable {
    public let id: String
    public var videoUrl: String
    public var pageUrl: String
    public var title: String
    public var thumbnailUrl: String
    public var siteId: String?
    public var notes: String
    public var addedBy: String?
    public var seenBy: [String]
    public let createdAt: Date

    public init(
        id: String = UUID().uuidString,
        videoUrl: String,
        pageUrl: String,
        title: String,
        thumbnailUrl: String,
        siteId: String? = nil,
        notes: String = "",
        addedBy: String? = nil,
        seenBy: [String] = [],
        createdAt: Date = Date()
    ) {
        self.id = id
        self.videoUrl = videoUrl
        self.pageUrl = pageUrl
        self.title = title
        self.thumbnailUrl = thumbnailUrl
        self.siteId = siteId
        self.notes = notes
        self.addedBy = addedBy
        self.seenBy = seenBy
        self.createdAt = createdAt
    }

    public var cleanPageUrl: String {
        NoteSync.cleanPageUrl(pageUrl)
    }

    public var isReel: Bool {
        let lower = (cleanPageUrl + " " + videoUrl + " " + title).lowercased()
        return lower.contains("/shorties/") || lower.contains("/shorts/") || lower.contains("/reel") || lower.contains("shorties")
    }

    public var hostDomain: String {
        guard let parsed = URL(string: cleanPageUrl.isEmpty ? pageUrl : cleanPageUrl), let host = parsed.host else {
            return "Web Video"
        }
        return host.replacingOccurrences(of: "www.", with: "")
    }

    public var cleanThumbnailUrl: String {
        let trimmed = thumbnailUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed.contains("/view_video.php") || trimmed.contains("/shorties/") ||
           trimmed.contains("logo") || trimmed.contains(".svg") ||
           (!trimmed.hasPrefix("data:image") && !trimmed.contains(".jpg") && !trimmed.contains(".jpeg") &&
            !trimmed.contains(".png") && !trimmed.contains(".webp") && !trimmed.contains(".gif") &&
            !trimmed.contains("phncdn") && !trimmed.contains("image") && !trimmed.contains("pics") &&
            !trimmed.contains("thumb") && !trimmed.contains("original") && !trimmed.contains("vts:")) {
            if isReel {
                return "https://di.phncdn.com/pics/categories/(m=e-SaK8daaaa)(mh=lq2c-4G)32.jpg"
            }
            return "https://di.phncdn.com/pics/categories/(m=e-SaK8daaaa)(mh=lq2c-4G)105.jpg"
        }
        return trimmed
    }
}
