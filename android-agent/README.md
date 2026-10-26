# OpenU60 Android agent

The device-side agent for Android-based ZTE hotspots, starting with the **U30 Air / F50
(MU300, Unisoc T760, Android 12)**, the **U30 Pro (MU5358, Unisoc T780, Android 15)** and
the **F50 Pro (MU3356, Unisoc T760, Android 15, dual SIM)**.
These run Android instead of OpenWrt, so `zte-agent` (ubus/uci) does not apply; this APK
serves the same `/api/` subset on port 9090 so the existing companion apps work unchanged.

| Area | Status | How |
|---|---|---|
| SMS list / capacity / send / mark read / delete | done, emulator-tested | `content://sms` (read only), `SmsManager` |
| Call status / dial / answer / hang up / reject / DTMF / mute / call waiting | done, emulator-tested | default dialer role + `InCallService` |
| Dual SIM (F50 Pro): pick the SIM, never wait for an on-screen SIM choice | done; one-SIM DSDS emulator-tested | explicit subscription / phone account |
| Short-code SMS (10086 …) without an on-screen dialog | done with root | `premium_sms_policy.xml`, policy 3 |
| Starts at boot, survives being killed | done | `BOOT_COMPLETED`, `SMS_RECEIVED`, sticky foreground service |
| Call audio over Wi-Fi | **not yet; needs hardware** | shell-UID PCM bridge (`VOICE_DOWNLINK` capture, Telephony Tx playback) |
| Push to iOS (CallKit / APNs) | not yet | |

## API

Same envelope as `zte-agent`: `{"ok":true,"data":…}` / `{"ok":false,"error":"…"}`, bearer
tokens from `POST /api/auth/login` (64 hex, 1 h idle, 24 h max, rate limited).

| Route | Notes |
|---|---|
| `GET /api/health` | no auth; `{"password_set":bool}` |
| `POST /api/auth/password` `{"new"}` | first password only from loopback (`adb forward`); later needs a token |
| `POST /api/auth/login` `{"password"}` / `POST /api/auth/logout` | |
| `GET /api/device`, `GET /api/capabilities` | `device.sims`: `[{"slot","name","embedded","default_sms","default_voice"}]` (no number, IMSI or ICCID); `capabilities.call_audio` is false until the PCM bridge exists |
| `POST /api/sms/list` `{"page","data_per_page"}` | ZTE wms shape: UCS-2 hex `content`, `"YY,MM,DD,HH,MM,SS,+Q"` `date`, `tag` 0–4 |
| `GET /api/sms/capacity` | provider counts, reported as NV |
| `POST /api/sms/send` `{"number","message_body","sim"?}` | body is UCS-2 hex, as the apps send it; optional `sim` slot |
| `POST /api/sms/read` / `POST /api/sms/delete` `{"id":"1;2;"}` | kept in the agent's overlay; the system provider is never written |
| `GET /api/call/status` | `{"calls":[{"id","dir":"mo/mt","stat","mode","number"}],"muted"}` with CLCC stat names |
| `POST /api/call/dial` `{"number","sim"?}`, `answer`, `hangup`, `dtmf` `{"digits"}`, `mute` `{"enabled"}` | `hangup` rejects a lone ringing call |

Connections are served only on loopback, hotspot/Wi-Fi, USB tethering, Bluetooth PAN and
VPN interfaces (`InterfacePolicy`); anything arriving on the cellular side is closed
without a reply, since a hotspot can hold a public address.

Why read/delete are an overlay: only the default SMS app may write the provider, and taking
that role from the stock ZTE software could break its own SMS handling.

## Dual SIM

Messages and calls carry a `sim` slot. Without `sim`, SMS go out on the system's default
SMS SIM and calls on the default voice SIM; when that is "ask every time" (a screenless
device cannot answer), the lowest slot is used. A call placed by anything else that stops
at the SIM choice is given the same SIM by the in-call service, so it never hangs there.
An empty or unknown slot is refused with 503.

The F50 Pro has two embedded carrier SIMs plus an external nano-SIM slot on the 插卡版,
and ZTE's own software switches between them. For an SMS/call gateway, turn on 双卡双待
(dual standby) in the stock admin page so the external SIM stays registered for SMS and
calls while data uses whichever card, and keep the external SIM as the default for SMS
and calls. Untested on hardware: whether 智能切卡 (automatic card switching) can still
take the external SIM off the network.

