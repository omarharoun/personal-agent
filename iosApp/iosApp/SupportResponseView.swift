// 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
import SwiftUI
import Shared

/// Identifiable wrapper so a Kotlin `CrisisResponse` can drive `.sheet(item:)`.
struct DistressPresentation: Identifiable {
    let id = UUID()
    let response: CrisisResponse
}

/// Builds `tel:` / `sms:` URLs for USER-INITIATED, USER-CONFIRMED contact only.
/// Nothing here dials or sends — it only produces a URL the user chooses to open,
/// which iOS then shows its own call/send confirmation for. Never auto-call,
/// never auto-send.
enum CrisisContactLauncher {
    private static let allowed = Set("0123456789+*#")

    static func telURL(_ raw: String) -> URL? {
        let digits = String(raw.filter { allowed.contains($0) })
        guard !digits.isEmpty else { return nil }
        return URL(string: "tel:\(digits)")
    }

    static func smsURL(_ raw: String, body: String? = nil) -> URL? {
        let digits = String(raw.filter { allowed.contains($0) })
        guard !digits.isEmpty else { return nil }
        var s = "sms:\(digits)"
        if let body, !body.isEmpty,
           let enc = body.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) {
            s += "&body=\(enc)"
        }
        return URL(string: s)
    }
}

/// 🔒 CRISIS-CRITICAL (Step 7) — the calm, warm support surface shown on
/// POSSIBLE_DISTRESS (and on explicit request). Mirrors the Android sibling.
///
/// What it does — and ONLY this:
///   (a) shows a brief, kind, non-clinical, non-alarmist message;
///   (b) gently encourages reaching out to a trusted person, listing the user's
///       trusted contacts with a "Help me contact <name>" action;
///   (c) lists crisis RESOURCES from the shared provider (`response.resources`);
///   (d) every contact affordance is an explicit tap = consent, which opens the
///       dialer / Messages PRE-FILLED — it never calls or sends on its own.
///
/// It makes NO confidentiality promise, no diagnosis, and takes NO autonomous
/// action.
///
/// ⚠️ PENDING-REVIEW / NOT-FOR-REAL-USERS — copy, tone, resource accuracy, and
/// the whole flow require crisis-expert + product review, and must be verified on
/// a real device.
struct SupportResponseView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.openURL) private var openURL
    @Environment(\.dismiss) private var dismiss

    let response: CrisisResponse

    /// A trusted contact the user tapped, held while we confirm before opening
    /// the dialer/Messages. We NEVER skip this confirmation.
    @State private var pendingContact: TrustedContact?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    header
                    reachOutSection
                    resourcesSection
                    footer
                }
                .padding()
            }
            .navigationTitle("You're not alone")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Close") { dismiss() }
                }
            }
            .confirmationDialog(
                pendingContact.map { "Contact \($0.name)?" } ?? "",
                isPresented: Binding(
                    get: { pendingContact != nil },
                    set: { if !$0 { pendingContact = nil } }
                ),
                titleVisibility: .visible,
                presenting: pendingContact
            ) { contact in
                if let phone = contact.phone, let tel = CrisisContactLauncher.telURL(phone) {
                    Button("Call \(contact.name)") { openURL(tel) }
                }
                if let phone = contact.phone, let sms = CrisisContactLauncher.smsURL(phone) {
                    Button("Text \(contact.name)") { openURL(sms) }
                }
                Button("Not now", role: .cancel) { pendingContact = nil }
            } message: { contact in
                Text("This just opens your phone with \(contact.name)'s number ready. "
                     + "You decide whether to call or text — nothing is sent automatically.")
            }
        }
    }

    // MARK: Header — brief, warm, non-alarmist

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: "heart.circle.fill")
                .font(.largeTitle)
                .foregroundStyle(.pink)
            Text(response.message)
                .font(.title3)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: (b) Gently encourage reaching out to a trusted person

    private var reachOutSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Reach out to someone you trust")
                .font(.headline)
            Text("Talking to someone who cares about you can really help. "
                 + "Would you like to reach one of your trusted people?")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            if model.trustedContacts.isEmpty {
                Text("You haven't added anyone yet. You can add a trusted contact "
                     + "any time in the Support tab — only if and when you want to.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(model.trustedContacts, id: \.id) { contact in
                    Button {
                        // Explicit tap = consent. We confirm again before anything opens.
                        pendingContact = contact
                    } label: {
                        HStack {
                            Image(systemName: "person.crop.circle")
                            VStack(alignment: .leading) {
                                Text("Help me contact \(contact.name)")
                                if !contact.relationship.isEmpty {
                                    Text(contact.relationship)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            Image(systemName: "chevron.right").foregroundStyle(.tertiary)
                        }
                    }
                    .buttonStyle(.bordered)
                    .disabled(contact.phone == nil)
                }
            }
        }
    }

    // MARK: (c) Crisis resources from the SHARED provider

    private var resourcesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Free, confidential support")
                .font(.headline)
            ForEach(Array(response.resources.enumerated()), id: \.offset) { _, resource in
                resourceCard(resource)
            }
        }
    }

    // Canonical `CrisisResource` is (name, contact, note) — descriptive text, not
    // dialable fields. We render the text and intentionally do NOT synthesize a
    // call/text button from a free-text `contact` line (it may be guidance like
    // "look up your region's helpline", not a number).
    // TODO crisis-review: if verified, localized dialable numbers are added to the
    // resource model, restore explicit user-initiated Call/Text affordances here.
    private func resourceCard(_ resource: CrisisResource) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(resource.name).font(.subheadline).bold()
            Text(resource.contact)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            if !resource.note.isEmpty {
                Text(resource.note)
                    .font(.caption)
                    .foregroundStyle(.tertiary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.quaternary.opacity(0.4), in: RoundedRectangle(cornerRadius: 12))
    }

    // MARK: Footer — honesty; no confidentiality promise, no autonomous action

    private var footer: some View {
        Text("These are options, not instructions — it's completely your choice. "
             + "Personal Agent never contacts anyone for you.")
            .font(.footnote)
            .foregroundStyle(.secondary)
            .fixedSize(horizontal: false, vertical: true)
    }
}
