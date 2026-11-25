import SwiftUI

/// One entry of a group list: "Favoris", a provider group, …
struct GroupEntry: Identifiable, Equatable {
    let key: String
    let title: String
    var count: Int? = nil
    var icon: String? = nil
    var id: String { key }
}

extension EnvironmentValues {
    /// Landscape phone or iPad: same TV-style layout as Android (side rail and group column).
    var isWideLayout: Bool {
        verticalSizeClass == .compact || horizontalSizeClass == .regular
    }
}

/// Accent- and case-insensitive group filter.
func filterGroups(_ entries: [GroupEntry], query: String) -> [GroupEntry] {
    let folded = query.searchFolded.trimmingCharacters(in: .whitespaces)
    guard !folded.isEmpty else { return entries }
    return entries.filter { $0.title.searchFolded.contains(folded) }
}

struct GroupSearchField: View {
    @Binding var text: String
    var prompt = "Rechercher un groupe"
    @Environment(\.palette) private var palette
    @Environment(\.novaStyle) private var style

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(palette.textSecondary)
            TextField(prompt, text: $text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .foregroundStyle(palette.text)
            if !text.isEmpty {
                Button { text = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(palette.textSecondary) }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Effacer la recherche")
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(palette.background2, in: RoundedRectangle(cornerRadius: min(style.radius, 14)))
        .overlay(RoundedRectangle(cornerRadius: min(style.radius, 14)).stroke(palette.border, lineWidth: 1))
    }
}

private struct GroupRow: View {
    let entry: GroupEntry
    let selected: Bool
    let action: () -> Void
    @Environment(\.palette) private var palette
    @Environment(\.novaStyle) private var style

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if let icon = entry.icon { Image(systemName: icon).frame(width: 18) }
                Text(entry.title).lineLimit(2).multilineTextAlignment(.leading)
                Spacer(minLength: 6)
                if let count = entry.count { Text("\(count)").font(.caption.monospacedDigit()).opacity(0.7) }
            }
            .font(.subheadline.weight(selected ? .bold : .regular))
            .foregroundStyle(selected ? palette.onAccent : palette.text)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(selected ? palette.accent : Color.clear, in: RoundedRectangle(cornerRadius: min(style.radius, 12)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// Full searchable list of groups (portrait "Groupes" button).
struct GroupPickerSheet: View {
    let title: String
    let entries: [GroupEntry]
    let selected: String?
    let onSelect: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.palette) private var palette
    @State private var query = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 10) {
                GroupSearchField(text: $query)
                    .padding(.horizontal)
                let shown = filterGroups(entries, query: query)
                if shown.isEmpty {
                    Text("Aucun groupe ne correspond à « \(query) ».")
                        .font(.subheadline).foregroundStyle(palette.textSecondary)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollView {
                        LazyVStack(spacing: 2) {
                            ForEach(shown) { entry in
                                GroupRow(entry: entry, selected: entry.key == selected) {
                                    onSelect(entry.key)
                                    dismiss()
                                }
                            }
                        }
                        .padding(.horizontal)
                    }
                }
            }
            .padding(.top, 8)
            .background(palette.background.ignoresSafeArea())
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() } }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

/// Groups of a catalogue page. Portrait: "Groupes" search button + chips. Landscape/iPad: a
/// searchable column on the left, like the TV interfaces on Android.
struct GroupBrowser<Main: View, Extra: View>: View {
    let title: String
    let entries: [GroupEntry]
    @Binding var selected: String?
    let defaultKey: String?
    let extra: () -> Extra
    let content: () -> Main
    @Environment(\.isWideLayout) private var wide
    @Environment(\.palette) private var palette
    @State private var showPicker = false
    @State private var query = ""

    private var current: String? { selected ?? defaultKey ?? entries.first?.key }

    init(title: String, entries: [GroupEntry], selected: Binding<String?>, defaultKey: String? = nil,
         @ViewBuilder extra: @escaping () -> Extra, @ViewBuilder content: @escaping () -> Main) {
        self.title = title
        self.entries = entries
        _selected = selected
        self.defaultKey = defaultKey
        self.extra = extra
        self.content = content
    }

    var body: some View {
        if wide {
            HStack(spacing: 0) {
                column
                    .frame(width: 250)
                    .background(palette.background2.opacity(0.6))
                Divider().overlay(palette.border)
                content().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        } else {
            VStack(spacing: 0) {
                bar
                content().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .sheet(isPresented: $showPicker) {
                GroupPickerSheet(title: title, entries: entries, selected: current) { selected = $0 }
            }
        }
    }

    private var bar: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    NovaChip(title: "Groupes", count: entries.count, icon: "magnifyingglass", selected: false) { showPicker = true }
                        .accessibilityLabel("Rechercher un groupe")
                    ForEach(entries) { entry in
                        NovaChip(title: entry.title, count: entry.count, icon: entry.icon, selected: current == entry.key) { selected = entry.key }
                            .id(entry.key)
                    }
                    extra()
                }
                .padding(.horizontal)
                .padding(.vertical, 8)
            }
            .onChange(of: selected) { _, key in
                guard let key else { return }
                withAnimation { proxy.scrollTo(key, anchor: .center) }
            }
        }
    }

    private var column: some View {
        VStack(spacing: 8) {
            GroupSearchField(text: $query)
            let shown = filterGroups(entries, query: query)
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 2) {
                        ForEach(shown) { entry in
                            GroupRow(entry: entry, selected: current == entry.key) { selected = entry.key }
                                .id(entry.key)
                        }
                        if shown.isEmpty {
                            Text("Aucun groupe").font(.footnote).foregroundStyle(palette.textSecondary).padding(.top, 12)
                        }
                        extra().padding(.top, 8)
                    }
                }
                .onAppear { if let current { proxy.scrollTo(current, anchor: .center) } }
            }
        }
        .padding(10)
    }
}
