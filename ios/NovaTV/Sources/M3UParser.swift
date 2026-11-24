import Foundation

struct M3UEntry: Equatable {
    var name: String
    var url: String
    var group: String
    var logo: String?
    var tvgID: String?
    var kind: ContentKind
}

/// Line-based M3U/M3U8 parser: entries are produced while the playlist downloads.
struct M3UParser {
    static let defaultGroup = "Sans catégorie"
    private static let vodExtensions = [".mp4", ".mkv", ".avi", ".mov", ".wmv", ".m4v", ".webm"]

    /// XMLTV URL declared in the #EXTM3U header (url-tvg / x-tvg-url), if any.
    private(set) var epgURL: String?
    private var info: String?
    private var extGroup: String?
    private var first = true

    /// Feeds one line; returns an entry when the line completes one.
    mutating func consume(_ rawLine: String) -> M3UEntry? {
        var line = rawLine
        if first {
            if line.hasPrefix("\u{FEFF}") { line.removeFirst() }
            first = false
        }
        line = line.trimmingCharacters(in: .whitespacesAndNewlines)
        let upper = line.uppercased()
        if line.isEmpty { return nil }
        if upper.hasPrefix("#EXTM3U") {
            let attrs = Self.attributes(line)
            epgURL = (attrs["url-tvg"] ?? attrs["x-tvg-url"]).flatMap { $0.isEmpty ? nil : $0 }
            return nil
        }
        if upper.hasPrefix("#EXTINF") { info = line; extGroup = nil; return nil }
        if upper.hasPrefix("#EXTGRP:") {
            extGroup = String(line.dropFirst(8)).trimmingCharacters(in: .whitespaces)
            return nil
        }
        if line.hasPrefix("#") { return nil }
        let entry = Self.entry(info: info, extGroup: extGroup, url: line)
        info = nil
        extGroup = nil
        return entry
    }

    static func parse(_ text: String) -> (entries: [M3UEntry], epgURL: String?) {
        var parser = M3UParser()
        var entries: [M3UEntry] = []
        text.enumerateLines { line, _ in
            if let entry = parser.consume(line) { entries.append(entry) }
        }
        return (entries, parser.epgURL)
    }

    static func entry(info: String?, extGroup: String?, url: String) -> M3UEntry {
        let separator = info.flatMap(nameSeparatorIndex)
        let header: String
        var name = ""
        if let info, let separator {
            header = String(info[..<separator])
            name = String(info[info.index(after: separator)...]).trimmingCharacters(in: .whitespaces)
        } else {
            header = info ?? ""
        }
        let attrs = attributes(header)
        if name.isEmpty { name = attrs["tvg-name"] ?? "" }
        if name.isEmpty {
            let path = url.components(separatedBy: "?").first ?? url
            name = path.components(separatedBy: "/").last ?? url
        }
        var group = attrs["group-title"] ?? ""
        if group.isEmpty { group = extGroup ?? "" }
        if group.isEmpty { group = defaultGroup }
        return M3UEntry(
            name: name, url: url, group: group,
            logo: attrs["tvg-logo"].flatMap { $0.isEmpty ? nil : $0 },
            tvgID: attrs["tvg-id"].flatMap { $0.isEmpty ? nil : $0 },
            kind: classify(url)
        )
    }

    static func attributes(_ text: String) -> [String: String] {
        var result: [String: String] = [:]
        let regex = try! NSRegularExpression(pattern: "([A-Za-z0-9_-]+)=\"([^\"]*)\"")
        let ns = text as NSString
        for match in regex.matches(in: text, range: NSRange(location: 0, length: ns.length)) {
            let key = ns.substring(with: match.range(at: 1)).lowercased()
            result[key] = ns.substring(with: match.range(at: 2)).trimmingCharacters(in: .whitespaces)
        }
        return result
    }

    /// Index of the comma separating the #EXTINF attributes from the display name (ignores commas inside quotes).
    static func nameSeparatorIndex(_ info: String) -> String.Index? {
        var inQuotes = false
        var index = info.startIndex
        while index < info.endIndex {
            let ch = info[index]
            if ch == "\"" { inQuotes.toggle() } else if ch == "," && !inQuotes { return index }
            index = info.index(after: index)
        }
        return nil
    }

    static func classify(_ url: String) -> ContentKind {
        let path = (url.components(separatedBy: "?").first ?? url).lowercased()
        if path.contains("/series/") { return .series }
        if path.contains("/movie/") { return .movie }
        if vodExtensions.contains(where: { path.hasSuffix($0) }) { return .movie }
        return .live
    }
}
