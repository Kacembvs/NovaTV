import AVKit
import MediaPlayer
import SwiftData
import SwiftUI
import VLCKitSPM

// MARK: Video surfaces

/// Apple engine surface: a bare AVPlayerLayer, so NOVA draws the same controls as on Android.
final class PlayerLayerView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}

struct AppleVideoView: UIViewRepresentable {
    let controller: PlayerController
    let request: PlaybackRequest

    func makeUIView(context: Context) -> PlayerLayerView {
        let view = PlayerLayerView()
        view.backgroundColor = .black
        view.playerLayer.player = controller.player
        controller.playerLayer = view.playerLayer
        controller.applyFit()
        PictureInPicture.shared.attach(layer: view.playerLayer, controller: controller, request: request)
        return view
    }

    func updateUIView(_ view: PlayerLayerView, context: Context) {
        if view.playerLayer.player !== controller.player { view.playerLayer.player = controller.player }
    }
}

/// Floating mini-player. iOS lets the user drag it anywhere, resize it and tuck it against an edge.
/// Started from the player button, it closes the full-screen player so the user can keep browsing NOVA;
/// started by leaving the app, it simply keeps the video on the home screen.
@MainActor
final class PictureInPicture: NSObject, AVPictureInPictureControllerDelegate {
    static let shared = PictureInPicture()

    private(set) var pip: AVPictureInPictureController?
    /// Kept alive while the full-screen player is closed and the video plays in the mini-player.
    private(set) var activeController: PlayerController?
    private(set) var activeRequest: PlaybackRequest?
    private var restoring = false
    var onDismissPlayer: (() -> Void)?
    var onRestorePlayer: ((PlaybackRequest) -> Void)?

    var isActive: Bool { pip?.isPictureInPictureActive == true }

    func attach(layer: AVPlayerLayer, controller: PlayerController, request: PlaybackRequest) {
        guard AVPictureInPictureController.isPictureInPictureSupported() else { return }
        if pip?.playerLayer !== layer {
            pip?.delegate = nil
            pip = AVPictureInPictureController(playerLayer: layer)
            pip?.delegate = self
        }
        pip?.canStartPictureInPictureAutomaticallyFromInline = controller.pictureInPictureAllowed
        activeController = controller
        activeRequest = request
        restoring = false
    }

    func start() {
        guard let pip, pip.isPictureInPicturePossible else { return }
        pip.startPictureInPicture()
    }

    /// The built-in VLC engine has no system mini-player.
    func disableAutomatic() { pip?.canStartPictureInPictureAutomaticallyFromInline = false }

    /// Same controller when the full-screen player reopens from the mini-player.
    func reusableController(for request: PlaybackRequest) -> PlayerController? {
        activeRequest?.id == request.id ? activeController : nil
    }

    /// Owns the controller only while the mini-player shows it.
    func owns(_ controller: PlayerController) -> Bool { isActive && activeController === controller }

    /// A new video replaces the one in the mini-player.
    func closeOther(than controller: PlayerController) {
        guard let current = activeController, current !== controller else { return }
        if isActive { pip?.stopPictureInPicture() }
        current.stop()
        activeController = nil
        activeRequest = nil
    }

    nonisolated func pictureInPictureControllerDidStartPictureInPicture(_ controller: AVPictureInPictureController) {
        Task { @MainActor in
            if UIApplication.shared.applicationState == .active { self.onDismissPlayer?() }
        }
    }

    nonisolated func pictureInPictureController(_ controller: AVPictureInPictureController,
                                                restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void) {
        Task { @MainActor in
            self.restoring = true
            if let request = self.activeRequest { self.onRestorePlayer?(request) }
            completionHandler(true)
        }
    }

    nonisolated func pictureInPictureControllerDidStopPictureInPicture(_ controller: AVPictureInPictureController) {
        Task { @MainActor in
            defer { self.restoring = false }
            // Closed with ×: stop the video unless the full-screen player is showing it again.
            guard !self.restoring, let player = self.activeController, !player.isPresented else { return }
            player.stop()
            self.activeController = nil
            self.activeRequest = nil
        }
    }

    nonisolated func pictureInPictureController(_ controller: AVPictureInPictureController,
                                                failedToStartPictureInPictureWithError error: Error) {}
}

final class VLCSurfaceView: UIView {
    var onResize: ((CGSize) -> Void)?
    override func layoutSubviews() {
        super.layoutSubviews()
        onResize?(bounds.size)
    }
}

struct VLCVideoView: UIViewRepresentable {
    let controller: PlayerController

    func makeUIView(context: Context) -> VLCSurfaceView {
        let view = VLCSurfaceView()
        view.backgroundColor = .black
        view.onResize = { [weak controller] size in
            Task { @MainActor in
                guard let controller, controller.surfaceSize != size else { return }
                controller.surfaceSize = size
                controller.applyFit()
            }
        }
        controller.attachVLCView(view)
        PictureInPicture.shared.disableAutomatic()
        return view
    }

    func updateUIView(_ view: VLCSurfaceView, context: Context) {}
}

// MARK: Brightness and volume

