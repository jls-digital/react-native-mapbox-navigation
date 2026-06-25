package com.margelo.nitro.ch.jls.reactnative.mapboxnavigation

import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.geojson.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

// ── RouteOptionsFactory — Layer-2 SDK-integration test ────────────────────
// Builds against the real mapbox-java `RouteOptions`/`Point` value types (plain
// JVM, on the unit-test classpath) and asserts the resulting options off-device.
// Covers coordinate ordering, silent-waypoint leg-splitting, and language/voice
// units — the cross-platform contract mirrored by iOS `buildWaypoints`.
class RouteOptionsFactoryTest {

  private val origin = RouteOptionsFactory.Coordinate(47.0, 8.0)
  private val destination = RouteOptionsFactory.Coordinate(47.3, 8.3)

  private fun wp(lat: Double, lon: Double, silent: Boolean) =
    RouteOptionsFactory.Waypoint(RouteOptionsFactory.Coordinate(lat, lon), silent)

  @Test
  fun ordersCoordinatesOriginThenWaypointsThenDestination() {
    val options = RouteOptionsFactory.build(
      origin = origin,
      destination = destination,
      waypoints = listOf(wp(47.1, 8.1, false), wp(47.2, 8.2, false)),
      locale = Locale.forLanguageTag("de-DE"),
    )
    // Points are (longitude, latitude).
    assertEquals(
      listOf(
        Point.fromLngLat(8.0, 47.0),
        Point.fromLngLat(8.1, 47.1),
        Point.fromLngLat(8.2, 47.2),
        Point.fromLngLat(8.3, 47.3),
      ),
      options.coordinatesList()
    )
  }

  @Test
  fun excludesSilentWaypointsFromLegIndices() {
    // wp #1 non-silent (index 1 stops), wp #2 silent (omitted).
    val options = RouteOptionsFactory.build(
      origin = origin,
      destination = destination,
      waypoints = listOf(wp(47.1, 8.1, false), wp(47.2, 8.2, true)),
      locale = Locale.forLanguageTag("de-DE"),
    )
    // origin=0, non-silent wp=1, silent wp omitted, destination=3.
    assertEquals(listOf(0, 1, 3), options.waypointIndicesList())
  }

  @Test
  fun includesAllWaypointsWhenNoneSilent() {
    val options = RouteOptionsFactory.build(
      origin = origin,
      destination = destination,
      waypoints = listOf(wp(47.1, 8.1, false), wp(47.2, 8.2, false)),
      locale = Locale.forLanguageTag("de-DE"),
    )
    assertEquals(listOf(0, 1, 2, 3), options.waypointIndicesList())
  }

  @Test
  fun handlesNoWaypoints() {
    val options = RouteOptionsFactory.build(
      origin = origin,
      destination = destination,
      waypoints = emptyList(),
      locale = Locale.forLanguageTag("de-DE"),
    )
    assertEquals(listOf(0, 1), options.waypointIndicesList())
    assertEquals(2, options.coordinatesList().size)
  }

  @Test
  fun metricLocaleSetsMetricVoiceUnitsAndBareLanguageCode() {
    val options = RouteOptionsFactory.build(
      origin = origin,
      destination = destination,
      waypoints = emptyList(),
      locale = Locale.forLanguageTag("de-DE"),
    )
    assertEquals(DirectionsCriteria.METRIC, options.voiceUnits())
    // Bare ISO code, not the full BCP-47 tag.
    assertEquals("de", options.language())
  }

  @Test
  fun imperialLocaleSetsImperialVoiceUnits() {
    val options = RouteOptionsFactory.build(
      origin = origin,
      destination = destination,
      waypoints = emptyList(),
      locale = Locale.forLanguageTag("en-US"),
    )
    assertEquals(DirectionsCriteria.IMPERIAL, options.voiceUnits())
    assertEquals("en", options.language())
  }

  @Test
  fun appliesDrivingTrafficProfileAndVoiceInstructions() {
    val options = RouteOptionsFactory.build(
      origin = origin,
      destination = destination,
      waypoints = emptyList(),
      locale = Locale.forLanguageTag("de-DE"),
    )
    assertEquals(DirectionsCriteria.PROFILE_DRIVING_TRAFFIC, options.profile())
    assertTrue(options.voiceInstructions() == true)
  }
}
