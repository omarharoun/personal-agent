// KnowledgeView.swift — the interactive knowledge map (Android KnowledgeGraphScreen):
// a force-directed node-link graph derived from the user's chat records (NOT Hermes
// memory). Deterministic Fruchterman–Reingold layout, pan + pinch-zoom, tap a node
// to reveal the real questions the user asked about it.

import SwiftUI
import Shared

@MainActor
final class KnowledgeModel: ObservableObject {
    @Published var graph: KnowledgeGraph?
    @Published var building = false
    @Published var error: String?

    private let env: AppEnvironment
    private let client: HermesClient?

    init(env: AppEnvironment) {
        self.env = env
        self.client = env.makeClient()
        graph = env.knowledgeService.cached()
        if env.knowledgeService.shouldRebuild(nowMillis: LifeAgentIos.shared.nowMillis()) { rebuild() }
    }

    func rebuild() {
        guard !building else { return }
        building = true
        error = nil
        _Concurrency.Task {
            do { graph = try await env.knowledgeService.rebuild(hermes: client, nowMillis: LifeAgentIos.shared.nowMillis()) }
            catch { self.error = "Couldn't build the map right now." }
            building = false
        }
    }
}

struct KnowledgeView: View {
    @StateObject private var model: KnowledgeModel
    @Environment(\.theme) private var theme

    @State private var scale: CGFloat = 1
    @State private var pan: CGSize = .zero
    @State private var selected: KnowledgeNode?
    @State private var positions: [String: CGPoint] = [:]      // normalized 0..1
    @State private var laidOutSignature = ""