/// iOS only allows changing the system volume through MPVolumeView's slider; the hidden view
/// also replaces the system volume popup while the player is open.
@MainActor
enum SystemVolume {
    static weak var view: MPVolumeView?

    static var value: Float { AVAudioSession.sharedInstance().outputVolume }

    static func set(_ value: Float) {
        guard let slider = view?.subviews.compactMap({ $0 as? UISlider }).first else { return }
        slider.setValue(max(0, min(1, value)), animated: false)
        slider.sendActions(for: .valueChanged)
    }
}

struct VolumeBridge: UIViewRepresentable {
    func makeUIView(context: Context) -> MPVolumeView {
        let view = MPVolumeView(frame: CGRect(x: -100, y: -100, width: 10, height: 10))
        view.alpha = 0.01
        SystemVolume.view = view
        return view
    }

    func updateUIView(_ view: MPVolumeView, context: Context) {}
}

@MainActor
enum ScreenBrightness {
    static var screen: UIScreen? { (UIApplication.shared.connectedScenes.first as? UIWindowScene)?.screen }
    static var value: CGFloat { screen?.brightness ?? 0.5 }
    static func set(_ value: CGFloat) { screen?.brightness = max(0, min(1, value)) }
}

// MARK: Dynamic Island / notch

/// Margins that keep the player controls clear of the Dynamic Island, the rounded corners and the
/// home indicator. Read from the window because the video itself is drawn edge to edge.
struct PlayerInsets: Equatable {
    var top: CGFloat = 0
    var leading: CGFloat = 0
    var bottom: CGFloat = 0
    var trailing: CGFloat = 0

    @MainActor
    static func current(landscape: Bool) -> PlayerInsets {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        let window = scene?.windows.first { $0.isKeyWindow } ?? scene?.windows.first
        let safe = window?.safeAreaInsets ?? .zero
        let hasIsland = max(safe.top, safe.left, safe.right, safe.bottom) > 30
        if landscape {
            // The island moves to the left or right when rotating: protect both sides the same way.
            let side = max(safe.left, safe.right, hasIsland ? 59 : 0)
            return PlayerInsets(top: 0, leading: side, bottom: max(safe.bottom, hasIsland ? 21 : 0), trailing: side)
        }
        return PlayerInsets(top: max(safe.top, hasIsland ? 59 : 0), leading: safe.left, bottom: safe.bottom, trailing: safe.right)
    }
}

private struct PlayerInsetsKey: EnvironmentKey {
    static let defaultValue = PlayerInsets()
}

extension EnvironmentValues {
    var playerInsets: PlayerInsets {
        get { self[PlayerInsetsKey.self] }
        set { self[PlayerInsetsKey.self] = newValue }
    }
}

private struct PlayerSafeAreaPadding: ViewModifier {
    @Environment(\.playerInsets) private var insets

    func body(content: Self.Content) -> some View {
        content.padding(EdgeInsets(top: insets.top, leading: insets.leading, bottom: insets.bottom, trailing: insets.trailing))
    }
}

extension View {
    func playerSafeArea() -> some View { modifier(PlayerSafeAreaPadding()) }
}

// MARK: Player screen

enum PlayerPanel: Equatable {
    case options, audio, subtitles, speed, fit, sleep, info, external
}

struct PlayerScreen: View {
    let request: PlaybackRequest
    let profile: Profile
    @StateObject private var controller: PlayerController
    @Environment(\.dismiss) private var dismiss
    @Environment(\.palette) private var appPalette
    private let freeRotation: Bool
    private let portraitBrowse: Bool
    /// Navigation offered under the video, from where playback started.
    private let browseKind: ContentKind
    @State private var controlsVisible = true
    @State private var hideTask: Task<Void, Never>?
    @State private var panel: PlayerPanel?
    @State private var locked = false
    @State private var scrubbing: Double?
    @State private var flash: String?
    @State private var gesture: GestureFeedback?
    @State private var dragStart: (side: GestureFeedback.Kind, value: Double)?
    @State private var originalBrightness: CGFloat?

    struct GestureFeedback: Equatable {
        enum Kind { case brightness, volume }
        var kind: Kind
        var value: Double
    }

    init(request: PlaybackRequest, profile: Profile, context: ModelContext, settings: AppSettings, downloads: DownloadCenter) {
        self.request = request
        self.profile = profile
        freeRotation = settings.freeRotation
        portraitBrowse = settings.portraitBrowse
        switch request.items[request.startIndex].kind {
        case .live: browseKind = .live
        case .movie: browseKind = .movie
        default: browseKind = .series
        }
        _controller = StateObject(wrappedValue: PictureInPicture.shared.reusableController(for: request)
            ?? PlayerController(request: request, profile: profile, context: context, settings: settings, downloads: downloads))
    }

    /// The player keeps a dark, readable look even with the light palette (as on Android).
    private var palette: Palette { appPalette.isLight ? Palette.of(.monochrome) : appPalette }

