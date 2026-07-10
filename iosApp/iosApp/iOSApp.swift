import SwiftUI
import UserNotifications

// App entry — the iOS Hermes Life Agent client at feature parity with Android,
// built as SwiftUI over the shared Kotlin Multiplatform module (`Shared`).
@main
struct iOSApp: App {
    @StateObject private var env = AppEnvironment()
    // Appearance preference (dark "Hermes Teal" is the default), persisted.
    @AppStorage("theme_mode") private var themeModeRaw = ThemeMode.dark.rawValue

    init() {
        // Ask for local-notification permission up front so reminders can fire.
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    private var themeMode: ThemeMode { ThemeMode(rawValue: themeModeRaw) ?? .dark }

    var body: some Scene {
        WindowGroup {
            ThemedRoot(themeMode: themeMode)
                .environmentObject(env)
        }
    }
}

/// Resolves the active theme (respecting System) and paints the whole window with
/// its background so every screen is self-consistent, then hosts the root gate.
private struct ThemedRoot: View {
    let themeMode: ThemeMode
    @Environment(\.colorScheme) private var systemScheme

    var body: some View {
        let theme = HermesTheme.make(themeMode, system: systemScheme)
        RootView()
            .environment(\.theme, theme)
            .tint(theme.primary)
            .background(theme.background.ignoresSafeArea())
            .preferredColorScheme(themeMode == .system ? nil : (theme.isDark ? .dark : .light))
    }
}
