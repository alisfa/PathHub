import SwiftUI

public enum HistorySortOrder: String, CaseIterable, Identifiable {
    case newest = "Newest First"
    case oldest = "Oldest First"
    case device = "By Device"
    case title = "By Site Title"

    public var id: String { rawValue }

    public var systemImage: String {
        switch self {
        case .newest: return "clock.arrow.down"
        case .oldest: return "clock.arrow.up"
        case .device: return "laptopcomputer.and.iphone"
        case .title: return "textformat"
        }
    }
}

public struct BalabooHistorySheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var historyStore = HistoryStore.shared

    @State private var searchText = ""
    @State private var selectedDevice: String? = nil
    @State private var sortOrder: HistorySortOrder = .newest
    @State private var showingClearAlert = false
    @State private var copiedUrlToast: String? = nil

    public init() {}

    private var devices: [String] {
        let unique = Set(historyStore.items.map { $0.deviceName.isEmpty ? "Unknown Device" : $0.deviceName })
        return unique.sorted()
    }

    private var filteredAndSortedItems: [HistoryItem] {
        var result = historyStore.items

        if let selected = selectedDevice, !selected.isEmpty, selected != "All" {
            result = result.filter { ($0.deviceName.isEmpty ? "Unknown Device" : $0.deviceName) == selected }
        }

        let q = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if !q.isEmpty {
            result = result.filter {
                $0.title.lowercased().contains(q) ||
                $0.url.lowercased().contains(q) ||
                $0.deviceName.lowercased().contains(q)
            }
        }

        switch sortOrder {
        case .newest:
            result.sort { $0.visitedAt > $1.visitedAt }
        case .oldest:
            result.sort { $0.visitedAt < $1.visitedAt }
        case .device:
            result.sort {
                if $0.deviceName != $1.deviceName {
                    return $0.deviceName.localizedCaseInsensitiveCompare($1.deviceName) == .orderedAscending
                }
                return $0.visitedAt > $1.visitedAt
            }
        case .title:
            result.sort {
                let t0 = $0.title.isEmpty ? $0.url : $0.title
                let t1 = $1.title.isEmpty ? $1.url : $1.title
                return t0.localizedCaseInsensitiveCompare(t1) == .orderedAscending
            }
        }

        return result
    }

    public var body: some View {
        NavigationStack {
            ZStack {
                Color(red: 0.05, green: 0.07, blue: 0.11)
                    .ignoresSafeArea()

                VStack(spacing: 0) {
                    // Device Filter Chips Bar
                    if !historyStore.items.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                // All Devices Chip
                                FilterChip(
                                    title: "All Devices",
                                    count: historyStore.items.count,
                                    isSelected: selectedDevice == nil || selectedDevice == "All",
                                    icon: "network"
                                ) {
                                    selectedDevice = "All"
                                }

                                // Per-device chips
                                ForEach(devices, id: \.self) { dev in
                                    let count = historyStore.items.filter {
                                        ($0.deviceName.isEmpty ? "Unknown Device" : $0.deviceName) == dev
                                    }.count
                                    FilterChip(
                                        title: dev,
                                        count: count,
                                        isSelected: selectedDevice == dev,
                                        icon: dev.lowercased().contains("ipad") ? "ipad" : (dev.lowercased().contains("mac") ? "laptopcomputer" : "iphone")
                                    ) {
                                        selectedDevice = dev
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                        }
                        .background(Color(red: 0.08, green: 0.10, blue: 0.15))

                        Divider().background(Color.white.opacity(0.08))
                    }

                    if filteredAndSortedItems.isEmpty && !historyStore.isLoading {
                        VStack(spacing: 12) {
                            Spacer()
                            Text("🕵️")
                                .font(.system(size: 44))
                            Text(searchText.isEmpty ? "No Browsing History" : "No Matches Found")
                                .font(.headline)
                                .foregroundColor(.white)
                            Text(
                                searchText.isEmpty
                                ? "Visits synced across all devices will appear here."
                                : "Try searching with a different term or clearing filters."
                            )
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                            Spacer()
                        }
                    } else {
                        List {
                            ForEach(filteredAndSortedItems) { item in
                                HistoryRowView(item: item) {
                                    UIPasteboard.general.string = item.url
                                    copiedUrlToast = "Copied to clipboard"
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                                        copiedUrlToast = nil
                                    }
                                }
                                .listRowBackground(Color(red: 0.10, green: 0.13, blue: 0.18))
                                .listRowSeparatorTint(Color.white.opacity(0.08))
                                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                    Button(role: .destructive) {
                                        historyStore.deleteItem(id: item.id)
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                            }
                        }
                        .listStyle(.insetGrouped)
                        .scrollContentBackground(.hidden)
                    }
                }

                if let toast = copiedUrlToast {
                    VStack {
                        Spacer()
                        Text(toast)
                            .font(.subheadline)
                            .bold()
                            .foregroundColor(.black)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(Capsule().fill(Color(red: 0.06, green: 0.85, blue: 0.58)))
                            .padding(.bottom, 24)
                    }
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .navigationTitle("Balaboo History")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $searchText, prompt: "Search site, URL, or device...")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") {
                        dismiss()
                    }
                    .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                }

                ToolbarItemGroup(placement: .confirmationAction) {
                    // Sort Menu
                    Menu {
                        ForEach(HistorySortOrder.allCases) { order in
                            Button {
                                sortOrder = order
                            } label: {
                                HStack {
                                    Label(order.rawValue, systemImage: order.systemImage)
                                    if sortOrder == order {
                                        Spacer()
                                        Image(systemName: "checkmark")
                                    }
                                }
                            }
                        }
                    } label: {
                        Image(systemName: "arrow.up.arrow.down.circle")
                            .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                    }

                    // Clear Action
                    if !historyStore.items.isEmpty {
                        Button {
                            showingClearAlert = true
                        } label: {
                            Image(systemName: "trash")
                                .foregroundColor(.red)
                        }
                    }
                }
            }
            .alert(clearAlertTitle, isPresented: $showingClearAlert) {
                Button("Cancel", role: .cancel) {}
                Button("Clear", role: .destructive) {
                    if let selected = selectedDevice, !selected.isEmpty, selected != "All" {
                        historyStore.clearHistory(deviceName: selected)
                    } else {
                        historyStore.clearHistory(deviceName: nil)
                    }
                }
            } message: {
                Text(clearAlertMessage)
            }
            .refreshable {
                await historyStore.fetchHistory()
            }
        }
    }

    private var isFilteringSpecificDevice: Bool {
        if let dev = selectedDevice, !dev.isEmpty, dev != "All" {
            return true
        }
        return false
    }

    private var clearAlertTitle: String {
        if isFilteringSpecificDevice, let dev = selectedDevice {
            return "Clear \(dev) History?"
        }
        return "Clear All History?"
    }

    private var clearAlertMessage: String {
        if isFilteringSpecificDevice, let dev = selectedDevice {
            return "This will delete all records visited on \(dev) across all devices."
        }
        return "This will permanently remove browsing history records across all synced devices."
    }
}

