import SwiftUI

public struct BalabooHistorySheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var historyStore = HistoryStore.shared

    @State private var searchText = ""
    @State private var showingClearAlert = false
    @State private var copiedUrlToast: String? = nil

    public init() {}

    private var filteredItems: [HistoryItem] {
        if searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return historyStore.items
        } else {
            let q = searchText.lowercased()
            return historyStore.items.filter {
                $0.title.lowercased().contains(q) ||
                $0.url.lowercased().contains(q) ||
                $0.deviceName.lowercased().contains(q)
            }
        }
    }

    public var body: some View {
        NavigationStack {
            ZStack {
                Color(red: 0.05, green: 0.07, blue: 0.11)
                    .ignoresSafeArea()

                VStack(spacing: 0) {
                    if historyStore.items.isEmpty && !historyStore.isLoading {
                        VStack(spacing: 12) {
                            Spacer()
                            Text("🕵️")
                                .font(.system(size: 40))
                            Text("No Browsing History Yet")
                                .font(.headline)
                                .foregroundColor(.white)
                            Text("Visits synced across all devices will appear here.")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 32)
                            Spacer()
                        }
                    } else {
                        List {
                            ForEach(filteredItems) { item in
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

                ToolbarItem(placement: .confirmationAction) {
                    if !historyStore.items.isEmpty {
                        Button("Clear All") {
                            showingClearAlert = true
                        }
                        .foregroundColor(.red)
                    }
                }
            }
            .alert("Clear All History?", isPresented: $showingClearAlert) {
                Button("Cancel", role: .cancel) {}
                Button("Clear All", role: .destructive) {
                    historyStore.clearHistory()
                }
            } message: {
                Text("This will permanently remove browsing history records across all synced devices.")
            }
            .refreshable {
                await historyStore.fetchHistory()
            }
        }
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
                        Text(item.deviceName)
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
