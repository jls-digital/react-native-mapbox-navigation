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
  // MainActor-isolated init. The provider and the embedded nav VC are
  // created lazily on the first `afterUpdate()` tick.

  private var mapboxNavigationProvider: MapboxNavigationProvider?
  private var mapboxNavigation: MapboxNavigation?
  private var cancellables = Set<AnyCancellable>()
  private var currentRoutes: NavigationRoutes?
  private var hasScheduledSessionStart = false
  private var routeRequestTask: Task<Void, Never>?

  private let carrier = UIViewController()
  private var navigationViewController: NavigationViewController?

  deinit {
    routeRequestTask?.cancel()
    // Capture MainActor state before self is gone, then tear down on main.
    let nav = mapboxNavigation
    let carrierVC = carrier
    let navVC = navigationViewController
    Task { @MainActor in
      navVC?.willMove(toParent: nil)
      navVC?.view.removeFromSuperview()
      navVC?.removeFromParent()
      carrierVC.willMove(toParent: nil)
      carrierVC.view.removeFromSuperview()
      carrierVC.removeFromParent()
      nav?.tripSession().setToIdle()
    }
  }

  // ── Nitro view lifecycle ─────────────────────────────

  func beforeUpdate() {}

  func afterUpdate() {
    scheduleSessionStart()
  }

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

  // ── Setup ────────────────────────────────────────────

  @MainActor
  private func ensureMapboxNavigation() -> MapboxNavigation {
    if let existing = mapboxNavigation { return existing }
    let locationSource: LocationSource
    if shouldSimulateRoute == true {
      let initial = CLLocation(
        latitude: origin.latitude,
        longitude: origin.longitude
      )
      locationSource = .simulation(initialLocation: initial)
    } else {
      locationSource = .live
    }
    let coreConfig = CoreConfig(locationSource: locationSource)
    let provider = MapboxNavigationProvider(coreConfig: coreConfig)
    mapboxNavigationProvider = provider
    mapboxNavigation = provider.mapboxNavigation
    return provider.mapboxNavigation
  }

  nonisolated private func scheduleSessionStart() {
    Task { @MainActor [weak self] in
      guard let self, !self.hasScheduledSessionStart else { return }
      self.hasScheduledSessionStart = true
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
          NSLog("\(logTag) routes fetched — \(routes.alternativeRoutes.count) alternatives")
          self?.currentRoutes = routes
          self?.presentNavigationUI(routes: routes)
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

  // ── Navigation UI mount ──────────────────────────────

  @MainActor
  private func presentNavigationUI(routes: NavigationRoutes) {
    guard navigationViewController == nil,
          let provider = mapboxNavigationProvider else { return }

    // NOTE: simulation is wired at the `MapboxNavigationProvider` level via
    // `CoreConfig.locationSource = .simulation(...)` (see `ensureMapboxNavigation`).
    // `simulationSpeedMultiplier` is a JS API-parity placeholder — iOS v3 does
    // not expose a public speed multiplier for the built-in simulator. Android
    // uses `ReplayRouteOptions.maxSpeedMps`; we'll bridge an iOS equivalent
    // once the SDK exposes one.

    let navigationOptions = NavigationOptions(
      mapboxNavigation: provider.mapboxNavigation,
      voiceController: provider.routeVoiceController,
      eventsManager: provider.eventsManager(),
      styles: [StandardDayStyle(), StandardNightStyle()]
    )

    let navVC = NavigationViewController(
      navigationRoutes: routes,
      navigationOptions: navigationOptions
    )
    navVC.delegate = self
    navigationViewController = navVC

    embedCarrierIfNeeded()

    carrier.addChild(navVC)
    navVC.view.translatesAutoresizingMaskIntoConstraints = false
    carrier.view.addSubview(navVC.view)
    NSLayoutConstraint.activate([
      navVC.view.topAnchor.constraint(equalTo: carrier.view.topAnchor),
      navVC.view.bottomAnchor.constraint(equalTo: carrier.view.bottomAnchor),
      navVC.view.leadingAnchor.constraint(equalTo: carrier.view.leadingAnchor),
      navVC.view.trailingAnchor.constraint(equalTo: carrier.view.trailingAnchor),
    ])
    navVC.didMove(toParent: carrier)
    NSLog("\(logTag) NavigationViewController embedded")
  }

  @MainActor
  private func embedCarrierIfNeeded() {
    guard carrier.view.superview == nil else { return }
    carrier.view.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(carrier.view)
    NSLayoutConstraint.activate([
      carrier.view.topAnchor.constraint(equalTo: view.topAnchor),
      carrier.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
      carrier.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
      carrier.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
    ])
    // Attach carrier to the nearest UIViewController in the responder
    // chain so the NavigationViewController inherits a valid parent
    // (safe areas, status bar, presentation context).
    if carrier.parent == nil, let host = findHostViewController() {
      host.addChild(carrier)
      carrier.didMove(toParent: host)
    }
  }

  @MainActor
  private func findHostViewController() -> UIViewController? {
    var responder: UIResponder? = view.next
    while let r = responder {
      if let vc = r as? UIViewController, vc !== carrier {
        return vc
      }
      responder = r.next
    }
    return nil
  }
}

// ── NavigationViewControllerDelegate ──────────────────

extension HybridReactNativeMapboxNavigation: NavigationViewControllerDelegate {

  @MainActor
  func navigationViewControllerDidDismiss(
    _ navigationViewController: NavigationViewController,
    byCanceling canceled: Bool
  ) {
    NSLog("\(logTag) navigationViewControllerDidDismiss canceled=\(canceled)")
    if canceled {
      onCancelNavigation?()
    }
  }

  @MainActor
  func navigationViewController(
    _ navigationViewController: NavigationViewController,
    didUpdate progress: MapboxNavigationCore.RouteProgress,
    with location: CLLocation,
    rawLocation: CLLocation
  ) {
    onRouteProgressChange?(RouteProgress(
      distanceTraveled: progress.distanceTraveled,
      distanceRemaining: progress.distanceRemaining,
      durationRemaining: progress.durationRemaining,
      fractionTraveled: progress.fractionTraveled
    ))
    onLocationChange?(location.coordinate.latitude, location.coordinate.longitude)
  }

  @MainActor
  func navigationViewController(
    _ navigationViewController: NavigationViewController,
    didArriveAt waypoint: MapboxDirections.Waypoint
  ) {
    let coord = waypoint.coordinate
    let isFinal = isFinalDestination(coord)
    NSLog("\(logTag) didArriveAt \(coord.latitude),\(coord.longitude) final=\(isFinal)")
    if isFinal {
      onArrive?(Coordinates(latitude: coord.latitude, longitude: coord.longitude))
    }
  }

  private func isFinalDestination(_ coord: CLLocationCoordinate2D) -> Bool {
    let epsilon = 1e-6
    return abs(coord.latitude - destination.latitude) < epsilon &&
           abs(coord.longitude - destination.longitude) < epsilon
  }
}

// ── Helpers ────────────────────────────────────────────

private extension Coordinates {
  var coreLocation: CLLocationCoordinate2D {
    CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
  }
}
