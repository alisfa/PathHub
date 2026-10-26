import Foundation

public final class SupabaseClient: Sendable {
    public static let shared = SupabaseClient()
    public static let globalPlaylistId = "00000000-0000-0000-0000-000000000001"

    private init() {}

    private func makeRequest(endpoint: String, method: String = "GET", query: [String: String] = [:]) async throws -> URLRequest {
        var components = URLComponents(string: "\(SupabaseConfig.url)/rest/v1/\(endpoint)")!
        if !query.isEmpty {
            components.queryItems = query.map { URLQueryItem(name: $0.key, value: $0.value) }
        }

        var request = URLRequest(url: components.url!)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(SupabaseConfig.anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(SupabaseConfig.anonKey)", forHTTPHeaderField: "Authorization")
        request.setValue("return=representation", forHTTPHeaderField: "Prefer")

        return request
    }

    // MARK: - Sites API (Shared Globally)

    public func fetchSites() async throws -> [Site] {
        guard SupabaseConfig.isConfigured else { return [] }

        let request = try await makeRequest(endpoint: "sites", query: ["select": "*", "order": "sort_order.asc"])
        let (data, response) = try await URLSession.shared.data(for: request)

        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            return []
        }

        struct DTO: Decodable {
            let id: String?
            let name: String?
            let url: String?
            let icon_url: String?
            let sort_order: Int?
        }

        let decoder = JSONDecoder()
        let dtos = try decoder.decode([DTO].self, from: data)
        return dtos.compactMap { dto in
            guard let url = dto.url, !url.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
            return Site(
                id: dto.id ?? UUID().uuidString,
                name: dto.name ?? url,
                url: url,
                iconUrl: dto.icon_url ?? "",
                sortOrder: dto.sort_order ?? 0
            )
        }
    }

