import Foundation

// ── ProviderCache ─────────────────────────────────────
// SDK-free orchestration of the provider lifecycle: it owns the refcount/config
// state (`ProviderStoreState`) AND sequences the side effects those decisions
// imply — crucially, idling + dropping a stale handle BEFORE building its
// replacement, so a single-instance-guarded provider never sees two live
// instances at once (the `checkInstanceIsUnique` crash).
//
// Generic over `ProviderControlling` so `MapboxProviderStore` can be a thin
// shell binding `Handle = MapboxNavigationProvider`, while the host tests bind a
// `FakeProvider` that asserts the single-live-instance + idle-before-release
// contract off-device — no Mapbox SDK, no simulator. The pure refcount math is
// `ProviderStoreState`; this adds the ordering of effects around it.

/// A provider handle the cache can idle (and release by dropping its only
/// strong reference). `AnyObject` so dropping the cache's reference deallocates
/// the handle, mirroring the real provider whose teardown is "idle + release".
protocol ProviderControlling: AnyObject {
  func idle()
}

final class ProviderCache<Handle: ProviderControlling> {
  private(set) var state = ProviderStoreState()
  private var current: Handle?
  private let log: (String) -> Void

  init(log: @escaping (String) -> Void = { _ in }) {
    self.log = log
  }

  var hasProvider: Bool { current != nil }

  /// Acquire a handle for `configKey`, building one via `make` when needed.
  /// Mirrors `ProviderStoreState.AcquireDecision` branch-for-branch.
  func acquire(configKey: String, make: (String) -> Handle) -> Handle {
    let decision = state.acquireDecision(configKey: configKey, hasProvider: current != nil)
    switch decision {
    case .reuseSameConfig:
      state.applyAcquire(decision)
      return current!
    case .reuseLiveDifferentConfig(let requested, let live):
      // A live holder is navigating under a different config. The provider is a
      // singleton, so we can neither build a second nor idle the live one;
      // reuse it and flag the unsupported combination.
      log("WARNING requested config (\(requested)) differs from live config (\(live ?? "nil")) while \(state.refCount) holder(s) active; reusing live provider (unsupported concurrent configs)")
      state.applyAcquire(decision)
      return current!
    case .rebuildStale(let previous, let requested):
      // Idle + DROP the stale handle before constructing the replacement. The
      // order matters: building first would briefly hold two live instances and
      // trip the provider's single-instance guard. Dropping the only strong
      // reference (current = nil) deallocates the old handle synchronously.
      log("config changed (\(previous ?? "nil") → \(requested)) — releasing stale provider before rebuild")
      current?.idle()
      current = nil
      let new = make(configKey)
      current = new
      state.applyAcquire(decision)
      return new
    case .buildFresh(let requested):
      let new = make(requested)
      current = new
      state.applyAcquire(decision)
      return new
    }
  }

  /// Release a hold. Idles (but keeps cached) only when the LAST holder
  /// releases. Returns the decision so callers can observe it in tests.
  @discardableResult
  func release(configKey: String) -> ProviderStoreState.ReleaseDecision {
    let decision = state.release(hasProvider: current != nil)
    if decision == .idle {
      log("last holder released (\(configKey)) — idling provider")
      current?.idle()
    }
    return decision
  }
}