private struct FilterChip: View {
    let title: String
    let count: Int
    let isSelected: Bool
    let icon: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 11))
                Text(title)
                    .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                Text("\(count)")
                    .font(.system(size: 10, weight: .bold))
                    .padding(.horizontal, 5)
                    .padding(.vertical, 1)
                    .background(
                        Capsule().fill(isSelected ? Color.black.opacity(0.3) : Color.white.opacity(0.12))
                    )
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .foregroundColor(isSelected ? .black : .white)
            .background(
                Capsule().fill(isSelected ? Color(red: 0.06, green: 0.85, blue: 0.58) : Color(red: 0.14, green: 0.17, blue: 0.23))
            )
        }
        .buttonStyle(.plain)
    }
}

private struct HistoryRowView: View {
    let item: HistoryItem
    let onCopy: () -> Void

    var body: some View {
        Button(action: onCopy) {
            VStack(alignment: .leading, spacing: 5) {
                Text(item.title.isEmpty ? item.url : item.title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white)
                    .lineLimit(1)

                Text(item.url)
                    .font(.system(size: 11))
                    .foregroundColor(Color.secondary)
                    .lineLimit(1)

                HStack(spacing: 8) {
                    // Device Chip
                    HStack(spacing: 4) {
                        Image(systemName: "iphone.gen3")
                            .font(.system(size: 9))
                        Text(item.deviceName.isEmpty ? "Unknown Device" : item.deviceName)
                            .font(.system(size: 10, weight: .semibold))
                    }
                    .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(
                        Capsule().fill(Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.15))
                    )

                    // Formatted Timestamp
                    Text(formatTimestamp(item.visitedAt))
                        .font(.system(size: 10))
                        .foregroundColor(Color.gray)

                    Spacer()
                }
                .padding(.top, 2)
            }
            .padding(.vertical, 2)
        }
    }

    private func formatTimestamp(_ isoString: String) -> String {
        guard !isoString.isEmpty else { return "" }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        var date = formatter.date(from: isoString)
        if date == nil {
            formatter.formatOptions = [.withInternetDateTime]
            date = formatter.date(from: isoString)
        }
        guard let validDate = date else {
            return String(isoString.prefix(19)).replacingOccurrences(of: "T", with: " ")
        }
        let display = DateFormatter()
        display.dateFormat = "MMM d, h:mm a"
        return display.string(from: validDate)
    }
}
