import Foundation

public struct SupabaseConfig: Sendable {
    public static var url: String {
        get {
            UserDefaults.standard.string(forKey: "supabase_url") ?? "https://ejobnyceertuvwkjfaxk.supabase.co"
        }
        set {
            UserDefaults.standard.set(newValue, forKey: "supabase_url")
        }
    }

    public static var anonKey: String {
        get {
            UserDefaults.standard.string(forKey: "supabase_anon_key") ?? "sb_publishable_tzwP5svHwAtTr3AnZSMOqg_EmStv2be"
        }
        set {
            UserDefaults.standard.set(newValue, forKey: "supabase_anon_key")
        }
    }

    public static var isConfigured: Bool {
        return !url.isEmpty && !anonKey.isEmpty
    }
}
