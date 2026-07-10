// SkillsView.swift — a browsable, searchable gallery of the real /v1/skills the
// user's Hermes has installed (Android SkillsScreen). Read-only. Category icons are
// this app's own mapping (Hermes ships none).

import SwiftUI
import Shared

@MainActor
final class SkillsModel: ObservableObject {
    @Published var all: [HermesSkill] = []
    @Published var loading = true
    @Published var error: String?
    @Published var query = ""

    private let client: HermesClient?
    init(env: AppEnvironment) { self.client = env.makeClient(); refresh() }

    func refresh() {
        loading = true; error = nil
        _Concurrency.Task {
            guard let client else { loading = false; return }
            do { all = try await client.skills() }
            catch { self.error = hermesMessage(error) ?? "Couldn't load skills." }
            loading = false
        }
    }

    var grouped: [(String, [HermesSkill])] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        let filtered = q.isEmpty ? all : all.filter {
            $0.name.lowercased().contains(q) || $0.description.lowercased().contains(q) || ($0.category ?? "").lowercased().contains(q)
        }
        let groups = Dictionary(grouping: filtered) { ($0.category?.isEmpty == false ? $0.category! : "other") }
        return groups.sorted { a, b in
            if a.key == "other" { return false }
            if b.key == "other" { return true }
            return a.key < b.key
        }.map { ($0.key, $0.value.sorted { $0.name < $1.name }) }
    }
}

struct SkillsView: View {
    @StateObject private var model: SkillsModel
    @Environment(\.theme) private var theme
    init(env: AppEnvironment) { _model = StateObject(wrappedValue: SkillsModel(env: env)) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Image(systemName: "magnifyingglass").foregroundColor(theme.onSurfaceVariant)
                TextField("Search skills", text: $model.query).foregroundColor(theme.onSurface)
            }
            .padding(12).background(theme.surfaceVariant).clipShape(RoundedCornerShape(8))
            .padding(.horizontal, 16).padding(.top, 8)

            if !model.all.isEmpty {
                Text("\(model.all.count) skills · \(model.grouped.count) categories")
                    .hermesMono(size: 11).foregroundColor(theme.onSurfaceVariant)
                    .padding(.horizontal, 18).padding(.top, 8)
            }

            if model.loading && model.all.isEmpty {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let err = model.error, model.all.isEmpty {
                Text(err).font(.callout).foregroundColor(theme.error).padding(24)
            } else if model.all.isEmpty {
                Text("No skills installed on this Hermes.").foregroundColor(theme.onSurfaceVariant).padding(24)
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 8) {
                        ForEach(model.grouped, id: \.0) { category, skills in
                            HStack(spacing: 8) {
                                Text(emoji(category)).font(.system(size: 18))
                                Text(category.uppercased()).hermesDisplayLabel().foregroundColor(theme.primary)
                                Text("· \(skills.count)").hermesMono(size: 11).foregroundColor(theme.onSurfaceVariant)
                            }.padding(.top, 6)
                            ForEach(skills, id: \.name) { skill in card(skill, category) }
                        }
                    }
                    .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 16)
                }
            }
        }
    }

    private func card(_ skill: HermesSkill, _ category: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text(emoji(category)).font(.system(size: 22))
            VStack(alignment: .leading, spacing: 4) {
                Text(skill.name).font(.subheadline.weight(.semibold)).foregroundColor(theme.onSurface)
                Text(skill.description).font(.caption).foregroundColor(theme.onSurfaceVariant).lineLimit(3)
            }
            Spacer()
        }
        .padding(14).frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.surface).clipShape(RoundedCornerShape(12))
        .overlay(RoundedCornerShape(12).stroke(theme.outline, lineWidth: 1))
    }

    private func emoji(_ category: String) -> String {
        switch category {
        case "autonomous-ai-agents": return "🤖"
        case "creative": return "🎨"
        case "data-science": return "📊"
        case "email": return "✉️"
        case "github": return "🐙"
        case "media": return "🎬"
        case "mlops": return "⚙️"
        case "note-taking": return "📝"
        case "productivity": return "✅"
        case "research": return "🔬"
        case "smart-home": return "🏠"
        case "social-media": return "💬"
        case "software-development": return "💻"
        default: return "🧩"
        }
    }
}
