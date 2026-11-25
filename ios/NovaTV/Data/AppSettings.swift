import CryptoKit
import Foundation
import SwiftUI

/// How NOVA plays video internally.
enum InternalEngine: String, CaseIterable, Identifiable {
    /// Apple's player (PiP, AirPlay), and the built-in VLC engine for MKV, AVI… or when Apple fails.
    case automatic
    /// Built-in VLC engine for everything.
    case vlc
    var id: String { rawValue }
    var label: String { self == .automatic ? "Automatique (Apple + VLC intégré)" : "Toujours le moteur VLC intégré" }
}

enum PlayerChoice: String, CaseIterable, Identifiable {
    case nova, vlc, outplayer, infuse
    var id: String { rawValue }
    var label: String {
        switch self {
        case .nova: return "Lecteur NOVA (interne)"
        case .vlc: return "Application VLC"
        case .outplayer: return "Application Outplayer"
        case .infuse: return "Application Infuse"
        }
    }
    var external: ExternalPlayer? { ExternalPlayer(rawValue: rawValue) }
}

/// Same identifiers as Android (`NovaThemeId`), so backups move between devices.
enum PaletteID: String, CaseIterable, Identifiable {
    case novaBlue = "NOVA_BLUE", cinemaGold = "CINEMA_GOLD", streamRed = "STREAM_RED", pureLight = "PURE_LIGHT",
         custom = "CUSTOM", monochrome = "MONOCHROME", universeIndigo = "UNIVERSE_INDIGO", horizonOcean = "HORIZON_OCEAN",
         guideEmerald = "GUIDE_EMERALD"
    var id: String { rawValue }
    var label: String {
        switch self {
        case .novaBlue: return "NOVA Blue"
        case .cinemaGold: return "Cinema Gold"
        case .streamRed: return "Stream Red"
        case .pureLight: return "Pure Light"
        case .custom: return "Personnalisé"
        case .monochrome: return "NOVA+ Noir et blanc"
        case .universeIndigo: return "Univers Indigo"
        case .horizonOcean: return "Horizon Océan"
        case .guideEmerald: return "Guide Émeraude"
        }
    }
}

/// Same interfaces as Android (`TvInterface`): layout choices applied on top of a palette.
enum NovaStyle: String, CaseIterable, Identifiable {
    case classic = "CLASSIC", cinema = "CINEMA", studios = "STUDIOS", antenna = "ANTENNA", discovery = "DISCOVERY",
         guide = "GUIDE", plus = "PLUS"
    var id: String { rawValue }

    var label: String {
        switch self {
        case .classic: return "NOVA Classique"
        case .cinema: return "NOVA Cinéma"
        case .studios: return "NOVA Univers"
        case .antenna: return "NOVA Antenne"
        case .discovery: return "NOVA Horizon"
        case .guide: return "NOVA Guide"
        case .plus: return "NOVA+"
        }
    }

    var summary: String {
        switch self {
        case .classic: return "L'interface actuelle, conservée comme repère."
        case .cinema: return "Grande scène, rail discret et affiches de cinéma."
        case .studios: return "Collections et grandes affiches."
        case .antenna: return "Direct prioritaire et cartes de programmes."
        case .discovery: return "Reprises en tête et découverte en cartes larges."
        case .guide: return "Listes IPTV détaillées et compactes."
        case .plus: return "Noir et blanc, angles droits, direct et collections."
        }
    }

    /// Corner radius of cards, buttons and panels (Android `cardRadius`).
    var radius: CGFloat {
        switch self {
        case .classic: return 14
        case .cinema: return 6
        case .studios: return 20
        case .antenna: return 12
        case .discovery: return 10
        case .guide: return 4
        case .plus: return 2
        }
    }

    var defaultPalette: PaletteID {
        switch self {
        case .classic: return .novaBlue
        case .cinema: return .streamRed
        case .studios: return .universeIndigo
        case .antenna: return .cinemaGold
        case .discovery: return .horizonOcean
        case .guide: return .guideEmerald
        case .plus: return .monochrome
        }
    }

    /// Catalogue cards are landscape in Antenne / Horizon, like on Android.
    var landscapeCards: Bool { self == .antenna || self == .discovery }
    var heroHeight: CGFloat {
        switch self {
        case .cinema: return 440
        case .guide: return 280
        case .antenna: return 320
        default: return 380
        }
    }
    /// Live first on the home screen.
    var liveFirst: Bool { self == .antenna || self == .guide || self == .plus }
}

let preferredLanguageCodes = ["fr", "en", "ar", "es", "de", "it", "pt", "tr"]

func languageName(_ code: String) -> String {
    Locale(identifier: "fr_FR").localizedString(forLanguageCode: code)?.capitalized ?? code
}

/// All user preferences, persisted in UserDefaults and observed by the UI.
@MainActor
final class AppSettings: ObservableObject {
    private let defaults: UserDefaults

