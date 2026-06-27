import SwiftUI
import UIKit

/// 🔒 SECURITY-CRITICAL (Step 5) — First-run encryption setup.
///
/// Shown once, before the app's data is accessible, while `AppModel.needsSetup`
/// is true. It either (a) generates a new on-device encryption key and DISPLAYS
/// the user-held recovery code with the unambiguous "we cannot recover this for
/// you" warning, requiring explicit confirmation, or (b) restores the key on a
/// new device from a previously-saved recovery code.
///
/// ⚠️ PENDING-REVIEW / NOT-FOR-REAL-USERS — copy and flow need product/security
/// review; behavior must be verified on a real device.
struct RecoverySetupView: View {
    @EnvironmentObject var model: AppModel

    private enum Mode { case choose, showCode, enterCode }
    @State private var mode: Mode = .choose
    @State private var recoveryCode: String = ""
    @State private var enteredCode: String = ""
    @State private var savedConfirmed = false
    @State private var errorText: String?

    var body: some View {
        NavigationStack {
            switch mode {
            case .choose:      chooseView
            case .showCode:    showCodeView
            case .enterCode:   enterCodeView
            }
        }
    }

    // MARK: Choose

    private var chooseView: some View {
        Form {
            Section {
                Text("Your data is encrypted on this device")
                    .font(.title2).bold()
                Text("Personal Agent encrypts everything you store using a key that "
                     + "never leaves this device. To set up, create a recovery code — "
                     + "the only way to get your data back if you lose this device.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Section {
                Button {
                    generate()
                } label: {
                    Label("Create my recovery code", systemImage: "key.horizontal")
                }
                Button {
                    mode = .enterCode
                } label: {
                    Label("I already have a recovery code", systemImage: "arrow.clockwise")
                }
            }
            if let errorText {
                Section { Text(errorText).foregroundStyle(.red) }
            }
        }
        .navigationTitle("Set up encryption")
    }

    // MARK: Show generated code

    private var showCodeView: some View {
        Form {
            Section("Your recovery code") {
                Text(recoveryCode)
                    .font(.system(.body, design: .monospaced))
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button {
                    UIPasteboard.general.string = recoveryCode
                } label: {
                    Label("Copy", systemImage: "doc.on.doc")
                }
            }

            // The explicit, plain-language warning required by Step 5.
            Section {
                Label {
                    Text("Write this code down and keep it somewhere safe.")
                        .bold()
                } icon: {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(.orange)
                }
                Text("This code is the ONLY way to recover your data if you lose, "
                     + "reset, or replace this device. We never see it and we do not "
                     + "store a copy.\n\nIf you lose this code, your data cannot be "
                     + "recovered or reset — not by you, and not by us. It will be "
                     + "permanently lost.")
                    .font(.subheadline)
            }

            Section {
                Toggle(isOn: $savedConfirmed) {
                    Text("I have saved my recovery code somewhere safe and understand "
                         + "it cannot be recovered if I lose it.")
                }
                Button {
                    Task { await model.finishSetup() }
                } label: {
                    Text("Continue").frame(maxWidth: .infinity)
                }
                .disabled(!savedConfirmed)
                .buttonStyle(.borderedProminent)
            }
        }
        .navigationTitle("Save your recovery code")
        .navigationBarBackButtonHidden(true)
        .interactiveDismissDisabled(true)
    }

    // MARK: Enter existing code (restore)

    private var enterCodeView: some View {
        Form {
            Section("Enter your recovery code") {
                TextField("XXXX-XXXX-…", text: $enteredCode, axis: .vertical)
                    .font(.system(.body, design: .monospaced))
                    .autocorrectionDisabled(true)
                    .textInputAutocapitalization(.characters)
            }
            if let errorText {
                Section { Text(errorText).foregroundStyle(.red) }
            }
            Section {
                Button {
                    restore()
                } label: {
                    Text("Restore my data").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(enteredCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                Button("Back") { errorText = nil; mode = .choose }
            }
        }
        .navigationTitle("Restore")
    }

    // MARK: Actions

    private func generate() {
        errorText = nil
        do {
            recoveryCode = try model.generateRecoveryCode()
            savedConfirmed = false
            mode = .showCode
        } catch {
            errorText = "Could not set up encryption: \(error.localizedDescription)"
        }
    }

    private func restore() {
        errorText = nil
        do {
            try model.restore(fromRecoveryCode: enteredCode)
            Task { await model.finishSetup() }
        } catch {
            errorText = "That recovery code didn't work. Check it and try again."
        }
    }
}
