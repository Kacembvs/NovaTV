import SwiftData
import SwiftUI

enum CatalogSort: String, CaseIterable, Identifiable {
    case provider, recent, alphabetical, rating
    var id: String { rawValue }
    var label: String {
        switch self {
        case .provider: return "Ordre du fournisseur"
        case .recent: return "Ajouts récents"
        case .alphabetical: return "A → Z"
        case .rating: return "Mieux notés"
        }
    }
}

struct CatalogView: View {
    let profile: Profile
    let kind: ContentKind
    @EnvironmentObject private var settings: AppSettings
    @Environment(\.palette) private var palette
    @Query private var categories: [ContentCategory]
    @State private var selected: String?
    @State private var askPin = false

    init(profile: Profile, kind: ContentKind) {
        self.profile = profile
        self.kind = kind
        let id = profile.id
        let raw = kind.rawValue
        _categories = Query(filter: #Predicate<ContentCategory> { $0.profileID == id && $0.kindRaw == raw }, sort: \ContentCategory.order)
    }

    private var visible: [ContentCategory] { categories.filter { !settings.isLocked(profile.id, kind, $0.name) } }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(visible) { category in
                        NovaChip(title: category.name, selected: (selected ?? visible.first?.name) == category.name) { selected = category.name }
                    }
                    if categories.count > visible.count {
                        Button { askPin = true } label: { Label("\(categories.count - visible.count) masquée(s)", systemImage: "lock.fill") }
                            .buttonStyle(.novaSecondary)
                    }
                }
                .padding(.horizontal)
                .padding(.vertical, 8)
            }
            if categories.isEmpty {
                EmptyStateView(systemImage: kind == .movie ? "film" : "rectangle.stack",
                               title: kind == .movie ? "Aucun film" : "Aucune série",
                               message: "Ce profil ne propose pas encore de contenus de ce type, ou le chargement est en cours.")
            } else if let group = selected ?? visible.first?.name {
                CategoryGrid(profile: profile, kind: kind, group: group).id(group)
            }
        }
        .background(palette.background.ignoresSafeArea())
        .navigationTitle(kind == .movie ? "Films" : "Séries")
        .navigationBarTitleDisplayMode(.inline)
        .novaToolbar()
        .novaDestinations(profile: profile)
        .sheet(isPresented: $askPin) {
            PinSheet(title: "Catégories protégées", message: "Saisissez le code PIN pour afficher les contenus protégés.") { pin in
                settings.verify(pin) ? nil : "Code incorrect."
            }
        }
    }
}

/// Standalone category page (from a "Tout voir" or a search result).
struct CategoryGridView: View {
    let profile: Profile
    let kind: ContentKind
    let group: String
    @Environment(\.palette) private var palette

    var body: some View {
        CategoryGrid(profile: profile, kind: kind, group: group)
            .background(palette.background.ignoresSafeArea())
            .navigationTitle(group)
            .navigationBarTitleDisplayMode(.inline)
    }
}

struct CategoryGrid: View {
    let profile: Profile
    let kind: ContentKind
    let group: String
    @Environment(\.palette) private var palette
    @Environment(\.novaStyle) private var style
    @Query private var items: [Content]
    @State private var sort = CatalogSort.provider
    @State private var filter = ""

    init(profile: Profile, kind: ContentKind, group: String) {
        self.profile = profile
        self.kind = kind
        self.group = group
        let id = profile.id
        let raw = kind.rawValue
        // M3U series entries may be stored as episodes.
        let episode = ContentKind.episode.rawValue
        if kind == .series {
            _items = Query(filter: #Predicate<Content> {
                $0.profileID == id && $0.group == group && ($0.kindRaw == raw || $0.kindRaw == episode)
            }, sort: \Content.sortIndex)
        } else {
            _items = Query(filter: #Predicate<Content> {
                $0.profileID == id && $0.group == group && $0.kindRaw == raw
            }, sort: \Content.sortIndex)
        }
    }

    private var shown: [Content] {
        let query = filter.searchFolded.trimmingCharacters(in: .whitespaces)
        let base = query.isEmpty ? items : items.filter { $0.searchName.contains(query) }
        switch sort {
        case .provider: return base
        case .recent: return base.sorted { ($0.addedAt ?? .distantPast) > ($1.addedAt ?? .distantPast) }
        case .alphabetical: return base.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
        case .rating: return base.sorted { (Double($0.rating ?? "") ?? 0) > (Double($1.rating ?? "") ?? 0) }
        }
    }

    private static func subtitle(_ item: Content) -> String {
        var parts: [String] = []
        if let year = item.year { parts.append(year) }
        if let rating = item.rating, !rating.isEmpty, rating != "0" { parts.append("★ " + rating) }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: style.landscapeCards ? 160 : 104, maximum: style.landscapeCards ? 260 : 160), spacing: 12)], spacing: 18) {
                ForEach(shown) { item in
                    if let route = route(for: item) {
                        NavigationLink(value: route) {
                            PosterCard(title: item.name, image: item.logo, subtitle: Self.subtitle(item), width: nil, aspect: style.landscapeCards ? 16 / 9 : 2 / 3)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding()
        }
        .searchable(text: $filter, prompt: "Filtrer « \(group) »")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Menu {
                    Picker("Trier", selection: $sort) {
                        ForEach(CatalogSort.allCases) { Text($0.label).tag($0) }
                    }
                } label: {
                    Image(systemName: "arrow.up.arrow.down")
                }
                .accessibilityLabel("Trier")
            }
        }
    }
}
