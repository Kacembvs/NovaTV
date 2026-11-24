import AVKit
import Combine
import SwiftData
import SwiftUI

struct PlaybackRequest: Identifiable {
    let id = UUID()
    let profileID: UUID
    let items: [PlayableItem]
    let startIndex: Int
    let fromStart: Bool
}

/// Entry point for every "play" action: picks the NOVA player, an external player, or a share sheet.
@MainActor
final class PlaybackCoordinator: ObservableObject {
    @Published var request: PlaybackRequest?
    @Published var shareFile: URL?
    @Published var message: String?

    private let container: ModelContainer
    private let settings: AppSettings
    private let downloads: DownloadCenter

    init(container: ModelContainer, settings: AppSettings, downloads: DownloadCenter) {
        self.container = container
        self.settings = settings
        self.downloads = downloads
    }

    func play(_ items: [PlayableItem], start: Int = 0, profile: Profile, fromStart: Bool = false, forcePlayer: PlayerChoice? = nil) {
        guard items.indices.contains(start) else { return }
        let item = items[start]
        let record = downloads.record(profile.id, item.ref)
        if let local = record.flatMap({ downloads.localURL($0) }) {
            if StreamURLs.needsExternalPlayer(local) {
                // External players cannot read NOVA's private folder: hand the file over with "Ouvrir dans…".
                shareFile = local
                return
            }
            request = PlaybackRequest(profileID: profile.id, items: items, startIndex: start, fromStart: fromStart)
            return
        }
        guard let url = StreamURLs.url(for: item.ref, profile: profile) else {
            message = "Adresse de lecture invalide."
            return
        }
        let choice = forcePlayer ?? settings.preferredPlayer
        if let external = choice.external {
            container.mainContext.saveProgress(profileID: profile.id, item: item, position: 0, duration: 0)
            external.open(url)
            return
        }
        if StreamURLs.needsExternalPlayer(url) {
            container.mainContext.saveProgress(profileID: profile.id, item: item, position: 0, duration: 0)
            settings.fallbackPlayer.open(url)
            return
        }
        request = PlaybackRequest(profileID: profile.id, items: items, startIndex: start, fromStart: fromStart)
    }
}

enum OrientationLock {
    static var mask: UIInterfaceOrientationMask = .all

    @MainActor
    static func set(_ newMask: UIInterfaceOrientationMask, preferred: UIInterfaceOrientationMask) {
        mask = newMask
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
        scene.windows.first?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
        scene.requestGeometryUpdate(.iOS(interfaceOrientations: preferred)) { _ in }
    }
}

enum SleepChoice: Equatable {
    case off, minutes(Int), endOfItem
}

@MainActor
final class PlayerController: ObservableObject {
    let player = AVPlayer()
    let items: [PlayableItem]
    let profile: Profile
    @Published private(set) var index: Int
    @Published var error: String?
    @Published var nextCountdown: Int?
    @Published var sleep: SleepChoice = .off
    @Published private(set) var actions: [UIAction] = []
    @Published var finished = false

    private let context: ModelContext
    private let settings: AppSettings
    private let downloads: DownloadCenter
    private let fromStart: Bool
    private var timeObserver: Any?
    private var cancellables = Set<AnyCancellable>()
    private var countdownTask: Task<Void, Never>?
    private var sleepTask: Task<Void, Never>?
    private var currentURL: URL?

