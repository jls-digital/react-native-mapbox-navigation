#!/usr/bin/env bash
# Run every Maestro flow with a fresh install of the example app
# (uninstall + reinstall between flows) and location pre-granted, so
# permission-dependent flows start from a clean slate.
#
# Usage:
#   .maestro/run-all.sh                    # iOS, first booted simulator
#   .maestro/run-all.sh <udid>             # iOS, specific simulator
#   .maestro/run-all.sh ios [udid]
#   .maestro/run-all.sh android [serial]   # Android, first adb device
#
# Build the app first (`yarn example ios` / `yarn example android`) and keep
# Metro running (`yarn example start`, without CI=1).

set -euo pipefail

PLATFORM="ios"
case "${1:-}" in
  ios | android)
    PLATFORM="$1"
    shift
    ;;
esac
DEVICE="${1:-}"

BUNDLE_ID="ch.jls.reactnative.mapboxnavigation.example"
FLOW_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$FLOW_DIR/.." && pwd)"

if [ "$PLATFORM" = "ios" ]; then
  if [ -z "$DEVICE" ]; then
    DEVICE=$(xcrun simctl list devices booted | grep -oE '\([0-9A-F-]{36}\)' | head -1 | tr -d '()')
  fi
  if [ -z "$DEVICE" ]; then
    echo "No booted simulator. Boot one with 'xcrun simctl boot <udid>' first." >&2
    exit 1
  fi
  APP_PATH=$(find "$HOME/Library/Developer/Xcode/DerivedData" \
    -type d \
    -path '*Build/Products/Debug-iphonesimulator/ReactNativeMapboxNavigationExample.app' \
    -not -path '*.dSYM*' \
    -not -path '*Index.noindex*' \
    2>/dev/null | head -1)
  if [ -z "$APP_PATH" ]; then
    echo "No built .app found in DerivedData. Run 'yarn example ios' first." >&2
    exit 1
  fi
else
  ADB="${ANDROID_HOME:+$ANDROID_HOME/platform-tools/}adb"
  if [ -z "$DEVICE" ]; then
    DEVICE=$("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')
  fi
  if [ -z "$DEVICE" ]; then
    echo "No Android device or emulator found ('adb devices')." >&2
    exit 1
  fi
  APP_PATH="$REPO_DIR/example/android/app/build/outputs/apk/debug/app-debug.apk"
  if [ ! -f "$APP_PATH" ]; then
    echo "No debug APK at $APP_PATH. Run 'yarn example android' first." >&2
    exit 1
  fi
  # The dev client reaches Metro on the host via localhost:8081.
  "$ADB" -s "$DEVICE" reverse tcp:8081 tcp:8081 >/dev/null
fi

FLOWS=("$FLOW_DIR"/tc_*.yaml)
if [ ! -e "${FLOWS[0]}" ]; then
  echo "No tc_*.yaml flows found in $FLOW_DIR" >&2
  exit 1
fi

reinstall() {
  if [ "$PLATFORM" = "ios" ]; then
    xcrun simctl uninstall "$DEVICE" "$BUNDLE_ID" >/dev/null 2>&1 || true
    xcrun simctl install "$DEVICE" "$APP_PATH"
    # Maestro's `clearState: true` clears app data but doesn't reset iOS
    # permissions, hence the reinstall + explicit grant.
    xcrun simctl privacy "$DEVICE" grant location "$BUNDLE_ID" >/dev/null 2>&1 || true
  else
    "$ADB" -s "$DEVICE" uninstall "$BUNDLE_ID" >/dev/null 2>&1 || true
    "$ADB" -s "$DEVICE" install -r "$APP_PATH" >/dev/null
    for permission in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS; do
      "$ADB" -s "$DEVICE" shell pm grant "$BUNDLE_ID" "android.permission.$permission" >/dev/null 2>&1 || true
    done
  fi
}

PASS=()
FAIL=()

for flow in "${FLOWS[@]}"; do
  name=$(basename "$flow")
  echo "════════════════════════════════════════════════════════════"
  echo " ▶  $name ($PLATFORM)"
  echo "════════════════════════════════════════════════════════════"

  reinstall

  if MAESTRO_DRIVER_STARTUP_TIMEOUT=240000 maestro --device "$DEVICE" test "$flow"; then
    PASS+=("$name")
  else
    FAIL+=("$name")
  fi
done

echo
echo "════════════════════════════════════════════════════════════"
echo " Summary ($PLATFORM): ${#PASS[@]} passed, ${#FAIL[@]} failed"
echo "════════════════════════════════════════════════════════════"
# `${arr[@]+"${arr[@]}"}`: an empty array is "unbound" under `set -u` in
# bash 3.2 (macOS default), which made the script exit 1 with no failures.
for n in ${PASS[@]+"${PASS[@]}"}; do echo " ✅  $n"; done
for n in ${FAIL[@]+"${FAIL[@]}"}; do echo " ❌  $n"; done

[ ${#FAIL[@]} -eq 0 ]
