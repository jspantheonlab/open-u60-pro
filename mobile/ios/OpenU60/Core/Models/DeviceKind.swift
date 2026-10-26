import Foundation

/// Which agent the app is talking to. The U60 Pro runs zte-agent (the full API); the Android
/// hotspots (U30 Air, U30 Pro, F50 Pro) run android-agent, which serves only SMS, calls and
/// device info, and says so in `/api/capabilities`.
enum DeviceKind: Equatable {
    case u60
    case androidHotspot

    /// zte-agent has no `/api/capabilities`, so anything but `platform: android` is a U60.
    static func from(capabilities: [String: Any]?) -> DeviceKind {
        (capabilities?["platform"] as? String) == "android" ? .androidHotspot : .u60
    }
}

/// One SIM as android-agent reports it in `/api/device` `sims`. No number, IMSI or ICCID.
struct HotspotSim: Identifiable, Equatable {
    let slot: Int
    let name: String
    let embedded: Bool
    let defaultSMS: Bool
    let defaultVoice: Bool

    var id: Int { slot }

    static func list(from device: [String: Any]) -> [HotspotSim] {
        guard let raw = device["sims"] as? [[String: Any]] else { return [] }
        return raw.compactMap { s in
            guard let slot = (s["slot"] as? NSNumber)?.intValue else { return nil }
            return HotspotSim(
                slot: slot,
                name: s["name"] as? String ?? "",
                embedded: s["embedded"] as? Bool ?? false,
                defaultSMS: s["default_sms"] as? Bool ?? false,
                defaultVoice: s["default_voice"] as? Bool ?? false
            )
        }.sorted { $0.slot < $1.slot }
    }
}
