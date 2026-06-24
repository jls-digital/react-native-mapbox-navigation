import Foundation

// ── ProviderStoreState ────────────────────────────────
// Pure, framework-free model of the provider cache's refcount/config state
// and the decisions that drive it. Extracted from `MapboxProviderStore`
// (which keeps the Mapbox SDK side effects) so the refcount transitions are
// unit-testable without the SDK, the binary frameworks, or the MainActor.
//
// `MapboxProviderStore` holds one of these plus a real
// `MapboxNavigationProvider`, delegating every decision here and performing
// only the side effects (build / idle the provider) the decisions name.
struct ProviderStoreState: Equatable {
  // Config key of the currently-cached provider, or nil when none cached.
  private(set) var currentConfigKey: String?
  // Number of live holders of the currently-cached provider. The provider
  // is only idled when this drops to zero.
  private(set) var refCount: Int = 0

  /// What `acquire(configKey:)` should do, given the requested key and the
  /// current state. Mirrors the original `acquire` branch order exactly.
  enum AcquireDecision: Equatable {
    /// Cached provider matches the requested key — hand it back, bump refcount.
    case reuseSameConfig
    /// A live holder exists under a DIFFERENT config. The SDK is a singleton
    /// so we can neither build a second provider nor idle the live one;
    /// reuse it, bump refcount, and warn (unsupported concurrent configs).
    case reuseLiveDifferentConfig(requested: String, live: String?)
    /// A stale cached provider (no live holders) under a different config.
    /// Idle + drop it, then build a fresh provider for the requested key.
    case rebuildStale(previous: String?, requested: String)
    /// No provider cached — build a fresh one for the requested key.
    case buildFresh(requested: String)
  }

  /// Decide what to do on `acquire`. Pure — no mutation, no side effects.
  /// `hasProvider` reflects whether the store currently holds a live
  /// `MapboxNavigationProvider` instance.
  func acquireDecision(configKey: String, hasProvider: Bool) -> AcquireDecision {
    if hasProvider, currentConfigKey == configKey {
      return .reuseSameConfig
    }
    if hasProvider, refCount > 0 {
      return .reuseLiveDifferentConfig(requested: configKey, live: currentConfigKey)
    }
    if hasProvider {
      return .rebuildStale(previous: currentConfigKey, requested: configKey)
    }
    return .buildFresh(requested: configKey)
  }

  /// Apply an acquire to the refcount/config bookkeeping. Must be called
  /// AFTER the matching side effect (rebuild/build) has happened.
  mutating func applyAcquire(_ decision: AcquireDecision) {
    switch decision {
    case .reuseSameConfig, .reuseLiveDifferentConfig:
      refCount += 1
    case .rebuildStale(_, let requested), .buildFresh(let requested):
      currentConfigKey = requested
      refCount = 1
    }
  }

  /// What `release(configKey:)` should do. `.idle` only when the LAST holder
  /// releases; `.noop` for a balanced release of a non-last holder, or when
  /// there is nothing to release.
  enum ReleaseDecision: Equatable { case noop, idle }

  /// Decide + apply a release in one step (release has no pre-side-effect to
  /// sequence around). Returns whether the trip session must now be idled.
  mutating func release(hasProvider: Bool) -> ReleaseDecision {
    guard hasProvider, refCount > 0 else { return .noop }
    refCount -= 1
    if refCount <= 0 {
      refCount = 0
      return .idle
    }
    return .noop
  }
}
