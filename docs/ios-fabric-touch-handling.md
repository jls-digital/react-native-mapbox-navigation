# iOS Touch Handling with React Native Fabric (Summary of debugging)

## Status: RESOLVED (2026-04-16)

## Problem

On **real iOS devices**, touch events do not work in the Mapbox turn-by-turn navigation view when embedded in a React Native Fabric app. Native UIKit buttons (mute, overview, cancel) and map gestures are unresponsive.

Two separate issues were identified:
1. **Debug builds**: `shouldSimulateRoute={true}` causes the Mapbox simulation mode to block touches ~1 second after loading. This is a Mapbox SDK behavior, not a code bug. Disable simulation for debug testing on real devices.
2. **All builds on real devices**: `UILayoutContainerView` (from `react-native-screens` / `UINavigationController`) has `isUserInteractionEnabled = false`, blocking all touches from reaching the Mapbox native view. The push transition animation temporarily provides an alternative touch path, which is why touches briefly work during the transition but stop once it settles.

## Root Causes

### 1. `UILayoutContainerView.isUserInteractionEnabled = false`

`react-native-screens` uses a native `UINavigationController` to manage screen transitions. Its internal `UILayoutContainerView` has `isUserInteractionEnabled = false`, which blocks UIKit's `hitTest` from ever reaching `MapboxNavigationView`. The push transition animation creates temporary `_UIParallaxTransitionCardView` views that bypass this, which is why touches work briefly during the transition but fail once it completes.

**Fix**: Walk up the view hierarchy after embedding and set `isUserInteractionEnabled = true` on all parent views. Re-apply after 0.6s to catch when the transition animation resets it.

### 2. Improper `removeFromSuperview` teardown

The original `removeFromSuperview` did not follow proper UIKit child VC lifecycle:
- Missing `willMove(toParent: nil)` — Mapbox SDK never cleans up location listeners, gesture recognizers, navigation service
- `vc.view` never explicitly removed
- `embedded`/`embedding` flags never reset

This caused stale state to accumulate across navigation sessions.

**Fix**: Proper UIKit teardown with `super.removeFromSuperview()` first, then cleanup.

### 3. Fabric touch system conflict

Fabric's touch responder (`RCTSurfaceTouchHandler`) treats all subviews as React-managed. The embedded `NavigationViewController`'s native UIKit views (buttons, map gestures) are not React components, so Fabric doesn't properly route touches to them.

**Fix**: Override `reactSubviews() -> []` and no-op `insertReactSubview`/`removeReactSubview` to isolate the native views from Fabric's touch management. Override `hitTest` to manually forward touches to the NavigationViewController's view hierarchy.

## Working Solution (`MapboxNavigationView.swift`)

Key changes from the original library code:

```swift
// 1. clipsToBounds = false in init
override init(frame: CGRect) {
    self.embedded = false
    self.embedding = false
    super.init(frame: frame)
    clipsToBounds = false
}

// 2. hitTest override for Fabric compatibility
override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
    guard let navView = navViewController?.view else {
        return super.hitTest(point, with: event)
    }
    let result = super.hitTest(point, with: event)
    if let result = result, result !== self {
        return result
    }
    let convertedPoint = convert(point, to: navView)
    if navView.point(inside: convertedPoint, with: event) {
        return navView.hitTest(convertedPoint, with: event) ?? navView
    }
    return nil
}

// 3. Prevent Fabric from managing native subviews' touch handling
override func reactSubviews() -> [UIView]! { return [] }
override func insertReactSubview(_ subview: UIView!, at atIndex: Int) {}
override func removeReactSubview(_ subview: UIView!) {}

// 4. Proper UIKit child VC teardown
override func removeFromSuperview() {
    super.removeFromSuperview()
    if let vc = navViewController {
        vc.willMove(toParent: nil)
        vc.view.removeFromSuperview()
        vc.removeFromParent()
        navViewController = nil
    }
    embedded = false
    embedding = false
}

// 5. Fix parent view interaction + delayed re-apply after transition
// Called in embed() after NavVC is set up:
//   strongSelf.fixParentInteraction()
//   DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { [weak self] in
//     self?.fixParentInteraction()
//   }
private func fixParentInteraction() {
    var view: UIView? = self.superview
    while let v = view {
        if !v.isUserInteractionEnabled {
            v.isUserInteractionEnabled = true
        }
        view = v.superview
    }
}

// 6. autoresizingMask in embed()
vc.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
```

## Consumer-Side Fix (App)

When dismissing navigation via a React Native `Modal` popup + `navigation.goBack()`, call `closePopup()` **before** `navigation.goBack()` to avoid a concurrent Modal dismiss + screen pop animation that can leave a ghost UIViewController intercepting touches:

```typescript
const onCancelNavigation = () => {
    closePopup();         // dismiss Modal first
    navigation.goBack();  // then pop the screen
};
```

## Tested Environment

- React Native 0.84.1 with Fabric (new architecture)
- Mapbox Navigation SDK v2.19
- react-native-screens 4.23.0
- react-navigation/native-stack 7.12.0
- iOS 18.x on physical iPhone

## Investigation Timeline

| # | What was tried | Result |
|---|---------------|--------|
| 1 | Pre-existing patch (hitTest, reactSubviews, etc.) | 1st session: ambiguous. 2nd+: broken |
| 2 | + removeFromSuperview fix (super LAST) | All broken (super ordering issue) |
| 3 | Minimal: original hitTest + removeFromSuperview (super LAST) | All broken |
| 4 | removeFromSuperview with super FIRST | All broken |
| 5 | Original unpatched code | All broken (no touch fixes at all) |
| 6 | disableRCTTouchCancellation on RCTSurfaceTouchHandler | Not the cause — disabling entirely didn't help |
| 7 | vc.view on parentVC.view (outside React tree) | UIKit VC hierarchy error |
| 8 | View hierarchy dump | Found `UILayoutContainerView interact=false` |
| 9 | fixParentInteraction (force interact=true) | 1st+2nd work, 3rd broken |
| 10 | fixParentInteraction + delayed re-apply (0.6s) | **All sessions work** |
