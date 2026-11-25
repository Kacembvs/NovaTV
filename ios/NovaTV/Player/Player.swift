import AVKit
import SwiftUI
import SwiftData
import VLCKitSPM

struct SystemPlayerView: UIViewControllerRepresentable {
    @ObservedObject var controller: PlayerController

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let vc = AVPlayerViewController()
        vc.player = controller.player
        vc.allowsPictureInPicturePlayback = controller.pictureInPicture
        vc.canStartPictureInPictureAutomaticallyFromInline = controller.pictureInPicture
        vc.updatesNowPlayingInfoCenter = true
        vc.showsPlaybackControls = true
        vc.videoGravity = .resizeAspect
        return vc
    }

    func updateUIViewController(_ vc: AVPlayerViewController, context: Context) {
        if vc.player !== controller.player { vc.player = controller.player }
    }
}

/// Surface the built-in VLC engine draws into.
struct VLCVideoView: UIViewRepresentable {
    let controller: PlayerController

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .black
        controller.attachVLCView(view)
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {}
}

struct PlayerScreen: View {
    let request: PlaybackRequest
    let profile: Profile
    @StateObject private var controller: PlayerController
    @Environment(\.dismiss) private var dismiss
    private let freeRotation: Bool

    init(request: PlaybackRequest, profile: Profile, context: ModelContext, settings: AppSettings, downloads: DownloadCenter) {
        self.request = request
        self.profile = profile
        freeRotation = settings.freeRotation
        _controller = StateObject(wrappedValue: PlayerController(request: request, profile: profile, context: context, settings: settings, downloads: downloads))
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if controller.engine == .apple {
                SystemPlayerView(controller: controller).ignoresSafeArea()
                VStack {
                    PlayerTopBar(controller: controller, onClose: close)
                    Spacer()
                }
            } else {
                VLCPlayerLayer(controller: controller, onClose: close)
            }
            if let message = controller.error {
                PlayerErrorView(message: message, controller: controller, onClose: close)
            }
            if let left = controller.nextCountdown, controller.hasNext {
                NextUpView(title: controller.items[controller.index + 1].subtitle ?? controller.items[controller.index + 1].title,
                           seconds: left, onPlay: { controller.next() }, onCancel: { controller.cancelCountdown() })
            }
        }
        .environment(\.colorScheme, .dark)
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
        .onAppear {
            OrientationLock.set(freeRotation ? .allButUpsideDown : .landscape, preferred: .landscapeRight)
        }
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

/// Close, title and options shared by both engines.
private struct PlayerTopBar: View {
    @ObservedObject var controller: PlayerController
    let onClose: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            PlayerChromeButton(systemImage: "xmark", label: "Fermer", action: onClose)
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
            PlayerOptionsMenu(controller: controller, onClose: onClose)
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }
}

private struct PlayerOptionsMenu: View {
    @ObservedObject var controller: PlayerController
    let onClose: () -> Void

