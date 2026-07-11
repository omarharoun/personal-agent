// ChatView.swift — the streaming chat surface (Android AppScreen's conversation
// content): a markdown transcript that grows token-by-token, a consent-first
// crisis card, and a floating composer with an attachment dock + hold-to-talk.

import SwiftUI
import Shared

struct ChatView: View {
    @ObservedObject var model: ChatModel
    @Environment(\.theme) private var theme
    @Environment(\.openURL) private var openURL
    var onOpenDrawer: () -> Void

    @State private var draft = ""

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ZStack(alignment: .bottom) {
                transcript
                VStack(spacing: 8) {
                    if let crisis = model.activeCrisis {
                        SupportCard(response: crisis) { model.dismissCrisis() }
                            .padding(.horizontal, 16)
                    }
                    // Fixed suggestion chips (shared curated copy) — tap to compose a view.
                    SuggestionChipRow { chip in model.sendChip(chip) }
                    Composer(draft: $draft, sending: model.sending,
                             onSend: { text in model.send(text); draft = "" },
                             onVoiceFinal: { spoken in model.send(spoken) },
                             onAttach: { marker in
                                 draft = (draft.trimmingCharacters(in: .whitespaces) + " " + marker)
                                     .trimmingCharacters(in: .whitespaces)
                             })
                }
            }
        }
        .background(theme.background.ignoresSafeArea())
    }

    private var topBar: some View {
        HStack {
            Button(action: onOpenDrawer) {
                Image(systemName: "line.3.horizontal").foregroundColor(theme.onSurface)
            }
            Spacer()
            Text("LIFE AGENT").hermesDisplayLabel(size: 15).foregroundColor(theme.primary)
            Spacer()
            Button(action: { model.newChat() }) {
                Image(systemName: "plus").foregroundColor(theme.onSurface)
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(theme.surface)
    }

    private var transcript: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 18) {
                    if model.messages.isEmpty {
                        EmptyChatState { model.send($0) }.padding(.top, 40)
                    } else {
                        ForEach(model.messages) { msg in
                            MessageRow(
                                msg: msg,
                                onPlanToggle: { model.togglePlanRow($0) },
                                onResourceOpen: { res in
                                    model.markResourceStarted(res.id)
                                    if let url = URL(string: res.url) { openURL(url) }
                                }
                            )
                        }
                        if model.sending && !(model.messages.last?.composing ?? false) {
                            TypingIndicator().id("typing")
                        }
                    }
                }
                .padding(.horizontal, 16).padding(.top, 16).padding(.bottom, 96)
            }
            .onChange(of: model.messages.count) { _, _ in
                if let last = model.messages.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } }
            }
        }
    }
}

private struct MessageRow: View {
    let msg: ChatModel.UIMessage
    var onPlanToggle: (PlanRow) -> Void = { _ in }
    var onResourceOpen: (LearningResource) -> Void = { _ in }
    @Environment(\.theme) private var theme

    var body: some View {
        switch msg.role {
        case .user:
            HStack {
                Spacer(minLength: 40)
                Text(msg.text)
                    .foregroundColor(theme.onSecondaryContainer)
                    .padding(.horizontal, 16).padding(.vertical, 12)
                    .background(theme.secondaryContainer)
                    .clipShape(RoundedCornerShape(12))
                    .overlay(RoundedCornerShape(12).stroke(theme.outline, lineWidth: 1))
            }
        case .system:
            HStack {
                Spacer()
                Text(msg.text)
                    .font(.callout).foregroundColor(theme.onSurfaceVariant)
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(theme.surfaceVariant).clipShape(RoundedCornerShape(12))
                Spacer()
            }
        case .assistant:
            if msg.composing {
                ComposingIndicator()
            } else if let view = msg.view {
                ComposedViewCard(view: view, onPlanToggle: onPlanToggle, onResourceOpen: onResourceOpen)
            } else {
                VStack(alignment: .leading, spacing: 2) {
                    MarkdownText(text: msg.text, color: theme.onBackground)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if !msg.text.isEmpty { MessageActions(text: msg.text) }
                }
            }
        }
    }
}

private struct MessageActions: View {
    let text: String
    @Environment(\.theme) private var theme
    var body: some View {
        HStack(spacing: 4) {
            Button { UIPasteboard.general.string = text } label: {
                Label("Copy", systemImage: "doc.on.doc").font(.footnote)
            }
            ShareLink(item: text) { Label("Save", systemImage: "square.and.arrow.up").font(.footnote) }
        }
        .foregroundColor(theme.onSurfaceVariant)
        .padding(.top, 2)
    }
}

private struct TypingIndicator: View {
    @Environment(\.theme) private var theme
    var body: some View {
        HStack(spacing: 10) {
            ProgressView().controlSize(.small).tint(theme.onSurfaceVariant)
            Text("Thinking…").font(.callout).foregroundColor(theme.onSurfaceVariant)
        }
    }
}

private struct EmptyChatState: View {
    var onPrompt: (String) -> Void
    @Environment(\.theme) private var theme
    private let examples: [(String, String)] = [
        ("📝  Remember something", "Remember that my sister's birthday is March 3rd"),
        ("⏰  Set a reminder", "Remind me to call mom in 2 hours"),
        ("🧭  Talk it through", "Help me think through a decision I'm facing"),
        ("🌱  Reflect", "What patterns have you noticed in what I've told you?"),
    ]
    var body: some View {
        VStack(spacing: 12) {
            Text("What's on your mind?")
                .font(.title2.weight(.semibold)).foregroundColor(theme.onBackground)
                .multilineTextAlignment(.center)
            Text("I'm your Life Agent, running on your own Hermes. I remember what matters to you, keep your notes and reminders, and help you think things through.")
                .font(.body).foregroundColor(theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 420)
                .padding(.bottom, 12)
            ForEach(examples, id: \.1) { label, prompt in
                Button { onPrompt(prompt) } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(label).font(.subheadline.weight(.medium)).foregroundColor(theme.onSurface)
                        Text(prompt).font(.footnote).foregroundColor(theme.onSurfaceVariant)
                            .lineLimit(2).frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(theme.surface)
                    .clipShape(RoundedCornerShape(16))
                    .overlay(RoundedCornerShape(16).stroke(theme.outline, lineWidth: 1))
                }
            }
        }
        .padding(.horizontal, 8)
        .frame(maxWidth: .infinity)
    }
}