    public func insertSite(site: Site) async throws {
        guard SupabaseConfig.isConfigured else { return }

        var request = try await makeRequest(endpoint: "sites", method: "POST")
        let payload: [String: Any] = [
            "id": site.id,
            "name": site.name,
            "url": site.url,
            "icon_url": site.iconUrl,
            "sort_order": site.sortOrder
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        _ = try await URLSession.shared.data(for: request)
    }

    public func updateSite(site: Site) async throws {
        guard SupabaseConfig.isConfigured else { return }

        var request = try await makeRequest(endpoint: "sites", method: "PATCH", query: ["id": "eq.\(site.id)"])
        let payload: [String: Any] = [
            "name": site.name,
            "url": site.url,
            "icon_url": site.iconUrl,
            "sort_order": site.sortOrder
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        _ = try await URLSession.shared.data(for: request)
    }

    public func deleteSite(id: String) async throws {
        guard SupabaseConfig.isConfigured else { return }

        let request = try await makeRequest(endpoint: "sites", method: "DELETE", query: ["id": "eq.\(id)"])
        _ = try await URLSession.shared.data(for: request)
    }

    // MARK: - Playlist Items API (Shared Globally across all users)

    public func fetchPlaylistItems(playlistId: String = globalPlaylistId) async throws -> [PlaylistItem] {
        guard SupabaseConfig.isConfigured else { return [] }

        let request = try await makeRequest(
            endpoint: "playlist_items",
            query: ["playlist_id": "eq.\(playlistId)", "select": "*", "order": "created_at.desc"]
        )
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            return []
        }

        struct ItemDTO: Decodable {
            let id: String
            let video_url: String
            let page_url: String
            let title: String
            let thumbnail_url: String?
            let site_id: String?
            let notes: String?
            let created_at: String?
        }

        let dtos = try JSONDecoder().decode([ItemDTO].self, from: data)
        return dtos.map { dto in
            let date: Date
            if let str = dto.created_at {
                let formatter = ISO8601DateFormatter()
                formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
                if let d = formatter.date(from: str) {
                    date = d
                } else {
                    formatter.formatOptions = [.withInternetDateTime]
                    date = formatter.date(from: str) ?? Date()
                }
            } else {
                date = Date()
            }
            let meta = NoteSync.extractMetadata(pageUrl: dto.page_url, columnNote: dto.notes)
            return PlaylistItem(
                id: dto.id,
                videoUrl: dto.video_url,
                pageUrl: dto.page_url,
                title: dto.title,
                thumbnailUrl: dto.thumbnail_url ?? "",
                siteId: dto.site_id,
                notes: meta.note,
                addedBy: meta.addedBy,
                seenBy: meta.seenBy,
                createdAt: date
            )
        }
    }

    public func insertPlaylistItem(item: PlaylistItem, playlistId: String = globalPlaylistId) async throws {
        guard SupabaseConfig.isConfigured else { return }

        var request = try await makeRequest(endpoint: "playlist_items", method: "POST")
        let meta = NoteSync.extractMetadata(pageUrl: item.pageUrl, columnNote: item.notes)
        let finalPageUrl = NoteSync.buildPageUrl(
            cleanUrl: item.pageUrl,
            note: meta.note.isEmpty ? item.notes : meta.note,
            addedBy: item.addedBy ?? meta.addedBy,
            seenBy: item.seenBy.isEmpty ? meta.seenBy : item.seenBy
        )

        var payload: [String: Any] = [
            "id": item.id,
            "playlist_id": playlistId,
            "video_url": item.videoUrl,
            "page_url": finalPageUrl,
            "title": item.title,
            "thumbnail_url": item.thumbnailUrl
        ]
        if let sid = item.siteId, !sid.isEmpty {
            payload["site_id"] = sid
        }

        request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        _ = try await URLSession.shared.data(for: request)
    }

    public func updatePlaylistItemNote(id: String, pageUrl: String, notes: String) async throws {
        guard SupabaseConfig.isConfigured else { return }

        var request = try await makeRequest(endpoint: "playlist_items", method: "PATCH", query: ["id": "eq.\(id)"])
        let payload: [String: Any] = ["page_url": pageUrl]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        let (data, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            let body = String(data: data, encoding: .utf8) ?? ""
            print("[Supabase] Failed to update note (\(http.statusCode)): \(body)")
        }
    }

    public func deletePlaylistItem(id: String) async throws {
        guard SupabaseConfig.isConfigured else { return }

        let request = try await makeRequest(endpoint: "playlist_items", method: "DELETE", query: ["id": "eq.\(id)"])
        _ = try await URLSession.shared.data(for: request)
    }

    public static let balabooPlaylistId = "00000000-0000-0000-0000-000000000099"

    // MARK: - Balaboo History API
    public func fetchHistory() async throws -> [HistoryItem] {
        guard SupabaseConfig.isConfigured else { return [] }

        let request = try await makeRequest(
            endpoint: "playlist_items",
            query: [
                "playlist_id": "eq.\(Self.balabooPlaylistId)",
                "select": "*",
                "order": "created_at.desc",
                "limit": "250"
            ]
        )
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            return []
        }

        struct HistoryDTO: Decodable {
            let id: String
            let page_url: String
            let title: String
            let thumbnail_url: String?
            let created_at: String?
        }

        let dtos = try JSONDecoder().decode([HistoryDTO].self, from: data)
        return dtos.map { dto in
            HistoryItem(
                id: dto.id,
                url: dto.page_url,
                title: dto.title,
                deviceName: (dto.thumbnail_url?.isEmpty == false) ? dto.thumbnail_url! : "Unknown Device",
                visitedAt: dto.created_at ?? "",
                createdAt: dto.created_at ?? ""
            )
        }
    }

    public func insertHistoryItem(url: String, title: String, deviceName: String) async throws {
        guard SupabaseConfig.isConfigured else { return }

        var request = try await makeRequest(endpoint: "playlist_items", method: "POST")
        let payload: [String: Any] = [
            "id": UUID().uuidString.lowercased(),
            "playlist_id": Self.balabooPlaylistId,
            "video_url": "urn:balaboo:hist:\(UUID().uuidString.lowercased())",
            "page_url": url,
            "title": title.isEmpty ? ((URL(string: url)?.host) ?? url) : title,
            "thumbnail_url": deviceName
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        _ = try await URLSession.shared.data(for: request)
    }

    public func deleteHistoryItem(id: String) async throws {
        try await deletePlaylistItem(id: id)
    }

    public func clearHistory() async throws {
        guard SupabaseConfig.isConfigured else { return }
        let request = try await makeRequest(
            endpoint: "playlist_items",
            method: "DELETE",
            query: ["playlist_id": "eq.\(Self.balabooPlaylistId)"]
        )
        _ = try await URLSession.shared.data(for: request)
    }
}
