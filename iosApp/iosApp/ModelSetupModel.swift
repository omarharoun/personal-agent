import Foundation
import Shared

/// SwiftUI-facing phase of a provisioning run — a Swift mirror of the shared
/// `ProvisionState` so views can `switch` cleanly without bridging Kotlin sealed
/// types at every call site.
enum ProvisionPhase: Equatable {
    case idle
    case downloading(done: Int64, total: Int64)
    case verifying
    case installed(path: String)
    case failed(reason: String)
}

/// Drives on-device model provisioning for both the onboarding "Set up your AI"
/// step and the Settings entry — the Swift counterpart of the Android
/// `ModelSetupViewModel`. All download/verify/install work goes through the shared
/// `ModelProvisioner` contract via `IosFactories.startProvision`; the UI never
/// touches the network directly.
///
/// Nothing downloads until `download()`/`retry()` is called — there is no
/// auto-download. State is published on the main thread (the Kotlin flow is
/// collected on `Dispatchers.Main`); UI updates are funneled through `DispatchQueue.main`
/// so `@Published` mutations always land on the main thread regardless of bridge.
final class ModelSetupModel: ObservableObject {

    /// Curated, trusted options from the SHARED catalog.
    let options: [ModelOption]

    @Published var selected: ModelOption
    /// Download-over-Wi-Fi only. Defaults ON, per the brief.
    @Published var wifiOnly: Bool = true
    @Published var phase: ProvisionPhase = .idle
    /// Id of the catalog model currently installed on-device, if any.
    @Published var installedOptionId: String?

    private let provisioner: ModelProvisioner
    private let onInstalledChange: () -> Void
    private var handle: IosProvisionHandle?

    init(provisioner: ModelProvisioner, onInstalledChange: @escaping () -> Void = {}) {
        self.provisioner = provisioner
        self.onInstalledChange = onInstalledChange
        // Canonical curated catalog (real pinned checksums + gated sentinels).
        let catalog = DefaultModelCatalog().options()
        self.options = catalog
        // Catalog is non-empty by construction; fall back defensively to DEFAULT.
        self.selected = catalog.first ?? DefaultModelCatalog.companion.DEFAULT
        refreshInstalled()
    }

    // MARK: - Derived state

    var isDownloading: Bool {
        if case .downloading = phase { return true }
        return false
    }

    /// True while download or verification is in flight (selection is locked).
    var isWorking: Bool {
        switch phase {
        case .downloading, .verifying: return true
        default: return false
        }
    }

    /// True when the *currently selected* option is the installed one.
    var isSelectedInstalled: Bool { installedOptionId == selected.id }

    // MARK: - Actions

    /// Re-check which catalog option is installed on-device right now.
    func refreshInstalled() {
        let installed = options.first { provisioner.isInstalled(option: $0) }
        installedOptionId = installed?.id
        if let installed = installed { selected = installed }
    }

    func select(_ option: ModelOption) {
        guard !isWorking else { return }
        selected = option
        if case .failed = phase { phase = .idle }
    }

    func setWifiOnly(_ enabled: Bool) { wifiOnly = enabled }

    /// Start (or restart) provisioning the selected model.
    func download() {
        guard !isWorking else { return }
        handle?.cancel()
        let option = selected
        phase = .downloading(done: 0, total: option.sizeBytes)
        handle = IosFactories.shared.startProvision(
            provisioner: provisioner,
            option: option,
            wifiOnly: wifiOnly
        ) { [weak self] state in
            // Collected on Dispatchers.Main; re-dispatch to guarantee main-thread
            // @Published mutation and preserve emission order (FIFO).
            DispatchQueue.main.async { self?.apply(state, for: option) }
        }
    }

    /// Retry after a failure (same as starting again).
    func retry() {
        phase = .idle
        download()
    }

    /// Cancel an in-flight download and reset to idle.
    func cancel() {
        handle?.cancel()
        handle = nil
        phase = .idle
    }

    /// Delete the installed bundle for the selected model.
    func delete() {
        guard !isWorking else { return }
        _ = provisioner.delete(option: selected)
        phase = .idle
        refreshInstalled()
        onInstalledChange()
    }

    // MARK: - Bridge

    private func apply(_ state: ProvisionState, for option: ModelOption) {
        switch state {
        case let downloading as ProvisionStateDownloading:
            phase = .downloading(done: downloading.done, total: downloading.total)
        case is ProvisionStateVerifying:
            phase = .verifying
        case let done as ProvisionStateInstalled:
            phase = .installed(path: done.path)
            installedOptionId = option.id
            onInstalledChange()
        case let failure as ProvisionStateFailed:
            phase = .failed(reason: failure.reason)
        default:
            break // Idle — nothing to render.
        }
    }
}
