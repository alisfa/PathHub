import Foundation
import Combine

@MainActor
public final class SiteStore: ObservableObject {
    @Published public var sites: [Site] = []
    @Published public var recentlyDeletedSite: (site: Site, index: Int)? = nil
    @Published public var showUndoBanner: Bool = false
    @Published public var isSyncing: Bool = false

    private var undoTimer: AnyCancellable?
    private let storageKey = "path_hub_sites_v1"

    public init() {
        loadLocalCache()
        if sites.isEmpty {
            seedDefaultSites()
        }

        setupRealtimeSubscription()
        syncWithRemote()
    }

    private func getFileURL() -> URL {
        let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        return paths[0].appendingPathComponent("sites.json")
    }

    private func loadLocalCache() {
        let fileURL = getFileURL()
        if let data = try? Data(contentsOf: fileURL),
           let decoded = try? JSONDecoder().decode([Site].self, from: data) {
            self.sites = decoded.filter { !self.isDisallowed($0) }.sorted(by: { $0.sortOrder < $1.sortOrder })
        } else if let data = UserDefaults.standard.data(forKey: storageKey),
                  let decoded = try? JSONDecoder().decode([Site].self, from: data) {
            self.sites = decoded.filter { !self.isDisallowed($0) }.sorted(by: { $0.sortOrder < $1.sortOrder })
        }
    }

    public func saveLocalCache() {
        sites.sort(by: { $0.sortOrder < $1.sortOrder })
        if let encoded = try? JSONEncoder().encode(sites) {
            try? encoded.write(to: getFileURL(), options: .atomic)
            UserDefaults.standard.set(encoded, forKey: storageKey)
        }
    }

    private func isDisallowed(_ site: Site) -> Bool {
        let u = site.url.lowercased()
        let n = site.name.lowercased()
        return u.contains("pathhub.internal") ||
               u.contains("commondatastorage.googleapis.com") ||
               u.contains("archive.org") ||
               u.contains("w3schools.com") ||
               n.contains("big buck bunny") ||
               n.contains("internet archive") ||
               n.contains("html5 video") ||
               n.contains("version_code")
    }

    private func seedDefaultSites() {
        let defaults: [Site] = [
            Site(
                name: "Pornhub",
                url: "https://www.pornhub.com",
                iconUrl: "https://www.google.com/s2/favicons?domain=pornhub.com&sz=128",
                sortOrder: 0
            ),
            Site(
                name: "XNXX",
                url: "https://www.xnxx.com",
                iconUrl: "https://www.google.com/s2/favicons?domain=xnxx.com&sz=128",
                sortOrder: 1
            ),
            Site(
                name: "XVideos",
                url: "https://www.xvideos.com",
                iconUrl: "https://www.google.com/s2/favicons?domain=xvideos.com&sz=128",
                sortOrder: 2
            )
        ]
        self.sites = defaults
        saveLocalCache()
    }

    // MARK: - Remote & Realtime Sync

