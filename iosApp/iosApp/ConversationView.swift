import SwiftUI
import Shared

/// UX Stream 1 — the single Claude-style conversational surface for iOS.
///
/// Replaces the old Notes/Reminders/Plan/Support/Settings `TabView`. The whole app
/// is now one scrollable transcript + a bottom input box; notes, reminders, and
/// planning are capabilities the agent invokes behind the scenes (see
/// `AppModel.send(_:)` + the shared `IntentRouter`). Settings lives behind a gear
/// toolbar button, and the crisis-safety "Support" surface stays reachable from the
/// same menu — 🔒 it is moved, never deleted, and its consent-first behaviour is
/// unchanged.
///
/// The age-gate + onboarding still run BEFORE this view (in `ContentView`); this is
/// only what shows AFTER onboarding completes.
struct ConversationView: View {
    @EnvironmentObject var model: AppModel
    @State private var draft = ""
    @State private var showSettings = false
    @State private var showSupport = false
    @FocusState private var inputFocused: Bool

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                transcript
                Divider()
                inputBar
            }
            .background(Color.paBackground.ignoresSafeArea())
            .tint(.paAccent) // Claude coral — primary action only
            .navigationTitle("Personal Agent")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        // 🔒 Crisis-safety support — moved here from the old tab bar.
                        Button {
                            showSupport = true
                        } label: {
                            Label("Support", systemImage: "heart.circle")
                        }
                        Button {
                            showSettings = true
                        } label: {
                            Label("Settings", systemImage: "gearshape")
                        }
                    } label: {
                        Image(systemName: "gearshape")
                    }
                }
            }
            .sheet(isPresented: $showSettings) {
                NavigationStack { SettingsView() }
            }
            .sheet(isPresented: $showSupport) {
                // 🔒 Trusted-contacts / crisis-safety surface, unchanged.
                NavigationStack { TrustedContactsView() }
            }
        }
    }

    // MARK: Transcript

    @ViewBuilder
    private var transcript: some View {
        if model.messages.isEmpty {
            homeEmptyState
        } else {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(model.messages) { msg in
                            MessageBubble(message: msg).id(msg.id)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 16)
                }
                .onChange(of: model.messages.count) { _ in
                    if let last = model.messages.last {
                        withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                    }
                }
            }
        }
    }

    /// Claude-style home: warm greeting + tappable example prompts that send.
    private var homeEmptyState: some View {
        // (card label, prompt text that gets sent)
        let examples: [(String, String)] = [
            ("📝  Take a note", "Take a note: weekend trip ideas"),
            ("⏰  Set a reminder", "Remind me to call mom in 2 hours"),
            ("✅  Plan something", "Add to my plan: finish the budget"),
            ("💭  Think it through", "Help me think through a decision"),
        ]
        return ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("What's on your mind?")
                    .font(.largeTitle).bold()
                    .foregroundStyle(Color.paText)
                Text("Just talk to me. I can take notes, set reminders, track your plan, "
                     + "and think things through — all on your device.")
                    .foregroundStyle(.secondary)
                Spacer().frame(height: 12)
                ForEach(examples, id: \.0) { label, prompt in
                    Button {
                        Task { await model.send(prompt) }
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(label).font(.headline).foregroundStyle(Color.paText)
                            Text("“\(prompt)”").font(.subheadline).foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(14)
                        .background(Color.paSurface, in: RoundedRectangle(cornerRadius: 16))
                    }
                    .buttonStyle(.plain)
                }
                Spacer().frame(height: 8)
                Text("Tip: notes, reminders, and plans work right away — no AI model "
                     + "needed. Install a model (gear, top-right) or add an API key to chat.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            .padding(24)
        }
    }

    // MARK: Input

    private var inputBar: some View {
        HStack(alignment: .bottom, spacing: 8) {
            TextField("Message your agent…", text: $draft, axis: .vertical)
                .lineLimit(1...6)
                .textFieldStyle(.roundedBorder)
                .focused($inputFocused)
                .submitLabel(.send)
                .onSubmit(sendDraft)

            Button(action: sendDraft) {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 30))
            }
            .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || model.sending)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
    }

    private func sendDraft() {
        let text = draft
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        draft = ""
        Task { await model.send(text) }
    }
}

/// One conversation bubble, aligned + tinted by role.
private struct MessageBubble: View {
    let message: AppModel.ChatMessage

    var body: some View {
        HStack {
            if message.role == .user { Spacer(minLength: 40) }
            Text(message.text)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(background)
                .foregroundStyle(foreground)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            if message.role != .user { Spacer(minLength: 40) }
        }
    }

    private var background: Color {
        switch message.role {
        case .user: return .accentColor
        case .assistant: return Color(.secondarySystemBackground)
        case .system: return Color(.tertiarySystemBackground)
        }
    }

    private var foreground: Color {
        message.role == .user ? .white : .primary
    }
}
