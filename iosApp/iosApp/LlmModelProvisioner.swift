import Foundation

/// Where the on-device LLM weights live and how they get there (Step 3).
///
/// We do **not** ship or commit the model (a 4-bit Llama 3.2 3B is ~1.8 GB).
/// Instead the quantized model files are *provisioned* into the app's
/// Application Support directory, and `IosOnDeviceLlm` loads them **from disk
/// with no network at inference time**.
///
/// Two provisioning paths, both documented in `iosApp/README.md`:
///   1. **Download-on-first-run** (default): `ensureProvisioned()` fetches the
///      quantized files from the Hugging Face MLX community repo into
///      Application Support. This is the only moment the network is touched; it
///      is explicit and user-triggerable, never part of inference.
///   2. **Documented manual drop**: copy a local model folder into the same
///      directory (e.g. for an air-gapped build). `isAvailable` then reports true
///      and no download happens.
///
/// `isAvailable` reflects path (2)/(1)'s result: are the weights present on this
/// device right now?
enum LlmModelProvisioner {

    /// Hugging Face MLX-community repo id for the quantized model. Swap to
    /// `mlx-community/Llama-3.2-1B-Instruct-4bit` for low-memory devices, or a
    /// Qwen 2.5 4-bit repo, without touching any other code.
    static let modelRepoId = "mlx-community/Llama-3.2-3B-Instruct-4bit"

    /// Minimal set of files that must exist for the model to be loadable. (MLX
    /// reads `config.json` + the safetensors shards + the tokenizer.)
    private static let requiredFiles = ["config.json", "tokenizer.json"]

    /// `…/Application Support/models/<repo-leaf>` — stable, app-private, excluded
    /// from iCloud/iTunes backup (large regenerable blob).
    static var modelDirectory: URL {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let leaf = modelRepoId.split(separator: "/").last.map(String.init) ?? "llm-model"
        return support.appendingPathComponent("models/\(leaf)", isDirectory: true)
    }

    /// True iff the required model files are present on disk right now. Backs
    /// `OnDeviceLlm.isAvailable`. Cheap: just file-existence checks, no load.
    static var isAvailable: Bool {
        let dir = modelDirectory
        let fm = FileManager.default
        guard requiredFiles.allSatisfy({ fm.fileExists(atPath: dir.appendingPathComponent($0).path) })
        else { return false }
        // At least one weight shard must be present.
        let contents = (try? fm.contentsOfDirectory(atPath: dir.path)) ?? []
        return contents.contains { $0.hasSuffix(".safetensors") }
    }

    /// Mark the model directory as "do not back up" (it's large and regenerable).
    static func excludeFromBackup() {
        var url = modelDirectory
        guard FileManager.default.fileExists(atPath: url.path) else { return }
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? url.setResourceValues(values)
    }

    /// Ensure the directory exists (caller creates it before a manual drop or a
    /// download). Returns the directory URL.
    @discardableResult
    static func prepareDirectory() throws -> URL {
        let dir = modelDirectory
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }
}
