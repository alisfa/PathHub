import Foundation
import UIKit
import SwiftUI
import Combine

public struct UpdateInfo: Identifiable, Sendable {
    public var id: String { tagName }
    public let tagName: String
    public let releaseName: String
    public let changelog: String
    public let downloadUrl: URL
    public let fileName: String
    public let fileSize: Int64
    public let htmlUrl: URL
}

@MainActor
public final class UpdateService: NSObject, ObservableObject, URLSessionDownloadDelegate {
    public static let shared = UpdateService()

    public static let defaultRepo = "alisfa/PathHub"
    public static let compileVersion = "1.0.4"
    private let repoKey = "pathhub_github_repo"

    @Published public var availableUpdate: UpdateInfo? = nil
    @Published public var isChecking: Bool = false
    @Published public var isDownloading: Bool = false
    @Published public var downloadProgress: Double = 0.0
    @Published public var downloadedBytes: Int64 = 0
    @Published public var totalBytes: Int64 = 0
    @Published public var localIpaUrl: URL? = nil
    @Published public var errorMessage: String? = nil

    private var downloadContinuation: CheckedContinuation<URL, Error>?
    private lazy var urlSession: URLSession = {
        let config = URLSessionConfiguration.default
        return URLSession(configuration: config, delegate: self, delegateQueue: nil)
    }()

    private override init() {
        super.init()
    }

    public func getRepo() -> String {
        return UserDefaults.standard.string(forKey: repoKey) ?? Self.defaultRepo
    }

    public func setRepo(_ repo: String) {
        UserDefaults.standard.set(repo.trimmingCharacters(in: .whitespacesAndNewlines), forKey: repoKey)
    }

    public func getCurrentVersion() -> String {
        if let bundleVer = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
           bundleVer != "1.0.0" && !bundleVer.isEmpty {
            return bundleVer
        }
        return Self.compileVersion
    }

    public static func compareVersions(_ remote: String, _ current: String) -> ComparisonResult {
        func normalize(_ v: String) -> [Int] {
            let cleaned = v.trimmingCharacters(in: .whitespacesAndNewlines)
                .replacingOccurrences(of: "v", with: "")
                .replacingOccurrences(of: "V", with: "")
            return cleaned.split { $0 == "." || $0 == "-" || $0 == "_" }
                .compactMap { Int($0) }
        }

        let rParts = normalize(remote)
        let cParts = normalize(current)
        let maxLen = max(rParts.count, cParts.count)

        for i in 0..<maxLen {
            let r = i < rParts.count ? rParts[i] : 0
            let c = i < cParts.count ? cParts[i] : 0
            if r > c { return .orderedDescending }
            if r < c { return .orderedAscending }
        }
        return .orderedSame
    }

    public func checkForUpdates(force: Bool = false) async -> UpdateInfo? {
        isChecking = true
        errorMessage = nil
        defer { isChecking = false }

        let repo = getRepo()
        guard let url = URL(string: "https://api.github.com/repos/\(repo)/releases/latest") else {
            errorMessage = "Invalid repository format"
            return nil
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/vnd.github.v3+json", forHTTPHeaderField: "Accept")
        request.setValue("PathHub-iOS/\(getCurrentVersion())", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 10

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                return nil
            }

            if httpResponse.statusCode == 404 {
                // No releases yet
                return nil
            }
            guard (200...299).contains(httpResponse.statusCode) else {
                errorMessage = "GitHub API returned status \(httpResponse.statusCode)"
                return nil
            }

            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                return nil
            }

            let tagName = (json["tag_name"] as? String) ?? ""
            let releaseName = (json["name"] as? String) ?? tagName
            let body = (json["body"] as? String) ?? "No release notes provided."
            let htmlUrlStr = (json["html_url"] as? String) ?? "https://github.com/\(repo)/releases"
            let htmlUrl = URL(string: htmlUrlStr) ?? URL(string: "https://github.com/\(repo)")!

            let currentVersion = getCurrentVersion()
            if Self.compareVersions(tagName, currentVersion) != .orderedDescending && !force {
                // Up to date
                return nil
            }

            // Find IPA asset
            if let assets = json["assets"] as? [[String: Any]] {
                for asset in assets {
                    if let name = asset["name"] as? String,
                       name.lowercased().hasSuffix(".ipa"),
                       let downloadUrlStr = asset["browser_download_url"] as? String,
                       let downloadUrl = URL(string: downloadUrlStr) {
                        let size = (asset["size"] as? Int64) ?? 0

                        let update = UpdateInfo(
                            tagName: tagName,
                            releaseName: releaseName,
                            changelog: body,
                            downloadUrl: downloadUrl,
                            fileName: name,
                            fileSize: size,
                            htmlUrl: htmlUrl
                        )
                        self.availableUpdate = update
                        return update
                    }
                }
            }

            // Fallback direct release download
            if let fallbackUrl = URL(string: "https://github.com/\(repo)/releases/download/\(tagName)/PathHub-unsigned.ipa") {
                let update = UpdateInfo(
                    tagName: tagName,
                    releaseName: releaseName,
                    changelog: body,
                    downloadUrl: fallbackUrl,
                    fileName: "PathHub-unsigned.ipa",
                    fileSize: 1370000,
                    htmlUrl: htmlUrl
                )
                self.availableUpdate = update
                return update
            }

            return nil
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }

