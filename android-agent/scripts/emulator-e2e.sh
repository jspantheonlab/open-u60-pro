#!/usr/bin/env bash
# End-to-end test against an emulator whose fake modem injects SMS and calls.
# Run setup-device.sh first (and reboot once, for the short-code SMS policy).
#
#   AGENT_PASSWORD=... scripts/emulator-e2e.sh
set -euo pipefail

ADB="${ADB:-adb}"
PORT_LOCAL="${PORT_LOCAL:-19090}"
: "${AGENT_PASSWORD:?set AGENT_PASSWORD}"
pass=0
fail=0
skip=0

check() { # name, python expression over `d` (the response data)
  local name="$1" expr="$2" json="$3"
  if python3 -c "import json,sys; d=json.loads(sys.argv[1]); assert d.get('ok') is not False, d; d=d.get('data'); assert ($expr), d" "$json" 2>/dev/null; then
    echo "  ok   $name"; pass=$((pass + 1))
  else
    echo "  FAIL $name: $json"; fail=$((fail + 1))
  fi
}

"$ADB" forward "tcp:$PORT_LOCAL" tcp:9090 >/dev/null
body=$(python3 -c 'import json,sys; print(json.dumps({"password": sys.argv[1]}))' "$AGENT_PASSWORD")
TOKEN=$(curl -sf -X POST "localhost:$PORT_LOCAL/api/auth/login" -d "$body" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["token"])')
api() { curl -s -H "Authorization: Bearer $TOKEN" "localhost:$PORT_LOCAL$1" ${2:+-d "$2"}; }
hex() { python3 -c 'import sys; print(sys.argv[1].encode("utf-16-be").hex().upper())' "$1"; }
calls() { api /api/call/status; }
focus() { "$ADB" shell dumpsys window | grep mCurrentFocus | tr -d '\r'; }

echo "SIM"
check "device lists the SIM in slot 0" "[x['slot'] for x in d['sims']]==[0]" "$(api /api/device)"
check "send on an explicit SIM" "True" "$(api /api/sms/send "{\"number\":\"10086\",\"sim\":0,\"message_body\":\"$(hex "e2e sim0 $RANDOM")\"}")"
code=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "localhost:$PORT_LOCAL/api/sms/send" -d '{"number":"10086","sim":1,"message_body":"0031"}')
[ "$code" = 503 ] && { echo "  ok   empty slot 1 is refused"; pass=$((pass + 1)); } || { echo "  FAIL empty slot 1 gave $code"; fail=$((fail + 1)); }

echo "SMS"
marker="e2e-$RANDOM 验证码 $RANDOM"
"$ADB" emu sms send 10690000 "$marker" >/dev/null
sleep 4
list=$(api /api/sms/list '{"page":0,"data_per_page":500}')
check "received SMS is listed, unread, UCS-2" \
  "any(bytes.fromhex(m['content']).decode('utf-16-be')=='''$marker''' and m['tag']=='1' and m['number']=='10690000' and m.get('sim')==0 for m in d['messages'])" "$list"
id=$(python3 -c "import json,sys; print(next(m['id'] for m in json.loads(sys.argv[1])['data']['messages'] if m['number']=='10690000'))" "$list")
check "mark read" "True" "$(api /api/sms/read "{\"id\":\"$id;\",\"tag\":0}")"
check "now read" "any(m['id']==$id and m['tag']=='0' for m in d['messages'])" "$(api /api/sms/list '{"page":0,"data_per_page":500}')"
check "capacity counts it" "d['sms_nvused_total']>=1" "$(api /api/sms/capacity)"
check "send to a short code" "True" "$(api /api/sms/send "{\"number\":\"10086\",\"message_body\":\"$(hex "e2e 查询 $RANDOM")\"}")"
sleep 4
case "$(focus)" in
  *com.android.phone*) echo "  FAIL short-code dialog is showing (policy not applied? reboot after setup)"; fail=$((fail + 1)) ;;
  *) echo "  ok   no short-code dialog"; pass=$((pass + 1)) ;;
esac
check "delete hides it" "True" "$(api /api/sms/delete "{\"id\":\"$id;\"}")"
check "gone from the list" "all(m['id']!=$id for m in d['messages'])" "$(api /api/sms/list '{"page":0,"data_per_page":500}')"

echo "Calls"
check "idle" "d['calls']==[]" "$(calls)"
"$ADB" emu gsm call 13800138000 >/dev/null; sleep 3
check "incoming" "[(c['dir'],c['stat'],c['number']) for c in d['calls']]==[('mt','incoming','13800138000')]" "$(calls)"
check "answer" "True" "$(api /api/call/answer '{}')"; sleep 2
check "active" "[c['stat'] for c in d['calls']]==['active']" "$(calls)"
check "dtmf" "True" "$(api /api/call/dtmf '{"digits":"1#"}')"
check "mute" "d['muted'] is True" "$(api /api/call/mute '{"enabled":true}')"
check "muted in status" "d['muted'] is True" "$(calls)"
# What the emulators' fake modems can drive (checked by what they do, skipped if not):
# a second incoming call is delivered only while the first call is outgoing, `gsm cancel`
# ends incoming calls only, and outgoing calls are answered by the modem on their own.
skipped() { echo "  skip $1 (this emulator's modem cannot simulate it)"; skip=$((skip + 1)); }
count() { python3 -c 'import json,sys; print(len(json.load(sys.stdin)["data"]["calls"]))'; }
"$ADB" emu gsm cancel 13800138000 >/dev/null; sleep 3
if [ "$(calls | count)" = 0 ]; then
  echo "  ok   remote hung up"; pass=$((pass + 1))
else
  skipped "remote hang-up"
  check "hang up" "True" "$(api /api/call/hangup '{}')"; sleep 3
fi
check "idle again" "d['calls']==[]" "$(calls)"
check "dial" "True" "$(api /api/call/dial '{"number":"10086"}')"; sleep 1
check "outgoing is listed as mo, alerting, on SIM 0" "[(c['dir'],c['stat'],c.get('sim')) for c in d['calls']]==[('mo','alerting',0)]" "$(calls)"
sleep 3
check "outgoing connects" "[c['stat'] for c in d['calls']]==['active']" "$(calls)"
"$ADB" emu gsm call 13900139000 >/dev/null; sleep 3
if [ "$(calls | count)" = 2 ]; then
  check "call waiting" "sorted((c['dir'],c['stat']) for c in d['calls'])==[('mo','active'),('mt','waiting')]" "$(calls)"
  "$ADB" emu gsm cancel 13900139000 >/dev/null; sleep 3
  check "waiting caller gave up" "[c['stat'] for c in d['calls']]==['active']" "$(calls)"
else
  skipped "call waiting"
fi
check "hang up outgoing" "True" "$(api /api/call/hangup '{}')"; sleep 3
check "idle after outgoing" "d['calls']==[]" "$(calls)"
"$ADB" emu gsm call 13700137000 >/dev/null; sleep 3
check "reject a ringing call" "True" "$(api /api/call/hangup '{}')"; sleep 3
check "rejected" "d['calls']==[]" "$(calls)"

echo "Auth"
code=$(curl -s -o /dev/null -w '%{http_code}' "localhost:$PORT_LOCAL/api/call/status")
[ "$code" = 401 ] && { echo "  ok   no token is 401"; pass=$((pass + 1)); } || { echo "  FAIL no token gave $code"; fail=$((fail + 1)); }

echo "$pass passed, $fail failed, $skip skipped"
[ "$fail" = 0 ]
