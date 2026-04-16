# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

React Native library wrapping native Mapbox Navigation SDKs (iOS v2.19, Android v3.17.7) into a single cross-platform `<MapboxNavigation>` component. Built with [react-native-builder-bob](https://github.com/callstack/react-native-builder-bob). Published to npm as `react-native-mapbox-navigation`.

## Commands

```bash
yarn                     # Install dependencies (Yarn 3.6.1 workspaces)
yarn lint                # ESLint + Prettier
yarn lint --fix          # Auto-fix lint issues
yarn typecheck           # TypeScript type check (tsc --noEmit)
yarn test                # Jest unit tests
yarn prepare             # Build library (bob build → lib/)
yarn clean               # Remove build artifacts
yarn release             # Publish via release-it

# Example app
yarn example start       # Start Metro bundler
yarn example ios         # Run on iOS simulator
yarn example android     # Run on Android emulator
```

## Architecture

### JS → Native Bridge

The library uses React Native's **ViewManager pattern** (legacy bridge). No TurboModule/Fabric component yet, though Android has a Fabric fallback via `onAfterUpdateTransaction`.

```
React Component (src/index.tsx)
  └─ requireNativeComponent('MapboxNavigation')
       ├─ iOS:     MapboxNavigationManager (RCTViewManager)
       │             └─ MapboxNavigationView (UIView embedding NavigationViewController)
       └─ Android: MapboxNavigationViewManager (ViewGroupManager<NavigationFrameLayout>)
                     └─ MapboxNavigationFragment (Fragment wrapping full Mapbox Nav SDK)
```

### Coordinate Convention

JS uses `{ latitude, longitude }` objects. Native expects `[longitude, latitude]` arrays. The conversion happens in `src/utils/coordinates-mapper.util.ts`.

### Event Plumbing (Platform Asymmetry)

- **iOS**: Events flow through `RCTDirectEventBlock` callback props set on the native view.
- **Android**: Events are emitted via `RCTDeviceEventEmitter`. The JS component sets up `NativeEventEmitter` listeners in a `useEffect` and forwards them as prop callbacks.

### Touch Handling (iOS — Fabric / Real Devices)

Embedding a UIKit `NavigationViewController` inside React Native's Fabric view tree causes multiple touch-delivery issues on real iOS devices. The fixes in `MapboxNavigationView.swift` address three layers:

1. **`hitTest` override**: Fabric's hit testing doesn't naturally reach the embedded NavigationViewController's buttons. The custom `hitTest` first tries `super.hitTest` (for subviews), then manually forwards to the nav view as a fallback. Returns `nil` (not `super`) when the point is outside the nav view — this prevents MapboxNavigationView itself from claiming the touch.

2. **`reactSubviews() -> []`** + no-op `insertReactSubview`/`removeReactSubview`: Tells Fabric there are no React-managed subviews. Without this, Fabric's touch responder tries to manage the NavigationViewController's native UIKit views and causes conflicts.

3. **`fixParentInteraction()`**: `react-native-screens` uses a native `UINavigationController` whose `UILayoutContainerView` has `isUserInteractionEnabled = false`. This blocks all UIKit hit testing from reaching the Mapbox view. The fix walks up the view hierarchy and forces `isUserInteractionEnabled = true`. It's called twice — once immediately after embedding, and once with a 0.6s delay to catch when the push transition animation resets it.

4. **Proper `removeFromSuperview` cleanup**: Follows the full UIKit child VC removal sequence (`willMove(toParent: nil)` → `vc.view.removeFromSuperview()` → `removeFromParent()`) and resets the `embedded`/`embedding` flags. Without this, stale NavVC state accumulates across navigation sessions, breaking touches on the 2nd+ open.

5. **Simulation mode caveat**: `shouldSimulateRoute = true` causes the Mapbox SDK to block touch events ~1 second after the view loads. When testing on real devices in debug mode, set `shouldSimulateRoute = false`.

See the Grand Tour App's `docs/GTOS-1275-touch-bug-investigation.md` for the full investigation.

### Touch Handling (Android)

- Android: `NavigationFrameLayout.onInterceptTouchEvent()` calls `requestDisallowInterceptTouchEvent(true)`

### Android Fragment Creation

Android uses a command-based pattern to create the navigation fragment:
- **Old architecture**: `UIManager.dispatchViewManagerCommand` with command ID `1`
- **New architecture (Fabric)**: falls back to creating the fragment in `onAfterUpdateTransaction`

## Key Conventions

- **Commit messages**: [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`). Pre-commit hooks enforce this.
- **Node version**: v18 (`.nvmrc`)
- **Package manager**: Yarn 3.6.1 only — npm will not work (workspaces).
- **Formatting**: Prettier with single quotes, 2-space indent, trailing commas in ES5 positions.

## Android Build Tokens

The Android build requires a `MAPBOX_DOWNLOADS_TOKEN` environment variable (or entry in `android/keys.properties`) to pull Mapbox SDK artifacts from their private Maven repository.

## Monorepo Layout

- `/src` — TypeScript library source (component, types, utils)
- `/ios` — Swift/ObjC native module (4 files)
- `/android/src/main/java/ch/jls/reactnative/mapboxnavigation/` — Kotlin native module
- `/example` — Example app workspace (may not be checked into this repo)
- `/lib` — Build output (gitignored)