    var body: some View {
        GeometryReader { geo in
            let landscape = geo.size.width > geo.size.height
            let portrait = portraitBrowse && !landscape
            let insets = PlayerInsets.current(landscape: landscape)
            ZStack(alignment: .top) {
                Color.black
                VStack(spacing: 0) {
                    if portrait { Color.black.frame(height: insets.top) }
                    stage(compact: portrait)
                        .frame(width: geo.size.width, height: portrait ? geo.size.width * 9 / 16 : geo.size.height)
                        .clipped()
                        .environment(\.playerInsets, portrait ? PlayerInsets() : insets)
                    if portrait {
                        PortraitBrowser(controller: controller, profile: profile, kind: browseKind)
                            .padding(.bottom, insets.bottom)
                            .background(appPalette.background)
                            .environment(\.palette, appPalette)
                            .environment(\.colorScheme, appPalette.isLight ? .light : .dark)
                    }
                }
                Group {
                    panelLayer
                    statusLayer
                }
                .frame(width: geo.size.width, height: geo.size.height)
                .environment(\.playerInsets, insets)
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
        .ignoresSafeArea()
        .environment(\.palette, palette)
        .environment(\.colorScheme, .dark)
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
        .animation(.easeInOut(duration: 0.2), value: controlsVisible)
        .animation(.easeInOut(duration: 0.2), value: panel)
        .onAppear(perform: appeared)
        .onDisappear(perform: disappeared)
    }

    /// Video with its controls; full screen in landscape, 16:9 above the browser in portrait.
    /// The insets are recomputed on every rotation to keep the controls clear of the Dynamic Island.
    private func stage(compact: Bool) -> some View {
        ZStack {
            Color.black
            videoSurface
            VolumeBridge().frame(width: 1, height: 1).allowsHitTesting(false)
            gestureLayer
            feedbackLayer
            controlsLayer(compact: compact)
        }
    }

    private var videoSurface: some View {
        Group {
            if controller.engine == .apple {
                AppleVideoView(controller: controller, request: request)
            } else {
                VLCVideoView(controller: controller)
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }

    @ViewBuilder
    private var feedbackLayer: some View {
        if controller.buffering && controller.error == nil && !controller.finished {
            ProgressView().tint(.white).scaleEffect(1.6).allowsHitTesting(false)
        }
        if let flash {
            Text(flash).font(.title2.bold()).foregroundStyle(.white)
                .padding(14).background(.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 12))
                .allowsHitTesting(false)
        }
        if let gesture { GestureIndicator(feedback: gesture) }
    }

    @ViewBuilder
    private func controlsLayer(compact: Bool) -> some View {
        if locked {
            lockedOverlay
        } else if controlsVisible && panel == nil {
            PlayerControls(controller: controller, scrubbing: $scrubbing, onClose: close, onPanel: open,
                           onLock: lock, onInteraction: scheduleHide, compact: compact,
                           onFullscreen: portraitBrowse ? { toggleFullscreen(enter: compact) } : nil)
                .transition(.opacity)
        }
        if let from = controller.resumedFrom, !locked {
            ResumeBanner(position: from) { controller.restartFromBeginning() }
        }
    }

    @ViewBuilder
    private var panelLayer: some View {
        if let panel {
            PlayerPanelView(panel: panel, controller: controller, onSelect: open, onClose: closePanel, onExternal: openExternal)
        }
    }

    @ViewBuilder
    private var statusLayer: some View {
        if let message = controller.error {
            PlayerErrorView(message: message, controller: controller, onClose: close, onExternal: { open(.external) })
        } else if let left = controller.nextCountdown, controller.hasNext {
            let next = controller.items[controller.index + 1]
            NextUpView(title: next.subtitle ?? next.title, seconds: left,
                       onPlay: { controller.next() }, onCancel: { controller.cancelCountdown() })
        } else if controller.finished {
            EndScreen(controller: controller, onClose: close)
        }
    }

    private func appeared() {
        controller.isPresented = true
        PictureInPicture.shared.closeOther(than: controller)
        if portraitBrowse {
            // Opens in the way the phone is held: portrait with navigation, or landscape full screen.
            OrientationLock.set(.allButUpsideDown, preferred: OrientationLock.currentMask)
        } else {
            OrientationLock.set(freeRotation ? .allButUpsideDown : .landscape, preferred: .landscapeRight)
        }
        originalBrightness = ScreenBrightness.value
        scheduleHide()
    }

    private func toggleFullscreen(enter: Bool) {
        if enter {
            OrientationLock.set(.landscape, preferred: .landscapeRight)
        } else {
            OrientationLock.set(.allButUpsideDown, preferred: .portrait)
        }
        scheduleHide()
    }

    private func disappeared() {
        controller.isPresented = false
        // Still playing in the mini-player: keep the video running.
        if !PictureInPicture.shared.owns(controller) { controller.stop() }
        if let originalBrightness { ScreenBrightness.set(originalBrightness) }
        OrientationLock.set(.all, preferred: .portrait)
    }

    private func lock() {
        locked = true
        controlsVisible = false
    }

    private func closePanel() {
        panel = nil
        showControls()
    }

    // MARK: Gestures

    private var gestureLayer: some View {
        GeometryReader { geo in
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture(count: 2) { location in
                    guard !locked, controller.gesturesEnabled, !controller.isLive else { return }
                    let forward = location.x > geo.size.width / 2
                    controller.skip(forward ? 10 : -10)
                    show(flash: forward ? "+10 s" : "−10 s")
                }
                .onTapGesture {
                    if locked { controlsVisible.toggle(); scheduleHide() } else if panel != nil { panel = nil } else { toggleControls() }
                }
                .gesture(
                    DragGesture(minimumDistance: 12)
                        .onChanged { value in
                            guard !locked, controller.gesturesEnabled,
                                  abs(value.translation.height) > abs(value.translation.width) else { return }
                            if dragStart == nil {
                                let left = value.startLocation.x < geo.size.width / 2
                                dragStart = left ? (.brightness, Double(ScreenBrightness.value)) : (.volume, Double(SystemVolume.value))
                            }
                            guard let start = dragStart else { return }
                            let delta = -value.translation.height / max(200, geo.size.height * 0.8)
                            let level = max(0, min(1, start.value + delta))
                            if start.side == .brightness { ScreenBrightness.set(level) } else { SystemVolume.set(Float(level)) }
                            gesture = GestureFeedback(kind: start.side, value: level)
                        }
                        .onEnded { _ in
                            dragStart = nil
                            Task {
                                try? await Task.sleep(nanoseconds: 700_000_000)
                                if dragStart == nil { gesture = nil }
                            }
                        }
                )
        }
        .ignoresSafeArea()
    }

    private var lockedOverlay: some View {
        VStack {
            Spacer()
            HStack {
                if controlsVisible {
                    Button {
                        locked = false
                        showControls()
                    } label: {
                        Label("Déverrouiller", systemImage: "lock.open.fill")
                            .font(.subheadline.weight(.semibold)).foregroundStyle(.white)
                            .padding(.horizontal, 16).padding(.vertical, 12)
                            .background(.black.opacity(0.6), in: Capsule())
                    }
                    .transition(.opacity)
                }
                Spacer()
            }
            .padding(24)
        }
        .playerSafeArea()
    }

    // MARK: Helpers

    private func open(_ target: PlayerPanel) {
        panel = target
        hideTask?.cancel()
    }

    private func openExternal(_ player: ExternalPlayer) {
        guard let url = controller.streamURL else { return }
        controller.stop()
        player.open(url)
        dismiss()
    }

    private func toggleControls() {
        controlsVisible.toggle()
        if controlsVisible { scheduleHide() }
    }

    private func showControls() {
        controlsVisible = true
        scheduleHide()
    }

    private func scheduleHide() {
        hideTask?.cancel()
        hideTask = Task {
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            guard !Task.isCancelled, controller.playing, scrubbing == nil, panel == nil else { return }
            controlsVisible = false
        }
    }

    private func show(flash text: String) {
        flash = text
        Task {
            try? await Task.sleep(nanoseconds: 700_000_000)
            if flash == text { flash = nil }
        }
    }

    private func close() {
        controller.stop()
        dismiss()
    }
}

// MARK: Controls

private struct PlayerControls: View {
    @ObservedObject var controller: PlayerController
    @Binding var scrubbing: Double?
    let onClose: () -> Void
    let onPanel: (PlayerPanel) -> Void
    let onLock: () -> Void
    let onInteraction: () -> Void
    /// Portrait: video above the browser, with fewer buttons.
    var compact = false
    /// Enter (portrait) or leave (landscape) full screen; nil when portrait viewing is off.
    var onFullscreen: (() -> Void)? = nil
    @Environment(\.palette) private var palette

    var body: some View {
        ZStack {
            LinearGradient(colors: [.black.opacity(0.7), .clear, .clear, .black.opacity(0.8)], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
                .allowsHitTesting(false)
            VStack(spacing: 0) {
                topBar
                Spacer(minLength: 0)
                transport
                Spacer(minLength: 0)
                bottomBar
            }
            .padding(.horizontal, compact ? 10 : 16)
            .padding(.vertical, compact ? 6 : 10)
            .playerSafeArea()
        }
    }

    private var topBar: some View {
        HStack(spacing: compact ? 6 : 10) {
            BarButton(icon: compact ? "chevron.down" : "chevron.backward", label: "Retour", action: onClose)
            VStack(alignment: .leading, spacing: 2) {
                Text(controller.current.title).font(compact ? .subheadline.weight(.semibold) : .headline).foregroundStyle(.white).lineLimit(1)
                if let subtitle = controller.current.subtitle {
                    Text(subtitle).font(.caption).foregroundStyle(.white.opacity(0.75)).lineLimit(1)
                }
            }
            if controller.isLive && !compact {
                Text("EN DIRECT").font(.caption2.bold()).foregroundStyle(.white)
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(Color.red, in: RoundedRectangle(cornerRadius: 4))
            }
            Spacer(minLength: 4)
            if compact { compactButtons } else { actionButtons }
        }
    }

    @ViewBuilder
    private var compactButtons: some View {
        if controller.pictureInPictureAllowed && AVPictureInPictureController.isPictureInPictureSupported() {
            BarButton(icon: "pip.enter", label: "Mini-lecteur") { PictureInPicture.shared.start() }
        }
        AirPlayButton().frame(width: 44, height: 44).background(.black.opacity(0.35), in: Circle())
        BarButton(icon: "gearshape", label: "Options de lecture") { onPanel(.options) }
    }

    @ViewBuilder
    private var actionButtons: some View {
            if controller.audioTracks.count > 1 { BarButton(icon: "waveform", label: "Audio") { onPanel(.audio) } }
            if !controller.subtitleTracks.isEmpty { BarButton(icon: "captions.bubble", label: "Sous-titres") { onPanel(.subtitles) } }
            if !controller.isLive { BarButton(icon: "speedometer", label: "Vitesse") { onPanel(.speed) } }
            BarButton(icon: controller.sleep == .off ? "moon" : "moon.zzz.fill", label: "Minuteur de sommeil",
                      highlighted: controller.sleep != .off) { onPanel(.sleep) }
            if controller.pictureInPictureAllowed && AVPictureInPictureController.isPictureInPictureSupported() {
                BarButton(icon: "pip.enter", label: "Mini-lecteur") { PictureInPicture.shared.start() }
            }
            AirPlayButton().frame(width: 44, height: 44).background(.black.opacity(0.35), in: Circle())
            BarButton(icon: "lock", label: "Verrouiller l'écran", action: onLock)
            if let onFullscreen {
                BarButton(icon: "arrow.down.right.and.arrow.up.left", label: "Quitter le plein écran", action: onFullscreen)
            }
            BarButton(icon: "gearshape", label: "Options de lecture") { onPanel(.options) }
    }

    private var transport: some View {
        let small: CGFloat = compact ? 20 : 26
        return HStack(spacing: compact ? 28 : 40) {
            if controller.isLive {
                if controller.index > 0 { TransportButton(icon: "backward.end.fill", label: "Chaîne précédente", size: small) { controller.previous(); onInteraction() } }
            } else {
                TransportButton(icon: "gobackward.10", label: "Reculer de 10 secondes", size: small) { controller.skip(-10); onInteraction() }
            }
            TransportButton(icon: controller.playing ? "pause.fill" : "play.fill", label: controller.playing ? "Pause" : "Lecture", size: compact ? 26 : 36) {
                controller.togglePlay()
                onInteraction()
            }
            if controller.isLive {
                if controller.hasNext { TransportButton(icon: "forward.end.fill", label: "Chaîne suivante", size: small) { controller.next(); onInteraction() } }
            } else {
                TransportButton(icon: "goforward.10", label: "Avancer de 10 secondes", size: small) { controller.skip(10); onInteraction() }
            }
        }
    }

    @ViewBuilder
    private var fullscreenButton: some View {
        if compact, let onFullscreen {
            Button(action: onFullscreen) {
                Image(systemName: "arrow.up.left.and.arrow.down.right").font(.system(size: 15, weight: .semibold)).foregroundStyle(.white)
                    .frame(width: 36, height: 36)
            }
            .accessibilityLabel("Plein écran")
        }
    }

    @ViewBuilder
    private var bottomBar: some View {
        if !controller.isLive && controller.duration > 0 {
            let position = scrubbing ?? min(1, controller.time / controller.duration)
            VStack(spacing: compact ? 0 : 4) {
                HStack(spacing: 6) {
                    Slider(value: Binding(get: { position }, set: {
                        scrubbing = $0
                        controller.scrub(toFraction: $0)
                        onInteraction()
                    }), in: 0...1) { editing in
                        if !editing, let value = scrubbing {
                            controller.seek(toFraction: value)
                            scrubbing = nil
                        }
                    }
                    .tint(palette.accent)
                    fullscreenButton
                }
                HStack {
                    Text((position * controller.duration).clock)
                    Spacer()
                    if controller.hasNext && !compact {
                        Button { controller.next() } label: {
                            Label(controller.items[controller.index + 1].kind == .episode ? "Épisode suivant" : "Suivant", systemImage: "forward.end")
                                .font(.caption.weight(.semibold))
                        }
                        .foregroundStyle(.white)
                        Spacer()
                    }
                    Text("−" + ((1 - position) * controller.duration).clock)
                }
                .font(.caption.monospacedDigit())
                .foregroundStyle(.white.opacity(0.85))
            }
        } else {
            HStack {
                if controller.isLive && compact {
                    Text("EN DIRECT").font(.caption2.bold()).foregroundStyle(.white)
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Color.red, in: RoundedRectangle(cornerRadius: 4))
                } else {
                    Text(controller.engine.label).font(.caption).foregroundStyle(.white.opacity(0.6))
                }
                Spacer()
                fullscreenButton
            }
        }
    }
}

private struct BarButton: View {
    let icon: String
    let label: String
    var highlighted = false
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(highlighted ? palette.accent2 : .white)
                .frame(width: 44, height: 44)
                .background(.black.opacity(0.35), in: Circle())
        }
        .accessibilityLabel(label)
    }
}

private struct TransportButton: View {
    let icon: String
    let label: String
    var size: CGFloat = 26
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: size, weight: .semibold)).foregroundStyle(.white)
                .frame(width: size * 2.2, height: size * 2.2)
                .background(.black.opacity(0.4), in: Circle())
        }
        .accessibilityLabel(label)
    }
}

