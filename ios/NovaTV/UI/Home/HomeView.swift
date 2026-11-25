import SwiftData
import SwiftUI

enum Route: Hashable {
    case movie(String)
    case series(String)
    case category(ContentKind, String)
    case search
    case settings
    case downloads
}

extension View {
    /// Destinations shared by every tab's navigation stack.
    func novaDestinations(profile: Profile) -> some View {
        navigationDestination(for: Route.self) { route in
            switch route {
            case .movie(let ref): MovieDetailView(profile: profile, ref: ref)
            case .series(let ref): SeriesDetailView(profile: profile, ref: ref)
            case .category(let kind, let group): CategoryGridView(profile: profile, kind: kind, group: group)
            case .search: SearchView(profile: profile)
            case .settings: SettingsView()
            case .downloads: DownloadsView(profile: profile)
            }
        }
    }

    func novaToolbar() -> some View {
        toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                NavigationLink(value: Route.search) { Image(systemName: "magnifyingglass") }.accessibilityLabel("Rechercher")
                NavigationLink(value: Route.settings) { Image(systemName: "gearshape") }.accessibilityLabel("Réglages")
            }
        }
    }
}

/// Route opened when a catalogue item is selected.
func route(for content: Content) -> Route? {
    switch content.kind {
    case .live: return nil
    case .series where content.isXtreamSeries: return .series(content.ref)
    default: return .movie(content.ref)
    }
}

struct HomeView: View {
    let profile: Profile
    let openTab: (AppTab) -> Void
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var sync: SyncCenter
    @EnvironmentObject private var playback: PlaybackCoordinator
    @EnvironmentObject private var downloads: DownloadCenter
    @Environment(\.palette) private var palette
    @ObservedObject private var network = NetworkStatus.shared
    @Query private var movies: [Content]
    @Query private var series: [Content]
    @Query private var history: [HistoryEntry]
    @Query private var favorites: [Favorite]

