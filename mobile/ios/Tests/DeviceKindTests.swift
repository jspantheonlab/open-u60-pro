import Foundation

// Run from the repository root: sh scripts/test-ios-hotspot.sh
@main
struct DeviceKindTests {
    static func data(_ json: String) -> [String: Any] {
        try! JSONSerialization.jsonObject(with: Data(json.utf8)) as! [String: Any]
    }

    static func main() {
        precondition(DeviceKind.from(capabilities: nil) == .u60, "zte-agent answers 404")
        precondition(DeviceKind.from(capabilities: data(#"{"platform":"android","sms":true}"#)) == .androidHotspot)
        precondition(DeviceKind.from(capabilities: data(#"{"platform":"openwrt"}"#)) == .u60)

        let device = data(#"""
        {"model":"MU3356","sims":[
          {"slot":1,"name":"CT","embedded":true,"default_sms":false,"default_voice":false},
          {"slot":0,"name":"CMCC","embedded":false,"default_sms":true,"default_voice":true},
          {"name":"no slot"}]}
        """#)
        let sims = HotspotSim.list(from: device)
        precondition(sims.map(\.slot) == [0, 1], "Sorted by slot, entries without a slot dropped")
        precondition(sims[0] == HotspotSim(slot: 0, name: "CMCC", embedded: false, defaultSMS: true, defaultVoice: true))
        precondition(sims[1].embedded && !sims[1].defaultSMS)
        precondition(HotspotSim.list(from: data(#"{"model":"U60"}"#)).isEmpty)
        print("DeviceKindTests passed")
    }
}
