import MapboxNavigationCore
import MapboxNavigationUIKit
import UIKit

class HybridReactNativeMapboxNavigation: HybridReactNativeMapboxNavigationSpec {

  // UIView
  var view: UIView = UIView()

  // ── Props ────────────────────────────────────────────

  var origin: Coordinates = Coordinates(latitude: 0, longitude: 0)
  var destination: Coordinates = Coordinates(latitude: 0, longitude: 0)
  var waypoints: [Waypoint]?
  var language: String?
  var shouldSimulateRoute: Bool?
  var simulationSpeedMultiplier: Double?
  var mute: Bool?
  var colorScheme: String?
  var fontFamily: String?

  // ── Callbacks ────────────────────────────────────────

  var onArrive: ((_ destination: Coordinates) -> Void)?
  var onError: ((_ code: String, _ message: String) -> Void)?
  var onCancelNavigation: (() -> Void)?
  var onMuteChange: ((_ isMuted: Bool) -> Void)?
  var onRouteProgressChange: ((_ progress: RouteProgress) -> Void)?
  var onLocationChange: ((_ latitude: Double, _ longitude: Double) -> Void)?
  var onReroute: (() -> Void)?

  // ── Methods ──────────────────────────────────────────

  func recenterCamera() throws {
    // TODO: implement with Mapbox SDK
  }

  func showRouteOverview() throws {
    // TODO: implement with Mapbox SDK
  }
}