    @Published var activeProfileID: UUID? { didSet { defaults.set(activeProfileID?.uuidString, forKey: "activeProfile") } }
    @Published var paletteID: PaletteID { didSet { defaults.set(paletteID.rawValue, forKey: "paletteID") } }
    @Published var customAccent: UInt32 { didSet { defaults.set(Int(customAccent), forKey: "customAccent") } }
    @Published var style: NovaStyle { didSet { defaults.set(style.rawValue, forKey: "style") } }

    @Published var preferredPlayer: PlayerChoice { didSet { defaults.set(preferredPlayer.rawValue, forKey: "player") } }
    @Published var internalEngine: InternalEngine { didSet { defaults.set(internalEngine.rawValue, forKey: "engine") } }
    @Published var autoplayNext: Bool { didSet { defaults.set(autoplayNext, forKey: "autoplay") } }
    @Published var countdownSeconds: Int { didSet { defaults.set(countdownSeconds, forKey: "countdown") } }
    @Published var gesturesEnabled: Bool { didSet { defaults.set(gesturesEnabled, forKey: "gestures") } }
    @Published var freeRotation: Bool { didSet { defaults.set(freeRotation, forKey: "freeRotation") } }
    @Published var pictureInPicture: Bool { didSet { defaults.set(pictureInPicture, forKey: "pip") } }

    @Published var audioLanguage: String? { didSet { defaults.set(audioLanguage, forKey: "langAudio") } }
    @Published var subtitleLanguage: String? { didSet { defaults.set(subtitleLanguage, forKey: "langSubtitles") } }
    @Published var autoRefresh: Bool { didSet { defaults.set(autoRefresh, forKey: "autoRefresh") } }

    @Published var downloadWifiOnly: Bool { didSet { defaults.set(downloadWifiOnly, forKey: "dlWifi") } }
    @Published var smartDownloads: Bool { didSet { defaults.set(smartDownloads, forKey: "dlSmart") } }

    @Published var pinHash: String? { didSet { defaults.set(pinHash, forKey: "pinHash") } }
    @Published var pinSalt: String? { didSet { defaults.set(pinSalt, forKey: "pinSalt") } }
    /// "profileID|kind|group" entries.
    @Published var lockedGroups: Set<String> { didSet { defaults.set(Array(lockedGroups), forKey: "lockedGroups") } }
    @Published var lockSettings: Bool { didSet { defaults.set(lockSettings, forKey: "lockSettings") } }
    /// Protected content shown until the app is relocked (not persisted).
    @Published var parentalUnlocked = false

    static let countdownChoices = [5, 10, 15, 20]
    static let customAccents: [UInt32] = [0x43D5FF, 0x8257FF, 0x2ED47A, 0xFF8A3D, 0xFF4FA3, 0xE8C45C, 0xFF4D5A, 0xB0BEC5]

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        func bool(_ key: String, _ fallback: Bool) -> Bool { defaults.object(forKey: key) as? Bool ?? fallback }
        activeProfileID = defaults.string(forKey: "activeProfile").flatMap(UUID.init(uuidString:))
        // First versions stored lowercase palette names.
        let legacy = ["nova_blue": PaletteID.novaBlue, "stream_red": .streamRed, "monochrome": .monochrome,
                      "cinema_gold": .cinemaGold, "pure_light": .pureLight, "nova_violet": .novaBlue]
        paletteID = PaletteID(rawValue: defaults.string(forKey: "paletteID") ?? "")
            ?? legacy[defaults.string(forKey: "palette") ?? ""] ?? .novaBlue
        customAccent = UInt32(defaults.object(forKey: "customAccent") as? Int ?? Int(Self.customAccents[0]))
        style = NovaStyle(rawValue: defaults.string(forKey: "style") ?? "") ?? .classic
        preferredPlayer = PlayerChoice(rawValue: defaults.string(forKey: "player") ?? "") ?? .nova
        internalEngine = InternalEngine(rawValue: defaults.string(forKey: "engine") ?? "") ?? .automatic
        autoplayNext = bool("autoplay", true)
        countdownSeconds = defaults.object(forKey: "countdown") as? Int ?? 20
        gesturesEnabled = bool("gestures", true)
        freeRotation = bool("freeRotation", false)
        pictureInPicture = bool("pip", true)
        audioLanguage = defaults.string(forKey: "langAudio")
        subtitleLanguage = defaults.string(forKey: "langSubtitles")
        autoRefresh = bool("autoRefresh", true)
        downloadWifiOnly = bool("dlWifi", true)
        smartDownloads = bool("dlSmart", true)
        pinHash = defaults.string(forKey: "pinHash")
        pinSalt = defaults.string(forKey: "pinSalt")
        lockedGroups = Set(defaults.stringArray(forKey: "lockedGroups") ?? [])
        lockSettings = defaults.bool(forKey: "lockSettings")
    }

    var palette: Palette { Palette.of(paletteID, customAccent: customAccent) }

    /// Choosing an interface also applies its colours, like on Android; the palette can be changed afterwards.
    func applyStyle(_ newStyle: NovaStyle) {
        style = newStyle
        paletteID = newStyle.defaultPalette
    }

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

