import SwiftUI

/// Claude-like palette for the SwiftUI app — warm cream background, warm
/// near-black text, a single coral accent used sparingly for the primary action.
/// Plain and decluttered. Colors adapt to light/dark.
extension Color {
    /// Warm cream/ivory background (light) / warm near-black (dark).
    static let paBackground = Color(light: 0xF0EEE6, dark: 0x1F1E1D)
    /// Raised surface (cards/input): lighter cream (light) / warm charcoal (dark).
    static let paSurface = Color(light: 0xFAF9F5, dark: 0x262624)
    /// Warm near-black text (light) / warm off-white (dark).
    static let paText = Color(light: 0x1F1E1D, dark: 0xF0EEE6)
    /// Muted secondary text.
    static let paTextMuted = Color(light: 0x6B6A65, dark: 0xAFADA4)
    /// Subtle warm border/outline.
    static let paBorder = Color(light: 0xE4E2D9, dark: 0x3A3A37)
    /// Coral/clay accent — primary action only.
    static let paAccent = Color(light: 0xD97757, dark: 0xE0896B)
    /// Text/icon on the coral accent.
    static let paOnAccent = Color.white

    /// Build a dynamic Color from two hex values (0xRRGGBB) for light/dark.
    init(light: UInt32, dark: UInt32) {
        self = Color(UIColor { traits in
            traits.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light)
        })
    }

    init(hex: UInt32) { self = Color(UIColor(hex: hex)) }
}

private extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255.0,
            green: CGFloat((hex >> 8) & 0xFF) / 255.0,
            blue: CGFloat(hex & 0xFF) / 255.0,
            alpha: 1.0
        )
    }
}

extension View {
    /// Fill the screen behind content with the warm "bone" background and tint the
    /// app's accent to Claude coral. Apply at the root of a screen.
    func paScreenBackground() -> some View {
        self
            .background(Color.paBackground.ignoresSafeArea())
            .tint(.paAccent)
    }
}
