import Foundation

// ── CoordinateValidation ──────────────────────────────
// Pure coordinate-range checks extracted from
// `HybridReactNativeMapboxNavigation.ensureCoordinatesValid`. Operates on
// primitive `Double` lat/lon (not the Nitro-generated `Coordinates`) so it
// is unit-testable without the Nitro runtime. The god class keeps emitting
// the error via `onError`/`emitPreconditionError` — it just calls
// `validateCoordinates` for the verdict + the (code, message) to emit.
enum CoordinateValidation {

  /// A coordinate is valid when it is inside the WGS84 lat/lon range AND is
  /// not the default (0, 0) sentinel (which Nitro hands us before the JS
  /// props have been applied).
  static func isValidCoordinate(latitude: Double, longitude: Double) -> Bool {
    latitude >= -90 && latitude <= 90 &&
    longitude >= -180 && longitude <= 180 &&
    !(latitude == 0 && longitude == 0)
  }

  /// Verdict of validating a full route's coordinates.
  /// `.ok` when origin, destination, and every waypoint are valid.
  /// `.invalid(code, message)` carries exactly the (code, message) the god
  /// class should surface via `onError` — preserving the previous strings.
  enum Result: Equatable {
    case ok
    case invalid(code: String, message: String)
  }

  /// Validate origin, destination, and waypoints in the SAME order the god
  /// class did, so the emitted message matches the first offending input.
  /// Waypoints are described by their 1-based index for the error message.
  static func validateCoordinates(
    origin: (latitude: Double, longitude: Double),
    destination: (latitude: Double, longitude: Double),
    waypoints: [(latitude: Double, longitude: Double)]
  ) -> Result {
    if !isValidCoordinate(latitude: origin.latitude, longitude: origin.longitude) ||
       !isValidCoordinate(latitude: destination.latitude, longitude: destination.longitude) {
      return .invalid(
        code: "INVALID_COORDINATES",
        message: "Origin or destination is outside the valid lat/lon range or is the default (0, 0)."
      )
    }
    for (i, wp) in waypoints.enumerated()
    where !isValidCoordinate(latitude: wp.latitude, longitude: wp.longitude) {
      return .invalid(
        code: "INVALID_COORDINATES",
        message: "Waypoint #\(i + 1) coordinate is invalid."
      )
    }
    return .ok
  }
}