struct Palette: Equatable {
    let id: PaletteID
    let background: Color
    let background2: Color
    let card: Color
    let accent: Color
    let accent2: Color
    let text: Color
    let textSecondary: Color
    let border: Color
    let error: Color
    let isLight: Bool
    /// Luminance of the accent, used to pick readable text on accent surfaces.
    let accentLuminance: Double

    var name: String { id.label }
    /// Text and icons drawn on top of the accent colour (black on NOVA+ white buttons).
    var onAccent: Color { accentLuminance > 0.55 ? Color(hex: 0x0A0A0A) : .white }
    /// Selected chips/segments: the accent unless it would vanish on the light palette.
    var selection: Color { accent }

    init(id: PaletteID, background: UInt32, background2: UInt32, card: UInt32, accent: UInt32, accent2: UInt32,
         text: UInt32, textSecondary: UInt32, border: UInt32, error: UInt32, isLight: Bool = false) {
        self.id = id
        self.background = Color(hex: background)
        self.background2 = Color(hex: background2)
        self.card = Color(hex: card)
        self.accent = Color(hex: accent)
        self.accent2 = Color(hex: accent2)
        self.text = Color(hex: text)
        self.textSecondary = Color(hex: textSecondary)
        self.border = Color(hex: border)
        self.error = Color(hex: error)
        self.isLight = isLight
        accentLuminance = Self.luminance(accent)
    }

    static func luminance(_ hex: UInt32) -> Double {
        func channel(_ v: UInt32) -> Double {
            let c = Double(v) / 255
            return c <= 0.03928 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel((hex >> 16) & 0xFF) + 0.7152 * channel((hex >> 8) & 0xFF) + 0.0722 * channel(hex & 0xFF)
    }

    static func of(_ id: PaletteID, customAccent: UInt32 = AppSettings.customAccents[0]) -> Palette {
        switch id {
        case .novaBlue:
            return Palette(id: id, background: 0x080D18, background2: 0x101829, card: 0x172238, accent: 0x258BFF, accent2: 0x43D5FF,
                           text: 0xFFFFFF, textSecondary: 0xA5B1C5, border: 0x27344A, error: 0xFF6B7A)
        case .cinemaGold:
            return Palette(id: id, background: 0x0B0A08, background2: 0x15130F, card: 0x221E17, accent: 0xD4A63A, accent2: 0xE8C45C,
                           text: 0xFFF8E8, textSecondary: 0xBDB29A, border: 0x3A3326, error: 0xFF6B6B)
        case .streamRed:
            return Palette(id: id, background: 0x0A0A0B, background2: 0x141416, card: 0x202024, accent: 0xE50914, accent2: 0xFF4D5A,
                           text: 0xFFFFFF, textSecondary: 0xB3B3B8, border: 0x34343A, error: 0xFF8A65)
        case .monochrome:
            return Palette(id: id, background: 0x080808, background2: 0x141414, card: 0x202020, accent: 0xFFFFFF, accent2: 0xD0D0D0,
                           text: 0xFFFFFF, textSecondary: 0xB5B5B5, border: 0x404040, error: 0xFF6B6B)
        case .pureLight:
            return Palette(id: id, background: 0xF5F7FB, background2: 0xFFFFFF, card: 0xE9EEF6, accent: 0x0A72D9, accent2: 0x6A3FE0,
                           text: 0x0E1525, textSecondary: 0x55627A, border: 0xCCD5E3, error: 0xD32F45, isLight: true)
        case .universeIndigo:
            return Palette(id: id, background: 0x070718, background2: 0x11112E, card: 0x1C1B45, accent: 0x7B5CFF, accent2: 0xA78BFF,
                           text: 0xFFFFFF, textSecondary: 0xB4AFD6, border: 0x2E2B66, error: 0xFF6B7A)
        case .horizonOcean:
            return Palette(id: id, background: 0x0F171E, background2: 0x16222C, card: 0x1F2E3B, accent: 0x0090C8, accent2: 0x7FD9FF,
                           text: 0xFFFFFF, textSecondary: 0x9FB3C4, border: 0x2C3F50, error: 0xFF6B7A)
        case .guideEmerald:
            return Palette(id: id, background: 0x0B100E, background2: 0x131A17, card: 0x1C2622, accent: 0x1FB866, accent2: 0x8BE8B5,
                           text: 0xFFFFFF, textSecondary: 0xA3B8AD, border: 0x2A3A33, error: 0xFF6B7A)
        case .custom:
            return Palette(id: id, background: 0x080D18, background2: 0x101829, card: 0x172238, accent: customAccent, accent2: 0x8257FF,
                           text: 0xFFFFFF, textSecondary: 0xA5B1C5, border: 0x27344A, error: 0xFF6B7A)
        }
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

private struct PaletteKey: EnvironmentKey {
    static let defaultValue = Palette.of(.novaBlue)
}

private struct StyleKey: EnvironmentKey {
    static let defaultValue = NovaStyle.classic
}

extension EnvironmentValues {
    var palette: Palette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }

    var novaStyle: NovaStyle {
        get { self[StyleKey.self] }
        set { self[StyleKey.self] = newValue }
    }
}
