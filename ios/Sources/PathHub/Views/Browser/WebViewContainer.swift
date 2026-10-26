import SwiftUI
import WebKit

@MainActor
public struct WebViewContainer: UIViewRepresentable {
    let url: URL
    let siteId: String
    @Binding var canGoBack: Bool
    @Binding var canGoForward: Bool
    @Binding var isLoading: Bool
    @Binding var currentTitle: String
    @Binding var isFullscreen: Bool
    @Binding var webViewRef: WKWebView?

    let playlistStore: PlaylistStore
    let onFullscreenChanged: (Bool) -> Void

    // Cache of domain-isolated website data stores
    private static var dataStores: [String: WKWebsiteDataStore] = [:]

    public init(
        url: URL,
        siteId: String,
        canGoBack: Binding<Bool>,
        canGoForward: Binding<Bool>,
        isLoading: Binding<Bool>,
        currentTitleString: Binding<String>,
        isFullscreen: Binding<Bool>,
        webViewRef: Binding<WKWebView?>,
        playlistStore: PlaylistStore,
        onFullscreenChanged: @escaping (Bool) -> Void = { _ in }
    ) {
        self.url = url
        self.siteId = siteId
        self._canGoBack = canGoBack
        self._canGoForward = canGoForward
        self._isLoading = isLoading
        self._currentTitle = currentTitleString
        self._isFullscreen = isFullscreen
        self._webViewRef = webViewRef
        self.playlistStore = playlistStore
        self.onFullscreenChanged = onFullscreenChanged
    }

    private static func getOrCreateDataStore(for domain: String) -> WKWebsiteDataStore {
        if let existing = dataStores[domain] {
            return existing
        }
        let store = WKWebsiteDataStore.nonPersistent()
        dataStores[domain] = store
        return store
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator(self, playlistStore: playlistStore, onFullscreenChanged: onFullscreenChanged)
    }

    public func makeUIView(context: Context) -> WKWebView {
        let host = url.host ?? "default"
        let dataStore = Self.getOrCreateDataStore(for: host)

        let config = WKWebViewConfiguration()
        config.websiteDataStore = dataStore
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        config.preferences.isElementFullscreenEnabled = true
        config.preferences.javaScriptCanOpenWindowsAutomatically = false

        let userContent = WKUserContentController()
        let coordinator = context.coordinator

        userContent.add(coordinator, name: "saveVideo")
        userContent.add(coordinator, name: "isSaved")
        userContent.add(coordinator, name: "setBrightness")
        userContent.add(coordinator, name: "setVolume")
        userContent.add(coordinator, name: "enterFullscreen")
        userContent.add(coordinator, name: "exitFullscreen")
        userContent.add(coordinator, name: "debugLog")

        // 1. Inject player.css at document start
        let css = InjectedScripts.playerCSS
        let cssInjection = """
        (function() {
            var el = document.createElement('style');
            el.id = 'pathhub-player-styles';
            el.textContent = \(JSONString(from: css));
            (document.head || document.documentElement).appendChild(el);
        })();
        """
        let cssScript = WKUserScript(source: cssInjection, injectionTime: .atDocumentStart, forMainFrameOnly: false)
        userContent.addUserScript(cssScript)

        // 2. Inject player.js at document start
        let js = InjectedScripts.playerJS
        let jsScript = WKUserScript(source: js, injectionTime: .atDocumentStart, forMainFrameOnly: false)
        userContent.addUserScript(jsScript)

        config.userContentController = userContent

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = coordinator
        webView.uiDelegate = coordinator
        webView.allowsBackForwardNavigationGestures = true
        coordinator.webView = webView

        DispatchQueue.main.async {
            self.webViewRef = webView
        }

        let initialResolved = VpnManager.shared.resolveUrl(url.absoluteString)
        let initialTarget = URL(string: initialResolved) ?? url
        webView.load(URLRequest(url: initialTarget))
        return webView
    }

    public func updateUIView(_ uiView: WKWebView, context: Context) {}

