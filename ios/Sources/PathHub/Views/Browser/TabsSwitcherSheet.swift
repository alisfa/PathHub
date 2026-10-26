import SwiftUI

public struct TabsSwitcherSheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var tabStore: TabStore
    let onTabSelected: (WebTab) -> Void
    let onNewTabRequested: () -> Void

    @State private var showSettings = false

    public init(
        tabStore: TabStore,
        onTabSelected: @escaping (WebTab) -> Void,
        onNewTabRequested: @escaping () -> Void
    ) {
        self.tabStore = tabStore
        self.onTabSelected = onTabSelected
        self.onNewTabRequested = onNewTabRequested
    }

    public var body: some View {
        NavigationStack {
            ZStack {
                // Cosmic Obsidian Backdrop
                LinearGradient(
                    colors: [
                        Color(red: 0.03, green: 0.04, blue: 0.08),
                        Color(red: 0.02, green: 0.03, blue: 0.06)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()

                VStack(spacing: 0) {
                    // Top Header Bar
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Tabs")
                                .font(.system(size: 24, weight: .black, design: .rounded))
                                .foregroundColor(.white)

                            Text("\(tabStore.tabs.count) active \(tabStore.tabs.count == 1 ? "tab" : "tabs")")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(Color.white.opacity(0.55))
                        }

                        Spacer()

                        // Action Buttons: Settings, + New Tab and Done
                        HStack(spacing: 8) {
                            Button(action: {
                                showSettings = true
                            }) {
                                Image(systemName: "gearshape.fill")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundColor(.white.opacity(0.85))
                                    .padding(8)
                                    .background(Circle().fill(Color.white.opacity(0.12)))
                            }

                            Button(action: {
                                onNewTabRequested()
                                dismiss()
                            }) {
                                HStack(spacing: 5) {
                                    Image(systemName: "plus")
                                        .font(.system(size: 13, weight: .bold))
                                    Text("New")
                                        .font(.system(size: 13, weight: .bold))
                                }
                                .foregroundColor(Color(red: 0.03, green: 0.04, blue: 0.08))
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
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
                                )
                                .shadow(color: Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.4), radius: 8, x: 0, y: 3)
                            }

                            Button(action: {
                                dismiss()
                            }) {
                                Text("Done")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(
                                        Capsule()
                                            .fill(Color.white.opacity(0.12))
                                            .overlay(
                                                Capsule()
                                                    .stroke(Color.white.opacity(0.2), lineWidth: 1)
                                            )
                                    )
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, 14)

                    if tabStore.tabs.isEmpty {
                        VStack(spacing: 16) {
                            Spacer()
                            Image(systemName: "square.on.square.dashed")
                                .font(.system(size: 52))
                                .foregroundColor(Color.white.opacity(0.3))

                            Text("No Open Tabs")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(.white)

                            Button(action: {
                                onNewTabRequested()
                                dismiss()
                            }) {
                                Text("Open New Tab")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(Color(red: 0.03, green: 0.04, blue: 0.08))
                                    .padding(.horizontal, 22)
                                    .padding(.vertical, 12)
                                    .background(
                                        Capsule()
                                            .fill(Color(red: 0.06, green: 0.85, blue: 0.58))
                                    )
                            }
                            Spacer()
                        }
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 12) {
                                ForEach(tabStore.tabs) { tab in
                                    let isActive = tabStore.activeTabId == tab.id
                                    Button(action: {
                                        tabStore.selectTab(id: tab.id)
                                        onTabSelected(tab)
                                        dismiss()
                                    }) {
                                        HStack(spacing: 14) {
                                            // Domain Favicon in glass squircle
                                            AsyncImage(url: URL(string: "https://www.google.com/s2/favicons?domain=\(tab.hostDomain)&sz=128")) { phase in
                                                if let img = phase.image {
                                                    img
                                                        .resizable()
                                                        .aspectRatio(contentMode: .fit)
                                                        .frame(width: 24, height: 24)
                                                } else {
                                                    Image(systemName: "globe")
                                                        .font(.system(size: 18))
                                                        .foregroundColor(isActive ? Color(red: 0.06, green: 0.85, blue: 0.58) : .white.opacity(0.6))
                                                }
                                            }
                                            .frame(width: 44, height: 44)
                                            .background(
                                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                    .fill(isActive ? Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.18) : Color.white.opacity(0.08))
                                                    .overlay(
                                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                            .stroke(isActive ? Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.5) : Color.white.opacity(0.15), lineWidth: 1)
                                                    )
                                            )

                                            // Tab Title and Domain
                                            VStack(alignment: .leading, spacing: 3) {
                                                HStack(spacing: 6) {
                                                    Text(tab.title)
                                                        .font(.system(size: 15, weight: .bold))
                                                        .foregroundColor(.white)
                                                        .lineLimit(1)

                                                    if isActive {
                                                        Text("ACTIVE")
                                                            .font(.system(size: 9, weight: .black))
                                                            .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                                                            .padding(.horizontal, 6)
                                                            .padding(.vertical, 2)
                                                            .background(
                                                                Capsule()
                                                                    .fill(Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.18))
                                                            )
                                                    }
                                                }

                                                Text(tab.hostDomain)
                                                    .font(.system(size: 12, weight: .medium))
                                                    .foregroundColor(Color.white.opacity(0.5))
                                                    .lineLimit(1)
                                            }

                                            Spacer()

                                            // Close Tab Button (X)
                                            Button(action: {
                                                withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                                    tabStore.closeTab(id: tab.id)
                                                }
                                            }) {
                                                Image(systemName: "xmark")
                                                    .font(.system(size: 12, weight: .bold))
                                                    .foregroundColor(.white.opacity(0.45))
                                                    .padding(10)
                                                    .background(Circle().fill(Color.white.opacity(0.06)))
                                            }
                                            .buttonStyle(.plain)
                                        }
                                        .padding(.horizontal, 16)
                                        .padding(.vertical, 12)
                                        .background(
                                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                                .fill(.ultraThinMaterial)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                                                        .fill(Color(red: 0.07, green: 0.10, blue: 0.18).opacity(isActive ? 0.95 : 0.8))
                                                )
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                                                        .stroke(
                                                            isActive
                                                                ? LinearGradient(
                                                                    colors: [Color(red: 0.06, green: 0.85, blue: 0.58), Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.2)],
                                                                    startPoint: .topLeading,
                                                                    endPoint: .bottomTrailing
                                                                )
                                                                : LinearGradient(
                                                                    colors: [Color.white.opacity(0.25), Color.white.opacity(0.05)],
                                                                    startPoint: .topLeading,
                                                                    endPoint: .bottomTrailing
                                                                ),
                                                            lineWidth: isActive ? 1.5 : 1
                                                        )
                                                )
                                                .shadow(color: isActive ? Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.25) : Color.black.opacity(0.25), radius: 10, x: 0, y: 4)
                                        )
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.horizontal, 18)
                            .padding(.top, 10)
                            .padding(.bottom, 30)
                        }
                    }
                }
            }
            .navigationBarHidden(true)
            .sheet(isPresented: $showSettings) {
                AccountSheet()
            }
        }
    }
}
