import SwiftData
import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var sync: SyncCenter
    @EnvironmentObject private var epg: EPGStore
    @EnvironmentObject private var downloads: DownloadCenter
    @Environment(\.modelContext) private var context
    @Environment(\.palette) private var palette
    @Query(sort: \Profile.createdAt) private var profiles: [Profile]
    @State private var editing: Profile?
    @State private var adding = false
    @State private var toDelete: Profile?
    @State private var askPin = false
    @State private var pinFlow: PinFlow?

    enum PinFlow: Identifiable {
        case create, change, remove
        var id: Int { hashValue }
    }

    var body: some View {
        Group {
            if settings.settingsBlocked {
                EmptyStateView(systemImage: "lock.fill", title: "Réglages protégés",
                               message: "Le contrôle parental protège les réglages.", actionTitle: "Saisir le code PIN") { askPin = true }
            } else {
                form
            }
        }
        .background(palette.background.ignoresSafeArea())
        .navigationTitle("Réglages")
        .sheet(isPresented: $askPin) {
            PinSheet(title: "Réglages protégés", message: nil) { settings.verify($0) ? nil : "Code incorrect." }
        }
        .sheet(isPresented: $adding) { NavigationStack { ProfileEditView(profile: nil) } }
        .sheet(item: $editing) { profile in NavigationStack { ProfileEditView(profile: profile) } }
        .sheet(item: $pinFlow) { flow in pinSheet(flow) }
        .confirmationDialog("Supprimer ce profil ?", isPresented: Binding(get: { toDelete != nil }, set: { if !$0 { toDelete = nil } }),
                            titleVisibility: .visible, presenting: toDelete) { profile in
            Button("Supprimer « \(profile.name) »", role: .destructive) { delete(profile) }
        } message: { _ in
            Text("Les chaînes, favoris, reprises et téléchargements de ce profil seront supprimés de l'iPhone.")
        }
    }

    private var form: some View {
        Form {
            Section("Profils IPTV") {
                ForEach(profiles) { profile in
                    ProfileRow(profile: profile, active: profile.id == (settings.activeProfileID ?? profiles.first?.id))
                        .contentShape(Rectangle())
                        .onTapGesture { settings.activeProfileID = profile.id }
                        .swipeActions {
                            Button(role: .destructive) { toDelete = profile } label: { Label("Supprimer", systemImage: "trash") }
                            Button { editing = profile } label: { Label("Modifier", systemImage: "pencil") }.tint(.blue)
                        }
                        .contextMenu {
                            Button { settings.activeProfileID = profile.id } label: { Label("Utiliser ce profil", systemImage: "checkmark.circle") }
                            Button { sync.sync(profile) } label: { Label("Actualiser le catalogue", systemImage: "arrow.clockwise") }
                            Button { Task { await epg.refresh(profile) } } label: { Label("Actualiser le guide TV", systemImage: "list.bullet.rectangle") }
                            Button { editing = profile } label: { Label("Modifier", systemImage: "pencil") }
                            Button(role: .destructive) { toDelete = profile } label: { Label("Supprimer", systemImage: "trash") }
                        }
                }
                Button { adding = true } label: { Label("Ajouter un profil", systemImage: "plus.circle.fill") }
                if let active = profiles.first(where: { $0.id == settings.activeProfileID }) ?? profiles.first {
                    Button { sync.sync(active) } label: { Label("Actualiser le catalogue actif", systemImage: "arrow.clockwise") }
                        .disabled(sync.isRunning(active.id))
                    Button { Task { await epg.refresh(active) } } label: {
                        Label(epg.loading ? "Chargement du guide TV…" : "Actualiser le guide TV", systemImage: "list.bullet.rectangle")
                    }
                    .disabled(epg.loading)
                }
            }

            Section {
                Picker("Lecteur par défaut", selection: $settings.preferredPlayer) {
                    ForEach(PlayerChoice.allCases) { Text($0.label).tag($0) }
                }
                Picker("Formats MKV / AVI", selection: $settings.fallbackPlayer) {
                    ForEach(ExternalPlayer.allCases) { Text($0.name).tag($0) }
                }
                Toggle("Épisode suivant automatique", isOn: $settings.autoplayNext)
            } header: {
                Text("Lecture")
            } footer: {
                Text("Le lecteur NOVA (Apple) gère Picture-in-Picture, AirPlay, pistes audio, sous-titres, vitesse et minuteur de sommeil. Les fichiers MKV et AVI, non pris en charge par Apple, s'ouvrent dans le lecteur choisi (VLC, Outplayer ou Infuse doivent être installés).")
            }

            Section {
                Toggle("Wi-Fi uniquement", isOn: $settings.downloadWifiOnly)
                Toggle("Téléchargements intelligents", isOn: $settings.smartDownloads)
                LabeledContent("Espace utilisé", value: downloads.usedBytes.formattedBytes)
                NavigationLink(value: Route.downloads) { Text("Mes téléchargements") }
            } header: {
                Text("Téléchargements")
            } footer: {
                Text("Jusqu'à 2 téléchargements simultanés, poursuivis en arrière-plan. Un épisode regardé jusqu'au bout est remplacé par le suivant si les téléchargements intelligents sont activés.")
            }

            Section("Apparence") {
                ForEach(Palette.all) { palette in
                    Button { settings.paletteID = palette.id } label: {
                        HStack(spacing: 12) {
                            HStack(spacing: 0) {
                                palette.background.frame(width: 18)
                                palette.card.frame(width: 18)
                                palette.accent.frame(width: 18)
                            }
                            .frame(height: 28)
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                            .overlay(RoundedRectangle(cornerRadius: 6).stroke(.gray.opacity(0.4)))
                            Text(palette.name).foregroundStyle(.primary)
                            Spacer()
                            if settings.paletteID == palette.id { Image(systemName: "checkmark").foregroundStyle(palette.accent) }
                        }
                    }
                }
            }

            Section {
                if settings.pinSet {
                    Toggle("Protéger les réglages", isOn: $settings.lockSettings)
                    if let active = profiles.first(where: { $0.id == settings.activeProfileID }) ?? profiles.first {
                        NavigationLink {
                            LockedCategoriesView(profile: active)
                        } label: {
                            LabeledContent("Catégories verrouillées", value: "\(settings.lockedGroups.filter { $0.hasPrefix(active.id.uuidString) }.count)")
                        }
                    }
                    if settings.parentalUnlocked {
                        Button("Reverrouiller les contenus") { settings.parentalUnlocked = false }
                    } else {
                        Button("Déverrouiller les contenus") { askPin = true }
                    }
                    Button("Changer le code PIN") { pinFlow = .change }
                    Button("Supprimer le code PIN", role: .destructive) { pinFlow = .remove }
                } else {
                    Button("Définir un code PIN") { pinFlow = .create }
                }
            } header: {
                Text("Contrôle parental")
            } footer: {
                Text("Les catégories verrouillées sont masquées dans l'accueil, les catalogues, le direct et la recherche tant que les contenus ne sont pas déverrouillés.")
            }

            Section("À propos") {
                LabeledContent("Version", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0")
                Text("NOVA ne fournit aucun contenu. Utilisez uniquement des sources auxquelles vous avez droit.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
        .scrollContentBackground(.hidden)
    }

    @ViewBuilder
    private func pinSheet(_ flow: PinFlow) -> some View {
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

private struct ProfileRow: View {
    let profile: Profile
    let active: Bool
    @EnvironmentObject private var sync: SyncCenter

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: active ? "checkmark.circle.fill" : "circle").foregroundStyle(active ? Color.accentColor : .secondary)
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(profile.name).font(.headline)
                    Text(profile.type == .m3u ? "M3U" : "Xtream").font(.caption2.bold()).foregroundStyle(.purple)
                }
                Group {
                    switch sync.states[profile.id] {
                    case .running(let count): Text("Synchronisation… \(count) contenus")
                    case .failed(let message): Text("Erreur : \(message)").foregroundStyle(.red)
                    case nil:
                        if let error = profile.lastSyncError { Text("Erreur : \(error)").foregroundStyle(.red) }
                        else if let date = profile.lastSyncAt { Text("\(profile.contentCount) contenus · actualisé \(date.formatted(.relative(presentation: .named)))") }
                        else { Text("Jamais synchronisé") }
                    }
                }
                .font(.caption)
                .foregroundStyle(.secondary)
                if let error = profile.epgError {
                    Text("Guide TV : \(error)").font(.caption2).foregroundStyle(.red)
                } else if let date = profile.epgSyncedAt {
                    Text("Guide TV actualisé \(date.formatted(.relative(presentation: .named)))").font(.caption2).foregroundStyle(.secondary)
                }
            }
        }
    }
}

