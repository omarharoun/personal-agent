import Foundation
import UserNotifications
import Shared

/// iOS implementation of the shared `ReminderScheduler` protocol (generated from
/// the Kotlin interface). Turns a `Reminder` into a real local notification via
/// UNUserNotificationCenter — this is what makes a reminder actually fire.
///
/// Kept in Swift (not Kotlin) so it uses the native UserNotifications APIs
/// directly, matching the brief's "native UI/notifications per platform".
final class IosReminderScheduler: ReminderScheduler {

    private let center = UNUserNotificationCenter.current()

    func schedule(reminder: Reminder) {
        let content = UNMutableNotificationContent()
        content.title = reminder.title
        content.body = reminder.note
        content.sound = .default

        // triggerAtMillis is epoch millis; convert to an interval from now.
        let fireDate = Date(timeIntervalSince1970: Double(reminder.triggerAtMillis) / 1000.0)
        let interval = max(1.0, fireDate.timeIntervalSinceNow)
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false)

        let request = UNNotificationRequest(
            identifier: reminder.id,
            content: content,
            trigger: trigger
        )
        center.add(request)
    }

    func cancel(reminderId: String) {
        center.removePendingNotificationRequests(withIdentifiers: [reminderId])
    }
}
