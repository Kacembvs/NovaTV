import XCTest
@testable import NovaTV

final class ParserTests: XCTestCase {
    func testGroupSearchIgnoresAccentsAndCase() {
        let groups = [GroupEntry(key: "a", title: "Cinéma | FR"), GroupEntry(key: "b", title: "Sport"), GroupEntry(key: "c", title: "CINEMA UK")]
        XCTAssertEqual(filterGroups(groups, query: "cinema").map(\.key), ["a", "c"])
        XCTAssertEqual(filterGroups(groups, query: "  ").map(\.key), ["a", "b", "c"])
        XCTAssertTrue(filterGroups(groups, query: "docu").isEmpty)
    }

    func testM3UEntriesKeepGroupsLogosAndNamesWithCommas() {
        let playlist = """
        \u{FEFF}#EXTM3U url-tvg="http://epg.example/guide.xml.gz"
        #EXTINF:-1 tvg-id="tf1.fr" tvg-logo="http://logo/tf1.png" group-title="France, TNT",TF1 HD
        http://srv/live/u/p/1.ts
        #EXTINF:-1 group-title="Films",Le film, la suite
        http://srv/movie/u/p/10.mkv
        #EXTINF:-1,Sans groupe
        #EXTGRP:Séries
        http://srv/series/u/p/20.mp4
        """
        let result = M3UParser.parse(playlist)
        XCTAssertEqual(result.epgURL, "http://epg.example/guide.xml.gz")
        XCTAssertEqual(result.entries.count, 3)
        XCTAssertEqual(result.entries[0], M3UEntry(name: "TF1 HD", url: "http://srv/live/u/p/1.ts", group: "France, TNT",
                                                   logo: "http://logo/tf1.png", tvgID: "tf1.fr", kind: .live))
        XCTAssertEqual(result.entries[1].name, "Le film, la suite")
        XCTAssertEqual(result.entries[1].kind, .movie)
        XCTAssertEqual(result.entries[2].group, "Séries")
        XCTAssertEqual(result.entries[2].kind, .series)
    }

    func testXtreamAddressesAreNormalizedAndCredentialsEncoded() {
        XCTAssertEqual(XtreamURLs.normalizeBase("srv.example:8080/player_api.php?username=a"), "http://srv.example:8080")
        XCTAssertEqual(XtreamURLs.normalizeBase("https://srv.example/get.php/"), "https://srv.example")
        XCTAssertEqual(XtreamURLs.streamURL(base: "http://srv:80/", username: "jean dupont", password: "p@ss/1", ref: "movie/12.mkv"),
                       "http://srv:80/movie/jean%20dupont/p%40ss%2F1/12.mkv")
    }

    func testLiveTransportStreamsAreRequestedAsHLSForApplesPlayer() {
        let ts = URL(string: "http://srv/live/u/p/123.ts")!
        XCTAssertEqual(StreamURLs.appleCompatible(ts, kind: .live).absoluteString, "http://srv/live/u/p/123.m3u8")
        XCTAssertEqual(StreamURLs.appleCompatible(URL(string: "http://srv/movie/u/p/1.ts")!, kind: .movie).absoluteString, "http://srv/movie/u/p/1.ts")
        XCTAssertTrue(StreamURLs.needsExternalPlayer(URL(string: "http://srv/movie/u/p/1.mkv")!))
        XCTAssertFalse(StreamURLs.needsExternalPlayer(URL(string: "http://srv/movie/u/p/1.mp4")!))
    }

    func testGzipGuidesAreDecompressedAndProgrammesParsed() throws {
        let gzip = try XCTUnwrap(Data(base64Encoded: "H4sIAAAAAAAEAFWPTQ6CMBCFr9J0a6AtblxMS9ywcokHaErBmv6QthA9kufwYkIkBmcxmXwvM+8N1A9n0axjMsFzzEqKkfYqdMYPHF/bpjjhWkCeBaib9F5bZDqOc8/KPmIBnUmjlc/CS6dF2zAgfwTItiVgjGGI0jmNUpYxc1zRI6WMsoquhQ5rx4sYxp3G9tp2a2efTbZaXDS6hyl6aYF8CXQ6KXFWeZLW5PcrLblWAuQXY5mXrz45ffo8AAEAAA=="))
        let xml = Gzip.decompressIfNeeded(gzip)
        XCTAssertTrue(String(decoding: xml, as: UTF8.self).hasPrefix("<?xml"))
        let start = XMLTVParser.date("20300101200000 +0000")!
        let parser = XMLTVParser(window: start.addingTimeInterval(-60)...start.addingTimeInterval(3600))
        XCTAssertTrue(parser.parse(xml))
        XCTAssertEqual(parser.channelNames["tf1.fr"], "TF1")
        let programme = try XCTUnwrap(parser.programmes["tf1.fr"]?.first)
        XCTAssertEqual(programme.title, "Le journal")
        XCTAssertEqual(programme.summary, "Actualités")
        XCTAssertEqual(programme.end.timeIntervalSince(programme.start), 3600)
        XCTAssertEqual(Gzip.decompressIfNeeded(Data("plain".utf8)), Data("plain".utf8))
    }

    @MainActor
    func testParentalPinLocksCategoriesUntilUnlocked() {
        let defaults = UserDefaults(suiteName: "nova-tests-\(UUID().uuidString)")!
        let settings = AppSettings(defaults: defaults)
        let profile = UUID()
        settings.setPin("1234")
        settings.setLocked(profile, .movie, "Adultes", true)
        XCTAssertTrue(settings.isLocked(profile, .movie, "Adultes"))
        XCTAssertFalse(settings.isLocked(profile, .series, "Adultes"))
        XCTAssertFalse(settings.verify("0000"))
        XCTAssertTrue(settings.isLocked(profile, .movie, "Adultes"))
        XCTAssertTrue(settings.verify("1234"))
        XCTAssertFalse(settings.isLocked(profile, .movie, "Adultes"))
        settings.removePin()
        XCTAssertTrue(settings.lockedGroups.isEmpty)
        XCTAssertEqual(AppSettings(defaults: defaults).pinSet, false)
    }
}
