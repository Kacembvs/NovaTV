import Foundation
import SwiftUI
import CryptoKit

enum PlayerChoice: String, CaseIterable, Identifiable {
    case nova, vlc, outplayer, infuse
    var id: String { rawValue }
    var label: String {
        switch self {
        case .nova: return "NOVA (lecteur Apple)"
        case .vlc: return "VLC"
        case .outplayer: return "Outplayer"
        case .infuse: return "Infuse"
        }
    }
    var external: ExternalPlayer? { ExternalPlayer(rawValue: rawValue) }
}

/// All user preferences, persisted in UserDefaults and observed by the UI.
@MainActor
final class AppSettings: ObservableObject {
    private let defaults: UserDefaults

    @Published var activeProfileID: UUID? { didSet { defaults.set(activeProfileID?.uuidString, forKey: "activeProfile") } }
    @Published var paletteID: String { didSet { defaults.set(paletteID, forKey: "palette") } }
    @Published var preferredPlayer: PlayerChoice { didSet { defaults.set(preferredPlayer.rawValue, forKey: "player") } }
    /// Used for formats Apple's player cannot read (MKV, AVI…).
    @Published var fallbackPlayer: ExternalPlayer { didSet { defaults.set(fallbackPlayer.rawValue, forKey: "fallbackPlayer") } }
    @Published var autoplayNext: Bool { didSet { defaults.set(autoplayNext, forKey: "autoplay") } }
    @Published var downloadWifiOnly: Bool { didSet { defaults.set(downloadWifiOnly, forKey: "dlWifi") } }
    @Published var smartDownloads: Bool { didSet { defaults.set(smartDownloads, forKey: "dlSmart") } }

    @Published var pinHash: String? { didSet { defaults.set(pinHash, forKey: "pinHash") } }
    @Published var pinSalt: String? { didSet { defaults.set(pinSalt, forKey: "pinSalt") } }
    /// "profileID|kind|group" entries.
    @Published var lockedGroups: Set<String> { didSet { defaults.set(Array(lockedGroups), forKey: "lockedGroups") } }
    @Published var lockSettings: Bool { didSet { defaults.set(lockSettings, forKey: "lockSettings") } }
    /// Protected content shown until the app is relocked (not persisted).
    @Published var parentalUnlocked = false

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        activeProfileID = defaults.string(forKey: "activeProfile").flatMap(UUID.init(uuidString:))
        paletteID = defaults.string(forKey: "palette") ?? Palette.novaBlue.id
        preferredPlayer = PlayerChoice(rawValue: defaults.string(forKey: "player") ?? "") ?? .nova
        fallbackPlayer = ExternalPlayer(rawValue: defaults.string(forKey: "fallbackPlayer") ?? "") ?? .vlc
        autoplayNext = defaults.object(forKey: "autoplay") as? Bool ?? true
        downloadWifiOnly = defaults.object(forKey: "dlWifi") as? Bool ?? true
        smartDownloads = defaults.object(forKey: "dlSmart") as? Bool ?? true
        pinHash = defaults.string(forKey: "pinHash")
        pinSalt = defaults.string(forKey: "pinSalt")
        lockedGroups = Set(defaults.stringArray(forKey: "lockedGroups") ?? [])
        lockSettings = defaults.bool(forKey: "lockSettings")
    }

    var palette: Palette { Palette.all.first { $0.id == paletteID } ?? .novaBlue }

    // MARK: Parental control

    var pinSet: Bool { pinHash != nil && pinSalt != nil }

    static func hash(_ pin: String, salt: String) -> String {
        SHA256.hash(data: Data((salt + pin).utf8)).map { String(format: "%02x", $0) }.joined()
    }

    func setPin(_ pin: String) {
        let salt = UUID().uuidString
        pinSalt = salt
        pinHash = Self.hash(pin, salt: salt)
    }

    func verify(_ pin: String) -> Bool {
        guard let pinHash, let pinSalt else { return true }
        let ok = Self.hash(pin, salt: pinSalt) == pinHash
        if ok { parentalUnlocked = true }
        return ok
    }

    func removePin() {
        pinHash = nil
        pinSalt = nil
        lockedGroups = []
        lockSettings = false
        parentalUnlocked = false
    }

    static func groupKey(_ profileID: UUID, _ kind: ContentKind, _ group: String) -> String {
        // Episodes belong to the series catalogue.
        let k: ContentKind = kind == .episode ? .series : kind
        return "\(profileID.uuidString)|\(k.rawValue)|\(group)"
    }

    func isLocked(_ profileID: UUID, _ kind: ContentKind, _ group: String) -> Bool {
        pinSet && !parentalUnlocked && lockedGroups.contains(Self.groupKey(profileID, kind, group))
    }

    func setLocked(_ profileID: UUID, _ kind: ContentKind, _ group: String, _ locked: Bool) {
        let key = Self.groupKey(profileID, kind, group)
        if locked { lockedGroups.insert(key) } else { lockedGroups.remove(key) }
    }

    func isVisible(_ content: Content) -> Bool {
        !isLocked(content.profileID, content.kind, content.group)
    }

    var settingsBlocked: Bool { pinSet && lockSettings && !parentalUnlocked }
}

