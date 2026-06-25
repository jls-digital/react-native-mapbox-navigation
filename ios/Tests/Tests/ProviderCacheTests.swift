import XCTest
@testable import PureUnits

// ── ProviderCache tests (off-device, no Mapbox SDK) ───────
// Drives the provider lifecycle through a FakeProvider that enforces the same
// contract the real MapboxNavigationProvider does: at most ONE live instance at
// a time (mirroring `checkInstanceIsUnique`, which crashes on a second
// concurrent instance). These tests are the off-device backstop for the
// dangling-provider crash — they prove the cache idles + drops a stale handle
// BEFORE building its replacement, and idles only on the last release, without
// a simulator or the SDK binary frameworks.

/// A stand-in provider that tracks how many instances are alive at once and
/// records idle() calls. `peakLive > 1` would mean the cache built a second
/// instance while the first was still alive — the exact crash we guard against.
private final class FakeProvider: ProviderControlling {
  static var live = 0
  static var peakLive = 0
  static func reset() { live = 0; peakLive = 0 }

  let configKey: String
  private(set) var idleCount = 0

  init(configKey: String) {
    self.configKey = configKey
    FakeProvider.live += 1
    FakeProvider.peakLive = max(FakeProvider.peakLive, FakeProvider.live)
  }
  deinit { FakeProvider.live -= 1 }

  func idle() { idleCount += 1 }
}

final class ProviderCacheTests: XCTestCase {

  override func setUp() {
    super.setUp()
    FakeProvider.reset()
  }

  private func makeCache() -> ProviderCache<FakeProvider> {
    ProviderCache<FakeProvider>()
  }

  // Reopen with the SAME config reuses the cached (idle) provider — no second
  // instance is ever built, so checkInstanceIsUnique would never fire.
  func testReopenSameConfigReusesSingleInstance() {
    let cache = makeCache()
    var built: [String] = []
    let make: (String) -> FakeProvider = { key in
      built.append(key)
      return FakeProvider(configKey: key)
    }

    // Mount → unmount (last holder) → mount again, same config.
    _ = cache.acquire(configKey: "k1", make: make)
    XCTAssertEqual(cache.release(configKey: "k1"), .idle)
    _ = cache.acquire(configKey: "k1", make: make)
    XCTAssertEqual(cache.release(configKey: "k1"), .idle)

    XCTAssertEqual(built, ["k1"], "same config must reuse the cached provider, not rebuild")
    XCTAssertEqual(FakeProvider.peakLive, 1, "never more than one live instance")
  }

  // Open A → close → open B (different config): the stale A must be idled +
  // deallocated BEFORE B is built. This is the open→close→open-different
  // sequence that used to trip checkInstanceIsUnique.
  func testReopenDifferentConfigRebuildsWithoutTwoLiveInstances() {
    let cache = makeCache()

    weak var weakFirst: FakeProvider?
    autoreleasepool {
      let first = cache.acquire(configKey: "k1") { FakeProvider(configKey: $0) }
      weakFirst = first
      XCTAssertEqual(cache.release(configKey: "k1"), .idle)
      XCTAssertEqual(first.idleCount, 1, "last release idles the provider")
    }
    // No external strong holder now; the cache still retains the stale "k1".
    XCTAssertNotNil(weakFirst, "stale provider stays cached until a config change")
    XCTAssertEqual(FakeProvider.live, 1)

    // Acquire a DIFFERENT config → rebuildStale.
    let second = cache.acquire(configKey: "k2") { FakeProvider(configKey: $0) }
    XCTAssertEqual(second.configKey, "k2")
    XCTAssertNil(weakFirst, "stale provider must be released before the rebuild")
    XCTAssertEqual(FakeProvider.live, 1, "exactly one live instance after rebuild")
    XCTAssertEqual(FakeProvider.peakLive, 1, "must never hold two live instances during rebuild")
  }

  // Two concurrent holders: the provider is idled only when the LAST one
  // releases — a sibling unmount must not idle a still-live session.
  func testIdlesOnlyOnLastHolderRelease() {
    let cache = makeCache()
    let provider = cache.acquire(configKey: "k1") { FakeProvider(configKey: $0) }
    _ = cache.acquire(configKey: "k1") { FakeProvider(configKey: $0) } // second holder, same config

    XCTAssertEqual(cache.release(configKey: "k1"), .noop, "non-last release must not idle")
    XCTAssertEqual(provider.idleCount, 0)
    XCTAssertEqual(cache.release(configKey: "k1"), .idle, "last release idles")
    XCTAssertEqual(provider.idleCount, 1)
    XCTAssertEqual(FakeProvider.peakLive, 1, "same config never builds a second instance")
  }

  // A live holder requesting a different config can't rebuild (singleton) — it
  // reuses the live provider rather than crashing or killing the live session.
  func testLiveDifferentConfigReusesInsteadOfRebuilding() {
    let cache = makeCache()
    var buildCount = 0
    let first = cache.acquire(configKey: "k1") { buildCount += 1; return FakeProvider(configKey: $0) }
    // Still a live holder (not released) → request different config.
    let second = cache.acquire(configKey: "k2") { buildCount += 1; return FakeProvider(configKey: $0) }

    XCTAssertTrue(first === second, "must reuse the live provider, not build a second")
    XCTAssertEqual(buildCount, 1)
    XCTAssertEqual(FakeProvider.peakLive, 1)
  }
}
