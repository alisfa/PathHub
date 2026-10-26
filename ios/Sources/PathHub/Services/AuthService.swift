import Foundation
import Combine

public struct AuthSession: Codable, Sendable {
    public let accessToken: String
    public let refreshToken: String
    public let userId: String
    public let email: String?
    public let isAnonymous: Bool
}

@MainActor
public final class AuthService: ObservableObject {
    public static let shared = AuthService()

    @Published public var session: AuthSession? = nil
    @Published public var isAnonymous: Bool = true
    @Published public var userEmail: String? = nil

    private let sessionKey = "pathhub_auth_session_v1"

    private init() {
        loadSession()
        if session == nil {
            ensureInitialAuth()
        }
    }

    public var accessToken: String {
        if let token = session?.accessToken, token.components(separatedBy: ".").count == 3 {
            return token
        }
        return SupabaseConfig.anonKey
    }

    public var userId: String {
        if let uid = session?.userId, !uid.isEmpty && uid != "local-user" {
            return uid
        }
        if let stored = UserDefaults.standard.string(forKey: "device_uuid"), !stored.isEmpty {
            return stored
        }
        let newId = UUID().uuidString.lowercased()
        UserDefaults.standard.set(newId, forKey: "device_uuid")
        return newId
    }

    private func loadSession() {
        if let data = UserDefaults.standard.data(forKey: sessionKey),
           let saved = try? JSONDecoder().decode(AuthSession.self, from: data) {
            self.session = saved
            self.isAnonymous = saved.isAnonymous
            self.userEmail = saved.email
        }
    }

    private func saveSession(_ session: AuthSession) {
        self.session = session
        self.isAnonymous = session.isAnonymous
        self.userEmail = session.email
        if let encoded = try? JSONEncoder().encode(session) {
            UserDefaults.standard.set(encoded, forKey: sessionKey)
        }
    }

    /// Automatically provisions an anonymous account on first app launch
    public func ensureInitialAuth() {
        guard SupabaseConfig.isConfigured else {
            // Local fallback session
            let localSession = AuthSession(
                accessToken: "local-token",
                refreshToken: "local-refresh",
                userId: UUID().uuidString,
                email: nil,
                isAnonymous: true
            )
            saveSession(localSession)
            return
        }

        Task {
            do {
                try await signInAnonymously()
            } catch {
                print("[AuthService] Anonymous sign-in error: \(error)")
            }
        }
    }

    /// Supabase Anonymous Sign In
    public func signInAnonymously() async throws {
        guard let url = URL(string: "\(SupabaseConfig.url)/auth/v1/signup") else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(SupabaseConfig.anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(SupabaseConfig.anonKey)", forHTTPHeaderField: "Authorization")

        // Anonymous auth payload in Supabase (or synthetic anonymous email/password)
        let anonId = UUID().uuidString.lowercased()
        let payload: [String: Any] = [
            "email": "anon_\(anonId)@pathhub.internal",
            "password": "AnonPwd_\(anonId)_Secure!",
            "data": ["is_anonymous": true]
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            return
        }

        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let token = json["access_token"] as? String,
           let user = json["user"] as? [String: Any],
           let uid = user["id"] as? String {
            let s = AuthSession(
                accessToken: token,
                refreshToken: json["refresh_token"] as? String ?? "",
                userId: uid,
                email: nil,
                isAnonymous: true
            )
            self.saveSession(s)
        }
    }

    /// Upgrades the anonymous account with email + password so other devices can share it
    public func upgradeAccount(email: String, password: String) async throws {
        guard SupabaseConfig.isConfigured else {
            let upgraded = AuthSession(
                accessToken: "local-token",
                refreshToken: "local-refresh",
                userId: self.userId,
                email: email,
                isAnonymous: false
            )
            self.saveSession(upgraded)
            return
        }

        guard let url = URL(string: "\(SupabaseConfig.url)/auth/v1/user") else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(SupabaseConfig.anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(self.accessToken)", forHTTPHeaderField: "Authorization")

        let payload = ["email": email, "password": password]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            let msg = String(data: data, encoding: .utf8) ?? "Failed to upgrade account"
            throw NSError(domain: "AuthService", code: 400, userInfo: [NSLocalizedDescriptionKey: msg])
        }

        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let uid = json["id"] as? String {
            let s = AuthSession(
                accessToken: self.accessToken,
                refreshToken: self.session?.refreshToken ?? "",
                userId: uid,
                email: email,
                isAnonymous: false
            )
            self.saveSession(s)
        }
    }

    /// Sign in with existing email on another device to share account
    public func signIn(email: String, password: String) async throws {
        guard SupabaseConfig.isConfigured else { return }
        guard let url = URL(string: "\(SupabaseConfig.url)/auth/v1/token?grant_type=password") else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(SupabaseConfig.anonKey, forHTTPHeaderField: "apikey")

        let payload = ["email": email, "password": password]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            let msg = String(data: data, encoding: .utf8) ?? "Sign in failed"
            throw NSError(domain: "AuthService", code: 401, userInfo: [NSLocalizedDescriptionKey: msg])
        }

        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let token = json["access_token"] as? String,
           let user = json["user"] as? [String: Any],
           let uid = user["id"] as? String {
            let s = AuthSession(
                accessToken: token,
                refreshToken: json["refresh_token"] as? String ?? "",
                userId: uid,
                email: email,
                isAnonymous: false
            )
            self.saveSession(s)
        }
    }
}
