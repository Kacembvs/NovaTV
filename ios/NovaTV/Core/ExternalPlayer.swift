import UIKit

/// Third-party players that can open a stream URL directly.
enum ExternalPlayer: String, CaseIterable, Identifiable, Codable {
    case vlc, outplayer, infuse

    var id: String { rawValue }

    var name: String {
        switch self {
        case .vlc: return "VLC"
        case .outplayer: return "Outplayer"
        case .infuse: return "Infuse"
        }
    }

    var appStoreURL: URL {
        switch self {
        case .vlc: return URL(string: "https://apps.apple.com/app/id650377962")!
        case .outplayer: return URL(string: "https://apps.apple.com/app/id1449923287")!
        case .infuse: return URL(string: "https://apps.apple.com/app/id1136220934")!
        }
    }

    func launchURL(for stream: URL) -> URL? {
        let encoded = XtreamURLs.encode(stream.absoluteString)
        switch self {
        case .vlc: return URL(string: "vlc-x-callback://x-callback-url/stream?url=\(encoded)")
        case .outplayer: return URL(string: "outplayer://\(stream.absoluteString)")
        case .infuse: return URL(string: "infuse://x-callback-url/play?url=\(encoded)")
        }
    }

    var isInstalled: Bool {
        guard let probe = launchURL(for: URL(string: "http://localhost/")!) else { return false }
        return UIApplication.shared.canOpenURL(probe)
    }

    /// Opens the stream, or the App Store page when the app is not installed.
    @MainActor
    func open(_ stream: URL) {
        if let url = launchURL(for: stream), UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
        } else {
            UIApplication.shared.open(appStoreURL)
        }
    }
}
