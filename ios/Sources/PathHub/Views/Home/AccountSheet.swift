import SwiftUI

public struct AccountSheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var authService = AuthService.shared
    @ObservedObject var vpnManager = VpnManager.shared
    @ObservedObject var updateService = UpdateService.shared

    @State private var emailInput = ""
    @State private var passwordInput = ""
    @State private var isSigningIn = false
    @State private var errorMessage: String? = nil
    @State private var successMessage: String? = nil

    @State private var githubRepoInput = UpdateService.shared.getRepo()
    @State private var showingUpdateSheet = false
    @State private var updateCheckStatus: String? = nil

    // Supabase endpoint settings
    @State private var supabaseUrl = SupabaseConfig.url
    @State private var supabaseKey = SupabaseConfig.anonKey

    // Balaboo PIN & History
    @State private var showingPinPrompt = false
    @State private var pinInput = ""
    @State private var pinError = false
    @State private var showingBalabooHistory = false

    public init() {}

    public var body: some View {
        NavigationStack {
            Form {
                // Section 1: Cloudflare 1.1.1.1 Device-Wide VPN Hub
                Section(
                    header: Text("Device VPN & Network"),
                    footer: Text("Cloudflare 1.1.1.1 (WARP) covers the ENTIRE device, routing all apps and media through Cloudflare's Anycast edge with zero speed loss.")
                ) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("🛡️ Cloudflare 1.1.1.1 (WARP)")
                                .font(.headline)
                            Text(vpnManager.isSystemVpnActive() ? "Entire device protected • Zero speed loss" : "Connect 1.1.1.1 to cover the entire device")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        Text(vpnManager.isSystemVpnActive() ? "CONNECTED" : "DISCONNECTED")
                            .font(.system(size: 11, weight: .black))
                            .foregroundColor(vpnManager.isSystemVpnActive() ? Color(red: 0.06, green: 0.85, blue: 0.58) : .orange)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(
                                Capsule()
                                    .fill(vpnManager.isSystemVpnActive() ? Color(red: 0.06, green: 0.85, blue: 0.58).opacity(0.15) : Color.orange.opacity(0.15))
                            )
                    }

                    Button(action: {
                        vpnManager.launch1111App()
                    }) {
                        HStack {
                            Image(systemName: "bolt.shield.fill")
                                .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                            Text("Launch 1.1.1.1 App")
                                .bold()
                            Spacer()
                            Image(systemName: "arrow.up.forward.app")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }

                    Button(action: {
                        vpnManager.open1111ProfileInstall()
                    }) {
                        HStack {
                            Image(systemName: "doc.badge.gearshape.fill")
                                .foregroundColor(.blue)
                            Text("Install 1.1.1.1 DNS Profile")
                            Spacer()
                            Image(systemName: "arrow.up.forward.app")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }

                    HStack {
                        Text("🚫 Popup Ad Blocker")
                        Spacer()
                        Text("Active & Guarding")
                            .font(.caption)
                            .bold()
                            .foregroundColor(Color(red: 0.06, green: 0.85, blue: 0.58))
                    }
                }

                Section(header: Text("Account Status")) {
                    HStack {
                        Text("Status")
                        Spacer()
                        Text(authService.isAnonymous ? "Anonymous (Guest)" : "Linked Account")
                            .foregroundColor(authService.isAnonymous ? .orange : .green)
                            .bold()
                    }

                    if let email = authService.userEmail {
                        HStack {
                            Text("Email")
                            Spacer()
                            Text(email)
                                .foregroundColor(.secondary)
                        }
                    }
                }

                Section(header: Text(authService.isAnonymous ? "Upgrade Account" : "Switch Account"),
                        footer: Text("Link an email to sync your websites and playlists across all your phones, tablets, and computers.")) {
                    TextField("Email", text: $emailInput)
                        .keyboardType(.emailAddress)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)

                    SecureField("Password", text: $passwordInput)

                    Button(action: {
                        handleAccountAction()
                    }) {
                        if isSigningIn {
                            ProgressView()
                        } else {
                            Text(authService.isAnonymous ? "Upgrade Account" : "Sign In")
                                .bold()
                        }
                    }
                    .disabled(emailInput.isEmpty || passwordInput.isEmpty || isSigningIn)
                }

                if let err = errorMessage {
                    Section {
                        Text(err).foregroundColor(.red).font(.subheadline)
                    }
                }

                if let success = successMessage {
                    Section {
                        Text(success).foregroundColor(.green).font(.subheadline)
                    }
                }

                Section(header: Text("App Version & Updates"),
                        footer: Text("Checks GitHub Releases for new Path Hub updates. Downloaded IPAs install through LiveContainer.")) {
                    HStack {
                        Text("Current Version")
                        Spacer()
                        Text("v\(updateService.getCurrentVersion())")
                            .foregroundColor(.secondary)
                            .bold()
                    }

                    TextField("GitHub Repo (owner/repo)", text: $githubRepoInput)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)

                    Button("Save Repository") {
                        updateService.setRepo(githubRepoInput)
                        successMessage = "GitHub repository updated!"
                    }

                    Button(action: {
                        updateService.setRepo(githubRepoInput)
                        updateCheckStatus = "Checking..."
                        Task {
                            let res = await updateService.checkForUpdates(force: true)
                            if let update = res {
                                updateCheckStatus = "Update \(update.tagName) available!"
                                showingUpdateSheet = true
                            } else {
                                updateCheckStatus = "Path Hub is up to date!"
                            }
                        }
                    }) {
                        HStack {
                            Image(systemName: "arrow.triangle.2.circlepath")
                            Text(updateService.isChecking ? "Checking for Updates..." : "Check for Updates")
                            Spacer()
                            if let status = updateCheckStatus {
                                Text(status)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                    .disabled(updateService.isChecking)
                }

                Section(header: Text("Supabase Connection"),
                        footer: Text("Enter your Supabase project credentials to connect to your live database.")) {
                    TextField("Project URL", text: $supabaseUrl)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)

                    SecureField("Anon Public Key", text: $supabaseKey)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)

                    Button("Save Configuration") {
                        SupabaseConfig.url = supabaseUrl.trimmingCharacters(in: .whitespacesAndNewlines)
                        SupabaseConfig.anonKey = supabaseKey.trimmingCharacters(in: .whitespacesAndNewlines)
                        SupabaseRealtime.shared.connect()
                        successMessage = "Supabase configuration updated!"
                    }
                }

                Section {
                    Button(action: {
                        pinInput = ""
                        pinError = false
                        showingPinPrompt = true
                    }) {
                        HStack {
                            Text("Balaboo")
                                .foregroundColor(.primary)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("Settings & Account")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") {
                        dismiss()
                    }
                }
            }
            .alert("Balaboo Verification", isPresented: $showingPinPrompt) {
                SecureField("PIN", text: $pinInput)
                    .keyboardType(.numberPad)
                Button("Cancel", role: .cancel) {
                    pinInput = ""
                }
                Button("Unlock") {
                    if pinInput == "1991" {
                        showingBalabooHistory = true
                    } else {
                        pinError = true
                    }
                }
            } message: {
                Text("Enter PIN to access secure history.")
            }
            .alert("Incorrect PIN", isPresented: $pinError) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("The entered PIN is incorrect. Access denied.")
            }
            .sheet(isPresented: $showingUpdateSheet) {
                if let u = updateService.availableUpdate {
                    UpdateSheet(update: u)
                }
            }
            .background(
                Color.clear
                    .sheet(isPresented: $showingBalabooHistory) {
                        BalabooHistorySheet()
                    }
            )
        }
    }

    private func handleAccountAction() {
        errorMessage = nil
        successMessage = nil
        isSigningIn = true

        Task {
            do {
                if authService.isAnonymous {
                    try await authService.upgradeAccount(email: emailInput, password: passwordInput)
                    await MainActor.run {
                        successMessage = "Account upgraded! Your devices now share this account."
                        isSigningIn = false
                    }
                } else {
                    try await authService.signIn(email: emailInput, password: passwordInput)
                    await MainActor.run {
                        successMessage = "Signed in successfully!"
                        isSigningIn = false
                    }
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isSigningIn = false
                }
            }
        }
    }
}
