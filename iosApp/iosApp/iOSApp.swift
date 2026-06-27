import SwiftUI
import UserNotifications

@main
struct iOSApp: App {
    @StateObject private var model = AppModel()

    init() {
        // Ask for local-notification permission up front so reminders can fire.
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(model)
                .task { await model.refresh() }
        }
    }
}