    init(env: AppEnvironment) { _model = StateObject(wrappedValue: KnowledgeModel(env: env)) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            if let g = model.graph, !g.isEmpty {
                canvas(g)
            } else {
                emptyState
            }
        }
        .onAppear { layoutIfNeeded() }
        .onChange(of: model.graph?.sourceSignature) { _, _ in layoutIfNeeded() }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text("KNOWLEDGE MAP").hermesDisplayLabel().foregroundColor(theme.primary)
                Spacer()
                if model.building { ProgressView().controlSize(.small) }
                else { Button { model.rebuild() } label: { Image(systemName: "arrow.clockwise").foregroundColor(theme.primary) } }
            }
            Text("Derived from your conversations · not Hermes memory")
                .font(.caption).foregroundColor(theme.onSurfaceVariant)
            if let g = model.graph, !g.isEmpty {
                Text("\(g.nodes.count) topics · \(g.edges.count) links · \(g.sourceConversationCount) chats")
                    .hermesMono(size: 11).foregroundColor(theme.onSurfaceVariant)
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
    }

    private func canvas(_ g: KnowledgeGraph) -> some View {
        GeometryReader { geo in canvasBody(g, size: geo.size) }
    }

    private func canvasBody(_ g: KnowledgeGraph, size: CGSize) -> some View {
        let w = size.width, h = size.height
        let pad: CGFloat = 40
        let pixel: (String) -> CGPoint = { id in
            let n = positions[id] ?? CGPoint(x: 0.5, y: 0.5)
            let x = pad + n.x * (w - 2 * pad)
            let y = pad + n.y * (h - 2 * pad)
            let cx = w / 2, cy = h / 2
            return CGPoint(x: cx + (x - cx) * scale + pan.width,
                           y: cy + (y - cy) * scale + pan.height)
        }
        let maxW = max(1, g.nodes.map { $0.weight }.max() ?? 1)

        return ZStack {
                Canvas { ctx, _ in
                    for e in g.edges {
                        let a = pixel(e.from), b = pixel(e.to)
                        var path = Path(); path.move(to: a); path.addLine(to: b)
                        ctx.stroke(path, with: .color(theme.onSurfaceVariant.opacity(0.35)), lineWidth: 1.5 * scale)
                    }
                    for n in g.nodes {
                        let c = pixel(n.id)
                        let r = nodeRadius(n.weight, maxW) * scale
                        let rect = CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)
                        ctx.fill(Circle().path(in: rect), with: .color(color(n.type).opacity(0.9)))
                        ctx.stroke(Circle().path(in: rect), with: .color(theme.outline), lineWidth: 1.2 * scale)
                        let label = n.label.count > 18 ? String(n.label.prefix(17)) + "…" : n.label
                        let text = Text(label).font(.system(size: 12 * min(scale, 1.6))).foregroundColor(theme.onBackground)
                        ctx.draw(text, at: CGPoint(x: c.x, y: c.y + r + 10), anchor: .top)
                    }
                }
                .contentShape(Rectangle())
                .gesture(
                    SpatialTapGesture().onEnded { value in
                        selected = hitTest(value.location, in: g, pixel: pixel, maxW: maxW)
                    }
                )
                .simultaneousGesture(
                    DragGesture().onChanged { v in pan = CGSize(width: v.translation.width, height: v.translation.height) }
                )
                .simultaneousGesture(
                    MagnificationGesture().onChanged { v in scale = min(3.5, max(0.5, v)) }
                )

                if let node = selected {
                    VStack { Spacer(); nodeDetail(node) }
                }
            }
        }

    private func nodeDetail(_ node: KnowledgeNode) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Circle().fill(color(node.type)).frame(width: 12, height: 12)
                Text(node.label).font(.headline).foregroundColor(theme.onSurface)
                Text(node.type).hermesMono(size: 11).foregroundColor(theme.onSurfaceVariant)
                Spacer()
                Button { selected = nil } label: { Image(systemName: "xmark").foregroundColor(theme.onSurfaceVariant) }
            }
            if node.snippets.isEmpty {
                Text("No specific questions found for this topic in your chats.")
                    .font(.callout).foregroundColor(theme.onSurfaceVariant)
            } else {
                Text("YOU ASKED ABOUT THIS").hermesDisplayLabel(size: 11).foregroundColor(theme.onSurfaceVariant)
                ForEach(Array(node.snippets.enumerated()), id: \.offset) { _, s in
                    HStack(alignment: .top, spacing: 6) {
                        Text("“").font(.headline).foregroundColor(theme.primary)
                        Text(s).font(.callout).foregroundColor(theme.onSurface)
                    }
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.surface)
        .clipShape(RoundedCornerShape(16))
        .overlay(RoundedCornerShape(16).stroke(theme.outline, lineWidth: 1))
        .padding(12)
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "point.3.connected.trianglepath.dotted").font(.system(size: 44)).foregroundColor(theme.onSurfaceVariant)
            Text("Your knowledge map is empty").font(.headline).foregroundColor(theme.onBackground)
            Text("Chat with your agent, then build a map of the topics you've explored.")
                .font(.callout).foregroundColor(theme.onSurfaceVariant).multilineTextAlignment(.center)
            Button { model.rebuild() } label: {
                Text(model.building ? "Building…" : "Build map").foregroundColor(theme.onPrimary)
                    .padding(.horizontal, 20).padding(.vertical, 12).background(theme.primary).clipShape(RoundedCornerShape(8))
            }.disabled(model.building)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity).padding(24)
    }

    // MARK: layout + helpers

    private func layoutIfNeeded() {
        guard let g = model.graph, !g.isEmpty, g.sourceSignature != laidOutSignature else { return }
        positions = KnowledgeLayout.compute(g)
        laidOutSignature = g.sourceSignature
    }

    private func nodeRadius(_ weight: Float, _ maxW: Float) -> CGFloat {
        let t = maxW > 1 ? CGFloat((weight - 1) / (maxW - 1)) : 0
        return 7 + 15 * t
    }

    private func color(_ type: String) -> Color {
        switch type.lowercased() {
        case "person", "people": return theme.tertiary
        case "place", "location": return theme.onSecondaryContainer
        case "activity", "habit", "skill": return theme.primary
        case "concept", "idea": return theme.onSurfaceVariant
        case "entity", "thing", "product", "tool": return theme.error
        default: return theme.primary
        }
    }

    private func hitTest(_ p: CGPoint, in g: KnowledgeGraph, pixel: (String) -> CGPoint, maxW: Float) -> KnowledgeNode? {
        var best: KnowledgeNode?
        var bestDist = CGFloat.greatestFiniteMagnitude
        for n in g.nodes {
            let c = pixel(n.id)
            let d = hypot(p.x - c.x, p.y - c.y)
            if d < bestDist { bestDist = d; best = n }
        }
        return bestDist <= 34 * scale ? best : nil
    }
}

