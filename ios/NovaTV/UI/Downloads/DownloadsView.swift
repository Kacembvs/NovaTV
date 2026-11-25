import SwiftData
import SwiftUI

struct SearchView: View {
    let profile: Profile
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var playback: PlaybackCoordinator
    @Environment(\.modelContext) private var context
    @Environment(\.palette) private var palette
    @State private var query = ""
    @State private var results: [Content] = []
    @State private var searching = false

    var body: some View {
        List {
            if query.trimmingCharacters(in: .whitespaces).count < 2 {
                Text("Saisissez au moins 2 caractères pour chercher une chaîne, un film ou une série.")
                    .foregroundStyle(palette.textSecondary)
                    .listRowBackground(palette.background)
            } else if results.isEmpty && !searching {
                Text("Aucun résultat pour « \(query) ».").foregroundStyle(palette.textSecondary).listRowBackground(palette.background)
            }
            section("TV en direct", results.filter { $0.kind == .live })
            section("Films", results.filter { $0.kind == .movie })
            section("Séries", results.filter { $0.kind == .series || $0.kind == .episode })
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(palette.background.ignoresSafeArea())
        .navigationTitle("Recherche")
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Chaînes, films, séries")
        .task(id: query) {
            // Debounce: wait for the user to stop typing.
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            search()
        }
    }

    @ViewBuilder
    private func section(_ title: String, _ items: [Content]) -> some View {
        if !items.isEmpty {
            Section(title) {
                ForEach(items) { item in
                    if let route = route(for: item) {
                        NavigationLink(value: route) { row(item) }
                    } else {
                        Button { playback.play([item.playable], profile: profile) } label: { row(item) }
                    }
                }
            }
            .listRowBackground(palette.background)
        }
    }

    private func row(_ item: Content) -> some View {
        HStack(spacing: 12) {
            PosterImage(url: item.logo, title: item.name, aspect: item.kind == .live ? 16 / 10 : 2 / 3)
                .frame(width: item.kind == .live ? 64 : 44)
            VStack(alignment: .leading, spacing: 2) {
                Text(item.name).foregroundStyle(palette.text).lineLimit(2)
                Text(item.group).font(.caption).foregroundStyle(palette.textSecondary).lineLimit(1)
            }
        }
    }

    private func search() {
        let text = query.searchFolded.trimmingCharacters(in: .whitespaces)
        guard text.count >= 2 else { results = []; return }
        searching = true
        defer { searching = false }
        let id = profile.id
        let raw = query.trimmingCharacters(in: .whitespaces)
        var descriptor = FetchDescriptor<Content>(predicate: #Predicate<Content> { $0.profileID == id && $0.name.localizedStandardContains(raw) },
                                                  sortBy: [SortDescriptor(\.sortIndex)])
        descriptor.fetchLimit = 150
        results = ((try? context.fetch(descriptor)) ?? []).filter(settings.isVisible)
    }
}

struct DownloadsView: View {
    let profile: Profile
    @EnvironmentObject private var downloads: DownloadCenter
    @EnvironmentObject private var playback: PlaybackCoordinator
    @EnvironmentObject private var settings: AppSettings
    @Environment(\.palette) private var palette
    @Query private var records: [DownloadRecord]
    @State private var confirmClear = false

