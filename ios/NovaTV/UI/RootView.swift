import SwiftData
import SwiftUI

enum AppTab: Hashable, CaseIterable {
    case home, live, movies, series, downloads

    var title: String {
        switch self {
        case .home: return "Accueil"
        case .live: return "TV"
        case .movies: return "Films"
        case .series: return "Séries"
        case .downloads: return "Téléchargements"
        }
    }

    var shortTitle: String { self == .downloads ? "Hors ligne" : title }

    var icon: String {
        switch self {
        case .home: return "house.fill"
        case .live: return "tv"
        case .movies: return "film"
        case .series: return "rectangle.stack"
        case .downloads: return "arrow.down.circle"
        }
    }
}

/// Landscape navigation, like the side menu of the TV interfaces on Android.
struct SideRail: View {
    @Binding var tab: AppTab
    @Environment(\.palette) private var palette
    @Environment(\.novaStyle) private var style

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 6) {
                NovaMark()
                    .frame(width: 52, height: 52)
                    .padding(.vertical, 4)
                    .accessibilityHidden(true)
                ForEach(AppTab.allCases, id: \.self) { item in
                    let selected = item == tab
                    Button { tab = item } label: {
                        VStack(spacing: 4) {
                            Image(systemName: item.icon).font(.system(size: 19, weight: .semibold))
                            Text(item.shortTitle).font(.caption2.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.8)
                        }
                        .foregroundStyle(selected ? palette.onAccent : palette.textSecondary)
                        .frame(width: 72, height: 54)
                        .background(selected ? palette.accent : Color.clear, in: RoundedRectangle(cornerRadius: min(style.radius, 14)))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(item.title)
                    .accessibilityAddTraits(selected ? .isSelected : [])
                }
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 6)
        }
        .frame(width: 84)
        .background(palette.background2.ignoresSafeArea())
        .overlay(alignment: .trailing) { Rectangle().fill(palette.border).frame(width: 1).ignoresSafeArea() }
    }
}

