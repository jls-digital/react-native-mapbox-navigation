import Combine
import CoreLocation
import MapboxDirections
import MapboxNavigationCore
import MapboxNavigationUIKit
import UIKit

private let logTag = "[RNMapboxNav]"

// ── MapboxProviderStore ────────────────────────────────
// Process-wide cache for `MapboxNavigationProvider`. Mapbox's provider
// is a deliberate singleton (guarded by `checkInstanceIsUnique`) and its
// internal threadpool owns StyleManager — releasing it in the same
// runloop turn as `setToIdle()` races with in-flight draw work and
// segfaults. Keeping it alive across mounts sidesteps the race entirely:
// unmount becomes "cancel subscriptions + detach nav VC + setToIdle()",
// with no provider release at all.
//
// `CoreConfig` (locale + locationSource) is baked in at init — so if the
// consumer toggles language or shouldSimulateRoute, we drop the cached
// provider and rebuild. That reintroduces the release race exactly once
// per config swap, which is rare and gated by setToIdle().
@MainActor
final class MapboxProviderStore {
  static let shared = MapboxProviderStore()
  private var provider: MapboxNavigationProvider?
  private var currentConfigKey: String?

  private init() {}

  func acquire(configKey: String, coreConfig: CoreConfig) -> MapboxNavigationProvider {
    if let existing = provider, currentConfigKey == configKey {
      return existing
    }
    if let old = provider {
      NSLog("\(logTag) MapboxProviderStore: config changed (\(currentConfigKey ?? "nil") → \(configKey)) — rebuilding")
      old.mapboxNavigation.tripSession().setToIdle()
    }
    let new = MapboxNavigationProvider(coreConfig: coreConfig)
    provider = new
    currentConfigKey = configKey
    return new
  }
}

class HybridReactNativeMapboxNavigation: HybridReactNativeMapboxNavigationSpec {

  // UIView — a lifecycle-aware subclass so we can detach Mapbox UI the
  // moment RN removes us from the view tree, regardless of why (cancel,
  // arrival followed by goBack, conditional render, parent unmount).
  // Nitro's HybridView protocol only exposes beforeUpdate / afterUpdate —
  // neither fires on unmount — so we piggy-back on the UIView lifecycle.
  var view: UIView = LifecycleView()

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

  // De-dup + post-arrival gate for location/progress emissions.
  private var hasArrivedAtDestination = false
  private var lastReportedCoordinate: CLLocationCoordinate2D?
  private static let locationChangedEpsilonMeters: CLLocationDistance = 0.5

  // Track mute so we can detect taps on the SDK's built-in mute ornament.
  // The SpeechSynthesizing protocol exposes `muted` as a plain get/set
  // (no publisher), so we observe UserDefaults where the multiplexed
  // synthesizer persists it under a well-known key.
  private var lastKnownMuted: Bool = false
  private var userDefaultsObserver: NSObjectProtocol?
  private static let multiplexedMutedKey =
    "com.mapbox.navigation.MultiplexedSpeechSynthesizer.isMuted"

  // Flipped once the nav VC dismisses (cancel OR arrival) so late events
  // from the SDK — e.g. an arrival tick that arrives after the user tapped
  // cancel — are dropped instead of triggering onArrive on a gone session.
  private var isShuttingDown = false

  deinit {
    routeRequestTask?.cancel()
    if let obs = userDefaultsObserver {
      NotificationCenter.default.removeObserver(obs)
    }
    // Provider lives in MapboxProviderStore across mounts; nothing to
    // release here. If UI detach didn't already happen via LifecycleView,
    // it's too late to touch MainActor-only state from deinit anyway.
  }

  // Detach this instance from the shared Mapbox provider: drop
  // subscriptions, dismiss the nav VC, flip the trip session to idle.
  // The provider itself stays alive in MapboxProviderStore so the next
  // mount reuses it without tripping `checkInstanceIsUnique` and without
  // racing Mapbox's internal threadpool during release.
  //
  // Called from:
  //  - `LifecycleView.onWillDetach` when RN removes our view from the
  //    tree (covers arrival→goBack, cancel→goBack, conditional render,
  //    parent screen unmount — i.e. every JS-initiated unmount).
  //  - `navigationViewControllerDidDismiss` when the SDK's own cancel
  //    UI fires, so the arrival sink stops before any late tick.
  @MainActor
  private func detachNavigationUI() {
    guard !isShuttingDown else { return }
    isShuttingDown = true
    NSLog("\(logTag) detachNavigationUI()")
    cancellables.removeAll()
    routeRequestTask?.cancel()
    routeRequestTask = nil
    if let navVC = navigationViewController {
      navVC.willMove(toParent: nil)
      navVC.view.removeFromSuperview()
      navVC.removeFromParent()
    }
    navigationViewController = nil
    if carrier.parent != nil {
      carrier.willMove(toParent: nil)
      carrier.view.removeFromSuperview()
      carrier.removeFromParent()
    }
    mapboxNavigation?.tripSession().setToIdle()
    mapboxNavigationProvider = nil
    mapboxNavigation = nil
    if let obs = userDefaultsObserver {
      NotificationCenter.default.removeObserver(obs)
      userDefaultsObserver = nil
    }
  }

