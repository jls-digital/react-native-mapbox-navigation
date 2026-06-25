# Test Cases — react-native-mapbox-navigation

> Natural-language E2E test case descriptions for Maestro flows.
> Each test case describes a user journey through the example app.

## Conventions

- **Preset 0** in the example app is the short Zurich test route
  (origin 47.37441, 8.53574 → waypoint 47.37375, 8.53410 →
  destination 47.37335, 8.53422, total ~150m). Use this preset for
  any test case that needs the simulated route to complete end-to-end
  in a reasonable time — the iOS SDK has no public simulation speed
  multiplier, so the full Zurich → Bern preset takes many minutes.
- Preset indices: `0` = short test route, `1` = Zurich → Bern,
  `2` = Zurich → Lucerne, `3` = Zurich → Bern via Lucerne (stop
  waypoint), `4` = Zurich → Bern (silent waypoint).
- Flows that require a specific permission state should either launch
  with `clearState: true` (resets app data; TCC state survives on iOS)
  or be preceded by `xcrun simctl privacy <udid> reset location
  ch.jls.reactnative.mapboxnavigation.example`.

---

## 1. Basic Navigation Flow

### TC-1.1: Home screen loads with presets

**Preconditions:** App freshly launched.

1. App opens on the Home screen.
2. Verify the title "Mapbox Navigation Demo" is visible.
3. Verify 5 route preset buttons are visible: the short test route, "Zurich → Bern", "Zurich → Lucerne", "Zurich → Bern via Lucerne", "Zurich → Bern (silent waypoint)".
4. Verify "Start Navigation" button is visible.
5. Verify default settings: Simulate Route ON, Mute OFF, Language "English" selected, Color Scheme "auto" selected.

### TC-1.2: Select a preset and start navigation

1. Tap the "Zurich → Bern" preset.
2. Verify the preset appears selected (highlighted).
3. Tap "Start Navigation".
4. Verify the Navigation screen opens.
5. Verify the MapboxNavigation component is mounted (visible area where the map/navigation would render).

### TC-1.3: Cancel navigation returns to Home

1. Start navigation with any preset (TC-1.2).
2. The native navigation view's exit/stop button triggers `onCancelNavigation`.
3. Verify the app navigates back to the Home screen.
4. Verify the Home screen state is preserved (same preset still selected).

### TC-1.4: Switch between presets

1. On the Home screen, tap "Zurich → Lucerne".
2. Verify it becomes selected and "Zurich → Bern" is deselected.
3. Tap "Zurich → Bern via Lucerne".
4. Verify it becomes selected and "Zurich → Lucerne" is deselected.

---

## 2. Arrival

### TC-2.1: Simulated route completes; onArrive fires; native end dismisses

**Preconditions:** Simulate Route is ON.

`onArrive` is a notification-only hook — the library does **not**
dismiss the nav view on arrival. The Mapbox SDK's built-in
end-of-trip UI stays visible; the user dismisses it via the native
"End Navigation" button, which fires `onNavigationEnd`.

1. Select **preset 0** (short test route) and tap "Start Navigation".
2. The simulated location moves along the route automatically.
3. On arrival, `onArrive` fires (notification-only) and Mapbox's
   end-of-trip UI appears — verify the native "You have arrived"
   banner is visible.
4. Tap the native "End Navigation" button on the end-of-trip UI.
5. Verify `onNavigationEnd` triggers `navigation.goBack()` and the
   app returns to the Home screen.

### TC-2.2: Arrival event contains correct destination coordinates

1. Start navigation with **preset 0** (simulation ON).
2. Open the debug console (via the dev-menu CTA).
3. Wait for the simulated route to complete.
4. Verify the debug log contains an arrival entry with coordinates matching the short route's destination (≈47.3734, 8.5342).

---

## 3. Multi-Waypoint Routing

### TC-3.1: Route with stop waypoint

1. Select "Zurich → Bern via Lucerne" (has one stop waypoint in Lucerne).
2. Start navigation with simulation ON.
3. Verify the route passes through Lucerne before continuing to Bern.
4. Verify the SDK announces arrival at Lucerne (leg boundary).
5. Verify final arrival is at Bern.

### TC-3.2: Route with silent waypoint

