# Maestro E2E flows

Each `tc_*.yaml` file maps to a test case in
`docs/test-cases/TEST_CASES.md`. Flows that require the simulated
route to finish use **preset 0** (the ~150m short Zurich route).

## Run all flows with a fresh install between each

```sh
./.maestro/run-all.sh
```

The script uninstalls + reinstalls the app before every flow so TCC
(location permission) state is reset — Maestro's `clearState: true`
clears app data but doesn't reset iOS permissions.

## Run a single flow

```sh
maestro --udid <simulator-udid> test .maestro/tc_01_01_home_loads.yaml
```

If Maestro reports `iOS driver not ready in time`, bump the startup
timeout:

```sh
MAESTRO_DRIVER_STARTUP_TIMEOUT=240000 maestro --udid <udid> test <flow>
```