    init(request: PlaybackRequest, profile: Profile, context: ModelContext, settings: AppSettings, downloads: DownloadCenter) {
        items = request.items
        index = request.startIndex
        self.profile = profile
        self.context = context
        self.settings = settings
        self.downloads = downloads
        fromStart = request.fromStart
        player.allowsExternalPlayback = true
        player.audiovisualBackgroundPlaybackPolicy = .continuesIfPossible
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 10, preferredTimescale: 1), queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.saveProgress() }
        }
        NotificationCenter.default.publisher(for: AVPlayerItem.didPlayToEndTimeNotification)
            .receive(on: RunLoop.main)
            .sink { [weak self] note in
                guard let self, (note.object as? AVPlayerItem) === self.player.currentItem else { return }
                self.itemEnded()
            }
            .store(in: &cancellables)
        load(index, resume: !request.fromStart)
    }

    var current: PlayableItem { items[index] }
    var isLive: Bool { current.kind == .live }
    var hasNext: Bool { index + 1 < items.count }
    var streamURL: URL? { currentURL }

    var title: String {
        if let subtitle = current.subtitle { return "\(current.title) · \(subtitle)" }
        return current.title
    }

    func load(_ newIndex: Int, resume: Bool) {
        saveProgress()
        countdownTask?.cancel()
        nextCountdown = nil
        error = nil
        finished = false
        index = newIndex
        let item = items[newIndex]
        let local = downloads.record(profile.id, item.ref).flatMap { downloads.localURL($0) }
        guard let url = local ?? StreamURLs.url(for: item.ref, profile: profile).map({ StreamURLs.appleCompatible($0, kind: item.kind) }) else {
            error = "Adresse de lecture invalide."
            return
        }
        currentURL = local == nil ? StreamURLs.url(for: item.ref, profile: profile) : nil
        if local == nil, StreamURLs.needsExternalPlayer(url) {
            error = "Ce format (\(url.pathExtension.uppercased())) n'est pas lisible par le lecteur Apple."
            player.replaceCurrentItem(with: nil)
            updateActions()
            return
        }
        let asset = AVURLAsset(url: url, options: ["AVURLAssetHTTPUserAgentKey": "NOVA-IPTV/1.0 (iOS)"])
        let playerItem = AVPlayerItem(asset: asset)
        playerItem.externalMetadata = metadata(for: item)
        playerItem.preferredForwardBufferDuration = item.kind == .live ? 4 : 0
        playerItem.publisher(for: \.status)
            .receive(on: RunLoop.main)
            .sink { [weak self, weak playerItem] status in
                guard let self, status == .failed, let playerItem, playerItem === self.player.currentItem else { return }
                self.error = Self.describe(playerItem.error)
            }
            .store(in: &cancellables)
        player.replaceCurrentItem(with: playerItem)
        if resume, item.kind != .live, let history = context.history(profile.id, item.ref), history.isResumable {
            player.seek(to: CMTime(seconds: history.position, preferredTimescale: 600))
        }
        player.play()
        if item.kind != .live { context.saveProgress(profileID: profile.id, item: item, position: resume ? (context.history(profile.id, item.ref)?.position ?? 0) : 0, duration: 0) }
        else { context.saveProgress(profileID: profile.id, item: item, position: 0, duration: 0) }
        updateActions()
    }

    private func metadata(for item: PlayableItem) -> [AVMetadataItem] {
        func entry(_ id: AVMetadataIdentifier, _ value: String) -> AVMetadataItem {
            let m = AVMutableMetadataItem()
            m.identifier = id
            m.value = value as NSString
            m.extendedLanguageTag = "und"
            return m
        }
        var result = [entry(.commonIdentifierTitle, item.subtitle ?? item.title)]
        if item.subtitle != nil { result.append(entry(.iTunesMetadataTrackSubTitle, item.title)) }
        return result
    }

    private static func describe(_ error: Error?) -> String {
        guard let error else { return "Lecture impossible." }
        let ns = error as NSError
        if ns.domain == NSURLErrorDomain { return error.userMessage }
        if ns.code == -11828 || ns.code == -11829 || ns.code == -12847 {
            return "Format non pris en charge par le lecteur Apple."
        }
        return "Lecture impossible : \(error.localizedDescription)"
    }

    func updateActions() {
        var list: [UIAction] = []
        if isLive {
            if index > 0 { list.append(UIAction(title: "Chaîne précédente", image: UIImage(systemName: "chevron.left")) { [weak self] _ in self?.previous() }) }
            if hasNext { list.append(UIAction(title: "Chaîne suivante", image: UIImage(systemName: "chevron.right")) { [weak self] _ in self?.next() }) }
        } else if hasNext {
            let label = items[index + 1].kind == .episode ? "Épisode suivant" : "Suivant"
            list.append(UIAction(title: label, image: UIImage(systemName: "forward.end.fill")) { [weak self] _ in self?.next() })
        }
        actions = list
    }

    func next() {
        guard hasNext else { return }
        markFinishedIfNeeded()
        load(index + 1, resume: !isLive)
    }

    func previous() {
        guard index > 0 else { return }
        load(index - 1, resume: !isLive)
    }

    private func markFinishedIfNeeded() {
        guard !isLive, let item = player.currentItem else { return }
        let duration = item.duration.seconds
        let position = player.currentTime().seconds
        if duration.isFinite, duration > 0, position > duration * 0.9 {
            context.saveProgress(profileID: profile.id, item: current, position: duration, duration: duration)
        }
    }

    func saveProgress() {
        guard !isLive, let item = player.currentItem else { return }
        let position = player.currentTime().seconds
        let duration = item.duration.seconds
        guard position.isFinite, position > 0 else { return }
        context.saveProgress(profileID: profile.id, item: current, position: position, duration: duration.isFinite ? duration : 0)
    }

    private func itemEnded() {
        guard !isLive else { return }
        let duration = player.currentItem?.duration.seconds ?? 0
        context.saveProgress(profileID: profile.id, item: current, position: duration, duration: duration)
        let nextItem = hasNext ? items[index + 1] : nil
        downloads.episodeFinished(profile: profile, item: current, next: nextItem)
        if sleep == .endOfItem {
            sleep = .off
            finished = true
            return
        }
        guard settings.autoplayNext, current.kind == .episode, hasNext else {
            finished = true
            return
        }
        nextCountdown = 8
        countdownTask = Task { [weak self] in
            while let self, let left = self.nextCountdown, left > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard !Task.isCancelled else { return }
                self.nextCountdown = left - 1
            }
            guard !Task.isCancelled, let self, self.nextCountdown == 0 else { return }
            self.next()
        }
    }

    func cancelCountdown() {
        countdownTask?.cancel()
        nextCountdown = nil
        finished = true
    }

    func setSleep(_ choice: SleepChoice) {
        sleepTask?.cancel()
        sleep = choice
        if case .minutes(let minutes) = choice {
            sleepTask = Task { [weak self] in
                try? await Task.sleep(nanoseconds: UInt64(minutes) * 60 * 1_000_000_000)
                guard !Task.isCancelled, let self else { return }
                self.player.pause()
                self.sleep = .off
                self.finished = true
            }
        }
    }

    func stop() {
        saveProgress()
        countdownTask?.cancel()
        sleepTask?.cancel()
        player.pause()
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        timeObserver = nil
        cancellables.removeAll()
        player.replaceCurrentItem(with: nil)
    }
}