1. Select "Zurich → Bern (silent waypoint)" (has one silent waypoint in Lucerne).
2. Start navigation with simulation ON.
3. Verify the route passes through/near Lucerne.
4. Verify the SDK does NOT announce arrival at Lucerne (no leg boundary for silent waypoint).
5. Verify final arrival is at Bern.

### TC-3.3: Direct route with no waypoints

1. Select "Zurich → Bern" (no waypoints).
2. Start navigation with simulation ON.
3. Verify the route goes directly from Zurich to Bern.
4. Verify that after route calc, the native nav view mounts (arrival verification uses **preset 0** in TC-2.1 since the full Zurich → Bern drive is too long to run in a Maestro flow).

### TC-3.4: Route with many waypoints (stress test)

**Preconditions:** Modify the app to inject 50+ waypoints between Zurich and Bern.

1. Start navigation with the large waypoint set.
2. Verify route calculation succeeds (or if Mapbox rejects, an `onError` with `ROUTE_CALCULATION_FAILED` fires).
3. If route succeeds, verify the route passes through all waypoints in order.

---

## 4. Voice & Mute

### TC-4.1: Start unmuted, toggle to mute

1. Set Mute OFF on Home screen.
2. Start navigation with simulation ON.
3. Verify voice instructions play (audio output during maneuvers).
4. Tap the native mute button in the navigation view.
5. Verify `onMuteChange` fires with `isMuted: true` (check debug console).
6. Verify voice instructions stop.

### TC-4.2: Start muted, toggle to unmute

1. Set Mute ON on Home screen.
2. Start navigation with simulation ON.
3. Verify no voice instructions play initially.
4. Tap the native unmute button.
5. Verify `onMuteChange` fires with `isMuted: false` (check debug console).
6. Verify voice instructions begin playing.

### TC-4.3: Mute state persists during navigation

1. Start navigation unmuted.
2. Toggle mute ON via native button.
3. Continue navigating — verify voice remains muted through multiple maneuvers.

---

## 5. Color Scheme

### TC-5.1: Light mode

1. Select color scheme "light" on Home screen.
2. Start navigation.
3. Verify the navigation view uses a light/daytime visual style.

### TC-5.2: Dark mode

1. Select color scheme "dark" on Home screen.
2. Start navigation.
3. Verify the navigation view uses a dark/nighttime visual style.

### TC-5.3: Auto mode

1. Select color scheme "auto" on Home screen.
2. Start navigation.
3. Verify the navigation view adapts its style based on the Mapbox SDK's automatic day/night detection.

---

## 6. Imperative Commands

### TC-6.1: Recenter camera after pan

1. Start navigation with simulation ON.
2. Wait for the route to begin (camera following the simulated vehicle).
3. Manually pan/drag the map away from the current position.
4. Verify the camera is no longer following the vehicle.
5. Tap the "Recenter" button in the overlay controls.
6. Verify the camera returns to following the vehicle's position and heading.

### TC-6.2: Show route overview

1. Start navigation with simulation ON.
2. Tap the "Overview" button in the overlay controls.
3. Verify the map zooms out to show the entire route from origin to destination.
4. Verify the route polyline is visible in its entirety.

---

## 7. Error Handling

### TC-7.1: GPS permission denied

**Preconditions:** Location permission NOT granted for the app.

1. Start navigation.
2. Verify `onError` fires with code `GPS_PERMISSION_DENIED`.
3. Verify the error alert shows "GPS_PERMISSION_DENIED" with an appropriate message.

### TC-7.2: GPS unavailable (location services off)

**Preconditions:** Device location services disabled entirely.

1. Start navigation.
2. Verify `onError` fires with code `GPS_UNAVAILABLE`.
3. Verify the error alert shows "GPS_UNAVAILABLE".

### TC-7.3: Invalid coordinates

**Preconditions:** Modify the app to pass origin `{ latitude: 999, longitude: 999 }`.

1. Start navigation with invalid coordinates.
2. Verify `onError` fires with code `INVALID_COORDINATES`.
3. Verify the error alert shows "INVALID_COORDINATES".

### TC-7.4: Route calculation failed (unroutable)

**Preconditions:** Modify the app to set destination to the middle of the ocean (e.g., `{ latitude: 0, longitude: 0 }`).

