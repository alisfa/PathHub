import SwiftUI

public enum PlaylistTypeFilter: String, CaseIterable, Identifiable {
    case all = "All"
    case videos = "🎬 Videos"
    case reels = "⚡ Reels"
    public var id: String { rawValue }
}

public enum PlaylistSortOption: String, CaseIterable, Identifiable {
    case newest = "Newest"
    case reelsFirst = "⚡ Reels First"
    case oldest = "Oldest"
    case title = "Title (A-Z)"
    public var id: String { rawValue }
}

public struct PlaylistView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var playlistStore: PlaylistStore
    @Binding var selectedVideoItem: PlaylistItem?

    @AppStorage("playlist_is_card_view") private var isCardView: Bool = true
    @State private var typeFilter: PlaylistTypeFilter = .all
    @State private var sortOption: PlaylistSortOption = .newest
    @State private var filterDomain: String = "All"
    @State private var hideMyShares: Bool = false
    @State private var showingJoinSheet = false
    @State private var inviteCodeInput = ""
    @State private var joinErrorMessage: String? = nil
    @State private var isJoining = false
    @State private var copiedCodeToast = false
    @State private var editingNoteItem: PlaylistItem? = nil
    @State private var noteDraftText: String = ""

    public init(playlistStore: PlaylistStore, selectedVideoItem: Binding<PlaylistItem?>) {
        self.playlistStore = playlistStore
        self._selectedVideoItem = selectedVideoItem
    }

    private var availableDomains: [String] {
        var domains = Array(Set(playlistStore.items.map { $0.hostDomain })).sorted()
        domains.insert("All", at: 0)
        return domains
    }

    private var filteredItems: [PlaylistItem] {
        var list = playlistStore.items

        // 0. Hide My Shares filter
        if hideMyShares {
            let currentUserId = AuthService.shared.userId
            list = list.filter { $0.addedBy == nil || $0.addedBy != currentUserId }
        }

        // 1. Domain filter
        if filterDomain != "All" {
            list = list.filter { $0.hostDomain == filterDomain }
        }

        // 2. Type filter (Videos vs Reels)
        switch typeFilter {
        case .all:
            break
        case .videos:
            list = list.filter { !$0.isReel }
        case .reels:
            list = list.filter { $0.isReel }
        }

        // 3. Sorting
        switch sortOption {
        case .reelsFirst:
            list.sort { a, b in
                if a.isReel != b.isReel {
                    return a.isReel && !b.isReel
                }
                return a.createdAt > b.createdAt
            }
        case .newest:
            list.sort { $0.createdAt > $1.createdAt }
        case .oldest:
            list.sort { $0.createdAt < $1.createdAt }
        case .title:
            list.sort { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
        }

        return list
    }

    public var body: some View {
        NavigationStack {
            ZStack {
                // Cosmic Obsidian & Radial Aurora Backdrop
                ZStack {
                    LinearGradient(
                        colors: [
                            Color(red: 0.03, green: 0.04, blue: 0.08),
                            Color(red: 0.02, green: 0.03, blue: 0.06)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .ignoresSafeArea()

                    // Top-leading emerald aurora
                    Circle()
                        .fill(Color(red: 0.06, green: 0.72, blue: 0.51).opacity(0.12))
                        .frame(width: 320, height: 320)
                        .blur(radius: 80)
                        .offset(x: -120, y: -260)

                    // Top-trailing ruby aurora
                    Circle()
                        .fill(Color(red: 0.95, green: 0.25, blue: 0.37).opacity(0.08))
                        .frame(width: 280, height: 280)
                        .blur(radius: 80)
                        .offset(x: 140, y: -200)
                }

                VStack(spacing: 0) {
                    // Bespoke Luxury Header
                    playlistHeroHeader

                    // Controls Bar: View Toggle, Type Filter & Sort
                    controlsFilterBar

                    // Domain Filter Carousel
                    if availableDomains.count > 2 {
                        domainFilterCarousel
                    }

                    if filteredItems.isEmpty {
                        playlistEmptyState
                    } else {
                        ScrollView {
                            LazyVStack(spacing: isCardView ? 20 : 12) {
                                ForEach(filteredItems) { item in
                                    if isCardView {
                                        luxuryCardItem(item)
                                    } else {
                                        compactListItem(item)
                                    }
                                }
                            }
                            .padding(.horizontal, 18)
                            .padding(.top, 10)
                            .padding(.bottom, 40)
                        }
                    }
                }
            }
            .navigationBarHidden(true)
            .sheet(isPresented: $showingJoinSheet) {
                joinPlaylistSheet
            }
            .sheet(item: $editingNoteItem) { item in
                NoteEditorSheet(item: item, playlistStore: playlistStore, isPresented: $editingNoteItem)
            }
        }
    }

    // MARK: - Controls Bar (View Mode, Type Filter, Hide My Shares & Sort)
    private var controlsFilterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                // Type Filter Chips (All / Videos / Reels)
                HStack(spacing: 4) {
                    ForEach(PlaylistTypeFilter.allCases) { type in
                        Button(action: {
                            withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                                typeFilter = type
                            }
                        }) {
                            Text(type.rawValue)
                                .font(.system(size: 11, weight: typeFilter == type ? .bold : .semibold))
                                .foregroundColor(typeFilter == type ? Color(red: 0.03, green: 0.04, blue: 0.08) : Color.white.opacity(0.75))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(
                                    Capsule()
                                        .fill(
                                            typeFilter == type
                                                ? LinearGradient(
                                                    colors: [Color(red: 0.06, green: 0.85, blue: 0.58), Color(red: 0.04, green: 0.65, blue: 0.45)],
                                                    startPoint: .topLeading,
                                                    endPoint: .bottomTrailing
                                                )
                                                : LinearGradient(
                                                    colors: [Color.white.opacity(0.08), Color.white.opacity(0.04)],
                                                    startPoint: .topLeading,
                                                    endPoint: .bottomTrailing
                                                )
                                        )
                                )
                                .overlay(
                                    Capsule()
                                        .stroke(typeFilter == type ? Color.white.opacity(0.4) : Color.white.opacity(0.12), lineWidth: 1)
                                )
                        }
                    }
                }

                // Hide My Shares Toggle Button
                Button(action: {
                    withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                        hideMyShares.toggle()
                    }
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: hideMyShares ? "eye.slash.fill" : "eye.fill")
                            .font(.system(size: 11, weight: .bold))
                        Text("Hide My Shares")
                            .font(.system(size: 11, weight: hideMyShares ? .bold : .semibold))
                    }
                    .foregroundColor(hideMyShares ? Color(red: 0.03, green: 0.04, blue: 0.08) : Color.white.opacity(0.85))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(
                        Capsule()
                            .fill(
                                hideMyShares
                                    ? LinearGradient(colors: [Color(red: 0.06, green: 0.85, blue: 0.58), Color(red: 0.04, green: 0.65, blue: 0.45)], startPoint: .topLeading, endPoint: .bottomTrailing)
                                    : LinearGradient(colors: [Color.white.opacity(0.08), Color.white.opacity(0.04)], startPoint: .topLeading, endPoint: .bottomTrailing)
                            )
                    )
                    .overlay(Capsule().stroke(hideMyShares ? Color.white.opacity(0.4) : Color.white.opacity(0.12), lineWidth: 1))
                }

                // Sort Picker Menu
                Menu {
                    ForEach(PlaylistSortOption.allCases) { opt in
                        Button(action: {
                            sortOption = opt
                        }) {
                            HStack {
                                Text(opt.rawValue)
                                if sortOption == opt {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.up.arrow.down")
                            .font(.system(size: 11, weight: .bold))
                        Text(sortOption == .reelsFirst ? "⚡ Reels" : sortOption.rawValue)
                            .font(.system(size: 11, weight: .bold))
                    }
                    .foregroundColor(Color.white.opacity(0.85))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(
                        Capsule()
                            .fill(Color.white.opacity(0.08))
                            .overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 1))
                    )
                }

                // View Selector Toggle (List vs Card)
                HStack(spacing: 2) {
                    Button(action: {
                        withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                            isCardView = false
                        }
                    }) {
                        Image(systemName: "list.bullet")
                            .font(.system(size: 13, weight: !isCardView ? .bold : .regular))
                            .foregroundColor(!isCardView ? Color(red: 0.03, green: 0.04, blue: 0.08) : Color.white.opacity(0.5))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 5)
                            .background(
                                Capsule()
                                    .fill(!isCardView ? Color(red: 0.06, green: 0.85, blue: 0.58) : Color.clear)
                            )
                    }

                    Button(action: {
                        withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                            isCardView = true
                        }
                    }) {
                        Image(systemName: "rectangle.grid.1x2.fill")
                            .font(.system(size: 13, weight: isCardView ? .bold : .regular))
                            .foregroundColor(isCardView ? Color(red: 0.03, green: 0.04, blue: 0.08) : Color.white.opacity(0.5))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 5)
                            .background(
                                Capsule()
                                    .fill(isCardView ? Color(red: 0.06, green: 0.85, blue: 0.58) : Color.clear)
                            )
                    }
                }
                .padding(2)
                .background(
                    Capsule()
                        .fill(Color.white.opacity(0.08))
                        .overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 1))
                )
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 6)
        }
    }

    // MARK: - Domain Filter Carousel
    private var domainFilterCarousel: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(availableDomains, id: \.self) { domain in
                    Button(action: {
                        filterDomain = domain
                    }) {
                        Text(domain)
                            .font(.system(size: 11, weight: filterDomain == domain ? .bold : .medium))
                            .foregroundColor(filterDomain == domain ? Color(red: 0.03, green: 0.04, blue: 0.08) : Color.white.opacity(0.65))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(
                                Capsule()
                                    .fill(filterDomain == domain ? Color(red: 0.06, green: 0.85, blue: 0.58) : Color.white.opacity(0.06))
                            )
                            .overlay(
                                Capsule().stroke(filterDomain == domain ? Color.white.opacity(0.3) : Color.white.opacity(0.1), lineWidth: 1)
                            )
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 4)
        }
    }

    // MARK: - Seen Status Badge
    @ViewBuilder
    private func seenStatusBadge(for item: PlaylistItem) -> some View {
        let currentUserId = AuthService.shared.userId
        let isSharedByMe = (item.addedBy != nil && item.addedBy == currentUserId)
        let isSeenByOthers = item.seenBy.contains(where: { $0 != item.addedBy && !$0.isEmpty })
        let isSeenByMe = item.seenBy.contains(currentUserId)

        if isSharedByMe {
            if isSeenByOthers {
                HStack(spacing: 3) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                    Text("Seen by others")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                }
                .padding(.horizontal, 7)
                .padding(.vertical, 3.5)
                .background(
                    Capsule()
                        .fill(Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.18))
                        .overlay(Capsule().stroke(Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.5), lineWidth: 0.8))
                )
            } else {
                HStack(spacing: 3) {
                    Image(systemName: "checkmark")
                        .font(.system(size: 8, weight: .bold))
                        .foregroundColor(Color.white.opacity(0.6))
                    Text("Unseen by others")
                        .font(.system(size: 9, weight: .medium))
                        .foregroundColor(Color.white.opacity(0.7))
                }
                .padding(.horizontal, 7)
                .padding(.vertical, 3.5)
                .background(
                    Capsule()
                        .fill(Color.white.opacity(0.08))
                        .overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 0.8))
                )
            }
        } else {
            if !isSeenByMe {
                HStack(spacing: 3) {
                    Circle()
                        .fill(Color(red: 0.02, green: 0.71, blue: 0.83))
                        .frame(width: 5, height: 5)
                    Text("NEW")
                        .font(.system(size: 9, weight: .black))
                        .foregroundColor(Color.white)
                }
                .padding(.horizontal, 7)
                .padding(.vertical, 3.5)
                .background(
                    Capsule()
                        .fill(Color(red: 0.02, green: 0.71, blue: 0.83).opacity(0.35))
                        .overlay(Capsule().stroke(Color(red: 0.02, green: 0.71, blue: 0.83).opacity(0.7), lineWidth: 0.8))
                )
            } else {
                HStack(spacing: 3) {
                    Image(systemName: "eye.fill")
                        .font(.system(size: 8))
                        .foregroundColor(Color.white.opacity(0.5))
                    Text("Seen")
                        .font(.system(size: 9, weight: .medium))
                        .foregroundColor(Color.white.opacity(0.55))
                }
                .padding(.horizontal, 6)
                .padding(.vertical, 3)
                .background(
                    Capsule()
                        .fill(Color.white.opacity(0.05))
                        .overlay(Capsule().stroke(Color.white.opacity(0.1), lineWidth: 0.6))
                )
            }
        }
    }

    // MARK: - Luxury Card View Item (Big Hero Thumbnail + Expanded Note Box)
    @ViewBuilder
    private func luxuryCardItem(_ item: PlaylistItem) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            // 1. 16:9 Cinematic Hero Image Box
            Button(action: {
                playlistStore.markItemSeen(id: item.id)
                selectedVideoItem = item
                dismiss()
            }) {
                ZStack(alignment: .bottomLeading) {
                    // Hero Image
                    RefererAsyncImage(
                        url: URL(string: item.cleanThumbnailUrl),
                        referer: "https://\(item.hostDomain)/"
                    ) { image in
                        image
                            .resizable()
                            .aspectRatio(16/9, contentMode: .fill)
                    } placeholder: {
                        AsyncImage(url: URL(string: "https://www.google.com/s2/favicons?domain=\(item.hostDomain)&sz=256")) { favPhase in
                            if let favImg = favPhase.image {
                                ZStack {
                                    Color(red: 0.07, green: 0.10, blue: 0.18)
                                    favImg
                                        .resizable()
                                        .aspectRatio(contentMode: .fit)
                                        .frame(width: 54, height: 54)
                                }
                            } else {
                                ZStack {
                                    Color(red: 0.07, green: 0.10, blue: 0.18)
                                    Image(systemName: "play.rectangle.fill")
                                        .font(.system(size: 44))
                                        .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                                }
                            }
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 200)
                    .clipped()

                    // Dark gradient scrim overlay
                    LinearGradient(
                        colors: [Color.clear, Color.black.opacity(0.8)],
                        startPoint: .center,
                        endPoint: .bottom
                    )

                    // Top Bar Badges (⚡ REEL, Seen Status & Domain)
                    VStack {
                        HStack(spacing: 8) {
                            if item.isReel {
                                HStack(spacing: 4) {
                                    Image(systemName: "bolt.fill")
                                        .font(.system(size: 10, weight: .black))
                                    Text("REEL")
                                        .font(.system(size: 10, weight: .black))
                                        .tracking(0.5)
                                }
                                .foregroundColor(.white)
                                .padding(.horizontal, 9)
                                .padding(.vertical, 4)
                                .background(
                                    Capsule()
                                        .fill(
                                            LinearGradient(
                                                colors: [Color(red: 1.0, green: 0.45, blue: 0.05), Color(red: 0.95, green: 0.25, blue: 0.0)],
                                                startPoint: .topLeading,
                                                endPoint: .bottomTrailing
                                            )
                                        )
                                )
                                .overlay(Capsule().stroke(Color.white.opacity(0.5), lineWidth: 1))
                                .shadow(color: Color(red: 1.0, green: 0.45, blue: 0.05).opacity(0.6), radius: 6, x: 0, y: 2)
                            }

                            seenStatusBadge(for: item)

                            Spacer()

                            // Domain pill
                            HStack(spacing: 4) {
                                Image(systemName: "network")
                                    .font(.system(size: 9))
                                Text(item.hostDomain)
                                    .font(.system(size: 10, weight: .bold))
                            }
                            .foregroundColor(.white.opacity(0.9))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(
                                Capsule()
                                    .fill(Color.black.opacity(0.65))
                                    .overlay(Capsule().stroke(Color.white.opacity(0.2), lineWidth: 0.8))
                            )
                        }
                        .padding(12)
                        Spacer()
                    }

                    // Center Glass Play Orb
                    HStack {
                        Spacer()
                        Circle()
                            .fill(.ultraThinMaterial)
                            .overlay(Circle().fill(Color.black.opacity(0.4)))
                            .frame(width: 48, height: 48)
                            .overlay(Circle().stroke(Color.white.opacity(0.5), lineWidth: 1.2))
                            .overlay(
                                Image(systemName: "play.fill")
                                    .font(.system(size: 18, weight: .black))
                                    .foregroundColor(.white)
                                    .offset(x: 2)
                            )
                            .shadow(color: Color.black.opacity(0.4), radius: 10, x: 0, y: 4)
                        Spacer()
                    }
                    .padding(.bottom, 60)
                }
            }
            .buttonStyle(.plain)

            // 2. Card Content (Title, Note, Actions)
            VStack(alignment: .leading, spacing: 12) {
                // Video Title
                Text(item.title)
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                    .lineLimit(2)
                    .lineSpacing(3)

                // 3. Expanded Note Card
                if !item.notes.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 5) {
                            Image(systemName: "quote.bubble.fill")
                                .font(.system(size: 11))
                                .foregroundColor(Color(red: 1.0, green: 0.65, blue: 0.15))
                            Text("Note")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(Color(red: 1.0, green: 0.65, blue: 0.15))
                            Spacer()
                            Button(action: {
                                noteDraftText = item.notes
                                editingNoteItem = item
                            }) {
                                Image(systemName: "square.and.pencil")
                                    .font(.system(size: 12))
                                    .foregroundColor(Color.white.opacity(0.5))
                            }
                        }

                        Text(item.notes)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(Color.white.opacity(0.9))
                            .lineSpacing(3)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(12)
                    .background(
                        RoundedRectangle(cornerRadius: 14)
                            .fill(Color(red: 1.0, green: 0.65, blue: 0.15).opacity(0.1))
                            .overlay(
                                RoundedRectangle(cornerRadius: 14)
                                    .stroke(Color(red: 1.0, green: 0.65, blue: 0.15).opacity(0.3), lineWidth: 1)
                            )
                    )
                } else {
                    Button(action: {
                        noteDraftText = ""
                        editingNoteItem = item
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "square.and.pencil")
                                .font(.system(size: 12))
                            Text("Add note or timestamp...")
                                .font(.system(size: 12, weight: .medium))
                        }
                        .foregroundColor(Color.white.opacity(0.45))
                        .padding(.vertical, 6)
                        .padding(.horizontal, 10)
                        .background(
                            Capsule()
                                .fill(Color.white.opacity(0.05))
                                .overlay(Capsule().stroke(Color.white.opacity(0.1), lineWidth: 0.8))
                        )
                    }
                    .buttonStyle(.plain)
                }

                // 4. Card Bottom Actions
                HStack {
                    Button(action: {
                        selectedVideoItem = item
                        dismiss()
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "play.fill")
                                .font(.system(size: 11, weight: .black))
                            Text("Play Video")
                                .font(.system(size: 13, weight: .bold))
                        }
                        .foregroundColor(Color(red: 0.03, green: 0.04, blue: 0.08))
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(
                            Capsule()
                                .fill(
                                    LinearGradient(
                                        colors: [Color(red: 0.06, green: 0.85, blue: 0.58), Color(red: 0.04, green: 0.65, blue: 0.45)],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                        )
                    }

                    Spacer()

                    // Edit Note Button
                    Button(action: {
                        noteDraftText = item.notes
                        editingNoteItem = item
                    }) {
                        Image(systemName: item.notes.isEmpty ? "square.and.pencil" : "note.text")
                            .font(.system(size: 15))
                            .foregroundColor(item.notes.isEmpty ? Color.white.opacity(0.4) : Color(red: 1.0, green: 0.65, blue: 0.15))
                            .padding(8)
                            .background(Circle().fill(Color.white.opacity(0.06)))
                    }

                    // Delete Button
                    Button(action: {
                        withAnimation {
                            playlistStore.deleteItem(item)
                        }
                    }) {
                        Image(systemName: "trash")
                            .font(.system(size: 14))
                            .foregroundColor(.white.opacity(0.4))
                            .padding(8)
                            .background(Circle().fill(Color.white.opacity(0.06)))
                    }
                }
            }
            .padding(16)
        }
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(.ultraThinMaterial)
                .overlay(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .fill(Color(red: 0.07, green: 0.10, blue: 0.18).opacity(0.9))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .stroke(
                            LinearGradient(
                                colors: [Color.white.opacity(0.32), Color.white.opacity(0.08), Color.white.opacity(0.01)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1
                        )
                )
                .shadow(color: Color.black.opacity(0.4), radius: 18, x: 0, y: 8)
        )
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    // MARK: - Compact List View Item
    @ViewBuilder
    private func compactListItem(_ item: PlaylistItem) -> some View {
        Button(action: {
            playlistStore.markItemSeen(id: item.id)
            selectedVideoItem = item
            dismiss()
        }) {
            HStack(spacing: 14) {
                // 16:9 Thumbnail Box
                ZStack {
                    RefererAsyncImage(
                        url: URL(string: item.cleanThumbnailUrl),
                        referer: "https://\(item.hostDomain)/"
                    ) { image in
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    } placeholder: {
                        AsyncImage(url: URL(string: "https://www.google.com/s2/favicons?domain=\(item.hostDomain)&sz=256")) { favPhase in
                            if let favImg = favPhase.image {
                                ZStack {
                                    Color(red: 0.07, green: 0.10, blue: 0.18)
                                    favImg
                                        .resizable()
                                        .aspectRatio(contentMode: .fit)
                                        .frame(width: 34, height: 34)
                                }
                            } else {
                                ZStack {
                                    Color(red: 0.07, green: 0.10, blue: 0.18)
                                    Image(systemName: "play.circle.fill")
                                        .font(.system(size: 28))
                                        .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                                }
                            }
                        }
                    }
                    .frame(width: 98, height: 64)
                    .cornerRadius(14)
                    .clipped()

                    // Center translucent play orb
                    Circle()
                        .fill(Color.black.opacity(0.55))
                        .frame(width: 28, height: 28)
                        .overlay(Circle().stroke(Color.white.opacity(0.4), lineWidth: 0.8))
                        .overlay(
                            Image(systemName: "play.fill")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.white)
                                .offset(x: 1)
                        )
                }
                .frame(width: 98, height: 64)
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.white.opacity(0.18), lineWidth: 1))

                // Title, Reel badge, Host Domain, Seen status and Note snippet
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 5) {
                        if item.isReel {
                            Text("⚡ REEL")
                                .font(.system(size: 9, weight: .black))
                                .foregroundColor(.white)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(Capsule().fill(Color(red: 1.0, green: 0.45, blue: 0.05)))
                        }

                        Text(item.title)
                            .font(.system(size: 14, weight: .bold))
                            .lineLimit(2)
                            .foregroundColor(.white)
                            .lineSpacing(3)
                    }

                    HStack(spacing: 6) {
                        HStack(spacing: 4) {
                            Image(systemName: "network")
                                .font(.system(size: 11))
                                .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))

                            Text(item.hostDomain)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                        }

                        seenStatusBadge(for: item)
                    }

                    // Attached Note preview pill
                    if !item.notes.isEmpty {
                        Button(action: {
                            noteDraftText = item.notes
                            editingNoteItem = item
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: "note.text")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(Color(red: 1.0, green: 0.65, blue: 0.15))
                                Text(item.notes)
                                    .font(.system(size: 11, weight: .medium))
                                    .foregroundColor(Color.white.opacity(0.85))
                                    .lineLimit(1)
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(
                                Capsule()
                                    .fill(Color(red: 1.0, green: 0.65, blue: 0.15).opacity(0.15))
                                    .overlay(
                                        Capsule().stroke(Color(red: 1.0, green: 0.65, blue: 0.15).opacity(0.35), lineWidth: 0.8)
                                    )
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }

                Spacer()

                // Action Buttons: Notes & Delete
                HStack(spacing: 2) {
                    Button(action: {
                        noteDraftText = item.notes
                        editingNoteItem = item
                    }) {
                        Image(systemName: item.notes.isEmpty ? "square.and.pencil" : "note.text")
                            .font(.system(size: 15))
                            .foregroundColor(item.notes.isEmpty ? Color.white.opacity(0.35) : Color(red: 1.0, green: 0.65, blue: 0.15))
                            .padding(7)
                    }

                    Button(action: {
                        withAnimation {
                            playlistStore.deleteItem(item)
                        }
                    }) {
                        Image(systemName: "trash")
                            .font(.system(size: 14))
                            .foregroundColor(.white.opacity(0.35))
                            .padding(7)
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(.ultraThinMaterial)
                    .overlay(
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .fill(Color(red: 0.07, green: 0.10, blue: 0.18).opacity(0.85))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .stroke(
                                LinearGradient(
                                    colors: [Color.white.opacity(0.32), Color.white.opacity(0.08), Color.white.opacity(0.01)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                ),
                                lineWidth: 1
                            )
                    )
                    .shadow(color: Color.black.opacity(0.35), radius: 14, x: 0, y: 7)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Luxury Header
    private var playlistHeroHeader: some View {
        HStack(alignment: .center) {
            HStack(spacing: 12) {
                // Glass Back / Dismiss Button
                Button(action: {
                    dismiss()
                }) {
                    ZStack {
                        Circle()
                            .fill(.ultraThinMaterial)
                            .overlay(
                                Circle()
                                    .fill(Color(red: 0.07, green: 0.10, blue: 0.18).opacity(0.8))
                            )
                            .frame(width: 40, height: 40)
                            .overlay(Circle().stroke(Color.white.opacity(0.2), lineWidth: 1))

                        Image(systemName: "chevron.backward")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.white)
                    }
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(playlistStore.currentPlaylistName)
                        .font(.system(size: 20, weight: .black, design: .rounded))
                        .foregroundColor(.white)
                        .tracking(-0.5)

                    Text("\(playlistStore.items.count) videos saved")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Color.white.opacity(0.5))
                }
            }

            Spacer()

            // Invite Code Pill & Join Button
            HStack(spacing: 8) {
                // Tap to copy pill
                Button(action: {
                    UIPasteboard.general.string = playlistStore.currentInviteCode
                    copiedCodeToast = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                        copiedCodeToast = false
                    }
                }) {
                    HStack(spacing: 5) {
                        Image(systemName: "doc.on.doc.fill")
                            .font(.system(size: 11))
                        Text(copiedCodeToast ? "COPIED" : playlistStore.currentInviteCode)
                            .font(.system(size: 11, weight: .black))
                            .tracking(0.5)
                    }
                    .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(
                        Capsule()
                            .fill(Color(red: 0.06, green: 0.72, blue: 0.51).opacity(0.15))
                    )
                    .overlay(
                        Capsule().stroke(Color(red: 0.06, green: 0.72, blue: 0.51).opacity(0.35), lineWidth: 0.8)
                    )
                }

                // Join Button
                Button(action: {
                    showingJoinSheet = true
                }) {
                    ZStack {
                        Circle()
                            .fill(.ultraThinMaterial)
                            .overlay(
                                Circle()
                                    .fill(Color(red: 0.07, green: 0.10, blue: 0.18).opacity(0.8))
                            )
                            .frame(width: 36, height: 36)
                            .overlay(Circle().stroke(Color.white.opacity(0.2), lineWidth: 1))

                        Image(systemName: "person.badge.plus")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 14)
        .padding(.bottom, 6)
    }

    // MARK: - Empty State
    private var playlistEmptyState: some View {
        VStack(spacing: 18) {
            Spacer()

            ZStack {
                Circle()
                    .fill(Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.12))
                    .frame(width: 88, height: 88)
                    .overlay(
                        Circle().stroke(Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.3), lineWidth: 1.5)
                    )
                    .shadow(color: Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.4), radius: 20, x: 0, y: 8)

                Image(systemName: "play.circle.fill")
                    .font(.system(size: 44))
                    .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
            }

            Text("No Saved Videos Yet")
                .font(.system(size: 22, weight: .black, design: .rounded))
                .foregroundColor(.white)
                .tracking(-0.5)

            Text("Browse your favorite sites and tap the Save Video jewel on any video to curate your private playlist.")
                .font(.system(size: 14))
                .foregroundColor(.white.opacity(0.55))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
                .lineSpacing(4)

            Spacer()
        }
    }

    // MARK: - Join Sheet
    private var joinPlaylistSheet: some View {
        NavigationStack {
            ZStack {
                Color(red: 0.03, green: 0.04, blue: 0.08)
                    .ignoresSafeArea()

                VStack(spacing: 20) {
                    Text("Enter a 6-character playlist invite code to immediately join a shared playlist.")
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.6))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)
                        .padding(.top, 10)

                    TextField("Invite Code (e.g. HUB001)", text: $inviteCodeInput)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .padding(14)
                        .background(
                            RoundedRectangle(cornerRadius: 14)
                                .fill(Color.white.opacity(0.08))
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(Color.white.opacity(0.2), lineWidth: 1)
                        )
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled(true)
                        .padding(.horizontal, 20)

                    if let err = joinErrorMessage {
                        Text(err)
                            .font(.caption)
                            .foregroundColor(.red)
                            .padding(.horizontal, 20)
                    }

                    Button(action: {
                        joinPlaylist()
                    }) {
                        HStack {
                            if isJoining {
                                ProgressView()
                                    .tint(.black)
                                    .padding(.trailing, 6)
                            }
                            Text("Join Playlist")
                                .fontWeight(.bold)
                        }
                        .foregroundColor(Color(red: 0.03, green: 0.04, blue: 0.08))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(
                            Capsule()
                                .fill(
                                    LinearGradient(
                                        colors: [Color(red: 0.06, green: 0.85, blue: 0.58), Color(red: 0.04, green: 0.65, blue: 0.45)],
                                        startPoint: .leading,
                                        endPoint: .trailing
                                    )
                                )
                        )
                    }
                    .disabled(inviteCodeInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isJoining)
                    .padding(.horizontal, 20)

                    Spacer()
                }
            }
            .navigationTitle("Join Playlist")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        showingJoinSheet = false
                    }
                    .foregroundColor(.white.opacity(0.8))
                }
            }
        }
        .presentationDetents([.fraction(0.4)])
    }

    private func joinPlaylist() {
        let code = inviteCodeInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !code.isEmpty else { return }
        isJoining = true
        joinErrorMessage = nil

        Task {
            let success = await playlistStore.joinByCode(code)
            isJoining = false
            if success {
                showingJoinSheet = false
                inviteCodeInput = ""
            } else {
                joinErrorMessage = "Invalid code or playlist not found. Try another."
            }
        }
    }
}

