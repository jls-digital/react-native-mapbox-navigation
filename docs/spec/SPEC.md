# react-native-mapbox-navigation — Library Specification

> Version: 3.0 | Date: 2026-04-02 | Status: Draft

## 1. Overview

A React Native library that wraps the Mapbox Navigation SDKs for iOS and Android, providing a **complete, ready-to-use turn-by-turn navigation screen** as a single React component. The library targets the React Native New Architecture exclusively (Fabric + Codegen).

The component renders a full-screen, self-contained navigation experience with all required UI elements (maneuvers, trip progress, controls, attribution). Consumers mount the component with origin, destination, and optional waypoints — the library handles everything else.

---

## 2. Business Logic Requirements

### B1 — Turn-by-Turn Navigation

Provide full driving navigation from an origin to a destination with real-time maneuver instructions, estimated time of arrival, and route visualization on the map.

### B2 — Multi-Waypoint Routing

Support 0–200+ intermediate waypoints between origin and destination. The Mapbox SDK handles route calculation through all waypoints in order.

**Waypoint types:**

- **Stop waypoints** (default): Create a route leg boundary. The SDK announces arrival at each stop.
- **Silent/via waypoints**: Shape the route without creating leg boundaries or arrival announcements. Controlled via a `isSilent` flag on the waypoint object.

### B3 — Arrival Detection

Detect and emit an event when the user physically arrives at the final destination.

### B4 — Voice Guidance

Provide spoken turn-by-turn instructions. The library accepts a `language` prop to set the instruction language. Supported values correspond to the Mapbox Directions API and Voice API supported languages (see §8 for the full list and TypeScript type).

When the requested language is supported by the Mapbox Voice API, high-quality cloud-synthesized speech is used. For languages supported only for text instructions, the SDK falls back to the platform's native TTS engine (iOS `AVSpeechSynthesizer`, Android `TextToSpeech`). Voice availability on Android fallback depends on the device's installed TTS engines.

### B5 — Voice Mute / Unmute

Provide an on-screen mute toggle button. Emit mute state changes to JS via an event. Accept an initial `mute` prop to set the default state.

### B6 — Cancel Navigation

Provide an on-screen button to exit navigation. Emit a cancel event to JS when the user taps it.

### B7 — Route Simulation

Simulate driving along the calculated route without requiring real GPS movement. Controlled via a `shouldSimulateRoute` prop. Intended for development and demo purposes.

### B8 — Route Progress Tracking

Continuously emit route progress data during active navigation:

- Distance traveled (meters)
- Distance remaining (meters)
- Duration remaining (seconds)
- Fraction traveled (0.0–1.0)

### B9 — Location Tracking

Continuously emit the user's current GPS coordinates during active navigation.

### B10 — Error Handling

Emit structured error events with a machine-readable `code` and a human-readable `message`. See §7 for the error code table.

**Permission validation:** The library does **not** request or manage location permissions — that is the consuming app's responsibility. However, the library **must** check the current permission state when the component mounts and emit a `GPS_PERMISSION_DENIED` error if location access has not been granted.

### B11 — Rerouting Notification

Emit an event when the Mapbox SDK recalculates the route because the user deviated from the current path.

### B12 — Unreachable Waypoints

> **Status: TBD**

The Mapbox Directions API is all-or-nothing: if **any** waypoint in the array cannot be snapped to the road network (`NoSegment`) or no path exists between consecutive waypoints (`NoRoute`), the **entire route request fails**. No partial routes or waypoint skipping occurs.

**Open questions to resolve:**

1. Should the library attempt to identify and remove the problematic waypoint(s) and retry? This would require iterative subset requests and adds latency.
2. Should the library simply forward the error to the consumer and let them handle retry logic?
3. Should the library expose a `waypointSnappingRadius` prop (maps to the Mapbox `radiuses` parameter) so consumers can control how far waypoints can be snapped to the nearest road?
4. For very long detours caused by a waypoint, should there be a configurable maximum detour threshold, or is that the consumer's responsibility to pre-validate?

**Current decision:** Forward the Mapbox error as-is via the `onError` callback with code `ROUTE_CALCULATION_FAILED`. Document the all-or-nothing behavior. Revisit if real-world usage reveals the need for automatic retry/skip logic.

### B13 — Automatic Day / Night Mode

