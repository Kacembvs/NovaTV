import SwiftData
import SwiftUI

struct MovieDetailView: View {
    let profile: Profile
    let ref: String
    @EnvironmentObject private var playback: PlaybackCoordinator
    @Environment(\.palette) private var palette
    @Query private var matches: [Content]
    @Query private var history: [HistoryEntry]
    @State private var info: XtreamClient.VodInfo?

    init(profile: Profile, ref: String) {
        self.profile = profile
        self.ref = ref
        let key = "\(profile.id.uuidString)|\(ref)"
        _matches = Query(filter: #Predicate<Content> { $0.key == key })
        _history = Query(filter: #Predicate<HistoryEntry> { $0.key == key })
    }

    var body: some View {
        if let content = matches.first {
            detail(content)
        } else {
            EmptyStateView(systemImage: "film", title: "Titre introuvable", message: "Ce titre n'est plus dans le catalogue de ce profil.")
        }
    }

    private func detail(_ content: Content) -> some View {
        let item = PlayableItem(ref: content.ref, title: content.name, kind: content.kind, logo: info?.cover ?? content.logo)
        return ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                header(content)
                actions(content, item: item)
                synopsis
                SimilarRail(profile: profile, content: content)
            }
            .padding(.bottom, 24)
        }
        .background(palette.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .task(id: content.ref) { await loadInfo(content) }
    }

    private func loadInfo(_ content: Content) async {
        guard profile.type == .xtream, content.ref.hasPrefix("movie/") else { return }
        let id = String(content.ref.dropFirst(6).split(separator: ".").first ?? "")
        info = try? await XtreamClient(credentials: .init(profile: profile)).vodInfo(id)
    }

    private func metaLine(_ content: Content) -> String {
        var parts: [String] = []
        if let date = info?.releaseDate { parts.append(String(date.prefix(4))) } else if let year = content.year { parts.append(year) }
        if let duration = info?.duration { parts.append(duration) }
        if let genre = info?.genre { parts.append(genre) }
        let rating: String? = info?.rating ?? content.rating
        if let rating, !rating.isEmpty, rating != "0" { parts.append("★ " + rating) }
        return parts.joined(separator: " · ")
    }

    private func backdropURL(_ content: Content) -> URL? {
        let raw: String? = info?.backdrop ?? info?.cover ?? content.logo
        return raw.flatMap { URL(string: $0) }
    }

    @ViewBuilder
    private func header(_ content: Content) -> some View {
        let meta = metaLine(content)
        ZStack(alignment: .bottomLeading) {
            AsyncImage(url: backdropURL(content)) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                palette.background2
            }
            .frame(height: 260)
            .frame(maxWidth: .infinity)
            .clipped()
            .overlay(LinearGradient(colors: [Color.clear, palette.background], startPoint: .center, endPoint: .bottom))
            HStack(alignment: .bottom, spacing: 14) {
                PosterImage(url: info?.cover ?? content.logo, title: content.name).frame(width: 110)
                VStack(alignment: .leading, spacing: 6) {
                    Text(content.name).font(.title2.bold()).foregroundStyle(palette.text)
                    if !meta.isEmpty { Text(meta).font(.caption).foregroundStyle(palette.textSecondary) }
                    Text(content.group).font(.caption).foregroundStyle(palette.accent2)
                }
            }
            .padding(.horizontal)
        }
    }

    @ViewBuilder
    private func actions(_ content: Content, item: PlayableItem) -> some View {
        let saved: HistoryEntry? = history.first
        let resumable = saved?.isResumable == true
        let playTitle = resumable ? "Reprendre à " + (saved?.position ?? 0).clock : "Regarder"
        VStack(spacing: 10) {
            Button {
                playback.play([item], profile: profile)
            } label: {
                Label(playTitle, systemImage: "play.fill").frame(maxWidth: .infinity)
            }
            .buttonStyle(.novaPrimary)
            .controlSize(.large)
            if resumable, let saved {
                ProgressView(value: saved.progress).tint(palette.accent)
                Button {
                    playback.play([item], profile: profile, fromStart: true)
                } label: {
                    Label("Recommencer", systemImage: "gobackward").frame(maxWidth: .infinity)
                }
                .buttonStyle(.novaSecondary)
            }
            HStack {
                FavoriteButton(profileID: profile.id, item: item)
                if content.kind != .live { DownloadButton(profile: profile, item: item) }
                Spacer()
                OpenWithMenu(profile: profile, item: item)
            }
        }
        .padding(.horizontal)
    }

