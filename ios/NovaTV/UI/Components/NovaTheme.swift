import SwiftUI

/// Buttons drawn with the palette and the interface's corner radius. Text on the accent always
/// uses `onAccent`, so white NOVA+ buttons get black labels instead of white on white.
struct NovaButtonStyle: ButtonStyle {
    var prominent: Bool
    var compact = false
    var destructive = false

    func makeBody(configuration: Configuration) -> some View {
        NovaButtonBody(configuration: configuration, prominent: prominent, compact: compact, destructive: destructive)
    }
}

private struct NovaButtonBody: View {
    let configuration: ButtonStyleConfiguration
    let prominent: Bool
    let compact: Bool
    let destructive: Bool
    @Environment(\.palette) private var palette
    @Environment(\.novaStyle) private var style
    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: style.radius)
        configuration.label
            .font(.subheadline.weight(.semibold))
            .lineLimit(2)
            .foregroundStyle(prominent ? palette.onAccent : (destructive ? palette.error : palette.text))
            .padding(.horizontal, compact ? 10 : 16)
            .padding(.vertical, compact ? 8 : 12)
            .background(prominent ? palette.accent : palette.card, in: shape)
            .overlay(shape.stroke(prominent ? Color.clear : palette.border, lineWidth: 1))
            .contentShape(shape)
            .opacity(isEnabled ? (configuration.isPressed ? 0.7 : 1) : 0.4)
    }
}

extension ButtonStyle where Self == NovaButtonStyle {
    static var novaPrimary: NovaButtonStyle { NovaButtonStyle(prominent: true) }
    static var novaSecondary: NovaButtonStyle { NovaButtonStyle(prominent: false) }
    static var novaCompact: NovaButtonStyle { NovaButtonStyle(prominent: false, compact: true) }
    static var novaDestructive: NovaButtonStyle { NovaButtonStyle(prominent: false, destructive: true) }
}

/// Switch whose knob stays visible on every palette (dark knob on the white NOVA+ track).
struct NovaSwitchStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        NovaSwitch(configuration: configuration)
    }
}

private struct NovaSwitch: View {
    let configuration: ToggleStyleConfiguration
    @Environment(\.palette) private var palette
    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        Button {
            configuration.isOn.toggle()
        } label: {
            HStack(spacing: 12) {
                configuration.label
                    .frame(maxWidth: .infinity, alignment: .leading)
                ZStack(alignment: configuration.isOn ? .trailing : .leading) {
                    Capsule()
                        .fill(configuration.isOn ? palette.accent : palette.border)
                        .frame(width: 51, height: 31)
                    Circle()
                        .fill(configuration.isOn ? palette.onAccent : Color.white)
                        .shadow(color: .black.opacity(0.25), radius: 1, y: 1)
                        .frame(width: 27, height: 27)
                        .padding(2)
                }
                .animation(.easeInOut(duration: 0.15), value: configuration.isOn)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .opacity(isEnabled ? 1 : 0.45)
        .accessibilityAddTraits(.isToggle)
        .accessibilityValue(configuration.isOn ? "Activé" : "Désactivé")
    }
}

/// Category / filter chip.
struct NovaChip: View {
    let title: String
    var count: Int? = nil
    var icon: String? = nil
    let selected: Bool
    let action: () -> Void
    @Environment(\.palette) private var palette
    @Environment(\.novaStyle) private var style

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: min(style.radius, 18))
        Button(action: action) {
            HStack(spacing: 5) {
                if let icon { Image(systemName: icon) }
                Text(title).lineLimit(1)
                if let count {
                    Text("\(count)").font(.caption2).opacity(0.75)
                }
            }
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .foregroundStyle(selected ? palette.onAccent : palette.text)
            .background(selected ? palette.accent : palette.card, in: shape)
            .overlay(shape.stroke(selected ? Color.clear : palette.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

extension View {
    /// Card surface following the interface radius.
    func novaCard(padding: CGFloat = 14) -> some View {
        modifier(NovaCardModifier(padding: padding))
    }

    func novaClip() -> some View {
        modifier(NovaClipModifier())
    }
}

private struct NovaCardModifier: ViewModifier {
    let padding: CGFloat
    @Environment(\.palette) private var palette
    @Environment(\.novaStyle) private var style

    func body(content: Self.Content) -> some View {
        content
            .padding(padding)
            .background(palette.card, in: RoundedRectangle(cornerRadius: style.radius))
            .overlay(RoundedRectangle(cornerRadius: style.radius).stroke(palette.border, lineWidth: 1))
    }
}

private struct NovaClipModifier: ViewModifier {
    @Environment(\.novaStyle) private var style
    func body(content: Self.Content) -> some View {
        content.clipShape(RoundedRectangle(cornerRadius: style.radius))
    }
}

/// Text field drawn like the rest of NOVA (no rounded system form).
struct NovaField: View {
    let title: String
    @Binding var text: String
    var secure = false
    var keyboard: UIKeyboardType = .default
    @State private var reveal = false
    @Environment(\.palette) private var palette
    @Environment(\.novaStyle) private var style

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.caption.weight(.semibold)).foregroundStyle(palette.textSecondary)
            HStack {
                Group {
                    if secure && !reveal {
                        SecureField(title, text: $text)
                    } else {
                        TextField(title, text: $text)
                    }
                }
                .keyboardType(keyboard)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .foregroundStyle(palette.text)
                if secure {
                    Button { reveal.toggle() } label: {
                        Image(systemName: reveal ? "eye.slash" : "eye").foregroundStyle(palette.textSecondary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(reveal ? "Masquer le mot de passe" : "Afficher le mot de passe")
                }
            }
            .padding(12)
            .background(palette.background2, in: RoundedRectangle(cornerRadius: style.radius))
            .overlay(RoundedRectangle(cornerRadius: style.radius).stroke(palette.border, lineWidth: 1))
        }
    }
}