Support light and dark map/navigation styles. Accept a `colorScheme` prop:

- `'light'` — force daytime navigation style
- `'dark'` — force nighttime navigation style
- `'auto'` (default) — let the Mapbox SDK switch automatically based on sun position and tunnel detection

---

## 3. UI / UX Requirements

The component renders a **full-screen, self-contained navigation view**. It is not a composable map fragment — one component equals one screen.

### U1 — Maneuver Instructions

Display the next maneuver at the top of the screen: turn arrow/icon, street name, and distance to the turn.

### U2 — Trip Progress Bar

Display estimated time of arrival, total distance remaining, and total duration remaining.

### U3 — Route Line on Map

Render the active route as a styled polyline on the map, including maneuver arrows at turns.

### U4 — Camera Following

The map camera follows the user's position and heading by default. When the user manually pans the map, show a **recenter button** to return to following mode.

### U5 — Route Overview Button

Allow the user to toggle between camera-following mode and a zoomed-out overview of the entire route.

### U6 — Sound / Mute Button

On-screen toggle button for voice guidance. Visual state reflects the current mute status.

### U7 — Stop / Exit Button

On-screen button to cancel and exit navigation. Triggers the `onCancelNavigation` callback.

### U8 — Mapbox Attribution

Display the **Mapbox logo** and an **(i) info/attribution button** as required by Mapbox Terms of Service. The info button must open an attribution detail view crediting Mapbox, OpenStreetMap, and other data providers. These elements are provided by the Mapbox SDK and must not be removed or hidden.

### U9 — Safe Area Handling

The navigation UI must respect device safe areas out of the box on both platforms: notch, Dynamic Island, status bar, home indicator (iOS), system navigation bar (Android). No configuration required from the consumer.

### U10 — Speed Limit Indicator

Display the current road speed limit when the data is available from the Mapbox SDK.

### U11 — Lane Guidance

Display lane guidance visualization when available for the upcoming maneuver.

---

## 4. Technical Requirements

### T1 — New Architecture Only

Fabric native component with Codegen specs as the single source of truth for the bridge layer. No old architecture (`UIManager`, `NativeModules`, `DeviceEventEmitter`) code.

### T2 — Codegen Spec

Define a `codegenNativeComponent` spec with:

- Typed props for all configuration
- `DirectEventHandler` for all event callbacks (consistent on both platforms)
- `codegenNativeCommands` for imperative operations

### T3 — Imperative Commands

Expose via a React ref (`MapboxNavigationRef`):

- `recenterCamera()` — return the camera to following mode
- `showRouteOverview()` — zoom out to show the full route

### T4 — Native Languages

- iOS: **Swift** (with ObjC++ interop layer for Codegen-generated files)
- Android: **Kotlin**

### T5 — Mapbox Navigation SDK v3.20.x

| | iOS | Android |
|---|---|---|
| **SDK** | `MapboxNavigationUIKit` + `MapboxNavigationCore` | `com.mapbox.navigationcore:android:3.20.x` |
| **Maps SDK** | MapboxMaps v11.20.x | Maps SDK v11.20.x |
| **Distribution** | Swift Package Manager | Maven / Gradle |
| **Min platform** | iOS 14.0+ | Per React Native requirements |

### T6 — iOS Distribution (SPM via CocoaPods)

Mapbox Navigation SDK v3 is SPM-only (CocoaPods support was dropped). The library uses React Native's `spm_dependency` helper (available since RN 0.75) in its `.podspec` to declare the Mapbox SPM dependency while remaining a CocoaPod itself.

**Consumer requirement:** Apps must use `use_frameworks! :linkage => :dynamic` in their Podfile. This must be documented in the library's setup guide.

### T7 — Android Distribution

Standard React Native library Gradle setup. Mapbox SDK pulled from Maven via `build.gradle` dependencies.

### T8 — Mapbox Access Token

The Mapbox access token is configured by the consuming app, not the library:

- **iOS:** Token in the app's `Info.plist` under `MBXAccessToken`, or via the Mapbox SDK's token resolution mechanism
- **Android:** Token in `AndroidManifest.xml` as `<meta-data android:name="MAPBOX_ACCESS_TOKEN" />`

The library must document this setup but never hardcode or bundle tokens.

### T9 — Lifecycle Management

