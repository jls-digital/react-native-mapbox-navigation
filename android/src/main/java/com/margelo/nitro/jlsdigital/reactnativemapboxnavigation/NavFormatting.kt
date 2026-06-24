package com.margelo.nitro.jlsdigital.reactnativemapboxnavigation

import java.util.Locale

// ── NavFormatting ─────────────────────────────────────
// The hand-rolled metric distance/duration fallbacks that were duplicated
// across [TripPanel], [ManeuverBanner], and the god class, plus the
// `haversine` great-circle distance the god class used for location-emit
// de-duping. Framework-free (primitives + an explicit [Locale]) so it is
// host-unit-testable.
//
// These fallbacks are used only when the SDK has not yet handed us a
// formatter (whose own output already does locale + unit). An explicit
// Locale is passed to String.format so the "km" decimal separator is
// deterministic rather than the implicit device default.
object NavFormatting {

  /**
   * Below 1000 m → whole metres ("750 m"); at/above → kilometres to one
   * decimal ("1.2 km"). The 999↔1000 m boundary lands on the metres side at
   * 999 and the km side at exactly 1000.
   */
  fun formatDistanceMeters(meters: Double, locale: Locale): String {
    if (meters < 1000) return "${meters.toInt()} m"
    return String.format(locale, "%.1f km", meters / 1000.0)
  }

  /**
   * Below 60 min → minutes ("45 min"); at/above → hours + minutes ("1h 5m").
   * Seconds are floored to whole minutes first, so 59↔60 min lands at 3540 s
   * ("59 min") vs 3600 s ("1h 0m").
   */
  fun formatDurationSeconds(secs: Double): String {
    val mins = (secs / 60.0).toInt()
    if (mins < 60) return "$mins min"
    val h = mins / 60
    val m = mins % 60
    return "${h}h ${m}m"
  }

  /** Great-circle distance in metres between two WGS84 coordinates. */
  fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2).let { it * it } +
      Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
      Math.sin(dLon / 2).let { it * it }
    return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  }
}
