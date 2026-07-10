// Composer.swift — the floating messenger-style composer (Android AppScreen's
// Composer): a "+" attachment dock, a floating text field over the page (no opaque
// band), and a trailing send / hold-to-talk mic. Empty field + press-and-hold the
// mic records a voice message that the device transcribes on-device (Speech
// framework) and sends as text; slide away while holding to cancel.
//
// P12/P13 polish (macOS-dock magnify on hold-slide, real photo/file pickers) layers
// on top of this; the dock + hold-to-talk + on-device voice are functional here.

import SwiftUI

struct Composer: View {
    @Binding var draft: String
    var sending: Bool
    var onSend: (String) -> Void
    var onVoiceFinal: (String) -> Void
    var onAttach: (String) -> Void

    @Environment(\.theme) private var theme
    @StateObject private var voice = VoiceRecognizer()

    @State private var menuOpen = false
    @State private var micPressed = false
    @State private var cancelHint = false
    @State private var elapsed = 0
    @State private var timer: Timer?

    private let options: [(String, String, String)] = [
        ("Camera", "camera.fill", "[📷 photo]"),
        ("Photo", "photo.fill", "[🖼 image]"),
        ("File", "paperclip", "[📎 file]"),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if menuOpen {
                VStack(alignment: .leading, spacing: 10) {
                    ForEach(options, id: \.0) { opt in
                        Button {
                            menuOpen = false
                            onAttach(opt.2)
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: opt.1).frame(width: 20)
                                Text(opt.0)
                            }
                            .foregroundColor(theme.onSurface)
                            .padding(.horizontal, 16).padding(.vertical, 10)
                            .background(theme.surface)
                            .clipShape(RoundedCornerShape(24))
                            .overlay(RoundedCornerShape(24).stroke(theme.outline.opacity(0.6), lineWidth: 1))
                            .shadow(radius: 3)
                        }
                    }
                }
                .padding(.leading, 6).padding(.bottom, 2)
                .transition(.opacity.combined(with: .move(edge: .bottom)))
            }

            HStack(alignment: .bottom, spacing: 6) {
                Button { withAnimation(.easeOut(duration: 0.15)) { menuOpen.toggle() } } label: {
                    Image(systemName: menuOpen ? "xmark" : "plus")
                        .foregroundColor(theme.onSurfaceVariant)
                        .frame(width: 44, height: 44)
                        .background(theme.surfaceVariant).clipShape(Circle())
                }

                fieldArea
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10).padding(.horizontal, 8)

                sendOrMic
            }
            .padding(.horizontal, 6).padding(.vertical, 8)
        }
        .padding(.horizontal, 14).padding(.top, 6).padding(.bottom, 12)
        .onChange(of: voice.error) { _, newValue in
            if newValue != nil {
                DispatchQueue.main.asyncAfter(deadline: .now() + 3.5) { voice.clearError() }
            }
        }
    }

    @ViewBuilder private var fieldArea: some View {
        if micPressed {
            HStack(spacing: 8) {
                Circle().fill(theme.error).frame(width: 9, height: 9)
                Text(String(format: "%d:%02d", elapsed / 60, elapsed % 60))
                    .foregroundColor(theme.onSurface)
                Text(voice.partial.isEmpty ? (cancelHint ? "release to cancel" : "release to send · slide up to cancel") : voice.partial)
                    .font(.callout)
                    .foregroundColor(voice.partial.isEmpty ? theme.onSurfaceVariant : theme.onSurface)
                    .lineLimit(1)
            }
        } else if let err = voice.error, draft.isEmpty {
            Text(err).font(.callout).foregroundColor(theme.error).lineLimit(2)
        } else {
            ZStack(alignment: .leading) {
                if draft.isEmpty {
                    Text("Message your Life Agent…").foregroundColor(theme.onSurfaceVariant)
                }
                TextField("", text: $draft, axis: .vertical)
                    .foregroundColor(theme.onSurface)
                    .lineLimit(1...6)
                    .tint(theme.primary)
            }
        }
    }

    @ViewBuilder private var sendOrMic: some View {
        if !draft.trimmingCharacters(in: .whitespaces).isEmpty {
            Button { onSend(draft) } label: {
                Image(systemName: "arrow.up")
                    .foregroundColor(theme.onPrimary)
                    .frame(width: 44, height: 44)
                    .background(sending ? theme.surfaceVariant : theme.primary)
                    .clipShape(Circle())
            }
            .disabled(sending)
        } else {
            Image(systemName: "mic.fill")
                .foregroundColor(theme.onPrimary)
                .frame(width: 44, height: 44)
                .background(micPressed ? theme.error : theme.primary)
                .clipShape(Circle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { g in
                            if !micPressed { startRecording() }
                            cancelHint = g.translation.height < -120 || g.translation.width < -120
                        }
                        .onEnded { _ in endRecording(cancelled: cancelHint) }
                )
        }
    }

    private func startRecording() {
        micPressed = true
        cancelHint = false
        elapsed = 0
        voice.start()
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in elapsed += 1 }
    }

    private func endRecording(cancelled: Bool) {
        micPressed = false
        timer?.invalidate(); timer = nil
        let text = voice.stop()
        if !cancelled {
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty { onVoiceFinal(trimmed) }
        }
    }
}
