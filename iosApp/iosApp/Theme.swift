// Theme.swift — NEUTRAL base + user-chosen ACCENT (mirrors the Android theme).
// The canvas is neutral (warm charcoal in dark, warm paper in light — NOT green);
// the accent the user picks in Settings (from the shared AccentPalette) recolors
// primary / active / highlight roles in BOTH modes. Nothing is force-green anymore.
//
// Design tokens adapted from the MIT-licensed Hermes Agent web frontend
// (© 2025 Nous Research) — see ATTRIBUTION.md.

import SwiftUI
import Shared

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

// MARK: - Color(hex:) + blend

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

    /// Mix two RGB hexes by ratio r (0 = a, 1 = b) — for accent-tinted surfaces.
    static func blend(_ a: UInt32, _ b: UInt32, _ r: Double) -> Color {
        func mix(_ shift: UInt32) -> Double {
            let av = Double((a >> shift) & 0xFF)
            let bv = Double((b >> shift) & 0xFF)
            return (av + (bv - av) * r) / 255.0
        }
        return Color(.sRGB, red: mix(16), green: mix(8), blue: mix(0), opacity: 1)
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

    // Neutral base hexes (no green identity), matching Android's Color.kt.
    private static let darkBg: UInt32 = 0x121415
    private static let darkSurface: UInt32 = 0x1B1E20
    private static let darkMuted: UInt32 = 0x212528
    private static let darkSecondary: UInt32 = 0x23272A
    private static let lightMuted: UInt32 = 0xECE7DD

    /// Build the active theme from the mode + the user's accent (from Shared).
    static func make(_ mode: ThemeMode, system: ColorScheme, accent: AccentOption) -> HermesTheme {
        let isDark = (mode == .dark) || (mode == .system && system == .dark)
        return isDark ? dark(accent) : light(accent)
    }

    private static func dark(_ accent: AccentOption) -> HermesTheme {
        let a = UInt32(truncatingIfNeeded: accent.darkRgb) & 0xFFFFFF
        let onA = UInt32(truncatingIfNeeded: accent.onDarkRgb) & 0xFFFFFF
        return HermesTheme(
            isDark: true,
            background: Color(hex: darkBg),
            onBackground: Color(hex: 0xECEAE4),
            surface: Color(hex: darkSurface),
            onSurface: Color(hex: 0xECEAE4),
            surfaceVariant: Color(hex: darkMuted),
            onSurfaceVariant: Color(hex: 0xA9ADAB),
            secondaryContainer: Color.blend(darkSecondary, a, 0.26),
            onSecondaryContainer: Color(hex: 0xECEAE4),
            primary: Color(hex: a),
            onPrimary: Color(hex: onA),
            tertiary: Color(hex: a),
            onTertiary: Color(hex: onA),
            error: Color(hex: 0xFB2C36),
            onError: .white,
            errorContainer: Color(hex: 0xFB2C36).opacity(0.15),
            onErrorContainer: Color(hex: 0xFB2C36),
            outline: Color(hex: 0x35393C)
        )
    }

    private static func light(_ accent: AccentOption) -> HermesTheme {
        let a = UInt32(truncatingIfNeeded: accent.lightRgb) & 0xFFFFFF
        let onA = UInt32(truncatingIfNeeded: accent.onLightRgb) & 0xFFFFFF
        return HermesTheme(
            isDark: false,
            background: Color(hex: 0xF6F3EC),
            onBackground: Color(hex: 0x1D1F20),
            surface: Color(hex: 0xFFFFFF),
            onSurface: Color(hex: 0x1D1F20),
            surfaceVariant: Color(hex: lightMuted),
            onSurfaceVariant: Color(hex: 0x5D605F),
            secondaryContainer: Color.blend(lightMuted, a, 0.14),
            onSecondaryContainer: Color(hex: 0x1D1F20),
            primary: Color(hex: a),
            onPrimary: Color(hex: onA),
            tertiary: Color(hex: a),
            onTertiary: Color(hex: onA),
            error: Color(hex: 0xFB2C36),
            onError: .white,
            errorContainer: Color(hex: 0xFB2C36).opacity(0.12),
            onErrorContainer: Color(hex: 0xFB2C36),
            outline: Color(hex: 0xDED8CC)
        )
    }

    /// A neutral default theme for the environment default value (before the accent
    /// loads from the store) — the shared default accent (Blue), inline so it needs
    /// no Kotlin-object access.
    static let dark = HermesTheme(
        isDark: true,
        background: Color(hex: darkBg),
        onBackground: Color(hex: 0xECEAE4),
        surface: Color(hex: darkSurface),
        onSurface: Color(hex: 0xECEAE4),
        surfaceVariant: Color(hex: darkMuted),
        onSurfaceVariant: Color(hex: 0xA9ADAB),
        secondaryContainer: Color.blend(darkSecondary, 0x60A5FA, 0.26),
        onSecondaryContainer: Color(hex: 0xECEAE4),
        primary: Color(hex: 0x60A5FA),
        onPrimary: Color(hex: 0x0A1A2F),
        tertiary: Color(hex: 0x60A5FA),
        onTertiary: Color(hex: 0x0A1A2F),
        error: Color(hex: 0xFB2C36),
        onError: .white,
        errorContainer: Color(hex: 0xFB2C36).opacity(0.15),
        onErrorContainer: Color(hex: 0xFB2C36),
        outline: Color(hex: 0x35393C)
    )
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
    func hermesDisplayLabel(size: CGFloat = 13) -> Text {
        self.font(.system(size: size, weight: .semibold))
            .tracking(2.2)
    }
}

extension View {
    func hermesMono(size: CGFloat = 12.5) -> some View {
        self.font(.system(size: size, design: .monospaced)).tracking(0.5)
    }
}