struct Palette: Identifiable, Equatable {
    let id: String
    let name: String
    let background: Color
    let background2: Color
    let card: Color
    let accent: Color
    let accent2: Color
    let text: Color
    let textSecondary: Color
    let border: Color
    let isLight: Bool

    static let novaBlue = Palette(id: "nova_blue", name: "NOVA Blue",
                                  background: Color(hex: 0x080D18), background2: Color(hex: 0x101829), card: Color(hex: 0x172238),
                                  accent: Color(hex: 0x258BFF), accent2: Color(hex: 0x43D5FF), text: .white,
                                  textSecondary: Color(hex: 0xA5B1C5), border: Color(hex: 0x27344A), isLight: false)
    static let cinema = Palette(id: "stream_red", name: "NOVA Cinéma",
                                background: Color(hex: 0x0B0B0B), background2: Color(hex: 0x161616), card: Color(hex: 0x222222),
                                accent: Color(hex: 0xE50914), accent2: Color(hex: 0xFF5A5F), text: .white,
                                textSecondary: Color(hex: 0xB3B3B3), border: Color(hex: 0x333333), isLight: false)
    static let plus = Palette(id: "monochrome", name: "NOVA+ Noir et blanc",
                              background: Color(hex: 0x080808), background2: Color(hex: 0x141414), card: Color(hex: 0x202020),
                              accent: .white, accent2: Color(hex: 0xD0D0D0), text: .white,
                              textSecondary: Color(hex: 0xB5B5B5), border: Color(hex: 0x404040), isLight: false)
    static let gold = Palette(id: "cinema_gold", name: "Cinema Gold",
                              background: Color(hex: 0x0C0A06), background2: Color(hex: 0x17130B), card: Color(hex: 0x231C10),
                              accent: Color(hex: 0xE3B341), accent2: Color(hex: 0xF5D78E), text: .white,
                              textSecondary: Color(hex: 0xC2B49A), border: Color(hex: 0x3A3020), isLight: false)
    static let violet = Palette(id: "nova_violet", name: "NOVA Univers",
                                background: Color(hex: 0x0A0820), background2: Color(hex: 0x14113A), card: Color(hex: 0x1E1A52),
                                accent: Color(hex: 0x8257FF), accent2: Color(hex: 0x43D5FF), text: .white,
                                textSecondary: Color(hex: 0xB1AACF), border: Color(hex: 0x2E2870), isLight: false)
    static let light = Palette(id: "pure_light", name: "Pure Light",
                               background: Color(hex: 0xF5F7FB), background2: .white, card: .white,
                               accent: Color(hex: 0x1F6FEB), accent2: Color(hex: 0x0EA5E9), text: Color(hex: 0x0F172A),
                               textSecondary: Color(hex: 0x5B6475), border: Color(hex: 0xD8DEE9), isLight: true)

    static let all: [Palette] = [novaBlue, cinema, plus, violet, gold, light]
}

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

private struct PaletteKey: EnvironmentKey {
    static let defaultValue = Palette.novaBlue
}

extension EnvironmentValues {
    var palette: Palette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
}
