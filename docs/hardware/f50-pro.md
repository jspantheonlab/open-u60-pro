# ZTE F50 Pro (MU3356)

Unisoc T760 (UMS9620), Android 15. Two embedded carrier SIMs plus, on the **插卡版**, an
external nano-SIM slot, with dual standby. The agent supports dual SIM: messages and calls
carry a slot, and it never waits for an on-screen "which SIM?" choice.

The catch: **firmware B15 and later no longer turn adb on from the admin page**, so on
current firmware you must root over Unisoc download mode before installing anything.

## 0. Buy the right one

Only the 插卡版 has the external slot for your own SIM. Check the firmware version in the
admin page before you start: the root guides below are tied to exact builds.

## 1. Get adb

- **Older firmware:** try the admin page USB debugging switch as on the
  [U30 Air / F50](u30-air-f50.md#1-turn-on-adb-no-root).
- **B15 and later:** root first (step 2). Rooted devices expose network adb at
  `192.168.0.1:5555`: `adb connect 192.168.0.1:5555`.

## 2. Root (needed on current firmware)

Community guides, each tied to a firmware build:

- **B28:** [againrivers/zte-f50pro-b28-root](https://github.com/againrivers/zte-f50pro-b28-root).
  Summary: back up all partitions (about 6 GB) with `spd_dump`, confirm B28
  (`ro.build.display.id`), patch only `init_boot` with Magisk while keeping the AVB
  structure (B28 does not need a TrustOS flash), fix the Magisk environment so boot scripts
  run, and pre-grant root for the screenless device.
- **macOS, one-tool flow:**
  [xaeolusw/zte-f50pro-macos-toolkit](https://github.com/xaeolusw/zte-f50pro-macos-toolkit)
  (patched TrustOS + `init_boot`, grants root to the adb shell).

Partition offsets differ between builds; never flash images made for another version, and
keep your backup: there is no official firmware to restore from.

## 3. Install the agent

Follow [android-agent-setup.md](android-agent-setup.md), using network adb:

```sh
adb connect 192.168.0.1:5555
ANDROID_SERIAL=192.168.0.1:5555 bash setup-device.sh
```

## 4. Dual SIM settings

In the stock admin page:

- turn on **双卡双待** (dual standby), so your SIM stays registered for SMS and calls while
  data uses whichever card;
- make your own (external) SIM the default for SMS and calls.

Without a default, the agent uses the lowest slot. The app's Device tab shows each SIM's
slot, carrier and defaults. Untested on hardware: whether ZTE's automatic card switching
(智能切卡) can take the external SIM off the network; turn it off if SMS stop arriving.
