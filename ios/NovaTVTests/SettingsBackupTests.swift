import SwiftData
import XCTest
@testable import NovaTV

@MainActor
final class SettingsBackupTests: XCTestCase {
    private func makeSettings() -> AppSettings {
        AppSettings(defaults: UserDefaults(suiteName: "nova-tests-\(UUID().uuidString)")!)
    }

    private func makeContext() throws -> ModelContext {
        let container = try ModelContainer(for: Schema(NovaSchema.models), configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        return ModelContext(container)
    }

    /// Salt, check value and password sealed with code "1234" by an independent PBKDF2-SHA256 / AES-GCM
    /// implementation, exactly like Android's BackupManager does.
    private let androidBackup = """
    {"format":"nova-iptv-backup","version":1,"createdAt":1700000000000,
     "security":{"salt":"AAECAwQFBgcICQoLDA0ODw==","check":"ZGVmZ2hpamtsbW5vZvKBEObGPjDc2l/QttERNkW/8WA="},
     "settings":{"autoplayNext":false,"countdownSeconds":10,"gesturesEnabled":true,"freeRotation":true,
                 "pictureInPicture":false,"theme":"MONOCHROME","tvInterface":"PLUS","customAccent":-12345},
     "profiles":[{"id":7,"name":"Salon","type":"XTREAM","url":"http://srv.example:8080","username":"jean",
                  "password":"ZGVmZ2hpamtsbW5vRdKjNf2tuiypAqo0NcMW2/7vNWF/781WoX7cPg==","createdAt":1700000000000}],
     "favorites":[{"profileId":7,"itemKey":"live/12.ts","kind":"LIVE","title":"TF1","createdAt":1700000000000}],
     "history":[{"profileId":7,"itemKey":"movie/3.mkv","kind":"MOVIE","title":"Le film","positionMs":60000,
                 "durationMs":7200000,"updatedAt":1700000000000}],
     "ratings":[],"customGroups":[]}
    """

    func testAndroidBackupRestoresProfilesPasswordsLibraryAndSettings() throws {
        let context = try makeContext()
        let settings = makeSettings()
        let data = Data(androidBackup.utf8)
        XCTAssertThrowsError(try BackupManager.restore(data, code: nil, context: context, settings: settings)) {
            XCTAssertEqual($0 as? BackupManager.BackupError, .codeRequired)
        }
        XCTAssertThrowsError(try BackupManager.restore(data, code: "0000", context: context, settings: settings)) {
            XCTAssertEqual($0 as? BackupManager.BackupError, .wrongCode)
        }
        let result = try BackupManager.restore(data, code: "1234", context: context, settings: settings)
        XCTAssertEqual(result.profilesAdded, 1)
        XCTAssertEqual(result.favorites, 1)
        XCTAssertEqual(result.history, 1)
        XCTAssertEqual(result.missingPasswords, 0)
        let profile = try XCTUnwrap(result.newProfiles.first)
        defer { Keychain.set(nil, for: profile.id.uuidString) }
        XCTAssertEqual(profile.type, .xtream)
        XCTAssertEqual(profile.username, "jean")
        XCTAssertEqual(Keychain.password(for: profile), "motdepasse-é")
        // Android's raw TS live reference becomes the HLS one iOS plays.
        XCTAssertNotNil(context.favorite(profile.id, "live/12.m3u8"))
        XCTAssertEqual(context.history(profile.id, "movie/3.mkv")?.position, 60)
        XCTAssertEqual(settings.style, .plus)
        XCTAssertEqual(settings.paletteID, .monochrome)
        XCTAssertEqual(settings.countdownSeconds, 10)
        XCTAssertFalse(settings.autoplayNext)
        XCTAssertFalse(settings.pictureInPicture)

        // Restoring the same file again merges instead of duplicating.
        XCTAssertEqual(try BackupManager.restore(data, code: "1234", context: context, settings: settings).profilesMerged, 1)

        let exported = try BackupManager.export(context: context, settings: settings, code: "9876")
        let text = String(decoding: exported, as: UTF8.self)
        XCTAssertTrue(text.contains("\"live\\/12.ts\"") || text.contains("\"live/12.ts\""), "Android keeps its own live references")
        XCTAssertFalse(text.contains("motdepasse"), "Passwords are never exported in clear")

        let other = try makeContext()
        let copy = try BackupManager.restore(exported, code: "9876", context: other, settings: makeSettings())
        let restored = try XCTUnwrap(copy.newProfiles.first)
        defer { Keychain.set(nil, for: restored.id.uuidString) }
        XCTAssertEqual(Keychain.password(for: restored), "motdepasse-é")
        XCTAssertNotNil(other.favorite(restored.id, "live/12.m3u8"))
    }

    func testInterfacesApplyTheirPaletteAndNovaPlusKeepsReadableButtons() {
        let settings = makeSettings()
        settings.applyStyle(.plus)
        XCTAssertEqual(settings.paletteID, .monochrome)
        XCTAssertEqual(NovaStyle.plus.radius, 2)
        settings.applyStyle(.cinema)
        XCTAssertEqual(settings.paletteID, .streamRed)
        settings.paletteID = .cinemaGold
        XCTAssertEqual(settings.style, .cinema, "Changing colours keeps the interface")
        // White accent: labels on buttons and switch knobs must be dark.
        XCTAssertGreaterThan(Palette.of(.monochrome).accentLuminance, 0.55)
        XCTAssertLessThan(Palette.of(.novaBlue).accentLuminance, 0.55)
    }

    func testBuiltInVLCEngineTakesFormatsApplesPlayerCannotRead() {
        XCTAssertTrue(PlayerController.needsVLC(URL(string: "http://srv/movie/u/p/1.mkv")!))
        XCTAssertTrue(PlayerController.needsVLC(URL(string: "http://srv/movie/u/p/1.AVI")!))
        XCTAssertTrue(PlayerController.needsVLC(URL(fileURLWithPath: "/tmp/episode.mkv")))
        XCTAssertFalse(PlayerController.needsVLC(URL(string: "http://srv/movie/u/p/1.mp4")!))
        XCTAssertFalse(PlayerController.needsVLC(URL(string: "http://srv/live/u/p/1.m3u8")!))
    }
}
