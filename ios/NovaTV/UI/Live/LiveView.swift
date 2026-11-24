import SwiftData
import SwiftUI

struct LiveView: View {
    let profile: Profile
    @EnvironmentObject private var settings: AppSettings
    @Environment(\.palette) private var palette
    @Query private var categories: [ContentCategory]
    @State private var selected: String?
    @State private var askPin = false
    private static let favoritesChip = "\u{2605} Favoris"

    init(profile: Profile) {
        self.profile = profile
        let id = profile.id
        let live = ContentKind.live.rawValue
        _categories = Query(filter: #Predicate<ContentCategory> { $0.profileID == id && $0.kindRaw == live }, sort: \ContentCategory.order)
    }

    private var visible: [ContentCategory] { categories.filter { !settings.isLocked(profile.id, .live, $0.name) } }
    private var hiddenCount: Int { categories.count - visible.count }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    chip(Self.favoritesChip, count: nil)
                    ForEach(visible) { category in chip(category.name, count: category.count) }
                    if hiddenCount > 0 {
                        Button { askPin = true } label: { Label("\(hiddenCount) masquée(s)", systemImage: "lock.fill") }
                            .buttonStyle(.bordered)
                    }
                }
                .padding(.horizontal)
                .padding(.vertical, 8)
            }
            if categories.isEmpty {
                EmptyStateView(systemImage: "tv", title: "Aucune chaîne", message: "Le catalogue de ce profil est vide ou en cours de chargement.")
            } else if selected == Self.favoritesChip {
                FavoriteChannels(profile: profile)
            } else if let group = selected ?? visible.first?.name {
                ChannelList(profile: profile, group: group).id(group)
            }
        }
        .background(palette.background.ignoresSafeArea())
        .navigationTitle("TV en direct")
        .navigationBarTitleDisplayMode(.inline)
        .novaToolbar()
        .novaDestinations(profile: profile)
        .sheet(isPresented: $askPin) {
            PinSheet(title: "Catégories protégées", message: "Saisissez le code PIN pour afficher les contenus protégés.") { pin in
                settings.verify(pin) ? nil : "Code incorrect."
            }
        }
    }

    private func chip(_ name: String, count: Int?) -> some View {
        let isSelected = (selected ?? visible.first?.name) == name
        return Button { selected = name } label: {
            HStack(spacing: 4) {
                Text(name).lineLimit(1)
                if let count { Text("\(count)").font(.caption2).foregroundStyle(isSelected ? palette.background : palette.textSecondary) }
            }
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .foregroundStyle(isSelected ? (palette.isLight ? .white : palette.background) : palette.text)
            .background(isSelected ? palette.accent : palette.card, in: Capsule())
            .overlay(Capsule().stroke(palette.border, lineWidth: isSelected ? 0 : 1))
        }
        .buttonStyle(.plain)
    }
}

struct ChannelList: View {
    let profile: Profile
    let group: String
    @EnvironmentObject private var playback: PlaybackCoordinator
    @EnvironmentObject private var epg: EPGStore
    @Environment(\.palette) private var palette
    @Query private var channels: [Content]
    @State private var filter = ""
    @State private var guideFor: Content?

