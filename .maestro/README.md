# Maestro E2E flows

Each `tc_*.yaml` file maps to a test case in
`docs/test-cases/TEST_CASES.md`. Flows that require the simulated
route to finish use **preset 0** (the ~150m short Zurich route).

Every flow starts with `subflows/launch_dev_client.yaml`, which launches
the app, connects the expo-dev-client to Metro and dismisses the dev menu.
If a dev-client update changes that UI, fix it there.

## Prerequisites

- The example app built for the target platform (`yarn example ios` /
  `yarn example android`)
- Metro running: `yarn example start`. Don't set `CI=1`: Expo CLI then
  doesn't register the dev server and the dev client can't find it.
- Maestro needs Java 17 or newer on the `PATH` (or `JAVA_HOME`).

## Run all flows with a fresh install between each

```sh
./.maestro/run-all.sh                    # iOS, first booted simulator
./.maestro/run-all.sh <udid>             # iOS, specific simulator
./.maestro/run-all.sh android            # Android, first adb device
./.maestro/run-all.sh android <serial>   # Android, specific device
```

The script uninstalls + reinstalls the app before every flow and
pre-grants location, so permission state is reset (Maestro's
`clearState: true` clears app data but doesn't reset iOS permissions).
On Android it also runs `adb reverse tcp:8081 tcp:8081` so the dev client
reaches Metro on the host.

## Run a single flow

```sh
maestro --device <udid-or-serial> test .maestro/tc_01_01_home_loads.yaml
```

If Maestro reports `iOS driver not ready in time`, bump the startup
timeout:

```sh
MAESTRO_DRIVER_STARTUP_TIMEOUT=240000 maestro --device <udid> test <flow>
```
