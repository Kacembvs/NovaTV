import SwiftData
import SwiftUI

enum AppTab: Hashable { case home, live, movies, series, downloads }

struct RootView: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var playback: PlaybackCoordinator
    @EnvironmentObject private var epg: EPGStore
    @EnvironmentObject private var sync: SyncCenter
    @EnvironmentObject private var downloads: DownloadCenter
    @Environment(\.modelContext) private var context
    @Environment(\.palette) private var palette
    @Query(sort: \Profile.createdAt) private var profiles: [Profile]
    @State private var tab = AppTab.home
    @State private var showAddProfile = false

    private var profile: Profile? {
        profiles.first { $0.id == settings.activeProfileID } ?? profiles.first
    }

    var body: some View {
        Group {
            if let profile {
                TabView(selection: $tab) {
                    NavigationStack { HomeView(profile: profile, openTab: { tab = $0 }) }
                        .tabItem { Label("Accueil", systemImage: "house.fill") }.tag(AppTab.home)
                    NavigationStack { LiveView(profile: profile) }
                        .tabItem { Label("TV", systemImage: "tv") }.tag(AppTab.live)
                    NavigationStack { CatalogView(profile: profile, kind: .movie) }
                        .tabItem { Label("Films", systemImage: "film") }.tag(AppTab.movies)
                    NavigationStack { CatalogView(profile: profile, kind: .series) }
                        .tabItem { Label("Séries", systemImage: "rectangle.stack") }.tag(AppTab.series)
                    NavigationStack { DownloadsView(profile: profile) }
                        .tabItem { Label("Téléchargements", systemImage: "arrow.down.circle") }.tag(AppTab.downloads)
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
            NovaLogo(size: 84)
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

struct NovaLogo: View {
    var size: CGFloat = 40
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: size * 0.2) {
            ZStack(alignment: .topTrailing) {
                RoundedRectangle(cornerRadius: size * 0.22)
                    .fill(Color(hex: 0x258BFF))
                    .frame(width: size, height: size)
                    .overlay(Image(systemName: "play.fill").font(.system(size: size * 0.45)).foregroundStyle(.white))
                Image(systemName: "sparkle").font(.system(size: size * 0.35, weight: .bold)).foregroundStyle(Color(hex: 0x43D5FF))
                    .offset(x: size * 0.15, y: -size * 0.15)
            }
            Text("NOVA").font(.system(size: size * 0.6, weight: .heavy)).tracking(size * 0.08).foregroundStyle(palette.text)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("NOVA")
    }
}
