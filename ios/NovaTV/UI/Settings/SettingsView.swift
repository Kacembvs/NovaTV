import SwiftData
import SwiftUI
import UniformTypeIdentifiers

enum SettingsSection: String, CaseIterable, Identifiable {
    case profiles, playback, downloads, languages, appearance, parental, backup
    var id: String { rawValue }

    var title: String {
        switch self {
        case .profiles: return "Profils"
        case .playback: return "Lecture"
        case .downloads: return "Téléchargements"
        case .languages: return "Langues"
        case .appearance: return "Apparence"
        case .parental: return "Contrôle parental"
        case .backup: return "Sauvegarde"
        }
    }

    var subtitle: String {
        switch self {
        case .profiles: return "Vos sources IPTV · M3U / M3U8 et Xtream Codes"
        case .playback: return "Lecteur, épisodes suivants et Picture-in-Picture"
        case .downloads: return "Lecture hors ligne · Wi-Fi et stockage"
        case .languages: return "Préférences audio, sous-titres et actualisation"
        case .appearance: return "Interfaces et couleurs NOVA"
        case .parental: return "Code PIN et catégories protégées"
        case .backup: return "Profils, favoris, reprises et réglages · compatible Android"
        }
    }

    var icon: String {
        switch self {
        case .profiles: return "person.2.fill"
        case .playback: return "play.fill"
        case .downloads: return "arrow.down.circle.fill"
        case .languages: return "globe"
        case .appearance: return "paintpalette.fill"
        case .parental: return "lock.fill"
        case .backup: return "externaldrive.fill"
        }
    }
}

struct SettingsView: View {
    @EnvironmentObject private var settings: AppSettings
    @Environment(\.palette) private var palette
    @State private var section = SettingsSection.profiles
    @State private var askPin = false

    var body: some View {
        Group {
            if settings.settingsBlocked {
                VStack(spacing: 16) {
                    Image(systemName: "lock.fill").font(.largeTitle).foregroundStyle(palette.textSecondary)
                    Text("Réglages protégés").font(.title2.bold()).foregroundStyle(palette.text)
                    Text("Le contrôle parental protège les réglages.").foregroundStyle(palette.textSecondary)
                    Button("Saisir le code PIN") { askPin = true }.buttonStyle(.novaPrimary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                GeometryReader { geo in
                    if geo.size.width >= 700 {
                        HStack(alignment: .top, spacing: 0) {
                            ScrollView {
                                VStack(spacing: 8) {
                                    ForEach(SettingsSection.allCases) { entry in sidebarButton(entry) }
                                }
                                .padding(16)
                            }
                            .frame(width: 250)
                            .background(palette.background2)
                            content
                        }
                    } else {
                        VStack(spacing: 0) {
                            ScrollViewReader { proxy in
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 8) {
                                        ForEach(SettingsSection.allCases) { entry in
                                            NovaChip(title: entry.title, icon: entry.icon, selected: section == entry) { section = entry }
                                                .id(entry)
                                        }
                                    }
                                    .padding(.horizontal)
                                    .padding(.vertical, 10)
                                }
                                .onChange(of: section) { _, value in withAnimation { proxy.scrollTo(value, anchor: .center) } }
                            }
                            content
                        }
                    }
                }
            }
        }
        .background(palette.background.ignoresSafeArea())
        .navigationTitle("Réglages")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $askPin) {
            PinSheet(title: "Réglages protégés", message: nil) { settings.verify($0) ? nil : "Code incorrect." }
        }
    }

    private func sidebarButton(_ entry: SettingsSection) -> some View {
        Button { section = entry } label: {
            Label(entry.title, systemImage: entry.icon).frame(maxWidth: .infinity, alignment: .leading)
        }
        .buttonStyle(NovaButtonStyle(prominent: section == entry))
    }

    private var content: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(section.title).font(.title2.bold()).foregroundStyle(palette.text)
                Text(section.subtitle).font(.subheadline).foregroundStyle(palette.textSecondary)
                Group {
                    switch section {
                    case .profiles: ProfilesSettings()
                    case .playback: PlaybackSettings()
                    case .downloads: DownloadSettings()
                    case .languages: LanguageSettings()
                    case .appearance: AppearanceSettings()
                    case .parental: ParentalSettings()
                    case .backup: BackupSettings()
                    }
                }
                .padding(.top, 6)
            }
            .padding()
            .frame(maxWidth: 760, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .id(section)
    }
}

