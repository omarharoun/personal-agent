import SwiftUI
import Shared

/// The "Set up your AI" view — the curated, honest, user-initiated model-setup
/// surface. Reused by the onboarding flow (`OnboardingFlowView`) and the Settings
/// entry (`AiModelSettingsView`). It renders ONLY the model picker + Wi-Fi toggle
/// + trust/license notes + the live download/verify/install state; the Skip /
/// Continue / Done chrome is supplied by the container.
///
/// Everything here is real: the progress bar reads byte counts from
/// `ProvisionState.Downloading(done,total)` via `ModelSetupModel`, and nothing
/// downloads until the user taps Download.
struct ModelSetupView: View {
    @ObservedObject var setup: ModelSetupModel

    private static let byteFormatter: ByteCountFormatter = {
        let f = ByteCountFormatter()
        f.countStyle = .file
        f.allowsNonnumericFormatting = false
        return f
    }()

    private func bytes(_ value: Int64) -> String {
        Self.byteFormatter.string(fromByteCount: max(value, 0))
    }

    var body: some View {
        Form {
            trustSection
            modelsSection
            wifiSection
            licenseSection
            statusSection
        }
    }

    // MARK: Trust + honesty note

    private var trustSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 8) {
                Label("On-device AI", systemImage: "cpu")
                    .font(.headline)
                Text("Personal Agent can run a small AI model **entirely on this device** — "
                     + "your text never leaves it for the model to work. The app works "
                     + "without it; you can set this up now or later in Settings.")
                    .font(.subheadline)
                Label {
                    Text("Models come from a **trusted source** and are **verified** "
                         + "(checksum) before they're ever used.")
                } icon: {
                    Image(systemName: "checkmark.seal.fill").foregroundStyle(.green)
                }
                .font(.footnote)
                Text("The model is downloaded once (it's large — see each size below) "
                     + "and then runs offline.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .padding(.vertical, 4)
        }
    }

    // MARK: Model picker

    private var modelsSection: some View {
        Section("Choose a model") {
            ForEach(setup.options, id: \.id) { option in
                Button {
                    setup.select(option)
                } label: {
                    modelRow(option)
                }
                .buttonStyle(.plain)
                .disabled(setup.isWorking)
            }
        }
    }

    private func modelRow(_ option: ModelOption) -> some View {
        let isSelected = option.id == setup.selected.id
        let isInstalled = option.id == setup.installedOptionId
        return HStack(alignment: .top, spacing: 12) {
            Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
                .padding(.top, 2)
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(option.displayName).font(.headline)
                    if isInstalled {
                        Text("Installed")
                            .font(.caption2).bold()
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(Color.green.opacity(0.18))
                            .clipShape(Capsule())
                            .foregroundStyle(.green)
                    }
                }
                Text("\(bytes(option.sizeBytes)) · \(option.quant)")
                    .font(.subheadline).foregroundStyle(.secondary)
                if option.requiresProviderAuth {
                    Label("Gated — provider license acceptance required",
                          systemImage: "lock.fill")
                        .font(.caption).foregroundStyle(.orange)
                }
            }
            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
    }

    // MARK: Wi-Fi-only toggle

    private var wifiSection: some View {
        Section {
            Toggle(isOn: Binding(
                get: { setup.wifiOnly },
                set: { setup.setWifiOnly($0) }
            )) {
                Label("Download over Wi-Fi only", systemImage: "wifi")
            }
            .disabled(setup.isWorking)
        } footer: {
            Text("On by default, so a large model doesn't use your mobile data. "
                 + "Turn off to allow downloading over cellular.")
        }
    }

    // MARK: License + gated note (for the selected model)

    private var licenseSection: some View {
        Section("License") {
            if let url = URL(string: setup.selected.licenseUrl) {
                Link(destination: url) {
                    Label(setup.selected.licenseName, systemImage: "doc.text")
                }
            } else {
                Label(setup.selected.licenseName, systemImage: "doc.text")
            }
            if setup.selected.requiresProviderAuth {
                Text("This is a **gated** model. Open the license above and accept the "
                     + "provider's terms before downloading — gated models may require "
                     + "provider authorization.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
    }

    // MARK: Live status / action

    @ViewBuilder
    private var statusSection: some View {
        Section {
            switch setup.phase {
            case .idle:
                if setup.isSelectedInstalled {
                    installedControls
                } else {
                    Button {
                        setup.download()
                    } label: {
                        Label("Download \(bytes(setup.selected.sizeBytes))", systemImage: "arrow.down.circle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }

            case let .downloading(done, total):
                VStack(alignment: .leading, spacing: 8) {
                    if total > 0 {
                        ProgressView(value: Double(done), total: Double(total))
                        Text("Downloading \(bytes(done)) of \(bytes(total))")
                            .font(.footnote).foregroundStyle(.secondary)
                    } else {
                        ProgressView()
                        Text("Downloading \(bytes(done))…")
                            .font(.footnote).foregroundStyle(.secondary)
                    }
                    Button("Cancel", role: .cancel) { setup.cancel() }
                }

            case .verifying:
                HStack(spacing: 10) {
                    ProgressView()
                    Text("Verifying the download…")
                }

            case .installed:
                Label("Installed and verified — on-device AI is ready.",
                      systemImage: "checkmark.circle.fill")
                    .foregroundStyle(.green)
                installedControls

            case let .failed(reason):
                VStack(alignment: .leading, spacing: 10) {
                    Label(reason, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.red)
                        .font(.footnote)
                    Button {
                        setup.retry()
                    } label: {
                        Label("Retry", systemImage: "arrow.clockwise").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
        }
    }

    /// Replace / delete controls shown once the selected model is installed.
    @ViewBuilder
    private var installedControls: some View {
        Button {
            setup.download()
        } label: {
            Label("Re-download / replace", systemImage: "arrow.triangle.2.circlepath")
        }
        Button(role: .destructive) {
            setup.delete()
        } label: {
            Label("Delete model", systemImage: "trash")
        }
    }
}
