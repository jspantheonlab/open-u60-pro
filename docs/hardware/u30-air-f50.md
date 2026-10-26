# ZTE U30 Air / F50 (MU300)

Unisoc T760 (UMS9620), Android 12 under ZTE's MyOS. The U30 Air and the F50 are the same
board; the U30 Air adds a battery. **Recommended** hotspot for SMS: no root needed.

## 0. Buy the right one

Get a variant with a **removable nano-SIM slot** (the U30 Air global version). The
APAC/standard U30 Air has only embedded carrier SIMs and cannot use your number.

## 1. Turn on adb (no root)

1. Connect to the hotspot's Wi-Fi or USB, open the admin page and log in:
   `http://192.168.0.1/index.html#usb_port` (the USB port settings page).
2. Turn on USB debugging there, and connect the USB-C cable to your computer.
3. `adb devices` should list the device. Accept the prompt if one appears.

Community-reported, firmware dependent. Tested by others on U30 Air `SSVB14` and F50
`MU300_ZYV1.0.0B09`. If the option is missing on your firmware, root first (below) or ask in
the [UFI-TOOLS](https://github.com/kanoqwq/UFI-TOOLS) community for your version.

## 2. Install the agent

Follow [android-agent-setup.md](android-agent-setup.md).

## 3. Root (optional)

Root is only needed for short-code SMS without a dialog and for later experiments (call
audio). Community toolkit (Windows, plus a macOS tool):
[dikeckaan/zte-f50-toolkit](https://github.com/dikeckaan/zte-f50-toolkit). Its order:

1. Install the Unisoc USB drivers (Windows).
2. **Full backup** first (`backup_direct.bat` or the short-circuit variant). Keep it safe;
   it is your only recovery.
3. Flash firmware **B09** (erases user data), then `flash-root_direct.bat` (patched TrustOS
   and a Magisk-patched boot image), then install the Magisk app.

The device enters download mode powered off: start the script, then plug in USB (or short
the test points for the short-circuit method). Skip the toolkit's modem/IMEI patch: it is
not needed here, can brick the modem, and changing an IMEI is illegal in many places.

## 4. Optional: OpenWrt instead of Android

[mu300-linux](https://github.com/jizni/mu300-linux) installs OpenWrt 25.12 (or Ubuntu)
next to Android; a reboot switches between them. SMS and 5G data work there, **calls do
not** (no audio, the call drops after about 20 s), and the Android agent does not run under
it. Worth it only if you want a real router OS and only need SMS. Requires root and an
unlocked bootloader.
