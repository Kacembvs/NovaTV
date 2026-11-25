import AVFoundation
import SwiftData
import SwiftUI

final class AppDelegate: NSObject, UIApplicationDelegate {
    static var backgroundCompletion: (() -> Void)?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // Playback continues in the background and in Picture-in-Picture.
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        return true
    }

    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        OrientationLock.mask
    }

    func application(_ application: UIApplication, handleEventsForBackgroundURLSession identifier: String, completionHandler: @escaping () -> Void) {
        Self.backgroundCompletion = completionHandler
    }
}

@main
struct NovaApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @Environment(\.scenePhase) private var scenePhase

    private let container: ModelContainer
    @StateObject private var settings: AppSettings
    @StateObject private var epg: EPGStore
    @StateObject private var sync: SyncCenter
    @StateObject private var downloads: DownloadCenter
    @StateObject private var playback: PlaybackCoordinator

    init() {
        let container: ModelContainer
        do {
            container = try ModelContainer(for: Schema(NovaSchema.models))
        } catch {
            // A broken store must not prevent the app from starting: start again from an empty catalogue.
            let url = URL.applicationSupportDirectory.appending(path: "default.store")
            try? FileManager.default.removeItem(at: url)
            container = try! ModelContainer(for: Schema(NovaSchema.models))
        }
        self.container = container
        let settings = AppSettings()
        let epg = EPGStore()
        let downloads = DownloadCenter(container: container, settings: settings)
        _settings = StateObject(wrappedValue: settings)
        _epg = StateObject(wrappedValue: epg)
        _sync = StateObject(wrappedValue: SyncCenter(container: container, epg: epg))
        _downloads = StateObject(wrappedValue: downloads)
        _playback = StateObject(wrappedValue: PlaybackCoordinator(container: container, settings: settings, downloads: downloads))
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(settings)
                .environmentObject(epg)
                .environmentObject(sync)
                .environmentObject(downloads)
                .environmentObject(playback)
                .environment(\.palette, settings.palette)
                .environment(\.novaStyle, settings.style)
                .toggleStyle(NovaSwitchStyle())
                .tint(settings.palette.accent)
                .preferredColorScheme(settings.palette.isLight ? .light : .dark)
                .onAppear {
                    downloads.backgroundCompletion = {
                        AppDelegate.backgroundCompletion?()
                        AppDelegate.backgroundCompletion = nil
                    }
                    downloads.restore()
                }
        }
        .modelContainer(container)
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { try? container.mainContext.save() }
        }
    }
}
