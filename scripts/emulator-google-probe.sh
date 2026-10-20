#!/usr/bin/env bash
# CI only: does "Ask Google" get coordinates for a text-only Google link?
# Expected for 4402 Broadway Blvd, Garland: about 32.860,-96.603 (within ~0.01).
set -euo pipefail
apk=$(ls ./*.apk | head -1)
adb install -r "$apk"
pkg=io.github.secnewt.maps.debug
adb logcat -c
adb shell am start -n "$pkg/io.github.secnewt.maps.GoogleProbeActivity" \
  -d "'https://maps.google.com/?q=4402+Broadway+Blvd+Garland+TX'"
for _ in $(seq 1 25); do
  line=$(adb logcat -d -s NewtGoogleProbe:I | grep RESULT || true)
  [ -n "$line" ] && break
  sleep 1
done
echo "probe: ${line:-no answer}"
case "$line" in
  *"RESULT 32.8"*|*"RESULT 32.9"*) echo "✅ Ask Google works"; exit 0 ;;
  *) echo "❌ Ask Google did not produce coordinates"; exit 1 ;;
esac
