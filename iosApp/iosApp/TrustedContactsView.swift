// 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
import SwiftUI
import Shared

/// 🔒 CRISIS-CRITICAL (Step 7) — the "Support" tab. Mirrors the Android sibling.
///
/// Two consent-first jobs:
///   1. Manage trusted contacts: add/remove people the user CHOOSES to list. The
///      up-front consent text is explicit, and adding only happens on the user's
///      tap. We never read the device address book or infer anyone.
///   2. Offer support on the user's terms: a "How are you feeling?" self check-in
///      (explicit, user-initiated — not background scanning) and an always-
///      available "Show support resources" button. Both route through `AppModel`,
///      which surfaces `SupportResponseView`. No autonomous action ever happens.
///
/// ⚠️ PENDING-REVIEW / NOT-FOR-REAL-USERS — copy, tone, and flow need crisis-
/// expert + product review; behavior must be verified on a real device.
struct TrustedContactsView: View {
    @EnvironmentObject var model: AppModel

    @State private var name = ""
    @State private var phone = ""
    @State private var relation = ""
    @State private var consentAccepted = false
    @State private var checkInText = ""

    var body: some View {
        NavigationStack {
            Form {
                supportSection
                checkInSection
                consentSection
                addContactSection
                contactsSection
                disclaimerSection
            }
            .navigationTitle("Support")
            .sheet(item: $model.distress) { presentation in
                SupportResponseView(response: presentation.response)
                    .environmentObject(model)
            }
            .alert(model.message ?? "", isPresented: .constant(model.message != nil)) {
                Button("OK") { model.message = nil }
            }
        }
    }

    // MARK: Always-available support

    private var supportSection: some View {
        Section {
            Button {
                model.openSupportResources()
            } label: {
                Label("Show support resources", systemImage: "lifepreserver")
            }
        } header: {
            Text("Support")
        } footer: {
            Text("Crisis resources are always here whenever you want them.")
        }
    }

    // MARK: Explicit, user-initiated self check-in

    private var checkInSection: some View {
        Section {
            TextField("How are you feeling? (optional)", text: $checkInText, axis: .vertical)
                .lineLimit(2...5)
            Button {
                model.checkIn(checkInText)
                checkInText = ""
            } label: {
                Label("Check in with myself", systemImage: "heart.text.square")
            }
            .disabled(checkInText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        } header: {
            Text("Check in")
        } footer: {
            Text("This stays on your device. If it sounds like you're having a hard "
                 + "time, we'll gently show some support options — that's all.")
        }
    }

    // MARK: Up-front consent (gates adding)

    private var consentSection: some View {
        Section {
            Toggle(isOn: $consentAccepted) {
                Text("I understand these contacts are stored only on this device "
                     + "(encrypted), and Personal Agent will never contact them on "
                     + "its own — only I can, by tapping.")
                    .font(.footnote)
            }
        } header: {
            Text("Trusted contacts")
        } footer: {
            Text("Add people you might want help reaching in a hard moment. "
                 + "Adding someone is entirely your choice.")
        }
    }

    // MARK: Add a contact

    private var addContactSection: some View {
        Section("Add a trusted contact") {
            TextField("Name", text: $name)
                .textContentType(.name)
            TextField("Phone (optional)", text: $phone)
                .textContentType(.telephoneNumber)
                .keyboardType(.phonePad)
            TextField("Relationship (e.g. Sister, Friend)", text: $relation)
            Button {
                Task {
                    await model.addTrustedContact(name: name, phone: phone, relation: relation)
                    name = ""; phone = ""; relation = ""
                }
            } label: {
                Label("Add contact", systemImage: "person.badge.plus")
            }
            .disabled(!canAdd)
        }
    }

    private var canAdd: Bool {
        consentAccepted && !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    // MARK: Existing contacts (remove anytime)

    private var contactsSection: some View {
        Section("Your trusted contacts") {
            if model.trustedContacts.isEmpty {
                Text("No trusted contacts yet.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(model.trustedContacts, id: \.id) { contact in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(contact.name).font(.headline)
                        if !contact.relationship.isEmpty || contact.phone != nil {
                            Text([contact.relationship, contact.phone].compactMap { $0 }
                                .filter { !$0.isEmpty }
                                .joined(separator: " • "))
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .onDelete { idx in
                    let ids = idx.map { model.trustedContacts[$0].id }
                    Task { for id in ids { await model.removeTrustedContact(id) } }
                }
            }
        }
    }

    private var disclaimerSection: some View {
        Section {
            Text("⚠️ Not for real users yet — this support feature is pending review "
                 + "by crisis-care experts. Personal Agent is not a crisis service and "
                 + "takes no action on its own.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }
}