    @ViewBuilder
    private var synopsis: some View {
        let plot: String? = info?.plot
        VStack(alignment: .leading, spacing: 8) {
            Text(plot ?? "Aucun synopsis fourni par la source.")
                .foregroundStyle(plot == nil ? palette.textSecondary : palette.text)
            if let director = info?.director { Text("Réalisation : " + director).font(.caption).foregroundStyle(palette.textSecondary) }
            if let cast = info?.cast { Text("Avec : " + cast).font(.caption).foregroundStyle(palette.textSecondary).lineLimit(3) }
        }
        .padding(.horizontal)
    }
}

struct OpenWithMenu: View {
    let profile: Profile
    let item: PlayableItem
    @EnvironmentObject private var playback: PlaybackCoordinator

    var body: some View {
        Menu {
            Button("Lecteur NOVA") { playback.play([item], profile: profile, forcePlayer: .nova) }
            ForEach(ExternalPlayer.allCases) { player in
                Button(player.isInstalled ? player.name : "\(player.name) (installer)") {
                    playback.play([item], profile: profile, forcePlayer: PlayerChoice(rawValue: player.rawValue))
                }
            }
        } label: {
            Image(systemName: "ellipsis.circle").font(.title2)
        }
        .accessibilityLabel("Ouvrir avec un autre lecteur")
    }
}

private struct SimilarRail: View {
    let profile: Profile
    let content: Content
    @EnvironmentObject private var settings: AppSettings
    @Query private var similar: [Content]

    init(profile: Profile, content: Content) {
        self.profile = profile
        self.content = content
        let id = profile.id
        let group = content.group
        let kind = content.kindRaw
        let ref = content.ref
        var descriptor = FetchDescriptor<Content>(predicate: #Predicate<Content> {
            $0.profileID == id && $0.group == group && $0.kindRaw == kind && $0.ref != ref
        }, sortBy: [SortDescriptor(\.sortIndex)])
        descriptor.fetchLimit = 20
        _similar = Query(descriptor)
    }

    var body: some View {
        ContentRail(title: "Titres similaires", items: similar.filter(settings.isVisible))
    }
}

struct SeriesDetailView: View {
    let profile: Profile
    let ref: String
    @EnvironmentObject private var playback: PlaybackCoordinator
    @EnvironmentObject private var downloads: DownloadCenter
    @Environment(\.palette) private var palette
    @Query private var matches: [Content]
    @Query private var progress: [HistoryEntry]
    @State private var info: XtreamClient.SeriesInfo?
    @State private var error: String?
    @State private var season: Int?
    @State private var downloadError: String?

