package com.margelo.nitro.ch.jls.reactnative.mapboxnavigation

import org.junit.Assert.assertEquals
import org.junit.Test

// ── SessionStartDecision ──────────────────────────────────────────────────
// Mirrors iOS PureUnitsTests.testSessionStartDecision. Invalid coords =
// TERMINAL is the popup-loop fix.
class SessionStartDecisionTest {

  @Test
  fun allPreconditionsMetStarts() {
    assertEquals(
      SessionStart.STARTED,
      SessionStartDecision.decide(coordsValid = true, locationAvailable = true, permissionGranted = true)
    )
  }

  @Test
  fun invalidCoordsAreTerminal() {
    assertEquals(
      SessionStart.TERMINAL,
      SessionStartDecision.decide(coordsValid = false, locationAvailable = true, permissionGranted = true)
    )
  }

  @Test
  fun locationUnavailableIsRetryable() {
    assertEquals(
      SessionStart.RETRYABLE,
      SessionStartDecision.decide(coordsValid = true, locationAvailable = false, permissionGranted = true)
    )
  }

  @Test
  fun permissionDeniedIsRetryable() {
    assertEquals(
      SessionStart.RETRYABLE,
      SessionStartDecision.decide(coordsValid = true, locationAvailable = true, permissionGranted = false)
    )
  }

  @Test
  fun invalidCoordsTakePrecedenceOverTransientFailures() {
    assertEquals(
      SessionStart.TERMINAL,
      SessionStartDecision.decide(coordsValid = false, locationAvailable = false, permissionGranted = false)
    )
  }
}