- Initialize Mapbox SDK resources when the component mounts
- Tear down all resources (GPS listeners, audio sessions, observers) when the component unmounts
- Pause GPS and rendering when the app goes to background; resume on foreground
- No GPS drain when the component is not mounted

### T10 — Event Throttling

Throttle high-frequency native events before dispatching to JS:

- Location updates: ~1 per second (configurable via prop in the future if needed)
- Route progress updates: ~1 per second

### T11 — TypeScript API

Export fully typed:

- Props interface
- Event payload types
- Ref handle type
- Language union type
- Error code union type

Built with `react-native-builder-bob` targeting CommonJS + ESM + TypeScript declarations.

### T12 — Example App

Include a working example app in the repository for development and testing.

### T13 — Test Coverage

- **JS layer:** Jest unit tests for the component wrapper and utility functions
- **Native layer:** XCTest (iOS) and JUnit (Android) for bridging logic
- **E2E scaffolding:** Maestro flow templates for manual and CI testing

### T14 — Bootstrapping

Use `create-react-native-library` with the `fabric-view` template as the project scaffold.

### T15 — Privacy Manifest (iOS)

Ensure the built framework ships with a valid `PrivacyInfo.xcprivacy`. Mapbox SDK v3.20.x includes privacy manifests — verify they pass App Store Connect validation during integration testing.

### T16 — Permission Checking

The library does **not** request permissions. On mount, it checks the current location permission state:

- If permission is not granted → emit `onError` with code `GPS_PERMISSION_DENIED`
- If location services are disabled (Android GPS off) → emit `onError` with code `GPS_UNAVAILABLE`

The consuming app is responsible for requesting permissions before mounting the navigation component.

---

## 5. Component API

### Props

```typescript
import type { ViewProps } from 'react-native';

interface Coordinates {
  latitude: number;
  longitude: number;
}

interface Waypoint {
  /** Waypoint coordinates */
  coordinate: Coordinates;
  /**
   * If true, the route passes through this point without creating
   * a leg boundary or arrival announcement. Default: false.
   */
  isSilent?: boolean;
}

interface MapboxNavigationProps extends ViewProps {
  // ── Route ──────────────────────────────────────────────
  origin: Coordinates;
  destination: Coordinates;
  waypoints?: Waypoint[];

  // ── Configuration ──────────────────────────────────────
  language?: MapboxLanguage;                       // default: 'en'
  shouldSimulateRoute?: boolean;                   // default: false
  mute?: boolean;                                  // default: false
  colorScheme?: 'light' | 'dark' | 'auto';        // default: 'auto'

  // ── Events ─────────────────────────────────────────────
  onArrive?: (event: ArriveEvent) => void;
  onError?: (event: ErrorEvent) => void;
  onCancelNavigation?: () => void;
  onMuteChange?: (event: MuteChangeEvent) => void;
  onRouteProgressChange?: (event: RouteProgressEvent) => void;
  onLocationChange?: (event: LocationEvent) => void;
  onReroute?: () => void;
}
```

### Event Types

```typescript
interface ArriveEvent {
  nativeEvent: {
    destination: Coordinates;
  };
}

interface ErrorEvent {
  nativeEvent: {
    code: MapboxNavigationErrorCode;
    message: string;
  };
}

interface MuteChangeEvent {
  nativeEvent: {
    isMuted: boolean;
  };
}

interface RouteProgressEvent {
  nativeEvent: {
    distanceTraveled: number;     // meters
    distanceRemaining: number;    // meters
    durationRemaining: number;    // seconds
    fractionTraveled: number;     // 0.0–1.0
  };
}

interface LocationEvent {
  nativeEvent: {
    latitude: number;
    longitude: number;
  };
}
```

### Ref Handle

```typescript
interface MapboxNavigationRef {
  recenterCamera: () => void;
  showRouteOverview: () => void;
}
```

### Usage Example

