# Quectel EC20/EC25 voice gateway: call audio on the iPhone

None of the ZTE hotspots can send call audio to the phone today. This build can: the call
audio comes out of the modem over USB, Asterisk turns the SIM into a SIP line, and the
iPhone answers it in a SIP app. It also sends and receives SMS. This is not part of this
repository's code; the steps follow a published, working setup on China Unicom
([sparktour.me](https://blog.sparktour.me/en/posts/2022/10/08/quectel-ec20-asterisk-freepbx-gsm-gateway/))
and [asterisk-chan-quectel](https://github.com/IchthysMaranatha/asterisk-chan-quectel).

## Hardware

| Part | Notes |
|---|---|
| **Quectel EC20 with voice**, e.g. `EC20CEFAG` (China carriers) | Some EC20 variants are data only: check that the exact model supports voice and lists Quectel's "Voice over USB and UAC" application note. EC25 works the same; the EC200A is reported working by one user. |
| Mini PCIe → USB adapter with a SIM slot | Often a mini-SIM slot: use a nano → mini adapter. |
| 2 × 4G antennas (IPEX → SMA) | |
| A small always-on Linux PC | Any x86 mini PC (the reference used a Dell OptiPlex Micro with Debian). A Raspberry Pi may not power the module reliably; use a powered USB hub if you try. |

## 1. Prepare the module (AT commands)

Open the module's AT port (e.g. `minicom -D /dev/ttyUSB2`):

```text
ATI                                   # model and firmware
AT+COPS?                              # registered on your carrier?
AT+QCFG="ims",1                       # VoLTE on
AT+QMBNCFG="AutoSel",0
AT+QMBNCFG="deactivate"
AT+QMBNCFG="select","ROW_Generic_3GPP"   # the reference setup needed this on China Unicom
AT+CFUN=1,1                           # restart the module
AT+QCFG="ims"                         # expect +QCFG: "ims",1,1  (VoLTE ready)
AT+QCFG="usbcfg",0x2C7C,0x0125,1,1,1,1,1,0,1   # expose USB audio (UAC)
AT+QPCMV=1,2                          # voice over USB/UAC
```

After a restart, `aplay -L` on the PC should list an "Android, USB Audio" device.

## 2. Asterisk with chan_quectel

On Debian:

```sh
sudo apt install asterisk asterisk-dev build-essential autoconf automake libasound2-dev
git clone https://github.com/IchthysMaranatha/asterisk-chan-quectel
cd asterisk-chan-quectel
./bootstrap && ./configure --with-astversion=<your Asterisk major version> && make && sudo make install
sudo cp uac/quectel.conf /etc/asterisk/ && sudo systemctl restart asterisk
sudo asterisk -rx 'quectel show devices'          # the module should be listed
```

In `extensions.conf`: send incoming calls on `quectel0` to your iPhone's SIP extension,
and outgoing calls from it to `Dial(Quectel/quectel0/${EXTEN})`. Incoming SMS arrive as
dialplan events you can forward (Bark, Telegram, a webhook); send with
`asterisk -rx 'quectel sms quectel0 <number> "<text>"'`.

## 3. The iPhone

- Use a SIP app that supports **push**, so calls ring while the app is closed: e.g.
  Linphone or Acrobits Groundwire. Without push, calls show as busy when the app is not
  running.
- Reach the PC from outside: **Tailscale** (or WireGuard) on the PC and the iPhone is the
  simplest and keeps SIP off the public internet. If you must forward ports, forward SIP
  and the RTP range only, with strong passwords and fail2ban.

## Keep in mind

- China Unicom and China Telecom calls need VoLTE working on the module (step 1); China
  Mobile can still fall back to 2G for now.
- Audio is 8 kHz (phone quality).
- Resets wear the module's flash: don't run `AT+QPRTPARA=3` repeatedly.
- The SIP line is the SIM's number: protect the PC like a phone.
