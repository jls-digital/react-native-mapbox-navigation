package com.margelo.nitro.jlsdigital.reactnativemapboxnavigation

/**
 * Outcome of a session-start attempt; drives whether afterUpdate retries.
 *  - [STARTED]   → preconditions met; the route request can be kicked off.
 *  - [TERMINAL]  → a precondition retrying can't fix (invalid coordinates);
 *                  the scheduler latches so onError doesn't re-fire each tick.
 *  - [RETRYABLE] → a transient precondition (permission pending, GPS off);
 *                  the scheduler stays unlatched so a later update can proceed.
 */
enum class SessionStart { STARTED, RETRYABLE, TERMINAL }

// ── SessionStartDecision ──────────────────────────────
// Pure decision extracted from
// `HybridReactNativeMapboxNavigation.startSessionIfReady`. The god class
// still gathers the three booleans via its framework calls
// (`CoordinateValidation`, `LocationManager`, `ContextCompat.checkSelfPermission`)
// and emits the matching onError; this unit holds only the precedence /
// branching, so it is unit-testable without any Android framework type.
//
// Precedence matches the original guard order exactly:
//   1. coords invalid          → TERMINAL  (retrying can't fix it)
//   2. location unavailable    → RETRYABLE (GPS may come back)
//   3. permission not granted  → RETRYABLE (user may grant it)
//   4. all good                → STARTED
//
// Mirrors iOS `SessionStartDecision`.
object SessionStartDecision {

  fun decide(
    coordsValid: Boolean,
    locationAvailable: Boolean,
    permissionGranted: Boolean
  ): SessionStart {
    if (!coordsValid) return SessionStart.TERMINAL
    if (!locationAvailable) return SessionStart.RETRYABLE
    if (!permissionGranted) return SessionStart.RETRYABLE
    return SessionStart.STARTED
  }
}
