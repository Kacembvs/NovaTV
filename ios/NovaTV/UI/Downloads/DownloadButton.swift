import SwiftData
import SwiftUI

/// Download control: labelled for movies, compact icon with a progress ring for episodes.
struct DownloadButton: View {
    let profile: Profile
    let item: PlayableItem
    var season: Int? = nil
    var episode: Int? = nil
    var compact = false
    @EnvironmentObject private var downloads: DownloadCenter
    @Environment(\.palette) private var palette
    @Query private var matches: [DownloadRecord]
    @State private var showChoices = false
    @State private var error: String?

    init(profile: Profile, item: PlayableItem, season: Int? = nil, episode: Int? = nil, compact: Bool = false) {
        self.profile = profile
        self.item = item
        self.season = season
        self.episode = episode
        self.compact = compact
        let key = "\(profile.id.uuidString)|\(item.ref)"
        _matches = Query(filter: #Predicate<DownloadRecord> { $0.key == key })
    }

    private var record: DownloadRecord? { matches.first }

    var body: some View {
        Button(action: tap) {
            if compact {
                ZStack {
                    if let record, record.state == .downloading || record.state == .queued {
                        Circle().stroke(palette.border, lineWidth: 2.5)
                        Circle().trim(from: 0, to: max(0.02, record.progress)).stroke(palette.accent, style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                    }
                    Image(systemName: icon).font(.system(size: record?.state == .downloading ? 12 : 18, weight: .semibold))
                        .foregroundStyle(record?.state == .completed ? palette.accent2 : (record?.state == .failed ? .red : palette.text))
                }
                .frame(width: 30, height: 30)
            } else {
                Label(label, systemImage: icon)
            }
        }
        .buttonStyle(NovaButtonStyle(prominent: false, compact: compact))
        .accessibilityLabel(compact ? "\(label) · \(item.subtitle ?? item.title)" : label)
        .confirmationDialog(item.subtitle ?? item.title, isPresented: $showChoices, titleVisibility: .visible) {
            if let record {
                switch record.state {
                case .paused: Button("Reprendre") { downloads.resume(record) }
                case .downloading, .queued: Button("Mettre en pause") { downloads.pause(record) }
                default: EmptyView()
                }
                Button(record.state == .completed ? "Supprimer le téléchargement" : "Annuler le téléchargement", role: .destructive) {
                    downloads.delete(record)
                }
            }
        }
        .alert("Téléchargement impossible", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(error ?? "")
        }
    }

    private var icon: String {
        switch record?.state {
        case nil: return "arrow.down.circle"
        case .completed: return "checkmark.circle.fill"
        case .failed: return "exclamationmark.circle"
        case .paused: return "pause.circle"
        case .queued: return "clock"
        case .downloading: return "stop.fill"
        }
    }

    private var label: String {
        guard let record else { return "Télécharger" }
        switch record.state {
        case .queued: return downloads.isWaitingForWifi ? "En attente du Wi-Fi" : "En file d'attente"
        case .downloading: return "Téléchargement · \(Int(record.progress * 100)) %"
        case .paused: return "En pause · \(Int(record.progress * 100)) %"
        case .completed: return "Téléchargé"
        case .failed: return "Échec · Réessayer"
        }
    }

    private func tap() {
        if record == nil || record?.state == .failed {
            do { try downloads.download([(item, season, episode)], profile: profile) } catch { self.error = error.userMessage }
        } else {
            showChoices = true
        }
    }
}
