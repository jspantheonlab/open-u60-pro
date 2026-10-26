import SwiftUI

/// The Android hotspot's identity, its SIMs and the phone, from android-agent's `/api/device`.
struct HotspotDeviceView: View {
    let client: AgentClient
    let authManager: AuthManager

    @State private var device: [String: Any] = [:]
    @State private var error: String?
    @State private var showCall = false

    private var sims: [HotspotSim] { HotspotSim.list(from: device) }

    var body: some View {
        NavigationStack {
            List {
                if let error {
                    Section { Text(error).foregroundStyle(.red) }
                }

                Section("Hotspot") {
                    row("Model", device["model"] as? String)
                    row("Android", device["android"] as? String)
                    row("Build", device["build"] as? String)
                    row("Agent", device["agent_version"] as? String)
                }

                Section {
                    if sims.isEmpty {
                        Text("No active SIM").foregroundStyle(.secondary)
                    }
                    ForEach(sims) { sim in
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Slot \(sim.slot + 1): \(sim.name.isEmpty ? "SIM" : sim.name)")
                            HStack(spacing: 8) {
                                if sim.embedded { tag("Embedded") }
                                if sim.defaultSMS { tag("SMS") }
                                if sim.defaultVoice { tag("Calls") }
                            }
                        }
                        .accessibilityElement(children: .combine)
                    }
                } header: {
                    Text("SIMs")
                } footer: {
                    Text("SMS and calls use the SIM marked for them, or the first slot when none is.")
                }

                Section {
                    Button {
                        showCall = true
                    } label: {
                        Label("Phone", systemImage: "phone")
                    }
                    .disabled(device["default_dialer"] as? Bool == false)
                } footer: {
                    if device["default_dialer"] as? Bool == false {
                        Text("The agent is not the hotspot's default dialer, so it cannot place or control calls. Run setup-device.sh again.")
                    }
                }
            }
            .navigationTitle("Device")
            .refreshable { await load() }
            .task { await load() }
            .fullScreenCover(isPresented: $showCall) {
                CallView(viewModel: CallViewModel(client: client, authManager: authManager))
            }
        }
    }

    private func load() async {
        do {
            device = try await client.getJSON("/api/device")
            error = nil
        } catch {
            guard !error.isCancellation else { return }
            self.error = error.localizedDescription
        }
    }

    private func row(_ label: String, _ value: String?) -> some View {
        LabeledContent(label, value: value ?? "–")
    }

    private func tag(_ text: String) -> some View {
        Text(text)
            .font(.caption2)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(.quaternary, in: Capsule())
    }
}
