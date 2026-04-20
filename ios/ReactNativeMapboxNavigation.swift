import Combine
import CoreLocation
import MapboxDirections
import MapboxNavigationCore
import MapboxNavigationUIKit
import UIKit

private let logTag = "[RNMapboxNav]"

class HybridReactNativeMapboxNavigation: HybridReactNativeMapboxNavigationSpec {

  // UIView
  var view: UIView = UIView()

  // ── Mapbox session ───────────────────────────────────
  // Nitro's base init() is non-isolated, so we can't override with a
  // MainActor-isolated init. The Mapbox provider is created lazily on
  // first prop-driven schedule tick, which runs via `MainActor.run`.

  private var mapboxNavigationProvider: MapboxNavigationProvider?
  private var mapboxNavigation: MapboxNavigation?
  private var cancellables = Set<AnyCancellable>()
  private var currentRoutes: NavigationRoutes?
  private var hasScheduledSessionStart = false
  private var routeRequestTask: Task<Void, Never>?

  deinit {
    routeRequestTask?.cancel()
    guard let nav = mapboxNavigation else { return }
    Task { @MainActor in
      nav.tripSession().setToIdle()
    }
  }

  @MainActor
  private func ensureMapboxNavigation() -> MapboxNavigation {
    if let existing = mapboxNavigation { return existing }
    let coreConfig = CoreConfig()
    let provider = MapboxNavigationProvider(coreConfig: coreConfig)
    mapboxNavigationProvider = provider
    mapboxNavigation = provider.mapboxNavigation
    return provider.mapboxNavigation
  }

  // ── Props ────────────────────────────────────────────

  var origin: Coordinates = Coordinates(latitude: 0, longitude: 0) {
    didSet {
      NSLog("\(logTag) origin=\(origin.latitude),\(origin.longitude)")
      scheduleSessionStart()
    }
  }
  var destination: Coordinates = Coordinates(latitude: 0, longitude: 0) {
    didSet {
      NSLog("\(logTag) destination=\(destination.latitude),\(destination.longitude)")
      scheduleSessionStart()
    }
  }
  var waypoints: [Waypoint]? {
    didSet {
      NSLog("\(logTag) waypoints count=\(waypoints?.count ?? 0)")
      scheduleSessionStart()
    }
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

  // ── Route calculation ────────────────────────────────

  nonisolated private func scheduleSessionStart() {
    Task { @MainActor [weak self] in
      guard let self, !self.hasScheduledSessionStart else { return }
      self.hasScheduledSessionStart = true
      // Let the rest of the prop setters run before we actually kick off.
      await Task.yield()
      self.startSessionIfReady()
    }
  }

  @MainActor
  private func startSessionIfReady() {
    guard ensureLocationPermission() else { return }
    let nav = ensureMapboxNavigation()
    let waypointList = buildWaypoints()
    NSLog("\(logTag) requesting route with \(waypointList.count) waypoints")

    let options = NavigationRouteOptions(waypoints: waypointList)
    let request = nav.routingProvider().calculateRoutes(options: options)

    routeRequestTask = Task { [weak self] in
      let result = await request.result
      await MainActor.run {
        switch result {
        case .success(let routes):
          self?.currentRoutes = routes
          NSLog("\(logTag) routes fetched — \(routes.alternativeRoutes.count) alternatives")
        case .failure(let error):
          self?.emitRouteError(error)
        }
      }
    }
  }

  @MainActor
  private func ensureLocationPermission() -> Bool {
    let manager = CLLocationManager()
    switch manager.authorizationStatus {
    case .notDetermined, .restricted, .denied:
      NSLog("\(logTag) location permission not granted")
      onError?(
        "GPS_PERMISSION_DENIED",
        "Location permission not granted. The example app needs location permission to navigate."
      )
      return false
    case .authorizedAlways, .authorizedWhenInUse:
      return true
    @unknown default:
      return false
    }
  }

  private func buildWaypoints() -> [MapboxDirections.Waypoint] {
    var list: [MapboxDirections.Waypoint] = []
    list.append(MapboxDirections.Waypoint(coordinate: origin.coreLocation, name: "Origin"))
    for (index, wp) in (waypoints ?? []).enumerated() {
      var mwp = MapboxDirections.Waypoint(
        coordinate: wp.coordinate.coreLocation,
        name: "Waypoint \(index + 1)"
      )
      mwp.separatesLegs = !(wp.isSilent ?? false)
      list.append(mwp)
    }
    list.append(MapboxDirections.Waypoint(coordinate: destination.coreLocation, name: "Destination"))
    return list
  }

  private func emitRouteError(_ error: Error) {
    let code: String
    if error is URLError {
      code = "NETWORK_ERROR"
    } else {
      code = "ROUTE_CALCULATION_FAILED"
    }
    NSLog("\(logTag) route error code=\(code) message=\(error.localizedDescription)")
    onError?(code, error.localizedDescription)
  }
}

// ── Helpers ────────────────────────────────────────────

private extension Coordinates {
  var coreLocation: CLLocationCoordinate2D {
    CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
  }
}
