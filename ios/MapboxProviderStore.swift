import Foundation
import MapboxNavigationCore

private let providerStoreLogTag = "[RNMapboxNav]"

// The pure refcount/config bookkeeping (`ProviderStoreState`) lives in
// `ProviderStoreState.swift` (Foundation-only) so it can be unit-tested
// without the Mapbox SDK / its iOS-only binary frameworks. This file keeps
// only the SDK-touching shell.

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
// consumer toggles language or shouldSimulateRoute we drop the cached
// provider and rebuild. That reintroduces the release race exactly once per
// config swap, which is rare and gated by setToIdle(). The route origin is
// deliberately NOT part of the config key: a new origin on a remount reuses
// the cached provider rather than rebuilding it (see acquire/configKey).
//
// Refcounting: the shared provider is reference-counted by live holders
// (acquire/release). `setToIdle()` only runs when the LAST holder
// releases — so a second view mounting with a *different* configKey never
// idles a still-mounted holder's live session (the bug this guards
// against: a config-key change while another view is mid-navigation used
// to call `setToIdle()` on the live shared provider). After the last
// holder releases, the provider stays cached and idle so the next mount
// with the same config reuses it without tripping `checkInstanceIsUnique`
// or racing Mapbox's threadpool during release.
//
// `MapboxNavigationProvider` is effectively a process singleton
// (`checkInstanceIsUnique` crashes on a second concurrent instance), so we
// can never hold two live providers with different configs at once. Two
// views mounted simultaneously must therefore share the same config
// (locale + sim). A request for a *different* config while a
// holder is still live is unsupportable by the SDK; rather than crash
// (build a 2nd provider) or kill the sibling (idle+rebuild), we reuse the
// existing provider and flag it. See TODO below.
//
// The pure refcount/config bookkeeping lives in `ProviderStoreState` so it
// can be unit-tested without the SDK; this class injects a provider-factory
// closure (defaulting to the real `MapboxNavigationProvider(coreConfig:)`)
// and performs the framework side effects the decisions name.
@MainActor
final class MapboxProviderStore {
  static let shared = MapboxProviderStore()
  private var provider: MapboxNavigationProvider?
  private var state = ProviderStoreState()
  // Injectable so the refcount transitions + build/idle sequencing can be
  // exercised in tests with a fake provider. Production uses the real ctor.
  private let makeProvider: (CoreConfig) -> MapboxNavigationProvider

  init(makeProvider: @escaping (CoreConfig) -> MapboxNavigationProvider = { MapboxNavigationProvider(coreConfig: $0) }) {
    self.makeProvider = makeProvider
  }

  func acquire(configKey: String, coreConfig: CoreConfig) -> MapboxNavigationProvider {
    let decision = state.acquireDecision(configKey: configKey, hasProvider: provider != nil)
    switch decision {
    case .reuseSameConfig:
      state.applyAcquire(decision)
      return provider!
    case .reuseLiveDifferentConfig(let requested, let live):
      // Another holder is still navigating with a different config. We
      // cannot safely build a second provider (SDK singleton) nor idle
      // this one (would kill the live session — the very bug we fix).
      // Reuse the live provider and increment the refcount; the newcomer
      // inherits the existing locale/sim/origin.
      // TODO: support concurrent nav views with differing configs if the
      // SDK ever drops the single-provider constraint. For now this is an
      // unsupported combination — surface it loudly so it's caught in dev.
      NSLog("\(providerStoreLogTag) MapboxProviderStore: WARNING requested config (\(requested)) differs from live config (\(live ?? "nil")) while \(state.refCount) holder(s) active; reusing live provider (unsupported concurrent configs)")
      state.applyAcquire(decision)
      return provider!
    case .rebuildStale(let previous, let requested):
      // A stale provider with no live holders. Idle it AND drop our strong
      // ref BEFORE constructing the replacement: MapboxNavigationProvider's
      // `checkInstanceIsUnique()` asserts (crashes) if a second instance is
      // built while the first is still alive. We deliberately don't bind it
      // to a local `let` — that would keep it alive across the init below.
      NSLog("\(providerStoreLogTag) MapboxProviderStore: config changed (\(previous ?? "nil") → \(requested)) — releasing stale provider before rebuild")
      provider?.mapboxNavigation.tripSession().setToIdle()
      provider = nil
      let new = makeProvider(coreConfig)
      provider = new
      state.applyAcquire(decision)
      return new
    case .buildFresh:
      let new = makeProvider(coreConfig)
      provider = new
      state.applyAcquire(decision)
      return new
    }
  }

  // Release a hold previously taken via `acquire`. When the LAST holder
  // releases, idle the trip session but KEEP the provider cached and alive.
  //
  // We deliberately do NOT deallocate it here: Mapbox's provider owns a
  // threadpool + StyleManager, and releasing it in the same runloop turn as
  // `setToIdle()` races in-flight draw work and segfaults. Keeping it cached
  // also lets the next mount with the same config reuse it (the common case —
  // the configKey is stable across remounts), with no rebuild and no
  // `checkInstanceIsUnique()` risk. A genuine config change (locale / sim
  // toggle) is handled in `acquire`, which drops the stale provider before
  // building the replacement.
  func release(configKey: String) {
    switch state.release(hasProvider: provider != nil) {
    case .noop:
      return
    case .idle:
      NSLog("\(providerStoreLogTag) MapboxProviderStore: last holder released (\(configKey)) — idling provider")
      provider?.mapboxNavigation.tripSession().setToIdle()
    }
  }
}
