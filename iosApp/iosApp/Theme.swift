// Theme.swift — the Hermes-Teal (dark) / warm-paper (light) palette, ported
// verbatim from the Android app's `ui/theme/Color.kt` + `Theme.kt` so the iOS
// Life Agent reads as the same product. Dark ("Hermes Teal") is the default.
//
// Design tokens adapted from the MIT-licensed Hermes Agent web frontend
// (© 2025 Nous Research) — see ATTRIBUTION.md.

import SwiftUI

// MARK: - Appearance preference (persisted; dark is the default)

enum ThemeMode: String, CaseIterable, Identifiable {
    case system, dark, light
    var id: String { rawValue }
    var label: String {
        switch self {
        case .system: return "System"
        case .dark: return "Dark"
        case .light: return "Light"
        }
    }
}

// MARK: - Color(hex:)

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: 1
        )
    }
}

// MARK: - Semantic theme (mirrors Material's colorScheme roles used by the app)

struct HermesTheme {
    let isDark: Bool

    let background: Color
    let onBackground: Color
    let surface: Color
    let onSurface: Color
    let surfaceVariant: Color
    let onSurfaceVariant: Color
    let secondaryContainer: Color
    let onSecondaryContainer: Color
    let primary: Color
    let onPrimary: Color
    let tertiary: Color
    let onTertiary: Color
    let error: Color
    let onError: Color
    let errorContainer: Color
    let onErrorContainer: Color
    let outline: Color

    static let dark = HermesTheme(
        isDark: true,
        background: Color(hex: 0x041C1C),
        onBackground: Color(hex: 0xFFE6CB),
        surface: Color(hex: 0x0E2423),
        onSurface: Color(hex: 0xFFE6CB),
        surfaceVariant: Color(hex: 0x182C2A),
        onSurfaceVariant: Color(hex: 0xCDBFA8),
        secondaryContainer: Color(hex: 0x1D302E),
        onSecondaryContainer: Color(hex: 0xFFE6CB),
        primary: Color(hex: 0xFFE6CB),
        onPrimary: Color(hex: 0x041C1C),
        tertiary: Color(hex: 0x34D399),
        onTertiary: Color(hex: 0x041C1C),
        error: Color(hex: 0xFB2C36),
        onError: .white,
        errorContainer: Color(hex: 0xFB2C36).opacity(0.15),
        onErrorContainer: Color(hex: 0xFB2C36),
        outline: Color(hex: 0x3C4C46)
    )

    static let light = HermesTheme(
        isDark: false,
        background: Color(hex: 0xF7F1E7),
        onBackground: Color(hex: 0x0A2422),
        surface: Color(hex: 0xFFFFFF),
        onSurface: Color(hex: 0x0A2422),
        surfaceVariant: Color(hex: 0xEFE7D7),
        onSurfaceVariant: Color(hex: 0x55635F),
        secondaryContainer: Color(hex: 0xEAF0EC),
        onSecondaryContainer: Color(hex: 0x0B3B37),
        primary: Color(hex: 0x0B3B37),
        onPrimary: Color(hex: 0xFFF3E4),
        tertiary: Color(hex: 0x0F9E74),
        onTertiary: Color(hex: 0xFFFFFF),
        error: Color(hex: 0xFB2C36),
        onError: .white,
        errorContainer: Color(hex: 0xFB2C36).opacity(0.12),
        onErrorContainer: Color(hex: 0xFB2C36),
        outline: Color(hex: 0xDDD2BF)
    )

    static func make(_ mode: ThemeMode, system: ColorScheme) -> HermesTheme {
        switch mode {
        case .dark: return .dark
        case .light: return .light
        case .system: return system == .dark ? .dark : .light
        }
    }
}

// MARK: - Environment plumbing

private struct HermesThemeKey: EnvironmentKey {
    static let defaultValue = HermesTheme.dark
}

extension EnvironmentValues {
    var theme: HermesTheme {
        get { self[HermesThemeKey.self] }
        set { self[HermesThemeKey.self] = newValue }
    }
}

// MARK: - Signature Hermes text treatments (uppercase, wide-tracked "display")

extension Text {
    /// UPPERCASE, wide letter-spacing, semibold — section headers + nav labels.
    func hermesDisplayLabel(size: CGFloat = 13) -> Text {
        self.font(.system(size: size, weight: .semibold))
            .tracking(2.2)
    }
}

extension View {
    /// Tabular monospace for technical/metadata readouts (versions, counts).
    func hermesMono(size: CGFloat = 12.5) -> some View {
        self.font(.system(size: size, design: .monospaced)).tracking(0.5)
    }
}
