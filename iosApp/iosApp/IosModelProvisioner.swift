import Foundation
import Network
import CryptoKit
import Shared

/// Real iOS implementation of the shared model-provisioning contract.
///
/// Implements the synchronous Kotlin seam `IosNativeModelProvisioner`; the Kotlin
/// `IosModelProvisioningAdapter` lifts this to the shared `ModelProvisioner`
/// (`Flow<ProvisionState>`) on `Dispatchers.Default`. Swift never *produces* a
/// Kotlin `Flow` — it runs the blocking download/verify/install and pushes
/// progress through the Kotlin callbacks it is handed. This mirrors
/// `IosOnDeviceLlm`/`IosLlmAdapter` (Step 3).
///
/// What it does, honestly:
///   • Wi-Fi gate: when `wifiOnly` is on and the device isn't on un-metered
///     Wi-Fi/Ethernet, it fails fast WITHOUT spending mobile data.
///   • Streams the bundle from the model's trusted `sourceUrl` to a `.part` file
///     in Application Support, reporting byte-accurate progress.
///   • Verifies SHA-256 before the file is ever promoted (skipped only when the
///     catalog entry ships an empty hash — same policy as the Android sibling).
///   • Promotes the verified file into place atomically and excludes it from
///     iCloud/iTunes backup (large, regenerable).
///   • There is NO auto-download — the adapter only runs this when the user taps
///     Download/Retry in the setup UI.
///
/// ⚠️ COORDINATION FLAG (catalog ↔ runtime format): the shared `ModelCatalog`
/// currently lists LiteRT `.task` bundles (the Android/MediaPipe runtime shape).
/// The iOS on-device LLM (`IosOnDeviceLlm`) runs **MLX**, which loads a *directory*
/// of `config.json` + `*.safetensors`, not a single `.task` file. This provisioner
/// faithfully downloads/verifies/installs whatever the catalog points at; making a
/// provisioned catalog model actually *drive MLX inference* needs either
/// MLX-format catalog entries (a safetensors bundle) or a `.task` runtime on iOS.
/// That reconciliation belongs to the shared-catalog owner — see the report. Until
/// then, `AppModel.refreshLlmAvailability()` treats a verified installed catalog
/// bundle as "AI is set up" so the onboarding/Settings UX is real end-to-end.
final class IosModelProvisioner: NSObject, IosNativeModelProvisioner {

    /// `…/Application Support/models/provisioned` — app-private, excluded from backup.
    private var baseDirectory: URL {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return support.appendingPathComponent("models/provisioned", isDirectory: true)
    }

    private func installedURL(_ fileName: String) -> URL {
        baseDirectory.appendingPathComponent(fileName)
    }

    // MARK: - IosNativeModelProvisioner

