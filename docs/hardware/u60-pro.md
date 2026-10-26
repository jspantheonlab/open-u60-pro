# ZTE U60 Pro (MU5250)

Qualcomm SDX75, ZTE's OpenWrt build (ZWRT). The project's original device: the Rust
`zte-agent` serves the full API (SMS, calls, signal, bands, eSIM, router settings, live
updates) and a built-in web UI. No root or flashing needed.

## 1. Install the agent

Pick one (details in the [main README](../../README.md#quick-start)):

- **Browser, no tools:** open [open-u60-pro.vercel.app](https://open-u60-pro.vercel.app)
  in Chrome or Edge, enter the router password and a new agent password, connect USB,
  click Deploy.
- **Command line:**
  ```sh
  ./setup.sh <router-password> <agent-password>     # first time: enables adb, installs, sets up SSH
  ./deploy.sh <agent-password>                      # later updates over SSH (port 2222)
  ```
- **Prebuilt binary:** the `zte-agent` file on the
  [releases page](https://github.com/jesther-ai/open-u60-pro/releases/latest).

## 2. Connect the iPhone

Join the router's Wi-Fi, open the app ([ios-app.md](ios-app.md)), gateway `192.168.0.1`,
and the agent password. Or open `http://192.168.0.1:9090/` in a browser.

## 3. As an SMS / call gateway

- SMS: list, send, delete, forwarding rules (Telegram, Bark, webhooks) on the router
  itself, so codes arrive even when the app is closed.
- Calls: the app shows incoming calls and can answer, hang up and send DTMF. **The call
  audio stays on the router**: there is no way yet to hear it on the iPhone. For calls you
  talk on, add an [EC20 voice box](ec20-voice-gateway.md).
- Remote access: the agent works over Tailscale (see the main README).

## Do not

The device-specific warnings in [CLAUDE.md](../../CLAUDE.md) apply, above all: never
disable a daemon listed in `/etc/config/zte_topsw_daemon.conf` with `init.d disable`, or
the device sticks on the boot logo with no WAN.
