import SwiftUI
import Shared
import UniformTypeIdentifiers

/// The Memory screen (iOS mirror of the Android one): everything the on-device
/// assistant remembers ABOUT THE USER, grouped by type, with delete + JSON
/// export/import. 🔒 Stored encrypted on-device; never sent to the cloud.
///
/// Embed via a `NavigationLink` from Settings, passing `appModel.memoryGraph`,
/// e.g. `MemoryView(graph: appModel.memoryGraph)`.
struct MemoryView: View {
    let graph: MemoryGraphService

    @State private var nodes: [MemoryNode] = []
    @State private var edges: [MemoryEdge] = []
    @State private var loading = true
    @State private var showImporter = false
    @State private var exportDoc: MemoryJsonDocument?
    @State private var showExporter = false

    private var grouped: [(type: String, items: [MemoryNode])] {
        Dictionary(grouping: nodes, by: { $0.type.name })
            .map { (type: $0.key, items: $0.value) }
            .sorted { $0.type < $1.type }
    }

    var body: some View {
        List {
            Section {
                Text("🔒 Stored encrypted on this device and never sent to the cloud. "
                     + "This is what the on-device assistant knows about you — you're in control.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            if !loading && nodes.isEmpty {
                Text("Nothing yet. As you chat with the on-device model, durable facts "
                     + "about you (people, preferences, goals…) show up here.")
                    .font(.callout).foregroundStyle(.secondary)
            }
            ForEach(grouped, id: \.type) { group in
                Section("\(group.type) (\(group.items.count))") {
                    ForEach(group.items, id: \.id) { node in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(node.label).font(.body)
                            ForEach(relations(for: node), id: \.self) { rel in
                                Text("• \(rel)").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                    .onDelete { offsets in delete(group.items, offsets) }
                }
            }
        }
        .navigationTitle("Memory")
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                Button("Export") { Task { await prepareExport() } }
                Button("Import") { showImporter = true }
            }
        }
        .task { await reload() }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json, .plainText]) { result in
            if case .success(let url) = result { Task { await runImport(url) } }
        }
        .fileExporter(isPresented: $showExporter, document: exportDoc, contentType: .json, defaultFilename: "memory-export") { _ in }
    }

    private func relations(for node: MemoryNode) -> [String] {
        let byId = Dictionary(uniqueKeysWithValues: nodes.map { ($0.id, $0.label) })
        return edges.compactMap { e in
            if e.fromId == node.id { return "\(e.relation.replacingOccurrences(of: "_", with: " ")) → \(byId[e.toId] ?? "?")" }
            if e.toId == node.id { return "\(byId[e.fromId] ?? "?") → \(e.relation.replacingOccurrences(of: "_", with: " "))" }
            return nil
        }
    }

    private func reload() async {
        loading = true
        nodes = (try? await graph.allNodes()) ?? []
        edges = (try? await graph.allEdges()) ?? []
        loading = false
    }

    private func delete(_ items: [MemoryNode], _ offsets: IndexSet) {
        let ids = offsets.map { items[$0].id }
        Task {
            for id in ids { _ = try? await graph.deleteNode(id: id) }
            await reload()
        }
    }

    private func prepareExport() async {
        let json = (try? await graph.exportJson()) ?? "{}"
        exportDoc = MemoryJsonDocument(text: json)
        showExporter = true
    }

    private func runImport(_ url: URL) async {
        guard url.startAccessingSecurityScopedResource() else { return }
        defer { url.stopAccessingSecurityScopedResource() }
        if let data = try? Data(contentsOf: url), let text = String(data: data, encoding: .utf8) {
            _ = try? await graph.importJson(text: text)
            await reload()
        }
    }
}

/// A trivial JSON document wrapper so `.fileExporter` can write the export.
struct MemoryJsonDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var text: String
    init(text: String) { self.text = text }
    init(configuration: ReadConfiguration) throws {
        text = configuration.file.regularFileContents.flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}
