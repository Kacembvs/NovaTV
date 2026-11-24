import SwiftData
import SwiftUI

struct PosterImage: View {
    let url: String?
    let title: String
    var aspect: CGFloat = 2.0 / 3.0
    @Environment(\.palette) private var palette

    var body: some View {
        Color.clear
            .aspectRatio(aspect, contentMode: .fit)
            .overlay {
                AsyncImage(url: url.flatMap(URL.init(string:))) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    } else {
                        ZStack {
                            palette.background2
                            Text(String(title.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased())
                                .font(.title.bold())
                                .foregroundStyle(palette.textSecondary)
                        }
                    }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

struct PosterCard: View {
    let title: String
    let image: String?
    var subtitle: String? = nil
    var progress: Double? = nil
    var width: CGFloat? = 118
    var aspect: CGFloat = 2.0 / 3.0
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            PosterImage(url: image, title: title, aspect: aspect)
                .overlay(alignment: .bottom) {
                    if let progress, progress > 0 {
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Rectangle().fill(.black.opacity(0.5))
                                Rectangle().fill(palette.accent).frame(width: geo.size.width * progress)
                            }
                        }
                        .frame(height: 3)
                    }
                }
            Text(title).font(.caption.weight(.semibold)).foregroundStyle(palette.text).lineLimit(2)
            if let subtitle { Text(subtitle).font(.caption2).foregroundStyle(palette.textSecondary).lineLimit(1) }
        }
        .frame(width: width)
        .contentShape(Rectangle())
    }
}

struct SectionHeader: View {
    let title: String
    var action: (() -> Void)? = nil
    @Environment(\.palette) private var palette

    var body: some View {
        HStack {
            Text(title).font(.title3.bold()).foregroundStyle(palette.text)
            Spacer()
            if let action {
                Button("Tout voir", action: action).font(.subheadline)
            }
        }
        .padding(.horizontal)
    }
}

struct EmptyStateView: View {
    let systemImage: String
    let title: String
    let message: String
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        ContentUnavailableView {
            Label(title, systemImage: systemImage)
        } description: {
            Text(message)
        } actions: {
            if let actionTitle, let action {
                Button(actionTitle, action: action).buttonStyle(.borderedProminent)
            }
        }
    }
}

struct FavoriteButton: View {
    let profileID: UUID
    let item: PlayableItem
    @Environment(\.modelContext) private var context
    @Query private var matches: [Favorite]

    init(profileID: UUID, item: PlayableItem) {
        self.profileID = profileID
        self.item = item
        let key = "\(profileID.uuidString)|\(item.ref)"
        _matches = Query(filter: #Predicate<Favorite> { $0.key == key })
    }

    var body: some View {
        let isFavorite = !matches.isEmpty
        Button {
            context.toggleFavorite(profileID: profileID, item: item)
        } label: {
            Label(isFavorite ? "Dans ma liste" : "Ma liste", systemImage: isFavorite ? "checkmark" : "plus")
        }
        .buttonStyle(.bordered)
        .accessibilityLabel(isFavorite ? "Retirer de ma liste" : "Ajouter à ma liste")
    }
}

/// Asks for the parental PIN; `verify` returns an error message or nil when accepted.
struct PinSheet: View {
    let title: String
    let message: String?
    let verify: (String) -> String?
    @Environment(\.dismiss) private var dismiss
    @State private var pin = ""
    @State private var error: String?
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            Form {
                if let message { Text(message).foregroundStyle(.secondary) }
                SecureField("Code PIN (4 chiffres)", text: $pin)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .focused($focused)
                    .onChange(of: pin) { _, value in
                        pin = String(value.filter(\.isNumber).prefix(4))
                        if pin.count == 4 { submit() }
                    }
                if let error { Text(error).foregroundStyle(.red) }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Valider", action: submit).disabled(pin.count != 4) }
            }
            .onAppear { focused = true }
        }
        .presentationDetents([.medium])
    }

    private func submit() {
        if let message = verify(pin) {
            error = message
            pin = ""
        } else {
            dismiss()
        }
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

struct IdentifiedURL: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

extension Int64 {
    var formattedBytes: String { ByteCountFormatter.string(fromByteCount: self, countStyle: .file) }
}

extension Double {
    /// "1:52:00" or "12:34".
    var clock: String {
        guard isFinite, self > 0 else { return "0:00" }
        let total = Int(self)
        let h = total / 3600, m = (total % 3600) / 60, s = total % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%d:%02d", m, s)
    }
}