private struct AirPlayButton: UIViewRepresentable {
    func makeUIView(context: Context) -> AVRoutePickerView {
        let view = AVRoutePickerView()
        view.tintColor = .white
        view.activeTintColor = .systemBlue
        view.prioritizesVideoDevices = true
        return view
    }

    func updateUIView(_ view: AVRoutePickerView, context: Context) {}
}

private struct GestureIndicator: View {
    let feedback: PlayerScreen.GestureFeedback

    var body: some View {
        let icon: String = {
            switch feedback.kind {
            case .brightness: return feedback.value < 0.35 ? "sun.min.fill" : "sun.max.fill"
            case .volume: return feedback.value == 0 ? "speaker.slash.fill" : (feedback.value < 0.5 ? "speaker.wave.1.fill" : "speaker.wave.3.fill")
            }
        }()
        HStack(spacing: 12) {
            Image(systemName: icon).font(.title2).frame(width: 30)
            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.25))
                Capsule().fill(.white).frame(width: 160 * feedback.value)
            }
            .frame(width: 160, height: 6)
            Text("\(Int(feedback.value * 100)) %").font(.subheadline.monospacedDigit()).frame(width: 48, alignment: .trailing)
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 18).padding(.vertical, 14)
        .background(.black.opacity(0.7), in: RoundedRectangle(cornerRadius: 14))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(feedback.kind == .brightness ? "Luminosité \(Int(feedback.value * 100)) %" : "Volume \(Int(feedback.value * 100)) %")
        .allowsHitTesting(false)
    }
}

