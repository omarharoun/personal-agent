// RootView.swift — the Path-A gate. Until the user connects their own Hermes, the
// Connect screen is the entire app; there is no default/hidden backend. Once
// connected, the drawer-based app shell takes over.

import SwiftUI
import Shared

struct RootView: View {
    @EnvironmentObject var env: AppEnvironment

    var body: some View {
        if env.isConnected {
            AppShell(env: env)
        } else {
            ConnectView()
        }
    }
}
