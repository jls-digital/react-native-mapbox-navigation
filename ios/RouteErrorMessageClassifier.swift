import Foundation

// ── RouteErrorMessageClassifier ───────────────────────
// The message-substring half of route-error classification, split out as a
// Foundation-only unit so it can be host-unit-tested (and kept in lockstep
// with Android's `classifyRouterFailure`) WITHOUT dragging in MapboxDirections
// and its binary-framework tree. `RouteErrorClassifier` (which needs the
// `DirectionsError` types) delegates its message paths here.
//
// Parity contract with Android (same input substrings → same code):
//   network/timeout/connection         -> NETWORK_ERROR
//   auth/401/403                       -> SDK_INIT_FAILED
//   no route / (matching) segment etc. -> ROUTE_CALCULATION_FAILED
//   input/invalid                      -> INVALID_COORDINATES
//   else                               -> ROUTE_CALCULATION_FAILED
// Order matters: network before auth, auth before the unroutable check, and
// the unroutable check BEFORE the generic input/invalid check. Mapbox's
// "NoRoute"/"NoSegment" failures phrase themselves as e.g. "Could not find a
// matching segment for input coordinates" — that contains "input" but is a
// routing failure, not malformed input, so it must be caught first. (Genuinely
// out-of-range coordinates never reach here: `CoordinateValidation` rejects
// them before a route is ever requested.)
enum RouteErrorMessageClassifier {
  static func classify(_ message: String) -> String {
    let msg = message.lowercased()
    if msg.contains("network") || msg.contains("timeout") || msg.contains("connection") {
      return "NETWORK_ERROR"
    }
    if msg.contains("auth") || msg.contains("401") || msg.contains("403") {
      return "SDK_INIT_FAILED"
    }
    if msg.contains("no route") || msg.contains("no segment")
      || msg.contains("matching segment") || msg.contains("unable to route") {
      return "ROUTE_CALCULATION_FAILED"
    }
    if msg.contains("input") || msg.contains("invalid") {
      return "INVALID_COORDINATES"
    }
    return "ROUTE_CALCULATION_FAILED"
  }
}