    var body: some View {
        Menu {
            if controller.engine == .vlc {
                if !controller.audioTracks.isEmpty {
                    Section("Audio") {
                        ForEach(controller.audioTracks.filter { $0.id >= 0 }) { track in
                            Button { controller.selectAudio(track) } label: {
                                check(track.name, controller.currentAudio == track.id)
                            }
                        }
                    }
                }
                Section("Sous-titres") {
                    Button { controller.selectSubtitle(nil) } label: { check("Désactivés", controller.currentSubtitle < 0) }
                    ForEach(controller.subtitleTracks.filter { $0.id >= 0 }) { track in
                        Button { controller.selectSubtitle(track) } label: { check(track.name, controller.currentSubtitle == track.id) }
                    }
                }
                if !controller.isLive {
                    Section("Vitesse") {
                        ForEach([0.5, 0.75, 1.0, 1.25, 1.5, 2.0], id: \.self) { value in
                            Button { controller.setRate(Float(value)) } label: {
                                check(value == 1 ? "Normale" : "×\(value.formatted())", abs(Double(controller.rate) - value) < 0.01)
                            }
                        }
                    }
                }
            }
            Section("Minuteur de sommeil") {
                Button { controller.setSleep(.off) } label: { check("Désactivé", controller.sleep == .off) }
                ForEach([15, 30, 45, 60, 90], id: \.self) { minutes in
                    Button { controller.setSleep(.minutes(minutes)) } label: { check("\(minutes) minutes", controller.sleep == .minutes(minutes)) }
                }
                if !controller.isLive {
                    Button { controller.setSleep(.endOfItem) } label: {
                        check(controller.current.kind == .movie ? "Fin du film" : "Fin de l'épisode", controller.sleep == .endOfItem)
                    }
                }
            }
            Section("Moteur de lecture") {
                Button { controller.switchEngine() } label: {
                    Label(controller.engine == .apple ? "Passer au moteur VLC intégré" : "Passer au lecteur Apple",
                          systemImage: "arrow.triangle.2.circlepath")
                }
            }
            if let url = controller.streamURL {
                Section("Ouvrir avec") {
                    ForEach(ExternalPlayer.allCases) { player in
                        Button(player.name) {
                            controller.stop()
                            player.open(url)
                            onClose()
                        }
                    }
                }
            }
        } label: {
            PlayerChromeIcon(systemImage: controller.sleep == .off ? "ellipsis" : "moon.zzz.fill")
        }
        .accessibilityLabel("Options de lecture")
    }

    @ViewBuilder
    private func check(_ title: String, _ selected: Bool) -> some View {
        if selected { Label(title, systemImage: "checkmark") } else { Text(title) }
    }
}

/// Built-in VLC engine with NOVA's own controls (MKV, AVI, raw TS…).
private struct VLCPlayerLayer: View {
    @ObservedObject var controller: PlayerController
    let onClose: () -> Void
    @State private var controlsVisible = true
    @State private var hideTask: Task<Void, Never>?
    @State private var scrubbing: Double?
    @State private var feedback: String?