// MARK: Building blocks

struct SettingToggle: View {
    let title: String
    var detail: String? = nil
    @Binding var isOn: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.body.weight(.medium)).foregroundStyle(palette.text)
                if let detail { Text(detail).font(.caption).foregroundStyle(palette.textSecondary) }
            }
        }
        .novaCard()
    }
}

struct SettingChoice<Leading: View>: View {
    let title: String
    var detail: String? = nil
    let selected: Bool
    let action: () -> Void
    @ViewBuilder var leading: () -> Leading
    @Environment(\.palette) private var palette
    @Environment(\.novaStyle) private var style

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                leading()
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(.body.weight(.medium)).foregroundStyle(palette.text)
                    if let detail { Text(detail).font(.caption).foregroundStyle(palette.textSecondary).multilineTextAlignment(.leading) }
                }
                Spacer(minLength: 8)
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(selected ? palette.accent : palette.border)
            }
            .padding(14)
            .background(palette.card, in: RoundedRectangle(cornerRadius: style.radius))
            .overlay(RoundedRectangle(cornerRadius: style.radius).stroke(selected ? palette.accent : palette.border, lineWidth: selected ? 2 : 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

extension SettingChoice where Leading == EmptyView {
    init(title: String, detail: String? = nil, selected: Bool, action: @escaping () -> Void) {
        self.init(title: title, detail: detail, selected: selected, action: action, leading: { EmptyView() })
    }
}

struct SettingAction: View {
    let title: String
    let icon: String
    var destructive = false
    var prominent = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(title, systemImage: icon).frame(maxWidth: .infinity, alignment: .leading)
        }
        .buttonStyle(NovaButtonStyle(prominent: prominent, destructive: destructive))
    }
}

struct SettingHeader: View {
    let text: String
    @Environment(\.palette) private var palette
    var body: some View {
        Text(text.uppercased()).font(.caption.weight(.bold)).tracking(1.2).foregroundStyle(palette.textSecondary).padding(.top, 10)
    }
}

struct SettingNote: View {
    let text: String
    @Environment(\.palette) private var palette
    var body: some View {
        Text(text).font(.footnote).foregroundStyle(palette.textSecondary).fixedSize(horizontal: false, vertical: true)
    }
}

// MARK: Profiles

