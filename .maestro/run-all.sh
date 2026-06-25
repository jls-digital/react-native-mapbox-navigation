#!/usr/bin/env bash
# Run every Maestro flow with a fresh install of the example app
# (uninstall + reinstall between flows). Resets iOS TCC state so
# permission-dependent flows start from a clean slate.
#
# Usage:
#   .maestro/run-all.sh           # uses the first booted simulator
#   .maestro/run-all.sh <udid>    # targets a specific simulator

set -euo pipefail

UDID="${1:-}"
if [ -z "$UDID" ]; then
  UDID=$(xcrun simctl list devices booted | grep -oE '\([0-9A-F-]{36}\)' | head -1 | tr -d '()')
fi
if [ -z "$UDID" ]; then
  echo "No booted simulator. Boot one with 'xcrun simctl boot <udid>' first." >&2
  exit 1
fi

BUNDLE_ID="ch.jls.reactnative.mapboxnavigation.example"
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

FLOW_DIR="$(cd "$(dirname "$0")" && pwd)"
FLOWS=("$FLOW_DIR"/tc_*.yaml)

if [ ${#FLOWS[@]} -eq 0 ] || [ ! -e "${FLOWS[0]}" ]; then
  echo "No tc_*.yaml flows found in $FLOW_DIR" >&2
  exit 1
fi

PASS=()
FAIL=()

for flow in "${FLOWS[@]}"; do
  name=$(basename "$flow")
  echo "════════════════════════════════════════════════════════════"
  echo " ▶  $name"
  echo "════════════════════════════════════════════════════════════"

  xcrun simctl uninstall "$UDID" "$BUNDLE_ID" >/dev/null 2>&1 || true
  xcrun simctl install "$UDID" "$APP_PATH"
  # Pre-grant location so flows don't race with a permission dialog.
  # Flows are still run post-uninstall so other state (app data, dev
  # menu shown-once flag) resets between runs.
  xcrun simctl privacy "$UDID" grant location "$BUNDLE_ID" >/dev/null 2>&1 || true

  if MAESTRO_DRIVER_STARTUP_TIMEOUT=240000 maestro --udid "$UDID" test "$flow"; then
    PASS+=("$name")
  else
    FAIL+=("$name")
  fi
done

echo
echo "════════════════════════════════════════════════════════════"
echo " Summary: ${#PASS[@]} passed, ${#FAIL[@]} failed"
echo "════════════════════════════════════════════════════════════"
for n in "${PASS[@]}"; do echo " ✅  $n"; done
for n in "${FAIL[@]}"; do echo " ❌  $n"; done

[ ${#FAIL[@]} -eq 0 ]