## Build and install

```sh
./gradlew testDebugUnitTest assembleRelease   # JDK 17, Android SDK 35
scripts/setup-device.sh                        # adb: install, grants, dialer role, password
```

Or take a prebuilt APK from the `android-v*` GitHub releases: download all the release
files into one directory and run `bash setup-device.sh` there. CI (`android-agent.yml`)
builds, tests and lints every pull request that touches this directory, and pushing an
`android-v1.2.3` tag publishes the release. Releases are signed with a fixed key from the
`ANDROID_KEYSTORE_BASE64` / `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` secrets so
each one installs over the last; local builds use the debug key, so uninstall first when
switching between the two.

`setup-device.sh` grants the runtime permissions, makes the agent the default dialer,
whitelists it from Doze, and (with Magisk `su` or `adb root`) allows short-code SMS without
a dialog; reboot once afterwards. Without root, answer **Always allow** on the device the
first time an SMS goes to a short code. Set `AGENT_PASSWORD` to skip the prompt.

Getting adb on the devices (community-tested, not by this project):
- U30 Air / F50: the stock web admin or the UFI-TOOLS installer.
- F50 Pro: newer firmware (B15 and later, per community reports) no longer opens adb from
  the admin page. The community route is root first (B28: patch `init_boot` only, Magisk;
  see [zte-f50pro-b28-root](https://github.com/againrivers/zte-f50pro-b28-root)), then
  network adb at `192.168.0.1:5555`: `adb connect 192.168.0.1:5555` and run
  `ANDROID_SERIAL=192.168.0.1:5555 scripts/setup-device.sh`. Back up all partitions first.
- U30 Pro: the Unisoc engineer mode **USB Debug** switch
  (`am start -n com.sprd.engineermode/.EngineerModeActivity` or `*#*#83781#*#*` in the
  dialer), then reboot; or root first. See
  [u30pro-ai-root-ufitools](https://github.com/foggggggg/u30pro-ai-root-ufitools).

Buy a variant with a **removable nano-SIM slot** (e.g. the U30 Air global version, the
F50 Pro 插卡版); the APAC/standard U30 Air only has embedded carrier SIMs.

## Testing without hardware

The emulator's modem fakes SMS and calls, which covers everything except call audio.
`scripts/emulator-e2e.sh` runs 30 checks (SIM choice, SMS in/out, short codes, read/delete, incoming,
answer, DTMF, mute, remote hang-up, outgoing, call waiting, reject, auth); it passes on
Android 12 (API 31, as the U30 Air) and Android 15 (API 35, as the U30 Pro and, with
`persist.radio.multisim.config=dsds`, an F50 Pro with one SIM) images. The emulator's
modem only has one SIM, so two live SIMs are covered by unit tests only.
By hand:

```sh
adb emu sms send 10086 "Your code is 482913"
adb emu gsm call 13800138000      # incoming; then gsm accept / cancel <number>
```

Without `kvm` group membership the emulator can run in Docker with `--device /dev/kvm
--group-add $(stat -c %g /dev/kvm)`; mount `~/.emulator_console_auth_token` (empty file =
no console auth) or `adb emu` will not connect.

## Call audio: what is left

The call-audio approach that works on Android phones (AirSIM, Samsung/Xiaomi) records the
remote side with `AudioRecord(VOICE_DOWNLINK)` and injects the local side through an
`AudioTrack` routed to the `TYPE_TELEPHONY` output, from a shell-UID process. Whether the
Unisoc T760/T780 audio HAL exposes both on these hotspots can only be checked on a device:

```sh
adb shell dumpsys media.audio_policy | grep -i -E "telephony|voice"
```

`AudioProbe` answers exactly that. Run it during an active call, as shell:

```sh
adb shell CLASSPATH=$(adb shell pm path com.openu60.agent | cut -d: -f2) \
    app_process /system/bin com.openu60.agent.probe.AudioProbe
```

It lists the audio devices, records 3 s from `VOICE_DOWNLINK` / `VOICE_UPLINK` /
`VOICE_CALL` (reporting only the level, nothing is saved), plays a quiet beep to the
Telephony Tx output (the other party should hear it), and prints a verdict. The emulator
has the Telephony devices but its HAL refuses voice capture, so only hardware can answer.

AirSIM is PolyForm Noncommercial; it is a design reference only and none of its code is
copied here.
