import Foundation

public struct ExtractedMeta: Sendable {
    public let note: String
    public let addedBy: String?
    public let seenBy: [String]

    public init(note: String = "", addedBy: String? = nil, seenBy: [String] = []) {
        self.note = note
        self.addedBy = addedBy
        self.seenBy = seenBy
    }
}

public enum NoteSync {
    private static let notePrefix = "#ph_note="
    private static let phPrefix = "#ph_"

    /// Extracts all metadata (note, addedBy, seenBy) from the URL fragment.
    public static func extractMetadata(pageUrl: String, columnNote: String? = nil) -> ExtractedMeta {
        var note = (columnNote?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false) ? columnNote!.trimmingCharacters(in: .whitespacesAndNewlines) : ""
        var addedBy: String? = nil
        var seenBy: [String] = []

        // Split by both '#' and '&' to handle any fragment format (#1#ph_note=..., &ph_note=..., etc.)
        let tokens = pageUrl.components(separatedBy: CharacterSet(charactersIn: "#&"))
        for rawToken in tokens {
            let token = rawToken.trimmingCharacters(in: .whitespacesAndNewlines)
            if token.hasPrefix("ph_note=") {
                if note.isEmpty {
                    let raw = String(token.dropFirst("ph_note=".count)).trimmingCharacters(in: .whitespacesAndNewlines)
                    note = decodeBase64OrUrl(raw)
                }
            } else if token.hasPrefix("ph_by=") {
                let by = String(token.dropFirst("ph_by=".count)).trimmingCharacters(in: .whitespacesAndNewlines)
                if !by.isEmpty { addedBy = by }
            } else if token.hasPrefix("ph_seen=") {
                let seenRaw = String(token.dropFirst("ph_seen=".count)).trimmingCharacters(in: .whitespacesAndNewlines)
                if !seenRaw.isEmpty {
                    let items = seenRaw.components(separatedBy: ",").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
                    seenBy.append(contentsOf: items)
                }
            }
        }

        // Deduplicate seenBy
        var seenUnique: [String] = []
        for s in seenBy {
            if !seenUnique.contains(s) { seenUnique.append(s) }
        }

        return ExtractedMeta(note: note, addedBy: addedBy, seenBy: seenUnique)
    }

    /// Extracts note from columnNote if present, otherwise decodes from pageUrl fragment.
    public static func extractNote(pageUrl: String, columnNote: String? = nil) -> String {
        return extractMetadata(pageUrl: pageUrl, columnNote: columnNote).note
    }

    /// Strips any #ph_ metadata fragment and webview tab anchors (#1, #2, #) so WebViews receive pure URLs.
    public static func cleanPageUrl(_ pageUrl: String) -> String {
        var clean = pageUrl
        if let range = clean.range(of: phPrefix) {
            clean = String(clean[..<range.lowerBound])
        }
        if let range = clean.range(of: notePrefix) {
            clean = String(clean[..<range.lowerBound])
        }
        while clean.hasSuffix("#1") || clean.hasSuffix("#2") || clean.hasSuffix("#") {
            if clean.hasSuffix("#1") || clean.hasSuffix("#2") {
                clean = String(clean.dropLast(2))
            } else if clean.hasSuffix("#") {
                clean = String(clean.dropLast(1))
            }
        }
        return clean
    }

    /// Builds pageUrl with metadata (note, addedBy, seenBy) persisted in the URL fragment.
    public static func buildPageUrl(
        cleanUrl: String,
        note: String = "",
        addedBy: String? = nil,
        seenBy: [String] = []
    ) -> String {
        let clean = cleanPageUrl(cleanUrl)
        var parts: [String] = []

        let trimmedNote = note.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmedNote.isEmpty {
            let data = Data(trimmedNote.utf8)
            let base64 = data.base64EncodedString()
                .replacingOccurrences(of: "+", with: "-")
                .replacingOccurrences(of: "/", with: "_")
                .replacingOccurrences(of: "=", with: "")
            parts.append("ph_note=\(base64)")
        }

        if let by = addedBy?.trimmingCharacters(in: .whitespacesAndNewlines), !by.isEmpty {
            parts.append("ph_by=\(by)")
        }

        var uniqueSeen: [String] = []
        for s in seenBy {
            let trimmed = s.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty && !uniqueSeen.contains(trimmed) {
                uniqueSeen.append(trimmed)
            }
        }
        if !uniqueSeen.isEmpty {
            parts.append("ph_seen=\(uniqueSeen.joined(separator: ","))")
        }

        return parts.isEmpty ? clean : "\(clean)#\(parts.joined(separator: "&"))"
    }

    private static func decodeBase64OrUrl(_ raw: String) -> String {
        guard !raw.isEmpty else { return "" }
        let unencoded = raw.removingPercentEncoding ?? raw
        var base64 = unencoded.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while base64.count % 4 != 0 {
            base64.append("=")
        }
        if let data = Data(base64Encoded: base64), let str = String(data: data, encoding: .utf8) {
            return str.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if let decoded = raw.removingPercentEncoding {
            return decoded.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return raw
    }
}
