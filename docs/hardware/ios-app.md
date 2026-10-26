# Installing the iOS app

The app (iOS 17+) works with every device in this guide: the full app on the U60 Pro, and
SMS, Device (SIMs, phone) and Settings on the Android hotspots, which it detects after
login.

## From a release (no Mac needed)

Every merge to `main` publishes an `ios-v*` release with an **unsigned** IPA
(`OpenU60-X.Y.Z-unsigned.ipa`). Sign and install it with your own Apple ID using a
sideloading tool such as **Sideloadly** or **AltStore** (Windows or macOS):

1. Download the IPA and check it against `SHA256SUMS`.
2. Open it in the sideloading tool, sign in with your Apple ID, install to the iPhone.
3. On the iPhone: Settings → General → VPN & Device Management → trust your Apple ID.
   On iOS 16+ also turn on Settings → Privacy & Security → **Developer Mode**.

With a **free** Apple ID the app stops opening after 7 days (re-sign to renew; AltStore
can do it automatically) and push features are not available (the signal Live Activity's
push updates). A paid developer account removes both limits.

## From source (Mac with Xcode)

```sh
brew install xcodegen
cd mobile/ios/OpenU60 && cp .env.example .env    # set TEAM_ID / DEVELOPMENT_TEAM
../scripts/build_and_run.sh                      # builds and installs on a connected iPhone
```

## Connect

Join the device's Wi-Fi, open the app, set the gateway (`192.168.0.1`; Settings → Detect
tries the usual addresses) and enter the agent password you set during setup. From
outside, use Tailscale on the device or on a PC next to it.
