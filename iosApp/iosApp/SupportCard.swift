// SupportCard.swift — 🔒 the consent-first crisis support surface (Gate 2).
// Renders the supportive message + real resources. It contacts NO ONE
// automatically; any outward action (call/text a trusted contact) is a deliberate
// user tap, added in the Support screen. Autonomous contacting stays DISABLED.

import SwiftUI
import Shared

struct SupportCard: View {
    let response: CrisisResponse
    var onDismiss: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Support feature — pending review by a crisis-response expert. If you're in immediate danger, contact your local emergency services.")
                .font(.caption).foregroundColor(theme.onSurfaceVariant)

            Text(response.message)
                .font(.body).foregroundColor(theme.onSecondaryContainer)

            if !response.resources.isEmpty {
                Text("Ways to get support").font(.headline).foregroundColor(theme.onSecondaryContainer)
                ForEach(Array(response.resources.enumerated()), id: \.offset) { _, r in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(r.name).font(.body).foregroundColor(theme.onSecondaryContainer)
                        Text(r.contact).font(.callout).foregroundColor(theme.onSurfaceVariant)
                        if !r.note.isEmpty {
                            Text(r.note).font(.caption).foregroundColor(theme.onSurfaceVariant)
                        }
                    }
                }
            }

            Button("Close", action: onDismiss)
                .font(.callout).foregroundColor(theme.primary)
                .frame(maxWidth: .infinity)
        }
        .padding(16)
        .background(theme.secondaryContainer)
        .clipShape(RoundedCornerShape(12))
        .overlay(RoundedCornerShape(12).stroke(theme.outline, lineWidth: 1))
    }
}
