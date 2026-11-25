import AVKit
import Combine
import SwiftData
import SwiftUI
import VLCKitSPM

enum PlaybackEngine: Equatable {
    case apple, vlc
}

struct PlaybackRequest: Identifiable {
    let id = UUID()
    let profileID: UUID
    let items: [PlayableItem]
    let startIndex: Int
    let fromStart: Bool
}

/// Entry point for every "play" action: the NOVA player (Apple or built-in VLC engine) or an external app.
@MainActor
final class PlaybackCoordinator: ObservableObject {
    @Published var request: PlaybackRequest?
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
        let isLocal = downloads.record(profile.id, item.ref).flatMap { downloads.localURL($0) } != nil
        let choice = forcePlayer ?? settings.preferredPlayer
        // Downloaded files live in NOVA's private folder: only the internal player can read them.
        if !isLocal, let external = choice.external {
            guard let url = StreamURLs.url(for: item.ref, profile: profile) else {
                message = "Adresse de lecture invalide."
                return
            }
            container.mainContext.saveProgress(profileID: profile.id, item: item, position: 0, duration: 0)
            external.open(url)
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

struct TrackChoice: Identifiable, Equatable {
    let id: Int32
    let name: String
}

@MainActor
final class PlayerController: ObservableObject {
    let player = AVPlayer()
    let vlc = VLCMediaPlayer()
    let items: [PlayableItem]
    let profile: Profile
    @Published private(set) var index: Int
    @Published private(set) var engine: PlaybackEngine = .apple
    @Published var error: String?
    @Published var nextCountdown: Int?
    @Published var sleep: SleepChoice = .off
    @Published var finished = false

    // State of the built-in VLC engine (Apple's player draws its own controls).
    @Published private(set) var vlcTime: Double = 0
    @Published private(set) var vlcDuration: Double = 0
    @Published private(set) var vlcPlaying = false
    @Published private(set) var vlcBuffering = true
    @Published private(set) var audioTracks: [TrackChoice] = []
    @Published private(set) var subtitleTracks: [TrackChoice] = []
    @Published private(set) var currentAudio: Int32 = -1
    @Published private(set) var currentSubtitle: Int32 = -1
    @Published private(set) var rate: Float = 1

    private let context: ModelContext
    private let settings: AppSettings
    private let downloads: DownloadCenter
    private var timeObserver: Any?
    private var cancellables = Set<AnyCancellable>()
    private var itemCancellables = Set<AnyCancellable>()
    private var countdownTask: Task<Void, Never>?
    private var sleepTask: Task<Void, Never>?
    private var vlcTimer: Timer?
    private var currentURL: URL?
    private var playURL: URL?
    private var resumeAt: Double = 0
    private var vlcLanguagesApplied = false
    private var vlcEndHandled = false
    private var appleFailedOnce = false
    private var vlcPendingPlay = false

    init(request: PlaybackRequest, profile: Profile, context: ModelContext, settings: AppSettings, downloads: DownloadCenter) {
        items = request.items
        index = request.startIndex
        self.profile = profile
        self.context = context
        self.settings = settings
        self.downloads = downloads
        player.allowsExternalPlayback = true
        player.audiovisualBackgroundPlaybackPolicy = .continuesIfPossible
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 10, preferredTimescale: 1), queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.saveProgress() }
        }
        NotificationCenter.default.publisher(for: AVPlayerItem.didPlayToEndTimeNotification)
            .receive(on: RunLoop.main)
            .sink { [weak self] note in
                guard let self, self.engine == .apple, (note.object as? AVPlayerItem) === self.player.currentItem else { return }
                self.itemEnded()
            }
            .store(in: &cancellables)
        NotificationCenter.default.publisher(for: AVPlayerItem.mediaSelectionDidChangeNotification)
            .receive(on: RunLoop.main)
            .sink { [weak self] note in
                guard let self, (note.object as? AVPlayerItem) === self.player.currentItem else { return }
                self.rememberAppleLanguages()
            }
            .store(in: &cancellables)
        vlcTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated { self?.tickVLC() }
        }
        load(index, resume: !request.fromStart)
    }

    var current: PlayableItem { items[index] }
    var isLive: Bool { current.kind == .live }
    var hasNext: Bool { index + 1 < items.count }
    var streamURL: URL? { currentURL }
    var gesturesEnabled: Bool { settings.gesturesEnabled }
    var pictureInPicture: Bool { settings.pictureInPicture }

    var title: String {
        if let subtitle = current.subtitle { return "\(current.title) · \(subtitle)" }
        return current.title
    }

    /// Containers Apple's player cannot open; the built-in VLC engine reads them.
    static func needsVLC(_ url: URL) -> Bool {
        let ext = url.pathExtension.lowercased()
        return ["mkv", "avi", "wmv", "flv", "webm", "ts", "mpg", "mpeg", "vob", "divx", "ogv", "3gp", "rm", "rmvb", "m2ts", "mts"].contains(ext)
    }

    // MARK: Loading

    func load(_ newIndex: Int, resume: Bool, forceEngine: PlaybackEngine? = nil) {
        saveProgress()
        countdownTask?.cancel()
        nextCountdown = nil
        error = nil
        finished = false
        index = newIndex
        let item = items[newIndex]
        let local = downloads.record(profile.id, item.ref).flatMap { downloads.localURL($0) }
        let remote = StreamURLs.url(for: item.ref, profile: profile)
        currentURL = local == nil ? remote : nil
        guard let source = local ?? remote else {
            error = "Adresse de lecture invalide."
            return
        }
        resumeAt = 0
        if resume, item.kind != .live, let history = context.history(profile.id, item.ref), history.isResumable {
            resumeAt = history.position
        }
        let wanted: PlaybackEngine = forceEngine
            ?? (settings.internalEngine == .vlc || Self.needsVLC(source) ? .vlc : .apple)
        if forceEngine == nil { appleFailedOnce = false }
        context.saveProgress(profileID: profile.id, item: item, position: resumeAt, duration: 0)
        switch wanted {
        case .apple:
            playURL = local ?? StreamURLs.appleCompatible(source, kind: item.kind)
            startApple(item)
        case .vlc:
            playURL = source
            startVLC()
        }
    }

    private func startApple(_ item: PlayableItem) {
        stopVLC()
        engine = .apple
        guard let url = playURL else { return }
        itemCancellables.removeAll()
        let asset = AVURLAsset(url: url, options: ["AVURLAssetHTTPUserAgentKey": "NOVA-IPTV/1.0 (iOS)"])
        let playerItem = AVPlayerItem(asset: asset)
        playerItem.externalMetadata = metadata(for: item)
        playerItem.preferredForwardBufferDuration = item.kind == .live ? 4 : 0
        playerItem.publisher(for: \.status)
            .receive(on: RunLoop.main)
            .sink { [weak self, weak playerItem] status in
                guard let self, let playerItem, playerItem === self.player.currentItem else { return }
                switch status {
                case .readyToPlay: Task { await self.applyAppleLanguages(playerItem) }
                case .failed: self.appleFailed(playerItem.error)
                default: break
                }
            }
            .store(in: &itemCancellables)
        player.replaceCurrentItem(with: playerItem)
        if resumeAt > 0 { player.seek(to: CMTime(seconds: resumeAt, preferredTimescale: 600)) }
        player.play()
    }

    /// Apple could not read the stream: retry once with the built-in VLC engine.
    private func appleFailed(_ failure: Error?) {
        let ns = failure as NSError?
        if !appleFailedOnce, ns?.domain != NSURLErrorDomain {
            appleFailedOnce = true
            let position = player.currentTime().seconds
            if position.isFinite, position > 5 { resumeAt = position }
            playURL = downloads.record(profile.id, current.ref).flatMap { downloads.localURL($0) } ?? currentURL
            startVLC()
            return
        }
        error = Self.describe(failure)
    }

    func switchEngine() {
        let position = currentSeconds
        if position.isFinite, position > 5 { resumeAt = position }
        if engine == .apple {
            appleFailedOnce = true
            playURL = downloads.record(profile.id, current.ref).flatMap { downloads.localURL($0) } ?? currentURL
            startVLC()
        } else {
            playURL = downloads.record(profile.id, current.ref).flatMap { downloads.localURL($0) }
                ?? currentURL.map { StreamURLs.appleCompatible($0, kind: current.kind) }
            startApple(current)
        }
    }

    private func startVLC() {
        player.pause()
        player.replaceCurrentItem(with: nil)
        // A new video surface is created when switching from Apple's player.
        let surfaceReady = engine == .vlc && vlc.drawable != nil
        engine = .vlc
        error = nil
        guard let url = playURL else { return }
        vlcTime = 0
        vlcDuration = 0
        vlcBuffering = true
        vlcLanguagesApplied = false
        vlcEndHandled = false
        audioTracks = []
        subtitleTracks = []
        let media = VLCMedia(url: url)
        media.addOption(":http-user-agent=NOVA-IPTV/1.0 (iOS)")
        media.addOption(":network-caching=\(isLive ? 2000 : 1500)")
        if resumeAt > 0 { media.addOption(":start-time=\(Int(resumeAt))") }
        vlc.media = media
        vlc.rate = rate
        // VLC needs its drawing surface before starting; SwiftUI creates it on the next layout pass.
        if surfaceReady { vlc.play() } else { vlcPendingPlay = true }
    }

    func attachVLCView(_ view: UIView) {
        vlc.drawable = view
        if vlcPendingPlay {
            vlcPendingPlay = false
            vlc.play()
        }
    }

    private func stopVLC() {
        if vlc.media != nil { vlc.stop() }
        vlcPlaying = false
    }

    private func tickVLC() {
        guard engine == .vlc else { return }
        vlcTime = Double(vlc.time.intValue) / 1000
        let length = Double(vlc.media?.length.intValue ?? 0) / 1000
        if length > 0 { vlcDuration = length }
        vlcPlaying = vlc.isPlaying
        switch vlc.state {
        case .buffering, .opening: vlcBuffering = !vlc.isPlaying
        case .playing:
            vlcBuffering = false
            refreshVLCTracks()
            if !vlcLanguagesApplied { applyVLCLanguages() }
        case .error:
            vlcBuffering = false
            error = "Lecture impossible avec le moteur VLC : flux indisponible ou format non pris en charge."
        case .ended:
            vlcBuffering = false
            if !vlcEndHandled {
                vlcEndHandled = true
                if isLive { error = "Le flux s'est interrompu." } else { itemEnded() }
            }
        default:
            vlcBuffering = false
        }
        if Int(vlcTime) % 10 == 0, vlcTime > 0 { saveProgress() }
    }

    // MARK: Tracks and languages

    private func refreshVLCTracks() {
        func tracks(_ names: [Any]?, _ indexes: [Any]?) -> [TrackChoice] {
            let n = (names as? [String]) ?? []
            let i = ((indexes as? [NSNumber]) ?? []).map(\.int32Value)
            return zip(i, n).map { TrackChoice(id: $0.0, name: $0.1) }
        }
        let audio = tracks(vlc.audioTrackNames, vlc.audioTrackIndexes)
        let subs = tracks(vlc.videoSubTitlesNames, vlc.videoSubTitlesIndexes)
        if audio != audioTracks { audioTracks = audio }
        if subs != subtitleTracks { subtitleTracks = subs }
        currentAudio = vlc.currentAudioTrackIndex
        currentSubtitle = vlc.currentVideoSubTitleIndex
    }

    private static func matches(_ name: String, language code: String) -> Bool {
        let lower = name.lowercased()
        if lower.hasSuffix(" - \(code)") || lower.contains("[\(code)]") { return true }
        let english = Locale(identifier: "en").localizedString(forLanguageCode: code)?.lowercased()
        let french = Locale(identifier: "fr").localizedString(forLanguageCode: code)?.lowercased()
        for candidate in [english, french] {
            if let candidate, lower.contains(candidate) { return true }
        }
        return false
    }

    private func applyVLCLanguages() {
        guard !audioTracks.isEmpty || !subtitleTracks.isEmpty else { return }
        vlcLanguagesApplied = true
        if let code = settings.audioLanguage, let track = audioTracks.first(where: { $0.id >= 0 && Self.matches($0.name, language: code) }) {
            vlc.currentAudioTrackIndex = track.id
        }
        if let code = settings.subtitleLanguage {
            if let track = subtitleTracks.first(where: { $0.id >= 0 && Self.matches($0.name, language: code) }) {
                vlc.currentVideoSubTitleIndex = track.id
            }
        } else {
            vlc.currentVideoSubTitleIndex = -1
        }
        refreshVLCTracks()
    }

    /// A choice made in the player becomes the new preference, like on Android.
    func selectAudio(_ track: TrackChoice) {
        vlc.currentAudioTrackIndex = track.id
        if let code = preferredLanguageCodes.first(where: { Self.matches(track.name, language: $0) }) { settings.audioLanguage = code }
        refreshVLCTracks()
    }

    func selectSubtitle(_ track: TrackChoice?) {
        vlc.currentVideoSubTitleIndex = track?.id ?? -1
        if let track {
            if let code = preferredLanguageCodes.first(where: { Self.matches(track.name, language: $0) }) { settings.subtitleLanguage = code }
        } else {
            settings.subtitleLanguage = nil
        }
        refreshVLCTracks()
    }

    private func applyAppleLanguages(_ item: AVPlayerItem) async {
        let asset = item.asset
        if let code = settings.audioLanguage, let group = try? await asset.loadMediaSelectionGroup(for: .audible),
           let option = group.options.first(where: { Self.code(of: $0) == code }) {
            item.select(option, in: group)
        }
        if let group = try? await asset.loadMediaSelectionGroup(for: .legible) {
            if let code = settings.subtitleLanguage {
                if let option = group.options.first(where: { Self.code(of: $0) == code }) { item.select(option, in: group) }
            } else if group.allowsEmptySelection {
                item.select(nil, in: group)
            }
        }
    }

    private static func code(of option: AVMediaSelectionOption) -> String? {
        if let tag = option.extendedLanguageTag { return String(tag.prefix(2)).lowercased() }
        return option.locale?.language.languageCode?.identifier
    }

    private func rememberAppleLanguages() {
        guard let item = player.currentItem else { return }
        Task {
            let asset = item.asset
            if let group = try? await asset.loadMediaSelectionGroup(for: .audible),
               let option = item.currentMediaSelection.selectedMediaOption(in: group), let code = Self.code(of: option) {
                settings.audioLanguage = code
            }
            if let group = try? await asset.loadMediaSelectionGroup(for: .legible) {
                if let option = item.currentMediaSelection.selectedMediaOption(in: group) {
                    if let code = Self.code(of: option) { settings.subtitleLanguage = code }
                } else {
                    settings.subtitleLanguage = nil
                }
            }
        }
    }

    // MARK: Transport

    var currentSeconds: Double {
        engine == .vlc ? vlcTime : player.currentTime().seconds
    }

    var durationSeconds: Double {
        if engine == .vlc { return vlcDuration }
        let d = player.currentItem?.duration.seconds ?? 0
        return d.isFinite ? d : 0
    }

    func togglePlay() {
        if engine == .vlc {
            if vlc.isPlaying { vlc.pause() } else { vlc.play() }
            vlcPlaying.toggle()
        } else if player.timeControlStatus == .playing {
            player.pause()
        } else {
            player.play()
        }
    }

    func skip(_ seconds: Int32) {
        if engine == .vlc {
            if seconds >= 0 { vlc.jumpForward(seconds) } else { vlc.jumpBackward(-seconds) }
        } else {
            let target = player.currentTime().seconds + Double(seconds)
            player.seek(to: CMTime(seconds: max(0, target), preferredTimescale: 600))
        }
    }

    func seek(toFraction fraction: Double) {
        if engine == .vlc {
            vlc.position = Float(max(0, min(1, fraction)))
            vlcTime = fraction * vlcDuration
        } else if durationSeconds > 0 {
            player.seek(to: CMTime(seconds: fraction * durationSeconds, preferredTimescale: 600))
        }
    }

    func setRate(_ value: Float) {
        rate = value
        if engine == .vlc { vlc.rate = value } else { player.rate = value }
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
        return "Lecture impossible : \(error.localizedDescription)"
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
        guard !isLive else { return }
        let duration = durationSeconds
        if duration > 0, currentSeconds > duration * 0.9 {
            context.saveProgress(profileID: profile.id, item: current, position: duration, duration: duration)
        }
    }

    func saveProgress() {
        guard !isLive else { return }
        let position = currentSeconds
        guard position.isFinite, position > 0 else { return }
        context.saveProgress(profileID: profile.id, item: current, position: position, duration: durationSeconds)
    }

    private func itemEnded() {
        guard !isLive else { return }
        let duration = durationSeconds
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
        nextCountdown = settings.countdownSeconds
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
                self.vlc.pause()
                self.sleep = .off
                self.finished = true
            }
        }
    }

    func stop() {
        saveProgress()
        countdownTask?.cancel()
        sleepTask?.cancel()
        vlcTimer?.invalidate()
        vlcTimer = nil
        stopVLC()
        player.pause()
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        timeObserver = nil
        cancellables.removeAll()
        itemCancellables.removeAll()
        player.replaceCurrentItem(with: nil)
    }
}