    func isInstalled(fileName: String) -> Bool {
        let url = installedURL(fileName)
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: url.path),
              let size = (attrs[.size] as? NSNumber)?.int64Value else { return false }
        return size > 0
    }

    func delete(fileName: String) -> Bool {
        let url = installedURL(fileName)
        guard FileManager.default.fileExists(atPath: url.path) else { return false }
        do { try FileManager.default.removeItem(at: url); return true } catch { return false }
    }

    func provision(
        sourceUrl: String,
        fileName: String,
        expectedSha256: String,
        expectedSize: Int64,
        wifiOnly: Bool,
        onProgress: @escaping (KotlinLong, KotlinLong) -> Void,
        onVerifying: @escaping () -> Void,
        isCancelled: @escaping () -> KotlinBoolean
    ) -> IosProvisionOutcome {
        let cancelled: () -> Bool = { isCancelled().boolValue }
        let report: (Int64, Int64) -> Void = { done, total in
            onProgress(KotlinLong(value: done), KotlinLong(value: max(total, 0)))
        }

        // Honor the Wi-Fi-only preference before spending any bytes.
        if wifiOnly && !Self.isOnUnmeteredNetwork() {
            return failed("Waiting for Wi-Fi. Connect to Wi-Fi, or turn off the Wi-Fi-only setting to download over mobile data.")
        }
        guard let remote = URL(string: sourceUrl) else {
            return failed("The model's source link is invalid.")
        }

        let dir = baseDirectory
        do {
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        } catch {
            return failed("Couldn't prepare storage: \(error.localizedDescription)")
        }

        let dest = installedURL(fileName)
        let partURL = dir.appendingPathComponent("\(fileName).part")
        try? FileManager.default.removeItem(at: partURL)

        // --- Download (blocking; runs on the Kotlin adapter's Dispatchers.Default) ---
        report(0, expectedSize)
        let downloader = ModelDownloadDelegate(
            expectedSize: expectedSize,
            onProgress: report,
            isCancelled: cancelled,
            destinationPart: partURL
        )
        switch downloader.run(remote: remote) {
        case .cancelled:
            try? FileManager.default.removeItem(at: partURL)
            return cancelledOutcome
        case .failure(let message):
            try? FileManager.default.removeItem(at: partURL)
            return failed(message)
        case .success:
            break
        }

        // --- Verify integrity BEFORE the model is ever used ---
        onVerifying()
        let expected = expectedSha256.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if !expected.isEmpty {
            guard let actual = Self.sha256Hex(of: partURL) else {
                try? FileManager.default.removeItem(at: partURL)
                return failed("Couldn't verify the download. It was not installed.")
            }
            if actual != expected {
                try? FileManager.default.removeItem(at: partURL)
                return failed("Verification failed: the downloaded file doesn't match the expected checksum. It was not installed.")
            }
        }

        // --- Promote the verified file into place ---
        do {
            if FileManager.default.fileExists(atPath: dest.path) {
                try FileManager.default.removeItem(at: dest)
            }
            try FileManager.default.moveItem(at: partURL, to: dest)
            Self.excludeFromBackup(dest)
        } catch {
            try? FileManager.default.removeItem(at: partURL)
            return failed("Couldn't install the model: \(error.localizedDescription)")
        }
        return installed(dest.path)
    }

    // MARK: - Outcome helpers (avoid Kotlin companion naming at the call site)

    private func failed(_ reason: String) -> IosProvisionOutcome {
        IosProvisionOutcome(installedPath: nil, failureReason: reason)
    }
    private func installed(_ path: String) -> IosProvisionOutcome {
        IosProvisionOutcome(installedPath: path, failureReason: nil)
    }
    private var cancelledOutcome: IosProvisionOutcome {
        IosProvisionOutcome(installedPath: nil, failureReason: nil)
    }

    // MARK: - Integrity + reachability + backup

    /// Streaming SHA-256 (1 MB chunks) so a multi-GB file never loads into memory.
    private static func sha256Hex(of url: URL) -> String? {
        guard let handle = try? FileHandle(forReadingFrom: url) else { return nil }
        defer { try? handle.close() }
        var hasher = SHA256()
        while true {
            let chunk = autoreleasepool { () -> Data in
                (try? handle.read(upToCount: 1024 * 1024)) ?? Data()
            }
            if chunk.isEmpty { break }
            hasher.update(data: chunk)
        }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }

    /// True only on a connected, un-metered (Wi-Fi / wired) network. One-shot
    /// snapshot via `NWPathMonitor` with a short timeout.
    private static func isOnUnmeteredNetwork() -> Bool {
        let monitor = NWPathMonitor()
        let semaphore = DispatchSemaphore(value: 0)
        var unmetered = false
        monitor.pathUpdateHandler = { path in
            unmetered = path.status == .satisfied
                && !path.isExpensive
                && !path.isConstrained
                && (path.usesInterfaceType(.wifi) || path.usesInterfaceType(.wiredEthernet))
            semaphore.signal()
        }
        monitor.start(queue: DispatchQueue(label: "model-provisioner.reachability"))
        _ = semaphore.wait(timeout: .now() + 3)
        monitor.cancel()
        return unmetered
    }

    /// Mark the installed bundle "do not back up" (large + regenerable).
    private static func excludeFromBackup(_ url: URL) {
        var url = url
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? url.setResourceValues(values)
    }
}

// MARK: - Blocking download with byte-accurate progress + cancellation

private enum DownloadResult {
    case success
    case cancelled
    case failure(String)
}

/// Drives a `URLSessionDownloadTask` to completion synchronously (semaphore-
/// bridged), reporting progress and honoring cancellation. Safe to block here:
/// the only caller is the Kotlin adapter's `Dispatchers.Default` coroutine, never
/// the main thread.
private final class ModelDownloadDelegate: NSObject, URLSessionDownloadDelegate {
    private let expectedSize: Int64
    private let onProgress: (Int64, Int64) -> Void
    private let isCancelled: () -> Bool
    private let destinationPart: URL

    private let semaphore = DispatchSemaphore(value: 0)
    private var result: DownloadResult = .failure("Download didn't start.")
    private var session: URLSession?

    init(
        expectedSize: Int64,
        onProgress: @escaping (Int64, Int64) -> Void,
        isCancelled: @escaping () -> Bool,
        destinationPart: URL
    ) {
        self.expectedSize = expectedSize
        self.onProgress = onProgress
        self.isCancelled = isCancelled
        self.destinationPart = destinationPart
    }

    func run(remote: URL) -> DownloadResult {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.waitsForConnectivity = false
        let session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
        self.session = session
        var request = URLRequest(url: remote)
        request.timeoutInterval = 30
        session.downloadTask(with: request).resume()
        semaphore.wait()
        session.invalidateAndCancel()
        return result
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        if isCancelled() {
            downloadTask.cancel()
            return
        }
        let total = totalBytesExpectedToWrite > 0 ? totalBytesExpectedToWrite : expectedSize
        onProgress(totalBytesWritten, total)
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        if let http = downloadTask.response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            result = .failure("Download failed (HTTP \(http.statusCode)). Please try again.")
            return
        }
        do {
            try? FileManager.default.removeItem(at: destinationPart)
            try FileManager.default.moveItem(at: location, to: destinationPart)
            result = .success
        } catch {
            result = .failure("Couldn't save the download: \(error.localizedDescription)")
        }
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        defer { semaphore.signal() }
        guard let error = error else { return } // success/failure already set above
        let nsError = error as NSError
        if nsError.code == NSURLErrorCancelled {
            result = isCancelled() ? .cancelled : .failure("Download was cancelled.")
        } else {
            result = .failure(error.localizedDescription)
        }
    }
}
