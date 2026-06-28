import SwiftUI
import Shared

/// 🔞 18+ age gate — the first screen of onboarding, before recovery/AI setup.
///
/// Collects a date of birth and uses the shared, unit-tested age logic
/// (`com.personalagent.shared.age`) to decide eligibility:
///  - 18 or older → `model.confirmAgeIsAtLeast18()` and the app proceeds.
///  - under 18 (or the explicit "I'm under 18" choice) → a polite blocking screen
///    with no path forward. The app does not open for under-18s.
///
/// The date of birth is evaluated on-device and is never stored — only the
/// boolean confirmation is persisted (see `AppModel`).
struct AgeGateView: View {
    @EnvironmentObject var model: AppModel

    @State private var day = ""
    @State private var month = ""
    @State private var year = ""
    @State private var showInvalid = false
    @State private var blocked = false

    var body: some View {
        if blocked {
            UnderageBlockedView(onBack: { blocked = false })
        } else {
            form
        }
    }

    private var canSubmit: Bool {
        !day.isEmpty && !month.isEmpty && year.count == 4
    }

    private var form: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("You must be 18 or older to use this app")
                    .font(.title2).bold()
                Text("Personal Agent is intended for adults. Please confirm your date "
                     + "of birth to continue. This is checked on your device and your "
                     + "date of birth is not stored.")
                    .foregroundStyle(.secondary)

                Text("Date of birth").font(.headline).padding(.top, 8)
                HStack(spacing: 12) {
                    numberField("Day", text: $day, maxLen: 2)
                    numberField("Month", text: $month, maxLen: 2)
                    numberField("Year", text: $year, maxLen: 4)
                }

                if showInvalid {
                    Text("Please enter a valid date of birth.")
                        .font(.footnote).foregroundStyle(.red)
                }

                Button(action: submit) {
                    Text("Continue").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(!canSubmit)
                .padding(.top, 8)

                Button("I'm under 18") { blocked = true }
                    .frame(maxWidth: .infinity)
            }
            .padding(24)
        }
    }

    private func numberField(_ label: String, text: Binding<String>, maxLen: Int) -> some View {
        TextField(label, text: text)
            .keyboardType(.numberPad)
            .textFieldStyle(.roundedBorder)
            .onChange(of: text.wrappedValue) { newValue in
                let digits = String(newValue.filter(\.isNumber).prefix(maxLen))
                if digits != newValue { text.wrappedValue = digits }
                showInvalid = false
            }
    }

    private func submit() {
        guard let d = Int32(day), let m = Int32(month), let y = Int32(year) else {
            showInvalid = true
            return
        }
        if model.meetsAgeRequirement(year: y, month: m, day: d) {
            model.confirmAgeIsAtLeast18()
        } else {
            blocked = true
        }
    }
}

/// Terminal block for under-18 users — no way into the app from here.
private struct UnderageBlockedView: View {
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Spacer()
            Text("Personal Agent isn't available to you yet")
                .font(.title2).bold().multilineTextAlignment(.center)
            Text("This app is intended for people who are 18 or older, so we can't let "
                 + "you continue right now. Thank you for your understanding — please "
                 + "come back when you're 18.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
            // Lets someone fix a mistyped date — there is still no way into the app
            // without a date of birth that meets the 18+ requirement.
            Button("I entered the wrong date", action: onBack)
                .buttonStyle(.bordered)
            Spacer()
        }
        .padding(24)
    }
}
