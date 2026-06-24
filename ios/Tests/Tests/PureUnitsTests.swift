import XCTest
@testable import PureUnits

// ── Pure-unit tests (off-device, no Mapbox SDK) ───────
// Covers the Foundation-only seams extracted from the iOS HybridView. The
// error-message PARITY TABLE is the cross-platform source of truth — the same
// (input → code) rows are mirrored in the Kotlin and TS suites so the three
// classifiers can't drift. (The MapboxDirections-typed branch of
// RouteErrorClassifier is exercised by the `yarn ios` build + covered at the
// message level here; it can't be host-tested without the SDK binary tree.)
final class PureUnitsTests: XCTestCase {

  // MARK: RouteErrorMessageClassifier — shared substring heuristic.
  // PARITY TABLE — mirror verbatim in Kotlin (classifyRouterFailure) + TS.
  // Codes: NETWORK_ERROR / SDK_INIT_FAILED / INVALID_COORDINATES /
  //        ROUTE_CALCULATION_FAILED.
  func testMessageParityTable() {
    let table: [(message: String, expected: String)] = [
      // network / timeout / connection → NETWORK_ERROR
      ("A network error occurred", "NETWORK_ERROR"),
      ("Request timeout", "NETWORK_ERROR"),
      ("Lost connection to host", "NETWORK_ERROR"),
      ("NETWORK UNREACHABLE", "NETWORK_ERROR"),          // case-insensitive
      // auth / 401 / 403 → SDK_INIT_FAILED
      ("Authentication failed", "SDK_INIT_FAILED"),
      ("HTTP 401 Unauthorized", "SDK_INIT_FAILED"),
      ("HTTP 403 Forbidden", "SDK_INIT_FAILED"),
      // input / invalid → INVALID_COORDINATES
      ("Invalid input provided", "INVALID_COORDINATES"),
      ("The input was malformed", "INVALID_COORDINATES"),
      ("invalid coordinate", "INVALID_COORDINATES"),
      // unroutable (Mapbox NoRoute/NoSegment) → ROUTE_CALCULATION_FAILED.
      // These beat the input/invalid check even when the message contains
      // "input" — the real Atlantic-destination failure is the first row.
      ("Could not find a matching segment for input coordinates", "ROUTE_CALCULATION_FAILED"),
      ("No segment found near coordinate", "ROUTE_CALCULATION_FAILED"),
      ("Unable to route to destination", "ROUTE_CALCULATION_FAILED"),
      // everything else → ROUTE_CALCULATION_FAILED
      ("Something unexpected went wrong", "ROUTE_CALCULATION_FAILED"),
      ("", "ROUTE_CALCULATION_FAILED"),
      ("No route found", "ROUTE_CALCULATION_FAILED"),
      // precedence: network beats auth beats unroutable beats input
      ("network auth invalid", "NETWORK_ERROR"),
      ("auth invalid input", "SDK_INIT_FAILED"),
    ]
    for row in table {
      XCTAssertEqual(
        RouteErrorMessageClassifier.classify(row.message), row.expected,
        "classify(\"\(row.message)\") should be \(row.expected)")
    }
  }

  // MARK: CoordinateValidation
  func testCoordinateValidity() {
    XCTAssertTrue(CoordinateValidation.isValidCoordinate(latitude: 47.37, longitude: 8.54))
    XCTAssertTrue(CoordinateValidation.isValidCoordinate(latitude: -90, longitude: 180))   // boundary
    XCTAssertTrue(CoordinateValidation.isValidCoordinate(latitude: 90, longitude: -180))   // boundary
    XCTAssertFalse(CoordinateValidation.isValidCoordinate(latitude: 0, longitude: 0))      // (0,0) sentinel
    XCTAssertFalse(CoordinateValidation.isValidCoordinate(latitude: 90.01, longitude: 0))
    XCTAssertFalse(CoordinateValidation.isValidCoordinate(latitude: 0, longitude: 180.01))
    XCTAssertFalse(CoordinateValidation.isValidCoordinate(latitude: -90.01, longitude: 0))
  }

  func testValidateCoordinatesVerdict() {
    XCTAssertEqual(
      CoordinateValidation.validateCoordinates(
        origin: (47.37, 8.54), destination: (46.95, 7.45), waypoints: []),
      .ok)
    // invalid origin → INVALID_COORDINATES with the origin/destination message
    guard case let .invalid(code, message) = CoordinateValidation.validateCoordinates(
      origin: (0, 0), destination: (46.95, 7.45), waypoints: []) else {
      return XCTFail("expected .invalid for (0,0) origin")
    }
    XCTAssertEqual(code, "INVALID_COORDINATES")
    XCTAssertTrue(message.contains("Origin or destination"))
    // first invalid waypoint reported by 1-based index
    guard case let .invalid(_, wpMessage) = CoordinateValidation.validateCoordinates(
      origin: (47.37, 8.54), destination: (46.95, 7.45),
      waypoints: [(10, 10), (200, 0)]) else {
      return XCTFail("expected .invalid for out-of-range waypoint")
    }
    XCTAssertTrue(wpMessage.contains("#2"), "should name the 2nd waypoint")
  }