```tsx
import { MapboxNavigation, type MapboxNavigationRef } from 'react-native-mapbox-navigation';

const ref = useRef<MapboxNavigationRef>(null);

<MapboxNavigation
  ref={ref}
  origin={{ latitude: 47.3769, longitude: 8.5417 }}
  destination={{ latitude: 46.9481, longitude: 7.4474 }}
  waypoints={[
    { coordinate: { latitude: 47.1, longitude: 8.2 } },
    { coordinate: { latitude: 47.0, longitude: 7.9 }, isSilent: true },
  ]}
  language="de"
  colorScheme="auto"
  mute={false}
  onArrive={(e) => console.log('Arrived at', e.nativeEvent.destination)}
  onError={(e) => console.error(e.nativeEvent.code, e.nativeEvent.message)}
  onCancelNavigation={() => navigation.goBack()}
  onRouteProgressChange={(e) => updateProgress(e.nativeEvent)}
  style={{ flex: 1 }}
/>
```

---

## 6. Architecture

```
┌─────────────────────────────────────────────────┐
│                   Consumer App                   │
│                                                  │
│  <MapboxNavigation origin={…} destination={…} /> │
└──────────────────────┬──────────────────────────┘
                       │ Fabric / Codegen
┌──────────────────────┴──────────────────────────┐
│              react-native-mapbox-navigation      │
│                                                  │
│  src/                                            │
│  ├── specs/                                      │
│  │   └── MapboxNavigationNativeComponent.ts      │
│  │       (codegenNativeComponent + Commands)     │
│  ├── MapboxNavigation.tsx                        │
│  │   (React wrapper with ref forwarding)         │
│  └── types.ts                                    │
│                                                  │
│  ios/                                            │
│  ├── MapboxNavigationView.swift                  │
│  │   (wraps NavigationViewController from SDK)   │
│  └── MapboxNavigationViewManager.mm              │
│      (Fabric ViewComponentView)                  │
│                                                  │
│  android/                                        │
│  ├── MapboxNavigationView.kt                     │
│  │   (wraps Mapbox NavigationView from SDK)      │
│  └── MapboxNavigationViewManager.kt              │
│      (Fabric SimpleViewManager + Delegate)       │
└──────────────────────┬──────────────────────────┘
                       │ Native SDK
┌──────────────────────┴──────────────────────────┐
│         Mapbox Navigation SDK v3.20.x            │
│  iOS: MapboxNavigationUIKit (SPM)                │
│  Android: com.mapbox.navigationcore (Maven)      │
└─────────────────────────────────────────────────┘
```

---

## 7. Error Codes

| Code | Meaning |
|------|---------|
| `ROUTE_CALCULATION_FAILED` | Mapbox Directions API returned `NoRoute` or `NoSegment` — no valid route through the given coordinates |
| `GPS_UNAVAILABLE` | Device location services are disabled or unavailable |
| `GPS_PERMISSION_DENIED` | Location permission has not been granted by the user. The consuming app must request permission before mounting the component |
| `NETWORK_ERROR` | Network request failed during route calculation or map tile loading |
| `INVALID_COORDINATES` | Origin, destination, or a waypoint has invalid coordinates |
| `SDK_INIT_FAILED` | Mapbox SDK initialization failed (e.g., invalid or missing access token) |

---

## 8. Supported Languages

The `language` prop accepts the following values. These correspond to the Mapbox Directions API's supported instruction languages.

**Voice quality tiers:**

- **Cloud voice**: High-quality speech via Mapbox Voice API (backed by Amazon Polly)
- **Device TTS fallback**: Uses the platform's built-in text-to-speech engine. Quality and availability varies by device.

```typescript
type MapboxLanguage =
  | 'ar'       // Arabic                    (device TTS fallback)
  | 'ca'       // Catalan                   (device TTS fallback)
  | 'cs'       // Czech                     (device TTS fallback)
  | 'da'       // Danish                    (cloud voice)
  | 'de'       // German                    (cloud voice)
  | 'en'       // English                   (cloud voice)
  | 'es'       // Spanish                   (cloud voice)
  | 'fi'       // Finnish                   (device TTS fallback)
  | 'fr'       // French                    (cloud voice)
  | 'he'       // Hebrew                    (device TTS fallback)
  | 'hu'       // Hungarian                 (device TTS fallback)
  | 'id'       // Indonesian                (device TTS fallback)
  | 'it'       // Italian                   (cloud voice)
  | 'ja'       // Japanese                  (cloud voice)
  | 'ko'       // Korean                    (cloud voice)
  | 'nb'       // Norwegian Bokmål          (cloud voice)
  | 'nl'       // Dutch                     (cloud voice)
  | 'pl'       // Polish                    (cloud voice)
  | 'pt-BR'    // Portuguese (Brazil)       (cloud voice)
  | 'pt-PT'    // Portuguese (Portugal)     (cloud voice)
  | 'ro'       // Romanian                  (cloud voice)
  | 'ru'       // Russian                   (cloud voice)
  | 'sl'       // Slovenian                 (device TTS fallback)
  | 'sv'       // Swedish                   (cloud voice)
  | 'tr'       // Turkish                   (cloud voice)
  | 'uk'       // Ukrainian                 (device TTS fallback)
  | 'vi'       // Vietnamese                (device TTS fallback)
  | 'zh-Hans'; // Chinese (Simplified)      (cloud voice)
```

