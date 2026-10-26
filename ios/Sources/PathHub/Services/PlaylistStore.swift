import Foundation
import Combine

@MainActor
public final class PlaylistStore: ObservableObject {
    @Published public var items: [PlaylistItem] = []
    @Published public var currentPlaylistId: String = SupabaseClient.globalPlaylistId
    @Published public var currentPlaylistName: String = "Saved Videos"
    @Published public var currentInviteCode: String = "HUB001"
    @Published public var isSyncing: Bool = false

    private let storageKey = "path_hub_playlist_items_v1"

    public init() {
        loadLocalCache()
        setupRealtimeSubscription()
        syncWithRemote()
    }

    private func getFileURL() -> URL {
        let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        return paths[0].appendingPathComponent("playlist.json")
    }

    private func loadLocalCache() {
        let fileURL = getFileURL()
        if let data = try? Data(contentsOf: fileURL),
           let decoded = try? JSONDecoder().decode([PlaylistItem].self, from: data) {
            self.items = decoded.sorted(by: { $0.createdAt > $1.createdAt })
        } else if let data = UserDefaults.standard.data(forKey: storageKey),
                  let decoded = try? JSONDecoder().decode([PlaylistItem].self, from: data) {
            self.items = decoded.sorted(by: { $0.createdAt > $1.createdAt })
        }
    }

    public func saveLocalCache() {
        if let encoded = try? JSONEncoder().encode(items) {
            try? encoded.write(to: getFileURL(), options: .atomic)
            UserDefaults.standard.set(encoded, forKey: storageKey)
        }
    }

    // MARK: - Remote & Realtime Sync (Shared across all users)

    public func syncWithRemote() {
        guard SupabaseConfig.isConfigured else { return }
        isSyncing = true

        Task {
            do {
                let remoteItems = try await SupabaseClient.shared.fetchPlaylistItems()
                await MainActor.run {
                    if !remoteItems.isEmpty {
                        self.items = remoteItems
                        self.saveLocalCache()
                    }
                    self.isSyncing = false
                }
            } catch {
                await MainActor.run {
                    self.isSyncing = false
                }
            }
        }
    }

    private func setupRealtimeSubscription() {
        SupabaseRealtime.shared.connect()
        SupabaseRealtime.shared.onPlaylistItemsChange = { [weak self] event in
            guard let self = self else { return }

            switch event.action {
            case .insert:
                if let id = event.record["id"] as? String,
                   let videoUrl = event.record["video_url"] as? String,
                   let pageUrl = event.record["page_url"] as? String,
                   let title = event.record["title"] as? String {
                    if !self.items.contains(where: { $0.id == id || $0.videoUrl == videoUrl }) {
                        let dateString = event.record["created_at"] as? String
                        let date: Date
                        if let ds = dateString {
                            let fmt = ISO8601DateFormatter()
                            fmt.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
                            date = fmt.date(from: ds) ?? ISO8601DateFormatter().date(from: ds) ?? Date()
                        } else {
                            date = Date()
                        }
                        let rawNote = event.record["notes"] as? String
                        let meta = NoteSync.extractMetadata(pageUrl: pageUrl, columnNote: rawNote)
                        let newItem = PlaylistItem(
                            id: id,
                            videoUrl: videoUrl,
                            pageUrl: pageUrl,
                            title: title,
                            thumbnailUrl: event.record["thumbnail_url"] as? String ?? "",
                            siteId: event.record["site_id"] as? String,
                            notes: meta.note,
                            addedBy: meta.addedBy,
                            seenBy: meta.seenBy,
                            createdAt: date
                        )
                        self.items.insert(newItem, at: 0)
                        self.saveLocalCache()
                    }
                }

            case .update:
                if let id = event.record["id"] as? String,
                   let index = self.items.firstIndex(where: { $0.id == id }) {
                    var updated = self.items[index]
                    if let t = event.record["title"] as? String { updated.title = t }
                    if let u = event.record["video_url"] as? String { updated.videoUrl = u }
                    if let p = event.record["page_url"] as? String {
                        updated.pageUrl = p
                        let rawNote = event.record["notes"] as? String
                        let meta = NoteSync.extractMetadata(pageUrl: p, columnNote: rawNote)
                        updated.notes = meta.note
                        updated.addedBy = meta.addedBy ?? updated.addedBy
                        updated.seenBy = meta.seenBy
                    } else if let n = event.record["notes"] as? String {
                        updated.notes = n
                    }
                    if let th = event.record["thumbnail_url"] as? String { updated.thumbnailUrl = th }
                    self.items[index] = updated
                    self.saveLocalCache()
                }

            case .delete:
                if let id = event.oldRecord?["id"] as? String ?? event.record["id"] as? String {
                    self.items.removeAll(where: { $0.id == id })
                    self.saveLocalCache()
                }
            }
        }
    }

