package com.margelo.nitro.ch.jls.reactnative.mapboxnavigation

import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.formatter.UnitType
import java.util.Locale

// ── RouteOptionsFactory ───────────────────────────────
// Builds the Mapbox Directions `RouteOptions` for a route request, extracted
// from `HybridReactNativeMapboxNavigation.buildRouteOptions` so the
// coordinate-ordering, silent-waypoint and language/voice-unit logic is
// unit-testable. Unlike the framework-free pure units (RouteErrorClassifier
// etc.), this is a Layer-2 SDK-integration seam: it builds against the real
// mapbox-java value types (`RouteOptions`, `Point`, `DirectionsCriteria`),
// which are plain JVM classes, so JUnit can assert the built options off-device
// — no emulator, no Robolectric.
//
// Mirrors iOS `buildWaypoints` + `NavigationRouteOptions(waypoints:)`: same
// origin → waypoints → destination order, and silent waypoints excluded from
// the leg-splitting indices (iOS sets `Waypoint.separatesLegs = false`).
object RouteOptionsFactory {

  /** A geographic coordinate (latitude/longitude in decimal degrees). */
  data class Coordinate(val latitude: Double, val longitude: Double)

  /**
   * An intermediate waypoint. A silent waypoint is routed through but does not
   * split the route into a new leg (no arrival event / no stop), matching iOS
   * `Waypoint.separatesLegs = false`.
   */
  data class Waypoint(val coordinate: Coordinate, val isSilent: Boolean)

  /**
   * Build the `RouteOptions` for an origin → waypoints → destination route.
   *
   * - Coordinates are ordered origin, then each waypoint in order, then
   *   destination.
   * - `waypointIndicesList` contains the origin (0), the destination (last),
   *   and every NON-silent waypoint's index — silent waypoints are omitted so
   *   they don't split the route into legs.
   * - Language + voice units come from [locale] (NOT the device locale), so the
   *   spoken units agree with the on-screen distance formatter. The bare ISO
   *   language code is used (e.g. "en"), matching what the SDK would infer.
   */
  fun build(
    origin: Coordinate,
    destination: Coordinate,
    waypoints: List<Waypoint>,
    locale: Locale,
  ): RouteOptions {
    val points = mutableListOf<Point>()
    points.add(Point.fromLngLat(origin.longitude, origin.latitude))
    waypoints.forEach { wp ->
      points.add(Point.fromLngLat(wp.coordinate.longitude, wp.coordinate.latitude))
    }
    points.add(Point.fromLngLat(destination.longitude, destination.latitude))

    val stopIndices = mutableListOf(0)
    waypoints.forEachIndexed { i, wp ->
      if (!wp.isSilent) stopIndices.add(i + 1)
    }
    stopIndices.add(points.size - 1)

    val voiceUnits = when (LocaleUnits.unitTypeFor(locale)) {
      UnitType.IMPERIAL -> DirectionsCriteria.IMPERIAL
      UnitType.METRIC -> DirectionsCriteria.METRIC
    }

    return RouteOptions.builder()
      .applyDefaultNavigationOptions()
      .language(locale.language)
      .voiceInstructions(true)
      .voiceUnits(voiceUnits)
      .coordinatesList(points)
      .waypointIndicesList(stopIndices)
      .build()
  }
}