private struct ResumeBanner: View {
    let position: Double
    let onRestart: () -> Void

    var body: some View {
        VStack {
            Spacer()
            HStack(spacing: 12) {
                Text("Reprise à \(position.clock)").font(.subheadline).foregroundStyle(.white)
                Button("Recommencer", action: onRestart).font(.subheadline.weight(.semibold)).foregroundStyle(.white)
            }
            .padding(.horizontal, 16).padding(.vertical, 10)
            .background(.black.opacity(0.75), in: Capsule())
            .padding(.bottom, 90)
        }
        .playerSafeArea()
    }
}

// MARK: Panels (same organisation as Android)

private struct PlayerPanelView: View {
    let panel: PlayerPanel
    @ObservedObject var controller: PlayerController
    let onSelect: (PlayerPanel) -> Void
    let onClose: () -> Void
    let onExternal: (ExternalPlayer) -> Void
    @Environment(\.palette) private var palette
    @Environment(\.novaStyle) private var style

    var body: some View {
        ZStack(alignment: .trailing) {
            Color.black.opacity(0.35).contentShape(Rectangle()).onTapGesture(perform: onClose)
            HStack(spacing: 0) {
                Spacer(minLength: 0).allowsHitTesting(false)
                panelCard
            }
            .playerSafeArea()
        }
        .transition(.move(edge: .trailing))
    }