  // ── Nitro view lifecycle ─────────────────────────────

  func beforeUpdate() {}

  func afterUpdate() {
    wireLifecycleHooks()
    scheduleSessionStart()
  }

  nonisolated private func wireLifecycleHooks() {
    Task { @MainActor [weak self] in
      guard let self, let lv = self.view as? LifecycleView,
            lv.onWillDetach == nil else { return }
      lv.onWillDetach = { [weak self] in
        // RN has detached our view from the window — drop our UI hold on
        // the shared provider. Safe to fire on transient detaches too;
        // worst case we'd kill a live nav session, which LifecycleView's
        // debounce guards against.
        self?.detachNavigationUI()
      }
    }
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
    didSet {
      NSLog("\(logTag) mute=\(mute?.description ?? "<nil>")")
      guard mute != oldValue, let newValue = mute else { return }
      Task { @MainActor [weak self] in
        self?.applyMute(newValue)
      }
    }
  }
  var colorScheme: String? {
    didSet {
      NSLog("\(logTag) colorScheme=\(colorScheme ?? "<nil>")")
      guard colorScheme != oldValue else { return }
      Task { @MainActor [weak self] in
        self?.applyColorScheme()
      }
    }
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
    Task { @MainActor [weak self] in
      self?.navigationViewController?.navigationMapView?.navigationCamera
        .update(cameraState: .following)
    }
  }

  func showRouteOverview() throws {
    NSLog("\(logTag) showRouteOverview() invoked")
    Task { @MainActor [weak self] in
      self?.navigationViewController?.navigationMapView?.navigationCamera
        .update(cameraState: .overview)
    }
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
    let locale: Locale
    if let code = language, !code.isEmpty {
      locale = Locale(identifier: code)
    } else {
      locale = .nationalizedCurrent
    }
    let coreConfig = CoreConfig(
      locationSource: locationSource,
      locale: locale
    )
    let configKey = "sim=\(shouldSimulateRoute == true)|locale=\(locale.identifier)"
    let provider = MapboxProviderStore.shared.acquire(
      configKey: configKey,
      coreConfig: coreConfig
    )
    mapboxNavigationProvider = provider
    mapboxNavigation = provider.mapboxNavigation
    // Seed initial mute state. Runtime changes come through the `mute`
    // prop setter or through the SDK's built-in mute ornament (observed
    // via UserDefaults, see `startObservingNativeMute`).
    let initialMuted = (mute == true)
    provider.routeVoiceController.speechSynthesizer.muted = initialMuted
    lastKnownMuted = initialMuted
    startObservingNativeMute()
    return provider.mapboxNavigation
  }

  @MainActor
  private func applyMute(_ newValue: Bool) {
    guard let provider = mapboxNavigationProvider else { return }
    provider.routeVoiceController.speechSynthesizer.muted = newValue
    // Update last-known BEFORE firing so the UserDefaults notification
    // that follows this write is a no-op (prevents double-emit).
    guard lastKnownMuted != newValue else { return }
    lastKnownMuted = newValue
    onMuteChange?(newValue)
  }

  @MainActor
  private func startObservingNativeMute() {
    guard userDefaultsObserver == nil else { return }
    userDefaultsObserver = NotificationCenter.default.addObserver(
      forName: UserDefaults.didChangeNotification,
      object: nil,
      queue: .main
    ) { [weak self] _ in
      // Always trampoline to MainActor; the notification may arrive on a
      // background queue depending on writer.
      Task { @MainActor in
        self?.syncNativeMuteIfChanged()
      }
    }
  }