private struct ProfilesSettings: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var sync: SyncCenter
    @EnvironmentObject private var epg: EPGStore
    @EnvironmentObject private var downloads: DownloadCenter
    @Environment(\.modelContext) private var context
    @Query(sort: \Profile.createdAt) private var profiles: [Profile]
    @State private var editing: Profile?
    @State private var adding = false
    @State private var toDelete: Profile?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SettingAction(title: "Ajouter un profil", icon: "plus", prominent: true) { adding = true }
            if profiles.isEmpty { SettingNote(text: "Aucun profil. Ajoutez votre première source IPTV pour commencer.") }
            ForEach(profiles) { profile in
                ProfileCard(profile: profile, active: profile.id == (settings.activeProfileID ?? profiles.first?.id),
                            onEdit: { editing = profile }, onDelete: { toDelete = profile })
            }
        }
        .sheet(isPresented: $adding) { NavigationStack { ProfileEditView(profile: nil) } }
        .sheet(item: $editing) { profile in NavigationStack { ProfileEditView(profile: profile) } }
        .confirmationDialog("Supprimer ce profil ?", isPresented: Binding(get: { toDelete != nil }, set: { if !$0 { toDelete = nil } }),
                            titleVisibility: .visible, presenting: toDelete) { profile in
            Button("Supprimer « \(profile.name) »", role: .destructive) { delete(profile) }
        } message: { _ in
            Text("Les chaînes, favoris, reprises et téléchargements de ce profil seront supprimés de l'iPhone.")
        }
    }

    private func delete(_ profile: Profile) {
        let id = profile.id
        sync.cancel(id)
        downloads.deleteAll(profileID: id)
        try? context.delete(model: Content.self, where: #Predicate<Content> { $0.profileID == id })
        try? context.delete(model: ContentCategory.self, where: #Predicate<ContentCategory> { $0.profileID == id })
        try? context.delete(model: Favorite.self, where: #Predicate<Favorite> { $0.profileID == id })
        try? context.delete(model: HistoryEntry.self, where: #Predicate<HistoryEntry> { $0.profileID == id })
        Keychain.set(nil, for: id.uuidString)
        context.delete(profile)
        try? context.save()
        if settings.activeProfileID == id { settings.activeProfileID = profiles.first { $0.id != id }?.id }
        toDelete = nil
    }
}

private struct ProfileCard: View {
    let profile: Profile
    let active: Bool
    let onEdit: () -> Void
    let onDelete: () -> Void
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var sync: SyncCenter
    @EnvironmentObject private var epg: EPGStore
    @Environment(\.palette) private var palette

    private var status: (String, Bool) {
        switch sync.states[profile.id] {
        case .running(let count): return ("Synchronisation… \(count) contenus", false)
        case .failed(let message): return ("Erreur : \(message)", true)
        case nil:
            if let error = profile.lastSyncError { return ("Erreur : \(error)", true) }
            if let date = profile.lastSyncAt {
                return ("\(profile.contentCount) contenus · actualisé \(date.formatted(.relative(presentation: .named)))", false)
            }
            return ("Jamais synchronisé", false)
        }
    }

    private var guide: (String, Bool) {
        if let error = profile.epgError { return ("Guide TV : \(error)", true) }
        if let date = profile.epgSyncedAt { return ("Guide TV actualisé \(date.formatted(.relative(presentation: .named)))", false) }
        return ("Guide TV : pas encore chargé", false)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Text(profile.name).font(.headline).foregroundStyle(palette.text)
                Text(profile.type == .m3u ? "M3U" : "Xtream").font(.caption2.bold()).foregroundStyle(palette.accent2)
                Spacer()
                if active { Label("Profil actif", systemImage: "checkmark.circle.fill").font(.caption.bold()).foregroundStyle(palette.accent) }
            }
            Text(status.0).font(.caption).foregroundStyle(status.1 ? palette.error : palette.textSecondary)
            Text(guide.0).font(.caption).foregroundStyle(guide.1 ? palette.error : palette.textSecondary)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 8)], spacing: 8) {
                if !active {
                    Button { settings.activeProfileID = profile.id } label: { Label("Utiliser", systemImage: "checkmark").frame(maxWidth: .infinity) }
                        .buttonStyle(.novaPrimary)
                }
                Button(action: onEdit) { Label("Modifier", systemImage: "pencil").frame(maxWidth: .infinity) }.buttonStyle(.novaCompact)
                Button { sync.sync(profile) } label: { Label("Actualiser", systemImage: "arrow.clockwise").frame(maxWidth: .infinity) }
                    .buttonStyle(.novaCompact).disabled(sync.isRunning(profile.id))
                Button { Task { await epg.refresh(profile) } } label: { Label("Guide TV", systemImage: "list.bullet.rectangle").frame(maxWidth: .infinity) }
                    .buttonStyle(.novaCompact).disabled(epg.loading)
                Button(action: onDelete) { Label("Supprimer", systemImage: "trash").frame(maxWidth: .infinity) }
                    .buttonStyle(NovaButtonStyle(prominent: false, compact: true, destructive: true))
            }
        }
        .novaCard()
    }
}

