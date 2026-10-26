# ZTE U30 Pro (MU5358)

Unisoc T780 (UMS9632), Android 15. adb can be turned on through Unisoc engineer mode
without root. Less community support than the U30 Air, and the OpenWrt port does not
cover this chip.

There are two firmware lines with the same model number: the original U30 Pro (e.g. B07)
and the **U30 Pro AI** (firmware containing `AI`, e.g. `U30Pro_AIV1.0.0B05`). The guide
below was verified on the AI line; check offsets before applying root steps to the other.

## 0. Buy the right one

Make sure your unit takes your own nano-SIM; embedded-only units can't use your number.

## 1. Turn on adb (no root)

From [foggggggg/u30pro-ai-root-ufitools](https://github.com/foggggggg/u30pro-ai-root-ufitools):

1. Open Unisoc engineer mode: `am start -n com.sprd.engineermode/.EngineerModeActivity`
   (from any shell, e.g. a UFI-TOOLS terminal), or dial `*#*#83781#*#*` in a dialer.
2. **DEBUG&LOG** → bottom of the list → **USB Debug** on.
3. Reboot, connect USB-C, accept the authorisation prompt.
4. In the admin page set **USB 上网协议** (USB internet protocol) to automatic: on RNDIS,
   adb disappears after boot.

Verified by its author, not guaranteed on every unit.

## 2. Install the agent

Follow [android-agent-setup.md](android-agent-setup.md). After `setup-device.sh` makes the
agent the default dialer, engineer mode by `*#*#83781#*#*` no longer opens from it; use
the `am start` command instead.

## 3. Root (optional)

Same guide, sections 1–6: full backup in download mode first (about 10 GB, your only way
back), patched TrustOS on the active slot, Magisk-patched `init_boot` keeping the original
vbmeta metadata (a plain AVB-off image bootloops). Root is checked with
`adb shell /debug_ramdisk/su -c id` (plain `su` is not on PATH; `setup-device.sh` looks
there too). Run `setup-device.sh` again after rooting so it can allow short-code SMS.