  // MARK: ProviderConfigKey — stable across calls; distinguishes sim+locale;
  // origin must NOT appear (regression guard for the checkInstanceIsUnique crash).
  func testProviderConfigKey() {
    let en = Locale(identifier: "en-US")
    let de = Locale(identifier: "de-DE")
    XCTAssertEqual(
      ProviderConfigKey.providerConfigKey(simulated: true, locale: en),
      ProviderConfigKey.providerConfigKey(simulated: true, locale: en))
    XCTAssertNotEqual(
      ProviderConfigKey.providerConfigKey(simulated: true, locale: en),
      ProviderConfigKey.providerConfigKey(simulated: false, locale: en))
    XCTAssertNotEqual(
      ProviderConfigKey.providerConfigKey(simulated: true, locale: en),
      ProviderConfigKey.providerConfigKey(simulated: true, locale: de))
    XCTAssertFalse(
      ProviderConfigKey.providerConfigKey(simulated: true, locale: en).contains("origin"))
  }

  // MARK: SessionStartDecision — invalid coords = terminal (the popup-loop fix)
  func testSessionStartDecision() {
    XCTAssertEqual(.started, SessionStartDecision.decideSessionStart(
      coordsValid: true, locationAvailable: true, permissionGranted: true))
    XCTAssertEqual(.terminal, SessionStartDecision.decideSessionStart(
      coordsValid: false, locationAvailable: true, permissionGranted: true))
    XCTAssertEqual(.retryable, SessionStartDecision.decideSessionStart(
      coordsValid: true, locationAvailable: false, permissionGranted: true))
    XCTAssertEqual(.retryable, SessionStartDecision.decideSessionStart(
      coordsValid: true, locationAvailable: true, permissionGranted: false))
    // coords-invalid takes precedence over the transient failures
    XCTAssertEqual(.terminal, SessionStartDecision.decideSessionStart(
      coordsValid: false, locationAvailable: false, permissionGranted: false))
  }

  // MARK: ProviderStoreState — refcount/config transitions
  func testProviderStoreRefcountLifecycle() {
    var s = ProviderStoreState()
    // first acquire: nothing cached → buildFresh, refcount 1
    XCTAssertEqual(s.acquireDecision(configKey: "k1", hasProvider: false),
                   .buildFresh(requested: "k1"))
    s.applyAcquire(.buildFresh(requested: "k1"))
    XCTAssertEqual(s.refCount, 1)
    // same config, live → reuse, refcount 2
    XCTAssertEqual(s.acquireDecision(configKey: "k1", hasProvider: true), .reuseSameConfig)
    s.applyAcquire(.reuseSameConfig)
    XCTAssertEqual(s.refCount, 2)
    // release: idle only on the LAST holder
    XCTAssertEqual(s.release(hasProvider: true), .noop)
    XCTAssertEqual(s.refCount, 1)
    XCTAssertEqual(s.release(hasProvider: true), .idle)
    XCTAssertEqual(s.refCount, 0)
    // over-release is a noop
    XCTAssertEqual(s.release(hasProvider: true), .noop)
  }

  func testProviderStoreDifferentConfigBranches() {
    // live holder + different config → reuse live (unsupported concurrent)
    var live = ProviderStoreState()
    live.applyAcquire(.buildFresh(requested: "k1"))
    XCTAssertEqual(live.acquireDecision(configKey: "k2", hasProvider: true),
                   .reuseLiveDifferentConfig(requested: "k2", live: "k1"))
    // stale (refcount 0) + different config → rebuild
    var stale = ProviderStoreState()
    stale.applyAcquire(.buildFresh(requested: "k1"))
    _ = stale.release(hasProvider: true)             // refcount 0, provider still cached
    XCTAssertEqual(stale.acquireDecision(configKey: "k2", hasProvider: true),
                   .rebuildStale(previous: "k1", requested: "k2"))
  }

  // MARK: LocaleUnits
  func testLocaleUnits() {
    XCTAssertEqual(LocaleUnits.resolveLocale(language: "de-DE").identifier, "de-DE")
    // empty string falls back identically to nil
    XCTAssertEqual(
      LocaleUnits.resolveLocale(language: "").identifier,
      LocaleUnits.resolveLocale(language: nil).identifier)
    XCTAssertFalse(LocaleUnits.usesMetric(for: Locale(identifier: "en-US")))
    XCTAssertTrue(LocaleUnits.usesMetric(for: Locale(identifier: "de-DE")))
  }
}