// MARK: Playback

private struct PlaybackSettings: View {
    @EnvironmentObject private var settings: AppSettings

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SettingHeader(text: "Lecteur par défaut")
            ForEach(PlayerChoice.allCases) { choice in
                SettingChoice(title: choice.label, detail: choice == .nova ? "Lecture dans NOVA, téléchargements inclus" : "Doit être installée sur l'iPhone",
                              selected: settings.preferredPlayer == choice, action: { settings.preferredPlayer = choice })
            }
            SettingHeader(text: "Moteur du lecteur NOVA")
            ForEach(InternalEngine.allCases) { engine in
                SettingChoice(title: engine.label,
                              detail: engine == .automatic
                                ? "Lecteur Apple (Picture-in-Picture, AirPlay) ; moteur VLC intégré pour MKV, AVI, TS… et en secours."
                                : "Lit tous les formats avec les commandes NOVA (pas de Picture-in-Picture).",
                              selected: settings.internalEngine == engine, action: { settings.internalEngine = engine })
            }
            SettingHeader(text: "Épisodes")
            SettingToggle(title: "Épisode suivant automatique", detail: "Propose l'épisode suivant avant la fin avec un compte à rebours.",
                          isOn: $settings.autoplayNext)
            if settings.autoplayNext {
                HStack(spacing: 8) {
                    ForEach(AppSettings.countdownChoices, id: \.self) { seconds in
                        NovaChip(title: "\(seconds) s", selected: settings.countdownSeconds == seconds) { settings.countdownSeconds = seconds }
                    }
                }
            }
            SettingHeader(text: "Écran")
            SettingToggle(title: "Picture-in-Picture", detail: "La vidéo continue dans une petite fenêtre quand vous quittez NOVA.",
                          isOn: $settings.pictureInPicture)
            SettingToggle(title: "Rotation libre du lecteur", detail: "Désactivé : lecture en paysage uniquement.", isOn: $settings.freeRotation)
            SettingToggle(title: "Gestes", detail: "Double appui à gauche ou à droite : −10 s / +10 s (moteur VLC).", isOn: $settings.gesturesEnabled)
            SettingNote(text: "Minuteur de sommeil : menu « … » dans le lecteur. Il est prioritaire sur la lecture automatique.")
        }
    }
}

// MARK: Downloads

private struct DownloadSettings: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var downloads: DownloadCenter
    @State private var confirmClear = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            NavigationLink(value: Route.downloads) {
                Label("Ouvrir mes téléchargements", systemImage: "arrow.down.circle").frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.novaPrimary)
            SettingNote(text: "NOVA utilise \(downloads.usedBytes.formattedBytes) · \(downloads.freeBytes.formattedBytes) libres sur l'iPhone")
            SettingToggle(title: "Wi-Fi uniquement", detail: "Les téléchargements attendent une connexion Wi-Fi pour préserver vos données mobiles.",
                          isOn: $settings.downloadWifiOnly)
            SettingToggle(title: "Téléchargements intelligents",
                          detail: "Un épisode téléchargé regardé jusqu'au bout est supprimé et l'épisode suivant est téléchargé.",
                          isOn: $settings.smartDownloads)
            SettingNote(text: "Les films et épisodes sont enregistrés dans la qualité fournie par la source. Jusqu'à 2 téléchargements simultanés, poursuivis en arrière-plan et repris après une coupure. Les flux adaptatifs HLS ne sont pas téléchargeables.")
            SettingAction(title: "Supprimer tous les téléchargements", icon: "trash", destructive: true) { confirmClear = true }
        }
        .confirmationDialog("Supprimer tous les téléchargements ?", isPresented: $confirmClear, titleVisibility: .visible) {
            Button("Supprimer", role: .destructive) { downloads.deleteAll() }
        } message: {
            Text("Les vidéos enregistrées sur cet iPhone seront effacées.")
        }
    }
}

