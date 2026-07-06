import SwiftUI
import UserNotifications

// App entry — the iOS Hermes Life Agent client (Connect + streaming Chat),
// mirroring the shipped Android app. The legacy on-device stack (Notes/Reminders/
// Plan over on-device ML) is retired on iOS just as it was on Android; those files
// remain in the repo but are no longer part of the build target (see project.yml).
@main
struct iOSApp: App {
    init() {
        // Ask for local-notification permission up front so reminders can fire.
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    var body: some Scene {
        WindowGroup {
            HermesLifeAgentView()
        }
    }
}
