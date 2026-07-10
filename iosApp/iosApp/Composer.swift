// Composer.swift — the floating messenger-style composer (Android AppScreen's
// Composer): a "+" attachment dock that you can press-and-hold and slide up to a
// magnifying, macOS-Dock-style stack of options (or tap to open and tap an option),
// a floating text field over the page (no opaque band), and a trailing send /
// hold-to-talk mic. Empty field + press-and-hold the mic records a voice message
// the device transcribes on-device (Speech framework) and sends as text; slide away
// while holding to cancel.

import SwiftUI

private struct OptionCenterKey: PreferenceKey {
    static var defaultValue: [Int: CGFloat] = [:]
    static func reduce(value: inout [Int: CGFloat], nextValue: () -> [Int: CGFloat]) {
        value.merge(nextValue()) { _, n in n }
    }
}

struct Composer: View {
    @Binding var draft: String
    var sending: Bool
    var onSend: (String) -> Void
    var onVoiceFinal: (String) -> Void
    var onAttach: (String) -> Void

    @Environment(\.theme) private var theme
    @StateObject private var voice = VoiceRecognizer()

    // Attachment dock (hold-slide-up-to-pick, or tap-to-toggle).
    @State private var menuOpen = false
    @State private var dragging = false
    @State private var fingerY: CGFloat = .nan
    @State private var optionCenters: [Int: CGFloat] = [:]

    // Voice (hold-to-talk).
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
            if menuOpen || dragging {
                dock.transition(.opacity.combined(with: .move(edge: .bottom)))
            }
            HStack(alignment: .bottom, spacing: 6) {
                plusButton
                fieldArea.frame(maxWidth: .infinity).padding(.vertical, 10).padding(.horizontal, 8)
                sendOrMic
            }
            .padding(.horizontal, 6).padding(.vertical, 8)
        }
        .padding(.horizontal, 14).padding(.top, 6).padding(.bottom, 12)
        .coordinateSpace(name: "composer")
        .onPreferenceChange(OptionCenterKey.self) { optionCenters = $0 }
        .onChange(of: voice.error) { _, newValue in
            if newValue != nil { DispatchQueue.main.asyncAfter(deadline: .now() + 3.5) { voice.clearError() } }
        }
    }

    // MARK: attachment dock

    private var dock: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(Array(options.enumerated()), id: \.offset) { i, opt in
                let center = optionCenters[i] ?? .nan
                let scale = magnifyScale(center)
                let highlighted = dragging && nearestOption() == i
                Button {
                    menuOpen = false
                    onAttach(opt.2)
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: opt.1).frame(width: 20)
                        Text(opt.0)
                    }
                    .foregroundColor(highlighted ? theme.onPrimary : theme.onSurface)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(highlighted ? theme.primary : theme.surface)
                    .clipShape(RoundedCornerShape(24))
                    .overlay(RoundedCornerShape(24).stroke(theme.outline.opacity(0.6), lineWidth: 1))
                    .shadow(radius: highlighted ? 8 : 3)
                }
                .scaleEffect(scale, anchor: .bottomLeading)
                .background(GeometryReader { geo in
                    Color.clear.preference(key: OptionCenterKey.self,
                                           value: [i: geo.frame(in: .named("composer")).midY])
                })
            }
        }
        .padding(.leading, 6).padding(.bottom, 2)
    }

    private var plusButton: some View {
        Image(systemName: (menuOpen || dragging) ? "xmark" : "plus")
            .foregroundColor(theme.onSurfaceVariant)
            .frame(width: 44, height: 44)
            .background(theme.surfaceVariant).clipShape(Circle())
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .named("composer"))
                    .onChanged { g in
                        dragging = true
                        menuOpen = true
                        fingerY = g.location.y
                    }
                    .onEnded { g in
                        let moved = abs(g.translation.height) > 24 || abs(g.translation.width) > 24
                        let idx = nearestOption()
                        dragging = false
                        fingerY = .nan
                        if moved {
                            if let idx { onAttach(options[idx].2) }
                            menuOpen = false
                        } else {
                            menuOpen.toggle()
                        }
                    }
            )
    }

    private func magnifyScale(_ center: CGFloat) -> CGFloat {
        guard dragging, !fingerY.isNaN, !center.isNaN else { return 1 }
        let d = fingerY - center
        let sigma: CGFloat = 60
        return 1 + 0.55 * exp(-(d * d) / (2 * sigma * sigma))
    }

    private func nearestOption() -> Int? {
        guard !fingerY.isNaN else { return nil }
        var best = -1
        var bestDist = CGFloat.greatestFiniteMagnitude
        for (i, c) in optionCenters where !c.isNaN {
            let d = abs(fingerY - c)
            if d < bestDist { bestDist = d; best = i }
        }
        return best >= 0 && bestDist <= 44 ? best : nil
    }

    // MARK: field + send/mic

    @ViewBuilder private var fieldArea: some View {
        if micPressed {
            HStack(spacing: 8) {
                Circle().fill(theme.error).frame(width: 9, height: 9)
                Text(String(format: "%d:%02d", elapsed / 60, elapsed % 60)).foregroundColor(theme.onSurface)
                Text(voice.partial.isEmpty ? (cancelHint ? "release to cancel" : "release to send · slide up to cancel") : voice.partial)
                    .font(.callout)
                    .foregroundColor(voice.partial.isEmpty ? theme.onSurfaceVariant : theme.onSurface)
                    .lineLimit(1)
            }
        } else if let err = voice.error, draft.isEmpty {
            Text(err).font(.callout).foregroundColor(theme.error).lineLimit(2)
        } else {
            ZStack(alignment: .leading) {
                if draft.isEmpty { Text("Message your Life Agent…").foregroundColor(theme.onSurfaceVariant) }
                TextField("", text: $draft, axis: .vertical)
                    .foregroundColor(theme.onSurface).lineLimit(1...6).tint(theme.primary)
            }
        }
    }

    @ViewBuilder private var sendOrMic: some View {
        if !draft.trimmingCharacters(in: .whitespaces).isEmpty {
            Button { onSend(draft) } label: {
                Image(systemName: "arrow.up").foregroundColor(theme.onPrimary)
                    .frame(width: 44, height: 44)
                    .background(sending ? theme.surfaceVariant : theme.primary).clipShape(Circle())
            }.disabled(sending)
        } else {
            Image(systemName: "mic.fill").foregroundColor(theme.onPrimary)
                .frame(width: 44, height: 44)
                .background(micPressed ? theme.error : theme.primary).clipShape(Circle())
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
        micPressed = true; cancelHint = false; elapsed = 0
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
