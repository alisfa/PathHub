import Foundation
import Combine

@MainActor
public class TabStore: ObservableObject {
    public static let shared = TabStore()

    private let defaultsKey = "pathhub_saved_tabs_v2"
    private let activeTabKey = "pathhub_active_tab_id_v2"

    @Published public var tabs: [WebTab] = []
    @Published public var activeTabId: String? = nil

    public init() {
        loadTabs()
    }

    public var activeTab: WebTab? {
        guard let id = activeTabId else { return tabs.first }
        return tabs.first(where: { $0.id == id }) ?? tabs.first
    }

    @discardableResult
    public func openTab(url: String, title: String = "New Tab", siteId: String? = nil) -> WebTab {
        // If an open tab with identical URL exists, switch to it
        if let existing = tabs.first(where: { $0.url == url }) {
            activeTabId = existing.id
            saveTabs()
            return existing
        }

        let tabTitle = title.isEmpty || title.hasPrefix("http") ? (URL(string: url)?.host?.replacingOccurrences(of: "www.", with: "") ?? "New Tab") : title
        let newTab = WebTab(url: url, title: tabTitle, siteId: siteId)
        tabs.append(newTab)
        activeTabId = newTab.id
        saveTabs()
        return newTab
    }

    public func selectTab(id: String) {
        guard tabs.contains(where: { $0.id == id }) else { return }
        activeTabId = id
        if let idx = tabs.firstIndex(where: { $0.id == id }) {
            tabs[idx].lastActiveAt = Date()
        }
        saveTabs()
    }

    public func updateActiveTab(url: String, title: String) {
        guard let id = activeTabId, let idx = tabs.firstIndex(where: { $0.id == id }) else {
            if tabs.isEmpty && !url.isEmpty {
                _ = openTab(url: url, title: title)
            }
            return
        }

        var changed = false
        if tabs[idx].url != url && !url.isEmpty {
            tabs[idx].url = url
            changed = true
        }
        if !title.isEmpty && !title.hasPrefix("http") && tabs[idx].title != title {
            tabs[idx].title = title
            changed = true
        }

        if changed {
            tabs[idx].lastActiveAt = Date()
            saveTabs()
        }
    }

    public func closeTab(id: String) {
        guard let idx = tabs.firstIndex(where: { $0.id == id }) else { return }
        tabs.remove(at: idx)

        if activeTabId == id {
            if idx < tabs.count {
                activeTabId = tabs[idx].id
            } else if let last = tabs.last {
                activeTabId = last.id
            } else {
                activeTabId = nil
            }
        }
        saveTabs()
    }

    public func closeAllTabs() {
        tabs.removeAll()
        activeTabId = nil
        saveTabs()
    }

    private func loadTabs() {
        if let data = UserDefaults.standard.data(forKey: defaultsKey),
           let decoded = try? JSONDecoder().decode([WebTab].self, from: data) {
            self.tabs = decoded
            self.activeTabId = UserDefaults.standard.string(forKey: activeTabKey)
            if activeTabId == nil || !tabs.contains(where: { $0.id == activeTabId }) {
                activeTabId = tabs.first?.id
            }
        }
    }

    private func saveTabs() {
        if let encoded = try? JSONEncoder().encode(tabs) {
            UserDefaults.standard.set(encoded, forKey: defaultsKey)
        }
        UserDefaults.standard.set(activeTabId, forKey: activeTabKey)
    }
}