  @MainActor
  private func syncNativeMuteIfChanged() {
    guard let provider = mapboxNavigationProvider else { return }
    let current = provider.routeVoiceController.speechSynthesizer.muted
    guard current != lastKnownMuted else { return }
    NSLog("\(logTag) native mute toggle detected → \(current)")
    lastKnownMuted = current
    onMuteChange?(current)
  }

  @MainActor
  private func applyColorScheme() {
    guard let navVC = navigationViewController else { return }
    switch colorScheme {
    case "light":
      navVC.styleManager.applyStyle(type: .day)
    case "dark":
      navVC.styleManager.applyStyle(type: .night)
    default:
      // "auto" / nil — StyleManager switches on sun position.
      break
    }
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
    guard ensureCoordinatesValid() else { return }
    guard ensureLocationAvailable() else { return }
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
        "Location permission not granted. The app needs location permission to navigate."
      )
      return false
    case .authorizedAlways, .authorizedWhenInUse:
      return true
    @unknown default:
      return false
    }
  }

  @MainActor
  private func ensureLocationAvailable() -> Bool {
    if CLLocationManager.locationServicesEnabled() {
      return true
    }
    NSLog("\(logTag) location services disabled")
    onError?(
      "GPS_UNAVAILABLE",
      "Location services are disabled on this device. Enable Location Services in Settings."
    )
    return false
  }

  @MainActor
  private func ensureCoordinatesValid() -> Bool {
    func isValid(_ c: Coordinates) -> Bool {
      c.latitude >= -90 && c.latitude <= 90 &&
      c.longitude >= -180 && c.longitude <= 180 &&
      !(c.latitude == 0 && c.longitude == 0)
    }
    if !isValid(origin) || !isValid(destination) {
      onError?(
        "INVALID_COORDINATES",
        "Origin or destination is outside the valid lat/lon range or is the default (0, 0)."
      )
      return false
    }
    for (i, wp) in (waypoints ?? []).enumerated() where !isValid(wp.coordinate) {
      onError?(
        "INVALID_COORDINATES",
        "Waypoint #\(i + 1) coordinate is invalid."
      )
      return false
    }
    return true
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
    let message = error.localizedDescription
    let code = classifyRouteError(error)
    NSLog("\(logTag) route error code=\(code) message=\(message)")
    onError?(code, message)
  }

  private func classifyRouteError(_ error: Error) -> String {
    if let directionsError = error as? DirectionsError {
      switch directionsError {
      case .network:
        return "NETWORK_ERROR"
      case .unknown(let response, _, _, _):
        if let http = response as? HTTPURLResponse, http.statusCode == 401 || http.statusCode == 403 {
          return "SDK_INIT_FAILED"
        }
        return "ROUTE_CALCULATION_FAILED"
      default:
        return "ROUTE_CALCULATION_FAILED"
      }
    }
    if error is URLError {
      return "NETWORK_ERROR"
    }
    return "ROUTE_CALCULATION_FAILED"
  }

  // ── Navigation UI mount ──────────────────────────────

  @MainActor
  private func presentNavigationUI(routes: NavigationRoutes) {
    guard navigationViewController == nil,
          let provider = mapboxNavigationProvider else { return }

    subscribeToArrival(navigation: provider.mapboxNavigation.navigation())

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
      styles: [FontDayStyle(fontFamily: fontFamily), FontNightStyle(fontFamily: fontFamily)]
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
    applyColorScheme()
  }

  @MainActor
  private func subscribeToArrival(navigation: NavigationController) {
    navigation.waypointsArrival
      .sink { [weak self] status in
        guard let self, !self.isShuttingDown else { return }
        if status.event is WaypointArrivalStatus.Events.ToFinalDestination {
          NSLog("\(logTag) waypointsArrival → ToFinalDestination")
          // Emit one final progress tick saturated to 100% before
          // the arrive callback. Mapbox's `fractionTraveled` reflects
          // polyline-fraction and fires arrival within a radius, so
          // the last natural tick is typically below 1.0.
          let totalDistance = self.currentRoutes?.mainRoute.route.distance ?? 0
          self.onRouteProgressChange?(RouteProgress(
            distanceTraveled: totalDistance,
            distanceRemaining: 0,
            durationRemaining: 0,
            fractionTraveled: 1.0
          ))
          self.hasArrivedAtDestination = true
          self.onArrive?(Coordinates(
            latitude: self.destination.latitude,
            longitude: self.destination.longitude
          ))
        }
      }
      .store(in: &cancellables)
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
    // Detach synchronously regardless of arrival vs cancel so the late
    // arrival sink is gated (isShuttingDown) before any trailing tick
    // and the SDK cancel path matches the JS-unmount path exactly.
    detachNavigationUI()
  }

  @MainActor
  func navigationViewController(
    _ navigationViewController: NavigationViewController,
    didUpdate progress: MapboxNavigationCore.RouteProgress,
    with location: CLLocation,
    rawLocation: CLLocation
  ) {
    // Once the user has reached the final destination we stop emitting
    // progress/location; the SDK keeps ticking but the caller already
    // got onArrive.
    guard !hasArrivedAtDestination else { return }

    // De-duplicate: only emit when the map-matched location actually
    // moved. This prevents a flood of identical updates when the user
    // (or simulator) is stationary and matches SPEC T10's intent that
    // observers fire on movement, not on a clock.
    let coord = location.coordinate
    if let last = lastReportedCoordinate,
       distanceBetween(last, coord) < Self.locationChangedEpsilonMeters {
      return
    }
    lastReportedCoordinate = coord

    onRouteProgressChange?(RouteProgress(
      distanceTraveled: progress.distanceTraveled,
      distanceRemaining: progress.distanceRemaining,
      durationRemaining: progress.durationRemaining,
      fractionTraveled: progress.fractionTraveled
    ))
    onLocationChange?(coord.latitude, coord.longitude)
  }

  @MainActor
  func navigationViewController(
    _ navigationViewController: NavigationViewController,
    didRerouteAlong route: Route
  ) {
    NSLog("\(logTag) didRerouteAlong route distance=\(route.distance)m")
    onReroute?()
  }

  private func distanceBetween(
    _ a: CLLocationCoordinate2D,
    _ b: CLLocationCoordinate2D
  ) -> CLLocationDistance {
    CLLocation(latitude: a.latitude, longitude: a.longitude)
      .distance(from: CLLocation(latitude: b.latitude, longitude: b.longitude))
  }
}