/// Deterministic Fruchterman–Reingold layout → normalized [0.06, 0.94] positions.
enum KnowledgeLayout {
    static func compute(_ g: KnowledgeGraph) -> [String: CGPoint] {
        let ids = g.nodes.map { $0.id }
        let n = ids.count
        guard n > 0 else { return [:] }
        var pos = [CGPoint](repeating: .zero, count: n)
        let index = Dictionary(uniqueKeysWithValues: ids.enumerated().map { ($1, $0) })

        // Initial ring + deterministic per-id jitter.
        for i in 0..<n {
            let angle = 2 * Double.pi * Double(i) / Double(n)
            let jitter = (Double(stableHash(ids[i]) & 0xFF) / 255.0 - 0.5) * 0.15
            pos[i] = CGPoint(x: 0.5 + cos(angle) * 0.35 + jitter, y: 0.5 + sin(angle) * 0.35 + jitter)
        }

        let k = max(0.05, 0.9 * (1.0 / sqrt(Double(n))))
        var temp = 0.12
        let edges: [(Int, Int)] = g.edges.compactMap { e in
            guard let a = index[e.from], let b = index[e.to] else { return nil }
            return (a, b)
        }

        for _ in 0..<320 {
            var disp = [CGPoint](repeating: .zero, count: n)
            for i in 0..<n {
                for j in (i + 1)..<n {
                    var dx = Double(pos[i].x - pos[j].x)
                    var dy = Double(pos[i].y - pos[j].y)
                    var dist = sqrt(dx * dx + dy * dy)
                    if dist < 1e-4 { dx = Double((i - j) % 7) * 1e-3 + 1e-4; dy = 1e-4; dist = sqrt(dx * dx + dy * dy) }
                    let rep = k * k / dist
                    disp[i].x += CGFloat(dx / dist * rep); disp[i].y += CGFloat(dy / dist * rep)
                    disp[j].x -= CGFloat(dx / dist * rep); disp[j].y -= CGFloat(dy / dist * rep)
                }
            }
            for (a, b) in edges {
                let dx = Double(pos[a].x - pos[b].x)
                let dy = Double(pos[a].y - pos[b].y)
                let dist = max(1e-4, sqrt(dx * dx + dy * dy))
                let att = dist * dist / k
                disp[a].x -= CGFloat(dx / dist * att); disp[a].y -= CGFloat(dy / dist * att)
                disp[b].x += CGFloat(dx / dist * att); disp[b].y += CGFloat(dy / dist * att)
            }
            for i in 0..<n {
                let d = max(1e-4, Double(hypot(disp[i].x, disp[i].y)))
                let m = min(d, temp)
                pos[i].x += CGFloat(Double(disp[i].x) / d * m)
                pos[i].y += CGFloat(Double(disp[i].y) / d * m)
                pos[i].x += (0.5 - pos[i].x) * 0.01
                pos[i].y += (0.5 - pos[i].y) * 0.01
            }
            temp = max(0.005, temp * 0.985)
        }

        // Normalize to [0.06, 0.94].
        let xs = pos.map { $0.x }, ys = pos.map { $0.y }
        let minX = xs.min() ?? 0, maxX = xs.max() ?? 1
        let minY = ys.min() ?? 0, maxY = ys.max() ?? 1
        func norm(_ v: CGFloat, _ lo: CGFloat, _ hi: CGFloat) -> CGFloat {
            hi - lo < 1e-4 ? 0.5 : 0.06 + (v - lo) / (hi - lo) * 0.88
        }
        var out: [String: CGPoint] = [:]
        for i in 0..<n { out[ids[i]] = CGPoint(x: norm(pos[i].x, minX, maxX), y: norm(pos[i].y, minY, maxY)) }
        return out
    }

    /// Stable FNV-1a hash so the layout is identical across launches.
    private static func stableHash(_ s: String) -> UInt32 {
        var h: UInt32 = 2166136261
        for b in s.utf8 { h = (h ^ UInt32(b)) &* 16777619 }
        return h
    }
}