struct LockedCategoriesView: View {
    let profile: Profile
    @EnvironmentObject private var settings: AppSettings
    @Query private var categories: [ContentCategory]
    @State private var kind = ContentKind.live

    init(profile: Profile) {
        self.profile = profile
        let id = profile.id
        _categories = Query(filter: #Predicate<ContentCategory> { $0.profileID == id }, sort: \ContentCategory.order)
    }

    var body: some View {
        List {
            Picker("Type", selection: $kind) {
                Text("TV").tag(ContentKind.live)
                Text("Films").tag(ContentKind.movie)
                Text("Séries").tag(ContentKind.series)
            }
            .pickerStyle(.segmented)
            ForEach(categories.filter { $0.kindRaw == kind.rawValue }) { category in
                let key = AppSettings.groupKey(profile.id, kind, category.name)
                Toggle(isOn: Binding(get: { settings.lockedGroups.contains(key) },
                                     set: { settings.setLocked(profile.id, kind, category.name, $0) })) {
                    VStack(alignment: .leading) {
                        Text(category.name)
                        Text("\(category.count) contenus").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .navigationTitle("Catégories verrouillées")
    }
}

struct ProfileEditView: View {
    let profile: Profile?
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var sync: SyncCenter
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @State private var type = ProfileType.m3u
    @State private var name = ""
    @State private var url = ""
    @State private var username = ""
    @State private var password = ""
    @State private var epgURL = ""
    @State private var showPassword = false
    @State private var saving = false
    @State private var error: String?

    var body: some View {
        Form {
            Section {
                Picker("Type", selection: $type) {
                    ForEach(ProfileType.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.segmented)
                TextField("Nom du profil", text: $name)
                TextField(type == .m3u ? "Adresse de la playlist (http…)" : "Serveur (http://hôte:port)", text: $url)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                if type == .xtream {
                    TextField("Nom d'utilisateur", text: $username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    HStack {
                        Group {
                            if showPassword { TextField("Mot de passe", text: $password) } else { SecureField("Mot de passe", text: $password) }
                        }
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        Button { showPassword.toggle() } label: { Image(systemName: showPassword ? "eye.slash" : "eye") }
                            .buttonStyle(.plain)
                            .accessibilityLabel(showPassword ? "Masquer le mot de passe" : "Afficher le mot de passe")
                    }
                    if profile != nil && password.isEmpty {
                        Text("Laissez vide pour conserver le mot de passe enregistré.").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            Section {
                TextField("Adresse du guide TV (facultatif)", text: $epgURL)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            } footer: {
                Text(type == .xtream ? "Par défaut, le guide fourni par le serveur Xtream est utilisé." : "Par défaut, le guide déclaré dans la playlist (url-tvg) est utilisé.")
            }
            if let error {
                Section { Text(error).foregroundStyle(.red) }
            }
        }
        .navigationTitle(profile == nil ? "Nouveau profil" : "Modifier le profil")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) {
                if saving { ProgressView() } else { Button("Enregistrer") { Task { await save() } } }
            }
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