// ── Font-aware styles ─────────────────────────────────
// Subclass the standard styles to set scoped UIAppearance font
// overrides. `whenContainedInInstancesOf: [NavigationViewController.self]`
// confines the override to our navigation view — the host app's other
// UI is untouched. See SPEC B14.

final class FontDayStyle: StandardDayStyle {
  private var customFontFamily: String?

  convenience init(fontFamily: String?) {
    self.init()
    self.customFontFamily = fontFamily
  }

  required init() {
    super.init()
  }

  override func apply() {
    super.apply()
    applyFontOverride(customFontFamily)
  }
}

final class FontNightStyle: StandardNightStyle {
  private var customFontFamily: String?

  convenience init(fontFamily: String?) {
    self.init()
    self.customFontFamily = fontFamily
  }

  required init() {
    super.init()
  }

  override func apply() {
    super.apply()
    applyFontOverride(customFontFamily)
  }
}

private func applyFontOverride(_ fontFamily: String?) {
  guard let name = fontFamily,
        !name.isEmpty,
        let base = UIFont(name: name, size: UIFont.systemFontSize)
  else { return }
  StylableLabel.appearance(whenContainedInInstancesOf: [NavigationViewController.self])
    .normalFont = base
}

// ── Helpers ────────────────────────────────────────────

private extension Coordinates {
  var coreLocation: CLLocationCoordinate2D {
    CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
  }
}

// ── Lifecycle-aware host view ──────────────────────────
// Fires `onWillDetach` once the view has been detached from the window
// for real — not during transient layout or stack-transition churn.
//
// Why `didMoveToWindow` + a runloop-deferred debounce: `react-native-
// screens` transiently detaches/re-attaches the screen's containers
// during push/pop animations. A spurious detach would dismiss live nav
// UI (the provider itself lives in `MapboxProviderStore` so it's
// unaffected, but the user would lose their session). The async defer
// lets the hierarchy re-attach before we commit.
final class LifecycleView: UIView {
  var onWillDetach: (() -> Void)?
  private var hasBeenAttached = false
  private var teardownPending = false

  override func didMoveToWindow() {
    super.didMoveToWindow()
    if window != nil {
      hasBeenAttached = true
      // A pending teardown is no longer valid — we're back in a window.
      teardownPending = false
      return
    }
    guard hasBeenAttached, !teardownPending else { return }
    teardownPending = true
    DispatchQueue.main.async { [weak self] in
      guard let self, self.teardownPending, self.window == nil else { return }
      self.teardownPending = false
      self.onWillDetach?()
    }
  }
}