1. Start navigation.
2. Verify `onError` fires with code `ROUTE_CALCULATION_FAILED`.
3. Verify the error alert shows "ROUTE_CALCULATION_FAILED".

### TC-7.5: Network error

**Preconditions:** Device in airplane mode / no network connectivity.

1. Start navigation.
2. Verify `onError` fires with code `NETWORK_ERROR`.
3. Verify the error alert shows "NETWORK_ERROR".

### TC-7.6: SDK initialization failure (invalid token)

**Preconditions:** Invalid or missing Mapbox access token in the app configuration.

1. Start navigation.
2. Verify `onError` fires with code `SDK_INIT_FAILED`.
3. Verify the error alert shows "SDK_INIT_FAILED".

---

## 8. Event Emission

### TC-8.1: Route progress events fire at ~1/s

1. Start navigation with simulation ON.
2. Open the debug console.
3. Verify `onRouteProgressChange` events appear in the log.
4. Verify events fire approximately once per second (not more frequently).
5. Verify each event contains: `distanceTraveled` (>= 0), `distanceRemaining` (>= 0), `durationRemaining` (>= 0), `fractionTraveled` (between 0.0 and 1.0).

### TC-8.2: Location events fire at ~1/s

1. Start navigation with simulation ON.
2. Open the debug console.
3. Verify `onLocationChange` events appear in the log.
4. Verify events fire approximately once per second.
5. Verify each event contains valid `latitude` (-90 to 90) and `longitude` (-180 to 180).

### TC-8.3: Progress values are monotonic during simulation

1. Start navigation with simulation ON.
2. Open the debug console.
3. Observe `fractionTraveled` values over time.
4. Verify `fractionTraveled` increases monotonically (never decreases) during uninterrupted simulation.
5. Verify `distanceRemaining` decreases monotonically during uninterrupted simulation.

### TC-8.4: Reroute event on deviation

**Preconditions:** Real GPS or a mechanism to simulate off-route movement.

1. Start navigation with a known route.
2. Deviate significantly from the calculated route.
3. Verify `onReroute` fires.
4. Verify a new route is calculated (progress values reset to reflect the new route).

---

## 9. Lifecycle

### TC-9.1: Background and foreground

1. Start navigation with simulation ON.
2. Verify events are flowing (location, progress).
3. Send the app to the background (press Home button).
4. Wait 5 seconds.
5. Bring the app back to the foreground.
6. Verify navigation resumes without errors.
7. Verify events resume flowing.
8. Verify no GPS drain occurred while backgrounded (battery/GPS indicator inactive in background).

### TC-9.2: Unmount cleans up resources

1. Start navigation with simulation ON.
2. Verify events are flowing.
3. Tap the native cancel button (or navigate back).
4. The MapboxNavigation component unmounts.
5. Verify no events fire after unmount.
6. Verify no GPS activity continues after unmount (no location indicator active).

### TC-9.3: Rapid mount/unmount (stress test)

1. Rapidly start navigation and go back 10 times in quick succession.
2. Verify no crashes occur.
3. Verify no memory leaks (app memory usage returns to baseline).
4. Verify the Home screen remains responsive after the stress test.

### TC-9.4: Cancel does not fire arrival; remount after cancel works

**Regression test** for two bugs: (a) `onArrive` firing after the user
cancelled navigation, because the `waypointsArrival` subscription kept
receiving events; (b) starting a second navigation session crashing
with Mapbox's `checkInstanceIsUnique` assertion because the previous
`MapboxNavigationProvider` was still alive (released only when the RN
component finalised, which lags unmount).

1. Start navigation with **preset 0** (simulation ON).
2. Wait for the `NavigationViewController` to mount and the simulator
   to begin producing progress updates.
3. Tap the SDK's built-in cancel button (accessibility label `close`)
   in the bottom banner.
4. Verify `onCancelNavigation` fires and the app navigates back to
   Home.
5. Verify NO arrival event leaks through — `onArrive` must not
   fire on the cancel path. (Under the new arrival contract
   `onArrive` no longer pops an Alert; observed via the debug
   console or by the absence of `checkInstanceIsUnique` in the
   remount below.)
