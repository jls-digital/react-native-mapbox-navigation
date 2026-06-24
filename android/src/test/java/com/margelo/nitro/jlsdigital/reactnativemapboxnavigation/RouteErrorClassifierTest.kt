package com.margelo.nitro.jlsdigital.reactnativemapboxnavigation

import org.junit.Assert.assertEquals
import org.junit.Test

// ── RouteErrorClassifier — shared substring heuristic ─────────────────────
// PARITY TABLE — these EXACT rows are mirrored in the iOS XCTest suite
// (PureUnitsTests.testMessageParityTable). Keep them identical so the two
// classifiers can't drift. Codes: NETWORK_ERROR / SDK_INIT_FAILED /
// INVALID_COORDINATES / ROUTE_CALCULATION_FAILED.
class RouteErrorClassifierTest {

  @Test
  fun parityTable() {
    val table = listOf(
      // network / timeout / connection → NETWORK_ERROR
      "A network error occurred" to "NETWORK_ERROR",
      "Request timeout" to "NETWORK_ERROR",
      "Lost connection to host" to "NETWORK_ERROR",
      "NETWORK UNREACHABLE" to "NETWORK_ERROR", // case-insensitive
      // auth / 401 / 403 → SDK_INIT_FAILED
      "Authentication failed" to "SDK_INIT_FAILED",
      "HTTP 401 Unauthorized" to "SDK_INIT_FAILED",
      "HTTP 403 Forbidden" to "SDK_INIT_FAILED",
      // input / invalid → INVALID_COORDINATES
      "Invalid input provided" to "INVALID_COORDINATES",
      "The input was malformed" to "INVALID_COORDINATES",
      "invalid coordinate" to "INVALID_COORDINATES",
      // everything else → ROUTE_CALCULATION_FAILED
      "Something unexpected went wrong" to "ROUTE_CALCULATION_FAILED",
      "" to "ROUTE_CALCULATION_FAILED",
      "No route found" to "ROUTE_CALCULATION_FAILED",
      // precedence: network beats auth beats input
      "network auth invalid" to "NETWORK_ERROR",
      "auth invalid input" to "SDK_INIT_FAILED",
    )
    for ((message, expected) in table) {
      assertEquals(
        "classify(\"$message\") should be $expected",
        expected, RouteErrorClassifier.classify(message)
      )
    }
  }

  @Test
  fun nullMessageFallsBackToRouteCalculationFailed() {
    // A null RouterFailure.message lowercases to "" → ROUTE_CALCULATION_FAILED.
    assertEquals("ROUTE_CALCULATION_FAILED", RouteErrorClassifier.classify(null))
  }
}