    init(profile: Profile, ref: String) {
        self.profile = profile
        self.ref = ref
        let key = "\(profile.id.uuidString)|\(ref)"
        let id = profile.id
        _matches = Query(filter: #Predicate<Content> { $0.key == key })
        _progress = Query(filter: #Predicate<HistoryEntry> { $0.profileID == id && $0.parentRef == ref })
    }

    private func item(_ episode: XtreamClient.Episode, cover: String?) -> PlayableItem {
        PlayableItem(ref: episode.ref, title: matches.first?.name ?? info?.name ?? "Série", kind: .episode,
                     logo: cover, subtitle: episode.label, parentRef: ref)
    }

    private var metaLine: String {
        var parts: [String] = []
        if let date = info?.releaseDate { parts.append(String(date.prefix(4))) }
        if let genre = info?.genre { parts.append(genre) }
        let seasons = Set((info?.episodes ?? []).map { $0.season }).count
        if seasons > 0 { parts.append("\(seasons) saison(s)") }
        return parts.joined(separator: " · ")
    }

    @ViewBuilder
    private func header(_ content: Content?, cover: String?) -> some View {
        let title: String = content?.name ?? info?.name ?? "Série"
        let meta = metaLine
        HStack(alignment: .top, spacing: 14) {
            PosterImage(url: cover, title: title).frame(width: 120)
            VStack(alignment: .leading, spacing: 8) {
                Text(title).font(.title2.bold()).foregroundStyle(palette.text)
                if !meta.isEmpty { Text(meta).font(.caption).foregroundStyle(palette.textSecondary) }
                if let content {
                    FavoriteButton(profileID: profile.id, item: PlayableItem(ref: ref, title: content.name, kind: .series, logo: cover))
                }
            }
        }
        .padding(.horizontal)
    }

    var body: some View {
        let content: Content? = matches.first
        let cover: String? = info?.cover ?? content?.logo
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header(content, cover: cover)
                if let info {
                    if let plot = info.plot, !plot.isEmpty {
                        Text(plot).font(.callout).foregroundStyle(palette.textSecondary).lineLimit(6).padding(.horizontal)
                    }
                    episodes(info, cover: cover)
                } else if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Impossible de charger les épisodes : \(error)").foregroundStyle(.red)
                        Button("Réessayer") { Task { await load() } }.buttonStyle(.novaSecondary)
                        NavigationLink(value: Route.downloads) { Label("Voir mes téléchargements", systemImage: "arrow.down.circle") }
                    }
                    .padding(.horizontal)
                } else {
                    ProgressView().frame(maxWidth: .infinity).padding(40)
                }
            }
            .padding(.vertical)
        }
        .background(palette.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .alert("Téléchargement impossible", isPresented: Binding(get: { downloadError != nil }, set: { if !$0 { downloadError = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(downloadError ?? "") }
    }

    @ViewBuilder
    private func episodes(_ info: XtreamClient.SeriesInfo, cover: String?) -> some View {
        let seasons = Array(Set(info.episodes.map(\.season))).sorted()
        let current = season ?? seasons.first
        let list = info.episodes.filter { $0.season == current }
        let watched = Dictionary(progress.map { ($0.ref, $0) }, uniquingKeysWith: { first, _ in first })
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                if !info.episodes.isEmpty {
                    Button {
                        let lastWatched = info.episodes.lastIndex { watched[$0.ref] != nil } ?? 0
                        playback.play(info.episodes.map { item($0, cover: cover) }, start: lastWatched, profile: profile)
                    } label: {
                        Label(watched.isEmpty ? "Lecture" : "Continuer", systemImage: "play.fill")
                    }
                    .buttonStyle(.novaPrimary)
                }
                Spacer()
                if seasons.count > 1 {
                    Picker("Saison", selection: Binding(get: { current ?? 0 }, set: { season = $0 })) {
                        ForEach(seasons, id: \.self) { Text("Saison \($0)").tag($0) }
                    }
                    .pickerStyle(.menu)
                }
            }
            if !list.isEmpty {
                Button {
                    do {
                        try downloads.download(list.map { (item($0, cover: cover), $0.season, $0.number) }, profile: profile)
                    } catch {
                        downloadError = error.userMessage
                    }
                } label: {
                    Label("Télécharger la saison \(current ?? 0) (\(list.count) épisodes)", systemImage: "arrow.down.circle")
                }
                .buttonStyle(.novaSecondary)
            }
            ForEach(list) { episode in
                let entry = watched[episode.ref]
                HStack(spacing: 10) {
                    Button {
                        let all = info.episodes
                        playback.play(all.map { item($0, cover: cover) }, start: all.firstIndex(of: episode) ?? 0, profile: profile)
                    } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(episode.label).font(.subheadline.weight(.medium)).foregroundStyle(palette.text).lineLimit(2)
                                .multilineTextAlignment(.leading)
                            if let duration = episode.duration, !duration.isEmpty {
                                Text(duration).font(.caption2).foregroundStyle(palette.textSecondary)
                            }
                            if let entry, entry.duration > 0 { ProgressView(value: entry.progress).tint(palette.accent) }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .novaCard(padding: 0)
                    }
                    .buttonStyle(.plain)
                    DownloadButton(profile: profile, item: item(episode, cover: cover), season: episode.season, episode: episode.number, compact: true)
                }
            }
        }
        .padding(.horizontal)
    }

    private func load() async {
        error = nil
        do {
            let id = String(ref.dropFirst(xtreamSeriesPrefix.count))
            info = try await XtreamClient(credentials: .init(profile: profile)).seriesInfo(id)
        } catch {
            self.error = error.userMessage
        }
    }
}
