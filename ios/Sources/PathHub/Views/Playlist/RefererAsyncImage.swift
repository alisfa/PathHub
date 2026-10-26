import SwiftUI
import UIKit

public struct RefererAsyncImage<Content: View, Placeholder: View>: View {
    let url: URL?
    let referer: String
    @ViewBuilder let content: (Image) -> Content
    @ViewBuilder let placeholder: () -> Placeholder

    @State private var loadedImage: UIImage? = nil
    @State private var isLoading = false

    public init(
        url: URL?,
        referer: String = "https://www.pornhub.com/",
        @ViewBuilder content: @escaping (Image) -> Content,
        @ViewBuilder placeholder: @escaping () -> Placeholder
    ) {
        self.url = url
        self.referer = referer
        self.content = content
        self.placeholder = placeholder
    }

    public var body: some View {
        Group {
            if let img = loadedImage {
                content(Image(uiImage: img))
            } else {
                placeholder()
                    .task(id: url) {
                        await loadImage()
                    }
            }
        }
    }

    private func loadImage() async {
        guard let url = url, !isLoading else { return }
        let cacheKey = url.absoluteString as NSString
        if let cached = ImageCache.shared.object(forKey: cacheKey) {
            self.loadedImage = cached
            return
        }

        isLoading = true
        var request = URLRequest(url: url)
        request.setValue(referer, forHTTPHeaderField: "Referer")
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 10

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode), let uiImg = UIImage(data: data) {
                ImageCache.shared.setObject(uiImg, forKey: cacheKey)
                self.loadedImage = uiImg
                isLoading = false
                return
            }
        } catch {
            // Standard resolution failed; attempt DoH anti-block resolution
        }

        // DoH Anti-Block Fallback for censored CDN hostnames (e.g. phncdn.com in Iraq)
        if let host = url.host, let resolvedIp = await DnsOverHttpsResolver.shared.resolve(hostname: host) {
            var dohComponents = URLComponents(url: url, resolvingAgainstBaseURL: false)
            dohComponents?.host = resolvedIp
            if let dohUrl = dohComponents?.url {
                var dohReq = URLRequest(url: dohUrl)
                dohReq.setValue(referer, forHTTPHeaderField: "Referer")
                dohReq.setValue(host, forHTTPHeaderField: "Host")
                dohReq.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
                dohReq.timeoutInterval = 10
                if let (dohData, dohResp) = try? await URLSession.shared.data(for: dohReq),
                   let http = dohResp as? HTTPURLResponse, (200...299).contains(http.statusCode),
                   let uiImg = UIImage(data: dohData) {
                    ImageCache.shared.setObject(uiImg, forKey: cacheKey)
                    self.loadedImage = uiImg
                }
            }
        }
        isLoading = false
    }
}

private final class ImageCache: @unchecked Sendable {
    nonisolated(unsafe) static let shared = NSCache<NSString, UIImage>()
}
