import MapboxNavigationCore
import MapboxNavigationUIKit
import UIKit

private let logTag = "[RNMapboxNav]"

class HybridReactNativeMapboxNavigation: HybridReactNativeMapboxNavigationSpec {

  // UIView
  var view: UIView = UIView()

  // ── Props ────────────────────────────────────────────

  var origin: Coordinates = Coordinates(latitude: 0, longitude: 0) {
    didSet { NSLog("\(logTag) origin=\(origin.latitude),\(origin.longitude)") }
  }
  var destination: Coordinates = Coordinates(latitude: 0, longitude: 0) {
    didSet { NSLog("\(logTag) destination=\(destination.latitude),\(destination.longitude)") }
  }
  var waypoints: [Waypoint]? {
    didSet { NSLog("\(logTag) waypoints count=\(waypoints?.count ?? 0)") }
  }
  var language: String? {
    didSet { NSLog("\(logTag) language=\(language ?? "<nil>")") }
  }
  var shouldSimulateRoute: Bool? {
    didSet { NSLog("\(logTag) shouldSimulateRoute=\(shouldSimulateRoute?.description ?? "<nil>")") }
  }
  var simulationSpeedMultiplier: Double? {
    didSet { NSLog("\(logTag) simulationSpeedMultiplier=\(simulationSpeedMultiplier?.description ?? "<nil>")") }
  }
  var mute: Bool? {
    didSet { NSLog("\(logTag) mute=\(mute?.description ?? "<nil>")") }
  }
  var colorScheme: String? {
    didSet { NSLog("\(logTag) colorScheme=\(colorScheme ?? "<nil>")") }
  }
  var fontFamily: String? {
    didSet { NSLog("\(logTag) fontFamily=\(fontFamily ?? "<nil>")") }
  }

  // ── Callbacks ────────────────────────────────────────

  var onArrive: ((_ destination: Coordinates) -> Void)? {
    didSet { NSLog("\(logTag) onArrive assigned=\(onArrive != nil)") }
  }
  var onError: ((_ code: String, _ message: String) -> Void)? {
    didSet { NSLog("\(logTag) onError assigned=\(onError != nil)") }
  }
  var onCancelNavigation: (() -> Void)? {
    didSet { NSLog("\(logTag) onCancelNavigation assigned=\(onCancelNavigation != nil)") }
  }
  var onMuteChange: ((_ isMuted: Bool) -> Void)? {
    didSet { NSLog("\(logTag) onMuteChange assigned=\(onMuteChange != nil)") }
  }
  var onRouteProgressChange: ((_ progress: RouteProgress) -> Void)? {
    didSet { NSLog("\(logTag) onRouteProgressChange assigned=\(onRouteProgressChange != nil)") }
  }
  var onLocationChange: ((_ latitude: Double, _ longitude: Double) -> Void)? {
    didSet { NSLog("\(logTag) onLocationChange assigned=\(onLocationChange != nil)") }
  }
  var onReroute: (() -> Void)? {
    didSet { NSLog("\(logTag) onReroute assigned=\(onReroute != nil)") }
  }

  // ── Methods ──────────────────────────────────────────

  func recenterCamera() throws {
    NSLog("\(logTag) recenterCamera() invoked")
  }

  func showRouteOverview() throws {
    NSLog("\(logTag) showRouteOverview() invoked")
  }
}
