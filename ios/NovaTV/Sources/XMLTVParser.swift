import Foundation

struct Programme: Codable, Hashable {
    var start: Date
    var end: Date
    var title: String
    var summary: String?
}

/// Streaming XMLTV reader keeping only programmes in a useful time window.
final class XMLTVParser: NSObject, XMLParserDelegate {
    private(set) var programmes: [String: [Programme]] = [:]
    /// Channel display names, used to match channels without tvg-id.
    private(set) var channelNames: [String: String] = [:]
    private let window: ClosedRange<Date>

    private var currentChannel: String?
    private var currentStart: Date?
    private var currentEnd: Date?
    private var currentTitle = ""
    private var currentSummary = ""
    private var element = ""
    private var displayNameFor: String?
    private var displayName = ""

    init(window: ClosedRange<Date>) {
        self.window = window
    }

    static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyyMMddHHmmss Z"
        return f
    }()

    private static let shortFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = "yyyyMMddHHmmss"
        return f
    }()

    static func date(_ raw: String?) -> Date? {
        guard let raw = raw?.trimmingCharacters(in: .whitespaces), raw.count >= 14 else { return nil }
        return dateFormatter.date(from: raw) ?? shortFormatter.date(from: String(raw.prefix(14)))
    }

    func parse(_ data: Data) -> Bool {
        let parser = XMLParser(data: data)
        parser.delegate = self
        return parser.parse()
    }

    func parser(_ parser: XMLParser, didStartElement name: String, namespaceURI: String?, qualifiedName: String?, attributes: [String: String] = [:]) {
        element = name
        switch name {
        case "programme":
            currentChannel = attributes["channel"]
            currentStart = Self.date(attributes["start"])
            currentEnd = Self.date(attributes["stop"])
            currentTitle = ""
            currentSummary = ""
        case "channel":
            displayNameFor = attributes["id"]
            displayName = ""
        default: break
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        switch element {
        case "title" where currentChannel != nil: currentTitle += string
        case "desc" where currentChannel != nil: currentSummary += string
        case "display-name" where displayNameFor != nil: displayName += string
        default: break
        }
    }

    func parser(_ parser: XMLParser, didEndElement name: String, namespaceURI: String?, qualifiedName: String?) {
        switch name {
        case "programme":
            if let channel = currentChannel, let start = currentStart {
                let end = currentEnd ?? start.addingTimeInterval(3600)
                if end >= window.lowerBound && start <= window.upperBound {
                    let summary = currentSummary.trimmingCharacters(in: .whitespacesAndNewlines)
                    programmes[channel, default: []].append(Programme(
                        start: start, end: end,
                        title: currentTitle.trimmingCharacters(in: .whitespacesAndNewlines),
                        summary: summary.isEmpty ? nil : summary
                    ))
                }
            }
            currentChannel = nil
        case "display-name":
            if let id = displayNameFor, channelNames[id] == nil {
                let trimmed = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty { channelNames[id] = trimmed }
            }
        case "channel":
            displayNameFor = nil
        default: break
        }
        element = ""
    }
}
