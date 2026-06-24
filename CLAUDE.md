# CLAUDE.md

## What is this repo?

`@jls-digital/react-native-mapbox-navigation` — a React Native library wrapping Mapbox Navigation SDK v3.20.x for turn-by-turn driving navigation. New Architecture only (Fabric + Nitro Modules).

## Repo Structure

```
src/                        # TypeScript source + Nitro spec
ios/                        # Swift native module
android/                    # Kotlin native module
nitrogen/                   # Auto-generated Nitro bindings (do not edit)
example/                    # Dev / demo app (Expo)
docs/
├── spec/SPEC.md            # Full product & API specification
├── technical/ARCHITECTURE.md  # Architecture & implementation notes
└── test-cases/TEST_CASES.md   # Natural-language E2E test cases
```

## Documentation

- **[Product Spec](docs/spec/SPEC.md)** — requirements, component API, error codes, supported languages
- **[Architecture](docs/technical/ARCHITECTURE.md)** — native bridge design, SDK integration, repo layout
- **[Mapbox SDK Research](docs/technical/MAPBOX_SDK_RESEARCH.md)** — per-requirement iOS / Android API map with code snippets, doc links, and open questions
- **[Test Cases](docs/test-cases/TEST_CASES.md)** — Maestro E2E test case descriptions

## Key Commands

```sh
yarn install          # install dependencies
yarn prepare          # build the library (bob build + nitrogen)
yarn nitrogen         # regenerate Nitro native bindings from .nitro.ts spec
yarn lint             # run ESLint
yarn typecheck        # run TypeScript type checking
yarn example start    # start the example app Metro bundler
yarn example ios      # build & run example on iOS simulator
yarn example android  # build & run example on Android emulator
```

## Native code: prefer Mapbox SDK components

Anything implemented on the native side (Swift / Kotlin) should rely as much as possible on the standard Mapbox Navigation / Maps SDK components and APIs — `MapboxRecenterButton`, `MapboxSpeedInfoView`, `MapboxManeuverView`, `MapboxTripProgressView`, `NavigationCameraStateChangedObserver`, `MapboxAudioGuidance.stateFlow`, `NavigationViewController` on iOS, etc. — rather than reinventing them. Only build custom UI / logic when the SDK genuinely doesn't ship the piece. Before adding a custom view, check the `ui-components` AAR (Android) or `MapboxNavigationUIKit` (iOS) for an equivalent.

## Android layouts under React Native — two recurring traps

Two distinct problems keep biting Android view code in this library. Recognize the symptom, apply the matching fix; don't reach for "explicit fixed dimensions" as a default workaround.

### Trap 1 — Mapbox SDK views measure to 0×0 when constructed programmatically

`MapboxRecenterButton`, `MapboxSpeedInfoView`, and other widgets in the Mapbox `ui-components` AAR have a single-arg `(Context)` constructor that **skips `initAttributes` entirely**. That means the base `android:minHeight` / `android:padding` / `android:elevation` from styles like `MapboxStyleExtendableButton` are never applied, and the view collapses to 0×0 inside its parent. `updateStyle(@StyleRes)` only applies the SDK-specific styleable attrs (icon drawable, background, text color) — not the base `android:` attrs, which can only be set at construction from an `AttributeSet`.

**Fix:** inflate the widget from an XML layout in `android/src/main/res/layout/` with `style="@style/Mapbox…"`. Example:

```xml
<!-- android/src/main/res/layout/mb_recenter_button.xml -->
<com.mapbox.navigation.ui.components.maps.camera.view.MapboxRecenterButton
    style="@style/MapboxStyleRecenterButton"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    … />
```

```kotlin
val button = LayoutInflater.from(context).inflate(
  R.layout.mb_recenter_button, parent, false
) as MapboxRecenterButton
parent.addView(button)
```

XML inflation hands the `AttributeSet` to the View ctor, which runs both the base View attr lookup and the SDK's `initAttributes` — the canonical path the SDK is designed for.

`MapboxSpeedInfoView` is a slight exception: it doesn't read its own theme attrs (styling comes from `MapboxSpeedInfoOptions.speedInfoStyle`), but its **inflated layout** uses `?attr/...` references that must resolve against `MapboxStyleSpeedLimit` — so it does need `ContextThemeWrapper(context, MapboxStyleSpeedLimit)` when constructed programmatically.

### Trap 2 — React Native's view tree swallows `requestLayout()` propagating from native children

Any time a native child view changes its measured size (visibility toggle, dynamic content, adapter change), Android's `View.setVisibility` / `View.requestLayout` walks the parent chain to mark them for a re-measure pass. Inside an RN-hosted view, that chain is intercepted and the re-measure never runs — so the child stays at its old (often zero) dimensions even though its layout state is dirty.

**Two reliable fixes; pick the one that matches the situation:**

1. **For simple visibility toggles (no size change needed):** default the view to `android:visibility="invisible"` instead of `"gone"`. `INVISIBLE` views participate in measurement (they reserve their natural space), so toggling to `VISIBLE` is a pure draw invalidation — no layout pass needed. Used by the recenter pill: it always occupies its small bottom-left footprint, just isn't drawn while following.

2. **For dynamic content size (a row appears/disappears, content height changes):** manually re-run `measure()` + `layout()` on the ancestor whose bounds are fixed by the RN host, using its current width/height as `EXACTLY` specs. `LinearLayout`/`FrameLayout` then redistribute children correctly. The `ManeuverBanner.forceContainerRelayout()` helper does this for the lane band; `MapboxSpeedInfoView` does it inline in the location observer when MUTCD/Vienna inner layouts flip.

```kotlin
private fun forceContainerRelayout() {
  val container = parent as? ViewGroup ?: return
  val w = container.width; val h = container.height
  if (w <= 0 || h <= 0) return
  val ws = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
  val hs = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
  container.measure(ws, hs)
  container.layout(container.left, container.top, container.right, container.bottom)
}
```

**Anti-pattern: do not just slap fixed `dp(N) × dp(M)` LayoutParams on every Mapbox widget.** That hides Trap 1 by force-feeding dimensions the SDK style would have provided, and hides Trap 2 by reserving permanent space even when the content is hidden. The visible result looks fine but the underlying layout is wrong — you'll regress the moment the content needs to grow, shrink, or be themed.

## Verification workflow

After making changes, always:

1. `yarn typecheck` — verify TypeScript compiles
2. `yarn lint` — verify no lint errors
3. `yarn prepare` — verify the library builds (module + types + nitrogen)
4. Build & run the example app on a simulator to verify changes visually
5. Use Maestro / mobile-interact MCP to interact with the running app and verify UI flows