    public static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        coordinator.cleanup()
        uiView.configuration.userContentController.removeAllScriptMessageHandlers()
        uiView.stopLoading()
        uiView.navigationDelegate = nil
        uiView.uiDelegate = nil
    }

    private func JSONString(from string: String) -> String {
        if let data = try? JSONSerialization.data(withJSONObject: [string]),
           let json = String(data: data, encoding: .utf8) {
            return String(json.dropFirst().dropLast())
        }
        return "\"\""
    }

    public class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
        var parent: WebViewContainer
        let playlistStore: PlaylistStore
        let onFullscreenChanged: (Bool) -> Void
        weak var webView: WKWebView?
        nonisolated(unsafe) private var checkTimer: Timer?
        private var lastState: Bool = false

        init(_ parent: WebViewContainer, playlistStore: PlaylistStore, onFullscreenChanged: @escaping (Bool) -> Void) {
            self.parent = parent
            self.playlistStore = playlistStore
            self.onFullscreenChanged = onFullscreenChanged
            super.init()

            NotificationCenter.default.addObserver(self, selector: #selector(windowDidBecomeVisible(_:)), name: UIWindow.didBecomeVisibleNotification, object: nil)
            NotificationCenter.default.addObserver(self, selector: #selector(windowDidBecomeHidden(_:)), name: UIWindow.didBecomeHiddenNotification, object: nil)

            // Periodic inspection for fullscreen AVPlayer / WebKit presentation controller
            self.checkTimer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
                Task { @MainActor [weak self] in
                    self?.inspectFullscreenState()
                }
            }
        }

        @MainActor
        private func inspectFullscreenState() {
            guard let wv = self.webView else { return }
            var isFS = false

            // Walk the presentation chain to inspect top-most presented controllers
            var topController = wv.window?.rootViewController
            while let next = topController?.presentedViewController {
                let name = String(describing: type(of: next))
                // Only count true video playback/fullscreen controllers, NEVER UIHostingController
                if name.contains("AVPlayer") || name.contains("AVFullScreen") || name.contains("AVPlayback") || name.contains("WKVideoFullScreen") {
                    isFS = true
                    break
                }
                topController = next
            }

            // Check separate overlay windows (e.g. AVFullScreenWindow)
            if !isFS, let windowScene = wv.window?.windowScene {
                for w in windowScene.windows {
                    if w != wv.window {
                        let name = String(describing: type(of: w))
                        if (name.contains("Fullscreen") || name.contains("AV")) && !w.isHidden && w.alpha > 0.05 {
                            isFS = true
                            break
                        }
                    }
                }
            }

            self.setFullscreenState(isFS)
        }

        @MainActor
        private func setFullscreenState(_ isFS: Bool) {
            if self.lastState != isFS {
                self.lastState = isFS
                self.parent.isFullscreen = isFS
                self.onFullscreenChanged(isFS)
            }
        }

        nonisolated func cleanup() {
            NotificationCenter.default.removeObserver(self)
            checkTimer?.invalidate()
        }

        deinit {
            cleanup()
        }

        @objc func windowDidBecomeVisible(_ notification: Notification) {
            if let window = notification.object as? UIWindow, window != self.webView?.window {
                let className = String(describing: type(of: window))
                if className.contains("Fullscreen") || className.contains("AV") {
                    self.setFullscreenState(true)
                }
            }
        }

        @objc func windowDidBecomeHidden(_ notification: Notification) {
            if let window = notification.object as? UIWindow, window != self.webView?.window {
                let className = String(describing: type(of: window))
                if className.contains("Fullscreen") || className.contains("AV") {
                    self.setFullscreenState(false)
                }
            }
        }

        public func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            switch message.name {
            case "saveVideo":
                if let dict = message.body as? [String: Any] {
                    DispatchQueue.main.async {
                        self.playlistStore.saveVideo(from: dict)
                    }
                }

            case "isSaved":
                if let dict = message.body as? [String: Any],
                   let videoUrl = dict["videoUrl"] as? String,
                   let callbackId = dict["callbackId"] as? String {
                    let isSaved = self.playlistStore.isSaved(videoUrl: videoUrl)
                    let script = "window.__pathHubReceiveSavedStatus('\(callbackId)', \(isSaved));"
                    DispatchQueue.main.async {
                        message.webView?.evaluateJavaScript(script, completionHandler: nil)
                    }
                }

            case "setBrightness":
                if let dict = message.body as? [String: Any],
                   let val = dict["brightness"] as? Double {
                    DispatchQueue.main.async {
                        UIScreen.main.brightness = CGFloat(val)
                    }
                }

            case "setVolume":
                break

            case "enterFullscreen":
                self.setFullscreenState(true)

            case "exitFullscreen":
                self.setFullscreenState(false)

            case "debugLog":
                if let dict = message.body as? [String: Any],
                   let msg = dict["message"] as? String {
                    print("[PathHub Injected Debug]: \(msg)")
                }

            default:
                break
            }
        }

        // MARK: - Popup Ad Blocking & Navigation Delegate
        public func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
            let targetUrl = navigationAction.request.url
            let currentUrl = webView.url ?? self.parent.url
            // Block popup ads or popup redirects
            if AdBlocker.isAdUrl(targetUrl) || AdBlocker.isCrossDomainAdRedirect(currentUrl: currentUrl, targetUrl: targetUrl) {
                print("[PathHub AdBlocker] Blocked popup window: \(targetUrl?.absoluteString ?? "")")
                return nil
            }

            // If user clicked a real link that wanted a new window, load in current webview
            if navigationAction.targetFrame == nil {
                if let url = targetUrl, !AdBlocker.isAdUrl(url) && !AdBlocker.isCrossDomainAdRedirect(currentUrl: currentUrl, targetUrl: url) {
                    webView.load(navigationAction.request)
                }
            }
            return nil
        }

        @MainActor
        public func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping @MainActor @Sendable (WKNavigationActionPolicy) -> Void) {
            if let targetUrl = navigationAction.request.url {
                let currentUrl = webView.url ?? self.parent.url
                if AdBlocker.isAdUrl(targetUrl) || AdBlocker.isCrossDomainAdRedirect(currentUrl: currentUrl, targetUrl: targetUrl) {
                    print("[PathHub AdBlocker] Blocked ad navigation: \(targetUrl.absoluteString)")
                    decisionHandler(.cancel)
                    return
                }

                let resolved = VpnManager.shared.resolveUrl(targetUrl.absoluteString)
                if resolved != targetUrl.absoluteString, let newUrl = URL(string: resolved) {
                    decisionHandler(.cancel)
                    webView.load(URLRequest(url: newUrl))
                    return
                }
            }
            decisionHandler(.allow)
        }

        public func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            parent.isLoading = true
        }

        public func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            parent.isLoading = false
            parent.canGoBack = webView.canGoBack
            parent.canGoForward = webView.canGoForward
            parent.currentTitle = webView.title ?? ""

            if let currentUrl = webView.url?.absoluteString, !currentUrl.isEmpty, !currentUrl.starts(with: "about:") {
                HistoryStore.shared.recordVisit(url: currentUrl, title: webView.title ?? "")
            }
        }

        public func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            parent.isLoading = false
            parent.canGoBack = webView.canGoBack
            parent.canGoForward = webView.canGoForward
        }

        public func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            parent.isLoading = false
            parent.canGoBack = webView.canGoBack
            parent.canGoForward = webView.canGoForward
            let nsError = error as NSError
            print("[PathHub WebView] Provisional navigation failed: \(error.localizedDescription) (code: \(nsError.code))")
            if nsError.domain == NSURLErrorDomain && (nsError.code == NSURLErrorCannotFindHost || nsError.code == NSURLErrorCannotConnectToHost || nsError.code == NSURLErrorTimedOut) {
                if let failedUrl = nsError.userInfo[NSURLErrorFailingURLStringErrorKey] as? String ?? webView.url?.absoluteString {
                    let resolved = VpnManager.shared.resolveUrl(failedUrl)
                    if resolved != failedUrl, let target = URL(string: resolved) {
                        webView.load(URLRequest(url: target))
                    }
                }
            }
        }
    }
}
