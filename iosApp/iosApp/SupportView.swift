// SupportView.swift — 🔒 the consent-first crisis-safety surface (Gate 2, Android
// SafetyScreen). Autonomous contacting stays DISABLED: the app only ever opens a
// call/message for the user to send themselves, and only when they tap. Trusted
// contacts are added explicitly with recorded consent.

import SwiftUI
import Shared

@MainActor
final class SupportModel: ObservableObject {
    @Published var contacts: [TrustedContact] = []
    @Published var support: CrisisResponse?
    @Published var message: String?

    private let env: AppEnvironment
    init(env: AppEnvironment) { self.env = env; refresh() }

    func refresh() { _Concurrency.Task { contacts = (try? await env.trustedContacts.all()) ?? [] } }

    func showSupport() { support = LifeAgentIos.shared.supportResponse(responder: env.crisisResponder) }
    func dismissSupport() { support = nil }

    func addContact(name: String, phone: String, relationship: String) {
        let n = name.trimmingCharacters(in: .whitespaces)
        guard !n.isEmpty else { message = "Enter a name."; return }
        _Concurrency.Task {
            let now = LifeAgentIos.shared.nowMillis()
            let c = TrustedContact(id: Ids.shared.next(nowMillis: now), name: n,
                                   relationship: relationship.trimmingCharacters(in: .whitespaces),
                                   phone: phone.isEmpty ? nil : phone, consentedAt: now)
            do { try await env.trustedContacts.add(contact: c); message = "Saved"; refresh() }
            catch { message = "Couldn't save that contact." }
        }
    }
    func remove(_ id: String) { _Concurrency.Task { try? await env.trustedContacts.remove(id: id); refresh() } }
}

struct SupportView: View {
    @StateObject private var model: SupportModel
    @Environment(\.theme) private var theme

    @State private var name = ""
    @State private var phone = ""
    @State private var relationship = ""
    @State private var consent = false

    init(env: AppEnvironment) { _model = StateObject(wrappedValue: SupportModel(env: env)) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Button { model.showSupport() } label: {
                    HStack { Image(systemName: "heart.fill"); Text("Find support") }
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .foregroundColor(theme.onPrimary).background(theme.primary).clipShape(RoundedCornerShape(8))
                }
                Text("If you're going through a hard time, you can find supportive resources here. You're never contacted or reported automatically.")
                    .font(.caption).foregroundColor(theme.onSurfaceVariant)

                if let s = model.support {
                    SupportCard(response: s) { model.dismissSupport() }
                }

                Divider().background(theme.outline)

                Text("People you trust").font(.headline).foregroundColor(theme.onBackground)
                Text("Choose people you might want help reaching if you're going through a hard time. The app only ever opens a call or message for you to send — never on its own.")
                    .font(.callout).foregroundColor(theme.onSurfaceVariant)

                VStack(alignment: .leading, spacing: 10) {
                    Text("Add someone").font(.subheadline.weight(.semibold)).foregroundColor(theme.onSurface)
                    field("Name", $name)
                    field("Phone (optional)", $phone).keyboardType(.phonePad)
                    field("Relationship (optional)", $relationship)
                    Toggle(isOn: $consent) {
                        Text("I'm choosing this person myself, and I understand the app will only help me reach them when I tap to — it won't contact them on its own.")
                            .font(.caption).foregroundColor(theme.onSurfaceVariant)
                    }
                    Button("Add to my trusted people") {
                        model.addContact(name: name, phone: phone, relationship: relationship)
                        name = ""; phone = ""; relationship = ""; consent = false
                    }
                    .foregroundColor(theme.onPrimary).frame(maxWidth: .infinity).padding(.vertical, 10)
                    .background((consent && !name.trimmingCharacters(in: .whitespaces).isEmpty) ? theme.primary : theme.surfaceVariant)
                    .clipShape(RoundedCornerShape(8))
                    .disabled(!consent || name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                .padding(14).background(theme.surfaceVariant).clipShape(RoundedCornerShape(12))

                if let msg = model.message { Text(msg).font(.footnote).foregroundColor(theme.onSurfaceVariant) }

                if model.contacts.isEmpty {
                    Text("You haven't added anyone yet.").font(.callout).foregroundColor(theme.onSurfaceVariant)
                } else {
                    ForEach(model.contacts, id: \.id) { c in contactRow(c) }
                }
            }
            .padding(16)
        }
    }

    private func contactRow(_ c: TrustedContact) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(c.relationship.isEmpty ? c.name : "\(c.name) · \(c.relationship)")
                .font(.body).foregroundColor(theme.onSurface)
            if let phone = c.phone, !phone.isEmpty {
                HStack(spacing: 8) {
                    Button("Help me call") { open("tel://\(digits(phone))") }
                        .font(.footnote).foregroundColor(theme.primary)
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .overlay(RoundedCornerShape(8).stroke(theme.outline, lineWidth: 1))
                    Button("Help me text") { open("sms:\(digits(phone))") }
                        .font(.footnote).foregroundColor(theme.primary)
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .overlay(RoundedCornerShape(8).stroke(theme.outline, lineWidth: 1))
                }
            } else {
                Text("No number saved for this person.").font(.caption).foregroundColor(theme.onSurfaceVariant)
            }
            Button("Remove") { model.remove(c.id) }.font(.footnote).foregroundColor(theme.onSurfaceVariant)
        }
        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.surface).clipShape(RoundedCornerShape(12))
        .overlay(RoundedCornerShape(12).stroke(theme.outline, lineWidth: 1))
    }

    private func field(_ placeholder: String, _ text: Binding<String>) -> some View {
        TextField(placeholder, text: text).foregroundColor(theme.onSurface)
            .padding(10).background(theme.surface).clipShape(RoundedCornerShape(8))
            .overlay(RoundedCornerShape(8).stroke(theme.outline, lineWidth: 1))
    }

    private func digits(_ s: String) -> String { s.filter { $0.isNumber || $0 == "+" } }
    private func open(_ url: String) { if let u = URL(string: url) { UIApplication.shared.open(u) } }
}
