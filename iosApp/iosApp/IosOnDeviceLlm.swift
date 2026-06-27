import Foundation
import Shared

// MLX Swift packages (added via SwiftPM — see iosApp/project.yml and README).
// Guarded so the app still compiles *before* the packages are added: in that
// state the LLM simply reports unavailable. Once `mlx-swift-examples` is wired
// in, `canImport(MLXLLM)` is true and real on-device inference is used.
#if canImport(MLXLLM)
import MLX
import MLXLLM
import MLXLMCommon
#endif

/// On-device LLM for Personal Agent (Step 3), running a **small quantized model
/// fully offline** via **MLX Swift** (Apple's Metal-backed array framework).
///
/// WHY MLX Swift (over llama.cpp via SwiftPM):
///   - **Apple-silicon native**: runs on the GPU/ANE via Metal with unified
///     memory — best perf/Watt on iPhone/iPad, no JNI/C++ bridging to maintain.
///   - **Swift-first API**: `LLMModelFactory` + `ModelContainer` + streaming
///     `generate(...)` map cleanly onto our synchronous seam + token callback.
///   - **Ready quantized models**: the `mlx-community` Hugging Face org ships
///     4-bit Llama 3.2 / Qwen 2.5 in MLX format — load straight from disk.
///   - Trade-off: **Apple silicon only** (A-series/M-series). It does **not**
///     run on the Intel iOS simulator; verify on a device or an Apple-silicon
///     Mac. llama.cpp would run more places but costs a C++ bridge and slower
///     Metal path; for an Apple-only target MLX is the better fit.
///
/// Model: **Llama 3.2 3B Instruct, 4-bit** (`mlx-community/Llama-3.2-3B-Instruct-4bit`,
/// ~1.8 GB). Swap to the 1B variant for low-memory devices — see
/// `LlmModelProvisioner.modelRepoId`. Weights are **never committed**; they are
/// provisioned into Application Support (see `LlmModelProvisioner`).
///
/// Implements the synchronous Kotlin seam `IosNativeLlm`; the Kotlin
/// `IosLlmAdapter` lifts this to the shared `suspend`/`Flow` `OnDeviceLlm`
/// contract on `Dispatchers.Default`. (Swift implementing a Kotlin `suspend`
/// function or producing a `Flow` directly is the fragile interop corner we
/// avoid — same reasoning as `IosEmbedder` in Step 2.)
final class IosOnDeviceLlm: IosNativeLlm {

    /// Reflects whether the model weights are present on this device.
    var isAvailable: Bool { LlmModelProvisioner.isAvailable }

    enum LlmError: Error, CustomStringConvertible {
        case unavailable
        case runtimeMissing
        var description: String {
            switch self {
            case .unavailable:
                return "On-device model not provisioned. See iosApp/README \"On-device LLM\"."
            case .runtimeMissing:
                return "MLX Swift packages are not linked into this build."
            }
        }
    }

    // MARK: - IosNativeLlm (synchronous seam)

    /// Blocking full generation. Called by the Kotlin adapter on
    /// `Dispatchers.Default`, never the main thread, so blocking here is safe.
    func generate(prompt: String, maxTokens: Int32, temperature: Float, stop: [String]) -> String {
        do {
            return try awaitSync {
                try await Self.run(
                    prompt: prompt,
                    maxTokens: Int(maxTokens),
                    temperature: temperature,
                    stop: stop,
                    onToken: nil
                )
            }
        } catch {
            // The seam returns String; surface the failure as text so the Kotlin
            // side (and UI) get a readable message rather than a crash. The
            // adapter still treats this as a completed call.
            return "⚠️ \(error)"
        }
    }

    /// Blocking streaming generation: invokes `onToken` per text chunk, returns
    /// when generation completes / a stop sequence / `maxTokens` is reached.
    func generateStream(
        prompt: String,
        maxTokens: Int32,
        temperature: Float,
        stop: [String],
        onToken: @escaping (String) -> Void
    ) {
        do {
            _ = try awaitSync {
                try await Self.run(
                    prompt: prompt,
                    maxTokens: Int(maxTokens),
                    temperature: temperature,
                    stop: stop,
                    onToken: onToken
                )
            }
        } catch {
            onToken("⚠️ \(error)")
        }
    }

    // MARK: - Async → sync bridge

    /// Run an async op to completion, blocking the *current* (background) thread.
    /// Safe because the only callers are the Kotlin adapter's `Dispatchers.Default`
    /// coroutines — this is never invoked on the main thread.
    private func awaitSync<T>(_ op: @escaping () async throws -> T) throws -> T {
        let sem = DispatchSemaphore(value: 0)
        var result: Result<T, Error>!
        Task.detached(priority: .userInitiated) {
            do { result = .success(try await op()) }
            catch { result = .failure(error) }
            sem.signal()
        }
        sem.wait()
        return try result.get()
    }

    // MARK: - MLX generation

#if canImport(MLXLLM)
    /// Single shared load of the model, so concurrent calls reuse one container.
    private static var loadTask: Task<ModelContainer, Error>?
    private static let loadLock = NSLock()

    private static func container() async throws -> ModelContainer {
        let task: Task<ModelContainer, Error> = {
            loadLock.lock(); defer { loadLock.unlock() }
            if let existing = loadTask { return existing }
            let created = Task { () throws -> ModelContainer in
                guard LlmModelProvisioner.isAvailable else { throw LlmError.unavailable }
                // Load straight from the local directory — **no network**.
                let configuration = ModelConfiguration(directory: LlmModelProvisioner.modelDirectory)
                return try await LLMModelFactory.shared.loadContainer(configuration: configuration)
            }
            loadTask = created
            return created
        }()
        return try await task.value
    }

    private static func run(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        stop: [String],
        onToken: ((String) -> Void)?
    ) async throws -> String {
        let container = try await container()
        let parameters = GenerateParameters(maxTokens: maxTokens, temperature: temperature)

        return try await container.perform { (context: ModelContext) -> String in
            // Apply the model's chat template to the raw prompt.
            let input = try await context.processor.prepare(
                input: UserInput(messages: [["role": "user", "content": prompt]])
            )

            var full = ""
            let stream = try MLXLMCommon.generate(input: input, parameters: parameters, context: context)
            for await event in stream {
                guard case .chunk(let text) = event else { continue }  // ignore .info
                let (delta, newFull, shouldStop) = Self.applyStop(full: full, chunk: text, stop: stop)
                if !delta.isEmpty { onToken?(delta) }
                full = newFull
                if shouldStop { break }
            }
            return full
        }
    }

    /// Honor `GenOptions.stop`: if a stop substring lands in `full + chunk`,
    /// truncate at its first occurrence and signal completion. Returns the text
    /// to emit (so we never stream past a stop), the new accumulated string, and
    /// whether to stop.
    private static func applyStop(full: String, chunk: String, stop: [String]) -> (String, String, Bool) {
        let combined = full + chunk
        var cut: String.Index?
        for s in stop where !s.isEmpty {
            if let r = combined.range(of: s), cut == nil || r.lowerBound < cut! {
                cut = r.lowerBound
            }
        }
        guard let cutIndex = cut else { return (chunk, combined, false) }
        let truncated = String(combined[..<cutIndex])
        let delta = truncated.count >= full.count ? String(truncated.dropFirst(full.count)) : ""
        return (delta, truncated, true)
    }
#else
    // MLX not linked yet: compile, but every call fails fast as unavailable.
    private static func run(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        stop: [String],
        onToken: ((String) -> Void)?
    ) async throws -> String {
        throw LlmError.runtimeMissing
    }
#endif
}
