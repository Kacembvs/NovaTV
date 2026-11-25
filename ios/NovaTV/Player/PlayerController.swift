import AVKit
import Combine
import MediaPlayer
import SwiftData
import SwiftUI
import VLCKitSPM

enum PlaybackEngine: Equatable {
    case apple, vlc
    var label: String { self == .apple ? "Lecteur Apple" : "Moteur VLC intégré" }
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

/// Same choices as Android's "Format d'image".
enum VideoFit: String, CaseIterable, Identifiable {
    case fit, fill, stretch
    var id: String { rawValue }
    var label: String {
        switch self {
        case .fit: return "Ajuster"
        case .fill: return "Remplir l'écran"
        case .stretch: return "Étirer"
        }
    }
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
    /// "Reprise à …" banner shown for a few seconds after resuming.
    @Published var resumedFrom: Double?

    // Shared state of both engines, drawn by NOVA's own controls.
    @Published private(set) var time: Double = 0
    @Published private(set) var duration: Double = 0
    @Published private(set) var playing = false
    @Published private(set) var buffering = true
    @Published private(set) var audioTracks: [TrackChoice] = []
    @Published private(set) var subtitleTracks: [TrackChoice] = []
    @Published private(set) var currentAudio: Int32 = -1
    @Published private(set) var currentSubtitle: Int32 = -1
    @Published private(set) var rate: Float = 1
    @Published private(set) var fit = VideoFit.fit
    @Published private(set) var videoSize: CGSize = .zero
    @Published private(set) var bitrate: Double = 0

    /// Set by the Apple video view; used for "Format d'image" and Picture-in-Picture.
    weak var playerLayer: AVPlayerLayer?
    /// Size of the video surface, needed to fill or stretch with the VLC engine.
    var surfaceSize: CGSize = .zero

    private let context: ModelContext
    private let settings: AppSettings
    private let downloads: DownloadCenter
    private var cancellables = Set<AnyCancellable>()
    private var itemCancellables = Set<AnyCancellable>()
    private var countdownTask: Task<Void, Never>?
    private var sleepTask: Task<Void, Never>?
    private var resumeTask: Task<Void, Never>?
    private var ticker: Timer?
    private var currentURL: URL?
    private var playURL: URL?
    private var resumeAt: Double = 0
    private var languagesApplied = false
    private var vlcEndHandled = false
    private var appleFailedOnce = false
    private var vlcPendingPlay = false
    private var lastSave = Date.distantPast
    private var audibleGroup: AVMediaSelectionGroup?
    private var legibleGroup: AVMediaSelectionGroup?

    init(request: PlaybackRequest, profile: Profile, context: ModelContext, settings: AppSettings, downloads: DownloadCenter) {
        items = request.items
        index = request.startIndex
        self.profile = profile
        self.context = context
        self.settings = settings
        self.downloads = downloads
        player.allowsExternalPlayback = true
        player.audiovisualBackgroundPlaybackPolicy = .continuesIfPossible
        NotificationCenter.default.publisher(for: AVPlayerItem.didPlayToEndTimeNotification)
            .receive(on: RunLoop.main)
            .sink { [weak self] note in
                guard let self, self.engine == .apple, (note.object as? AVPlayerItem) === self.player.currentItem else { return }
                self.itemEnded()
            }
            .store(in: &cancellables)
        ticker = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated { self?.tick() }
        }
        setupRemoteCommands()
        load(index, resume: !request.fromStart)
    }