    public func syncWithRemote() {
        guard SupabaseConfig.isConfigured else { return }
        isSyncing = true

        Task {
            do {
                let fetched = try await SupabaseClient.shared.fetchSites()
                let remoteSites = fetched.filter { !self.isDisallowed($0) }
                await MainActor.run {
                    if !remoteSites.isEmpty {
                        // Smart Reconcile: Preserve any local custom sites that haven't synced to remote yet
                        let remoteUrls = Set(remoteSites.map { $0.url.lowercased().trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/")) })
                        let unsyncedLocal = self.sites.filter { local in
                            let norm = local.url.lowercased().trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
                            return !remoteUrls.contains(norm)
                        }

                        let combined = (remoteSites + unsyncedLocal).sorted(by: { $0.sortOrder < $1.sortOrder })
                        self.sites = combined
                        self.saveLocalCache()

                        // Push any previously unsynced local sites up to Supabase
                        if !unsyncedLocal.isEmpty {
                            Task {
                                for local in unsyncedLocal {
                                    try? await SupabaseClient.shared.insertSite(site: local)
                                }
                            }
                        }
                    } else if !self.sites.isEmpty {
                        // Push initial local seed to remote
                        Task {
                            for s in self.sites {
                                try? await SupabaseClient.shared.insertSite(site: s)
                            }
                        }
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
        SupabaseRealtime.shared.onSitesChange = { [weak self] event in
            guard let self = self else { return }

            switch event.action {
            case .insert:
                if let id = event.record["id"] as? String,
                   let name = event.record["name"] as? String,
                   let url = event.record["url"] as? String {
                    let normUrl = url.lowercased().trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
                    if !self.sites.contains(where: {
                        $0.id == id || $0.url.lowercased().trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/")) == normUrl
                    }) {
                        let newSite = Site(
                            id: id,
                            name: name,
                            url: url,
                            iconUrl: event.record["icon_url"] as? String ?? "",
                            sortOrder: event.record["sort_order"] as? Int ?? self.sites.count
                        )
                        self.sites.append(newSite)
                        self.sites.sort(by: { $0.sortOrder < $1.sortOrder })
                        self.saveLocalCache()
                    }
                }

            case .update:
                if let id = event.record["id"] as? String,
                   let index = self.sites.firstIndex(where: { $0.id == id }) {
                    var updated = self.sites[index]
                    if let n = event.record["name"] as? String { updated.name = n }
                    if let u = event.record["url"] as? String { updated.url = u }
                    if let i = event.record["icon_url"] as? String { updated.iconUrl = i }
                    if let s = event.record["sort_order"] as? Int { updated.sortOrder = s }
                    self.sites[index] = updated
                    self.sites.sort(by: { $0.sortOrder < $1.sortOrder })
                    self.saveLocalCache()
                }

            case .delete:
                if let id = event.oldRecord?["id"] as? String ?? event.record["id"] as? String {
                    self.sites.removeAll(where: { $0.id == id })
                    self.saveLocalCache()
                }
            }
        }
    }

    // MARK: - CRUD Actions

    public func addSite(urlInput: String, nameInput: String?) async throws -> Site {
        let normalizedURL = try FaviconService.shared.normalizeUrl(urlInput)
        let urlString = normalizedURL.absoluteString

        if sites.contains(where: { $0.url.lowercased() == urlString.lowercased() }) {
            throw NSError(domain: "PathHub", code: 409, userInfo: [
                NSLocalizedDescriptionKey: "This website has already been added to your Path Hub."
            ])
        }

        let metadata = await FaviconService.shared.fetchMetadata(for: normalizedURL, customName: nameInput)
        let maxSort = sites.map(\.sortOrder).max() ?? -1
        let newSite = Site(
            name: metadata.title,
            url: metadata.normalizedUrl,
            iconUrl: metadata.iconUrl,
            sortOrder: maxSort + 1,
            createdAt: Date()
        )

        self.sites.append(newSite)
        self.saveLocalCache()

        // Sync to Supabase
        Task {
            try? await SupabaseClient.shared.insertSite(site: newSite)
        }

        return newSite
    }

    public func updateSite(id: String, name: String, url: String) throws {
        let normalized = try FaviconService.shared.normalizeUrl(url)
        guard let index = sites.firstIndex(where: { $0.id == id }) else { return }

        var site = sites[index]
        site.name = name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? site.hostDomain : name
        site.url = normalized.absoluteString
        site.iconUrl = "https://www.google.com/s2/favicons?domain=\(site.hostDomain)&sz=128"

        sites[index] = site
        saveLocalCache()

        Task {
            try? await SupabaseClient.shared.updateSite(site: site)
        }
    }

    public func deleteSite(at offsets: IndexSet) {
        guard let index = offsets.first else { return }
        let site = sites[index]
        deleteSite(site)
    }

    public func deleteSite(_ site: Site) {
        guard let index = sites.firstIndex(where: { $0.id == site.id }) else { return }

        recentlyDeletedSite = (site: site, index: index)
        showUndoBanner = true

        undoTimer?.cancel()
        undoTimer = Just(())
            .delay(for: .seconds(5), scheduler: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.showUndoBanner = false
                self?.recentlyDeletedSite = nil
            }

        sites.remove(at: index)
        reindexSortOrders()
        saveLocalCache()

        // Sync delete to Supabase
        Task {
            try? await SupabaseClient.shared.deleteSite(id: site.id)
        }
    }

    public func undoDelete() {
        guard let deleted = recentlyDeletedSite else { return }
        undoTimer?.cancel()

        let insertIndex = min(deleted.index, sites.count)
        sites.insert(deleted.site, at: insertIndex)
        recentlyDeletedSite = nil
        showUndoBanner = false

        reindexSortOrders()
        saveLocalCache()

        Task {
            try? await SupabaseClient.shared.insertSite(site: deleted.site)
        }
    }

    public func moveSites(fromOffsets source: IndexSet, toOffset destination: Int) {
        sites.move(fromOffsets: source, toOffset: destination)
        reindexSortOrders()
        saveLocalCache()

        Task {
            for s in self.sites {
                try? await SupabaseClient.shared.updateSite(site: s)
            }
        }
    }

    private func reindexSortOrders() {
        for (i, _) in sites.enumerated() {
            sites[i].sortOrder = i
        }
    }
}
