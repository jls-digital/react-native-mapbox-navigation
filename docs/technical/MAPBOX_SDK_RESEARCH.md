# Mapbox Navigation SDK v3 — Implementation Research

> Status: Research draft — 2026-04-20
> SDK target: **Mapbox Navigation v3.20.x** (iOS 15+, Android API 21+)
> Scope: Maps each requirement from [SPEC.md](../spec/SPEC.md) to concrete SDK APIs on both platforms with doc references. Drives the native implementation of `HybridReactNativeMapboxNavigation`.

This document was compiled from Mapbox's official docs (docs.mapbox.com), GitHub (github.com/mapbox), and context7 library snapshots. API signatures marked ⚠️ should be validated against the SDK at implementation time — Mapbox has renamed several types between v3 minor releases and some code snippets below are reconstructions rather than verbatim copies.

---

## 0. Dependencies

### iOS — Swift Package Manager (`podspec` with `spm_dependency`)

```ruby
# react-native-mapbox-navigation.podspec
s.dependency 'React-Core'
s.spm_dependency(
  url: 'https://github.com/mapbox/mapbox-navigation-ios',
  requirement: { kind: 'upToNextMajorVersion', minimumVersion: '3.20.0' },
  products: ['MapboxNavigationCore', 'MapboxNavigationUIKit']
)
```

Primary modules used:

- `MapboxNavigationCore` — trip session, observers, routing provider, config
- `MapboxNavigationUIKit` — `NavigationViewController`, styles
- `MapboxDirections` — `Waypoint`, `RouteOptions`
- `MapboxMaps` — `NavigationCamera`, `EdgeInsets`

Token: set `MBXAccessToken` in `Info.plist` (or via app init). No code-level token required in v3.

### Android — Gradle

```gradle
// android/build.gradle
dependencies {
    implementation "com.mapbox.navigationcore:android:3.20.0"
    implementation "com.mapbox.navigationcore:ui-components:3.20.0"
    implementation "com.mapbox.navigationcore:ui-maps:3.20.0"
    implementation "com.mapbox.navigationcore:voice:3.20.0"
}
```

Token: add `res/values/mapbox_access_token.xml`:

```xml
<string name="mapbox_access_token" translatable="false">YOUR_TOKEN</string>
```

The SDK resolves this string resource at init in v3 (the v2 `NavigationOptions#accessToken` parameter was removed).

---

## 1. Initialization & Lifecycle

### iOS

```swift
import MapboxNavigationCore
import MapboxNavigationUIKit

let coreConfig = CoreConfig()                // locale, TTS, location source, …
let provider = MapboxNavigationProvider(coreConfig: coreConfig)
let mapboxNavigation = provider.mapboxNavigation
```

- `MapboxNavigationProvider` is the root DI container — holds `mapboxNavigation`, `routeVoiceController`, `eventsManager`.
- Combine-based. Publishers (`routeProgress`, `locationMatching`, `rerouting`, `waypointsArrival`, `voiceInstructions`, `errors`) deliver on the main thread.
- Teardown: `mapboxNavigation.tripSession().setToIdle()` + cancel all `AnyCancellable`s.

### Android

```kotlin
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.NavigationOptions

// In Application#onCreate (can also be lazy on first view attach)
MapboxNavigationApp
    .setup(NavigationOptions(context = this))
    .attachAllActivities(this)
```

- `MapboxNavigationApp` is a singleton tied to activity lifecycles. Access the current instance with `MapboxNavigationApp.current()`.
- On the Nitro view, register an observer in `onAttach`:

```kotlin
MapboxNavigationApp.registerObserver(object : MapboxNavigationObserver {
    override fun onAttached(mapboxNavigation: MapboxNavigation) { /* ready */ }
    override fun onDetached(mapboxNavigation: MapboxNavigation) { /* cleanup */ }
})
```

- Callbacks run on the main thread.

**Docs:**
- iOS: <https://docs.mapbox.com/ios/navigation/guides/>
- Android: <https://docs.mapbox.com/android/navigation/guides/install/>
- Android v2→v3 migration: <https://docs.mapbox.com/android/navigation/guides/migration-from-v2/>

---

## 2. Embedding the Drop-In UI inside a Nitro HybridView