    init(profile: Profile, openTab: @escaping (AppTab) -> Void) {
        self.profile = profile
        self.openTab = openTab
        let id = profile.id
        let movie = ContentKind.movie.rawValue
        let serie = ContentKind.series.rawValue
        var movies = FetchDescriptor<Content>(predicate: #Predicate<Content> { $0.profileID == id && $0.kindRaw == movie },
                                              sortBy: [SortDescriptor(\.addedAt, order: .reverse), SortDescriptor(\.sortIndex, order: .reverse)])
        movies.fetchLimit = 40
        var shows = FetchDescriptor<Content>(predicate: #Predicate<Content> { $0.profileID == id && $0.kindRaw == serie },
                                             sortBy: [SortDescriptor(\.addedAt, order: .reverse), SortDescriptor(\.sortIndex, order: .reverse)])
        shows.fetchLimit = 40
        var recent = FetchDescriptor<HistoryEntry>(predicate: #Predicate<HistoryEntry> { $0.profileID == id },
                                                   sortBy: [SortDescriptor(\.updatedAt, order: .reverse)])
        recent.fetchLimit = 60
        _movies = Query(movies)
        _series = Query(shows)
        _history = Query(recent)
        _favorites = Query(filter: #Predicate<Favorite> { $0.profileID == id }, sort: \Favorite.addedAt, order: .reverse)
    }

    private var visibleMovies: [Content] { movies.filter(settings.isVisible) }
    private var visibleSeries: [Content] { series.filter(settings.isVisible) }
    private var resume: [HistoryEntry] { history.filter { $0.kind != .live && $0.isResumable } }
    private var channels: [HistoryEntry] { Array(history.filter { $0.kind == .live }.prefix(15)) }

    /// Films and series alternated, as in Android's featured banner.
    private static func featured(_ movies: [Content], _ series: [Content]) -> [Content] {
        let m = movies.filter { $0.logo != nil }.prefix(5)
        let s = series.filter { $0.logo != nil }.prefix(4)
        var result: [Content] = []
        for i in 0..<max(m.count, s.count) {
            if i < m.count { result.append(m[m.startIndex + i]) }
            if i < s.count { result.append(s[s.startIndex + i]) }
        }
        return Array(result.prefix(8))
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 26) {
                NovaLogo(size: 22)
                    .padding(.horizontal)
                    .padding(.bottom, -12)
                if !network.online {
                    OfflineBanner { openTab(.downloads) }
                }
                if case .running(let count) = sync.states[profile.id] {
                    StatusBanner(text: "Chargement de « \(profile.name) »… \(count) contenus", isError: false)
                } else if case .failed(let message) = sync.states[profile.id] {
                    StatusBanner(text: "Échec du chargement : \(message)", isError: true)
                }
                let featured = Self.featured(visibleMovies, visibleSeries)
                if !featured.isEmpty {
                    HeroCarousel(items: featured, profile: profile)
                }
                QuickTiles(profile: profile, openTab: openTab)
                if !resume.isEmpty {
                    Rail(title: "Reprendre la lecture") {
                        ForEach(resume.prefix(20)) { entry in
                            Button {
                                playback.play([entry.playable], profile: profile)
                            } label: {
                                PosterCard(title: entry.title, image: entry.logo, subtitle: entry.subtitle ?? "Reste \((entry.duration - entry.position).clock)",
                                           progress: entry.progress, width: 200, aspect: 16 / 9)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                if !favorites.isEmpty {
                    Rail(title: "Ma liste") {
                        ForEach(favorites) { favorite in
                            FavoriteTile(profile: profile, favorite: favorite)
                        }
                    }
                }
                if !channels.isEmpty {
                    Rail(title: "Dernières chaînes regardées") {
                        ForEach(channels) { entry in
                            Button { playback.play([entry.playable], profile: profile) } label: {
                                PosterCard(title: entry.title, image: entry.logo, width: 120, aspect: 16 / 9)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                ContentRail(title: "Nouveaux films", items: Array(visibleMovies.prefix(20))) { openTab(.movies) }
                ContentRail(title: "Nouvelles séries", items: Array(visibleSeries.prefix(20))) { openTab(.series) }
            }
            .padding(.vertical)
        }
        .background(palette.background.ignoresSafeArea())
        .novaToolbar()
        .navigationBarTitleDisplayMode(.inline)
        .novaDestinations(profile: profile)
        .refreshable { sync.sync(profile) }
    }
}

struct Rail<Items: View>: View {
    let title: String
    var action: (() -> Void)? = nil
    @ViewBuilder let content: () -> Items

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: title, action: action)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 12) { content() }
                    .padding(.horizontal)
            }
        }
    }
}

struct ContentRail: View {
    let title: String
    let items: [Content]
    var onAll: (() -> Void)? = nil

    var body: some View {
        if !items.isEmpty {
            Rail(title: title, action: onAll) {
                ForEach(items) { item in
                    if let route = route(for: item) {
                        NavigationLink(value: route) {
                            PosterCard(title: item.name, image: item.logo, subtitle: item.year)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

private struct FavoriteTile: View {
    let profile: Profile
    let favorite: Favorite
    @EnvironmentObject private var playback: PlaybackCoordinator

    var body: some View {
        switch favorite.kind {
        case .live, .episode:
            Button {
                playback.play([PlayableItem(ref: favorite.ref, title: favorite.name, kind: favorite.kind, logo: favorite.logo)], profile: profile)
            } label: {
                PosterCard(title: favorite.name, image: favorite.logo, width: favorite.kind == .live ? 140 : 118, aspect: favorite.kind == .live ? 16 / 9 : 2 / 3)
            }
            .buttonStyle(.plain)
        case .series where favorite.ref.hasPrefix(xtreamSeriesPrefix):
            NavigationLink(value: Route.series(favorite.ref)) { PosterCard(title: favorite.name, image: favorite.logo) }.buttonStyle(.plain)
        default:
            NavigationLink(value: Route.movie(favorite.ref)) { PosterCard(title: favorite.name, image: favorite.logo) }.buttonStyle(.plain)
        }
    }
}

private struct QuickTiles: View {
    let profile: Profile
    let openTab: (AppTab) -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 10) {
            tile("TV en direct", "tv", .live)
            tile("Films", "film", .movies)
            tile("Séries", "rectangle.stack", .series)
        }
        .padding(.horizontal)
    }

    private func tile(_ title: String, _ icon: String, _ tab: AppTab) -> some View {
        Button { openTab(tab) } label: {
            VStack(spacing: 8) {
                Image(systemName: icon).font(.title2)
                Text(title).font(.caption.weight(.semibold))
            }
            .foregroundStyle(palette.text)
            .frame(maxWidth: .infinity, minHeight: 72)
            .novaCard(padding: 0)
        }
        .buttonStyle(.plain)
    }
}

/// Featured titles, laid out like Android's banner (image, text and buttons on the left, poster on
/// the right). A paging scroll view keeps the buttons working: links inside a paged TabView are disabled.
private struct HeroCarousel: View {
    let items: [Content]
    let profile: Profile
    @EnvironmentObject private var playback: PlaybackCoordinator
    @Environment(\.palette) private var palette
    @Environment(\.novaStyle) private var style
    @State private var selection: String?
    @State private var lastInteraction = Date()
    private let timer = Timer.publish(every: 9, on: .main, in: .common).autoconnect()

    private var currentIndex: Int { items.firstIndex { $0.key == selection } ?? 0 }

    var body: some View {
        VStack(spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 0) {
                    ForEach(items, id: \.key) { item in
                        slide(item)
                            .padding(.horizontal)
                            .containerRelativeFrame(.horizontal)
                            .id(item.key)
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollPosition(id: $selection)
            .frame(height: heroHeight)
            if items.count > 1 {
                HStack(spacing: 6) {
                    ForEach(Array(items.enumerated()), id: \.element.key) { index, _ in
                        Capsule().fill(index == currentIndex ? palette.accent : palette.border)
                            .frame(width: index == currentIndex ? 18 : 6, height: 6)
                    }
                }
                .animation(.easeInOut(duration: 0.2), value: currentIndex)
                .accessibilityHidden(true)
            }
        }
        .onChange(of: selection) { _, _ in lastInteraction = .now }
        .onReceive(timer) { _ in
            guard Date().timeIntervalSince(lastInteraction) > 8, items.count > 1 else { return }
            withAnimation { selection = items[(currentIndex + 1) % items.count].key }
        }
    }

    private var heroHeight: CGFloat { max(230, style.heroHeight) }

    private func slide(_ item: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: min(style.radius, 22))
        let meta = [item.year, item.group, item.rating.flatMap { $0 == "0" || $0.isEmpty ? nil : "★ " + $0 }].compactMap { $0 }
        // The text defines the card; the artwork stays a background so it can never push it out of view.
        return HStack(spacing: 14) {
            VStack(alignment: .leading, spacing: 7) {
                Text(item.kind == .movie ? "FILM" : "SÉRIE").font(.caption.bold()).foregroundStyle(palette.accent)
                Text(item.name).font(.title3.bold()).foregroundStyle(palette.text).lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                if !meta.isEmpty {
                    Text(meta.joined(separator: " · ")).font(.footnote).foregroundStyle(palette.textSecondary).lineLimit(1)
                }
                Spacer(minLength: 4)
                HStack(spacing: 8) {
                    watchButton(item)
                    if let route = route(for: item) {
                        NavigationLink(value: route) { Label("Détails", systemImage: "info.circle").lineLimit(1) }
                            .buttonStyle(NovaButtonStyle(prominent: false, compact: true))
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            PosterImage(url: item.logo, title: item.name).frame(width: 96)
        }
        .padding(16)
        .frame(maxWidth: .infinity)
        .frame(height: heroHeight)
        .background {
            ZStack {
                palette.background2
                Color.clear.overlay {
                    AsyncImage(url: item.logo.flatMap(URL.init(string:))) { image in
                        image.resizable().scaledToFill()
                    } placeholder: {
                        Color.clear
                    }
                }
                .clipped()
                .opacity(0.45)
                LinearGradient(colors: [palette.background, palette.background.opacity(0.6), .clear], startPoint: .leading, endPoint: .trailing)
            }
        }
        .clipShape(shape)
        .overlay(shape.stroke(palette.border, lineWidth: 1))
    }

    @ViewBuilder
    private func watchButton(_ item: Content) -> some View {
        if item.kind == .series && item.isXtreamSeries {
            NavigationLink(value: Route.series(item.ref)) { Label("Voir la série", systemImage: "play.fill") }
                .buttonStyle(NovaButtonStyle(prominent: true, compact: true))
        } else {
            Button { playback.play([item.playable], profile: profile) } label: { Label("Regarder", systemImage: "play.fill") }
                .buttonStyle(NovaButtonStyle(prominent: true, compact: true))
        }
    }
}

struct StatusBanner: View {
    let text: String
    let isError: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 10) {
            if !isError { ProgressView() }
            Text(text).font(.footnote).foregroundStyle(isError ? .red : palette.textSecondary)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .novaCard(padding: 0)
        .padding(.horizontal)
    }
}

struct OfflineBanner: View {
    let onOpen: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "wifi.slash").foregroundStyle(palette.textSecondary)
            VStack(alignment: .leading) {
                Text("Vous êtes hors ligne").font(.subheadline.bold())
                Text("Vos téléchargements restent disponibles.").font(.caption).foregroundStyle(palette.textSecondary)
            }
            Spacer()
            Button("Ouvrir", action: onOpen).buttonStyle(.novaPrimary)
        }
        .padding(12)
        .novaCard(padding: 0)
        .padding(.horizontal)
    }
}
