// GenerativeUiView.swift — renders a shared `ComposedView` natively in SwiftUI,
// mirroring Android's ComposedViewUi.kt: a `switch` (via `as?` casts) over the
// exported sealed `ViewBlock`, one SwiftUI view per fixed primitive, inside the
// app's own trusted cards. The model never ships markup — this is the only thing
// that turns a validated view spec into pixels, and it knows only the ~5 primitives.
// Everything textual is already sanitized/inert by the shared parser. The accent
// (from the user's theme choice) is used throughout, so re-theming recolors these.

import SwiftUI
import Shared

struct ComposedViewCard: View {
    let view: ComposedView
    var onPlanToggle: (PlanRow) -> Void
    var onResourceOpen: (LearningResource) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(eyebrow)
                .font(.caption2).tracking(1.5)
                .foregroundColor(theme.onSurfaceVariant)
            if let title = view.title {
                Text(title).font(.headline).foregroundColor(theme.onSurface)
            }
            ForEach(Array(view.blocks.enumerated()), id: \.offset) { _, block in
                blockView(block)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.surface)
        .clipShape(RoundedCornerShape(14))
        .overlay(RoundedCornerShape(14).stroke(theme.outline, lineWidth: 1))
    }

    private var eyebrow: String {
        let provenance = view.provenance == "local-default" ? "on your device" : "composed for you"
        let name = view.view.replacingOccurrences(of: "-", with: " ")
        return "· \(provenance) · \(name)".uppercased()
    }

    @ViewBuilder
    private func blockView(_ block: ViewBlock) -> some View {
        if let p = block as? ViewBlock.ProseLine {
            ProseLineView(block: p)
        } else if let g = block as? ViewBlock.StatGrid {
            StatGridView(block: g)
        } else if let s = block as? ViewBlock.Sparkline {
            SparklineView(block: s)
        } else if let plan = block as? ViewBlock.Plan {
            PlanView(block: plan, onToggle: onPlanToggle)
        } else if let rec = block as? ViewBlock.ResourceRec {
            ResourceRecView(block: rec, onOpen: onResourceOpen)
        } else {
            EmptyView() // unknown primitive → never rendered
        }
    }
}

private struct ProseLineView: View {
    let block: ViewBlock.ProseLine
    @Environment(\.theme) private var theme
    var body: some View {
        // Serif italic — the agent's voice. (Emphasis kept simple as accent-tinted
        // whole line when present; substring styling is an Android nicety.)
        Text(block.text)
            .font(.body.italic())
            .foregroundColor(theme.onSurface)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct StatGridView: View {
    let block: ViewBlock.StatGrid
    @Environment(\.theme) private var theme
    var body: some View {
        HStack(spacing: 10) {
            ForEach(Array(block.stats.enumerated()), id: \.offset) { _, stat in
                VStack(alignment: .leading, spacing: 2) {
                    Text(stat.value).font(.title2.weight(.bold)).foregroundColor(theme.primary)
                    Text(stat.label).font(.caption).foregroundColor(theme.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 12).padding(.horizontal, 8)
                .background(theme.surfaceVariant)
                .clipShape(RoundedCornerShape(10))
            }
        }
    }
}

private struct SparklineView: View {
    let block: ViewBlock.Sparkline
    @Environment(\.theme) private var theme
    private var points: [Double] { block.points.map { $0.doubleValue } }
    var body: some View {
        let maxV = max(points.max() ?? 0, 1)
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .bottom, spacing: 6) {
                ForEach(Array(points.enumerated()), id: \.offset) { i, p in
                    let frac = min(max(p / maxV, 0.06), 1.0)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(i == Int(block.highlightIndex) ? theme.primary : theme.surfaceVariant)
                        .frame(maxWidth: .infinity)
                        .frame(height: CGFloat(frac) * 56)
                }
            }
            .frame(height: 56)
            if let caption = block.caption {
                Text(caption).font(.caption).foregroundColor(theme.onSurfaceVariant)
            }
        }
    }
}

private struct PlanView: View {
    let block: ViewBlock.Plan
    var onToggle: (PlanRow) -> Void
    @Environment(\.theme) private var theme
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(block.heading).font(.subheadline.weight(.semibold)).foregroundColor(theme.onSurface)
            if let meta = block.meta {
                Text(meta).font(.caption).foregroundColor(theme.onSurfaceVariant)
            }
            ForEach(Array(block.items.enumerated()), id: \.offset) { _, row in
                PlanRowView(row: row, onToggle: onToggle)
            }
        }
    }
}

private struct PlanRowView: View {
    let row: PlanRow
    var onToggle: (PlanRow) -> Void
    @Environment(\.theme) private var theme
    private var tickable: Bool {
        row.actionable && (row.source == "task" || row.source == "learning")
    }
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: row.done ? "checkmark.circle.fill" : "circle")
                .foregroundColor(row.done ? theme.primary : (tickable ? theme.onSurfaceVariant : theme.outline))
            VStack(alignment: .leading, spacing: 2) {
                Text(row.title).foregroundColor(theme.onSurface)
                if let note = row.note {
                    Text(note).font(.caption).foregroundColor(theme.onSurfaceVariant)
                }
            }
            Spacer(minLength: 6)
            if let time = row.time {
                Text(time).font(.caption).foregroundColor(theme.primary)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { if tickable { onToggle(row) } }
    }
}

private struct ResourceRecView: View {
    let block: ViewBlock.ResourceRec
    var onOpen: (LearningResource) -> Void
    @Environment(\.theme) private var theme
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(block.goal + (block.level.map { " · \($0)" } ?? ""))
                .font(.caption).foregroundColor(theme.onSurfaceVariant)
            Text(block.resource.title).font(.subheadline.weight(.semibold)).foregroundColor(theme.onSurface)
            if !block.resource.why.isEmpty {
                Text(block.resource.why).font(.footnote).foregroundColor(theme.onSurfaceVariant)
            }
            Button { onOpen(block.resource) } label: {
                Label("Start reading", systemImage: "arrow.up.right.square").font(.footnote)
            }
            .foregroundColor(theme.primary)
            .padding(.top, 4)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.surfaceVariant)
        .clipShape(RoundedCornerShape(10))
    }
}

/// The transient "composing your view…" affordance while the service runs.
struct ComposingIndicator: View {
    @Environment(\.theme) private var theme
    var body: some View {
        HStack(spacing: 10) {
            ProgressView().controlSize(.small).tint(theme.primary)
            Text("composing your view…").font(.callout).foregroundColor(theme.onSurfaceVariant)
        }
    }
}

/// The FIXED suggestion-chip row (shared curated copy). Taps compose a view.
struct SuggestionChipRow: View {
    var onChip: (SuggestionChip) -> Void
    @Environment(\.theme) private var theme
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(LifeAgentIos.shared.suggestionChips(), id: \.label) { chip in
                    Button { onChip(chip) } label: {
                        Text(chip.label).font(.footnote)
                            .padding(.horizontal, 12).padding(.vertical, 7)
                            .foregroundColor(theme.onSurface)
                            .background(theme.surfaceVariant)
                            .clipShape(Capsule())
                            .overlay(Capsule().stroke(theme.outline, lineWidth: 1))
                    }
                }
            }
            .padding(.horizontal, 12)
        }
    }
}
