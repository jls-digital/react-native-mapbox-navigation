package com.margelo.nitro.ch.jls.reactnative.mapboxnavigation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

// ── NavFormatting ─────────────────────────────────────────────────────────
// Android-specific pure unit (no iOS mirror): distance/duration fallback
// formatters + haversine. Pin Locale.US for deterministic decimal separators.
class NavFormattingTest {

  @Test
  fun distanceMetresVsKilometresBoundary() {
    // 999 m stays in metres; 1000 m flips to km (one decimal).
    assertEquals("999 m", NavFormatting.formatDistanceMeters(999.0, Locale.US))
    assertEquals("1.0 km", NavFormatting.formatDistanceMeters(1000.0, Locale.US))
    assertEquals("0 m", NavFormatting.formatDistanceMeters(0.0, Locale.US))
    assertEquals("750 m", NavFormatting.formatDistanceMeters(750.0, Locale.US))
    assertEquals("1.2 km", NavFormatting.formatDistanceMeters(1234.0, Locale.US))
  }

  @Test
  fun distanceUsesProvidedLocaleDecimalSeparator() {
    // German uses a comma decimal separator — proves the Locale is honoured.
    assertEquals("1,2 km", NavFormatting.formatDistanceMeters(1234.0, Locale.GERMANY))
  }

  @Test
  fun durationMinutesVsHoursBoundary() {
    // 59 min stays in minutes; 60 min flips to "1h 0m".
    assertEquals("59 min", NavFormatting.formatDurationSeconds(59 * 60.0))
    assertEquals("1h 0m", NavFormatting.formatDurationSeconds(60 * 60.0))
    assertEquals("0 min", NavFormatting.formatDurationSeconds(0.0))
    assertEquals("45 min", NavFormatting.formatDurationSeconds(45 * 60.0))
    assertEquals("1h 5m", NavFormatting.formatDurationSeconds(65 * 60.0))
    // Seconds floor to whole minutes (matches (secs / 60.0).toInt()).
    assertEquals("0 min", NavFormatting.formatDurationSeconds(59.0))
  }

  @Test
  fun haversineZurichToBernIsAboutNinetyFiveKilometres() {
    // Zurich (47.3769, 8.5417) ↔ Bern (46.9480, 7.4474) ≈ 95 km great-circle.
    val meters = NavFormatting.haversineMeters(47.3769, 8.5417, 46.9480, 7.4474)
    assertEquals(95000.0, meters, 3000.0) // within 3 km tolerance
  }

  @Test
  fun haversineSamePointIsZero() {
    assertEquals(0.0, NavFormatting.haversineMeters(47.0, 8.0, 47.0, 8.0), 1e-6)
  }
}
