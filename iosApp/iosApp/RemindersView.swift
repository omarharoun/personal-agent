// RemindersView.swift — reminders backed by the user's Hermes (/api/jobs) with a
// local history (Android RemindersScreen). Create with preset durations, list with
// status badges, dismiss. iOS delivery is a local notification scheduled at the
// fire time (ReminderNotifications), replacing Android's WorkManager path.

import SwiftUI
import Shared

@MainActor
final class RemindersModel: ObservableObject {
    @Published var reminders: [ReminderView] = []
    @Published var loading = true
    @Published var error: String?
    @Published var message: String?

    private let env: AppEnvironment
    private let client: HermesClient?
    init(env: AppEnvironment) { self.env = env; self.client = env.makeClient(); refresh() }

    func statusName(_ v: ReminderView) -> String { LifeAgentIos.shared.reminderStatusName(view: v) }

    func refresh() {
        loading = true; error = nil
        _Concurrency.Task {
            let ios = LifeAgentIos.shared
            let now = ios.nowMillis()
            guard let client else {
                reminders = ios.mergeReminders(liveJobs: [], history: env.reminderHistory.all(), nowMillis: now)
                loading = false; return
            }
            do {
                let live = try await client.listJobs()
                for j in live {
                    env.reminderHistory.upsert(record: ios.reminderRecord(id: j.id, text: j.label,
                                                                          targetMillis: j.nextRunAtMillis?.int64Value ?? now))
                }
                reminders = ios.mergeReminders(liveJobs: live, history: env.reminderHistory.all(), nowMillis: now)
                error = nil
            } catch {
                reminders = ios.mergeReminders(liveJobs: [], history: env.reminderHistory.all(), nowMillis: now)
                self.error = reminders.isEmpty ? (hermesMessage(error) ?? "Couldn't reach Hermes.") : nil
            }
            loading = false
        }
    }

    func create(_ title: String, minutes: Int64) {
        let text = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { message = "Enter what to be reminded about."; return }
        guard let client else { return }
        let ios = LifeAgentIos.shared
        let now = ios.nowMillis()
        let target = now + minutes * 60_000
        _Concurrency.Task {
            do {
                let job = try await client.createJob(name: text,
                                                     schedule: ios.scheduleForMinutes(nowMillis: now, targetMillis: target),
                                                     prompt: "Remind the user: \(text)")
                let fire = job.nextRunAtMillis?.int64Value ?? target
                env.reminderHistory.upsert(record: ios.reminderRecord(id: job.id, text: text, targetMillis: fire))
                ReminderNotifications.schedule(jobId: job.id, title: text, fireMillis: fire)
                message = "Reminder set"
                refresh()
            } catch {
                message = hermesMessage(error) ?? "Couldn't set the reminder."
            }
        }
    }

    func dismiss(_ view: ReminderView) {
        _Concurrency.Task {
            if view.live, let client { _ = try? await client.deleteJob(id: view.id) }
            ReminderNotifications.cancel(jobId: view.id)
            env.reminderHistory.remove(id: view.id)
            refresh()
        }
    }
}

struct RemindersView: View {
    @StateObject private var model: RemindersModel
    @Environment(\.theme) private var theme
    @State private var title = ""
    @State private var minutes: Int64 = 60

    init(env: AppEnvironment) { _model = StateObject(wrappedValue: RemindersModel(env: env)) }

    private let presets: [(String, Int64)] = [
        ("1 min", 1), ("1 hour", 60), ("3 hours", 180), ("Tomorrow", 1440), ("Next week", 10080),
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                TextField("Remind me to…", text: $title)
                    .foregroundColor(theme.onSurface)
                    .padding(12).background(theme.surfaceVariant).clipShape(RoundedCornerShape(8))
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(presets, id: \.0) { p in
                            Button { minutes = p.1 } label: {
                                Text(p.0).font(.footnote)
                                    .padding(.horizontal, 12).padding(.vertical, 8)
                                    .foregroundColor(minutes == p.1 ? theme.onPrimary : theme.onSurface)
                                    .background(minutes == p.1 ? theme.primary : theme.surfaceVariant)
                                    .clipShape(Capsule())
                            }
                        }
                    }
                }
                Button("Set reminder") { model.create(title, minutes: minutes); title = "" }
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                    .foregroundColor(theme.onPrimary).background(theme.primary).clipShape(RoundedCornerShape(8))
                    .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty)

                if let msg = model.message { Text(msg).font(.footnote).foregroundColor(theme.onSurfaceVariant) }
                if let err = model.error { Text(err).font(.footnote).foregroundColor(theme.error) }

                if model.loading && model.reminders.isEmpty {
                    ProgressView().frame(maxWidth: .infinity).padding()
                } else if model.reminders.isEmpty {
                    Text("No reminders yet.").font(.callout).foregroundColor(theme.onSurfaceVariant)
                } else {
                    ForEach(model.reminders, id: \.id) { r in row(r) }
                }
            }
            .padding(16)
        }
    }

    private func row(_ r: ReminderView) -> some View {
        let status = model.statusName(r)
        let (badge, color): (String, Color) = {
            switch status {
            case "DUE_NOW": return ("DUE NOW", theme.primary)
            case "DONE": return ("DONE", theme.onSurfaceVariant)
            default: return ("UPCOMING", theme.tertiary)
            }
        }()
        return HStack(alignment: .top, spacing: 10) {
            VStack(alignment: .leading, spacing: 6) {
                Text(badge).font(.system(size: 10, weight: .semibold)).tracking(1.5)
                    .padding(.horizontal, 7).padding(.vertical, 3)
                    .foregroundColor(color)
                    .overlay(Capsule().stroke(color.opacity(0.5), lineWidth: 1))
                Text(r.text).font(.callout).foregroundColor(theme.onSurface)
                if let w = r.whenMillis { Text(DashboardView.stamp(w.int64Value)).font(.caption2).foregroundColor(theme.onSurfaceVariant) }
            }
            Spacer()
            Button { model.dismiss(r) } label: { Image(systemName: "xmark").foregroundColor(theme.onSurfaceVariant) }
        }
        .padding(12).background(theme.surface).clipShape(RoundedCornerShape(12))
        .overlay(RoundedCornerShape(12).stroke(theme.outline, lineWidth: 1))
    }
}