// MARK: Languages

private struct LanguageSettings: View {
    @EnvironmentObject private var settings: AppSettings

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SettingToggle(title: "Actualisation automatique",
                          detail: "À l'ouverture, catalogue et guide TV sont actualisés s'ils ont plus de 12 h.",
                          isOn: $settings.autoRefresh)
            SettingNote(text: "Les pistes sont choisies automatiquement quand le flux les propose. Un choix fait dans le lecteur devient la nouvelle préférence.")
            SettingHeader(text: "Audio")
            languages(selection: settings.audioLanguage, none: "Automatique") { settings.audioLanguage = $0 }
            SettingHeader(text: "Sous-titres")
            languages(selection: settings.subtitleLanguage, none: "Désactivés") { settings.subtitleLanguage = $0 }
        }
    }

    private func languages(selection: String?, none: String, set: @escaping (String?) -> Void) -> some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 8)], alignment: .leading, spacing: 8) {
            NovaChip(title: none, selected: selection == nil) { set(nil) }
            ForEach(preferredLanguageCodes, id: \.self) { code in
                NovaChip(title: languageName(code), selected: selection == code) { set(code) }
            }
        }
    }
}

// MARK: Appearance

private struct AppearanceSettings: View {
    @EnvironmentObject private var settings: AppSettings
    @Environment(\.novaStyle) private var style

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SettingHeader(text: "Interface · disposition, formes et couleurs")
            ForEach(NovaStyle.allCases) { entry in
                SettingChoice(title: entry.label, detail: entry.summary, selected: settings.style == entry,
                              action: { settings.applyStyle(entry) }) {
                    swatch(Palette.of(entry.defaultPalette), radius: entry.radius)
                }
            }
            SettingHeader(text: "Couleurs · changer d'interface réapplique ses couleurs")
            ForEach(PaletteID.allCases) { id in
                SettingChoice(title: id.label, selected: settings.paletteID == id, action: { settings.paletteID = id }) {
                    swatch(Palette.of(id, customAccent: settings.customAccent), radius: style.radius)
                }
            }
            if settings.paletteID == .custom {
                SettingHeader(text: "Couleur d'accent")
                HStack(spacing: 12) {
                    ForEach(AppSettings.customAccents, id: \.self) { argb in
                        Button { settings.customAccent = argb } label: {
                            Circle().fill(Color(hex: argb)).frame(width: 34, height: 34)
                                .overlay(Circle().stroke(Color.white, lineWidth: settings.customAccent == argb ? 3 : 0))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(String(format: "Accent #%06X", argb))
                    }
                }
            }
        }
    }

    private func swatch(_ palette: Palette, radius: CGFloat) -> some View {
        HStack(spacing: 0) {
            palette.background.frame(width: 14)
            palette.card.frame(width: 14)
            palette.accent.frame(width: 14)
        }
        .frame(height: 30)
        .clipShape(RoundedRectangle(cornerRadius: min(radius, 8)))
        .overlay(RoundedRectangle(cornerRadius: min(radius, 8)).stroke(Color.gray.opacity(0.5)))
    }
}

// MARK: Parental control

private struct ParentalSettings: View {
    @EnvironmentObject private var settings: AppSettings
    @Query(sort: \Profile.createdAt) private var profiles: [Profile]
    @State private var flow: PinFlow?
    @State private var showCategories = false

    enum PinFlow: String, Identifiable {
        case create, change, remove, unlock
        var id: String { rawValue }
    }

