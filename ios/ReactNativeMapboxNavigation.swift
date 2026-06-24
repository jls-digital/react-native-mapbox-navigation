import Combine
import CoreLocation
import MapboxDirections
import MapboxNavigationCore
import MapboxNavigationUIKit
import UIKit

private let logTag = "[RNMapboxNav]"

// `MapboxProviderStore` (the process-wide refcounted provider cache) moved
// to `MapboxProviderStore.swift`. Its pure refcount/config bookkeeping lives
// in `ProviderStoreState` there so it can be unit-tested without the SDK.

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
  // The config key under which we acquired the shared provider. Held so
  // teardown releases the exact hold we took (refcount balance).
  private var acquiredConfigKey: String?
  private var cancellables = Set<AnyCancellable>()
  private var currentRoutes: NavigationRoutes?
  private var hasScheduledSessionStart = false
  // Last precondition error code emitted this mount. A retryable precondition
  // (permission/GPS) is re-checked on every afterUpdate, so de-dupe by code to
  // avoid spamming onError with the same failure. Reset when a session starts.
  private var lastPreconditionErrorCode: String?
  private var routeRequestTask: Task<Void, Never>?

  private let carrier = UIViewController()
  // The exact host VC the carrier was added as a child of. Tracked so
  // teardown removes the carrier from the SAME parent it was attached to,
  // keeping addChild/removeFromParent balanced even across a host swap.
  private weak var carrierHost: UIViewController?
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
  // Desired mute value. Set by the `mute` prop setter; applied immediately
  // if the provider already exists, otherwise re-applied once the provider
  // is created in `ensureMapboxNavigation`. This prevents a runtime mute
  // change arriving before the provider exists from being dropped.
  private var pendingMute: Bool?
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
    // Remove the carrier from whatever VC it is actually a child of, so
    // addChild/removeFromParent stay balanced even if the host changed
    // since we attached. We tracked `carrierHost` at attach time; assert
    // (via the parent check) that it still matches before removing.
    if carrier.parent != nil {
      carrier.willMove(toParent: nil)
      carrier.view.removeFromSuperview()
      carrier.removeFromParent()
    }
    carrierHost = nil
    // Release our hold on the shared provider. The store idles the trip
    // session only when the LAST holder releases — so we never idle a
    // still-mounted sibling's live session (refcounting, see store).
    if let key = acquiredConfigKey {
      MapboxProviderStore.shared.release(configKey: key)
      acquiredConfigKey = nil
    }
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
  var onNavigationEnd: (() -> Void)? {
    didSet { NSLog("\(logTag) onNavigationEnd assigned=\(onNavigationEnd != nil)") }
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
    let locale = LocaleUnits.resolveLocale(language: language)
    // Disable proactive (faster-route) rerouting to preserve the
    // fixed-route invariant: the route must only change when the user
    // deviates, never spontaneously mid-trip because a faster path
    // appeared. This is the v3 equivalent of v1's
    // `navigationService.router.reroutesProactively = false`. Deviation-
    // based rerouting stays enabled via the default `rerouteConfig`;
    // setting `fasterRouteDetectionConfig` to nil disables only the
    // faster-route mechanism. Matches the Android side (which likewise
    // does not enable faster-route detection).
    let routingConfig = RoutingConfig(fasterRouteDetectionConfig: nil)
    let coreConfig = CoreConfig(
      routingConfig: routingConfig,
      locationSource: locationSource,
      locale: locale
    )
    // The config key distinguishes the inputs that REQUIRE a different
    // provider: locale and simulation mode. It must stay STABLE across
    // remounts of the same logical config so open→close→open reuses the
    // cached provider rather than rebuilding it (rebuilding a checked
    // singleton while the old one is still alive trips
    // `checkInstanceIsUnique()`).
    //
    // Origin is intentionally NOT part of the key. For a simulated route the
    // origin seeds the simulation's `initialLocation`, but once the route is
    // set and active guidance starts the simulator replays the new route —
    // the seed only affects the puck position in the brief pre-route window,
    // which isn't worth forcing a provider rebuild (and the crash risk that
    // comes with it) on every new-origin reopen.
    let isSimulated = shouldSimulateRoute == true
    let configKey = ProviderConfigKey.providerConfigKey(
      simulated: isSimulated,
      locale: locale
    )
    let provider = MapboxProviderStore.shared.acquire(
      configKey: configKey,
      coreConfig: coreConfig
    )
    acquiredConfigKey = configKey
    mapboxNavigationProvider = provider
    mapboxNavigation = provider.mapboxNavigation
    // Seed initial mute state. Prefer any pending value from a `mute` prop
    // change that arrived before the provider existed; otherwise fall back
    // to the current `mute` prop. Runtime changes after this come through
    // the `mute` prop setter or the SDK's built-in mute ornament (observed
    // via UserDefaults, see `startObservingNativeMute`).
    let initialMuted = pendingMute ?? (mute == true)
    pendingMute = nil
    provider.routeVoiceController.speechSynthesizer.muted = initialMuted
    lastKnownMuted = initialMuted
    startObservingNativeMute()
    return provider.mapboxNavigation
  }

  @MainActor
  private func applyMute(_ newValue: Bool) {
    guard let provider = mapboxNavigationProvider else {
      // Provider not created yet — remember the desired value so
      // `ensureMapboxNavigation` seeds it once the provider exists.
      // Without this, a mute change arriving before first route fetch is
      // silently lost.
      pendingMute = newValue
      return
    }
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
    // The SDK's MultiplexedSpeechSynthesizer persists `muted` to
    // `UserDefaults.standard` (key `multiplexedMutedKey`). Scope the
    // observation to that exact instance via `object:` so writes to OTHER
    // UserDefaults suites (named suites used by analytics SDKs, etc.) do
    // not fire this observer at all. `didChangeNotification` carries no
    // changed-key payload, so `syncNativeMuteIfChanged` still guards
    // against unrelated standard-suite writes by comparing the actual
    // muted value before emitting — but narrowing the suite removes the
    // bulk of spurious wakeups during navigation.
    userDefaultsObserver = NotificationCenter.default.addObserver(
      forName: UserDefaults.didChangeNotification,
      object: UserDefaults.standard,
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
      // Pin to day. Forcing a fixed style implicitly takes over from the
      // automatic time-of-day switching.
      navVC.automaticallyAdjustsStyleForTimeOfDay = false
      navVC.styleManager.applyStyle(type: .day)
    case "dark":
      navVC.automaticallyAdjustsStyleForTimeOfDay = false
      navVC.styleManager.applyStyle(type: .night)
    default:
      // "auto" / nil — restore the SDK's automatic day/night switching.
      // `applyStyle(type:)` above pins `currentStyleType`, and the SDK
      // exposes no public call to re-run the automatic evaluation, so:
      //   1. re-enable the auto flag (forwards to styleManager + restarts
      //      the sun-position timer), and
      //   2. re-assign `styles` to itself — its `didSet` runs the
      //      internal `applyStyle()` which, with the auto flag on and two
      //      styles present, picks the time-of-day style now and refreshes
      //      appearance. Without (2) the previously forced style would
      //      stay pinned until the next sunrise/sunset boundary.
      navVC.automaticallyAdjustsStyleForTimeOfDay = true
      navVC.styleManager.styles = navVC.styleManager.styles
    }
  }

  nonisolated private func scheduleSessionStart() {
    Task { @MainActor [weak self] in
      guard let self, !self.hasScheduledSessionStart else { return }
      // Latch once the attempt reaches a definitive outcome:
      //  - started   → session is up; never re-run.
      //  - terminal  → a precondition retrying can't fix (invalid coords).
      //    Latch so the error doesn't re-fire on every afterUpdate; if the
      //    host re-renders in response (e.g. an error popup) that feedback
      //    loop would otherwise spam onError and the UI can't recover.
      //  - retryable → transient (permission pending, GPS off): leave
      //    unlatched so a later update can proceed once it's resolved;
      //    emitPreconditionError de-dupes so the retry doesn't spam onError.
      switch self.startSessionIfReady() {
      case .started, .terminal:
        self.hasScheduledSessionStart = true
      case .retryable:
        break
      }
    }
  }

  /// Attempts to start the navigation session. `.started` once the route
  /// request is kicked off, `.terminal` for a precondition retrying can't fix
  /// (invalid coordinates), `.retryable` for a transient one (permission/GPS).
  @MainActor
  private func startSessionIfReady() -> SessionStart {
    // Gather the three preconditions in precedence order, short-circuiting so
    // a failing earlier check doesn't trigger the later checks' error
    // emissions (matching the original guard chain). Each `ensure*` call
    // emits its own de-duped onError as a side effect; the verdict itself is
    // decided by the pure `SessionStartDecision`.
    let coordsValid = ensureCoordinatesValid()
    let locationAvailable = coordsValid && ensureLocationAvailable()
    let permissionGranted = locationAvailable && ensureLocationPermission()
    let decision = SessionStartDecision.decideSessionStart(
      coordsValid: coordsValid,
      locationAvailable: locationAvailable,
      permissionGranted: permissionGranted
    )
    guard decision == .started else { return decision }
    lastPreconditionErrorCode = nil
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
    return .started
  }

  /// Emit a precondition error at most once per distinct code per mount.
  /// scheduleSessionStart re-checks preconditions on every afterUpdate while
  /// waiting on a retryable one (permission/GPS); without de-duping, the same
  /// onError would fire each tick and — if the host re-renders in response —
  /// spam the UI.
  @MainActor
  private func emitPreconditionError(_ code: String, _ message: String) {
    if lastPreconditionErrorCode == code { return }
    lastPreconditionErrorCode = code
    onError?(code, message)
  }

  @MainActor
  private func ensureLocationPermission() -> Bool {
    let manager = CLLocationManager()
    switch manager.authorizationStatus {
    case .notDetermined, .restricted, .denied:
      NSLog("\(logTag) location permission not granted")
      emitPreconditionError(
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
    emitPreconditionError(
      "GPS_UNAVAILABLE",
      "Location services are disabled on this device. Enable Location Services in Settings."
    )
    return false
  }

  @MainActor
  private func ensureCoordinatesValid() -> Bool {
    // Verdict (and the exact code/message to emit) comes from the pure
    // `CoordinateValidation` unit; the god class only emits via onError.
    let result = CoordinateValidation.validateCoordinates(
      origin: (origin.latitude, origin.longitude),
      destination: (destination.latitude, destination.longitude),
      waypoints: (waypoints ?? []).map {
        ($0.coordinate.latitude, $0.coordinate.longitude)
      }
    )
    switch result {
    case .ok:
      return true
    case .invalid(let code, let message):
      emitPreconditionError(code, message)
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
    let message = error.localizedDescription
    // Classification moved to the pure `RouteErrorClassifier` unit (kept in
    // lockstep with Android's `classifyRouterFailure`).
    let code = RouteErrorClassifier.classifyRouteError(error)
    NSLog("\(logTag) route error code=\(code) message=\(message)")
    onError?(code, message)
  }

  // ── Navigation UI mount ──────────────────────────────

  @MainActor
  private func presentNavigationUI(routes: NavigationRoutes) {
    guard navigationViewController == nil,
          let provider = mapboxNavigationProvider else { return }

    subscribeToArrival(navigation: provider.mapboxNavigation.navigation())

    // Simulation is wired at the `MapboxNavigationProvider` level via
    // `CoreConfig.locationSource = .simulation(...)` (see `ensureMapboxNavigation`).
    //
    // TODO: simulationSpeedMultiplier is a no-op on iOS — no public API in
    // Mapbox Navigation v3.20.x to set the simulation speed. The built-in
    // simulator's speed control (`SimulatedLocationManager.speedMultiplier`)
    // is `internal`, the `SimulatedLocationManagerWrapper` that drives the
    // `.simulation` LocationSource is `private`, and there is no public
    // accessor to the live simulated manager. The only public
    // `speedMultiplier` lives on `HistoryReplayer` (history-trace replay,
    // a different feature) and `TestHelper.Fixture` (test-only). Android
    // uses `ReplayRouteOptions.maxSpeedMps`. Achieving parity here would
    // require driving simulation through a custom `.custom(LocationClient)`
    // that re-implements the simulator — out of scope for this fix.
    // Revisit when the SDK exposes a public speed knob for `.simulation`.

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
    if carrier.view.superview == nil {
      carrier.view.translatesAutoresizingMaskIntoConstraints = false
      view.addSubview(carrier.view)
      NSLayoutConstraint.activate([
        carrier.view.topAnchor.constraint(equalTo: view.topAnchor),
        carrier.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        carrier.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
        carrier.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
      ])
    }
    // Attach carrier to the nearest UIViewController in the responder
    // chain so the NavigationViewController inherits a valid parent
    // (safe areas, status bar, presentation context). Remember the exact
    // host so teardown removes from the same parent (balanced
    // addChild/removeFromParent). If the responder chain's host changed
    // since we last attached (a host swap), move the carrier to the new
    // host instead of leaving it stranded under the old one — always
    // balancing each addChild with the matching removeFromParent.
    guard let host = findHostViewController() else { return }
    if let currentHost = carrierHost, currentHost === host {
      return // already correctly parented
    }
    if carrier.parent != nil {
      carrier.willMove(toParent: nil)
      carrier.removeFromParent()
    }
    host.addChild(carrier)
    carrier.didMove(toParent: host)
    carrierHost = host
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
    } else {
      // Dismissed normally — user tapped the SDK's built-in "End Navigation"
      // button on the arrival UI. Distinct from onCancelNavigation so JS can
      // react to arrival (onArrive) without dismounting, then unmount here.
      onNavigationEnd?()
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

    // Route progress (ETA / duration / distance remaining) must keep
    // flowing on every tick regardless of movement — these still change
    // while the user is stationary (e.g. stopped at a light: duration
    // remaining keeps counting). Gating progress on the location epsilon
    // froze the ETA whenever the map-matched position didn't move. Mirror
    // Android, where onRouteProgressChange comes from the route-progress
    // observer (movement-independent) and only onLocationChange is gated
    // on distance moved.
    onRouteProgressChange?(RouteProgress(
      distanceTraveled: progress.distanceTraveled,
      distanceRemaining: progress.distanceRemaining,
      durationRemaining: progress.durationRemaining,
      fractionTraveled: progress.fractionTraveled
    ))

    // De-duplicate the LOCATION callback only: emit it when the
    // map-matched location actually moved. This prevents a flood of
    // identical location updates when the user (or simulator) is
    // stationary and matches SPEC T10's intent that location observers
    // fire on movement, not on a clock.
    let coord = location.coordinate
    if let last = lastReportedCoordinate,
       distanceBetween(last, coord) < Self.locationChangedEpsilonMeters {
      return
    }
    lastReportedCoordinate = coord
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
