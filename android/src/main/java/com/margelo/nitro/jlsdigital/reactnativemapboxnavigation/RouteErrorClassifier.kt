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
//   network/timeout/connection -> NETWORK_ERROR
//   auth/401/403               -> SDK_INIT_FAILED
//   input/invalid              -> INVALID_COORDINATES
//   else                       -> ROUTE_CALCULATION_FAILED
// Order matters: network is checked before auth, auth before the generic
// input/invalid check. A null message lowercases to "" → ROUTE_CALCULATION_FAILED.
object RouteErrorClassifier {
  fun classify(message: String?): String {
    val msg = message.orEmpty().lowercase()
    return when {
      msg.contains("network") || msg.contains("timeout") || msg.contains("connection") -> "NETWORK_ERROR"
      msg.contains("auth") || msg.contains("401") || msg.contains("403") -> "SDK_INIT_FAILED"
      msg.contains("input") || msg.contains("invalid") -> "INVALID_COORDINATES"
      else -> "ROUTE_CALCULATION_FAILED"
    }
  }
}