    private var panelCard: some View {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    if panel != .options {
                        Button { onSelect(.options) } label: { Image(systemName: "chevron.backward").font(.headline) }
                            .foregroundStyle(.white)
                            .accessibilityLabel("Options de lecture")
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title).font(.title3.bold()).foregroundStyle(.white)
                        if let subtitle { Text(subtitle).font(.caption).foregroundStyle(palette.textSecondary) }
                    }
                    Spacer()
                    Button(action: onClose) { Image(systemName: "xmark").font(.headline) }
                        .foregroundStyle(.white)
                        .accessibilityLabel("Fermer")
                }
                ScrollView {
                    VStack(spacing: 8) { content }
                }
            }
            .padding(18)
            .frame(width: 360)
            .frame(maxHeight: .infinity)
            .background(palette.background2.opacity(0.97), in: RoundedRectangle(cornerRadius: style.radius))
            .overlay(RoundedRectangle(cornerRadius: style.radius).stroke(palette.border))
            .padding(.vertical, 8)
            .padding(.trailing, 8)
    }

    private var title: String {
        switch panel {
        case .options: return "Options de lecture"
        case .audio: return "Langue audio"
        case .subtitles: return "Sous-titres"
        case .speed: return "Vitesse de lecture"
        case .fit: return "Format d'image"
        case .sleep: return "Minuteur de sommeil"
        case .info: return "Informations techniques"
        case .external: return "Lecteur externe"
        }
    }

    private var subtitle: String? {
        switch panel {
        case .sleep: return "Prioritaire sur la lecture automatique : la progression est enregistrée à l'arrêt."
        case .info: return "Mise à jour chaque seconde."
        case .external: return "Le lecteur NOVA s'arrête et la lecture continue dans l'application choisie."
        default: return nil
        }
    }

    @ViewBuilder
    private var content: some View {
        switch panel {
        case .options:
            let audio = controller.audioTracks.first { $0.id == controller.currentAudio }?.name ?? "Automatique"
            let subs = controller.subtitleTracks.first { $0.id == controller.currentSubtitle }?.name ?? "Désactivés"
            PanelRow(title: "Audio", icon: "waveform", trailing: audio, chevron: true) { onSelect(.audio) }
            PanelRow(title: "Sous-titres", icon: "captions.bubble", trailing: subs, chevron: true) { onSelect(.subtitles) }
            if !controller.isLive {
                PanelRow(title: "Vitesse", icon: "speedometer", trailing: speedLabel(controller.rate), chevron: true) { onSelect(.speed) }
            }
            PanelRow(title: "Format d'image", icon: "aspectratio", trailing: controller.fit.label, chevron: true) { onSelect(.fit) }
            PanelRow(title: "Minuteur de sommeil", icon: "moon.zzz", trailing: controller.sleep == .off ? "Désactivé" : "Actif", chevron: true) {
                onSelect(.sleep)
            }
            PanelRow(title: controller.engine == .apple ? "Passer au moteur VLC intégré" : "Passer au lecteur Apple",
                     icon: "arrow.triangle.2.circlepath", trailing: controller.engine.label) {
                controller.switchEngine()
                onClose()
            }
            PanelRow(title: "Informations techniques", icon: "info.circle", chevron: true) { onSelect(.info) }
            if controller.streamURL != nil {
                PanelRow(title: "Ouvrir dans un lecteur externe", icon: "arrow.up.forward.app", chevron: true) { onSelect(.external) }
            }
        case .audio:
            if controller.audioTracks.count <= 1 { PanelNote(text: "Une seule piste audio disponible.") }
            ForEach(controller.audioTracks) { track in
                PanelRow(title: track.name, selected: track.id == controller.currentAudio) { controller.selectAudio(track) }
            }
        case .subtitles:
            PanelRow(title: "Désactivés", selected: controller.currentSubtitle < 0) { controller.selectSubtitle(nil) }
            if controller.subtitleTracks.isEmpty { PanelNote(text: "Aucun sous-titre dans ce flux.") }
            ForEach(controller.subtitleTracks) { track in
                PanelRow(title: track.name, selected: track.id == controller.currentSubtitle) { controller.selectSubtitle(track) }
            }
        case .speed:
            ForEach([0.5, 0.75, 1.0, 1.25, 1.5, 2.0], id: \.self) { value in
                PanelRow(title: speedLabel(Float(value)), selected: abs(Double(controller.rate) - value) < 0.01) { controller.setRate(Float(value)) }
            }
        case .fit:
            ForEach(VideoFit.allCases) { fit in
                PanelRow(title: fit.label, selected: controller.fit == fit) { controller.setFit(fit) }
            }
        case .sleep:
            PanelRow(title: "Désactivé", selected: controller.sleep == .off) { controller.setSleep(.off); onClose() }
            if !controller.isLive {
                PanelRow(title: controller.current.kind == .movie ? "Fin du film" : "Fin de l'épisode", selected: controller.sleep == .endOfItem) {
                    controller.setSleep(.endOfItem)
                    onClose()
                }
            }
            ForEach([15, 30, 45, 60, 90, 120], id: \.self) { minutes in
                PanelRow(title: "Dans \(minutes) minutes", selected: controller.sleep == .minutes(minutes)) {
                    controller.setSleep(.minutes(minutes))
                    onClose()
                }
            }
        case .info:
            InfoRow(name: "Moteur", value: controller.engine.label)
            InfoRow(name: "Résolution", value: controller.videoSize == .zero ? "—" : "\(Int(controller.videoSize.width)) × \(Int(controller.videoSize.height))")
            InfoRow(name: "Débit", value: controller.bitrate > 0 ? String(format: "%.1f Mb/s", controller.bitrate / 1_000_000) : "—")
            InfoRow(name: "Format", value: (controller.streamURL?.pathExtension.uppercased()).flatMap { $0.isEmpty ? nil : $0 } ?? "Fichier téléchargé")
            InfoRow(name: "Position", value: controller.isLive ? "Direct" : "\(controller.time.clock) / \(controller.duration.clock)")
            InfoRow(name: "Pistes", value: "\(controller.audioTracks.count) audio · \(controller.subtitleTracks.count) sous-titres")
        case .external:
            ForEach(ExternalPlayer.allCases) { player in
                PanelRow(title: player.isInstalled ? player.name : "\(player.name) (à installer)", icon: "arrow.up.forward.app") { onExternal(player) }
            }
        }
    }

    private func speedLabel(_ value: Float) -> String {
        value == 1 ? "Normale" : "×" + (Double(value)).formatted(.number.precision(.fractionLength(0...2)))
    }
}

