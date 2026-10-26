#!/system/bin/sh
# Run as root on the device: mark package $1 "always allow" for premium/short-code SMS
# (policy 3 in SmsUsageMonitor's file), keeping other packages' entries. Reboot after.
set -e
pkg="$1"
f=/data/misc/sms/premium_sms_policy.xml
[ -n "$pkg" ] || { echo "usage: $0 <package>" >&2; exit 2; }
if [ ! -f "$f" ]; then
  mkdir -p /data/misc/sms
  printf '%s\n%s\n%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" '<premium-sms-policy>' '</premium-sms-policy>' > "$f"
fi
grep -v "name=\"$pkg\"" "$f" | grep -v '</premium-sms-policy>' > "$f.new"
printf '<package name="%s" sms-policy="3" />\n</premium-sms-policy>\n' "$pkg" >> "$f.new"
mv "$f.new" "$f"
chown radio:radio "$f" 2>/dev/null || true
chmod 660 "$f"
restorecon "$f" 2>/dev/null || true
cat "$f"
