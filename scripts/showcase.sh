#!/usr/bin/env bash
# Records the README animation of KodeEditor, see docs/showcase.md.
#
# Usage: scripts/showcase.sh [output]
#   output: the animated WebP to write, default screenshot/KodeEditor.webp
#
# Environment:
#   ANDROID_SERIAL  the device to record on, default the first emulator. Only use an emulator: this changes its
#                   display size and installs the demo app.
#   KEEP_RAW        set to keep the raw recording next to the output
set -euo pipefail

cd "$(dirname "$0")/.."
OUTPUT="${1:-screenshot/KodeEditor.webp}"
PACKAGE="de.markusressel.kodeeditor.local"
ACTIVITY="de.markusressel.kodeeditor.demo.ComposeMainActivity"
LOG_TAG="KodeEditorShowcase"

# what the animation looks like, docs/showcase.md explains the values
SIZE="1080x2400"
DENSITY=420
WIDTH=480
FPS=15
QUALITY=55
LEAD_IN=0.3
# how long the system bars take to go away after the start of the demo, a slow emulator needs about 3 s
SETTLE="${SETTLE:-5}"

for tool in adb ffmpeg; do
    command -v "$tool" >/dev/null || { echo "missing: $tool" >&2; exit 1; }
done

if [ -z "${ANDROID_SERIAL:-}" ]; then
    ANDROID_SERIAL="$(adb devices | awk '/^emulator-[0-9]+\tdevice$/ { print $1; exit }')"
    [ -n "$ANDROID_SERIAL" ] || { echo "no emulator found, start one or set ANDROID_SERIAL" >&2; exit 1; }
fi
export ANDROID_SERIAL
case "$ANDROID_SERIAL" in
    emulator-*) ;;
    *) echo "warning: $ANDROID_SERIAL is not an emulator" >&2 ;;
esac

./gradlew -q app:assembleDebug
APK="$(ls -t app/build/outputs/apk/debug/*.apk | head -1)"
adb install -r -t "$APK" >/dev/null

reset_display() {
    adb shell wm size reset || true
    adb shell wm density reset || true
}
trap reset_display EXIT
adb shell wm size "$SIZE"
adb shell wm density "$DENSITY"
# animations at their normal speed, the appearance animation follows this setting
adb shell settings put global animator_duration_scale 1
# the demo hides the system bars, without this Android covers the top with a hint about that
adb shell settings put secure immersive_mode_confirmations confirmed

adb shell am force-stop "$PACKAGE"
adb logcat -c
adb shell rm -f /sdcard/showcase.mp4

# the demo waits with the scenario until it gets a second intent, so the recording starts without the system bars
adb shell am start -W -n "$PACKAGE/$ACTIVITY" --ez showcase true >/dev/null
sleep "$SETTLE"
adb shell screenrecord --size "$SIZE" --bit-rate 12000000 --time-limit 60 /sdcard/showcase.mp4 &
RECORDER=$!
recording_started=$(date +%s.%N)
sleep 1
# FLAG_ACTIVITY_SINGLE_TOP, the running demo gets it in onNewIntent
adb shell am start -f 0x20000000 -n "$PACKAGE/$ACTIVITY" --ez showcase_play true >/dev/null

# the scenario logs when it starts and ends, the time of the host is close enough to cut at
start=""
while IFS= read -r line; do
    case "$line" in
        *"$LOG_TAG"*start*) start=$(date +%s.%N) ;;
        *"$LOG_TAG"*end*) break ;;
    esac
done < <(timeout 90 adb logcat -s "$LOG_TAG:I" -v brief)
end=$(date +%s.%N)
[ -n "$start" ] || { echo "the showcase did not start" >&2; kill "$RECORDER"; exit 1; }

# screenrecord writes a complete file when it gets SIGINT
adb shell pkill -INT screenrecord || true
wait "$RECORDER" || true
sleep 1

RAW="$(mktemp --suffix=.mp4)"
adb pull -q /sdcard/showcase.mp4 "$RAW"
adb shell rm -f /sdcard/showcase.mp4

from=$(awk "BEGIN { x = $start - $recording_started - $LEAD_IN; printf \"%.3f\", (x < 0 ? 0 : x) }")
duration=$(awk "BEGIN { printf \"%.3f\", $end - $start + $LEAD_IN }")
ffmpeg -v error -y -ss "$from" -t "$duration" -i "$RAW" \
    -vf "fps=$FPS,scale=$WIDTH:-2:flags=lanczos" \
    -c:v libwebp_anim -lossless 0 -quality "$QUALITY" -compression_level 6 -loop 0 -an \
    "$OUTPUT"

if [ -n "${KEEP_RAW:-}" ]; then mv "$RAW" "${OUTPUT%.*}.raw.mp4"; else rm -f "$RAW"; fi
echo "$OUTPUT: $(du -h "$OUTPUT" | cut -f1), $(printf '%.1f' "$duration") s"
