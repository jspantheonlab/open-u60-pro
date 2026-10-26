#!/usr/bin/env bash
# Install and configure the OpenU60 Android agent on a ZTE U30 Air / U30 Pro (or any
# Android device with telephony) over adb.
#
#   scripts/setup-device.sh [path/to/app.apk]
#
# Uses ANDROID_SERIAL if set. Root (Magisk `su`, or `adb root` on userdebug builds) is
# optional; without it short-code SMS (10086, 10010 ...) show a confirmation dialog on
# the device the first time, which a screenless hotspot cannot answer.
set -euo pipefail

PKG=com.openu60.agent
SELF="$(cd "$(dirname "$0")" && pwd)"
HERE="$(dirname "$SELF")"
# Repo checkout: scripts/device/ and the Gradle output. Release download: everything side by side.
POLICY_SH="$SELF/device/allow-short-code-sms.sh"
[ -f "$POLICY_SH" ] || POLICY_SH="$SELF/allow-short-code-sms.sh"
default_apk="$HERE/app/build/outputs/apk/release/app-release.apk"
[ -f "$default_apk" ] || default_apk="$(ls "$SELF"/openu60-agent-*.apk 2>/dev/null | tail -1)"
APK="${1:-$default_apk}"
ADB="${ADB:-adb}"
PORT_LOCAL="${PORT_LOCAL:-19090}"

die() { echo "error: $*" >&2; exit 1; }
sh_() { "$ADB" shell "$@" | tr -d '\r'; }

[ -f "$APK" ] || die "APK not found: $APK (build it with ./gradlew assembleRelease)"
"$ADB" get-state >/dev/null 2>&1 || die "no adb device (check ANDROID_SERIAL / USB debugging)"

SDK=$(sh_ getprop ro.build.version.sdk)
echo "device: $(sh_ getprop ro.product.model) / Android $(sh_ getprop ro.build.version.release) (API $SDK)"
[ "$(sh_ pm list features | grep -c 'feature:android.hardware.telephony$')" -ge 1 ] \
  || die "this device reports no telephony feature"

echo "installing $APK"
"$ADB" install -r "$APK" >/dev/null

perms="READ_SMS RECEIVE_SMS SEND_SMS CALL_PHONE READ_PHONE_STATE ANSWER_PHONE_CALLS"
[ "$SDK" -ge 33 ] && perms="$perms POST_NOTIFICATIONS"
for p in $perms; do sh_ pm grant "$PKG" "android.permission.$p"; done

echo "taking the default dialer role (Telecom binds the agent's InCallService)"
sh_ cmd role add-role-holder android.app.role.DIALER "$PKG" 0 >/dev/null
# `cmd role get-role-holders` exists from Android 13; Android 12 only has dumpsys.
holder=$(sh_ cmd role get-role-holders android.app.role.DIALER 2>/dev/null | grep -v -i unknown || true)
[ -n "$holder" ] || holder=$(sh_ dumpsys role | grep -A3 'name=android.app.role.DIALER$' | sed -n 's/.*holders=//p' | head -1)
[ "$holder" = "$PKG" ] || echo "warning: default dialer is '$holder', not $PKG; call control will not work"

echo "exempting it from battery optimisation"
sh_ cmd deviceidle whitelist "+$PKG" >/dev/null
sh_ cmd appops set "$PKG" RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1 || true

# Premium/short-code SMS: policy 3 = always allow, read by SmsUsageMonitor at boot.
root_sh=""
# Magisk on the rooted U30 Pro / F50 Pro puts su in /debug_ramdisk, not on PATH.
for su in su /debug_ramdisk/su; do
  if [ "$(sh_ "$su" -c id 2>/dev/null | grep -c 'uid=0')" -ge 1 ]; then root_sh="$su -c"; break; fi
done
if [ -z "$root_sh" ] && "$ADB" root >/dev/null 2>&1 && sleep 2 && "$ADB" wait-for-device && [ "$(sh_ id -u)" = 0 ]; then
  root_sh="sh -c"
fi
if [ -n "$root_sh" ]; then
  echo "allowing short-code SMS without a dialog (root)"
  "$ADB" push "$POLICY_SH" /data/local/tmp/allow-short-code-sms.sh >/dev/null
  sh_ "$root_sh 'sh /data/local/tmp/allow-short-code-sms.sh $PKG'"
  sh_ rm -f /data/local/tmp/allow-short-code-sms.sh
  REBOOT_NEEDED=1
  [ "$root_sh" = "sh -c" ] && { "$ADB" unroot >/dev/null 2>&1 || true; sleep 2; "$ADB" wait-for-device; }
else
  echo "note: no root; the first SMS to a short code will need 'Always allow' on the device screen"
fi

echo "starting the agent"
sh_ am start -n "$PKG/.MainActivity" >/dev/null
for _ in 1 2 3 4 5 6 7 8 9 10; do
  "$ADB" forward "tcp:$PORT_LOCAL" tcp:9090 >/dev/null
  curl -sf "localhost:$PORT_LOCAL/api/health" >/dev/null 2>&1 && break
  sleep 1
done
health=$(curl -sf "localhost:$PORT_LOCAL/api/health") || die "agent did not answer on :9090"

if echo "$health" | grep -q '"password_set":false'; then
  if [ -n "${AGENT_PASSWORD:-}" ]; then pw="$AGENT_PASSWORD"; else
    read -rsp "new agent password (8+ chars): " pw; echo
  fi
  body=$(python3 -c 'import json,sys; print(json.dumps({"new": sys.argv[1]}))' "$pw")
  curl -sf -X POST "localhost:$PORT_LOCAL/api/auth/password" -d "$body" >/dev/null \
    || die "setting the password failed (8+ characters?)"
  echo "password set"
else
  echo "password already set (change it with POST /api/auth/password and a token)"
fi
"$ADB" forward --remove "tcp:$PORT_LOCAL" >/dev/null 2>&1 || true

echo "done. The API is on port 9090 of the device's hotspot address."
[ "${REBOOT_NEEDED:-0}" = 1 ] && echo "reboot the device once so the SMS policy takes effect: adb reboot"
exit 0
