import Foundation
import SwiftData

extension ModelContext {
    func favorite(_ profileID: UUID, _ ref: String) -> Favorite? {
        let key = "\(profileID.uuidString)|\(ref)"
        return try? fetch(FetchDescriptor<Favorite>(predicate: #Predicate<Favorite> { $0.key == key })).first
    }

    func toggleFavorite(profileID: UUID, item: PlayableItem) {
        if let existing = favorite(profileID, item.ref) {
            delete(existing)
        } else {
            insert(Favorite(profileID: profileID, ref: item.ref, kind: item.kind, name: item.title, logo: item.logo))
        }
        try? save()
    }

    func history(_ profileID: UUID, _ ref: String) -> HistoryEntry? {
        let key = "\(profileID.uuidString)|\(ref)"
        return try? fetch(FetchDescriptor<HistoryEntry>(predicate: #Predicate<HistoryEntry> { $0.key == key })).first
    }

    func saveProgress(profileID: UUID, item: PlayableItem, position: Double, duration: Double) {
        let entry = history(profileID, item.ref) ?? {
            let created = HistoryEntry(item: item, profileID: profileID)
            insert(created)
            return created
        }()
        if position.isFinite { entry.position = max(0, position) }
        if duration.isFinite && duration > 0 { entry.duration = duration }
        entry.title = item.title
        entry.subtitle = item.subtitle
        entry.logo = item.logo ?? entry.logo
        entry.updatedAt = .now
        try? save()
    }

    func content(_ profileID: UUID, ref: String) -> Content? {
        let key = "\(profileID.uuidString)|\(ref)"
        return try? fetch(FetchDescriptor<Content>(predicate: #Predicate<Content> { $0.key == key })).first
    }

    func profile(_ id: UUID?) -> Profile? {
        guard let id else { return nil }
        return try? fetch(FetchDescriptor<Profile>(predicate: #Predicate<Profile> { $0.id == id })).first
    }
}
