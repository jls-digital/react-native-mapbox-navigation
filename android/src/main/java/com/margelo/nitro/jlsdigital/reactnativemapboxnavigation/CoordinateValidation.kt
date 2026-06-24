package com.margelo.nitro.jlsdigital.reactnativemapboxnavigation

// ── CoordinateValidation ──────────────────────────────
// Pure coordinate-range checks extracted from
// `HybridReactNativeMapboxNavigation.ensureCoordinatesValid`. Operates on
// primitive `Double` lat/lon (not the Nitro-generated `Coordinates`) so it
// is unit-testable without the Nitro runtime. The god class keeps emitting
// the error via `onError`/`emitPreconditionError` — it just calls
// [validate] for the verdict + the (code, message) to emit.
//
// Mirrors iOS `CoordinateValidation`.
object CoordinateValidation {

  /**
   * A coordinate is valid when it is inside the WGS84 lat/lon range AND is
   * not the default (0, 0) sentinel (which Nitro hands us before the JS
   * props have been applied).
   */
  fun isValid(lat: Double, lon: Double): Boolean =
    lat in -90.0..90.0 &&
    lon in -180.0..180.0 &&
    !(lat == 0.0 && lon == 0.0)

  /**
   * Verdict of validating a full route's coordinates.
   *  - [Ok] when origin, destination, and every waypoint are valid.
   *  - [Invalid] carries exactly the (code, message) the god class should
   *    surface via `onError` — preserving the previous strings.
   */
  sealed class Result {
    object Ok : Result()
    data class Invalid(val code: String, val message: String) : Result()
  }

  /**
   * Validate origin, destination, and waypoints in the SAME order the god
   * class did, so the emitted message matches the first offending input.
   * Waypoints are described by their 1-based index for the error message.
   */
  fun validate(
    origin: Pair<Double, Double>,
    destination: Pair<Double, Double>,
    waypoints: List<Pair<Double, Double>>
  ): Result {
    if (!isValid(origin.first, origin.second) ||
      !isValid(destination.first, destination.second)
    ) {
      return Result.Invalid(
        "INVALID_COORDINATES",
        "Origin or destination is outside the valid lat/lon range or is the default (0, 0)."
      )
    }
    waypoints.forEachIndexed { i, wp ->
      if (!isValid(wp.first, wp.second)) {
        return Result.Invalid("INVALID_COORDINATES", "Waypoint #${i + 1} coordinate is invalid.")
      }
    }
    return Result.Ok
  }
}
