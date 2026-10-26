import Foundation
import UIKit
import Combine

@MainActor
public final class HistoryStore: ObservableObject {
    public static let shared = HistoryStore()

    @Published public var items: [HistoryItem] = []
    @Published public var isLoading: Bool = false

    private let storageKey = "path_hub_history_v1"
    private var lastRecordedUrl: String? = nil
    private var lastRecordedTime: Date = Date.distantPast
    private var cancellables = Set<AnyCancellable>()

    public static var currentDeviceName: String {
        let name = UIDevice.current.name
        if !name.isEmpty && name != "iPhone" && name != "iPad" {
            return name
        }
        return UIDevice.current.model
    }

    private init() {
        loadFromCache()
        Task {
            await fetchHistory()
        }

        NotificationCenter.default.publisher(for: NSNotification.Name("SupabasePlaylistItemsChanged"))
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                Task {
                    await self?.fetchHistory()
                }
            }
            .store(in: &cancellables)
    }

    private func loadFromCache() {
        guard let data = UserDefaults.standard.data(forKey: storageKey) else { return }
        if let decoded = try? JSONDecoder().decode([HistoryItem].self, from: data) {
            self.items = decoded
        }
    }

    private func saveToCache(_ itemsToSave: [HistoryItem]) {
        if let encoded = try? JSONEncoder().encode(itemsToSave) {
            UserDefaults.standard.set(encoded, forKey: storageKey)
        }
    }

    @MainActor
    public func fetchHistory() async {
        guard SupabaseConfig.isConfigured else { return }
        isLoading = true
        do {
            let remote = try await SupabaseClient.shared.fetchHistory()
            self.items = remote
            saveToCache(remote)
        } catch {
            print("[HistoryStore] Failed to fetch history: \(error.localizedDescription)")
        }
        isLoading = false
    }

    @MainActor
    public func recordVisit(url: String, title: String) {
        let clean = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty,
              !clean.starts(with: "about:"),
              !clean.starts(with: "chrome:"),
              !clean.starts(with: "javascript:") else {
            return
        }

        let now = Date()
        if clean == lastRecordedUrl && now.timeIntervalSince(lastRecordedTime) < 20 {
            return
        }
        lastRecordedUrl = clean
        lastRecordedTime = now

        let deviceName = Self.currentDeviceName
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? ((URL(string: clean)?.host) ?? clean) : title
        let localItem = HistoryItem(
            id: "local-\(UUID().uuidString)",
            url: clean,
            title: cleanTitle,
            deviceName: deviceName
        )

        self.items = [localItem] + self.items.filter { $0.id != localItem.id }
        saveToCache(self.items)

        Task {
            do {
                try await SupabaseClient.shared.insertHistoryItem(
                    url: clean,
                    title: cleanTitle,
                    deviceName: deviceName
                )
                await fetchHistory()
            } catch {
                print("[HistoryStore] Failed to record history remotely: \(error)")
            }
        }
    }

    @MainActor
    public func deleteItem(id: String) {
        self.items = self.items.filter { $0.id != id }
        saveToCache(self.items)

        Task {
            do {
                try await SupabaseClient.shared.deleteHistoryItem(id: id)
            } catch {
                print("[HistoryStore] Failed to delete history item: \(error)")
            }
        }
    }

    @MainActor
    public func clearHistory() {
        self.items = []
        saveToCache([])

        Task {
            do {
                try await SupabaseClient.shared.clearHistory()
            } catch {
                print("[HistoryStore] Failed to clear history: \(error)")
            }
        }
    }
}
