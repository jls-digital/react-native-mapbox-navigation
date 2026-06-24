package com.margelo.nitro.jlsdigital.reactnativemapboxnavigation

// ── RouteErrorClassifier ──────────────────────────────
// The message-substring half of route-error classification, split out of
// `HybridReactNativeMapboxNavigation.classifyRouterFailure` as a
// framework-free unit so it can be host-unit-tested without the Nitro
// runtime or any Mapbox SDK type (no `RouterFailure` import). The god
// class's `classifyRouterFailure(reason)` now just forwards
// `reason?.message` here.
//
// Parity contract with iOS (`RouteErrorMessageClassifier.classify`) — the
// same input substrings map to the same shared `MapboxNavigationErrorCode`:
//   network/timeout/connection         -> NETWORK_ERROR
//   auth/401/403                       -> SDK_INIT_FAILED
//   no route / (matching) segment etc. -> ROUTE_CALCULATION_FAILED
//   input/invalid                      -> INVALID_COORDINATES
//   else                               -> ROUTE_CALCULATION_FAILED
// Order matters: network before auth, auth before the unroutable check,
// and the unroutable check BEFORE the generic input/invalid check. Mapbox's
// "NoRoute"/"NoSegment" failures phrase themselves as e.g. "Could not find a
// matching segment for input coordinates" — that contains "input" but is a
// routing failure, not malformed input, so it must be caught first. (Genuinely
// out-of-range coordinates never reach here: `CoordinateValidation` rejects
// them before a route is ever requested.) A null message lowercases to "" →
// ROUTE_CALCULATION_FAILED.
object RouteErrorClassifier {
  fun classify(message: String?): String {
    val msg = message.orEmpty().lowercase()
    return when {
      msg.contains("network") || msg.contains("timeout") || msg.contains("connection") -> "NETWORK_ERROR"
      msg.contains("auth") || msg.contains("401") || msg.contains("403") -> "SDK_INIT_FAILED"
      msg.contains("no route") || msg.contains("no segment") ||
        msg.contains("matching segment") || msg.contains("unable to route") -> "ROUTE_CALCULATION_FAILED"
      msg.contains("input") || msg.contains("invalid") -> "INVALID_COORDINATES"
      else -> "ROUTE_CALCULATION_FAILED"
    }
  }
}
