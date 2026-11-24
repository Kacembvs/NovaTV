import Foundation

/// Electronic programme guide: downloaded independently of the channel list, kept on disk per profile.
@MainActor
final class EPGStore: ObservableObject {
    private struct Snapshot: Codable {
        var programmes: [String: [Programme]]
        var names: [String: String]
    }

    @Published private(set) var programmes: [String: [Programme]] = [:]
    @Published private(set) var loading = false
    @Published private(set) var lastError: String?
    /// Normalized channel display name -> XMLTV channel id.
    private var idsByName: [String: String] = [:]
    private var loadedProfile: UUID?

    private func file(_ profileID: UUID) -> URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("epg-\(profileID.uuidString).json")
    }

    static func normalize(_ name: String) -> String {
        name.searchFolded.filter { $0.isLetter || $0.isNumber }
    }

    func load(_ profile: Profile) {
        guard loadedProfile != profile.id else { return }
        loadedProfile = profile.id
        programmes = [:]
        idsByName = [:]
        let url = file(profile.id)
        Task.detached {
            guard let data = try? Data(contentsOf: url), let snapshot = try? JSONDecoder().decode(Snapshot.self, from: data) else { return }
            await MainActor.run {
                guard self.loadedProfile == profile.id else { return }
                self.apply(snapshot)
            }
        }
    }

    private func apply(_ snapshot: Snapshot) {
        programmes = snapshot.programmes
        var map: [String: String] = [:]
        for (id, name) in snapshot.names { map[Self.normalize(name)] = id }
        idsByName = map
    }

    static func guideURL(for profile: Profile) -> URL? {
        if let custom = profile.customEPGURL, !custom.isEmpty { return URL(string: custom) }
        if profile.type == .xtream {
            return URL(string: XtreamURLs.xmltv(base: profile.url, username: profile.username ?? "", password: Keychain.password(for: profile)))
        }
        return profile.playlistEPGURL.flatMap(URL.init(string:))
    }

    func refresh(_ profile: Profile) async {
        guard let url = Self.guideURL(for: profile) else {
            lastError = "Aucun guide TV pour ce profil."
            return
        }
        let profileID = profile.id
        loading = true
        defer { loading = false }
        do {
            let raw = try await Http.data(url)
            let fileURL = file(profileID)
            let snapshot = try await Task.detached(priority: .utility) { () -> Snapshot in
                let data = Gzip.decompressIfNeeded(raw)
                let now = Date()
                let parser = XMLTVParser(window: now.addingTimeInterval(-3 * 3600)...now.addingTimeInterval(36 * 3600))
                guard parser.parse(data) else { throw NovaError("Guide TV illisible.") }
                var programmes: [String: [Programme]] = [:]
                for (channel, list) in parser.programmes { programmes[channel.lowercased()] = list.sorted { $0.start < $1.start } }
                let names = Dictionary(parser.channelNames.map { ($0.key.lowercased(), $0.value) }, uniquingKeysWith: { first, _ in first })
                let snapshot = Snapshot(programmes: programmes, names: names)
                try JSONEncoder().encode(snapshot).write(to: fileURL, options: .atomic)
                return snapshot
            }.value
            if loadedProfile == profileID { apply(snapshot) }
            profile.epgSyncedAt = .now
            profile.epgError = nil
            lastError = nil
        } catch {
            profile.epgError = error.userMessage
            lastError = error.userMessage
        }
    }

    func schedule(for content: Content) -> [Programme] {
        if let id = content.tvgID?.lowercased(), let list = programmes[id] { return list }
        if let id = idsByName[Self.normalize(content.name)], let list = programmes[id] { return list }
        return []
    }

    func nowNext(for content: Content, at date: Date = .now) -> (now: Programme?, next: Programme?) {
        let list = schedule(for: content)
        guard let index = list.firstIndex(where: { $0.end > date }) else { return (nil, nil) }
        let current = list[index].start <= date ? list[index] : nil
        let next = current == nil ? list[index] : list[safe: index + 1]
        return (current, next)
    }
}

extension Array {
    subscript(safe index: Int) -> Element? { indices.contains(index) ? self[index] : nil }
}