    var body: some View {
        ZStack {
            VLCVideoView(controller: controller)
                .ignoresSafeArea()
                .allowsHitTesting(false)
            GeometryReader { geo in
                Color.clear
                    .contentShape(Rectangle())
                    .onTapGesture(count: 2) { location in
                        guard controller.gesturesEnabled, !controller.isLive else { return }
                        let forward = location.x > geo.size.width / 2
                        controller.skip(forward ? 10 : -10)
                        flash(forward ? "+10 s" : "−10 s")
                    }
                    .onTapGesture { toggleControls() }
            }
            .ignoresSafeArea()
            if controller.vlcBuffering && controller.error == nil {
                ProgressView().tint(.white).scaleEffect(1.5)
            }
            if let feedback {
                Text(feedback).font(.title2.bold()).foregroundStyle(.white)
                    .padding(14).background(.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 12))
            }
            if controlsVisible {
                controls.transition(.opacity)
            }
        }
        .onAppear { scheduleHide() }
    }

    private var controls: some View {
        ZStack {
            LinearGradient(colors: [.black.opacity(0.65), .clear, .clear, .black.opacity(0.75)], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
                .allowsHitTesting(false)
            VStack {
                PlayerTopBar(controller: controller, onClose: onClose)
                Spacer()
                HStack(spacing: 44) {
                    if !controller.isLive {
                        transport("gobackward.10", "Reculer de 10 secondes") { controller.skip(-10) }
                    }
                    transport(controller.vlcPlaying ? "pause.fill" : "play.fill", controller.vlcPlaying ? "Pause" : "Lecture", size: 34) {
                        controller.togglePlay()
                    }
                    if !controller.isLive {
                        transport("goforward.10", "Avancer de 10 secondes") { controller.skip(10) }
                    }
                }
                Spacer()
                if !controller.isLive && controller.vlcDuration > 0 {
                    let position = scrubbing ?? (controller.vlcTime / controller.vlcDuration)
                    VStack(spacing: 4) {
                        Slider(value: Binding(get: { position }, set: { scrubbing = $0; scheduleHide() }), in: 0...1) { editing in
                            if !editing, let value = scrubbing {
                                controller.seek(toFraction: value)
                                scrubbing = nil
                            }
                        }
                        .tint(.white)
                        HStack {
                            Text((position * controller.vlcDuration).clock)
                            Spacer()
                            Text("−" + ((1 - position) * controller.vlcDuration).clock)
                        }
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.white.opacity(0.85))
                    }
                    .padding(.horizontal, 24)
                    .padding(.bottom, 16)
                } else if controller.isLive {
                    HStack {
                        Label("EN DIRECT", systemImage: "dot.radiowaves.left.and.right").font(.caption.bold()).foregroundStyle(.red)
                        Spacer()
                    }
                    .padding(.horizontal, 24).padding(.bottom, 20)
                }
            }
        }
    }

    private func transport(_ icon: String, _ label: String, size: CGFloat = 26, action: @escaping () -> Void) -> some View {
        Button {
            action()
            scheduleHide()
        } label: {
            Image(systemName: icon).font(.system(size: size, weight: .semibold)).foregroundStyle(.white)
                .frame(width: size * 2.2, height: size * 2.2)
                .background(.black.opacity(0.35), in: Circle())
        }
        .accessibilityLabel(label)
    }

    private func toggleControls() {
        withAnimation(.easeInOut(duration: 0.2)) { controlsVisible.toggle() }
        if controlsVisible { scheduleHide() }
    }

    private func scheduleHide() {
        hideTask?.cancel()
        hideTask = Task {
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            guard !Task.isCancelled, controller.vlcPlaying, scrubbing == nil else { return }
            withAnimation(.easeInOut(duration: 0.3)) { controlsVisible = false }
        }
    }

    private func flash(_ text: String) {
        feedback = text
        Task {
            try? await Task.sleep(nanoseconds: 700_000_000)
            if feedback == text { feedback = nil }
        }
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

/// Player panels keep a dark, readable look whatever the app palette.
private struct PlayerPanelButton: View {
    let title: String
    var icon: String? = nil
    var prominent = false
    let action: () -> Void
    @Environment(\.novaStyle) private var style

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let icon { Image(systemName: icon) }
                Text(title)
            }
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(prominent ? Color.black : Color.white)
            .padding(.horizontal, 16).padding(.vertical, 11)
            .background(prominent ? Color.white : Color.white.opacity(0.14), in: RoundedRectangle(cornerRadius: style.radius))
            .overlay(RoundedRectangle(cornerRadius: style.radius).stroke(Color.white.opacity(prominent ? 0 : 0.35)))
        }
        .buttonStyle(.plain)
    }
}

private struct PlayerErrorView: View {
    let message: String
    @ObservedObject var controller: PlayerController
    let onClose: () -> Void
    @Environment(\.novaStyle) private var style

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill").font(.largeTitle).foregroundStyle(.yellow)
            Text(message).multilineTextAlignment(.center).foregroundStyle(.white)
            HStack {
                PlayerPanelButton(title: "Réessayer", icon: "arrow.clockwise", prominent: true) { controller.load(controller.index, resume: true) }
                PlayerPanelButton(title: controller.engine == .apple ? "Moteur VLC intégré" : "Lecteur Apple", icon: "arrow.triangle.2.circlepath") {
                    controller.switchEngine()
                }
            }
            if let url = controller.streamURL {
                Text("Ou ouvrir dans une application :").font(.footnote).foregroundStyle(.white.opacity(0.7))
                HStack {
                    ForEach(ExternalPlayer.allCases) { player in
                        PlayerPanelButton(title: player.name) {
                            controller.stop()
                            player.open(url)
                            onClose()
                        }
                    }
                }
            }
            PlayerPanelButton(title: "Fermer", action: onClose)
        }
        .padding(24)
        .background(.black.opacity(0.88), in: RoundedRectangle(cornerRadius: style.radius))
        .padding()
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
                        PlayerPanelButton(title: "Lire", icon: "play.fill", prominent: true, action: onPlay)
                        PlayerPanelButton(title: "Annuler", action: onCancel)
                    }
                }
                .padding(16)
                .frame(maxWidth: 320, alignment: .leading)
                .background(.black.opacity(0.85), in: RoundedRectangle(cornerRadius: style.radius))
            }
            .padding(24)
            .padding(.bottom, 60)
        }
    }
}