    public func downloadIpa(info: UpdateInfo) async throws -> URL {
        isDownloading = true
        downloadProgress = 0.0
        downloadedBytes = 0
        totalBytes = info.fileSize
        errorMessage = nil

        return try await withCheckedThrowingContinuation { continuation in
            self.downloadContinuation = continuation
            let task = self.urlSession.downloadTask(with: info.downloadUrl)
            task.resume()
        }
    }

    // MARK: - URLSessionDownloadDelegate
    nonisolated public func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        Task { @MainActor in
            self.downloadedBytes = totalBytesWritten
            let expected = totalBytesExpectedToWrite > 0 ? totalBytesExpectedToWrite : self.totalBytes
            if expected > 0 {
                self.totalBytes = expected
                self.downloadProgress = min(1.0, Double(totalBytesWritten) / Double(expected))
            } else {
                self.downloadProgress = 0.5
            }
        }
    }

    nonisolated public func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        do {
            let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("updates", isDirectory: true)
            try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)

            let fileName = downloadTask.originalRequest?.url?.lastPathComponent ?? "PathHub-update.ipa"
            let destinationUrl = tempDir.appendingPathComponent(fileName)

            if FileManager.default.fileExists(atPath: destinationUrl.path) {
                try FileManager.default.removeItem(at: destinationUrl)
            }

            try FileManager.default.copyItem(at: location, to: destinationUrl)

            Task { @MainActor in
                self.isDownloading = false
                self.downloadProgress = 1.0
                self.localIpaUrl = destinationUrl
                self.downloadContinuation?.resume(returning: destinationUrl)
                self.downloadContinuation = nil
            }
        } catch {
            Task { @MainActor in
                self.isDownloading = false
                self.errorMessage = error.localizedDescription
                self.downloadContinuation?.resume(throwing: error)
                self.downloadContinuation = nil
            }
        }
    }

    nonisolated public func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        if let error = error {
            Task { @MainActor in
                self.isDownloading = false
                self.errorMessage = error.localizedDescription
                self.downloadContinuation?.resume(throwing: error)
                self.downloadContinuation = nil
            }
        }
    }

    // MARK: - LiveContainer Interaction
    public func canOpenLiveContainer() -> Bool {
        guard let url = URL(string: "livecontainer://") else { return false }
        return UIApplication.shared.canOpenURL(url)
    }

    public func openLiveContainerApp() {
        if let url = URL(string: "livecontainer://") {
            UIApplication.shared.open(url, options: [:], completionHandler: nil)
        }
    }

    public func shareToLiveContainer(fileUrl: URL) {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootVC = windowScene.windows.first(where: { $0.isKeyWindow })?.rootViewController else {
            return
        }

        let activityVC = UIActivityViewController(activityItems: [fileUrl], applicationActivities: nil)
        if let popover = activityVC.popoverPresentationController {
            popover.sourceView = rootVC.view
            popover.sourceRect = CGRect(x: rootVC.view.bounds.midX, y: rootVC.view.bounds.midY, width: 0, height: 0)
            popover.permittedArrowDirections = []
        }

        // Find top-most presented controller
        var topVC = rootVC
        while let presented = topVC.presentedViewController {
            topVC = presented
        }
        topVC.present(activityVC, animated: true)
    }

    public func reset() {
        availableUpdate = nil
        isDownloading = false
        downloadProgress = 0.0
        localIpaUrl = nil
        errorMessage = nil
    }
}