struct SystemPlayerView: UIViewControllerRepresentable {
    @ObservedObject var controller: PlayerController

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let vc = AVPlayerViewController()
        vc.player = controller.player
        vc.allowsPictureInPicturePlayback = true
        vc.canStartPictureInPictureAutomaticallyFromInline = true
        vc.updatesNowPlayingInfoCenter = true
        vc.showsPlaybackControls = true
        vc.videoGravity = .resizeAspect
        return vc
    }

    func updateUIViewController(_ vc: AVPlayerViewController, context: Context) {
        if vc.player !== controller.player { vc.player = controller.player }
    }
}

struct PlayerScreen: View {
    let request: PlaybackRequest
    let profile: Profile
    @StateObject private var controller: PlayerController
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var settings: AppSettings

    init(request: PlaybackRequest, profile: Profile, context: ModelContext, settings: AppSettings, downloads: DownloadCenter) {
        self.request = request
        self.profile = profile
        _controller = StateObject(wrappedValue: PlayerController(request: request, profile: profile, context: context, settings: settings, downloads: downloads))
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            SystemPlayerView(controller: controller).ignoresSafeArea()
            VStack {
                HStack(spacing: 12) {
                    PlayerChromeButton(systemImage: "xmark", label: "Fermer") { close() }
                    Text(controller.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .shadow(radius: 4)
                    Spacer()
                    if controller.index > 0 && controller.isLive {
                        PlayerChromeButton(systemImage: "chevron.left", label: "Chaîne précédente") { controller.previous() }
                    }
                    if controller.hasNext {
                        PlayerChromeButton(systemImage: controller.isLive ? "chevron.right" : "forward.end.fill",
                                           label: controller.isLive ? "Chaîne suivante" : "Suivant") { controller.next() }
                    }
                    Menu {
                        Section("Minuteur de sommeil") {
                            Button("Désactivé") { controller.setSleep(.off) }
                            ForEach([15, 30, 45, 60, 90], id: \.self) { minutes in
                                Button("\(minutes) minutes") { controller.setSleep(.minutes(minutes)) }
                            }
                            if !controller.isLive {
                                Button(controller.current.kind == .movie ? "Fin du film" : "Fin de l'épisode") { controller.setSleep(.endOfItem) }
                            }
                        }
                        if let url = controller.streamURL {
                            Section("Ouvrir avec") {
                                ForEach(ExternalPlayer.allCases) { player in
                                    Button(player.name) {
                                        controller.stop()
                                        player.open(url)
                                        close()
                                    }
                                }
                            }
                        }
                    } label: {
                        PlayerChromeIcon(systemImage: controller.sleep == .off ? "ellipsis" : "moon.zzz.fill")
                    }
                    .accessibilityLabel("Options de lecture")
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                Spacer()
            }
            if let message = controller.error {
                PlayerErrorView(message: message, url: controller.streamURL, onRetry: { controller.load(controller.index, resume: true) }, onClose: close)
            }
            if let left = controller.nextCountdown, controller.hasNext {
                NextUpView(title: controller.items[controller.index + 1].subtitle ?? controller.items[controller.index + 1].title,
                           seconds: left, onPlay: { controller.next() }, onCancel: { controller.cancelCountdown() })
            }
        }
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
        .onAppear { OrientationLock.set(.allButUpsideDown, preferred: .landscapeRight) }
        .onDisappear {
            controller.stop()
            OrientationLock.set(.all, preferred: .portrait)
        }
    }

    private func close() {
        controller.stop()
        dismiss()
    }
}

private struct PlayerChromeIcon: View {
    let systemImage: String
    var body: some View {
        Image(systemName: systemImage)
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 44, height: 44)
            .background(.black.opacity(0.45), in: Circle())
    }
}

