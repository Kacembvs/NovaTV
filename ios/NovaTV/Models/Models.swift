import Foundation
import SwiftData

enum ProfileType: String, Codable, CaseIterable, Identifiable {
    case m3u, xtream
    var id: String { rawValue }
    var label: String { self == .m3u ? "M3U / M3U8" : "Xtream Codes" }
}

/// EPISODE is a directly playable series episode; SERIES may be a container (Xtream) or an episode (M3U).
enum ContentKind: String, Codable, CaseIterable {
    case live, movie, series, episode
}

let xtreamSeriesPrefix = "xseries:"

@Model
final class Profile {
    @Attribute(.unique) var id: UUID
    var name: String
    var typeRaw: String
    var url: String
    var username: String?
    var customEPGURL: String?
    /// XMLTV address declared by the M3U header (url-tvg), if any.
    var playlistEPGURL: String?
    var createdAt: Date
    var lastSyncAt: Date?
    var lastSyncError: String?
    var contentCount: Int
    var epgSyncedAt: Date?
    var epgError: String?

    init(name: String, type: ProfileType, url: String, username: String? = nil, customEPGURL: String? = nil) {
        id = UUID()
        self.name = name
        typeRaw = type.rawValue
        self.url = url
        self.username = username
        self.customEPGURL = customEPGURL
        createdAt = .now
        contentCount = 0
    }

    var type: ProfileType { ProfileType(rawValue: typeRaw) ?? .m3u }
}

@Model
final class Content {
    /// "profileID|ref": a stable identity across refreshes.
    @Attribute(.unique) var key: String
    var profileID: UUID
    var kindRaw: String
    var name: String
    /// Lowercased, accent-free name used by search.
    var searchName: String
    var group: String
    var logo: String?
    /// Full URL for M3U profiles, credential-free path ("movie/12.mkv") for Xtream ones.
    var ref: String
    var tvgID: String?
    var sortIndex: Int
    var year: String?
    var rating: String?
    var addedAt: Date?

    init(profileID: UUID, kind: ContentKind, name: String, group: String, logo: String?, ref: String,
         tvgID: String? = nil, sortIndex: Int, year: String? = nil, rating: String? = nil, addedAt: Date? = nil) {
        key = "\(profileID.uuidString)|\(ref)"
        self.profileID = profileID
        kindRaw = kind.rawValue
        self.name = name
        searchName = name.searchFolded
        self.group = group
        self.logo = logo
        self.ref = ref
        self.tvgID = tvgID
        self.sortIndex = sortIndex
        self.year = year
        self.rating = rating
        self.addedAt = addedAt
    }

    var kind: ContentKind { ContentKind(rawValue: kindRaw) ?? .live }
    var isXtreamSeries: Bool { ref.hasPrefix(xtreamSeriesPrefix) }
}

@Model
final class ContentCategory {
    @Attribute(.unique) var key: String
    var profileID: UUID
    var kindRaw: String
    var name: String
    var count: Int
    var order: Int

    init(profileID: UUID, kind: ContentKind, name: String, count: Int, order: Int) {
        key = "\(profileID.uuidString)|\(kind.rawValue)|\(name)"
        self.profileID = profileID
        kindRaw = kind.rawValue
        self.name = name
        self.count = count
        self.order = order
    }

    var kind: ContentKind { ContentKind(rawValue: kindRaw) ?? .live }
}

@Model
final class Favorite {
    @Attribute(.unique) var key: String
    var profileID: UUID
    var ref: String
    var kindRaw: String
    var name: String
    var logo: String?
    var addedAt: Date

    init(profileID: UUID, ref: String, kind: ContentKind, name: String, logo: String?) {
        key = "\(profileID.uuidString)|\(ref)"
        self.profileID = profileID
        self.ref = ref
        kindRaw = kind.rawValue
        self.name = name
        self.logo = logo
        addedAt = .now
    }

    var kind: ContentKind { ContentKind(rawValue: kindRaw) ?? .movie }
}

@Model
final class HistoryEntry {
    @Attribute(.unique) var key: String
    var profileID: UUID
    var ref: String
    var kindRaw: String
    var title: String
    var subtitle: String?
    var logo: String?
    /// Series reference for episodes.
    var parentRef: String?
    var position: Double
    var duration: Double
    var updatedAt: Date

    init(item: PlayableItem, profileID: UUID) {
        key = "\(profileID.uuidString)|\(item.ref)"
        self.profileID = profileID
        ref = item.ref
        kindRaw = item.kind.rawValue
        title = item.title
        subtitle = item.subtitle
        logo = item.logo
        parentRef = item.parentRef
        position = 0
        duration = 0
        updatedAt = .now
    }

    var kind: ContentKind { ContentKind(rawValue: kindRaw) ?? .movie }
    var progress: Double { duration > 0 ? min(1, position / duration) : 0 }
    /// Worth resuming: started, and not (almost) finished.
    var isResumable: Bool { position > 10 && (duration <= 0 || position < duration * 0.95) }
    var playable: PlayableItem { PlayableItem(ref: ref, title: title, kind: kind, logo: logo, subtitle: subtitle, parentRef: parentRef) }
}

enum DownloadState: String, Codable {
    case queued, downloading, paused, completed, failed
}

@Model
final class DownloadRecord {
    @Attribute(.unique) var key: String
    var profileID: UUID
    var ref: String
    var kindRaw: String
    var title: String
    var subtitle: String?
    var logo: String?
    var parentRef: String?
    var season: Int?
    var episode: Int?
    var stateRaw: String
    var progress: Double
    var bytes: Int64
    var expectedBytes: Int64
    /// File name inside the offline folder once completed.
    var fileName: String?
    var failure: String?
    var createdAt: Date
    var updatedAt: Date

    init(profileID: UUID, item: PlayableItem, season: Int?, episode: Int?) {
        key = "\(profileID.uuidString)|\(item.ref)"
        self.profileID = profileID
        ref = item.ref
        kindRaw = item.kind.rawValue
        title = item.title
        subtitle = item.subtitle
        logo = item.logo
        parentRef = item.parentRef
        self.season = season
        self.episode = episode
        stateRaw = DownloadState.queued.rawValue
        progress = 0
        bytes = 0
        expectedBytes = 0
        createdAt = .now
        updatedAt = .now
    }

    var kind: ContentKind { ContentKind(rawValue: kindRaw) ?? .movie }
    var state: DownloadState {
        get { DownloadState(rawValue: stateRaw) ?? .queued }
        set { stateRaw = newValue.rawValue; updatedAt = .now }
    }
    var playable: PlayableItem { PlayableItem(ref: ref, title: title, kind: kind, logo: logo, subtitle: subtitle, parentRef: parentRef) }
}

/// A playable entry handed to the player.
struct PlayableItem: Hashable, Codable {
    var ref: String
    var title: String
    var kind: ContentKind
    var logo: String?
    var subtitle: String?
    var parentRef: String?
}

extension Content {
    var playable: PlayableItem { PlayableItem(ref: ref, title: name, kind: kind, logo: logo) }
}

extension String {
    /// Case and accent insensitive form used for search.
    var searchFolded: String {
        folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "fr_FR"))
    }
}

enum NovaSchema {
    static let models: [any PersistentModel.Type] = [
        Profile.self, Content.self, ContentCategory.self, Favorite.self, HistoryEntry.self, DownloadRecord.self,
    ]
}
