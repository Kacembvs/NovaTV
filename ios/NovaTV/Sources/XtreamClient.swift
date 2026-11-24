import Foundation

/// Minimal Xtream Codes "player_api.php" client.
struct XtreamClient {
    struct Credentials {
        var base: String
        var username: String
        var password: String

        init(base: String, username: String, password: String) {
            self.base = base
            self.username = username
            self.password = password
        }

        init(profile: Profile) {
            self.init(base: profile.url, username: profile.username ?? "", password: Keychain.password(for: profile))
        }
    }

    struct Episode: Identifiable, Hashable {
        var id: String
        var season: Int
        var number: Int
        var title: String
        var fileExtension: String
        var plot: String?
        var image: String?
        var duration: String?

        var ref: String { "series/\(id).\(fileExtension)" }
        var label: String {
            let prefix = "S\(season) · É\(number)"
            return title.isEmpty ? prefix : "\(prefix) — \(title)"
        }
    }

    struct SeriesInfo {
        var name: String?
        var plot: String?
        var cover: String?
        var backdrop: String?
        var genre: String?
        var releaseDate: String?
        var rating: String?
        var cast: String?
        var episodes: [Episode]
    }

    struct VodInfo {
        var plot: String?
        var genre: String?
        var duration: String?
        var releaseDate: String?
        var rating: String?
        var director: String?
        var cast: String?
        var cover: String?
        var backdrop: String?
        var trailer: String?
    }

    let credentials: Credentials

    func authenticate() async throws {
        let json = try await request(nil)
        guard let object = json as? [String: Any], let info = object["user_info"] as? [String: Any] else {
            throw NovaError("Réponse inattendue du serveur Xtream.")
        }
        guard Self.string(info["auth"]) == "1" else { throw NovaError("Identifiants refusés par le serveur.") }
        if let status = Self.string(info["status"]), status.lowercased() != "active" {
            throw NovaError("Compte non actif (\(status)).")
        }
    }

    func categories(_ action: String) async throws -> [String: String] {
        var result: [String: String] = [:]
        for item in try await list(action) {
            if let id = Self.string(item["category_id"]) { result[id] = Self.string(item["category_name"]) ?? "" }
        }
        return result
    }

    /// Category names in the provider's order.
    func orderedCategories(_ action: String) async throws -> [(id: String, name: String)] {
        try await list(action).compactMap { item in
            guard let id = Self.string(item["category_id"]) else { return nil }
            return (id, Self.string(item["category_name"]) ?? "")
        }
    }

    func list(_ action: String) async throws -> [[String: Any]] {
        let json = try await request(action)
        return (json as? [[String: Any]]) ?? []
    }

    func seriesInfo(_ seriesID: String) async throws -> SeriesInfo {
        guard let object = try await request("get_series_info", extra: ["series_id": seriesID]) as? [String: Any] else {
            throw NovaError("Série introuvable sur le serveur.")
        }
        let info = object["info"] as? [String: Any] ?? [:]
        var episodes: [Episode] = []
        func read(_ list: [Any], season: Int) {
            for case let item as [String: Any] in list {
                guard let id = Self.string(item["id"]) else { continue }
                let details = item["info"] as? [String: Any] ?? [:]
                episodes.append(Episode(
                    id: id,
                    season: Int(Self.string(item["season"]) ?? "") ?? season,
                    number: Int(Self.string(item["episode_num"]) ?? "") ?? episodes.count + 1,
                    title: Self.string(item["title"]) ?? "",
                    fileExtension: Self.string(item["container_extension"]).flatMap { $0.isEmpty ? nil : $0 } ?? "mp4",
                    plot: Self.string(details["plot"]),
                    image: Self.string(details["movie_image"]),
                    duration: Self.string(details["duration"])
                ))
            }
        }
        if let bySeason = object["episodes"] as? [String: Any] {
            for (season, list) in bySeason { read(list as? [Any] ?? [], season: Int(season) ?? 0) }
        } else if let lists = object["episodes"] as? [Any] {
            for list in lists { read(list as? [Any] ?? [], season: 0) }
        }
        episodes.sort { ($0.season, $0.number) < ($1.season, $1.number) }
        return SeriesInfo(
            name: Self.string(info["name"]),
            plot: Self.string(info["plot"]),
            cover: Self.string(info["cover"]),
            backdrop: (info["backdrop_path"] as? [Any])?.compactMap { Self.string($0) }.first,
            genre: Self.string(info["genre"]),
            releaseDate: Self.string(info["releaseDate"]) ?? Self.string(info["release_date"]),
            rating: Self.string(info["rating"]),
            cast: Self.string(info["cast"]),
            episodes: episodes
        )
    }

    func vodInfo(_ vodID: String) async throws -> VodInfo {
        let object = try await request("get_vod_info", extra: ["vod_id": vodID]) as? [String: Any] ?? [:]
        let info = object["info"] as? [String: Any] ?? [:]
        func field(_ keys: String...) -> String? {
            keys.lazy.compactMap { Self.string(info[$0])?.trimmingCharacters(in: .whitespaces) }.first { !$0.isEmpty }
        }
        return VodInfo(
            plot: field("plot", "description"),
            genre: field("genre"),
            duration: field("duration"),
            releaseDate: field("releasedate", "release_date"),
            rating: field("rating"),
            director: field("director"),
            cast: field("cast", "actors"),
            cover: field("cover_big", "movie_image"),
            backdrop: (info["backdrop_path"] as? [Any])?.compactMap { Self.string($0) }.first { !$0.isEmpty },
            trailer: field("youtube_trailer")
        )
    }

    private func request(_ action: String?, extra: [String: String] = [:]) async throws -> Any {
        guard let url = XtreamURLs.api(base: credentials.base, username: credentials.username, password: credentials.password,
                                      action: action, extra: extra) else {
            throw NovaError("Adresse du serveur invalide.")
        }
        let data = try await Http.data(url)
        guard !data.isEmpty else { throw NovaError("Réponse vide du serveur.") }
        do {
            return try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
        } catch {
            throw NovaError("Réponse illisible du serveur Xtream.")
        }
    }

    /// Xtream servers mix strings and numbers for the same fields.
    static func string(_ value: Any?) -> String? {
        switch value {
        case let s as String: return s
        case let n as NSNumber: return n.stringValue
        default: return nil
        }
    }
}