private struct PlayerChromeButton: View {
    let systemImage: String
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) { PlayerChromeIcon(systemImage: systemImage) }
            .accessibilityLabel(label)
    }
}

private struct PlayerErrorView: View {
    let message: String
    let url: URL?
    let onRetry: () -> Void
    let onClose: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill").font(.largeTitle).foregroundStyle(.yellow)
            Text(message).multilineTextAlignment(.center).foregroundStyle(.white)
            if let url {
                Text("Essayez avec un autre lecteur :").font(.footnote).foregroundStyle(.white.opacity(0.7))
                HStack {
                    ForEach(ExternalPlayer.allCases) { player in
                        Button(player.name) {
                            player.open(url)
                            onClose()
                        }
                        .buttonStyle(.borderedProminent)
                    }
                }
            }
            HStack {
                Button("Réessayer", action: onRetry).buttonStyle(.bordered)
                Button("Fermer", action: onClose).buttonStyle(.bordered)
            }
            .tint(.white)
        }
        .padding(24)
        .background(.black.opacity(0.85), in: RoundedRectangle(cornerRadius: 20))
        .padding()
    }
}

private struct NextUpView: View {
    let title: String
    let seconds: Int
    let onPlay: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack {
            Spacer()
            HStack {
                Spacer()
                VStack(alignment: .leading, spacing: 10) {
                    Text("Épisode suivant dans \(seconds) s").font(.caption).foregroundStyle(.white.opacity(0.8))
                    Text(title).font(.headline).foregroundStyle(.white).lineLimit(2)
                    HStack {
                        Button(action: onPlay) { Label("Lire", systemImage: "play.fill") }
                            .buttonStyle(.borderedProminent)
                        Button("Annuler", action: onCancel).buttonStyle(.bordered).tint(.white)
                    }
                }
                .padding(16)
                .frame(maxWidth: 320, alignment: .leading)
                .background(.black.opacity(0.8), in: RoundedRectangle(cornerRadius: 16))
            }
            .padding(24)
            .padding(.bottom, 60)
        }
    }
}
