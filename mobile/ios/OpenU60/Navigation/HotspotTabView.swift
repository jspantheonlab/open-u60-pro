import SwiftUI

/// Tabs for an Android hotspot (android-agent): only what that agent serves. The U60 screens
/// (dashboard, tools, router) call endpoints it does not have.
struct HotspotTabView: View {
    let client: AgentClient
    let authManager: AuthManager

    private enum Tab: Hashable { case sms, device, settings }
    @State private var selectedTab: Tab = .sms
    @State private var smsVM: SMSViewModel

    init(client: AgentClient, authManager: AuthManager) {
        self.client = client
        self.authManager = authManager
        _smsVM = State(initialValue: SMSViewModel(client: client, authManager: authManager))
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            SMSListView(viewModel: smsVM, client: client, authManager: authManager)
                .tabItem { Label("SMS", systemImage: "message") }
                .tag(Tab.sms)

            HotspotDeviceView(client: client, authManager: authManager)
                .tabItem { Label("Device", systemImage: "antenna.radiowaves.left.and.right") }
                .tag(Tab.device)

            SettingsView(client: client)
                .tabItem { Label("Settings", systemImage: "gearshape") }
                .tag(Tab.settings)
        }
    }
}
