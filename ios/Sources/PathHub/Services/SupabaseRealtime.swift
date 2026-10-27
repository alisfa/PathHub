import Foundation
import Combine

public enum RealtimeAction: String, Sendable {
    case insert = "INSERT"
    case update = "UPDATE"
    case delete = "DELETE"
}

public struct RealtimeRecordEvent: @unchecked Sendable {
    public let table: String
    public let action: RealtimeAction
    public let record: [String: Any]
    public let oldRecord: [String: Any]?
}

@MainActor
public final class SupabaseRealtime: ObservableObject {
    public static let shared = SupabaseRealtime()

    @Published public var isConnected: Bool = false

    private var webSocketTask: URLSessionWebSocketTask?
    private var pingTimer: AnyCancellable?
    private var reconnectTimer: AnyCancellable?
    private var messageRef = 0

    public var onSitesChange: ((RealtimeRecordEvent) -> Void)?
    public var onPlaylistItemsChange: ((RealtimeRecordEvent) -> Void)?
    public var onHistoryChange: ((RealtimeRecordEvent) -> Void)?

    private init() {}

    public func connect() {
        guard SupabaseConfig.isConfigured else { return }
        disconnect()

        guard let wsUrl = buildWebSocketURL() else { return }

        let session = URLSession(configuration: .default)
        webSocketTask = session.webSocketTask(with: wsUrl)
        webSocketTask?.resume()

        self.isConnected = true
        startListening()
        startHeartbeat()

        // Join channels for realtime postgres changes
        joinChannel(topic: "realtime:public:sites")
        joinChannel(topic: "realtime:public:playlist_items")
        joinChannel(topic: "realtime:public:history")
    }

    public func disconnect() {
        pingTimer?.cancel()
        reconnectTimer?.cancel()
        webSocketTask?.cancel(with: .normalClosure, reason: nil)
        webSocketTask = nil
        isConnected = false
    }

    private func buildWebSocketURL() -> URL? {
        var base = SupabaseConfig.url
            .replacingOccurrences(of: "https://", with: "wss://")
            .replacingOccurrences(of: "http://", with: "ws://")
        if base.hasSuffix("/") { base.removeLast() }

        let token = AuthService.shared.accessToken
        let full = "\(base)/realtime/v1/websocket?apikey=\(SupabaseConfig.anonKey)&token=\(token)&vsn=1.0.0"
        return URL(string: full)
    }

    private func joinChannel(topic: String) {
        messageRef += 1
        let tableName = String(topic.split(separator: ":").last ?? "")
        let message = """
        {"topic":"\(topic)","event":"phx_join","payload":{"config":{"postgres_changes":[{"event":"*","schema":"public","table":"\(tableName)"}]}},"ref":"\(messageRef)"}
        """
        send(rawString: message)
    }

    private func send(rawString: String) {
        webSocketTask?.send(.string(rawString)) { error in
            if let error = error {
                print("[Realtime WS] Send error: \(error)")
            }
        }
    }

    private func startHeartbeat() {
        pingTimer?.cancel()
        pingTimer = Timer.publish(every: 25, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                guard let self = self else { return }
                self.messageRef += 1
                let heartbeat = """
                {"topic":"phoenix","event":"heartbeat","payload":{},"ref":"\(self.messageRef)"}
                """
                self.send(rawString: heartbeat)
            }
    }

    private func startListening() {
        webSocketTask?.receive { [weak self] result in
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                switch result {
                case .success(let message):
                    switch message {
                    case .string(let text):
                        self.handleIncomingMessage(text)
                    case .data(let data):
                        if let str = String(data: data, encoding: .utf8) {
                            self.handleIncomingMessage(str)
                        }
                    @unknown default:
                        break
                    }
                    // Continue listening for next packet
                    self.startListening()

                case .failure(let error):
                    print("[Realtime WS] Connection closed or failed: \(error)")
                    self.isConnected = false
                    self.scheduleReconnect()
                }
            }
        }
    }

    private func handleIncomingMessage(_ rawText: String) {
        guard let data = rawText.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }

        let event = json["event"] as? String ?? ""

        if event == "postgres_changes",
           let payload = json["payload"] as? [String: Any] {
            let dataObj = (payload["data"] as? [String: Any]) ?? payload
            let table = (dataObj["table"] as? String) ?? (payload["table"] as? String) ?? ""
            let type = (dataObj["type"] as? String) ?? (payload["type"] as? String) ?? ""
            let record = (dataObj["record"] as? [String: Any]) ?? (payload["record"] as? [String: Any]) ?? [:]
            let oldRecord = (dataObj["old_record"] as? [String: Any]) ?? (payload["old_record"] as? [String: Any])

            guard let action = RealtimeAction(rawValue: type) else { return }
            let changeEvent = RealtimeRecordEvent(
                table: table,
                action: action,
                record: record,
                oldRecord: oldRecord
            )

            if table == "sites" {
                self.onSitesChange?(changeEvent)
            } else if table == "playlist_items" {
                let videoUrl = (record["video_url"] as? String) ?? (oldRecord?["video_url"] as? String) ?? ""
                if videoUrl.hasPrefix("urn:balaboo:hist") {
                    self.onHistoryChange?(changeEvent)
                    NotificationCenter.default.post(name: NSNotification.Name("SupabaseHistoryChanged"), object: changeEvent)
                } else {
                    self.onPlaylistItemsChange?(changeEvent)
                }
            } else if table == "history" {
                self.onHistoryChange?(changeEvent)
                NotificationCenter.default.post(name: NSNotification.Name("SupabaseHistoryChanged"), object: changeEvent)
            }
        }
    }

    private func scheduleReconnect() {
        reconnectTimer?.cancel()
        reconnectTimer = Just(())
            .delay(for: .seconds(5), scheduler: DispatchQueue.main)
            .sink { [weak self] _ in
                print("[Realtime WS] Attempting reconnection...")
                self?.connect()
            }
    }
}