private struct PanelRow: View {
    let title: String
    var icon: String? = nil
    var trailing: String? = nil
    var selected = false
    var chevron = false
    let action: () -> Void
    @Environment(\.palette) private var palette
    @Environment(\.novaStyle) private var style

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                if let icon { Image(systemName: icon).frame(width: 22).foregroundStyle(.white) }
                Text(title).foregroundStyle(.white).multilineTextAlignment(.leading)
                Spacer(minLength: 8)
                if let trailing { Text(trailing).font(.subheadline).foregroundStyle(palette.textSecondary).lineLimit(1) }
                if selected { Image(systemName: "checkmark").font(.headline).foregroundStyle(palette.accent) }
                if chevron { Image(systemName: "chevron.forward").font(.caption).foregroundStyle(palette.textSecondary) }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(minHeight: 48)
            .background(palette.card, in: RoundedRectangle(cornerRadius: style.radius))
            .overlay(RoundedRectangle(cornerRadius: style.radius).stroke(selected ? palette.accent : palette.border, lineWidth: selected ? 2 : 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct PanelNote: View {
    let text: String
    @Environment(\.palette) private var palette
    var body: some View {
        Text(text).font(.footnote).foregroundStyle(palette.textSecondary).frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct InfoRow: View {
    let name: String
    let value: String
    @Environment(\.palette) private var palette
    var body: some View {
        HStack {
            Text(name).foregroundStyle(palette.textSecondary)
            Spacer()
            Text(value).foregroundStyle(.white).monospacedDigit()
        }
        .font(.subheadline)
        .padding(.vertical, 6)
    }
}

// MARK: Overlays

/// Dark panel button that stays readable with every palette.
private struct OverlayButton: View {
    let title: String
    var icon: String? = nil
    var prominent = false
    let action: () -> Void
    @Environment(\.palette) private var palette
    @Environment(\.novaStyle) private var style

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let icon { Image(systemName: icon) }
                Text(title)
            }
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(prominent ? palette.onAccent : .white)
            .padding(.horizontal, 16).padding(.vertical, 11)
            .background(prominent ? palette.accent : Color.white.opacity(0.14), in: RoundedRectangle(cornerRadius: style.radius))
            .overlay(RoundedRectangle(cornerRadius: style.radius).stroke(Color.white.opacity(prominent ? 0 : 0.35)))
        }
        .buttonStyle(.plain)
    }
}

private struct PlayerErrorView: View {
    let message: String
    @ObservedObject var controller: PlayerController
    let onClose: () -> Void
    let onExternal: () -> Void
    @Environment(\.novaStyle) private var style

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill").font(.largeTitle).foregroundStyle(.yellow)
            Text(message).multilineTextAlignment(.center).foregroundStyle(.white)
            HStack {
                OverlayButton(title: "Réessayer", icon: "arrow.clockwise", prominent: true) { controller.load(controller.index, resume: true) }
                OverlayButton(title: controller.engine == .apple ? "Moteur VLC intégré" : "Lecteur Apple", icon: "arrow.triangle.2.circlepath") {
                    controller.switchEngine()
                }
            }
            HStack {
                if controller.streamURL != nil { OverlayButton(title: "Lecteur externe", icon: "arrow.up.forward.app", action: onExternal) }
                if controller.isLive && controller.hasNext { OverlayButton(title: "Chaîne suivante") { controller.next() } }
                OverlayButton(title: "Fermer", action: onClose)
            }
        }
        .padding(24)
        .background(.black.opacity(0.88), in: RoundedRectangle(cornerRadius: style.radius))
        .padding()
        .playerSafeArea()
    }
}

