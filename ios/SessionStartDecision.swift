import Foundation

/// Outcome of a session-start attempt; drives whether afterUpdate retries.
///  - `.started`   → preconditions met; the route request can be kicked off.
///  - `.terminal`  → a precondition retrying can't fix (invalid coordinates);
///                   the scheduler latches so onError doesn't re-fire each tick.
///  - `.retryable` → a transient precondition (permission pending, GPS off);
///                   the scheduler stays unlatched so a later update can proceed.
enum SessionStart: Equatable { case started, retryable, terminal }

// ── SessionStartDecision ──────────────────────────────
// Pure decision extracted from
// `HybridReactNativeMapboxNavigation.startSessionIfReady`. The god class
// still gathers the three booleans via its framework calls
// (`CoordinateValidation`, `CLLocationManager.locationServicesEnabled()`,
// `CLLocationManager.authorizationStatus`) and emits the matching onError;
// this unit holds only the precedence/branching, so it is unit-testable
// without CoreLocation.
//
// Precedence matches the original guard order exactly:
//   1. coords invalid          → .terminal  (retrying can't fix it)
//   2. location unavailable    → .retryable (GPS may come back)
//   3. permission not granted  → .retryable (user may grant it)
//   4. all good                → .started
enum SessionStartDecision {

  static func decideSessionStart(
    coordsValid: Bool,
    locationAvailable: Bool,
    permissionGranted: Bool
  ) -> SessionStart {
    guard coordsValid else { return .terminal }
    guard locationAvailable else { return .retryable }
    guard permissionGranted else { return .retryable }
    return .started
  }
}
