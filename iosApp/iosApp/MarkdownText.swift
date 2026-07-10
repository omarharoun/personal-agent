// MarkdownText.swift — lightweight markdown rendering for assistant replies and
// agent-authored summaries. Uses Foundation's AttributedString markdown parser,
// preserving line breaks. (A richer renderer — code blocks, headings — can layer
// on later; this covers bold/italic/links/inline-code + paragraphs.)

import SwiftUI

struct MarkdownText: View {
    let text: String
    var color: Color = .primary

    var body: some View {
        Text(attributed)
            .foregroundColor(color)
            .textSelection(.enabled)
    }

    private var attributed: AttributedString {
        // Render each paragraph so hard line breaks survive (the inline parser
        // collapses single newlines otherwise).
        var out = AttributedString()
        let paragraphs = text.components(separatedBy: "\n")
        for (i, line) in paragraphs.enumerated() {
            if let a = try? AttributedString(
                markdown: line,
                options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)
            ) {
                out += a
            } else {
                out += AttributedString(line)
            }
            if i < paragraphs.count - 1 { out += AttributedString("\n") }
        }
        return out
    }
}
