import Foundation
import MapboxDirections

// ── RouteErrorClassifier ──────────────────────────────
// Pure mapping of routing failures → shared `MapboxNavigationErrorCode`
// strings. Extracted from `HybridReactNativeMapboxNavigation` so the
// classification logic is unit-testable without the Nitro runtime or any
// UIKit / MapboxNavigationUIKit dependency (Foundation + MapboxDirections
// only).
//
// Kept in lockstep with Android's `classifyRouterFailure` so the same
// failure yields the same code on both platforms. Android keys off the
// failure message substrings:
//   network/timeout/connection -> NETWORK_ERROR
//   auth/401/403               -> SDK_INIT_FAILED
//   input/invalid              -> INVALID_COORDINATES
//   else                       -> ROUTE_CALCULATION_FAILED
// iOS gets structured `DirectionsError` cases, so we map those directly
// and additionally apply the same substring heuristic to `.unknown`
// payloads (and any non-Directions error) for full parity — in
// particular so the routing path CAN emit INVALID_COORDINATES, which
// the previous implementation never did.
enum RouteErrorClassifier {

  // Classify a routing failure into one of the shared
  // `MapboxNavigationErrorCode`s.
  static func classifyRouteError(_ error: Error) -> String {
    if let directionsError = error as? DirectionsError {
      switch directionsError {
      case .network:
        return "NETWORK_ERROR"
      case .invalidInput:
        // Server rejected the request input — parity with Android's
        // "input"/"invalid" -> INVALID_COORDINATES mapping.
        return "INVALID_COORDINATES"
      case .unknown(let response, _, _, let message):
        if let http = response as? HTTPURLResponse,
           http.statusCode == 401 || http.statusCode == 403 {
          return "SDK_INIT_FAILED"
        }
        return RouteErrorMessageClassifier.classify(message ?? directionsError.localizedDescription)
      default:
        return RouteErrorMessageClassifier.classify(directionsError.localizedDescription)
      }
    }
    if error is URLError {
      return "NETWORK_ERROR"
    }
    return RouteErrorMessageClassifier.classify(error.localizedDescription)
  }
}
