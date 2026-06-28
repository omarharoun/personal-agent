import SwiftUI
import Shared

/// The second onboarding stage — the new **"Set up your AI"** step — shown once,
/// after first-run encryption setup (`RecoverySetupView`) completes. The full
/// once-only flow is:
///
///   Welcome → Recovery setup (existing) → **AI model setup (this)** → Done
///
/// Gated by `AppModel.needsModelOnboarding`; completing or skipping persists the
/// "done" flag so it never shows again (the user can still provision later in
/// Settings). Skipping is first-class: the app works without AI.
struct OnboardingFlowView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        // The child takes `model` in its init so it can build the StateObject-owned
        // ModelSetupModel from the (already-ready) provisioner.
        OnboardingFlowContent(model: model)
    }
}

private struct OnboardingFlowContent: View {
    @EnvironmentObject var model: AppModel
    @StateObject private var setup: ModelSetupModel

    private enum Step { case welcome, setup }
    @State private var step: Step = .welcome

    @MainActor
    init(model: AppModel) {
        _setup = StateObject(wrappedValue: model.makeModelSetupModel())
    }

    var body: some View {
        NavigationStack {
            switch step {
            case .welcome: welcomeView
            case .setup:   setupView
            }
        }
    }

    // MARK: Welcome

    private var welcomeView: some View {
        Form {
            Section {
                VStack(alignment: .leading, spacing: 12) {
                    Image(systemName: "sparkles")
                        .font(.largeTitle)
                        .foregroundStyle(Color.accentColor)
                    Text("Set up your AI")
                        .font(.title2).bold()
                    Text("Personal Agent can run a small AI model **on this device** so it "
                         + "can help you fully offline — nothing is sent anywhere for it to work.")
                        .font(.subheadline)
                    Text("It's optional: the app works without it, and you can always set "
                         + "it up later in Settings.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)
            }
            Section {
                Button {
                    step = .setup
                } label: {
                    Label("Set up on-device AI", systemImage: "cpu").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                Button("Skip for now") { model.completeModelOnboarding() }
                    .frame(maxWidth: .infinity)
            } footer: {
                Text("You can set up, replace, or remove the model anytime in Settings.")
            }
        }
        .navigationTitle("Welcome")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: AI model setup

    private var setupView: some View {
        ModelSetupView(setup: setup)
            .navigationTitle("Set up your AI")
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .bottom) { bottomBar }
    }

    private var bottomBar: some View {
        VStack(spacing: 8) {
            if setup.isSelectedInstalled {
                Button {
                    model.completeModelOnboarding()
                } label: {
                    Text("Continue").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            } else {
                Button("Skip for now") { model.completeModelOnboarding() }
                    .frame(maxWidth: .infinity)
            }
        }
        .padding()
        .background(.bar)
    }
}