    var current: PlayableItem { items[index] }
    var isLive: Bool { current.kind == .live }
    var hasNext: Bool { index + 1 < items.count }
    var streamURL: URL? { currentURL }
    var gesturesEnabled: Bool { settings.gesturesEnabled }
    var pictureInPictureAllowed: Bool { settings.pictureInPicture && engine == .apple }
    /// True while the full-screen NOVA player shows this controller (false in the mini-player).
    var isPresented = false

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
        saveProgress(force: true)
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
        showResumeBanner(resumeAt)
        let wanted: PlaybackEngine = forceEngine
            ?? (settings.internalEngine == .vlc || Self.needsVLC(source) ? .vlc : .apple)
        if forceEngine == nil { appleFailedOnce = false }
        context.saveProgress(profileID: profile.id, item: item, position: resumeAt, duration: 0)
        resetTrackState()
        switch wanted {
        case .apple:
            playURL = local ?? StreamURLs.appleCompatible(source, kind: item.kind)
            startApple(item)
        case .vlc:
            playURL = source
            startVLC()
        }
        updateNowPlaying()
    }

    private func resetTrackState() {
        time = 0
        duration = 0
        buffering = true
        languagesApplied = false
        audioTracks = []
        subtitleTracks = []
        currentAudio = -1
        currentSubtitle = -1
        audibleGroup = nil
        legibleGroup = nil
        videoSize = .zero
        bitrate = 0
    }

    private func showResumeBanner(_ position: Double) {
        resumeTask?.cancel()
        resumedFrom = position > 0 ? position : nil
        guard position > 0 else { return }
        resumeTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 10_000_000_000)
            guard !Task.isCancelled else { return }
            self?.resumedFrom = nil
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
                case .readyToPlay: Task { await self.loadAppleTracks(playerItem) }
                case .failed: self.appleFailed(playerItem.error)
                default: break
                }
            }
            .store(in: &itemCancellables)
        player.replaceCurrentItem(with: playerItem)
        if resumeAt > 0 { player.seek(to: CMTime(seconds: resumeAt, preferredTimescale: 600)) }
        player.rate = rate
        player.play()
        applyFit()
    }

    /// Apple could not read the stream: retry once with the built-in VLC engine.
    private func appleFailed(_ failure: Error?) {
        let ns = failure as NSError?
        if !appleFailedOnce, ns?.domain != NSURLErrorDomain {
            appleFailedOnce = true
            let position = player.currentTime().seconds
            if position.isFinite, position > 5 { resumeAt = position }
            playURL = downloads.record(profile.id, current.ref).flatMap { downloads.localURL($0) } ?? currentURL
            resetTrackState()
            startVLC()
            return
        }
        error = Self.describe(failure)
    }

    func switchEngine() {
        let position = time
        if position.isFinite, position > 5 { resumeAt = position }
        resetTrackState()
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
        vlcEndHandled = false
        let media = VLCMedia(url: url)
        media.addOption(":http-user-agent=NOVA-IPTV/1.0 (iOS)")
        media.addOption(":network-caching=\(isLive ? 2000 : 1500)")
        if resumeAt > 0 { media.addOption(":start-time=\(Int(resumeAt))") }
        vlc.media = media
        vlc.rate = rate
        if surfaceReady { vlc.play() } else { vlcPendingPlay = true }
    }

    func attachVLCView(_ view: UIView) {
        vlc.drawable = view
        if vlcPendingPlay {
            vlcPendingPlay = false
            vlc.play()
            applyFit()
        }
    }

    private func stopVLC() {
        if vlc.media != nil { vlc.stop() }
    }

    // MARK: Periodic state

    private func tick() {
        switch engine {
        case .apple: tickApple()
        case .vlc: tickVLC()
        }
        if Date().timeIntervalSince(lastSave) > 10 { saveProgress() }
    }

    private func tickApple() {
        let now = player.currentTime().seconds
        if now.isFinite { time = now }
        let d = player.currentItem?.duration.seconds ?? 0
        duration = d.isFinite && d > 0 ? d : 0
        playing = player.timeControlStatus == .playing
        buffering = player.timeControlStatus == .waitingToPlayAtSpecifiedRate
        if let item = player.currentItem {
            if item.presentationSize != .zero { videoSize = item.presentationSize }
            if let event = item.accessLog()?.events.last, event.indicatedBitrate > 0 { bitrate = event.indicatedBitrate }
        }
    }

    private func tickVLC() {
        time = Double(vlc.time.intValue) / 1000
        let length = Double(vlc.media?.length.intValue ?? 0) / 1000
        if length > 0 { duration = length }
        playing = vlc.isPlaying
        if vlc.videoSize != .zero { videoSize = vlc.videoSize }
        switch vlc.state {
        case .buffering, .opening: buffering = !vlc.isPlaying
        case .playing:
            buffering = false
            refreshVLCTracks()
            if !languagesApplied { applyVLCLanguages() }
        case .error:
            buffering = false
            error = "Lecture impossible avec le moteur VLC : flux indisponible ou format non pris en charge."
        case .ended:
            buffering = false
            if !vlcEndHandled {
                vlcEndHandled = true
                if isLive { error = "Le flux s'est interrompu." } else { itemEnded() }
            }
        default:
            buffering = false
        }
    }

    // MARK: Tracks and languages

    private func refreshVLCTracks() {
        func tracks(_ names: [Any]?, _ indexes: [Any]?) -> [TrackChoice] {
            let n = (names as? [String]) ?? []
            let i = ((indexes as? [NSNumber]) ?? []).map(\.int32Value)
            return zip(i, n).filter { $0.0 >= 0 }.map { TrackChoice(id: $0.0, name: $0.1) }
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
        languagesApplied = true
        if let code = settings.audioLanguage, let track = audioTracks.first(where: { Self.matches($0.name, language: code) }) {
            vlc.currentAudioTrackIndex = track.id
        }
        if let code = settings.subtitleLanguage {
            if let track = subtitleTracks.first(where: { Self.matches($0.name, language: code) }) {
                vlc.currentVideoSubTitleIndex = track.id
            }
        } else {
            vlc.currentVideoSubTitleIndex = -1
        }
        refreshVLCTracks()
    }

    private func loadAppleTracks(_ item: AVPlayerItem) async {
        let asset = item.asset
        audibleGroup = try? await asset.loadMediaSelectionGroup(for: .audible)
        legibleGroup = try? await asset.loadMediaSelectionGroup(for: .legible)
        guard item === player.currentItem else { return }
        if !languagesApplied {
            languagesApplied = true
            if let code = settings.audioLanguage, let group = audibleGroup,
               let option = group.options.first(where: { Self.code(of: $0) == code }) {
                item.select(option, in: group)
            }
            if let group = legibleGroup {
                if let code = settings.subtitleLanguage {
                    if let option = group.options.first(where: { Self.code(of: $0) == code }) { item.select(option, in: group) }
                } else if group.allowsEmptySelection {
                    item.select(nil, in: group)
                }
            }
        }
        refreshAppleTracks()
    }

    private func refreshAppleTracks() {
        guard let item = player.currentItem else { return }
        func list(_ group: AVMediaSelectionGroup?) -> [TrackChoice] {
            (group?.options ?? []).enumerated().map { TrackChoice(id: Int32($0.offset), name: $0.element.displayName) }
        }
        func selected(_ group: AVMediaSelectionGroup?) -> Int32 {
            guard let group, let option = item.currentMediaSelection.selectedMediaOption(in: group),
                  let index = group.options.firstIndex(of: option) else { return -1 }
            return Int32(index)
        }
        audioTracks = list(audibleGroup)
        subtitleTracks = list(legibleGroup)
        currentAudio = selected(audibleGroup)
        currentSubtitle = selected(legibleGroup)
    }

    private static func code(of option: AVMediaSelectionOption) -> String? {
        if let tag = option.extendedLanguageTag { return String(tag.prefix(2)).lowercased() }
        return option.locale?.language.languageCode?.identifier
    }

    /// A choice made in the player becomes the new preference, like on Android.
    func selectAudio(_ track: TrackChoice) {
        if engine == .vlc {
            vlc.currentAudioTrackIndex = track.id
            if let code = preferredLanguageCodes.first(where: { Self.matches(track.name, language: $0) }) { settings.audioLanguage = code }
            refreshVLCTracks()
        } else if let group = audibleGroup, group.options.indices.contains(Int(track.id)) {
            let option = group.options[Int(track.id)]
            player.currentItem?.select(option, in: group)
            if let code = Self.code(of: option) { settings.audioLanguage = code }
            refreshAppleTracks()
        }
    }

    func selectSubtitle(_ track: TrackChoice?) {
        if engine == .vlc {
            vlc.currentVideoSubTitleIndex = track?.id ?? -1
            if let track {
                if let code = preferredLanguageCodes.first(where: { Self.matches(track.name, language: $0) }) { settings.subtitleLanguage = code }
            } else {
                settings.subtitleLanguage = nil
            }
            refreshVLCTracks()
        } else if let group = legibleGroup {
            if let track, group.options.indices.contains(Int(track.id)) {
                let option = group.options[Int(track.id)]
                player.currentItem?.select(option, in: group)
                if let code = Self.code(of: option) { settings.subtitleLanguage = code }
            } else {
                player.currentItem?.select(nil, in: group)
                settings.subtitleLanguage = nil
            }
            refreshAppleTracks()
        }
    }

    // MARK: Transport

    func togglePlay() {
        if engine == .vlc {
            if vlc.isPlaying { vlc.pause() } else { vlc.play() }
        } else if player.timeControlStatus == .playing {
            player.pause()
        } else {
            player.play()
            player.rate = rate
        }
        playing.toggle()
        updateNowPlaying()
    }

    func skip(_ seconds: Int32) {
        if engine == .vlc {
            if seconds >= 0 { vlc.jumpForward(seconds) } else { vlc.jumpBackward(-seconds) }
        } else {
            let target = player.currentTime().seconds + Double(seconds)
            player.seek(to: CMTime(seconds: max(0, target), preferredTimescale: 600))
        }
        time = max(0, time + Double(seconds))
    }

    func seek(toFraction fraction: Double) {
        let value = max(0, min(1, fraction))
        if engine == .vlc {
            vlc.position = Float(value)
        } else if duration > 0 {
            player.seek(to: CMTime(seconds: value * duration, preferredTimescale: 600))
        }
        time = value * duration
    }

    func restartFromBeginning() {
        resumedFrom = nil
        seek(toFraction: 0)
    }

    func replay() {
        finished = false
        restartFromBeginning()
        if engine == .vlc {
            if vlc.state == .ended || vlc.state == .stopped { load(index, resume: false, forceEngine: .vlc) } else { vlc.play() }
        } else {
            player.play()
        }
    }

    func setRate(_ value: Float) {
        rate = value
        if engine == .vlc { vlc.rate = value } else if player.timeControlStatus == .playing { player.rate = value }
    }

    func setFit(_ value: VideoFit) {
        fit = value
        applyFit()
    }

    func applyFit() {
        switch engine {
        case .apple:
            playerLayer?.videoGravity = fit == .fit ? .resizeAspect : (fit == .fill ? .resizeAspectFill : .resize)
        case .vlc:
            let ratio = surfaceSize.width > 0 && surfaceSize.height > 0 ? "\(Int(surfaceSize.width)):\(Int(surfaceSize.height))" : nil
            vlc.videoAspectRatio = fit == .stretch ? ratio.flatMap { strdup($0) } : nil
            vlc.videoCropGeometry = fit == .fill ? ratio.flatMap { strdup($0) } : nil
        }
    }

    // MARK: Lock screen / Control Center

    private func setupRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            MainActor.assumeIsolated { self?.togglePlay() }
            return .success
        }
        center.playCommand.addTarget { [weak self] _ in
            MainActor.assumeIsolated { if self?.playing == false { self?.togglePlay() } }
            return .success
        }
        center.pauseCommand.addTarget { [weak self] _ in
            MainActor.assumeIsolated { if self?.playing == true { self?.togglePlay() } }
            return .success
        }
        center.nextTrackCommand.addTarget { [weak self] _ in
            MainActor.assumeIsolated { self?.next() }
            return .success
        }
    }

    private func updateNowPlaying() {
        var info: [String: Any] = [MPMediaItemPropertyTitle: current.subtitle ?? current.title,
                                   MPNowPlayingInfoPropertyIsLiveStream: isLive,
                                   MPNowPlayingInfoPropertyPlaybackRate: playing ? Double(rate) : 0]
        if current.subtitle != nil { info[MPMediaItemPropertyArtist] = current.title }
        if duration > 0 {
            info[MPMediaItemPropertyPlaybackDuration] = duration
            info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = time
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
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
        if duration > 0, time > duration * 0.9 {
            context.saveProgress(profileID: profile.id, item: current, position: duration, duration: duration)
        }
    }

    func saveProgress(force: Bool = false) {
        lastSave = Date()
        guard !isLive, time.isFinite, time > 0 else { return }
        context.saveProgress(profileID: profile.id, item: current, position: time, duration: duration)
        updateNowPlaying()
    }

    private func itemEnded() {
        guard !isLive else { return }
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
                self.saveProgress(force: true)
                self.sleep = .off
                self.finished = true
            }
        }
    }

    func stop() {
        saveProgress(force: true)
        countdownTask?.cancel()
        sleepTask?.cancel()
        resumeTask?.cancel()
        ticker?.invalidate()
        ticker = nil
        stopVLC()
        player.pause()
        cancellables.removeAll()
        itemCancellables.removeAll()
        player.replaceCurrentItem(with: nil)
        let center = MPRemoteCommandCenter.shared()
        [center.togglePlayPauseCommand, center.playCommand, center.pauseCommand, center.nextTrackCommand].forEach { $0.removeTarget(nil) }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }
}
