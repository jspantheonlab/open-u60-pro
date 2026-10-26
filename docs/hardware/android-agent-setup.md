# Installing the Android agent (U30 Air, F50, F50 Pro, U30 Pro)

Do your device's guide first: it ends with a working `adb` connection. Then:

## 1. Get the files

Download everything from the latest
[`android-v*` release](https://github.com/jspantheonlab/open-u60-pro/releases) into one
folder: `openu60-agent-X.Y.Z.apk`, `setup-device.sh`, `allow-short-code-sms.sh`,
`SHA256SUMS`. Check them:

```sh
sha256sum -c SHA256SUMS          # macOS: shasum -a 256 -c SHA256SUMS
```

(Or build from source: `cd android-agent && ./gradlew assembleRelease`, then use
`android-agent/scripts/setup-device.sh`.)

You need `adb` (Android platform-tools), `curl` and `python3` on your computer.

## 2. Run the setup script

```sh
adb devices                       # the hotspot must be listed as "device"
bash setup-device.sh              # asks for a new agent password (8+ characters)
```

For network adb, select the device first: `adb connect 192.168.0.1:5555`, then
`ANDROID_SERIAL=192.168.0.1:5555 bash setup-device.sh`.

The script:

1. installs the APK and grants its permissions (SMS, phone);
2. makes it the **default dialer**: that is how Android lets an app see and control calls;
3. exempts it from battery optimisation so it keeps running;
4. with root (Magisk `su` or `adb root`), allows SMS to short codes such as 10086 without
   an on-screen "this may cost money" dialog the hotspot cannot show;
5. starts it and sets the password over the USB/adb link (the first password can never be
   set over Wi-Fi).

If it says so, **reboot once** (`adb reboot`) for the short-code permission to apply.
Without root, the first SMS to a short code needs "Always allow" tapped on the device;
devices without a screen can't, so avoid short codes or root the device.

## 3. Check it

```sh
adb forward tcp:19090 tcp:9090
curl -s localhost:19090/api/health        # {"ok":true,"data":{"password_set":true}}
```

Then on the iPhone: join the hotspot's Wi-Fi, open the app, gateway `192.168.0.1`, your
agent password. It recognises the hotspot and shows SMS, Device and Settings. Device
lists the SIMs and has the phone.

## 4. Settings on the hotspot

- Keep the SIM you care about **registered**: in the stock admin page, prefer your own SIM
  over the built-in carrier cards for SMS and calls, and turn off automatic card switching
  if it moves away from it.
- Make sure the carrier's **VoLTE** is on for that SIM if you want calls.
- The agent listens on port 9090 on Wi-Fi, USB and VPN interfaces only; connections from
  the mobile network are dropped.

## 5. Help test call audio

Whether these hotspots can send call audio to the phone is unknown. During an active call:

```sh
adb shell CLASSPATH=$(adb shell pm path com.openu60.agent | cut -d: -f2) \
    app_process /system/bin com.openu60.agent.probe.AudioProbe
```

It records nothing to disk, only reports levels and plays a short beep into the call.
Please share the output in an issue: it decides whether a call-audio bridge can be built.

## Updating

Run `setup-device.sh` again with the new APK. Releases are signed with the same key, so
they install over each other. A build you made yourself is signed with your debug key:
`adb uninstall com.openu60.agent` before switching between the two.

## Removing

```sh
adb uninstall com.openu60.agent
```

The stock dialer role returns to the system default; nothing else on the device was changed
except the optional short-code permission in `/data/misc/sms/premium_sms_policy.xml`.
