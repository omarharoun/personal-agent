// AppShell.swift — the connected-state app shell: a slide-out navigation drawer
// (New chat · recent history · Dashboard / History / Knowledge / Goals / Tasks /
// Memos / Reminders / Reflection / Skills / Support / Settings) hosting one surface
// at a time. Mirrors Android's AppScreen ModalNavigationDrawer.

import SwiftUI
import Shared

enum Surface: Hashable {
    case dashboard, conversation, history, knowledge, settings, support
    case notes, reminders, goals, learning, reflection, tasks, runTask, skills
}

struct AppShell: View {
    @EnvironmentObject var env: AppEnvironment
    @Environment(\.theme) private var theme

    @StateObject private var chat: ChatModel
    @StateObject private var dashboard: DashboardModel

    @State private var surface: Surface = .dashboard
    @State private var drawerOpen = false

    init(env: AppEnvironment) {
        _chat = StateObject(wrappedValue: ChatModel(env: env))
        _dashboard = StateObject(wrappedValue: DashboardModel(env: env))
    }

    var body: some View {
        ZStack(alignment: .leading) {
            content
                .disabled(drawerOpen)

            if drawerOpen {
                Color.black.opacity(0.4).ignoresSafeArea()
                    .onTapGesture { closeDrawer() }
                    .transition(.opacity)
                drawer
                    .frame(maxWidth: 300)
                    .transition(.move(edge: .leading))
            }
        }
        .animation(.easeInOut(duration: 0.22), value: drawerOpen)
    }

    // MARK: routing

    @ViewBuilder private var content: some View {
        switch surface {
        case .dashboard:
            DashboardView(model: dashboard, nav: DashboardNav(
                onOpenDrawer: openDrawer,
                onChat: { surface = .conversation },
                onReminders: { surface = .reminders },
                onGoals: { surface = .goals },
                onReflection: { surface = .reflection },
                onNotes: { surface = .notes },
                onTasks: { surface = .tasks },
                onRunTask: { surface = .runTask },
                onSkills: { surface = .skills }))
        case .conversation:
            ChatView(model: chat, onOpenDrawer: openDrawer)
        case .history:
            sub("History") { HistoryView(model: chat) { id in chat.selectChat(id); surface = .conversation } }
        case .notes:
            sub("Memos") { NotesView(env: env) }
        case .tasks:
            sub("Tasks") { TasksView(env: env) }
        case .reminders:
            sub("Reminders") { RemindersView(env: env) }
        case .goals:
            sub("Goals") { GoalsView(env: env, onOpenLearning: { surface = .learning }) }
        case .learning:
            sub("Learning") { LearningView(env: env) }
        case .reflection:
            sub("Reflection") { ReflectionView(env: env) }
        case .settings:
            sub("Settings") { SettingsView() }
        case .knowledge:
            sub("Knowledge") { KnowledgeView(env: env) }
        case .skills:
            sub("Skills") { SkillsView(env: env) }
        case .support:
            sub("Support") { SupportView(env: env) }
        case .runTask:
            sub("Run a task") { RunTaskView(env: env) }
        }
    }

    private var placeholderTitle: String {
        switch surface {
        case .knowledge: return "Knowledge"
        case .settings: return "Settings"
        case .support: return "Support"
        case .notes: return "Memos"
        case .reminders: return "Reminders"
        case .goals: return "Goals"
        case .learning: return "Learning"
        case .reflection: return "Reflection"
        case .tasks: return "Tasks"
        case .runTask: return "Run a task"
        case .skills: return "Skills"
        default: return ""
        }
    }

    private func sub<V: View>(_ title: String, @ViewBuilder _ content: () -> V) -> some View {
        VStack(spacing: 0) {
            HStack {
                Button { backHome() } label: { Image(systemName: "chevron.left").foregroundColor(theme.onSurface) }
                Text(title.uppercased()).hermesDisplayLabel(size: 15).foregroundColor(theme.primary)
                Spacer()
            }
            .padding(.horizontal, 16).padding(.vertical, 12).background(theme.surface)
            content().frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(theme.background.ignoresSafeArea())
    }

    // MARK: drawer

    private var drawer: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("LIFE AGENT").font(.system(size: 20, weight: .bold)).tracking(1.6)
                .foregroundColor(theme.primary).padding(.horizontal, 16).padding(.top, 24).padding(.bottom, 12)

            Button { chat.newChat(); surface = .conversation; closeDrawer() } label: {
                HStack(spacing: 10) { Image(systemName: "plus"); Text("New chat").fontWeight(.medium) }
                    .foregroundColor(theme.onSurface)
                    .padding(.horizontal, 14).padding(.vertical, 12).frame(maxWidth: .infinity, alignment: .leading)
                    .background(theme.surfaceVariant).clipShape(RoundedCornerShape(12))
                    .overlay(RoundedCornerShape(12).stroke(theme.outline, lineWidth: 1))
            }
            .padding(.horizontal, 12)

            Text("RECENT").hermesDisplayLabel(size: 11).foregroundColor(theme.onSurfaceVariant)
                .padding(.horizontal, 20).padding(.top, 16).padding(.bottom, 4)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(recent) { s in
                        drawerItem(icon: "bubble.left", label: s.title) {
                            chat.selectChat(s.id); surface = .conversation; closeDrawer()
                        }
                    }
                }
            }
            .frame(maxHeight: 220)

            Divider().background(theme.outline).padding(.vertical, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    drawerItem(icon: "square.grid.2x2", label: "Dashboard") { go(.dashboard) }
                    drawerItem(icon: "clock.arrow.circlepath", label: "History") { go(.history) }
                    drawerItem(icon: "point.3.connected.trianglepath.dotted", label: "Knowledge") { go(.knowledge) }
                    drawerItem(icon: "flag", label: "Goals") { go(.goals) }
                    drawerItem(icon: "graduationcap", label: "Learning") { go(.learning) }
                    drawerItem(icon: "checkmark.circle", label: "Tasks") { go(.tasks) }
                    drawerItem(icon: "note.text", label: "Memos") { go(.notes) }
                    drawerItem(icon: "bell", label: "Reminders") { go(.reminders) }
                    drawerItem(icon: "leaf", label: "Reflection") { go(.reflection) }
                    drawerItem(icon: "sparkles", label: "Skills") { go(.skills) }
                    drawerItem(icon: "heart", label: "Support") { go(.support) }
                    drawerItem(icon: "gearshape", label: "Settings") { go(.settings) }
                }
            }
            Spacer(minLength: 0)
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .background(theme.surface.ignoresSafeArea())
    }

    private var recent: [ChatModel.UISession] {
        chat.sessions.filter { !$0.messages.isEmpty }.sorted { $0.updatedAt > $1.updatedAt }
    }

    private func drawerItem(icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon).frame(width: 22).foregroundColor(theme.onSurface)
                Text(label).foregroundColor(theme.onSurface)
                Spacer()
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        }.buttonStyle(.plain)
    }

    // MARK: nav helpers

    private func openDrawer() { drawerOpen = true }
    private func closeDrawer() { drawerOpen = false }
    private func go(_ s: Surface) { surface = s; closeDrawer() }
    private func backHome() { dashboard.refresh(); surface = .dashboard }
}

private struct ComingSoon: View {
    let title: String
    @Environment(\.theme) private var theme
    var body: some View {
        VStack(spacing: 8) {
            Text(title).font(.headline).foregroundColor(theme.onBackground)
            Text("Coming to iOS shortly.").font(.callout).foregroundColor(theme.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