    private var active: Profile? { profiles.first { $0.id == settings.activeProfileID } ?? profiles.first }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if settings.pinSet {
                SettingToggle(title: "Protéger les réglages", detail: "Un code PIN sera demandé pour accéder aux réglages.", isOn: $settings.lockSettings)
                if let active {
                    let count = settings.lockedGroups.filter { $0.hasPrefix(active.id.uuidString) }.count
                    SettingNote(text: "\(count) catégorie(s) verrouillée(s) pour le profil actif. "
                                + (settings.parentalUnlocked ? "Contenus protégés visibles jusqu'au reverrouillage." : "Contenus protégés masqués."))
                    SettingAction(title: "Catégories verrouillées", icon: "list.bullet") { showCategories = true }
                } else {
                    SettingNote(text: "Activez un profil pour gérer ses catégories.")
                }
                if settings.parentalUnlocked {
                    SettingAction(title: "Reverrouiller les contenus", icon: "lock") { settings.parentalUnlocked = false }
                } else {
                    SettingAction(title: "Déverrouiller les contenus", icon: "lock.open") { flow = .unlock }
                }
                SettingAction(title: "Changer le code PIN", icon: "key") { flow = .change }
                SettingAction(title: "Supprimer le code PIN", icon: "trash", destructive: true) { flow = .remove }
            } else {
                SettingAction(title: "Définir un code PIN", icon: "lock", prominent: true) { flow = .create }
                SettingNote(text: "Un code PIN permet de masquer des catégories et de protéger les réglages.")
            }
            SettingNote(text: "Les catégories verrouillées sont masquées dans l'accueil, les catalogues, le direct et la recherche. Les téléchargements déjà effectués restent visibles.")
        }
        .sheet(item: $flow) { flow in sheet(flow) }
        .sheet(isPresented: $showCategories) {
            if let active { NavigationStack { LockedCategoriesView(profile: active) } }
        }
    }

    @ViewBuilder
    private func sheet(_ flow: PinFlow) -> some View {
        switch flow {
        case .create:
            PinSheet(title: "Nouveau code PIN", message: "Choisissez un code à 4 chiffres.") { pin in
                settings.setPin(pin)
                return nil
            }
        case .change:
            ChangePinSheet()
        case .remove:
            PinSheet(title: "Supprimer le code PIN", message: "Saisissez le code actuel. Les catégories verrouillées seront de nouveau visibles.") { pin in
                guard settings.verify(pin) else { return "Code incorrect." }
                settings.removePin()
                return nil
            }
        case .unlock:
            PinSheet(title: "Déverrouiller", message: "Saisissez le code PIN pour afficher les contenus protégés.") { pin in
                settings.verify(pin) ? nil : "Code incorrect."
            }
        }
    }
}

private struct ChangePinSheet: View {
    @EnvironmentObject private var settings: AppSettings
    @State private var verified = false

    var body: some View {
        if verified {
            PinSheet(title: "Nouveau code PIN", message: "Choisissez le nouveau code à 4 chiffres.") { pin in
                settings.setPin(pin)
                return nil
            }
        } else {
            PinSheet(title: "Code actuel", message: "Saisissez le code PIN actuel.") { pin in
                guard settings.verify(pin) else { return "Code incorrect." }
                verified = true
                return "Saisissez maintenant le nouveau code."
            }
        }
    }
}

struct LockedCategoriesView: View {
    let profile: Profile
    @EnvironmentObject private var settings: AppSettings
    @Environment(\.palette) private var palette
    @Environment(\.dismiss) private var dismiss
    @Query private var categories: [ContentCategory]
    @State private var kind = ContentKind.live

    init(profile: Profile) {
        self.profile = profile
        let id = profile.id
        _categories = Query(filter: #Predicate<ContentCategory> { $0.profileID == id }, sort: \ContentCategory.order)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    NovaChip(title: "TV", selected: kind == .live) { kind = .live }
                    NovaChip(title: "Films", selected: kind == .movie) { kind = .movie }
                    NovaChip(title: "Séries", selected: kind == .series) { kind = .series }
                }
                ForEach(categories.filter { $0.kindRaw == kind.rawValue }) { category in
                    let key = AppSettings.groupKey(profile.id, kind, category.name)
                    SettingToggle(title: category.name, detail: "\(category.count) contenus",
                                  isOn: Binding(get: { settings.lockedGroups.contains(key) },
                                                set: { settings.setLocked(profile.id, kind, category.name, $0) }))
                }
            }
            .padding()
        }
        .background(palette.background.ignoresSafeArea())
        .navigationTitle("Catégories verrouillées")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Terminé") { dismiss() } } }
    }
}

