import SwiftUI

public struct HomeView: View {
    @StateObject private var siteStore = SiteStore()
    @StateObject private var playlistStore = PlaylistStore()
    @ObservedObject private var tabStore = TabStore.shared
    @ObservedObject private var updateService = UpdateService.shared

    @State private var showingAddSheet = false
    @State private var showingSettingsSheet = false
    @State private var siteToEdit: Site? = nil
    @State private var siteToDelete: Site? = nil
    @State private var showingDeleteConfirmation = false
    @State private var activeSite: Site? = nil
    @State private var showPlaylistSheet = false
    @State private var selectedVideoItem: PlaylistItem? = nil

    public init() {}

    public var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                // Cosmic Obsidian & Radial Aura Backdrop
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

                    // Top-trailing cyan aurora
                    Circle()
                        .fill(Color(red: 0.02, green: 0.71, blue: 0.83).opacity(0.09))
                        .frame(width: 280, height: 280)
                        .blur(radius: 80)
                        .offset(x: 140, y: -200)
                }

                VStack(spacing: 0) {
                    // Bespoke Flagship Glass Hero Header
                    homeHeroHeader

                    // 1.1.1.1 Device VPN Quick Status Card
                    let isVpnActive = VpnManager.shared.isSystemVpnActive()
                    Button(action: {
                        if !isVpnActive {
                            VpnManager.shared.launch1111App()
                        } else {
                            showingSettingsSheet = true
                        }
                    }) {
                        HStack(spacing: 10) {
                            Circle()
                                .fill(isVpnActive ? Color(red: 0.06, green: 0.85, blue: 0.58) : Color.orange)
                                .frame(width: 8, height: 8)

                            VStack(alignment: .leading, spacing: 1) {
                                Text(isVpnActive ? "1.1.1.1 Device VPN Active" : "Cloudflare 1.1.1.1 Disconnected")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(.white)

                                Text(isVpnActive ? "Entire device protected • Zero speed loss" : "Tap to connect 1.1.1.1 & unblock all sites")
                                    .font(.system(size: 10))
                                    .foregroundColor(isVpnActive ? Color(red: 0.06, green: 0.85, blue: 0.58) : Color.white.opacity(0.55))
                            }

                            Spacer()

                            if !isVpnActive {
                                Text("CONNECT")
                                    .font(.system(size: 10, weight: .black))
                                    .foregroundColor(Color(red: 0.03, green: 0.04, blue: 0.08))
                                    .padding(.horizontal, 9)
                                    .padding(.vertical, 4)
                                    .background(Capsule().fill(Color(red: 0.06, green: 0.85, blue: 0.58)))
                            }
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(isVpnActive ? Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.12) : Color(red: 0.07, green: 0.10, blue: 0.18).opacity(0.75))
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(isVpnActive ? Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.3) : Color.white.opacity(0.12), lineWidth: 1)
                        )
                        .padding(.horizontal, 20)
                        .padding(.top, 4)
                        .padding(.bottom, 6)
                    }
                    .buttonStyle(.plain)

                    if siteStore.sites.isEmpty {
                        emptyStateView
                    } else {
                        siteListView
                    }
                }

                // Million-Dollar Liquid Jewel Action Button (Bottom Right)
                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        Button(action: {
                            showingAddSheet = true
                        }) {
                            ZStack {
                                Circle()
                                    .fill(
                                        LinearGradient(
                                            colors: [
                                                Color(red: 0.06, green: 0.85, blue: 0.58),
                                                Color(red: 0.04, green: 0.58, blue: 0.42)
                                            ],
                                            startPoint: .topLeading,
                                            endPoint: .bottomTrailing
                                        )
                                    )
                                    .frame(width: 62, height: 62)
                                    .overlay(
                                        Circle()
                                            .stroke(
                                                LinearGradient(
                                                    colors: [Color.white.opacity(0.65), Color.white.opacity(0.15)],
                                                    startPoint: .topLeading,
                                                    endPoint: .bottomTrailing
                                                ),
                                                lineWidth: 1
                                            )
                                    )
                                    .shadow(color: Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.55), radius: 18, x: 0, y: 8)

                                Image(systemName: "plus")
                                    .font(.system(size: 26, weight: .bold))
                                    .foregroundColor(.white)
                            }
                        }
                        .padding(.trailing, 22)
                        .padding(.bottom, 26)
                    }
                }

                // Liquid Glass Undo Banner
                if siteStore.showUndoBanner, let deleted = siteStore.recentlyDeletedSite {
                    HStack(spacing: 12) {
                        Image(systemName: "trash.fill")
                            .foregroundColor(.white.opacity(0.8))

                        Text("Deleted \"\(deleted.site.name)\"")
                            .font(.subheadline)
                            .foregroundColor(.white)
                            .lineLimit(1)

                        Spacer()

                        Button(action: {
                            withAnimation(.spring()) {
                                siteStore.undoDelete()
                            }
                        }) {
                            Text("UNDO")
                                .font(.subheadline)
                                .fontWeight(.bold)
                                .foregroundColor(Color(red: 0.06, green: 0.72, blue: 0.51))
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 14)
                    .background(
                        Capsule()
                            .fill(.ultraThinMaterial)
                            .overlay(
                                Capsule()
                                    .fill(Color(red: 0.07, green: 0.10, blue: 0.18).opacity(0.85))
                            )
                            .overlay(
                                Capsule()
                                    .stroke(Color.white.opacity(0.25), lineWidth: 1)
                            )
                            .shadow(color: Color.black.opacity(0.4), radius: 16, x: 0, y: 8)
                    )
                    .padding(.horizontal, 20)
                    .padding(.bottom, 24)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .navigationBarHidden(true)
            .sheet(isPresented: $showingAddSheet) {
                AddSiteSheet(store: siteStore)
            }
            .sheet(item: $siteToEdit) { site in
                EditSiteSheet(store: siteStore, site: site)
            }
            .sheet(isPresented: $showPlaylistSheet) {
                PlaylistView(playlistStore: playlistStore, selectedVideoItem: $selectedVideoItem)
            }
            .sheet(isPresented: $showingSettingsSheet) {
                AccountSheet()
            }
            .sheet(item: $updateService.availableUpdate) { update in
                UpdateSheet(update: update)
            }
            .fullScreenCover(item: $activeSite) { site in
                let targetStr = VpnManager.shared.resolveUrl(site.url)
                if let url = URL(string: targetStr) {
                    BrowserView(url: url, siteId: site.id, playlistStore: playlistStore)
                }
            }
            .confirmationDialog(
                "Delete Website",
                isPresented: $showingDeleteConfirmation,
                titleVisibility: .visible,
                presenting: siteToDelete
            ) { site in
                Button("Delete \"\(site.name)\"", role: .destructive) {
                    withAnimation {
                        siteStore.deleteSite(site)
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: { site in
                Text("Are you sure you want to delete this site from your Path Hub? You can undo this action.")
            }
            .onChange(of: selectedVideoItem) { _, item in
                if let video = item {
                    let rawUrl = video.cleanPageUrl.isEmpty ? video.videoUrl : video.cleanPageUrl
                    let resolved = VpnManager.shared.resolveUrl(rawUrl)
                    if let url = URL(string: resolved) {
                        activeSite = Site(
                            id: video.siteId ?? UUID().uuidString,
                            name: video.title,
                            url: url.absoluteString,
                            iconUrl: video.thumbnailUrl
                        )
                        selectedVideoItem = nil
                    }
                }
            }
            .onAppear {
                siteStore.syncWithRemote()
                playlistStore.syncWithRemote()
            }
            .task {
                _ = await updateService.checkForUpdates()
            }
        }
    }

    // MARK: - Bespoke Glass Hero Header
    private var homeHeroHeader: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text("Path Hub")
                        .font(.system(size: 26, weight: .black, design: .rounded))
                        .foregroundColor(.white)
                        .tracking(-0.8)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)

                    // Realtime Status Indicator (Concentric glowing dot, never stretched)
                    ZStack {
                        Circle()
                            .fill(Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.2))
                            .frame(width: 10, height: 10)
                        Circle()
                            .fill(Color(red: 0.06, green: 0.85, blue: 0.58))
                            .frame(width: 6, height: 6)
                    }
                }

                Text(siteStore.sites.count == 1 ? "1 site synced" : "\(siteStore.sites.count) sites synced")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Color.white.opacity(0.5))
            }

            Spacer()

            HStack(spacing: 8) {
                // Resume Session Pill if open tabs exist
                if let active = tabStore.activeTab {
                    Button(action: {
                        activeSite = Site(id: active.siteId ?? UUID().uuidString, name: active.title, url: active.url, iconUrl: "")
                    }) {
                        HStack(spacing: 5) {
                            Image(systemName: "square.on.square.fill")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(Color(red: 0.02, green: 0.71, blue: 0.83))

                            Text("Resume")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)

                            Text("\(tabStore.tabs.count)")
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(Color(red: 0.03, green: 0.04, blue: 0.08))
                                .padding(.horizontal, 5)
                                .padding(.vertical, 1)
                                .background(Capsule().fill(Color(red: 0.02, green: 0.71, blue: 0.83)))
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 7)
                        .background(
                            Capsule()
                                .fill(.ultraThinMaterial)
                                .overlay(
                                    Capsule()
                                        .fill(Color(red: 0.07, green: 0.10, blue: 0.18).opacity(0.8))
                                )
                                .overlay(
                                    Capsule()
                                        .stroke(Color.white.opacity(0.2), lineWidth: 1)
                                )
                        )
                        .shadow(color: Color.black.opacity(0.3), radius: 8, x: 0, y: 4)
                    }
                }

                // Floating Glass Playlist Pill
                Button(action: {
                    showPlaylistSheet = true
                }) {
                HStack(spacing: 7) {
                    Image(systemName: "play.circle.fill")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))

                    Text("Playlist")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)

                    if !playlistStore.items.isEmpty {
                        Text("\(playlistStore.items.count)")
                            .font(.system(size: 11, weight: .black))
                            .foregroundColor(Color(red: 0.03, green: 0.04, blue: 0.08))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(
                                Capsule()
                                    .fill(Color(red: 0.06, green: 0.85, blue: 0.58))
                            )
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(
                    Capsule()
                        .fill(.ultraThinMaterial)
                        .overlay(
                            Capsule()
                                .fill(Color(red: 0.07, green: 0.10, blue: 0.18).opacity(0.75))
                        )
                )
                .overlay(
                    Capsule()
                        .stroke(
                            LinearGradient(
                                colors: [Color.white.opacity(0.35), Color.white.opacity(0.1)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1
                        )
                )
                .shadow(color: Color.black.opacity(0.35), radius: 10, x: 0, y: 5)
            }

            // Discreet Settings Icon Button
            Button(action: {
                showingSettingsSheet = true
            }) {
                Image(systemName: "gearshape.fill")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.white.opacity(0.85))
                    .frame(width: 34, height: 34)
                    .background(
                        Circle()
                            .fill(.ultraThinMaterial)
                            .overlay(
                                Circle()
                                    .fill(Color(red: 0.07, green: 0.10, blue: 0.18).opacity(0.75))
                            )
                    )
                    .overlay(
                        Circle()
                            .stroke(Color.white.opacity(0.2), lineWidth: 1)
                    )
                    .shadow(color: Color.black.opacity(0.3), radius: 8, x: 0, y: 4)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 14)
        .padding(.bottom, 10)
    }
    }

    // MARK: - Luxury Media Site Card List
    private var siteListView: some View {
        ScrollView {
            LazyVStack(spacing: 14) {
                ForEach(siteStore.sites) { site in
                    Button(action: {
                        tabStore.openTab(url: site.url, title: site.name, siteId: site.id)
                        activeSite = site
                    }) {
                        HStack(spacing: 14) {
                            // Favicon in squircle glass well
                            AsyncImage(url: URL(string: site.iconUrl)) { phase in
                                switch phase {
                                case .success(let image):
                                    image
                                        .resizable()
                                        .aspectRatio(contentMode: .fit)
                                        .frame(width: 28, height: 28)
                                        .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                                default:
                                    Image(systemName: "globe")
                                        .font(.system(size: 22))
                                        .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                                }
                            }
                            .frame(width: 48, height: 48)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(
                                        LinearGradient(
                                            colors: [Color.white.opacity(0.18), Color.white.opacity(0.06)],
                                            startPoint: .topLeading,
                                            endPoint: .bottomTrailing
                                        )
                                    )
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                                            .stroke(
                                                LinearGradient(
                                                    colors: [Color.white.opacity(0.4), Color.white.opacity(0.1)],
                                                    startPoint: .topLeading,
                                                    endPoint: .bottomTrailing
                                                ),
                                                lineWidth: 1
                                            )
                                    )
                            )
                            .shadow(color: Color.black.opacity(0.2), radius: 6, x: 0, y: 3)

                            // Title & Host Domain
                            VStack(alignment: .leading, spacing: 4) {
                                Text(site.name)
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.white)
                                    .lineLimit(1)

                                HStack(spacing: 4) {
                                    Image(systemName: "network")
                                        .font(.system(size: 11))
                                        .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))

                                    Text(site.hostDomain)
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundColor(Color.white.opacity(0.55))
                                        .lineLimit(1)
                                }
                            }

                            Spacer()

                            // Forward Chevron
                            Image(systemName: "chevron.right")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white.opacity(0.25))
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 14)
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
                                                colors: [
                                                    Color.white.opacity(0.32),
                                                    Color.white.opacity(0.08),
                                                    Color.white.opacity(0.01)
                                                ],
                                                startPoint: .topLeading,
                                                endPoint: .bottomTrailing
                                            ),
                                            lineWidth: 1
                                        )
                                )
                                .shadow(color: Color.black.opacity(0.35), radius: 14, x: 0, y: 7)
                        )
                    }
                    .contextMenu {
                        Button {
                            siteToEdit = site
                        } label: {
                            Label("Edit", systemImage: "pencil")
                        }

                        Button(role: .destructive) {
                            siteToDelete = site
                            showingDeleteConfirmation = true
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
            .padding(.horizontal, 18)
            .padding(.top, 8)
            .padding(.bottom, 96)
        }
        .refreshable {
            siteStore.syncWithRemote()
            playlistStore.syncWithRemote()
        }
    }

    // MARK: - Empty State
    private var emptyStateView: some View {
        VStack(spacing: 20) {
            Spacer()

            ZStack {
                Circle()
                    .fill(Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.12))
                    .frame(width: 88, height: 88)
                    .overlay(
                        Circle()
                            .stroke(Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.3), lineWidth: 1.5)
                    )
                    .shadow(color: Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.4), radius: 20, x: 0, y: 8)

                Image(systemName: "safari.fill")
                    .font(.system(size: 44))
                    .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
            }

            Text("Your Private Media Hub")
                .font(.system(size: 22, weight: .black, design: .rounded))
                .foregroundColor(.white)
                .tracking(-0.5)

            Text("Add websites with media to experience fluid, gesture-driven controls and instant playlist sync.")
                .font(.system(size: 14))
                .foregroundColor(.white.opacity(0.55))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
                .lineSpacing(4)

            Button(action: {
                showingAddSheet = true
            }) {
                Label("Add First Website", systemImage: "plus")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(Color(red: 0.03, green: 0.04, blue: 0.08))
                    .padding(.horizontal, 24)
                    .padding(.vertical, 14)
                    .background(
                        Capsule()
                            .fill(
                                LinearGradient(
                                    colors: [
                                        Color(red: 0.06, green: 0.85, blue: 0.58),
                                        Color(red: 0.04, green: 0.65, blue: 0.45)
                                    ],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .shadow(color: Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.45), radius: 12, x: 0, y: 5)
                    )
            }
            .padding(.top, 10)

            Spacer()
        }
    }
}
