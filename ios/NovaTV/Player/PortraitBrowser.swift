import SwiftData
import SwiftUI

/// Portrait player: navigation shown under the video, following where playback was started —
/// groups → channels, groups → films, groups → series → episodes.
struct PortraitBrowser: View {
    @ObservedObject var controller: PlayerController
    let profile: Profile
    let kind: ContentKind
    @Environment(\.modelContext) private var context
    @Environment(\.palette) private var palette
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var epg: EPGStore
    @Query private var categories: [ContentCategory]
    @State private var group: String?
    @State private var showGroups = false
    /// Series whose episodes are listed (the playing series at first).
    @State private var series: (ref: String, name: String, episodes: [PlayableItem])?
    @State private var loadingSeries: String?
    @State private var message: String?

    init(controller: PlayerController, profile: Profile, kind: ContentKind) {
        self.controller = controller
        self.profile = profile
        self.kind = kind
        let id = profile.id
        let raw = kind.rawValue
        _categories = Query(filter: #Predicate<ContentCategory> { $0.profileID == id && $0.kindRaw == raw }, sort: \ContentCategory.order)
    }

    private var visible: [ContentCategory] { categories.filter { !settings.isLocked(profile.id, kind, $0.name) } }
    private var entries: [GroupEntry] { visible.map { GroupEntry(key: $0.name, title: $0.name, count: $0.count) } }
    private var currentGroup: String? { group ?? visible.first?.name }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14, pinnedViews: []) {
                nowPlaying
                if kind == .series { episodesSection }
                groupsHeader
                if let message {
                    Text(message).font(.footnote).foregroundStyle(palette.error).padding(.horizontal)
                }
                if let currentGroup {
                    BrowserGroupItems(profile: profile, kind: kind, group: currentGroup,
                                      currentRef: kind == .series ? (series?.ref ?? controller.current.parentRef ?? "") : controller.current.ref,
                                      loadingRef: loadingSeries, onPick: pick)
                        .id(currentGroup)
                } else {
                    Text("Aucun groupe disponible.").font(.subheadline).foregroundStyle(palette.textSecondary).padding(.horizontal)
                }
            }
            .padding(.vertical, 12)
        }
        .background(palette.background)
        .sheet(isPresented: $showGroups) {
            GroupPickerSheet(title: Self.groupsTitle(kind), entries: entries, selected: currentGroup) { group = $0 }
        }
        .onAppear(perform: locateCurrent)
    }

    // MARK: Sections

    private var nowPlaying: some View {
        let item = controller.current
        return VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                if item.kind == .live {
                    Text("EN DIRECT").font(.caption2.bold()).foregroundStyle(.white)
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Color.red, in: RoundedRectangle(cornerRadius: 4))
                }
                Text(item.title).font(.title3.bold()).foregroundStyle(palette.text).lineLimit(2)
            }
            if let subtitle = item.subtitle {
                Text(subtitle).font(.subheadline).foregroundStyle(palette.textSecondary).lineLimit(2)
            }
            if item.kind == .live, let channel = context.content(profile.id, ref: item.ref), let now = epg.nowNext(for: channel).now {
                Text("\(now.start.formatted(date: .omitted, time: .shortened)) · \(now.title)")
                    .font(.footnote).foregroundStyle(palette.textSecondary).lineLimit(1)
            }
        }
        .padding(.horizontal)
    }

    @ViewBuilder
    private var episodesSection: some View {
        let list = series?.episodes ?? (controller.current.kind == .live ? [] : controller.items)
        if !list.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text(series.map { "Épisodes · \($0.name)" } ?? "Épisodes")
                    .font(.headline).foregroundStyle(palette.text).padding(.horizontal)
                ScrollViewReader { proxy in
                    ScrollView(.horizontal, showsIndicators: false) {
                        LazyHStack(spacing: 10) {
                            ForEach(Array(list.enumerated()), id: \.element.ref) { index, episode in
                                EpisodeChip(episode: episode, playing: episode.ref == controller.current.ref) {
                                    controller.replaceQueue(list, start: index)
                                }
                                .id(episode.ref)
                            }
                        }
                        .padding(.horizontal)
                    }
                    .onAppear { proxy.scrollTo(controller.current.ref, anchor: .center) }
                }
            }
        }
    }

    private var groupsHeader: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(Self.groupsTitle(kind)).font(.headline).foregroundStyle(palette.text).padding(.horizontal)
            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        NovaChip(title: "Groupes", count: entries.count, icon: "magnifyingglass", selected: false) { showGroups = true }
                            .accessibilityLabel("Rechercher un groupe")
                        ForEach(entries) { entry in
                            NovaChip(title: entry.title, count: entry.count, selected: currentGroup == entry.key) { group = entry.key }
                                .id(entry.key)
                        }
                    }
                    .padding(.horizontal)
                }
                .onChange(of: group) { _, key in
                    guard let key else { return }
                    withAnimation { proxy.scrollTo(key, anchor: .center) }
                }
                .onAppear { if let currentGroup { proxy.scrollTo(currentGroup, anchor: .center) } }
            }
        }
    }

    // MARK: Actions

    private static func groupsTitle(_ kind: ContentKind) -> String {
        switch kind {
        case .live: return "Chaînes"
        case .movie: return "Films"
        default: return "Séries"
        }
    }

    /// Opens on the group of what is playing.
    private func locateCurrent() {
        guard group == nil else { return }
        let item = controller.current
        let content = context.content(profile.id, ref: item.ref)
            ?? item.parentRef.flatMap { context.content(profile.id, ref: $0) }
        if let name = content?.group, visible.contains(where: { $0.name == name }) { group = name }
    }

    private func pick(_ list: [Content], _ index: Int) {
        message = nil
        let item = list[index]
        switch kind {
        case .live:
            controller.replaceQueue(list.map(\.playable), start: index)
        case .movie:
            controller.replaceQueue([item.playable], start: 0)
        default:
            if item.isXtreamSeries { Task { await openSeries(item) } } else { controller.replaceQueue([item.playable], start: 0) }
        }
    }

    /// Lists the episodes of another series; the user then chooses one.
    private func openSeries(_ item: Content) async {
        loadingSeries = item.ref
        defer { loadingSeries = nil }
        do {
            let id = String(item.ref.dropFirst(xtreamSeriesPrefix.count))
            let info = try await XtreamClient(credentials: .init(profile: profile)).seriesInfo(id)
            let cover = info.cover ?? item.logo
            let episodes = info.episodes.map {
                PlayableItem(ref: $0.ref, title: item.name, kind: .episode, logo: cover, subtitle: $0.label, parentRef: item.ref)
            }
            guard !episodes.isEmpty else {
                message = "Aucun épisode disponible pour « \(item.name) »."
                return
            }
            series = (item.ref, item.name, episodes)
        } catch {
            message = error.userMessage
        }
    }
}