// MARK: Backup

private struct BackupSettings: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var sync: SyncCenter
    @Environment(\.modelContext) private var context
    @Environment(\.palette) private var palette
    @State private var code = ""
    @State private var exported: IdentifiedURL?
    @State private var importing = false
    @State private var pendingData: Data?
    @State private var restoreCode = ""
    @State private var message: String?
    @State private var isError = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            NovaField(title: "Code de sauvegarde (facultatif)", text: $code, secure: true)
            SettingNote(text: "Avec un code, les mots de passe Xtream sont inclus et chiffrés. Sans code, ils ne sont pas exportés.")
            SettingAction(title: "Sauvegarder", icon: "square.and.arrow.up", prominent: true, action: export)
            SettingAction(title: "Restaurer une sauvegarde", icon: "square.and.arrow.down") { importing = true }
            SettingNote(text: "Profils, favoris, reprises et réglages. Les catalogues sont retéléchargés après restauration. Le fichier est compatible avec NOVA sur Android.")
            if let message {
                Text(message).font(.subheadline).foregroundStyle(isError ? palette.error : palette.accent2)
            }
        }
        .sheet(item: $exported) { file in ShareSheet(url: file.url) }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .data, .text]) { result in
            switch result {
            case .success(let url):
                let access = url.startAccessingSecurityScopedResource()
                defer { if access { url.stopAccessingSecurityScopedResource() } }
                do { restore(try Data(contentsOf: url), code: nil) } catch { show(error.userMessage, error: true) }
            case .failure(let error):
                show(error.userMessage, error: true)
            }
        }
        .alert("Code de la sauvegarde", isPresented: Binding(get: { pendingData != nil }, set: { if !$0 { pendingData = nil } })) {
            SecureField("Code", text: $restoreCode)
            Button("Restaurer") {
                if let data = pendingData { restore(data, code: restoreCode) }
            }
            Button("Annuler", role: .cancel) { pendingData = nil }
        } message: {
            Text("Cette sauvegarde contient des mots de passe chiffrés.")
        }
    }

    private func show(_ text: String, error: Bool) {
        message = text
        isError = error
    }

    private func export() {
        do {
            let data = try BackupManager.export(context: context, settings: settings, code: code.isEmpty ? nil : code)
            let stamp = Date().formatted(.iso8601.year().month().day())
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("NOVA-sauvegarde-\(stamp).json")
            try data.write(to: url, options: .atomic)
            exported = IdentifiedURL(url: url)
            show("Sauvegarde prête : enregistrez-la dans Fichiers ou envoyez-la.", error: false)
        } catch {
            show("Échec de la sauvegarde : \(error.userMessage)", error: true)
        }
    }

    private func restore(_ data: Data, code: String?) {
        do {
            let result = try BackupManager.restore(data, code: code, context: context, settings: settings)
            pendingData = nil
            restoreCode = ""
            result.newProfiles.forEach { sync.sync($0) }
            if settings.activeProfileID == nil { settings.activeProfileID = result.newProfiles.first?.id }
            show(result.summary, error: false)
        } catch BackupManager.BackupError.codeRequired {
            pendingData = data
        } catch BackupManager.BackupError.wrongCode {
            pendingData = data
            restoreCode = ""
            show("Code incorrect.", error: true)
        } catch {
            pendingData = nil
            show("Échec de la restauration : \(error.userMessage)", error: true)
        }
    }
}

// MARK: Profile form