// Separate Sheet Component for Note Editing
public struct NoteEditorSheet: View {
    let item: PlaylistItem
    let playlistStore: PlaylistStore
    @Binding var isPresented: PlaylistItem?
    @State private var noteDraftText: String

    public init(item: PlaylistItem, playlistStore: PlaylistStore, isPresented: Binding<PlaylistItem?>) {
        self.item = item
        self.playlistStore = playlistStore
        self._isPresented = isPresented
        self._noteDraftText = State(initialValue: item.notes)
    }

    public var body: some View {
        NavigationStack {
            ZStack {
                Color(red: 0.03, green: 0.04, blue: 0.08)
                    .ignoresSafeArea()

                VStack(spacing: 18) {
                    // Video reference banner
                    HStack(spacing: 12) {
                        AsyncImage(url: URL(string: item.cleanThumbnailUrl)) { phase in
                            if let img = phase.image {
                                img.resizable().aspectRatio(contentMode: .fill)
                            } else {
                                Color(red: 0.08, green: 0.12, blue: 0.22)
                            }
                        }
                        .frame(width: 58, height: 40)
                        .cornerRadius(8)
                        .clipped()

                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title)
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(.white)
                                .lineLimit(1)
                            Text(item.hostDomain)
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                        }
                        Spacer()
                    }
                    .padding(12)
                    .background(
                        RoundedRectangle(cornerRadius: 14)
                            .fill(Color.white.opacity(0.05))
                    )
                    .padding(.horizontal, 20)

                    // Text Editor for Notes
                    ZStack(alignment: .topLeading) {
                        RoundedRectangle(cornerRadius: 16)
                            .fill(Color(red: 0.08, green: 0.11, blue: 0.18))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(
                                        LinearGradient(
                                            colors: [Color.white.opacity(0.2), Color.white.opacity(0.05)],
                                            startPoint: .topLeading,
                                            endPoint: .bottomTrailing
                                        ),
                                        lineWidth: 1
                                    )
                            )

                        if noteDraftText.isEmpty {
                            Text("Add your personal notes, memorable timestamps (e.g. 05:22), or thoughts for this video...")
                                .font(.system(size: 14))
                                .foregroundColor(.white.opacity(0.35))
                                .padding(14)
                        }

                        TextEditor(text: $noteDraftText)
                            .scrollContentBackground(.hidden)
                            .font(.system(size: 14))
                            .foregroundColor(.white)
                            .padding(8)
                    }
                    .frame(height: 150)
                    .padding(.horizontal, 20)

                    // Save / Delete Note Actions
                    VStack(spacing: 10) {
                        Button(action: {
                            playlistStore.updateItemNotes(id: item.id, notes: noteDraftText.trimmingCharacters(in: .whitespacesAndNewlines))
                            isPresented = nil
                        }) {
                            HStack {
                                Image(systemName: "checkmark.circle.fill")
                                Text("Save Note")
                                    .fontWeight(.bold)
                            }
                            .foregroundColor(Color(red: 0.03, green: 0.04, blue: 0.08))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(
                                Capsule()
                                    .fill(
                                        LinearGradient(
                                            colors: [Color(red: 1.0, green: 0.65, blue: 0.15), Color(red: 0.95, green: 0.45, blue: 0.05)],
                                            startPoint: .leading,
                                            endPoint: .trailing
                                        )
                                    )
                            )
                        }

                        if !item.notes.isEmpty {
                            Button(action: {
                                playlistStore.updateItemNotes(id: item.id, notes: "")
                                isPresented = nil
                            }) {
                                Text("Delete Note")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(Color.red.opacity(0.85))
                            }
                        }
                    }
                    .padding(.horizontal, 20)

                    Spacer()
                }
                .padding(.top, 16)
            }
            .navigationTitle("Video Note")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") {
                        isPresented = nil
                    }
                    .foregroundColor(.white.opacity(0.8))
                }
            }
        }
        .presentationDetents([.fraction(0.55), .medium])
    }
}