This is the trickiest integration concern and deserves a prototype before committing. Both platforms provide a full-screen "drop-in" container (`NavigationViewController` / `NavigationView`); our job is to host it inside a plain RN view.

### iOS — `NavigationViewController` as a child view controller

`NavigationViewController` is a `UIViewController`, not a `UIView`. Nitro's HybridView exposes a `UIView`, so we need a carrier `UIViewController` that parents the navigation VC:

```swift
// Inside HybridReactNativeMapboxNavigation
private lazy var carrier: UIViewController = {
    let vc = UIViewController()
    vc.view = view   // the Nitro-provided UIView
    return vc
}()

func embed(_ navVC: NavigationViewController) {
    carrier.addChild(navVC)
    navVC.view.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(navVC.view)
    NSLayoutConstraint.activate([
        navVC.view.topAnchor.constraint(equalTo: view.topAnchor),
        navVC.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        navVC.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
        navVC.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
    ])
    navVC.didMove(toParent: carrier)
}
```

⚠️ **Gotchas:**
- `NavigationViewController` must have a parent `UIViewController` in the responder chain; otherwise, safe-area insets and status-bar behavior are wrong. Walking up the responder chain to find the RN root VC is the cleanest approach — if none is found, fall back to our own carrier.
- **Resource leaks:** historically (issue [#3052](https://github.com/mapbox/mapbox-navigation-ios/issues/3052), [#664](https://github.com/mapbox/mapbox-navigation-ios/issues/664)) the SDK was designed primarily for modal presentation. Verify that GPS listeners and audio sessions are released on unmount.
- Safe area handling is automatic if the child VC is properly parented.

### Android — `NavigationView` as a Kotlin `View`

In v3 the drop-in UI is a `View` (not a Fragment), which is exactly what Nitro's HybridView expects:

```kotlin
import com.mapbox.navigation.ui.components.NavigationView  // ⚠️ verify exact package at impl time

class HybridReactNativeMapboxNavigation(val context: ThemedReactContext) {
    val view: View = NavigationView(context).apply {
        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    }
}
```

⚠️ **Gotchas:**
- Inherits the host activity's theme — this matters for day/night (§13) and fonts (§14).
- On `onDetachedFromWindow`, call `mapboxNavigation.stopTripSession()` and unregister all observers. Don't wait for GC.
- NavigationView is full-screen by design; keyboard overlap is not handled.

---

## 3. Route Computation with Waypoints (B1, B2, B12)

Inputs from JS (per SPEC §7):

```ts
interface RouteInput {
  origin: Coordinates;
  destination: Coordinates;
  waypoints?: Array<{ coordinate: Coordinates; isSilent?: boolean }>; // 0–200+
}
```

The full point sequence passed to the SDK is **always** `[origin, ...waypoints, destination]`. Each platform marks stop vs. silent waypoints differently — iOS per-waypoint, Android by index list.

### iOS — per-waypoint `separatesLegs` flag

```swift
import MapboxDirections

func buildWaypoints(
    origin: Coordinates,
    intermediates: [WaypointInput],      // WaypointInput = (coord, isSilent)
    destination: Coordinates
) -> [Waypoint] {
    var wps: [Waypoint] = []

    // Origin — always a "stop" (separatesLegs = true is the default)
    wps.append(Waypoint(coordinate: origin.clLocation, name: "Origin"))

    // Intermediates — each carries its own isSilent flag
    for (i, input) in intermediates.enumerated() {
        var wp = Waypoint(coordinate: input.coord.clLocation, name: "Waypoint \(i + 1)")
        wp.separatesLegs = !(input.isSilent ?? false)       // silent → false, stop → true
        wps.append(wp)
    }

    // Destination — always a stop
    wps.append(Waypoint(coordinate: destination.clLocation, name: "Destination"))
    return wps
}

let options = NavigationRouteOptions(waypoints: buildWaypoints(origin: o, intermediates: w, destination: d))
let request = mapboxNavigation.routingProvider().calculateRoutes(options: options)

Task {
    switch await request.result {
    case .success(let routes): /* set active routes */
    case .failure(let error):  onError("ROUTE_CALCULATION_FAILED", error.localizedDescription)
    }
}
```

Rules:
- `isSilent = true`  → `waypoint.separatesLegs = false`
- `isSilent = false` (default) → `waypoint.separatesLegs = true`
- Origin and destination are always stops — the `isSilent` flag does not apply to them.

### Android — `waypointsIndices` array

Android's Directions API takes a flat `coordinates` list plus a `waypointsIndices` array listing which positions are **stops**. Omitted indices are silent. Origin (index 0) and destination (last index) must always be included in `waypointsIndices`.

```kotlin
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.router.NavigationRouterCallback

data class WaypointInput(val point: Point, val isSilent: Boolean)

fun buildRouteOptions(
    origin: Point,
    intermediates: List<WaypointInput>,
    destination: Point
): RouteOptions {
    val points = buildList {
        add(origin)
        addAll(intermediates.map { it.point })
        add(destination)
    }

    // Indices that correspond to STOPS (not silent). Origin (0) and destination (last) are always stops.
    val stopIndices = buildList {
        add(0)
        intermediates.forEachIndexed { i, w ->
            if (!w.isSilent) add(i + 1)   // +1 to offset past origin
        }
        add(points.size - 1)
    }.toIntArray()

    return RouteOptions.builder()
        .coordinates(points)
        .waypointsIndices(stopIndices)
        .alternatives(true)
        .profile("driving-traffic")
        .build()
}

mapboxNavigation.requestRoutes(buildRouteOptions(o, w, d), object : NavigationRouterCallback {
    override fun onRoutesReady(routes: List<NavigationRoute>, origin: String) {
        mapboxNavigation.setNavigationRoutes(routes)
    }
    override fun onFailure(reasons: List<RouterFailure>, opts: RouteOptions) {
        onError("ROUTE_CALCULATION_FAILED", reasons.first().message)
    }
    override fun onCanceled(opts: RouteOptions, origin: String) {}
})
```

⚠️ **Edge cases to test:**
- Zero intermediates — `waypointsIndices = [0, 1]`.
- All intermediates silent — `waypointsIndices = [0, N]` (origin + destination only).
- All intermediates stops — `waypointsIndices` lists every index.
- Mapbox Directions API caps requests at ~25 waypoints per request on the default tier; the SDK paginates/clusters automatically in some scenarios but B12 says we forward failures verbatim. Verify the current limit at implementation time.

- **All-or-nothing:** per SPEC B12, forward the failure reason verbatim via `onError`. No retry/skip logic in v3.0.

**Docs:**
- iOS: <https://github.com/mapbox/mapbox-navigation-ios/blob/main/README.md>
- Android: <https://docs.mapbox.com/android/navigation/api-reference/>

---

## 4. Arrival (B3)

### iOS

```swift
navigation.waypointsArrival
    .sink { status in
        switch status {
        case .arrived(let wp):
            onArrive(wp.coordinate)   // SDK only fires for stops, not silent waypoints
        case .approaching: break
        }
    }.store(in: &cancellables)
```

To distinguish intermediate vs. final, compare `wp == routeWaypoints.last`.

### Android

```kotlin
mapboxNavigation.registerArrivalObserver(object : ArrivalObserver {
    override fun onWaypointArrival(progress: RouteProgress)       { /* intermediate stop */ }
    override fun onNextRouteLegStart(legProgress: RouteLegProgress) { /* new leg started */ }
    override fun onFinalDestinationArrival(progress: RouteProgress) {
        onArrive(finalCoord)
    }
})
mapboxNavigation.setArrivalController(AutoArrivalController())  // auto-advance legs
```

Android already splits "final" vs. "waypoint" at the observer level — no manual index math needed.

---

## 5. Voice Guidance & Language (B4)

### iOS

```swift
var coreConfig = CoreConfig()
coreConfig.locale = Locale(identifier: language)         // e.g., "de"
coreConfig.ttsConfig = TTSConfig(audioVolume: mute ? 0 : 1.0, ...)
let provider = MapboxNavigationProvider(coreConfig: coreConfig)
// provider.routeVoiceController handles playback; falls back to AVSpeechSynthesizer
```

### Android

```kotlin
val speech = MapboxSpeechApi(context, language = "de")
val player = MapboxVoiceInstructionsPlayer(context, language = "de", options = VoiceInstructionsPlayerOptions.Builder().build())

mapboxNavigation.registerVoiceInstructionsObserver { voiceInstructions ->
    speech.generate(voiceInstructions) { result ->
        result.fold(
            { err -> player.play(err.fallback) { speech.clean(err.fallback) } },
            { ok  -> player.play(ok.announcement) { speech.clean(ok.announcement) } }
        )
    }
}
```

- **Cloud voice (Polly-backed) languages** as of SDK v3.20: `en, es, fr, de, it, pt-BR, pt-PT, ja, ko, zh-Hans, ru, sv, nb, nl, pl, tr, da`.
- **TTS fallback only:** `ar, ca, cs, fi, he, hu, id, ro, sl, uk, vi`.
- Verify at implementation time against the current SPEC §8 table and <https://docs.mapbox.com/ios/navigation/guides/system-integration/localization-and-internationalization/>.

---

## 6. Mute / Unmute (B5)

### iOS

⚠️ The SDK does not expose a "muted: Bool" publisher. Emulate it by setting `routeVoiceController.volume = 0.0` / `1.0` and emitting `onMuteChange` whenever we flip it. The SDK's built-in mute button tap is surfaced via `NavigationViewControllerDelegate` (verify exact method at impl time).

### Android

```kotlin
val audio = MapboxAudioGuidance.create()
mapboxNavigation.attachComponent(audio)

audio.mute(); audio.unmute(); audio.toggle()

// State flow (Kotlin Flow)
audio.stateFlow().collect { state ->
    onMuteChange(state.isMuted)       // also fires when user taps the built-in button
}
```

Android has a proper state flow — we get reactive mute state for free.

---

## 7. Cancel Navigation (B6)

### iOS

```swift
extension HybridReactNativeMapboxNavigation: NavigationViewControllerDelegate {
    func navigationViewControllerDidDismiss(_ vc: NavigationViewController, byCanceling canceled: Bool) {
        if canceled { onCancelNavigation() }
    }
}
```

### Android

`NavigationView` has a built-in exit button, but v3 does not expose a single "onCancel" callback. Observe route clearing instead:

```kotlin
mapboxNavigation.registerRoutesObserver { result ->
    if (result.reason == RoutesExtra.ROUTES_UPDATE_REASON_CLEAN_UP) {
        onCancelNavigation()
    }
}
```

⚠️ Also fires on programmatic cleanup — disambiguate by checking whether we triggered the clear ourselves.

---

## 8. Route Simulation (B7)

### iOS

```swift
let navOptions = NavigationOptions(
    mapboxNavigation: mapboxNavigation,
    voiceController: provider.routeVoiceController,
    eventsManager: provider.eventsManager(),
    simulationMode: shouldSimulateRoute ? .always : .never
)
```

`.always`, `.never`, `.onPoorGPS`, `.inTunnels`.

### Android

```kotlin
if (shouldSimulateRoute == true) {
    mapboxNavigation.startReplayTripSession()
    mapboxNavigation.mapboxReplayer.pushRealLocation(routes.first())
} else {
    mapboxNavigation.startTripSession()
}
```

Optional `ReplayRouteOptions` lets you tweak replay speed/accel/turn handling.

**Docs:** <https://docs.mapbox.com/android/navigation/ux/configuration/location-simulation/>

---

## 9. Route Progress (B8)

Both platforms push updates at ~1 Hz by default.

### iOS

```swift
navigation.routeProgress
    .compactMap { $0 }
    .sink { state in
        let p = state.routeProgress
        onRouteProgressChange(.init(
            distanceTraveled:  p.distanceTraveled,
            distanceRemaining: p.distanceRemaining,
            durationRemaining: p.durationRemaining,
            fractionTraveled:  p.fractionTraveled
        ))
    }.store(in: &cancellables)
```

### Android

```kotlin
mapboxNavigation.registerRouteProgressObserver { p ->
    onRouteProgressChange(RouteProgress(
        distanceTraveled  = p.distanceTraveled.toDouble(),
        distanceRemaining = p.distanceRemaining.toDouble(),
        durationRemaining = p.durationRemaining,
        fractionTraveled  = p.fractionTraveled.toDouble()
    ))
}
```

---

## 10. Location Updates (B9)

### iOS

```swift
navigation.locationMatching
    .throttle(for: .seconds(1), scheduler: DispatchQueue.main, latest: true)
    .sink { match in
        let loc: CLLocation
        switch match {
        case .matched(let l, _), .uncertain(let l), .notMatched(let l): loc = l
        }
        onLocationChange(loc.coordinate.latitude, loc.coordinate.longitude)
    }.store(in: &cancellables)
```

### Android

```kotlin
mapboxNavigation.registerLocationObserver(object : LocationObserver {
    override fun onNewRawLocation(raw: Location) {}
    override fun onNewLocationMatcherResult(r: LocationMatcherResult) {
        val loc = r.enhancedLocation                      // snapped to road
        onLocationChange(loc.latitude, loc.longitude)
    }
})
```

Use the **enhanced** location for UI; raw is only useful for debugging.

---

## 11. Error Handling (B10)

### Error code map (both platforms)

| Our code                    | iOS source                                        | Android source                                         |
|-----------------------------|---------------------------------------------------|--------------------------------------------------------|
| `GPS_PERMISSION_DENIED`     | `CLLocationManager.authorizationStatus`           | `ContextCompat.checkSelfPermission(ACCESS_FINE_LOCATION)` |
| `GPS_UNAVAILABLE`           | `CLLocationManager.locationServicesEnabled()`     | `LocationManager.isLocationEnabled`                    |
| `ROUTE_CALCULATION_FAILED`  | `DirectionsError.noRoute` / `.noSegment`          | `RouterFailureType.ROUTE_CREATION_ERROR` / `RESPONSE_PARSING_ERROR` |
| `NETWORK_ERROR`             | `URLError`                                        | `RouterFailureType.NETWORK_ERROR`                      |
| `INVALID_COORDINATES`       | pre-validate in Swift                             | `RouterFailureType.INPUT_ERROR`                        |
| `SDK_INIT_FAILED`           | missing `MBXAccessToken`                          | `RouterFailureType.AUTHENTICATION_ERROR`               |

### iOS

```swift
navigation.errors.sink { error in
    let code: String = {
        if let d = error as? DirectionsError {
            switch d {
            case .noRoute, .noSegment: return "ROUTE_CALCULATION_FAILED"
            default:                    return "ROUTE_CALCULATION_FAILED"
            }
        }
        if error is URLError { return "NETWORK_ERROR" }
        return "SDK_INIT_FAILED"
    }()
    onError(code, error.localizedDescription)
}.store(in: &cancellables)
```

### Android

Map `RouterFailureType` → our code in `onFailure` (see §3). Check `ACCESS_FINE_LOCATION` permission and `LocationManager.isLocationEnabled` before `startTripSession()` — emit `GPS_PERMISSION_DENIED` / `GPS_UNAVAILABLE` if either fails.

---

## 12. Rerouting (B11)

### iOS

```swift
navigation.rerouting.sink { status in
    switch status {
    case .fetching: break
    case .complete: onReroute()
    case .failed, .interrupted: break
    }
}.store(in: &cancellables)
```

### Android

```kotlin
mapboxNavigation.registerRoutesObserver { result ->
    if (result.reason == RoutesExtra.ROUTES_UPDATE_REASON_REROUTE) onReroute()
}
```

Android uses a single `RoutesObserver` with a `reason` discriminator — reroute is one of `NEW`, `REROUTE`, `REFRESH`, `ALTERNATIVE`, `CLEAN_UP`.

---

## 13. Day / Night Mode (B13)

### iOS

```swift
import MapboxNavigationUIKit

let navOptions = NavigationOptions(
    /* … */,
    styles: [StandardDayStyle(), StandardNightStyle()]
)
let navVC = NavigationViewController(navigationRoutes: routes, navigationOptions: navOptions)

switch colorScheme {
case "light": navVC.styleManager.applyStyle(type: .day)
case "dark":  navVC.styleManager.applyStyle(type: .night)
default:      break   // "auto" = StyleManager switches on sun position / tunnels
}
```

### Android

NavigationView inherits the host activity's theme; Android day/night via `values-night/` switches automatically when the system is in dark mode.

```kotlin
when (colorScheme) {
    "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    "dark"  -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    else    -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
}
```

**Decision (2026-04-20):** Android's "auto" follows **system dark mode** while iOS's "auto" follows **Mapbox sun position**. This asymmetry is accepted — users on each platform get the behavior that matches platform conventions. Document the difference in SPEC §B13 when we finalize.

---

## 14. Fonts / Typography (B14)

Both platforms offer only a **single global font hook**; per-element typography is out of scope for v3.0 (per SPEC).

### Scoping the override to our navigation view ("leak scope")

`UIAppearance` (iOS) and theme attributes (Android) are **global** by default — a naive implementation would re-skin text outside our view. Since the consuming RN app may also render its own content (including, occasionally, its own Mapbox views), we scope the override to the `NavigationViewController` / `NavigationView` subtree:

- **iOS:** use `UIAppearance(whenContainedInInstancesOf: [NavigationViewController.self])` instead of bare `appearance()`. Proxies only apply to labels whose ancestor chain includes our VC.
- **Android:** wrap `NavigationView` in a `ContextThemeWrapper` carrying a theme overlay with `android:fontFamily` — rather than mutating the host activity's theme.

This keeps the override contained; the host app's own UI is untouched.

### iOS

Subclass `StandardDayStyle` / `StandardNightStyle` and override `apply()` with **scoped** appearance proxies:

```swift
class CustomDayStyle: StandardDayStyle {
    let fontFamily: String?
    init(fontFamily: String?) { self.fontFamily = fontFamily; super.init() }
    required init() { fatalError() }

    override func apply() {
        super.apply()
        guard let name = fontFamily, let base = UIFont(name: name, size: 17) else { return }

        // Scoped: only affects labels inside NavigationViewController
        InstructionLabel.appearance(whenContainedInInstancesOf: [NavigationViewController.self])
            .font = base.withSize(20)
        SecondaryLabel.appearance(whenContainedInInstancesOf: [NavigationViewController.self])
            .font = base.withSize(16)
        // + other labels as discovered during impl (DistanceLabel, PrimaryLabel, …)
    }
}
```

⚠️ The exact set of label classes to override isn't documented — discover by introspecting `NavigationViewController`'s view hierarchy during implementation. Start with the classes Mapbox advertises in its custom-style guide and grow the list as we find gaps.

Font file must be registered in the host app's `UIAppFonts` (Info.plist) — we cannot bundle fonts inside the library.

### Android

Apply the font via a theme overlay scoped to `NavigationView` using `ContextThemeWrapper`:

```xml
<!-- our library/res/values/styles.xml -->
<style name="ThemeOverlay.ReactNativeMapboxNavigation.Typography" parent="">
    <!-- android:fontFamily accepts a font resource name -->
    <item name="android:fontFamily">@font/custom_font</item>
    <item name="fontFamily">@font/custom_font</item>
</style>
```

```kotlin
import android.view.ContextThemeWrapper
import androidx.core.content.res.ResourcesCompat

fun createNavigationView(context: Context, fontFamily: String?): NavigationView {
    val themed = if (fontFamily != null) {
        // Build a theme overlay that swaps in the requested font.
        // fontFamily is a font resource name the host app bundled in res/font/.
        ContextThemeWrapper(context, R.style.ThemeOverlay_ReactNativeMapboxNavigation_Typography)
            .also { wrapper ->
                val fontRes = context.resources.getIdentifier(fontFamily, "font", context.packageName)
                if (fontRes != 0) {
                    wrapper.theme.applyStyle(fontRes, true)  // ⚠️ placeholder — actual mechanism is runtime typeface resolution
                }
            }
    } else {
        context
    }
    return NavigationView(themed)
}
```

⚠️ **Runtime font resolution on Android is tricky:**
- `android:fontFamily` in the theme resolves at layout-inflation time. If `fontFamily` changes after the `NavigationView` is attached, the subtree won't pick up the new font without rebinding.
- For a truly dynamic `fontFamily` prop we may need to walk the `NavigationView` subtree after attach and call `TextView.setTypeface(...)` per text widget. Prototype during implementation to confirm.
- Font must be bundled by the **host app** in `res/font/<name>.xml` (or as a variable TTF). Our library cannot ship fonts.

**Decision:** if dynamic font swaps are rare (typical consumer picks a font once at app launch), accept the inflation-time limitation. Re-evaluate only if tests demand hot-swapping.

---

## 15. Imperative Methods (M1, M2)

### iOS

```swift
func recenterCamera() throws {
    navVC.navigationMapView.navigationCamera.update(cameraState: .following(zoom: 15, bearing: .course, pitch: 45))
}
func showRouteOverview() throws {
    guard let shape = routes.mainRoute.shape else { return }
    navVC.navigationMapView.navigationCamera.update(cameraState: .overview(geometry: shape))
}
```

⚠️ Exact `NavigationCameraState` initializer signatures vary across v3 minors — confirm at impl time.

### Android

```kotlin
override fun recenterCamera()    { navigationCamera.requestNavigationCameraToFollowing() }
override fun showRouteOverview() { navigationCamera.requestNavigationCameraToOverview() }
```

`NavigationCamera` requires a `MapboxNavigationViewportDataSource` fed with location + routes (see §10, §3). `NavigationView` may already wire this up internally — verify before wiring a second instance.

---

## Open Questions — Decide Before Implementation

| # | Question | Impact |
|---|----------|--------|
| Q1 | iOS: does child VC containment leak GPS / audio sessions on unmount? | Resource drain. If yes, consider modal presentation via a dedicated carrier VC or explicit `mapboxNavigation.tripSession().setToIdle()` in `deinit`. |
| Q2 | iOS: exact delegate method for built-in mute button tap? | Needed for `onMuteChange`. Fall back to manual `onMuteChange` emission. |
| Q3 | Android: exact package for `NavigationView` in v3.20 (`ui.components` vs `ui.maps`)? | Compile-time blocker. Resolve by grepping the SDK jar at impl time. |
| Q4 | Android: how to intercept the built-in cancel-button tap without relying on `ROUTES_UPDATE_REASON_CLEAN_UP`? | If no first-class callback, disambiguate via a flag set when we trigger the clear ourselves. |
| ~~Q5~~ | ~~Day/night "auto" asymmetry (iOS sun position vs. Android system dark mode).~~ | **Resolved 2026-04-20:** asymmetry accepted; document in SPEC §B13. |
| ~~Q6~~ | ~~`UIAppearance` / theme overrides leak to the host app's UI.~~ | **Resolved 2026-04-20:** scope to `NavigationViewController` via `UIAppearance(whenContainedInInstancesOf:)` (iOS) and to `NavigationView` via `ContextThemeWrapper` (Android). See §14. |
| Q9 | Android: can a dynamic `fontFamily` prop change be picked up without rebinding the view, or do we need a post-attach `TextView.setTypeface` walk? | UX / perf. Prototype during impl. Accept re-inflation if dynamic swaps are rare. |
| Q7 | Voice language table in SPEC §8 — re-verify against current Mapbox Voice API coverage. | Ongoing. Verify before each release. |
| Q8 | `NavigationCamera` ownership on Android: does `NavigationView` expose one, or do we construct our own? | Duplicate cameras would fight for updates. Resolve at impl time. |

---

## Reference Links

### iOS
- Guides: <https://docs.mapbox.com/ios/navigation/guides/>
- Location tracking: <https://docs.mapbox.com/ios/navigation/guides/get-started/location-tracking/>
- Localization: <https://docs.mapbox.com/ios/navigation/guides/system-integration/localization-and-internationalization/>
- GitHub: <https://github.com/mapbox/mapbox-navigation-ios>
- Simulation: <https://docs.mapbox.com/ios/maps/examples/simulate-navigation/>
- Child VC issues: [#3052](https://github.com/mapbox/mapbox-navigation-ios/issues/3052), [#664](https://github.com/mapbox/mapbox-navigation-ios/issues/664)

### Android
- Guides: <https://docs.mapbox.com/android/navigation/guides/>
- API reference: <https://docs.mapbox.com/android/navigation/api-reference/>
- Examples: <https://docs.mapbox.com/android/navigation/examples/>
- Migration v2→v3: <https://docs.mapbox.com/android/navigation/guides/migration-from-v2/>
- Simulation: <https://docs.mapbox.com/android/navigation/ux/configuration/location-simulation/>
- GitHub: <https://github.com/mapbox/mapbox-navigation-android>