struct ProfileEditView: View {
    let profile: Profile?
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var sync: SyncCenter
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Environment(\.palette) private var palette
    @State private var type = ProfileType.m3u
    @State private var name = ""
    @State private var url = ""
    @State private var username = ""
    @State private var password = ""
    @State private var epgURL = ""
    @State private var saving = false
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 8) {
                    ForEach(ProfileType.allCases) { entry in
                        NovaChip(title: entry.label, selected: type == entry) { type = entry }
                    }
                }
                NovaField(title: "Nom du profil", text: $name)
                NovaField(title: type == .m3u ? "Adresse de la playlist (http…)" : "Serveur (http://hôte:port)", text: $url, keyboard: .URL)
                if type == .xtream {
                    NovaField(title: "Nom d'utilisateur", text: $username)
                    NovaField(title: "Mot de passe", text: $password, secure: true)
                    if profile != nil && password.isEmpty {
                        SettingNote(text: "Laissez vide pour conserver le mot de passe enregistré.")
                    }
                }
                NovaField(title: "Adresse du guide TV (facultatif)", text: $epgURL, keyboard: .URL)
                SettingNote(text: type == .xtream ? "Par défaut, le guide fourni par le serveur Xtream est utilisé."
                                                  : "Par défaut, le guide déclaré dans la playlist (url-tvg) est utilisé.")
                if let error { Text(error).foregroundStyle(palette.error) }
                Button { Task { await save() } } label: {
                    HStack {
                        if saving { ProgressView().tint(palette.onAccent) }
                        Text(saving ? "Vérification…" : "Enregistrer")
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.novaPrimary)
                .disabled(saving)
            }
            .padding()
        }
        .background(palette.background.ignoresSafeArea())
        .navigationTitle(profile == nil ? "Nouveau profil" : "Modifier le profil")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
        }
        .onAppear {
            guard let profile else { return }
            type = profile.type
            name = profile.name
            url = profile.url
            username = profile.username ?? ""
            epgURL = profile.customEPGURL ?? ""
        }
        .interactiveDismissDisabled(saving)
    }

    private func save() async {
        let trimmedURL = url.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedEPG = epgURL.trimmingCharacters(in: .whitespacesAndNewlines)
        error = nil
        guard !trimmedURL.isEmpty else { error = "Indiquez l'adresse."; return }
        if type == .m3u {
            guard let parsed = URL(string: trimmedURL), parsed.scheme?.hasPrefix("http") == true else {
                error = "L'adresse doit commencer par http:// ou https://."
                return
            }
        }
        if !trimmedEPG.isEmpty, URL(string: trimmedEPG)?.scheme?.hasPrefix("http") != true {
            error = "L'adresse du guide TV doit commencer par http:// ou https://."
            return
        }
        let storedPassword = profile.map(Keychain.password(for:)) ?? ""
        let effectivePassword = password.isEmpty ? storedPassword : password
        if type == .xtream {
            guard !username.isEmpty, !effectivePassword.isEmpty else { error = "Indiquez l'utilisateur et le mot de passe."; return }
            saving = true
            defer { saving = false }
            do {
                try await XtreamClient(credentials: .init(base: trimmedURL, username: username, password: effectivePassword)).authenticate()
            } catch {
                self.error = error.userMessage
                return
            }
        }
        let target: Profile
        if let profile {
            target = profile
            target.typeRaw = type.rawValue
            target.url = type == .xtream ? XtreamURLs.normalizeBase(trimmedURL) : trimmedURL
        } else {
            target = Profile(name: "", type: type, url: type == .xtream ? XtreamURLs.normalizeBase(trimmedURL) : trimmedURL)
            context.insert(target)
        }
        target.name = trimmedName.isEmpty ? (URL(string: target.url)?.host ?? "Mon profil") : trimmedName
        target.username = type == .xtream ? username : nil
        target.customEPGURL = trimmedEPG.isEmpty ? nil : trimmedEPG
        Keychain.set(type == .xtream ? effectivePassword : nil, for: target.id.uuidString)
        try? context.save()
        settings.activeProfileID = target.id
        sync.sync(target)
        dismiss()
    }
}