    init(profile: Profile) {
        self.profile = profile
        let id = profile.id
        _records = Query(filter: #Predicate<DownloadRecord> { $0.profileID == id }, sort: \DownloadRecord.createdAt, order: .reverse)
    }

    /// Series folders and movies, like the streaming apps.
    private var groups: [(key: String, title: String, logo: String?, items: [DownloadRecord])] {
        var result: [(key: String, title: String, logo: String?, items: [DownloadRecord])] = []
        var seen: [String: Int] = [:]
        for record in records {
            let key = record.parentRef ?? record.key
            if let index = seen[key] {
                result[index].items.append(record)
            } else {
                seen[key] = result.count
                result.append((key, record.title, record.logo, [record]))
            }
        }
        return result.map { group in
            (group.key, group.title, group.logo, group.items.sorted { ($0.season ?? 0, $0.episode ?? 0) < ($1.season ?? 0, $1.episode ?? 0) })
        }
    }

    var body: some View {
        List {
            Section {
                let used = downloads.usedBytes
                let free = downloads.freeBytes
                VStack(alignment: .leading, spacing: 6) {
                    if free > 0 { ProgressView(value: Double(used), total: Double(max(1, used + free))).tint(palette.accent) }
                    Text("NOVA : \(used.formattedBytes)" + (free > 0 ? " · Espace libre : \(free.formattedBytes)" : ""))
                        .font(.caption).foregroundStyle(palette.textSecondary)
                }
                Toggle("Téléchargements intelligents", isOn: $settings.smartDownloads)
                Toggle("Wi-Fi uniquement", isOn: $settings.downloadWifiOnly)
                if downloads.isWaitingForWifi && records.contains(where: { $0.state == .queued }) {
                    Label("En attente d'une connexion Wi-Fi", systemImage: "wifi.exclamationmark").font(.caption).foregroundStyle(.orange)
                }
            } footer: {
                Text("Téléchargements intelligents : un épisode regardé jusqu'au bout est supprimé et le suivant est téléchargé.")
            }
            .listRowBackground(palette.card)

            if records.isEmpty {
                Section {
                    EmptyStateView(systemImage: "arrow.down.circle", title: "Aucun téléchargement",
                                   message: "Touchez « Télécharger » sur un film ou un épisode pour le regarder ensuite sans connexion.")
                }
                .listRowBackground(palette.background)
            }
            ForEach(groups, id: \.key) { group in
                if group.items.first?.kind == .episode {
                    Section {
                        DisclosureGroup {
                            ForEach(group.items) { record in row(record, group: group.items, showPoster: false) }
                        } label: {
                            HStack(spacing: 12) {
                                PosterImage(url: group.logo, title: group.title).frame(width: 46)
                                VStack(alignment: .leading) {
                                    Text(group.title).font(.headline).foregroundStyle(palette.text)
                                    Text("\(group.items.count) épisode(s) · \(group.items.reduce(Int64(0)) { $0 + $1.bytes }.formattedBytes)")
                                        .font(.caption).foregroundStyle(palette.textSecondary)
                                }
                            }
                        }
                    }
                    .listRowBackground(palette.card)
                } else if let record = group.items.first {
                    Section { row(record, group: [record], showPoster: true) }.listRowBackground(palette.card)
                }
            }
            if !records.isEmpty {
                Section {
                    Button("Supprimer tous les téléchargements", role: .destructive) { confirmClear = true }
                }
                .listRowBackground(palette.card)
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(palette.background.ignoresSafeArea())
        .navigationTitle("Téléchargements")
        .confirmationDialog("Supprimer tous les téléchargements ?", isPresented: $confirmClear, titleVisibility: .visible) {
            Button("Supprimer", role: .destructive) { downloads.deleteAll(profileID: profile.id) }
        } message: {
            Text("Les vidéos enregistrées sur cet iPhone seront effacées.")
        }
        .novaDestinations(profile: profile)
    }

    private func row(_ record: DownloadRecord, group: [DownloadRecord], showPoster: Bool) -> some View {
        HStack(spacing: 12) {
            if showPoster { PosterImage(url: record.logo, title: record.title).frame(width: 46) }
            VStack(alignment: .leading, spacing: 4) {
                Text(record.kind == .episode ? (record.subtitle ?? record.title) : record.title)
                    .font(.subheadline.weight(.medium)).foregroundStyle(palette.text).lineLimit(2)
                Text(status(record)).font(.caption).foregroundStyle(record.state == .failed ? .red : (record.state == .completed ? palette.accent2 : palette.textSecondary))
                if record.state == .downloading || record.state == .paused || record.state == .queued {
                    ProgressView(value: record.progress).tint(palette.accent)
                }
            }
            Spacer()
            switch record.state {
            case .completed:
                Button {
                    let ready = group.filter { $0.state == .completed }
                    playback.play(ready.map(\.playable), start: ready.firstIndex(of: record) ?? 0, profile: profile)
                } label: { Image(systemName: "play.circle.fill").font(.title) }
                    .buttonStyle(.plain).foregroundStyle(palette.accent)
                    .accessibilityLabel("Lire \(record.subtitle ?? record.title)")
            case .downloading, .queued:
                Button { downloads.pause(record) } label: { Image(systemName: "pause.circle").font(.title2) }.buttonStyle(.plain)
                    .accessibilityLabel("Mettre en pause")
            case .paused, .failed:
                Button { downloads.resume(record) } label: { Image(systemName: "arrow.clockwise.circle").font(.title2) }.buttonStyle(.plain)
                    .accessibilityLabel("Reprendre")
            }
        }
        .swipeActions {
            Button(role: .destructive) { downloads.delete(record) } label: { Label("Supprimer", systemImage: "trash") }
        }
    }

    private func status(_ record: DownloadRecord) -> String {
        let size = record.bytes > 0 ? " · \(record.bytes.formattedBytes)" : ""
        switch record.state {
        case .queued: return (downloads.isWaitingForWifi ? "En attente du Wi-Fi" : "En file d'attente") + size
        case .downloading: return "Téléchargement · \(Int(record.progress * 100)) %" + size
        case .paused: return "En pause · \(Int(record.progress * 100)) %" + size
        case .completed: return "Téléchargé" + size
        case .failed: return "Échec : \(record.failure ?? "erreur inconnue")"
        }
    }
}
