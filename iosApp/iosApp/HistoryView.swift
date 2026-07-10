// HistoryView.swift — saved conversations (Android ChatHistoryScreen): browse,
// multi-select, delete. Chats persist on-device via the sealed ChatStore.

import SwiftUI

struct HistoryView: View {
    @ObservedObject var model: ChatModel
    var onOpenChat: (Int64) -> Void
    @Environment(\.theme) private var theme

    @State private var selecting = false
    @State private var selected: Set<Int64> = []

    private var visible: [ChatModel.UISession] {
        model.sessions
            .filter { !$0.messages.isEmpty || $0.id == model.currentChatId }
            .sorted { $0.updatedAt > $1.updatedAt }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            toolbar
            if visible.isEmpty {
                emptyState
            } else {
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(visible) { row($0) }
                    }
                    .padding(.horizontal, 12).padding(.vertical, 12)
                }
            }
        }
    }

    private var toolbar: some View {
        HStack {
            Text(selecting ? "\(selected.count) SELECTED"
                 : "\(visible.count) CONVERSATION\(visible.count == 1 ? "" : "S") · SAVED ON THIS DEVICE")
                .hermesDisplayLabel(size: 11).foregroundColor(theme.onSurfaceVariant)
            Spacer()
            if selecting {
                Button("All") { selected = Set(visible.map { $0.id }) }.font(.footnote)
                Button("Delete") {
                    selected.forEach { model.deleteChat($0) }
                    selected.removeAll(); selecting = false
                }.font(.footnote).foregroundColor(theme.error).disabled(selected.isEmpty)
                Button("Cancel") { selecting = false; selected.removeAll() }.font(.footnote)
            } else {
                Button("Select") { selecting = true }.font(.footnote)
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
    }

    private func row(_ s: ChatModel.UISession) -> some View {
        let isSelected = selected.contains(s.id)
        return Button {
            if selecting { toggle(s.id) } else { onOpenChat(s.id) }
        } label: {
            HStack(spacing: 12) {
                if selecting {
                    Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                        .foregroundColor(isSelected ? theme.primary : theme.onSurfaceVariant)
                } else {
                    Image(systemName: "bubble.left").foregroundColor(theme.primary)
                }
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(s.title).font(.subheadline.weight(.semibold)).foregroundColor(theme.onSurface).lineLimit(1)
                        if s.fromHermes { Image(systemName: "cloud").font(.caption2).foregroundColor(theme.onSurfaceVariant) }
                    }
                    if let preview = s.messages.last(where: { !$0.text.isEmpty })?.text {
                        Text(preview).font(.caption).foregroundColor(theme.onSurfaceVariant).lineLimit(1)
                    }
                }
                Spacer()
                if !selecting {
                    Button { model.deleteChat(s.id) } label: {
                        Image(systemName: "trash").foregroundColor(theme.onSurfaceVariant)
                    }.buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(isSelected ? theme.secondaryContainer : theme.surface)
            .clipShape(RoundedCornerShape(12))
            .overlay(RoundedCornerShape(12).stroke(isSelected ? theme.primary : theme.outline, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Image(systemName: "bubble.left").font(.system(size: 40)).foregroundColor(theme.onSurfaceVariant)
            Text("No saved conversations yet").font(.headline).foregroundColor(theme.onBackground)
            Text("Your chats are saved on this device as you talk, and stay here when you reopen the app.")
                .font(.callout).foregroundColor(theme.onSurfaceVariant).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity).padding(24)
    }

    private func toggle(_ id: Int64) {
        if selected.contains(id) { selected.remove(id) } else { selected.insert(id) }
    }
}
