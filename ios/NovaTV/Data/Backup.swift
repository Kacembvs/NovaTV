import CommonCrypto
import CryptoKit
import Foundation
import SwiftData

/// Same file format as the Android app ("nova-iptv-backup" v1): a backup moves between Android and iPhone.
/// Passwords are only exported with a backup code (PBKDF2-SHA256 + AES-GCM), never in clear.
@MainActor
enum BackupManager {
    static let format = "nova-iptv-backup"
    static let version = 1
    private static let checkValue = "NOVA"
    private static let iterations: UInt32 = 60_000

    struct RestoreResult {
        var profilesAdded = 0
        var profilesMerged = 0
        var favorites = 0
        var history = 0
        var missingPasswords = 0
        var newProfiles: [Profile] = []

        var summary: String {
            var text = "Restauration terminée : \(profilesAdded) profil(s) ajouté(s)"
            if profilesMerged > 0 { text += ", \(profilesMerged) déjà présent(s)" }
            text += ", \(favorites) favori(s), \(history) reprise(s)."
            if missingPasswords > 0 { text += " \(missingPasswords) profil(s) Xtream sans mot de passe : modifiez-les pour le ressaisir." }
            return text
        }
    }

    enum BackupError: LocalizedError {
        case notABackup, tooRecent, codeRequired, wrongCode
        var errorDescription: String? {
            switch self {
            case .notABackup: return "Ce fichier n'est pas une sauvegarde NOVA IPTV."
            case .tooRecent: return "Sauvegarde créée par une version plus récente de NOVA."
            case .codeRequired: return "Cette sauvegarde contient des mots de passe : indiquez son code."
            case .wrongCode: return "Code de sauvegarde incorrect."
            }
        }
    }

    // MARK: Export

