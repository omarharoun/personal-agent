// DashboardView.swift — the Life-OS home (Android DashboardScreen): a warm
// time-based greeting + four cards (Goals / Tasks / Memos / Reminders) that paint
// instantly from local/cached state, plus a Chat button.

import SwiftUI
import Shared

struct DashboardNav {
    var onOpenDrawer: () -> Void = {}
    var onChat: () -> Void = {}
    var onReminders: () -> Void = {}
    var onGoals: () -> Void = {}
    var onReflection: () -> Void = {}
    var onNotes: () -> Void = {}
    var onTasks: () -> Void = {}
    var onRunTask: () -> Void = {}
    var onSkills: () -> Void = {}
}

struct DashboardView: View {
    @ObservedObject var model: DashboardModel
    @Environment(\.theme) private var theme
    var nav: DashboardNav

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    greeting
                    goalsCard
                    tasksCard
                    memosCard
                    remindersCard
                }
                .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 96)
            }
        }
        .background(theme.background.ignoresSafeArea())
        .overlay(alignment: .bottomTrailing) {
            Button(action: nav.onChat) {
                HStack(spacing: 8) { Image(systemName: "arrow.right"); Text("Chat").fontWeight(.semibold) }
                    .foregroundColor(theme.onPrimary)
                    .padding(.horizontal, 20).padding(.vertical, 14)
                    .background(theme.primary).clipShape(Capsule())
            }
            .padding(.trailing, 20).padding(.bottom, 28)
        }
    }

    private var topBar: some View {
        HStack {
            Button(action: nav.onOpenDrawer) { Image(systemName: "line.3.horizontal").foregroundColor(theme.onSurface) }
            Spacer()
            HStack(spacing: 6) {
                Circle().fill(model.connected ? theme.tertiary : theme.onSurfaceVariant).frame(width: 7, height: 7)
                Text(model.connected ? "Connected" : "Connecting…").hermesMono(size: 11.5)
                    .foregroundColor(theme.onSurfaceVariant)
            }
            Spacer()
            Menu {
                Button("Run a task", action: nav.onRunTask)
                Button("Skills", action: nav.onSkills)
                Button("Refresh") { model.refresh(force: true) }
            } label: { Image(systemName: "ellipsis").foregroundColor(theme.onSurface) }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(theme.surface)
    }

    private var greeting: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(greetingLine).font(.title.weight(.semibold)).foregroundColor(theme.onBackground)
            Text(dateLine).font(.callout).foregroundColor(theme.onSurfaceVariant)
        }
        .padding(.top, 8)
    }

    private var greetingLine: String {
        let h = Calendar.current.component(.hour, from: Date())
        let part = h < 12 ? "morning" : (h < 18 ? "afternoon" : "evening")
        if let n = model.name, !n.isEmpty { return "Good \(part), \(n)." }
        return "Good \(part)."
    }
    private var dateLine: String {
        let f = DateFormatter(); f.dateFormat = "EEEE, MMMM d"; return f.string(from: Date())
    }

    // MARK: cards

    private var goalsCard: some View {
        LifeCard(emoji: "🎯", title: "Goals", accent: theme.tertiary,
                 refreshing: model.goalsRefreshing, onTap: nav.onGoals) {
            if model.goalsLoading && model.goals.isEmpty {
                HStack(spacing: 8) { ProgressView().controlSize(.small); Text("Loading…").foregroundColor(theme.onSurfaceVariant) }
            } else if model.goals.isEmpty {
                Text("No goals yet — tap to set what “better” looks like for you.")
                    .font(.callout).foregroundColor(theme.onSurfaceVariant)
            } else {
                ForEach(Array(model.goals.prefix(3).enumerated()), id: \.offset) { _, g in
                    HStack(alignment: .top, spacing: 8) {
                        Circle().fill(theme.tertiary).frame(width: 5, height: 5).padding(.top, 7)
                        Text(g).font(.callout).foregroundColor(theme.onSurface).lineLimit(2)
                    }
                }
            }
        }
    }

    private var tasksCard: some View {
        LifeCard(emoji: "✅", title: "Tasks", accent: theme.primary, onTap: nav.onTasks) {
            if model.tasks.isEmpty {
                Text("All clear — tap to add a to-do.").font(.callout).foregroundColor(theme.onSurfaceVariant)
            } else {
                ForEach(Array(model.tasks.prefix(3).enumerated()), id: \.offset) { _, t in
                    Button { model.toggleTask(t.id, !t.done) } label: {
                        HStack(spacing: 8) {
                            Image(systemName: t.done ? "checkmark.circle.fill" : "circle")
                                .foregroundColor(t.done ? theme.primary : theme.onSurfaceVariant)
                            Text(t.text).font(.callout).foregroundColor(theme.onSurface)
                                .strikethrough(t.done).lineLimit(1)
                        }
                    }.buttonStyle(.plain)
                }
                if model.tasks.count > 3 {
                    Text("+\(model.tasks.count - 3) more").font(.footnote).foregroundColor(theme.onSurfaceVariant)
                }
            }
        }
    }

    private var memosCard: some View {
        LifeCard(emoji: "📝", title: "Memos", accent: theme.tertiary, onTap: nav.onNotes) {
            if model.memos.isEmpty {
                Text("Nothing saved yet — tap to jot something your agent should remember.")
                    .font(.callout).foregroundColor(theme.onSurfaceVariant)
            } else {
                ForEach(Array(model.memos.prefix(3).enumerated()), id: \.offset) { _, m in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(m.text).font(.callout).foregroundColor(theme.onSurface).lineLimit(2)
                        Text(Self.stamp(m.savedAt)).font(.caption2).foregroundColor(theme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    private var remindersCard: some View {
        LifeCard(emoji: "⏰", title: "Reminders", accent: theme.primary, onTap: nav.onReminders) {
            if model.reminders.isEmpty {
                Text("Nothing upcoming — tap to set a reminder.")
                    .font(.callout).foregroundColor(theme.onSurfaceVariant)
            } else {
                ForEach(Array(model.reminders.prefix(3).enumerated()), id: \.offset) { _, r in
                    let due = model.statusName(r) == "DUE_NOW"
                    HStack(spacing: 8) {
                        Circle().fill(due ? theme.primary : theme.tertiary).frame(width: 6, height: 6)
                        Text(r.text).font(.callout).foregroundColor(theme.onSurface).lineLimit(1)
                        Spacer()
                        Text(due ? "Due now" : (r.whenMillis != nil ? Self.stamp(r.whenMillis!.int64Value) : "scheduled"))
                            .font(.caption2).foregroundColor(theme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    static func stamp(_ millis: Int64) -> String {
        let f = DateFormatter(); f.dateFormat = "EEE d MMM, HH:mm"
        return f.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
    }
}

private struct LifeCard<Content: View>: View {
    let emoji: String
    let title: String
    let accent: Color
    var refreshing: Bool = false
    var onTap: () -> Void
    @ViewBuilder var content: () -> Content
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(emoji).font(.system(size: 18))
                    Text(title.uppercased()).hermesDisplayLabel().foregroundColor(theme.onSurface)
                    Spacer()
                    if refreshing { ProgressView().controlSize(.mini) }
                }
                content()
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(theme.surface)
            .clipShape(RoundedCornerShape(16))
            .overlay(RoundedCornerShape(16).stroke(theme.outline, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}
