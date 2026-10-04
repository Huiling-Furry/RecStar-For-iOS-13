#!/usr/bin/env bash
# Compose/Skiko simulator smoke test.
#
# What this PROVES:
#   the Compose Multiplatform runtime (Skiko/Metal) can start, render a first
#   frame and stay alive on an iOS simulator.
#
# What this does NOT prove:
#   iOS 13 compatibility. Apple no longer ships iOS 13 simulator runtimes, so
#   this runs on the newest runtime available on the runner. iOS 13 specific
#   API misuse cannot be caught here (Kotlin/Native cinterop has no
#   availability checking) - that still needs a real device.
set -euo pipefail

SCHEME="iosApp"
BUNDLE_ID="com.sdercolin.recstar"
DERIVED="$GITHUB_WORKSPACE/build/DerivedData-Simulator"
SIM_NAME="RecStarSmoke"
OUT="$GITHUB_WORKSPACE/ci-artifacts"
mkdir -p "$OUT"
REPORT="$OUT/simulator-smoke-report.md"

echo "== 1/6 Build Kotlin framework for iOS simulator =="
./gradlew :shared:compileKotlinIosSimulatorArm64 -Dos.name="Mac OS X" --console=plain

echo "== 2/6 Build app for simulator =="
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme "$SCHEME" \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath "$DERIVED" \
  ONLY_ACTIVE_ARCH=YES \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  build

APP="$DERIVED/Build/Products/Debug-iphonesimulator/RecStar.app"
if [[ ! -d "$APP" ]]; then
  echo "::error::App bundle not found at $APP"
  find "$DERIVED/Build/Products" -maxdepth 2 -name '*.app' || true
  exit 1
fi

echo "== 3/6 Boot simulator =="
RUNTIME=$(xcrun simctl list runtimes -j | python3 -c 'import json,sys
r=[x for x in json.load(sys.stdin)["runtimes"] if x["name"].startswith("iOS") and x.get("isAvailable")]
print(r[-1]["identifier"])')
DEVICE=$(xcrun simctl list devicetypes -j | python3 -c 'import json,sys
d=[x for x in json.load(sys.stdin)["devicetypes"] if "iPhone" in x["identifier"]]
print(d[-1]["identifier"])')
echo "runtime=$RUNTIME"
echo "device=$DEVICE"
echo "$RUNTIME" > "$OUT/simulator-runtime.txt"

xcrun simctl delete "$SIM_NAME" >/dev/null 2>&1 || true
xcrun simctl create "$SIM_NAME" "$DEVICE" "$RUNTIME"
xcrun simctl boot "$SIM_NAME"
xcrun simctl bootstatus "$SIM_NAME" -b
# Pre-grant microphone so the permission dialog cannot cover the first frame.
xcrun simctl privacy booted grant microphone --bundle "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl install booted "$APP"

echo "== 4/6 Launch app =="
xcrun simctl launch booted "$BUNDLE_ID" | tee "$OUT/launch.txt" || true
PID=$(awk -F': ' 'NF>1 {print $2}' "$OUT/launch.txt" | tr -d ' ' | head -1)
echo "pid=$PID"

# Give Compose enough time to attach the Metal layer and draw a first frame.
sleep 25

echo "== 5/6 Capture screenshot and logs =="
xcrun simctl io booted screenshot "$OUT/first-frame.png" || true
xcrun simctl spawn booted log show --last 3m --style compact \
  --predicate "process == \"RecStar\"" > "$OUT/app-log.txt" 2>&1 || true

ALIVE="unknown"
if [[ -n "$PID" ]] && xcrun simctl spawn booted ps -p "$PID" >/dev/null 2>&1; then
  ALIVE="yes"
else
  ALIVE="no"
fi
echo "alive=$ALIVE"

# Analyse the screenshot: a fully black frame means Compose never rendered.
FRAME="unknown"
if command -v python3 >/dev/null 2>&1; then
  python3 -m pip install --quiet --user pillow >/dev/null 2>&1 || true
  FRAME=$(python3 - <<'PY' || echo "unknown"
try:
    from PIL import Image
    im = Image.open("$OUT/first-frame.png").convert("RGB")
    px = list(im.getdata())
    total = len(px)
    non_black = sum(1 for p in px if p[0] + p[1] + p[2] > 30)
    colors = len(set(px))
    ratio = non_black / total
    if ratio < 0.01:
        print("black")
    elif colors < 8:
        print("flat:%d" % colors)
    else:
        print("rendered:%.0f%%:%d" % (ratio * 100, colors))
except Exception as e:
    print("unknown")
PY
)
fi
echo "frame=$FRAME"

echo "== 6/6 Write report =="
cat > "$REPORT" <<REPORT
# RecStar simulator smoke test

- Runtime: $RUNTIME
- Device: $DEVICE
- Bundle: $BUNDLE_ID
- Process alive after 25s: $ALIVE
- First frame: $FRAME
- Evidence: first-frame.png, app-log.txt

## Scope

This proves the Compose/Skiko runtime starts and renders on an iOS simulator.
It does NOT prove iOS 13 compatibility: Apple no longer distributes iOS 13
simulator runtimes, and Kotlin/Native cinterop performs no availability check,
so an iOS 14+ API call still compiles and only crashes on an old device.
Remaining iOS 13 risk must be closed on a real device.
REPORT

cat "$REPORT"

if [[ "$ALIVE" == "no" ]]; then
  echo "::error::App process died after launch"
  exit 1
fi
if [[ "$FRAME" == "black" ]]; then
  echo "::error::First frame is fully black - Compose may not be rendering"
  exit 1
fi
echo "Smoke test passed (alive=$ALIVE, frame=$FRAME)."
