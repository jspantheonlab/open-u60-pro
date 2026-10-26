# Hardware guide: which device, and how to set it up

This project turns a SIM in a pocket hotspot (or a small modem box) into something the
iPhone app can drive: read and send SMS, see and control calls, and on the U60 Pro, the
whole router. One guide per device:

| Device | Chip / OS | Agent | Guide |
|---|---|---|---|
| **ZTE U60 Pro** (MU5250) | Qualcomm SDX75, OpenWrt (ZWRT) | `zte-agent` (full) | [u60-pro.md](u60-pro.md) |
| **ZTE U30 Air / F50** (MU300) | Unisoc T760, Android 12 | `android-agent` | [u30-air-f50.md](u30-air-f50.md) |
| **ZTE F50 Pro** (MU3356) | Unisoc T760, Android 15, dual SIM | `android-agent` | [f50-pro.md](f50-pro.md) |
| **ZTE U30 Pro** (MU5358) | Unisoc T780, Android 15 | `android-agent` | [u30-pro.md](u30-pro.md) |
| **Quectel EC20/EC25 voice box** | Qualcomm LTE module + Linux PC | Asterisk (not this repo's agent) | [ec20-voice-gateway.md](ec20-voice-gateway.md) |

Installing the iOS app itself: [ios-app.md](ios-app.md).

## What works where

| | U60 Pro | U30 Air / F50 | F50 Pro | U30 Pro | EC20 box |
|---|---|---|---|---|---|
| Read / send SMS from the iPhone | ✅ tested on hardware | ✅ emulator-tested | ✅ emulator-tested | ✅ emulator-tested | ✅ (Asterisk) |
| See / answer / hang up calls | ✅ | ✅ emulator-tested | ✅ emulator-tested | ✅ emulator-tested | ✅ |
| **Call audio on the iPhone** | ❌ not built | ❌ unknown, likely not | ❌ unknown, likely not | ❌ unknown | ✅ **proven** (SIP) |
| Router features (signal, bands, eSIM, Wi-Fi…) | ✅ full | ❌ | ❌ | ❌ | – |
| Your own nano-SIM | ✅ | global version only | 插卡版 only | ✅ check the variant | ✅ |
| Getting a shell (adb) | easy, official installer | easy (admin page) | **hard** on new firmware: root first | medium (engineer mode) | it is a PC |
| Root needed | no | no (optional) | yes, on B15+ firmware | no (optional) | – |
| Price (rough) | high | low | low–mid | mid | low–mid + a PC |

"Emulator-tested" means the Android agent passes its 30 end-to-end checks on Android 12
and 15 emulators but has not yet run on that device. Report results in an issue.

## Recommendation

**1. SMS and data only (verification codes, notifications): ZTE U30 Air, global version.**
Cheapest, removable nano-SIM, adb from its own admin page without rooting, the most mature
community (UFI-TOOLS, an OpenWrt port), and the Android agent covers SMS and call control.
Same board as the F50 (MU300), which works the same way.

**2. You need to talk on calls from the iPhone: add a Quectel EC20/EC25 voice box.**
None of the hotspots can send call audio to the phone today. The proven way is a Quectel
EC20 module (with voice; e.g. EC20CEFAG for Chinese carriers) on a small PC or router,
running Asterisk with `chan_quectel`. The iPhone answers through a SIP app with push.
It handles SMS too, so it can replace the hotspot entirely if you don't need its Wi-Fi.

**3. You already own a U60 Pro: use it.** It is the only device with the full app
(signal, bands, eSIM profiles, router settings). It also has SMS and call control, without
call audio.

**Avoid unless you already have one:**
- **F50 Pro.** Dual SIM is attractive, but firmware B15 and later closed adb; you have to
  root it (flashing over Unisoc download mode) before anything can be installed.
- **U30 Pro.** Works through engineer mode, but it is a different chip (T780) with less
  community support, and the OpenWrt port does not cover it.
- **Any hotspot without a nano-SIM slot** (APAC/standard U30 Air, F50 Pro without 插卡):
  it can only use the carriers' built-in cards, not your number.

### The phone number you keep matters

The SIM must stay registered on the network to receive SMS and calls. In China, China
Unicom and China Telecom calls need VoLTE (2G/3G are mostly shut down); check that the
device or module has VoLTE enabled for your carrier before relying on calls.

## Safety

- Root and flashing guides below are **community work**, not this project's. Flashing
  can brick the device. Always make a full partition backup from your own device first:
  it is the only way back, since ZTE publishes no firmware for these models.
- The agents refuse connections from the mobile-data side, but treat the hotspot's Wi-Fi
  password as the key to your SMS: use a strong one.
- Don't run the agents or setup scripts on a device you don't own.
