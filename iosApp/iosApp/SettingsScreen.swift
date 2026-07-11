// SettingsScreen.swift — Hermes connection, Appearance, About (Android SettingsScreen).
// No first-name field (matches the latest Android). Disconnect forgets the backend
// but keeps the memory-scope key so reconnecting lands in the same memory.
// (Named *Screen to avoid the retired legacy SettingsView.swift, which is excluded.)

import SwiftUI
import Shared

struct SettingsView: View {
    @EnvironmentObject var env: AppEnvironment
    @Environment(\.theme) private var theme
    @AppStorage("theme_mode") private var themeModeRaw = ThemeMode.dark.rawValue

    private var baseURL: String { env.configStore.load()?.baseUrl ?? "Not connected" }
    private var version: String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return "v\(v) (build \(b))"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                section("Your Hermes") {
                    Text(baseURL).font(.callout).foregroundColor(theme.onSurfaceVariant)
                    Text("Your conversations, notes, and reminders live on this server. This app stores no copy of them.")
                        .font(.caption).foregroundColor(theme.onSurfaceVariant).padding(.top, 6)
                    Button("Change / disconnect") { env.disconnect() }
                        .font(.callout).foregroundColor(theme.primary)
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .overlay(RoundedCornerShape(8).stroke(theme.outline, lineWidth: 1))
                        .padding(.top, 10)
                }

                Divider().background(theme.outline)

                section("Appearance") {
                    Text("Mode").font(.caption).foregroundColor(theme.onSurfaceVariant).padding(.top, 2)
                    HStack(spacing: 8) {
                        ForEach(ThemeMode.allCases) { mode in
                            Button { themeModeRaw = mode.rawValue } label: {
                                Text(mode.label).font(.footnote)
                                    .padding(.horizontal, 14).padding(.vertical, 8)
                                    .foregroundColor(themeModeRaw == mode.rawValue ? theme.onPrimary : theme.onSurface)
                                    .background(themeModeRaw == mode.rawValue ? theme.primary : theme.surfaceVariant)
                                    .clipShape(Capsule())
                            }
                        }
                    }

                    Text("Accent color").font(.caption).foregroundColor(theme.onSurfaceVariant).padding(.top, 12)
                    Text("Applies to highlights and active states, in both light and dark.")
                        .font(.caption2).foregroundColor(theme.onSurfaceVariant)
                    AccentSwatches()
                }

                Divider().background(theme.outline)

                section("About") {
                    Text("Life Agent — \(version)").font(.callout).foregroundColor(theme.onSurfaceVariant)
                }
            }
            .padding(16)
        }
    }

    @ViewBuilder private func section<V: View>(_ title: String, @ViewBuilder _ content: () -> V) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.headline).foregroundColor(theme.onBackground)
            content()
        }
    }
}

/// Tappable accent-color swatches (shared curated list). Selecting one re-themes
/// the whole app live via `env.setAccent`.
private struct AccentSwatches: View {
    @EnvironmentObject var env: AppEnvironment
    @Environment(\.theme) private var theme

    private let columns = [GridItem(.adaptive(minimum: 44), spacing: 14)]

    var body: some View {
        LazyVGrid(columns: columns, alignment: .leading, spacing: 14) {
            ForEach(LifeAgentIos.shared.accentOptions(), id: \.id) { opt in
                let rgb = UInt32(truncatingIfNeeded: theme.isDark ? opt.darkRgb : opt.lightRgb) & 0xFFFFFF
                let onRgb = UInt32(truncatingIfNeeded: theme.isDark ? opt.onDarkRgb : opt.onLightRgb) & 0xFFFFFF
                let selected = opt.id == env.accentId
                Button { env.setAccent(opt.id) } label: {
                    Circle()
                        .fill(Color(hex: rgb))
                        .frame(width: 40, height: 40)
                        .overlay(
                            Circle().stroke(selected ? theme.onBackground : theme.outline,
                                            lineWidth: selected ? 3 : 1)
                        )
                        .overlay(
                            Group {
                                if selected {
                                    Circle().fill(Color(hex: onRgb)).frame(width: 10, height: 10)
                                }
                            }
                        )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(opt.name + (selected ? ", selected" : ""))
            }
        }
        .padding(.top, 8)
    }
}
