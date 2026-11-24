import Foundation
import Security

/// Xtream passwords never go to SwiftData: they live in the iOS Keychain.
enum Keychain {
    private static let service = "com.nova.tv.credentials"

    static func set(_ value: String?, for account: String) {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service,
                                    kSecAttrAccount as String: account]
        SecItemDelete(query as CFDictionary)
        guard let value, let data = value.data(using: .utf8) else { return }
        var add = query
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(add as CFDictionary, nil)
    }

    static func get(_ account: String) -> String? {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service,
                                    kSecAttrAccount as String: account,
                                    kSecReturnData as String: true,
                                    kSecMatchLimit as String: kSecMatchLimitOne]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func password(for profile: Profile) -> String { get(profile.id.uuidString) ?? "" }
}

struct NovaError: LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}

extension Error {
    /// French, user-facing description.
    var userMessage: String {
        if let nova = self as? NovaError { return nova.message }
        let ns = self as NSError
        if ns.domain == NSURLErrorDomain {
            switch ns.code {
            case NSURLErrorNotConnectedToInternet: return "Pas de connexion Internet."
            case NSURLErrorTimedOut: return "Le serveur ne répond pas (délai dépassé)."
            case NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed: return "Serveur introuvable : vérifiez l'adresse."
            case NSURLErrorCannotConnectToHost: return "Connexion au serveur impossible."
            case NSURLErrorSecureConnectionFailed: return "Connexion sécurisée impossible avec ce serveur."
            default: break
            }
        }
        return localizedDescription
    }
}

enum Http {
    static let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 600
        config.httpAdditionalHeaders = ["User-Agent": "NOVA-IPTV/1.0 (iOS)"]
        return URLSession(configuration: config)
    }()

    static func data(_ url: URL) async throws -> Data {
        let (data, response) = try await session.data(from: url)
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw NovaError("Le serveur a répondu HTTP \(http.statusCode).")
        }
        return data
    }
}

enum XtreamURLs {
    /// Accepts "host:port", "http://host:port/", ".../player_api.php" or a pasted ".../get.php?..." link.
    static func normalizeBase(_ raw: String) -> String {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if let q = s.firstIndex(of: "?") { s = String(s[..<q]) }
        if !s.lowercased().hasPrefix("http://") && !s.lowercased().hasPrefix("https://") { s = "http://" + s }
        while s.hasSuffix("/") { s.removeLast() }
        for suffix in ["/player_api.php", "/get.php", "/xmltv.php"] where s.lowercased().hasSuffix(suffix) {
            s.removeLast(suffix.count)
        }
        while s.hasSuffix("/") { s.removeLast() }
        return s
    }

    static func encode(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .alphanumerics.union(CharacterSet(charactersIn: "-._~"))) ?? value
    }

    /// Builds the playable URL from a credential-free reference such as "live/123.m3u8".
    static func streamURL(base: String, username: String, password: String, ref: String) -> String {
        let parts = ref.split(separator: "/", maxSplits: 1).map(String.init)
        guard parts.count == 2 else { return ref }
        return "\(normalizeBase(base))/\(parts[0])/\(encode(username))/\(encode(password))/\(parts[1])"
    }

    static func api(base: String, username: String, password: String, action: String?, extra: [String: String] = [:]) -> URL? {
        var components = URLComponents(string: normalizeBase(base) + "/player_api.php")
        var items = [URLQueryItem(name: "username", value: username), URLQueryItem(name: "password", value: password)]
        if let action { items.append(URLQueryItem(name: "action", value: action)) }
        for (key, value) in extra.sorted(by: { $0.key < $1.key }) { items.append(URLQueryItem(name: key, value: value)) }
        components?.queryItems = items
        return components?.url
    }

    static func xmltv(base: String, username: String, password: String) -> String {
        "\(normalizeBase(base))/xmltv.php?username=\(encode(username))&password=\(encode(password))"
    }
}

enum StreamURLs {
    /// Resolves a stored reference to a URL (credentials are only injected here, in memory).
    static func url(for ref: String, profile: Profile) -> URL? {
        let raw: String
        switch profile.type {
        case .m3u: raw = ref
        case .xtream:
            raw = XtreamURLs.streamURL(base: profile.url, username: profile.username ?? "", password: Keychain.password(for: profile), ref: ref)
        }
        return URL(string: raw) ?? URL(string: raw.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? raw)
    }

    /// Apple's player cannot read raw MPEG-TS live streams: IPTV servers expose the same channel as HLS.
    static func appleCompatible(_ url: URL, kind: ContentKind) -> URL {
        guard kind == .live else { return url }
        let path = url.path.lowercased()
        if path.hasSuffix(".ts") {
            return URL(string: url.absoluteString.replacingOccurrences(of: ".ts", with: ".m3u8", options: [.backwards, .caseInsensitive],
                                                                     range: url.absoluteString.range(of: ".ts", options: [.backwards, .caseInsensitive]))) ?? url
        }
        return url
    }

    /// Containers Apple's player does not support.
    static func needsExternalPlayer(_ url: URL) -> Bool {
        ["mkv", "avi", "wmv", "flv", "webm"].contains(url.pathExtension.lowercased())
    }
}

enum Gzip {
    /// XMLTV guides are frequently gzip files.
    static func decompressIfNeeded(_ data: Data) -> Data {
        guard data.count > 18, data[data.startIndex] == 0x1f, data[data.startIndex + 1] == 0x8b else { return data }
        let bytes = [UInt8](data)
        let flags = bytes[3]
        var offset = 10
        if flags & 0x04 != 0, bytes.count > offset + 2 { offset += 2 + Int(bytes[offset]) + Int(bytes[offset + 1]) << 8 }
        if flags & 0x08 != 0 { while offset < bytes.count && bytes[offset] != 0 { offset += 1 }; offset += 1 }
        if flags & 0x10 != 0 { while offset < bytes.count && bytes[offset] != 0 { offset += 1 }; offset += 1 }
        if flags & 0x02 != 0 { offset += 2 }
        guard offset < bytes.count - 8 else { return data }
        let deflate = Data(bytes[offset..<(bytes.count - 8)]) as NSData
        // Apple's "zlib" algorithm is raw DEFLATE (RFC 1951), which is what gzip wraps.
        return (try? deflate.decompressed(using: .zlib) as Data) ?? data
    }
}