> **Note:** The Mapbox Voice API language availability is verified against Mapbox documentation as of April 2026. Verify the current list at [Mapbox Localization Docs](https://docs.mapbox.com/ios/navigation/guides/system-integration/localization-and-internationalization/) before implementation.

---

## 9. Platform SDK Details

### iOS — Mapbox Navigation SDK v3.20.x

- **Packages:** `MapboxNavigationUIKit`, `MapboxNavigationCore` (via SPM)
- **Distribution:** Swift Package Manager — consumed via `spm_dependency` in the library's podspec
- **Consumer requirement:** `use_frameworks! :linkage => :dynamic` in Podfile
- **Key classes:**
  - `NavigationViewController` — drop-in navigation UI
  - `CoreConfig` — SDK configuration (replaces `NavigationSettings` from v2)
  - `MapboxNavigation` — facade object for navigation lifecycle
- **Event system:** Combine publishers (replaces delegates/NotificationCenter from v2)
- **Min requirements:** iOS 14.0+, Xcode 16+, Swift 5.9+

### Android — Mapbox Navigation SDK v3.20.x

- **Artifact:** `com.mapbox.navigationcore:android:3.20.x` (or granular: `ui-components`, `ui-maps`, `voice`, `tripdata`)
- **Distribution:** Maven
- **Key classes:**
  - `NavigationView` — drop-in navigation UI
  - `MapboxNavigation` — navigation lifecycle management
  - `RouteOptions.Builder` — route configuration
- **Event system:** Observer pattern (`LocationObserver`, `RouteProgressObserver`, `ArrivalObserver`, `VoiceInstructionsObserver`, `RoutesObserver`)
- **Min requirements:** Per React Native's Android min SDK

---

## 10. Out of Scope

| Item | Reason |
|------|--------|
| Distance matrix / Directions API queries | App-level concern — not part of the navigation UI |
| Offline map tiles / routing | Significant complexity; not required for initial release |
| Non-driving routing profiles | Library targets driving navigation only |
| Custom map markers / annotations | The navigation view is self-contained, not a composable map |
| Old Architecture support | New Architecture only — no bridge/UIManager code |
| External maps app fallback | App-level concern (e.g., `react-native-map-link`) |
| Location permission requests | App-level concern — library only validates current state |
| Waypoint name / label display | Not surfaced in the Mapbox Navigation UI by default |

---

## 11. Open Questions

| # | Question | Status |
|---|----------|--------|
| 1 | **Unreachable waypoints** (B12): Should the library implement automatic retry/skip logic, or forward errors to the consumer? | TBD — see §2 B12 |
| 2 | **Waypoint snapping radius**: Should a `waypointSnappingRadius` prop be exposed? | TBD — depends on answer to #1 |
| 3 | **`spm_dependency` + dynamic frameworks**: Does `use_frameworks! :linkage => :dynamic` cause conflicts with other common RN libraries in a typical app? | Validate during integration testing |
| 4 | **Speed limit data availability**: Speed limit data coverage varies by region. Should the speed limit indicator hide automatically when no data is available, or show a placeholder? | TBD — default to SDK behavior (auto-hide) |

---

## 12. Revision History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-04-02 | Initial requirements derived from consumer app analysis |
| 2.0 | 2026-04-02 | Updated: new arch only, Mapbox v3.20.x, full-screen UI, attribution, dark mode, safe areas |
| 3.0 | 2026-04-02 | Standalone library (no app-specific references), SPM validation, language typing, waypoint reachability TBD, permission checking clarification, waypoint types (silent/stop) |
