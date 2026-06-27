import Foundation
import NaturalLanguage
import Shared

/// On-device text embedder for Personal Agent memory (Step 2).
///
/// APPROACH: Apple's NaturalLanguage `NLEmbedding.sentenceEmbedding(for:)`.
///   - 100% on-device, **no network**, nothing to download or ship in the bundle
///     (contrast with the Core ML all-MiniLM route, which would require shipping
///     a ~90 MB model). This keeps provisioning trivial and the repo binary-free.
///   - Produces a fixed-size sentence vector (Apple-defined dimension, typically
///     512 for English). This differs from Android's all-MiniLM-L6-v2 (384-dim),
///     which is fine: each device builds and queries its OWN vector index, so
///     vectors are never compared across platforms (per the Step 2 brief).
///
/// Implements the synchronous Kotlin seam `IosNativeEmbedder`; the Kotlin
/// `IosEmbedderAdapter` wraps this to satisfy the shared `suspend` `Embedder`
/// contract and runs it off the main thread. (Swift implementing a Kotlin
/// `suspend` function directly is the fragile corner of KMP interop we avoid.)
final class IosEmbedder: IosNativeEmbedder {

    private let sentence: NLEmbedding?
    private let dim: Int

    /// Stable, lifetime-constant vector size. The memory index relies on this
    /// never changing for a given device, so we capture it once at init.
    let dimension: Int32

    /// Fixed width used only if sentence embeddings are unavailable on this
    /// OS/locale, so `dimension` is always stable and non-zero.
    private static let fallbackDimension = 512

    init() {
        let e = NLEmbedding.sentenceEmbedding(for: .english)
        self.sentence = e
        let d = e?.dimension ?? IosEmbedder.fallbackDimension
        self.dim = d
        self.dimension = Int32(d)
    }

    func embed(text: String) -> KotlinFloatArray {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        var vec: [Float]
        if !trimmed.isEmpty, let doubles = sentence?.vector(for: trimmed) {
            vec = doubles.map { Float($0) }
        } else {
            // Either no NL model on this device, or NL produced no vector for
            // this string (very short / out-of-vocabulary). Use a deterministic
            // hashing bag-of-words fallback so we never return an empty or
            // wrong-sized vector.
            vec = Self.hashingEmbedding(trimmed, dimension: dim)
        }
        Self.l2NormalizeInPlace(&vec)
        return Self.toKotlin(vec, dimension: dim)
    }

    // MARK: - Helpers

    /// Deterministic FNV-1a bag-of-words fallback.
    ///
    /// NOTE: Swift's built-in `hashValue` is randomized per process, which would
    /// make these embeddings unstable across app launches — so we hash the UTF-8
    /// bytes ourselves with FNV-1a to keep the index reproducible.
    private static func hashingEmbedding(_ text: String, dimension: Int) -> [Float] {
        var v = [Float](repeating: 0, count: dimension)
        let tokens = text.lowercased().split { !$0.isLetter && !$0.isNumber }
        for token in tokens {
            let h = fnv1a(String(token))
            let idx = Int(h % UInt64(dimension))
            // A bit from the hash flips the sign so the vector isn't all-positive.
            let sign: Float = (h & 1) == 0 ? 1 : -1
            v[idx] += sign
        }
        return v
    }

    private static func fnv1a(_ s: String) -> UInt64 {
        var hash: UInt64 = 0xcbf29ce484222325
        let prime: UInt64 = 0x100000001b3
        for byte in s.utf8 {
            hash ^= UInt64(byte)
            hash = hash &* prime
        }
        return hash
    }

    /// L2-normalize so the memory layer can use plain dot-product as cosine
    /// similarity. (NLEmbedding vectors are not guaranteed unit-length.)
    private static func l2NormalizeInPlace(_ v: inout [Float]) {
        var sum: Float = 0
        for x in v { sum += x * x }
        let norm = sum.squareRoot()
        if norm > 0 {
            for i in v.indices { v[i] /= norm }
        }
    }

    /// Build the Kotlin `FloatArray` the shared code expects, padding/truncating
    /// to exactly `dimension` so the contract's `dimension` is always honored.
    private static func toKotlin(_ v: [Float], dimension: Int) -> KotlinFloatArray {
        let out = KotlinFloatArray(size: Int32(dimension))
        let n = min(v.count, dimension)
        var i = 0
        while i < n {
            out.set(index: Int32(i), value: v[i])
            i += 1
        }
        return out
    }
}