    public func isSaved(videoUrl: String) -> Bool {
        return items.contains(where: { $0.videoUrl == videoUrl || $0.pageUrl == videoUrl })
    }

    public func saveVideo(from dict: [String: Any]) {
        guard let videoUrl = dict["videoUrl"] as? String,
              let pageUrl = dict["pageUrl"] as? String,
              let title = dict["title"] as? String else { return }

        if isSaved(videoUrl: videoUrl) { return }

        let currentUserId = AuthService.shared.userId
        let initialPageUrl = NoteSync.buildPageUrl(
            cleanUrl: pageUrl,
            note: "",
            addedBy: currentUserId,
            seenBy: []
        )

        let item = PlaylistItem(
            id: UUID().uuidString.lowercased(),
            videoUrl: videoUrl,
            pageUrl: initialPageUrl,
            title: title.isEmpty ? "Saved Video" : title,
            thumbnailUrl: dict["thumbnailUrl"] as? String ?? "",
            siteId: dict["siteId"] as? String,
            notes: "",
            addedBy: currentUserId,
            seenBy: []
        )

        items.insert(item, at: 0)
        saveLocalCache()

        Task {
            try? await SupabaseClient.shared.insertPlaylistItem(item: item)
        }
    }

    public func deleteItem(_ item: PlaylistItem) {
        items.removeAll(where: { $0.id == item.id })
        saveLocalCache()

        Task {
            try? await SupabaseClient.shared.deletePlaylistItem(id: item.id)
        }
    }

    public func updateItemNotes(id: String, notes: String) {
        guard let index = items.firstIndex(where: { $0.id == id }) else { return }
        let cleanUrl = NoteSync.cleanPageUrl(items[index].pageUrl)
        let newPageUrl = NoteSync.buildPageUrl(
            cleanUrl: cleanUrl,
            note: notes,
            addedBy: items[index].addedBy,
            seenBy: items[index].seenBy
        )
        items[index].notes = notes
        items[index].pageUrl = newPageUrl
        saveLocalCache()

        Task {
            do {
                try await SupabaseClient.shared.updatePlaylistItemNote(id: id, pageUrl: newPageUrl, notes: notes)
            } catch {
                print("[PlaylistStore] Error saving note to remote: \(error)")
            }
        }
    }

    public func markItemSeen(id: String) {
        guard let index = items.firstIndex(where: { $0.id == id }) else { return }
        let currentUserId = AuthService.shared.userId
        if items[index].seenBy.contains(currentUserId) { return }

        var updatedSeen = items[index].seenBy
        updatedSeen.append(currentUserId)
        let cleanUrl = NoteSync.cleanPageUrl(items[index].pageUrl)
        let newPageUrl = NoteSync.buildPageUrl(
            cleanUrl: cleanUrl,
            note: items[index].notes,
            addedBy: items[index].addedBy,
            seenBy: updatedSeen
        )
        items[index].seenBy = updatedSeen
        items[index].pageUrl = newPageUrl
        saveLocalCache()

        Task {
            try? await SupabaseClient.shared.updatePlaylistItemNote(id: id, pageUrl: newPageUrl, notes: items[index].notes)
        }
    }

    public func joinPlaylist(code: String) async throws {
        // All users now share the global community playlist
        syncWithRemote()
    }

    public func joinByCode(_ code: String) async -> Bool {
        do {
            try await joinPlaylist(code: code)
            return true
        } catch {
            return false
        }
    }
}