6. Immediately start navigation again with preset 0.
7. Verify the second `NavigationViewController` mounts without
   triggering `checkInstanceIsUnique`, proving the previous SDK
   session was fully released by `tearDownNavigation()`.

### TC-9.5: Arrival → remount works (teardown on RN unmount)

**Regression test** that teardown fires on *any* JS-initiated unmount,
not only when the SDK's own cancel button is tapped. Under the new
arrival contract the flow is: `onArrive` updates app state →
end-of-trip UI stays visible → user taps Mapbox's native
"End Navigation" button → `onNavigationEnd` fires →
`navigation.goBack()`, which unmounts `MapboxNavigation` via the
Mapbox-dismissal path. The SDK's own
`navigationViewControllerDidDismiss` delegate *does* fire here (with
`byCanceling == false`), but React Navigation's pop may race it and
the UIView-detach path may win — so teardown must be idempotent and
driven primarily by the host UIView's lifecycle (see SPEC §T9).

1. Start navigation with **preset 0** (simulation ON).
2. Wait for the simulated route to complete — verify Mapbox's
   native "You have arrived" banner is visible.
3. Tap the Mapbox native "End Navigation" button on the end-of-trip
   UI. `onNavigationEnd` fires and the example app calls
   `navigation.goBack()`.
4. Verify the app returns to Home.
5. Immediately start navigation again with preset 0.
6. Verify the second `NavigationViewController` mounts without
   triggering `checkInstanceIsUnique`.

---

## 10. Language

### TC-10.1: Cloud voice language (German)

1. Select language "German" on Home screen.
2. Start navigation with simulation ON.
3. Verify voice instructions are spoken in German.
4. Verify on-screen text instructions (maneuver panel) are in German.

### TC-10.2: Cloud voice language (French)

1. Select language "French" on Home screen.
2. Start navigation with simulation ON.
3. Verify voice instructions are spoken in French.
4. Verify on-screen text instructions are in French.

### TC-10.3: Device TTS fallback language

**Preconditions:** Modify the app to set language to a device TTS language (e.g., `'cs'` for Czech). Ensure the device has a Czech TTS engine installed.

1. Start navigation with simulation ON.
2. Verify voice instructions are spoken using the device's TTS engine in Czech.
3. Verify text instructions are in Czech.

### TC-10.4: Language prop defaults to English

1. Leave language as "English" (default) on Home screen.
2. Start navigation with simulation ON.
3. Verify voice instructions are in English.

---

## 11. Edge Cases

### TC-11.1: Origin and destination are the same

**Preconditions:** Modify the app to set origin and destination to the same coordinates (Zurich → Zurich).

1. Start navigation.
2. Verify the component handles this gracefully: either immediate `onArrive` fires, or `onError` with `ROUTE_CALCULATION_FAILED`.
3. Verify no crash.

### TC-11.2: Very large waypoint count

**Preconditions:** Modify the app to inject 200+ waypoints.

1. Start navigation.
2. Verify the route calculates successfully, or `onError` fires with `ROUTE_CALCULATION_FAILED` if the Mapbox API rejects the request.
3. Verify no crash or timeout in the JS thread.

### TC-11.3: Rapid mount/unmount does not leak

1. Using a profiling tool, measure memory before and after 20 mount/unmount cycles.
2. Verify memory returns to baseline (no significant leak).

### TC-11.4: Prop change during active navigation — destination

**Preconditions:** Modify the app to change the `destination` prop 10 seconds into navigation.

1. Start navigation from Zurich to Bern.
2. After 10 seconds, change the destination to Lucerne.
3. Verify the route recalculates to the new destination.
4. Verify `onReroute` fires or a new route is displayed.
5. Verify arrival at Lucerne (not Bern).

### TC-11.5: Prop change during active navigation — mute

1. Start navigation with `mute={false}`.
2. After voice instructions begin, change the `mute` prop to `true` from the JS side (not via native button).
3. Verify voice instructions stop.
4. Verify `onMuteChange` fires with `isMuted: true`.

### TC-11.6: Empty waypoints array

1. Start navigation with `waypoints={[]}` (explicit empty array, not undefined).
2. Verify navigation proceeds directly from origin to destination.
3. Verify no errors related to empty waypoints.
