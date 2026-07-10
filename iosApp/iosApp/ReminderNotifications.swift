// ReminderNotifications.swift — the iOS reminder-delivery path. Android schedules
// delivery with WorkManager + AlarmManager; iOS schedules a local
// UNUserNotificationCenter notification at each reminder's fire time (and a
// foreground poll of /api/jobs reuses the shared HermesReminderPoller logic).
//
// The notification's userInfo carries a deep-link ("reminders") so a tap opens the
// Reminders surface, mirroring Android's notification tap → EXTRA_OPEN.

import Foundation
import UserNotifications

enum ReminderNotifications {

    /// Schedule a one-shot local notification for [jobId] firing at [fireMillis].
    static func schedule(jobId: String, title: String, fireMillis: Int64) {
        let now = Date().timeIntervalSince1970 * 1000
        let delaySec = max(1, (Double(fireMillis) - now) / 1000)

        let content = UNMutableNotificationContent()
        content.title = "Reminder"
        content.body = title
        content.sound = .default
        content.userInfo = ["open": "reminders", "jobId": jobId]

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: delaySec, repeats: false)
        let request = UNNotificationRequest(identifier: "reminder-\(jobId)", content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(request)
    }

    /// Cancel a scheduled (not-yet-fired) reminder notification.
    static func cancel(jobId: String) {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["reminder-\(jobId)"])
    }
}