struct RootView: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var playback: PlaybackCoordinator
    @EnvironmentObject private var epg: EPGStore
    @EnvironmentObject private var sync: SyncCenter
    @EnvironmentObject private var downloads: DownloadCenter
    @Environment(\.modelContext) private var context
    @Environment(\.palette) private var palette
    @Environment(\.isWideLayout) private var wide
    @Query(sort: \Profile.createdAt) private var profiles: [Profile]
    @State private var tab = AppTab.home
    @State private var showAddProfile = false

    private var profile: Profile? {
        profiles.first { $0.id == settings.activeProfileID } ?? profiles.first
    }

    var body: some View {
        Group {
            if let profile {
                HStack(spacing: 0) {
                    if wide { SideRail(tab: $tab) }
                    tabs(profile)
                }
                .id(profile.id)
                .onAppear { prepare(profile) }
                .onChange(of: profile.id) { _, _ in prepare(profile) }
            } else {
                WelcomeView { showAddProfile = true }
            }
        }
        .background(palette.background.ignoresSafeArea())
        .sheet(isPresented: $showAddProfile) {
            NavigationStack { ProfileEditView(profile: nil) }
        }
        .fullScreenCover(item: $playback.request) { request in
            if let owner = context.profile(request.profileID) {
                PlayerScreen(request: request, profile: owner, context: context, settings: settings, downloads: downloads)
                    .environmentObject(settings)
                    .environmentObject(epg)
                    .environmentObject(downloads)
                    .environmentObject(playback)
                    .environment(\.novaStyle, settings.style)
                    .environment(\.palette, palette)
            }
        }
        .onAppear {
            PictureInPicture.shared.onDismissPlayer = { [playback] in playback.request = nil }
            PictureInPicture.shared.onRestorePlayer = { [playback] request in
                if playback.request?.id != request.id { playback.request = request }
            }
        }
        .alert("Lecture", isPresented: Binding(get: { playback.message != nil }, set: { if !$0 { playback.message = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(playback.message ?? "")
        }
    }

    /// Tabs keep their navigation state; in landscape the bottom bar is replaced by the side rail.
    private func tabs(_ profile: Profile) -> some View {
        let bar: Visibility = wide ? .hidden : .visible
        return TabView(selection: $tab) {
            NavigationStack { HomeView(profile: profile, openTab: { tab = $0 }) }
                .toolbar(bar, for: .tabBar)
                .tabItem { Label(AppTab.home.title, systemImage: AppTab.home.icon) }.tag(AppTab.home)
            NavigationStack { LiveView(profile: profile) }
                .toolbar(bar, for: .tabBar)
                .tabItem { Label(AppTab.live.title, systemImage: AppTab.live.icon) }.tag(AppTab.live)
            NavigationStack { CatalogView(profile: profile, kind: .movie) }
                .toolbar(bar, for: .tabBar)
                .tabItem { Label(AppTab.movies.title, systemImage: AppTab.movies.icon) }.tag(AppTab.movies)
            NavigationStack { CatalogView(profile: profile, kind: .series) }
                .toolbar(bar, for: .tabBar)
                .tabItem { Label(AppTab.series.title, systemImage: AppTab.series.icon) }.tag(AppTab.series)
            NavigationStack { DownloadsView(profile: profile) }
                .toolbar(bar, for: .tabBar)
                .tabItem { Label(AppTab.downloads.title, systemImage: AppTab.downloads.icon) }.tag(AppTab.downloads)
        }
    }

    private func prepare(_ profile: Profile) {
        if settings.activeProfileID != profile.id { settings.activeProfileID = profile.id }
        epg.load(profile)
        if profile.lastSyncAt == nil && !sync.isRunning(profile.id) { sync.sync(profile) }
        guard settings.autoRefresh, let synced = profile.lastSyncAt else { return }
        let stale = Date().addingTimeInterval(-12 * 3600)
        // Like Android's 12 h background refresh: catalogue (which then reloads the guide) or guide alone.
        if synced < stale, !sync.isRunning(profile.id) {
            sync.sync(profile)
        } else if (profile.epgSyncedAt ?? .distantPast) < stale, !epg.loading {
            Task { await epg.refresh(profile) }
        }
    }
}

struct WelcomeView: View {
    let onAdd: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            NovaLogo(size: 36)
            Text("Bienvenue sur NOVA").font(.largeTitle.bold()).foregroundStyle(palette.text)
            Text("Ajoutez votre playlist M3U ou votre compte Xtream Codes pour retrouver vos chaînes, films et séries.")
                .multilineTextAlignment(.center)
                .foregroundStyle(palette.textSecondary)
                .padding(.horizontal, 32)
            Button(action: onAdd) {
                Label("Ajouter un profil", systemImage: "plus").frame(maxWidth: 280).padding(.vertical, 6)
            }
            .buttonStyle(.novaPrimary)
            .controlSize(.large)
            Spacer()
            Text("NOVA ne fournit aucun contenu : utilisez uniquement des sources auxquelles vous avez droit.")
                .font(.footnote)
                .multilineTextAlignment(.center)
                .foregroundStyle(palette.textSecondary)
                .padding()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.background.ignoresSafeArea())
    }
}

/// Same drawing as Android's `nova_mark` vector (108 × 108 viewport): blue tile with a white play
/// triangle and a cyan four-point star.
struct NovaMark: View {
    var body: some View {
        Canvas { context, size in
            let s = min(size.width, size.height) / 108
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * s, y: y * s) }
            var tile = Path()
            tile.move(to: p(30, 30))
            tile.addLine(to: p(66, 30))
            tile.addQuadCurve(to: p(78, 42), control: p(78, 30))
            tile.addLine(to: p(78, 66))
            tile.addQuadCurve(to: p(66, 78), control: p(78, 78))
            tile.addLine(to: p(30, 78))
            tile.closeSubpath()
            context.fill(tile, with: .color(Color(hex: 0x258BFF)))
            var play = Path()
            play.move(to: p(43, 39))
            play.addLine(to: p(43, 69))
            play.addLine(to: p(67, 54))
            play.closeSubpath()
            context.fill(play, with: .color(.white))
            var star = Path()
            let points: [(CGFloat, CGFloat)] = [(76, 22), (79, 30), (87, 33), (79, 36), (76, 44), (73, 36), (65, 33), (73, 30)]
            star.move(to: p(points[0].0, points[0].1))
            points.dropFirst().forEach { star.addLine(to: p($0.0, $0.1)) }
            star.closeSubpath()
            context.fill(star, with: .color(Color(hex: 0x43D5FF)))
        }
        .aspectRatio(1, contentMode: .fit)
    }
}

/// Mark + "NOVA" like Android's NovaLogo (48 dp mark, 24 sp bold text, 3 sp letter spacing).
struct NovaLogo: View {
    var size: CGFloat = 40
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 0) {
            NovaMark().frame(width: size * 2, height: size * 2)
            Text("NOVA").font(.system(size: size, weight: .bold)).tracking(size / 8).foregroundStyle(palette.text)
                .lineLimit(1).fixedSize()
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("NOVA")
    }
}