private struct EpisodeChip: View {
    let episode: PlayableItem
    let playing: Bool
    let action: () -> Void
    @Environment(\.palette) private var palette
    @Environment(\.novaStyle) private var style

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 4) {
                    if playing { Image(systemName: "play.fill").font(.caption2) }
                    Text(playing ? "En lecture" : "Épisode").font(.caption2.weight(.semibold))
                }
                .foregroundStyle(playing ? palette.onAccent : palette.textSecondary)
                Text(episode.subtitle ?? episode.title).font(.footnote.weight(.semibold)).lineLimit(2)
                    .multilineTextAlignment(.leading)
                    .foregroundStyle(playing ? palette.onAccent : palette.text)
            }
            .padding(10)
            .frame(width: 150, height: 70, alignment: .topLeading)
            .background(playing ? palette.accent : palette.card, in: RoundedRectangle(cornerRadius: min(style.radius, 12)))
            .overlay(RoundedRectangle(cornerRadius: min(style.radius, 12)).stroke(playing ? Color.clear : palette.border))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(playing ? .isSelected : [])
    }
}

/// Contents of one group, read straight from the catalogue.
private struct BrowserGroupItems: View {
    let profile: Profile
    let kind: ContentKind
    let group: String
    let currentRef: String
    let loadingRef: String?
    let onPick: ([Content], Int) -> Void
    @EnvironmentObject private var settings: AppSettings
    @Environment(\.palette) private var palette
    @Query private var items: [Content]

    init(profile: Profile, kind: ContentKind, group: String, currentRef: String, loadingRef: String?, onPick: @escaping ([Content], Int) -> Void) {
        self.profile = profile
        self.kind = kind
        self.group = group
        self.currentRef = currentRef
        self.loadingRef = loadingRef
        self.onPick = onPick
        let id = profile.id
        let raw = kind.rawValue
        let episode = ContentKind.episode.rawValue
        if kind == .series {
            _items = Query(filter: #Predicate<Content> {
                $0.profileID == id && $0.group == group && ($0.kindRaw == raw || $0.kindRaw == episode)
            }, sort: \Content.sortIndex)
        } else {
            _items = Query(filter: #Predicate<Content> { $0.profileID == id && $0.group == group && $0.kindRaw == raw }, sort: \Content.sortIndex)
        }
    }

    var body: some View {
        let list = items.filter(settings.isVisible)
        LazyVStack(spacing: 6) {
            if list.isEmpty {
                Text("Ce groupe est vide.").font(.subheadline).foregroundStyle(palette.textSecondary)
            }
            ForEach(Array(list.enumerated()), id: \.element.key) { index, item in
                Button { onPick(list, index) } label: { row(item, index: index) }
                    .buttonStyle(.plain)
            }
        }
        .padding(.horizontal)
    }

    @ViewBuilder
    private func row(_ item: Content, index: Int) -> some View {
        let playing = item.ref == currentRef
        HStack(spacing: 12) {
            if kind == .live {
                ChannelRow(channel: item)
            } else {
                PosterImage(url: item.logo, title: item.name, aspect: 2 / 3).frame(width: 46)
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.name).font(.subheadline.weight(.semibold)).foregroundStyle(palette.text).lineLimit(2)
                    if let year = item.year { Text(year).font(.caption).foregroundStyle(palette.textSecondary) }
                }
                Spacer(minLength: 0)
            }
            if loadingRef == item.ref {
                ProgressView()
            } else if playing {
                Image(systemName: "speaker.wave.2.fill").foregroundStyle(palette.accent).accessibilityLabel("En lecture")
            } else if kind == .series && item.isXtreamSeries {
                Image(systemName: "chevron.forward").font(.caption).foregroundStyle(palette.textSecondary)
            }
        }
        .padding(8)
        .background(playing ? palette.accent.opacity(0.18) : palette.card.opacity(0.6), in: RoundedRectangle(cornerRadius: 10))
        .contentShape(Rectangle())
    }
}
