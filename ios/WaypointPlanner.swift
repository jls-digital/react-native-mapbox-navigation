import Foundation

// ── WaypointPlanner ───────────────────────────────────
// The ordering + leg-splitting half of route-waypoint construction, split out
// as a Foundation-only unit so it can be host-unit-tested WITHOUT MapboxDirections.
//
// `MapboxDirections` in this project is a product of the `mapbox-navigation-ios`
// SPM package, whose binary targets (MapboxNavigationNative / MapboxCommon) ship
// no macOS slice — so the real `MapboxDirections.Waypoint` type can't be built
// on the macOS test host. The god class's `buildWaypoints()` therefore asks this
// planner for neutral `PlannedWaypoint`s and only maps them onto
// `MapboxDirections.Waypoint` (coordinate + name + separatesLegs) at the very
// edge, where the mapping is trivial and compile-checked.
//
// Mirrors Android `RouteOptionsFactory` (silent waypoint → excluded from the
// leg-splitting indices == `separatesLegs = false`).

/// A waypoint resolved into the fields needed to build a `MapboxDirections.Waypoint`.
struct PlannedWaypoint: Equatable {
  let latitude: Double
  let longitude: Double
  /// Human-readable name ("Origin", "Waypoint N", "Destination").
  let name: String
  /// Whether this waypoint splits the route into a new leg. Silent intermediate
  /// waypoints are routed through but do NOT separate legs.
  let separatesLegs: Bool
}

enum WaypointPlanner {
  /// Plan the ordered waypoint list for an origin → waypoints → destination route.
  ///
  /// - Order is origin, each intermediate waypoint in order, then destination.
  /// - Origin and destination always separate legs.
  /// - An intermediate waypoint separates legs unless it is silent.
  static func plan(
    origin: (latitude: Double, longitude: Double),
    destination: (latitude: Double, longitude: Double),
    waypoints: [(coordinate: (latitude: Double, longitude: Double), isSilent: Bool)]
  ) -> [PlannedWaypoint] {
    var list: [PlannedWaypoint] = []
    list.append(
      PlannedWaypoint(
        latitude: origin.latitude, longitude: origin.longitude,
        name: "Origin", separatesLegs: true))
    for (index, wp) in waypoints.enumerated() {
      list.append(
        PlannedWaypoint(
          latitude: wp.coordinate.latitude, longitude: wp.coordinate.longitude,
          name: "Waypoint \(index + 1)", separatesLegs: !wp.isSilent))
    }
    list.append(
      PlannedWaypoint(
        latitude: destination.latitude, longitude: destination.longitude,
        name: "Destination", separatesLegs: true))
    return list
  }
}
