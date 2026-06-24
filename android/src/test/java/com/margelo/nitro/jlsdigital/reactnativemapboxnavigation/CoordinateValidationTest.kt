package com.margelo.nitro.jlsdigital.reactnativemapboxnavigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// ── CoordinateValidation ──────────────────────────────────────────────────
// Mirrors iOS PureUnitsTests.testCoordinateValidity / testValidateCoordinatesVerdict.
class CoordinateValidationTest {

  @Test
  fun isValidBoundariesAndSentinel() {
    assertTrue(CoordinateValidation.isValid(47.37, 8.54))
    assertTrue(CoordinateValidation.isValid(-90.0, 180.0)) // boundary
    assertTrue(CoordinateValidation.isValid(90.0, -180.0)) // boundary
    assertFalse(CoordinateValidation.isValid(0.0, 0.0)) // (0,0) sentinel
    assertFalse(CoordinateValidation.isValid(90.01, 0.0))
    assertFalse(CoordinateValidation.isValid(0.0, 180.01))
    assertFalse(CoordinateValidation.isValid(-90.01, 0.0))
    assertFalse(CoordinateValidation.isValid(0.0, -180.01))
  }

  @Test
  fun validateOkWhenAllValid() {
    assertEquals(
      CoordinateValidation.Result.Ok,
      CoordinateValidation.validate(47.37 to 8.54, 46.95 to 7.45, emptyList())
    )
  }

  @Test
  fun validateInvalidOriginUsesOriginDestinationMessage() {
    val result = CoordinateValidation.validate(0.0 to 0.0, 46.95 to 7.45, emptyList())
    assertTrue(result is CoordinateValidation.Result.Invalid)
    result as CoordinateValidation.Result.Invalid
    assertEquals("INVALID_COORDINATES", result.code)
    assertEquals(
      "Origin or destination is outside the valid lat/lon range or is the default (0, 0).",
      result.message
    )
  }

  @Test
  fun validateInvalidDestinationUsesOriginDestinationMessage() {
    val result = CoordinateValidation.validate(47.37 to 8.54, 0.0 to 0.0, emptyList())
    assertTrue(result is CoordinateValidation.Result.Invalid)
    result as CoordinateValidation.Result.Invalid
    assertEquals("INVALID_COORDINATES", result.code)
    assertTrue(result.message.contains("Origin or destination"))
  }

  @Test
  fun validateReportsFirstInvalidWaypointByOneBasedIndex() {
    // First waypoint valid, second out of range → message names "#2".
    val result = CoordinateValidation.validate(
      47.37 to 8.54, 46.95 to 7.45,
      listOf(10.0 to 10.0, 200.0 to 0.0)
    )
    assertTrue(result is CoordinateValidation.Result.Invalid)
    result as CoordinateValidation.Result.Invalid
    assertEquals("INVALID_COORDINATES", result.code)
    assertEquals("Waypoint #2 coordinate is invalid.", result.message)
  }
}
