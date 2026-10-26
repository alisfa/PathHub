import SwiftUI

public struct UpdateSheet: View {
    let update: UpdateInfo
    @ObservedObject var updateService = UpdateService.shared
    @Environment(\.dismiss) private var dismiss

    public init(update: UpdateInfo) {
        self.update = update
    }

    public var body: some View {
        NavigationStack {
            ZStack {
                // Background
                LinearGradient(
                    colors: [
                        Color(red: 0.04, green: 0.05, blue: 0.09),
                        Color(red: 0.02, green: 0.03, blue: 0.06)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()

                VStack(spacing: 20) {
                    // Header Card
                    VStack(spacing: 8) {
                        ZStack {
                            Circle()
                                .fill(Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.15))
                                .frame(width: 64, height: 64)

                            Image(systemName: "arrow.down.circle.fill")
                                .font(.system(size: 32, weight: .bold))
                                .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                        }

                        Text("New Update Available")
                            .font(.system(size: 22, weight: .black, design: .rounded))
                            .foregroundColor(.white)

                        Text(update.releaseName.isEmpty ? update.tagName : update.releaseName)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                    }
                    .padding(.top, 10)

                    // Version comparison pill
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Current Version")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(.white.opacity(0.5))
                            Text("v\(updateService.getCurrentVersion())")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white)
                        }

                        Spacer()

                        Image(systemName: "arrow.forward")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))

                        Spacer()

                        VStack(alignment: .trailing, spacing: 2) {
                            Text("Latest Release")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(.white.opacity(0.5))
                            Text(update.tagName)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)
                    .background(
                        RoundedRectangle(cornerRadius: 16)
                            .fill(Color.white.opacity(0.05))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color.white.opacity(0.1), lineWidth: 1)
                            )
                    )
                    .padding(.horizontal, 20)

                    // Changelog Section
                    VStack(alignment: .leading, spacing: 8) {
                        Text("What's New")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white.opacity(0.6))
                            .textCase(.uppercase)

                        ScrollView {
                            Text(update.changelog.isEmpty ? "Performance optimizations and stability enhancements." : update.changelog)
                                .font(.system(size: 13))
                                .foregroundColor(.white.opacity(0.85))
                                .lineSpacing(4)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(12)
                        }
                        .frame(maxHeight: 140)
                        .background(
                            RoundedRectangle(cornerRadius: 14)
                                .fill(Color.black.opacity(0.35))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14)
                                        .stroke(Color.white.opacity(0.08), lineWidth: 1)
                                )
                        )
                    }
                    .padding(.horizontal, 20)

                    // Download Progress or Status
                    if updateService.isDownloading {
                        VStack(spacing: 8) {
                            HStack {
                                Text("Downloading IPA...")
                                    .font(.system(size: 12))
                                    .foregroundColor(.white.opacity(0.7))
                                Spacer()
                                let percent = Int(updateService.downloadProgress * 100)
                                Text("\(percent)%")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                            }

                            ProgressView(value: updateService.downloadProgress, total: 1.0)
                                .tint(Color(red: 0.06, green: 0.85, blue: 0.58))
                        }
                        .padding(.horizontal, 20)
                    } else if let localUrl = updateService.localIpaUrl {
                        VStack(spacing: 12) {
                            HStack {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                                Text("IPA downloaded! Ready to install in LiveContainer.")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(.white)
                            }

                            Button(action: {
                                updateService.shareToLiveContainer(fileUrl: localUrl)
                            }) {
                                HStack {
                                    Image(systemName: "square.and.arrow.up")
                                    Text("Open in LiveContainer")
                                        .fontWeight(.bold)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(
                                    RoundedRectangle(cornerRadius: 16)
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
                                )
                                .foregroundColor(Color(red: 0.03, green: 0.04, blue: 0.08))
                            }

                            Button(action: {
                                updateService.openLiveContainerApp()
                            }) {
                                HStack {
                                    Image(systemName: "arrow.up.forward.app")
                                    Text("Launch LiveContainer App")
                                        .font(.system(size: 13, weight: .medium))
                                }
                                .foregroundColor(.white.opacity(0.8))
                            }
                        }
                        .padding(.horizontal, 20)
                    } else {
                        // Action Buttons
                        VStack(spacing: 10) {
                            Button(action: {
                                Task {
                                    do {
                                        let fileUrl = try await updateService.downloadIpa(info: update)
                                        updateService.shareToLiveContainer(fileUrl: fileUrl)
                                    } catch {
                                        // Handled in service
                                    }
                                }
                            }) {
                                HStack(spacing: 8) {
                                    Image(systemName: "arrow.down.to.line.compact")
                                        .font(.system(size: 16, weight: .bold))
                                    Text("Download & Install IPA")
                                        .font(.system(size: 16, weight: .bold))
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(
                                    RoundedRectangle(cornerRadius: 16)
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
                                )
                                .foregroundColor(Color(red: 0.03, green: 0.04, blue: 0.08))
                            }

                            Text("Downloads the unsigned IPA and opens the iOS share sheet to install directly into LiveContainer.")
                                .font(.system(size: 11))
                                .foregroundColor(.white.opacity(0.45))
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 10)
                        }
                        .padding(.horizontal, 20)
                    }

                    Spacer()
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Later") {
                        updateService.reset()
                        dismiss()
                    }
                    .foregroundColor(.white.opacity(0.6))
                }

                ToolbarItem(placement: .confirmationAction) {
                    Link(destination: update.htmlUrl) {
                        Image(systemName: "safari")
                            .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                    }
                }
            }
        }
    }
}
