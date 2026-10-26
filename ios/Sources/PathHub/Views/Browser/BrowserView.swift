import SwiftUI
import WebKit

public struct BrowserView: View {
    @Environment(\.dismiss) private var dismiss

    let initialUrl: URL
    let siteId: String
    @ObservedObject var playlistStore: PlaylistStore
    @ObservedObject var tabStore: TabStore = TabStore.shared

    @State private var canGoBack: Bool = false
    @State private var canGoForward: Bool = false
    @State private var isLoading: Bool = false
    @State private var currentTitle: String = ""
    @State private var isFullscreen: Bool = false
    @State private var isBarCollapsed: Bool = false
    @State private var webView: WKWebView? = nil
    @State private var showPlaylistSheet: Bool = false
    @State private var showTabsSheet: Bool = false
    @State private var showUrlSheet: Bool = false
    @State private var urlInputText: String = ""
    @State private var selectedVideoItem: PlaylistItem? = nil
    @State private var isSavedCurrent: Bool = false

    private var currentHostDomain: String {
        if let host = webView?.url?.host {
            return host.replacingOccurrences(of: "www.", with: "")
        }
        return initialUrl.host?.replacingOccurrences(of: "www.", with: "") ?? "Web"
    }

    private func navigateToUrl(_ rawUrl: String) {
        let trimmed = rawUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return }
        let formatted: String
        if trimmed.hasPrefix("http://") || trimmed.hasPrefix("https://") {
            formatted = trimmed
        } else if trimmed.contains(".") && !trimmed.contains(" ") {
            formatted = "https://\(trimmed)"
        } else {
            let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? trimmed
            formatted = "https://www.google.com/search?q=\(encoded)"
        }
        let resolved = VpnManager.shared.resolveUrl(formatted)
        if let targetUrl = URL(string: resolved) {
            webView?.load(URLRequest(url: targetUrl))
            tabStore.updateActiveTab(url: resolved, title: "Loading...")
        }
    }

    private func pasteAndGo() {
        if let clip = UIPasteboard.general.string, !clip.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            navigateToUrl(clip)
            showUrlSheet = false
        }
    }

    public init(url: URL, siteId: String, playlistStore: PlaylistStore) {
        self.initialUrl = url
        self.siteId = siteId
        self.playlistStore = playlistStore
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            // Fullscreen WebView
            WebViewContainer(
                url: initialUrl,
                siteId: siteId,
                canGoBack: $canGoBack,
                canGoForward: $canGoForward,
                isLoading: $isLoading,
                currentTitleString: $currentTitle,
                isFullscreen: $isFullscreen,
                webViewRef: $webView,
                playlistStore: playlistStore,
                onFullscreenChanged: { isFS in
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                        self.isFullscreen = isFS
                    }
                }
            )
            .ignoresSafeArea(edges: .all)

            // Top Liquid Glass Progress Bar
            if isLoading && !isFullscreen {
                VStack {
                    LinearProgressView()
                        .frame(height: 3)
                        .padding(.top, 0)
                    Spacer()
                }
                .transition(.opacity)
            }

            // Top Floating Address / Domain Pill (Tap to paste link or enter URL)
            if !isFullscreen && !isBarCollapsed {
                VStack {
                    Button(action: {
                        urlInputText = webView?.url?.absoluteString ?? initialUrl.absoluteString
                        showUrlSheet = true
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "lock.fill")
                                .font(.system(size: 10))
                                .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                            Text(currentHostDomain)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(.white.opacity(0.85))
                                .lineLimit(1)
                            Image(systemName: "doc.on.clipboard")
                                .font(.system(size: 10))
                                .foregroundColor(.white.opacity(0.5))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background(
                            Capsule()
                                .fill(Color(red: 0.03, green: 0.05, blue: 0.10).opacity(0.85))
                                .overlay(Capsule().stroke(Color.white.opacity(0.18), lineWidth: 1))
                        )
                        .shadow(color: Color.black.opacity(0.4), radius: 8, x: 0, y: 3)
                    }
                    .padding(.top, 10)
                    Spacer()
                }
            }

            // Bottom Navigation Controls
            if !isFullscreen {
                if !isBarCollapsed {
                    // Full Floating Liquid Glass Bottom Navbar
                    liquidGlassBottomBar
                        .padding(.horizontal, 18)
                        .padding(.bottom, 18)
                        .zIndex(100)
                        .gesture(
                            DragGesture(minimumDistance: 15)
                                .onEnded { value in
                                    if value.translation.height > 20 {
                                        withAnimation(.spring(response: 0.35, dampingFraction: 0.82)) {
                                            isBarCollapsed = true
                                        }
                                    }
                                }
                        )
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                } else {
                    // Smaller Mini-Pill that sits lower in the screen
                    lowerCompactMiniPill
                }
            }
        }
        .navigationBarHidden(true)
        .statusBarHidden(isFullscreen)
        .sheet(isPresented: $showPlaylistSheet) {
            PlaylistView(playlistStore: playlistStore, selectedVideoItem: $selectedVideoItem)
        }
        .sheet(isPresented: $showUrlSheet) {
            UrlNavigatorSheet(
                initialText: urlInputText,
                onNavigate: { target in
                    navigateToUrl(target)
                },
                onPasteAndGo: {
                    pasteAndGo()
                }
            )
        }
        .sheet(isPresented: $showTabsSheet) {
            TabsSwitcherSheet(
                tabStore: tabStore,
                onTabSelected: { tab in
                    let resolved = VpnManager.shared.resolveUrl(tab.url)
                    if let targetUrl = URL(string: resolved) {
                        webView?.load(URLRequest(url: targetUrl))
                    }
                },
                onNewTabRequested: {
                    let target = VpnManager.shared.resolveUrl("https://www.pornhub.com")
                    let newTab = tabStore.openTab(url: target, title: "Pornhub")
                    if let targetUrl = URL(string: newTab.url) {
                        webView?.load(URLRequest(url: targetUrl))
                    }
                }
            )
        }
        .onAppear {
            if tabStore.tabs.isEmpty {
                let resolved = VpnManager.shared.resolveUrl(initialUrl.absoluteString)
                tabStore.openTab(url: resolved, title: "Web Tab", siteId: siteId)
            }
        }
        .onChange(of: currentTitle) { _, newTitle in
            if let currentUrl = webView?.url?.absoluteString {
                tabStore.updateActiveTab(url: currentUrl, title: newTitle)
            }
        }
        .onChange(of: selectedVideoItem) { _, newItem in
            if let item = newItem {
                let rawUrl = item.cleanPageUrl.isEmpty ? item.videoUrl : item.cleanPageUrl
                let resolved = VpnManager.shared.resolveUrl(rawUrl)
                if let targetUrl = URL(string: resolved) {
                    webView?.load(URLRequest(url: targetUrl))
                    selectedVideoItem = nil
                }
            }
        }
    }

    // MARK: - Full Floating Liquid Glass Bottom Bar
    private var liquidGlassBottomBar: some View {
        HStack(spacing: 0) {
            // Back Button
            Button(action: {
                webView?.goBack()
            }) {
                Image(systemName: "chevron.backward")
                    .font(.system(size: 16, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .frame(height: 54)
                    .foregroundColor(canGoBack ? .white : .white.opacity(0.25))
            }
            .disabled(!canGoBack)

            // Forward Button
            Button(action: {
                webView?.goForward()
            }) {
                Image(systemName: "chevron.forward")
                    .font(.system(size: 16, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .frame(height: 54)
                    .foregroundColor(canGoForward ? .white : .white.opacity(0.25))
            }
            .disabled(!canGoForward)

            // Home Button
            Button(action: {
                dismiss()
            }) {
                Image(systemName: "house.fill")
                    .font(.system(size: 17, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .frame(height: 54)
                    .foregroundColor(.white.opacity(0.9))
            }

            // Upgraded Jewel "Add Video" Button
            Button(action: {
                triggerSaveVideo()
            }) {
                ZStack {
                    Circle()
                        .fill(
                            isSavedCurrent
                                ? LinearGradient(
                                    colors: [
                                        Color(red: 1.0, green: 0.22, blue: 0.38),
                                        Color(red: 0.85, green: 0.08, blue: 0.28)
                                    ],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                                : LinearGradient(
                                    colors: [
                                        Color(red: 0.06, green: 0.85, blue: 0.58),
                                        Color(red: 0.04, green: 0.62, blue: 0.44)
                                    ],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                        )
                        .frame(width: 42, height: 42)
                        .overlay(
                            Circle()
                                .stroke(
                                    LinearGradient(
                                        colors: [Color.white.opacity(0.7), Color.white.opacity(0.18)],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    ),
                                    lineWidth: 1
                                )
                        )
                        .shadow(
                            color: isSavedCurrent
                                ? Color(red: 1.0, green: 0.22, blue: 0.38).opacity(0.65)
                                : Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.55),
                            radius: 12,
                            x: 0,
                            y: 4
                        )

                    Image(systemName: isSavedCurrent ? "heart.fill" : "plus")
                        .font(.system(size: 18, weight: .heavy))
                        .foregroundColor(.white)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 54)
            }

            // Enter / Paste URL Button
            Button(action: {
                urlInputText = webView?.url?.absoluteString ?? initialUrl.absoluteString
                showUrlSheet = true
            }) {
                Image(systemName: "link")
                    .font(.system(size: 17, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .frame(height: 54)
                    .foregroundColor(.white.opacity(0.9))
            }

            // Playlist Button
            Button(action: {
                showPlaylistSheet = true
            }) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: "play.rectangle.on.rectangle.fill")
                        .font(.system(size: 17, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                        .foregroundColor(.white.opacity(0.9))

                    if !playlistStore.items.isEmpty {
                        Circle()
                            .fill(Color(red: 1.0, green: 0.22, blue: 0.38))
                            .frame(width: 7, height: 7)
                            .offset(x: -12, y: 14)
                            .shadow(color: Color(red: 1.0, green: 0.22, blue: 0.38).opacity(0.6), radius: 4)
                    }
                }
            }

            // Tabs Switcher Button
            Button(action: {
                showTabsSheet = true
            }) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: "square.on.square")
                        .font(.system(size: 16, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                        .foregroundColor(.white.opacity(0.9))

                    if tabStore.tabs.count > 0 {
                        Text("\(tabStore.tabs.count)")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(Color(red: 0.03, green: 0.04, blue: 0.08))
                            .padding(.horizontal, 4)
                            .padding(.vertical, 1)
                            .background(
                                Capsule()
                                    .fill(Color(red: 0.06, green: 0.85, blue: 0.58))
                            )
                            .offset(x: -8, y: 12)
                    }
                }
            }

            // Dedicated Collapse / Hide Button
            Button(action: {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.82)) {
                    isBarCollapsed = true
                }
            }) {
                Image(systemName: "chevron.down")
                    .font(.system(size: 14, weight: .bold))
                    .frame(maxWidth: .infinity)
                    .frame(height: 54)
                    .foregroundColor(.white.opacity(0.45))
            }
        }
        .padding(.horizontal, 6)
        .background(
            Capsule()
                .fill(.ultraThinMaterial)
                .overlay(
                    Capsule()
                        .fill(Color(red: 0.07, green: 0.10, blue: 0.18).opacity(0.85))
                )
                .overlay(
                    Capsule()
                        .stroke(
                            LinearGradient(
                                colors: [
                                    Color.white.opacity(0.35),
                                    Color.white.opacity(0.12),
                                    Color.white.opacity(0.02)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1
                        )
                )
                .shadow(color: Color.black.opacity(0.45), radius: 24, x: 0, y: 10)
                .shadow(color: Color.black.opacity(0.2), radius: 5, x: 0, y: 2)
        )
        .frame(height: 56)
    }

    // MARK: - Lower Compact Mini-Pill (Sits lower in the screen)
    private var lowerCompactMiniPill: some View {
        Button(action: {
            withAnimation(.spring(response: 0.35, dampingFraction: 0.82)) {
                isBarCollapsed = false
            }
        }) {
            HStack(spacing: 8) {
                Image(systemName: "chevron.up")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.white.opacity(0.85))

                Circle()
                    .fill(isSavedCurrent ? Color(red: 1.0, green: 0.22, blue: 0.38) : Color(red: 0.06, green: 0.85, blue: 0.58))
                    .frame(width: 6, height: 6)

                Text(isSavedCurrent ? "Saved ❤️" : "Controls")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.white.opacity(0.9))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 7)
            .background(
                Capsule()
                    .fill(.ultraThinMaterial)
                    .overlay(
                        Capsule()
                            .fill(Color(red: 0.07, green: 0.10, blue: 0.18).opacity(0.85))
                    )
            )
            .overlay(
                Capsule()
                    .stroke(
                        LinearGradient(
                            colors: [Color.white.opacity(0.4), Color.white.opacity(0.1)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1
                    )
            )
            .shadow(color: Color.black.opacity(0.4), radius: 12, x: 0, y: 6)
        }
        .padding(.bottom, 6) // Sits lower in the screen right above home indicator!
        .zIndex(100)
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    private func triggerSaveVideo() {
        let script = "if (window.__pathHubSaveCurrentVideo) { window.__pathHubSaveCurrentVideo(); }"
        webView?.evaluateJavaScript(script) { res, _ in
            if (res as? Bool) == true {
                isSavedCurrent = true
            }
        }
    }
}

// Custom Liquid Glass Progress View
struct LinearProgressView: View {
    @State private var phase: CGFloat = 0

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Rectangle()
                    .fill(Color.white.opacity(0.08))

                Rectangle()
                    .fill(
                        LinearGradient(
                            colors: [
                                Color(red: 0.06, green: 0.85, blue: 0.58),
                                Color(red: 0.02, green: 0.71, blue: 0.83),
                                Color(red: 0.23, green: 0.51, blue: 0.96)
                            ],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .frame(width: geo.size.width * 0.75)
                    .offset(x: phase * geo.size.width)
            }
            .clipShape(Capsule())
            .onAppear {
                withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) {
                    phase = 1
                }
            }
        }
    }
}

// Separate Sheet Component for URL Navigation & Pasting Links
struct UrlNavigatorSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var inputText: String
    let onNavigate: (String) -> Void
    let onPasteAndGo: () -> Void

    init(initialText: String, onNavigate: @escaping (String) -> Void, onPasteAndGo: @escaping () -> Void) {
        self._inputText = State(initialValue: initialText)
        self.onNavigate = onNavigate
        self.onPasteAndGo = onPasteAndGo
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color(red: 0.03, green: 0.04, blue: 0.08)
                    .ignoresSafeArea()

                VStack(spacing: 16) {
                    // Quick 1-Tap Paste & Go Button
                    Button(action: {
                        onPasteAndGo()
                        dismiss()
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "doc.on.clipboard.fill")
                                .font(.system(size: 15, weight: .bold))
                            Text("Paste from Clipboard & Go")
                                .font(.system(size: 15, weight: .bold))
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
                    .padding(.horizontal, 20)
                    .padding(.top, 10)

                    // URL Input Field
                    HStack(spacing: 10) {
                        Image(systemName: "link")
                            .font(.system(size: 14))
                            .foregroundColor(Color.white.opacity(0.5))

                        TextField("https://example.com/video...", text: $inputText)
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(.white)
                            .autocorrectionDisabled(true)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.URL)
                            .onSubmit {
                                if !inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                    onNavigate(inputText)
                                    dismiss()
                                }
                            }

                        if !inputText.isEmpty {
                            Button(action: { inputText = "" }) {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.system(size: 14))
                                    .foregroundColor(.white.opacity(0.4))
                            }
                        }
                    }
                    .padding(14)
                    .background(
                        RoundedRectangle(cornerRadius: 16)
                            .fill(Color(red: 0.08, green: 0.11, blue: 0.18))
                            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.15), lineWidth: 1))
                    )
                    .padding(.horizontal, 20)

                    // Quick Site Shortcuts
                    HStack(spacing: 10) {
                        ForEach([
                            ("Pornhub", "https://www.pornhub.com"),
                            ("XNXX", "https://www.xnxx.com"),
                            ("Xvideos", "https://www.xvideos.com")
                        ], id: \.0) { name, link in
                            Button(action: {
                                onNavigate(link)
                                dismiss()
                            }) {
                                Text(name)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(.white.opacity(0.85))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(
                                        RoundedRectangle(cornerRadius: 12)
                                            .fill(Color.white.opacity(0.06))
                                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.white.opacity(0.12), lineWidth: 1))
                                    )
                            }
                        }
                    }
                    .padding(.horizontal, 20)

                    // Go Button
                    Button(action: {
                        if !inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            onNavigate(inputText)
                            dismiss()
                        }
                    }) {
                        Text("Go to URL")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(
                                Capsule()
                                    .fill(Color.white.opacity(0.12))
                                    .overlay(Capsule().stroke(Color.white.opacity(0.2), lineWidth: 1))
                            )
                    }
                    .disabled(inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    .padding(.horizontal, 20)

                    Spacer()
                }
            }
            .navigationTitle("Enter or Paste Link")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                        .foregroundColor(.white.opacity(0.8))
                }
            }
        }
        .presentationDetents([.fraction(0.55), .medium])
    }
}