    static func export(context: ModelContext, settings: AppSettings, code: String?) throws -> Data {
        let key: SymmetricKey?
        var root: [String: Any] = ["format": format, "version": version, "createdAt": millis(.now)]
        if let code, !code.isEmpty {
            let salt = Data((0..<16).map { _ in UInt8.random(in: 0...255) })
            let derived = try deriveKey(code, salt: salt)
            key = derived
            root["security"] = ["salt": salt.base64EncodedString(), "check": try seal(derived, checkValue)]
        } else {
            key = nil
        }
        root["settings"] = [
            "autoplayNext": settings.autoplayNext,
            "countdownSeconds": settings.countdownSeconds,
            "gesturesEnabled": settings.gesturesEnabled,
            "freeRotation": settings.freeRotation,
            "pictureInPicture": settings.pictureInPicture,
            "theme": settings.paletteID.rawValue,
            "tvInterface": settings.style.rawValue,
            "customAccent": Int(Int32(bitPattern: 0xFF00_0000 | settings.customAccent)),
        ] as [String: Any]

        let profiles = try context.fetch(FetchDescriptor<Profile>(sortBy: [SortDescriptor(\.createdAt)]))
        var numbers: [UUID: Int] = [:]
        root["profiles"] = try profiles.enumerated().map { offset, p -> [String: Any] in
            numbers[p.id] = offset + 1
            var entry: [String: Any] = ["id": offset + 1, "name": p.name, "type": p.type == .m3u ? "M3U" : "XTREAM",
                                        "url": p.url, "createdAt": millis(p.createdAt)]
            if let username = p.username { entry["username"] = username }
            if let epg = p.customEPGURL { entry["customEpgUrl"] = epg }
            let password = Keychain.password(for: p)
            if let key, !password.isEmpty { entry["password"] = try seal(key, password) }
            return entry
        }
        let types = Dictionary(profiles.map { ($0.id, $0.type) }, uniquingKeysWith: { a, _ in a })
        root["favorites"] = try context.fetch(FetchDescriptor<Favorite>()).compactMap { f -> [String: Any]? in
            guard let number = numbers[f.profileID] else { return nil }
            var entry = item(number, androidRef(f.ref, types[f.profileID]), f.kind, f.name, f.logo)
            entry["createdAt"] = millis(f.addedAt)
            return entry
        }
        root["history"] = try context.fetch(FetchDescriptor<HistoryEntry>()).compactMap { h -> [String: Any]? in
            guard let number = numbers[h.profileID] else { return nil }
            var entry = item(number, androidRef(h.ref, types[h.profileID]), h.kind, h.title, h.logo)
            entry["positionMs"] = Int64(h.position * 1000)
            entry["durationMs"] = Int64(h.duration * 1000)
            entry["updatedAt"] = millis(h.updatedAt)
            if let subtitle = h.subtitle { entry["subtitle"] = subtitle }
            if let parent = h.parentRef { entry["parentKey"] = parent }
            return entry
        }
        root["ratings"] = [Any]()
        root["customGroups"] = [Any]()
        return try JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted, .sortedKeys])
    }

    // MARK: Restore

    static func restore(_ data: Data, code: String?, context: ModelContext, settings: AppSettings) throws -> RestoreResult {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any], root["format"] as? String == format else {
            throw BackupError.notABackup
        }
        if (root["version"] as? Int ?? 0) > version { throw BackupError.tooRecent }
        var key: SymmetricKey?
        if let security = root["security"] as? [String: Any] {
            guard let code, !code.isEmpty else { throw BackupError.codeRequired }
            guard let saltText = security["salt"] as? String, let salt = Data(base64Encoded: saltText) else { throw BackupError.notABackup }
            let derived = try deriveKey(code, salt: salt)
            guard let check = security["check"] as? String, (try? open(derived, check)) == checkValue else { throw BackupError.wrongCode }
            key = derived
        }

        var result = RestoreResult()
        var map: [String: Profile] = [:]
        let existing = try context.fetch(FetchDescriptor<Profile>())
        for case let p as [String: Any] in root["profiles"] as? [Any] ?? [] {
            guard let typeName = p["type"] as? String, let url = (p["url"] as? String)?.trimmingCharacters(in: .whitespaces) else { continue }
            let type: ProfileType = typeName.uppercased() == "XTREAM" ? .xtream : .m3u
            let username = p["username"] as? String
            let backupID = "\(p["id"] ?? "")"
            if let match = existing.first(where: { $0.type == type && $0.url.trimmingCharacters(in: .whitespaces) == url && $0.username == username }) {
                map[backupID] = match
                result.profilesMerged += 1
                continue
            }
            let password = (p["password"] as? String).flatMap { sealed in key.flatMap { try? open($0, sealed) } }
            if type == .xtream && password == nil { result.missingPasswords += 1 }
            let profile = Profile(name: p["name"] as? String ?? "Profil", type: type, url: url, username: username,
                                  customEPGURL: p["customEpgUrl"] as? String)
            if let created = p["createdAt"] as? Double { profile.createdAt = Date(timeIntervalSince1970: created / 1000) }
            context.insert(profile)
            if let password { Keychain.set(password, for: profile.id.uuidString) }
            map[backupID] = profile
            result.newProfiles.append(profile)
            result.profilesAdded += 1
        }

        for case let f as [String: Any] in root["favorites"] as? [Any] ?? [] {
            guard let profile = map["\(f["profileId"] ?? "")"], let ref = f["itemKey"] as? String, let title = f["title"] as? String else { continue }
            let localRef = iosRef(ref, profile.type)
            if context.favorite(profile.id, localRef) != nil { continue }
            let favorite = Favorite(profileID: profile.id, ref: localRef, kind: kind(f["kind"]), name: title, logo: f["logo"] as? String)
            if let created = f["createdAt"] as? Double, created > 0 { favorite.addedAt = Date(timeIntervalSince1970: created / 1000) }
            context.insert(favorite)
            result.favorites += 1
        }
        for case let h as [String: Any] in root["history"] as? [Any] ?? [] {
            guard let profile = map["\(h["profileId"] ?? "")"], let ref = h["itemKey"] as? String, let title = h["title"] as? String else { continue }
            let localRef = iosRef(ref, profile.type)
            let updated = Date(timeIntervalSince1970: (h["updatedAt"] as? Double ?? 0) / 1000)
            // Never overwrite a more recent local progress.
            if let local = context.history(profile.id, localRef), local.updatedAt >= updated { continue }
            let item = PlayableItem(ref: localRef, title: title, kind: kind(h["kind"]), logo: h["logo"] as? String,
                                    subtitle: h["subtitle"] as? String, parentRef: h["parentKey"] as? String)
            context.saveProgress(profileID: profile.id, item: item, position: (h["positionMs"] as? Double ?? 0) / 1000,
                                 duration: (h["durationMs"] as? Double ?? 0) / 1000)
            context.history(profile.id, localRef)?.updatedAt = updated
            result.history += 1
        }
        try context.save()

        if let s = root["settings"] as? [String: Any] {
            if let v = s["autoplayNext"] as? Bool { settings.autoplayNext = v }
            if let v = s["countdownSeconds"] as? Int { settings.countdownSeconds = v }
            if let v = s["gesturesEnabled"] as? Bool { settings.gesturesEnabled = v }
            if let v = s["freeRotation"] as? Bool { settings.freeRotation = v }
            if let v = s["pictureInPicture"] as? Bool { settings.pictureInPicture = v }
            if let v = s["customAccent"] as? Int { settings.customAccent = UInt32(truncatingIfNeeded: v) & 0xFF_FFFF }
            if let v = (s["tvInterface"] as? String).flatMap(NovaStyle.init(rawValue:)) { settings.style = v }
            if let v = (s["theme"] as? String).flatMap(PaletteID.init(rawValue:)) { settings.paletteID = v }
        }
        return result
    }

    // MARK: Helpers

    /// Android plays Xtream live channels as ".ts", iOS asks the same server for HLS (".m3u8").
    static func androidRef(_ ref: String, _ type: ProfileType?) -> String {
        guard type == .xtream, ref.hasPrefix("live/"), ref.hasSuffix(".m3u8") else { return ref }
        return String(ref.dropLast(5)) + ".ts"
    }

    static func iosRef(_ ref: String, _ type: ProfileType) -> String {
        guard type == .xtream, ref.hasPrefix("live/"), ref.hasSuffix(".ts") else { return ref }
        return String(ref.dropLast(3)) + ".m3u8"
    }

    private static func kind(_ value: Any?) -> ContentKind {
        ContentKind(rawValue: (value as? String ?? "").lowercased()) ?? .movie
    }

    private static func item(_ profile: Int, _ ref: String, _ kind: ContentKind, _ title: String, _ logo: String?) -> [String: Any] {
        var entry: [String: Any] = ["profileId": profile, "itemKey": ref, "kind": kind.rawValue.uppercased(), "title": title]
        if let logo { entry["logo"] = logo }
        return entry
    }

    private static func millis(_ date: Date) -> Int64 { Int64(date.timeIntervalSince1970 * 1000) }

    static func deriveKey(_ code: String, salt: Data) throws -> SymmetricKey {
        var derived = [UInt8](repeating: 0, count: 32)
        let password = Array(code.utf8)
        let status = salt.withUnsafeBytes { saltBytes in
            password.withUnsafeBufferPointer { passwordBytes in
                CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2),
                                     passwordBytes.baseAddress.map { UnsafeRawPointer($0).assumingMemoryBound(to: CChar.self) },
                                     password.count,
                                     saltBytes.bindMemory(to: UInt8.self).baseAddress, salt.count,
                                     CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), iterations, &derived, derived.count)
            }
        }
        guard status == 0 else { throw NovaError("Chiffrement de la sauvegarde impossible.") }
        return SymmetricKey(data: derived)
    }

    /// iv (12 bytes) + ciphertext + tag, Base64: the layout produced by Android's AES/GCM/NoPadding.
    static func seal(_ key: SymmetricKey, _ plain: String) throws -> String {
        guard let combined = try AES.GCM.seal(Data(plain.utf8), using: key).combined else { throw NovaError("Chiffrement impossible.") }
        return combined.base64EncodedString()
    }

    static func open(_ key: SymmetricKey, _ sealed: String) throws -> String {
        guard let data = Data(base64Encoded: sealed) else { throw BackupError.notABackup }
        let plain = try AES.GCM.open(AES.GCM.SealedBox(combined: data), using: key)
        return String(decoding: plain, as: UTF8.self)
    }
}