    init(profile: Profile, group: String) {
        self.profile = profile
        self.group = group
        let id = profile.id
        let live = ContentKind.live.rawValue
        _channels = Query(filter: #Predicate<Content> { $0.profileID == id && $0.kindRaw == live && $0.group == group }, sort: \Content.sortIndex)
    }

    private var shown: [Content] {
        let query = filter.searchFolded.trimmingCharacters(in: .whitespaces)
        return query.isEmpty ? channels : channels.filter { $0.searchName.contains(query) }
    }

    var body: some View {
        List {
            ForEach(Array(shown.enumerated()), id: \.element.key) { index, channel in
                Button {
                    let list = shown
                    playback.play(list.map(\.playable), start: index, profile: profile)
                } label: {
                    ChannelRow(channel: channel)
                }
                .listRowBackground(palette.background)
                .contextMenu { ChannelMenu(profile: profile, channel: channel, onGuide: { guideFor = channel }) }
                .swipeActions(edge: .trailing) {
                    Button { guideFor = channel } label: { Label("Programme", systemImage: "list.bullet.rectangle") }.tint(palette.accent)
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .searchable(text: $filter, placement: .navigationBarDrawer(displayMode: .automatic), prompt: "Filtrer les chaînes")
        .sheet(item: $guideFor) { channel in
            ChannelGuideSheet(profile: profile, channel: channel)
        }
    }
}

struct ChannelRow: View {
    let channel: Content
    @EnvironmentObject private var epg: EPGStore
    @Environment(\.palette) private var palette

    var body: some View {
        let schedule = epg.nowNext(for: channel)
        HStack(spacing: 12) {
            AsyncImage(url: channel.logo.flatMap(URL.init(string:))) { image in
                image.resizable().scaledToFit()
            } placeholder: {
                Text(String(channel.name.prefix(2)).uppercased()).font(.caption.bold()).foregroundStyle(palette.textSecondary)
            }
            .frame(width: 64, height: 40)
            .padding(4)
            .background(palette.card, in: RoundedRectangle(cornerRadius: 8))
            VStack(alignment: .leading, spacing: 3) {
                Text(channel.name).font(.subheadline.weight(.semibold)).foregroundStyle(palette.text).lineLimit(1)
                if let now = schedule.now {
                    Text("\(now.start.formatted(date: .omitted, time: .shortened)) · \(now.title)")
                        .font(.caption).foregroundStyle(palette.textSecondary).lineLimit(1)
                    ProgressView(value: Date().timeIntervalSince(now.start), total: max(1, now.end.timeIntervalSince(now.start)))
                        .tint(palette.accent)
                }
                if let next = schedule.next {
                    Text("Ensuite \(next.start.formatted(date: .omitted, time: .shortened)) · \(next.title)")
                        .font(.caption2).foregroundStyle(palette.textSecondary).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 2)
        .contentShape(Rectangle())
    }
}

struct ChannelMenu: View {
    let profile: Profile
    let channel: Content
    let onGuide: () -> Void
    @Environment(\.modelContext) private var context
    @EnvironmentObject private var playback: PlaybackCoordinator

    var body: some View {
        let isFavorite = context.favorite(profile.id, channel.ref) != nil
        Button { context.toggleFavorite(profileID: profile.id, item: channel.playable) } label: {
            Label(isFavorite ? "Retirer des favoris" : "Ajouter aux favoris", systemImage: isFavorite ? "star.slash" : "star")
        }
        Button(action: onGuide) { Label("Programme TV", systemImage: "list.bullet.rectangle") }
        Menu("Ouvrir avec") {
            ForEach(ExternalPlayer.allCases) { player in
                Button(player.name) { playback.play([channel.playable], profile: profile, forcePlayer: PlayerChoice(rawValue: player.rawValue)) }
            }
        }
    }
}

private struct FavoriteChannels: View {
    let profile: Profile
    @EnvironmentObject private var playback: PlaybackCoordinator
    @Environment(\.palette) private var palette
    @Query private var favorites: [Favorite]

    init(profile: Profile) {
        self.profile = profile
        let id = profile.id
        let live = ContentKind.live.rawValue
        _favorites = Query(filter: #Predicate<Favorite> { $0.profileID == id && $0.kindRaw == live }, sort: \Favorite.addedAt)
    }

    var body: some View {
        if favorites.isEmpty {
            EmptyStateView(systemImage: "star", title: "Aucune chaîne favorite",
                           message: "Appuyez longuement sur une chaîne puis « Ajouter aux favoris ».")
        } else {
            List {
                ForEach(Array(favorites.enumerated()), id: \.element.key) { index, favorite in
                    Button {
                        let items = favorites.map { PlayableItem(ref: $0.ref, title: $0.name, kind: .live, logo: $0.logo) }
                        playback.play(items, start: index, profile: profile)
                    } label: {
                        HStack(spacing: 12) {
                            PosterImage(url: favorite.logo, title: favorite.name, aspect: 16 / 10).frame(width: 64)
                            Text(favorite.name).foregroundStyle(palette.text)
                        }
                    }
                    .listRowBackground(palette.background)
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
        }
    }
}

struct ChannelGuideSheet: View {
    let profile: Profile
    let channel: Content
    @EnvironmentObject private var epg: EPGStore
    @EnvironmentObject private var playback: PlaybackCoordinator
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            let schedule = epg.schedule(for: channel).filter { $0.end > Date() }
            List {
                if schedule.isEmpty {
                    Text(epg.loading ? "Chargement du guide TV…" : "Aucun programme disponible pour cette chaîne.")
                        .foregroundStyle(.secondary)
                }
                ForEach(schedule, id: \.self) { programme in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("\(programme.start.formatted(date: .omitted, time: .shortened)) – \(programme.end.formatted(date: .omitted, time: .shortened))")
                                .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                            if programme.start <= Date() { Text("EN DIRECT").font(.caption2.bold()).foregroundStyle(.red) }
                        }
                        Text(programme.title).font(.subheadline.weight(.semibold))
                        if let summary = programme.summary { Text(summary).font(.caption).foregroundStyle(.secondary).lineLimit(3) }
                    }
                }
            }
            .navigationTitle(channel.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button { dismiss(); playback.play([channel.playable], profile: profile) } label: { Label("Regarder", systemImage: "play.fill") }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
