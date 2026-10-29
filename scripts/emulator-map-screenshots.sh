#!/usr/bin/env bash
# Screenshots of the real map on an emulator: dark, light, 200% text, and a tapped street.
# The map needs real graphics and internet (OpenFreeMap tiles), so it can't be done in the
# Robolectric tests. Run by the "Emulator map screenshots" job; pictures land in shots/.
set -euo pipefail
APK=$(ls ./*.apk | head -1)
PKG=io.github.secnewt.maps.debug
mkdir -p shots
adb logcat -c
adb install -r "$APK"
# Main Street, Garland: the screen's center is on the street, so a tap there finds it.
open_map() {
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/io.github.secnewt.maps.MainActivity" --ed lat 32.9126413 --ed lng -96.6329141 --ed zoom 16.0
  sleep 25
}
shot() { adb exec-out screencap -p > "shots/$1.png"; echo "saved $1"; }
center_tap() {
  local size w h
  size=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1)
  w=${size%x*}; h=${size#*x}
  adb shell input tap $((w / 2)) $((h / 2))
  sleep 3
}

adb shell cmd uimode night yes
open_map; shot map-dark
center_tap; shot map-dark-street-tapped
adb shell input keyevent KEYCODE_BACK; sleep 1

adb shell cmd uimode night no
open_map; shot map-light

adb shell settings put system font_scale 2.0
open_map; shot map-light-200pct
adb shell settings put system font_scale 1.0

adb shell cmd uimode night yes
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/io.github.secnewt.maps.MainActivity" --ed lat 32.9126413 --ed lng -96.6329141 --ed zoom 13.0
sleep 25; shot map-dark-zoomed-out

# MapLibre's own messages (style, tile and font loading), to explain anything missing above.
adb logcat -d | grep -iE "maplibre|mbgl|glyph|NewtMap" > shots/maplibre-log.txt || true
