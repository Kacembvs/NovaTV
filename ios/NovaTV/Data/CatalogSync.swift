import Foundation
import SwiftData

/// Downloads a profile catalogue in batches, off the main thread, so the UI fills up progressively.
@ModelActor
actor CatalogSync {
    typealias ProgressHandler = @Sendable (Int) async -> Void

    func sync(profileID: UUID, progress: ProgressHandler) async throws {
        let descriptor = FetchDescriptor<Profile>(predicate: #Predicate<Profile> { $0.id == profileID })
        guard let profile = try modelContext.fetch(descriptor).first else { throw NovaError("Profil introuvable.") }
        let type = profile.type
        let url = profile.url
        let credentials = XtreamClient.Credentials(profile: profile)
        do {
            try modelContext.delete(model: Content.self, where: #Predicate<Content> { $0.profileID == profileID })
            try modelContext.delete(model: ContentCategory.self, where: #Predicate<ContentCategory> { $0.profileID == profileID })
            try modelContext.save()
            let count: Int
            switch type {
            case .m3u: count = try await syncM3U(profile: profile, url: url, progress: progress)
            case .xtream: count = try await syncXtream(profileID: profileID, client: XtreamClient(credentials: credentials), progress: progress)
            }
            profile.contentCount = count
            profile.lastSyncAt = .now
            profile.lastSyncError = nil
            try modelContext.save()
        } catch {
            profile.lastSyncError = error.userMessage
            try? modelContext.save()
            throw error
        }
    }

    private struct CategoryCounter {
        var order: [String] = []
        var counts: [String: Int] = [:]
        mutating func add(_ group: String) {
            if counts[group] == nil { order.append(group) }
            counts[group, default: 0] += 1
        }
    }

    private func syncM3U(profile: Profile, url raw: String, progress: ProgressHandler) async throws -> Int {
        guard let url = URL(string: raw.trimmingCharacters(in: .whitespacesAndNewlines)) else { throw NovaError("Adresse de playlist invalide.") }
        let profileID = profile.id
        var parser = M3UParser()
        var counters: [ContentKind: CategoryCounter] = [:]
        var index = 0
        let (bytes, response) = try await Http.session.bytes(from: url)
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw NovaError("Le serveur a répondu HTTP \(http.statusCode).")
        }
        for try await line in bytes.lines {
            guard let entry = parser.consume(line) else { continue }
            modelContext.insert(Content(profileID: profileID, kind: entry.kind, name: entry.name, group: entry.group,
                                        logo: entry.logo, ref: entry.url, tvgID: entry.tvgID, sortIndex: index))
            counters[entry.kind == .episode ? .series : entry.kind, default: CategoryCounter()].add(entry.group)
            index += 1
            if index % 1000 == 0 {
                try modelContext.save()
                await progress(index)
            }
        }
        guard index > 0 else { throw NovaError("La playlist ne contient aucune chaîne.") }
        profile.playlistEPGURL = parser.epgURL
        saveCategories(profileID: profileID, counters: counters)
        try modelContext.save()
        await progress(index)
        return index
    }

    private func syncXtream(profileID: UUID, client: XtreamClient, progress: ProgressHandler) async throws -> Int {
        try await client.authenticate()
        var index = 0
        var counters: [ContentKind: CategoryCounter] = [:]

        func add(_ kind: ContentKind, categories: [(id: String, name: String)], items: [[String: Any]], build: ([String: Any], String) -> Content?) async throws {
            let names = Dictionary(categories.map { ($0.id, $0.name) }, uniquingKeysWith: { first, _ in first })
            var counter = CategoryCounter()
            categories.forEach { if counter.counts[$0.name] == nil { counter.order.append($0.name); counter.counts[$0.name] = 0 } }
            for item in items {
                let group = XtreamClient.string(item["category_id"]).flatMap { names[$0] } ?? M3UParser.defaultGroup
                guard let content = build(item, group) else { continue }
                modelContext.insert(content)
                counter.add(group)
                index += 1
                if index % 1000 == 0 {
                    try modelContext.save()
                    await progress(index)
                }
            }
            counter.order.removeAll { (counter.counts[$0] ?? 0) == 0 }
            counters[kind] = counter
            try modelContext.save()
        }

        func date(_ value: Any?) -> Date? {
            XtreamClient.string(value).flatMap(Double.init).map { Date(timeIntervalSince1970: $0) }
        }
        func nonEmpty(_ value: Any?) -> String? {
            XtreamClient.string(value).flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 }
        }

        try await add(.live, categories: try await client.orderedCategories("get_live_categories"),
                      items: try await client.list("get_live_streams")) { item, group in
            guard let id = XtreamClient.string(item["stream_id"]) else { return nil }
            // HLS output: Apple's player cannot read raw MPEG-TS live streams.
            return Content(profileID: profileID, kind: .live, name: nonEmpty(item["name"]) ?? "Chaîne \(id)", group: group,
                           logo: nonEmpty(item["stream_icon"]), ref: "live/\(id).m3u8", tvgID: nonEmpty(item["epg_channel_id"]),
                           sortIndex: index)
        }
        try await add(.movie, categories: try await client.orderedCategories("get_vod_categories"),
                      items: try await client.list("get_vod_streams")) { item, group in
            guard let id = XtreamClient.string(item["stream_id"]) else { return nil }
            let ext = nonEmpty(item["container_extension"]) ?? "mp4"
            return Content(profileID: profileID, kind: .movie, name: nonEmpty(item["name"]) ?? "Film \(id)", group: group,
                           logo: nonEmpty(item["stream_icon"]), ref: "movie/\(id).\(ext)", sortIndex: index,
                           year: nonEmpty(item["year"]), rating: nonEmpty(item["rating"]), addedAt: date(item["added"]))
        }
        try await add(.series, categories: try await client.orderedCategories("get_series_categories"),
                      items: try await client.list("get_series")) { item, group in
            guard let id = XtreamClient.string(item["series_id"]) else { return nil }
            let release = nonEmpty(item["releaseDate"]) ?? nonEmpty(item["release_date"])
            return Content(profileID: profileID, kind: .series, name: nonEmpty(item["name"]) ?? "Série \(id)", group: group,
                           logo: nonEmpty(item["cover"]), ref: "\(xtreamSeriesPrefix)\(id)", sortIndex: index,
                           year: release.map { String($0.prefix(4)) }, rating: nonEmpty(item["rating"]),
                           addedAt: date(item["last_modified"]))
        }
        saveCategories(profileID: profileID, counters: counters)
        try modelContext.save()
        await progress(index)
        return index
    }

    private func saveCategories(profileID: UUID, counters: [ContentKind: CategoryCounter]) {
        for (kind, counter) in counters {
            for (order, name) in counter.order.enumerated() {
                modelContext.insert(ContentCategory(profileID: profileID, kind: kind, name: name, count: counter.counts[name] ?? 0, order: order))
            }
        }
    }
}

/// Observable state of catalogue refreshes, shown by the UI.
@MainActor
final class SyncCenter: ObservableObject {
    enum State: Equatable {
        case running(Int)
        case failed(String)
    }

    @Published private(set) var states: [UUID: State] = [:]
    private let container: ModelContainer
    private let epg: EPGStore
    private var tasks: [UUID: Task<Void, Never>] = [:]

    init(container: ModelContainer, epg: EPGStore) {
        self.container = container
        self.epg = epg
    }

    func isRunning(_ id: UUID) -> Bool {
        if case .running = states[id] { return true }
        return false
    }

    func sync(_ profile: Profile) {
        let id = profile.id
        guard !isRunning(id) else { return }
        states[id] = .running(0)
        let container = container
        tasks[id] = Task {
            let worker = CatalogSync(modelContainer: container)
            do {
                try await worker.sync(profileID: id) { count in
                    await MainActor.run { self.states[id] = .running(count) }
                }
                states[id] = nil
                await epg.refresh(profile)
            } catch is CancellationError {
                states[id] = nil
            } catch {
                states[id] = .failed(error.userMessage)
            }
            tasks[id] = nil
        }
    }

    func cancel(_ id: UUID) {
        tasks[id]?.cancel()
        tasks[id] = nil
        states[id] = nil
    }
}