private struct NextUpView: View {
    let title: String
    let seconds: Int
    let onPlay: () -> Void
    let onCancel: () -> Void
    @Environment(\.novaStyle) private var style

    var body: some View {
        VStack {
            Spacer()
            HStack {
                Spacer()
                VStack(alignment: .leading, spacing: 10) {
                    Text("Épisode suivant dans \(seconds) s").font(.caption).foregroundStyle(.white.opacity(0.8))
                    Text(title).font(.headline).foregroundStyle(.white).lineLimit(2)
                    HStack {
                        OverlayButton(title: "Lire maintenant", icon: "play.fill", prominent: true, action: onPlay)
                        OverlayButton(title: "Annuler", action: onCancel)
                    }
                }
                .padding(16)
                .frame(maxWidth: 340, alignment: .leading)
                .background(.black.opacity(0.85), in: RoundedRectangle(cornerRadius: style.radius))
            }
            .padding(24)
            .padding(.bottom, 60)
        }
        .playerSafeArea()
    }
}

private struct EndScreen: View {
    @ObservedObject var controller: PlayerController
    let onClose: () -> Void
    @Environment(\.novaStyle) private var style

    var body: some View {
        VStack(spacing: 14) {
            Text(controller.current.kind == .movie ? "Film terminé" : "Lecture terminée").font(.title3.bold()).foregroundStyle(.white)
            HStack {
                if controller.hasNext {
                    OverlayButton(title: "Épisode suivant", icon: "forward.end.fill", prominent: true) { controller.next() }
                    OverlayButton(title: "Revoir", icon: "gobackward") { controller.replay() }
                } else {
                    OverlayButton(title: "Revoir", icon: "gobackward", prominent: true) { controller.replay() }
                }
                OverlayButton(title: "Fermer", action: onClose)
            }
            if controller.hasNext {
                Text("Suivant : " + (controller.items[controller.index + 1].subtitle ?? controller.items[controller.index + 1].title))
                    .font(.caption).foregroundStyle(.white.opacity(0.7)).lineLimit(1)
            }
        }
        .padding(24)
        .background(.black.opacity(0.85), in: RoundedRectangle(cornerRadius: style.radius))
        .playerSafeArea()
    }
}
