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
// The refcount/config bookkeeping lives in `ProviderStoreState` and the
// effect-sequencing (idle-before-rebuild, idle-on-last-release, never two live
// instances) lives in the SDK-free `ProviderCache` — both host-unit-tested
// without the SDK. This class is the thin shell: it conforms the real
// `MapboxNavigationProvider` to `ProviderControlling`, injects the
// provider-factory closure (defaulting to the real `MapboxNavigationProvider(coreConfig:)`),
// and forwards acquire/release to the cache.
//
// We deliberately keep the cached provider alive + idle between mounts rather
// than deallocating on release: Mapbox's provider owns a threadpool +
// StyleManager, and releasing it in the same runloop turn as `setToIdle()`
// races in-flight draw work and segfaults. The next mount with the same config
// reuses it (the common case — the configKey is stable across remounts), with
// no rebuild and no `checkInstanceIsUnique()` risk. A genuine config change
// (locale / sim toggle) is the `rebuildStale` path in `ProviderCache`, which
// idles + drops the stale provider before building the replacement.
//
// TODO: support concurrent nav views with differing configs if the SDK ever
// drops the single-provider constraint. For now that combination is
// unsupported (the `reuseLiveDifferentConfig` path) and logged loudly.
extension MapboxNavigationProvider: ProviderControlling {
  // `ProviderControlling` is deliberately non-isolated (so the generic cache +
  // its tests stay SDK- and actor-free), but `mapboxNavigation` is main-actor
  // isolated. Every call into the cache — and therefore into idle() — originates
  // from the @MainActor `MapboxProviderStore`, so assert that isolation here
  // rather than hopping actors (which would make idle() async).
  nonisolated func idle() {
    MainActor.assumeIsolated {
      mapboxNavigation.tripSession().setToIdle()
    }
  }
}

@MainActor
final class MapboxProviderStore {
  static let shared = MapboxProviderStore()
  private let cache: ProviderCache<MapboxNavigationProvider>
  // Injectable so the build/idle sequencing can be exercised in tests with a
  // fake provider. Production uses the real ctor.
  private let makeProvider: (CoreConfig) -> MapboxNavigationProvider

  init(makeProvider: @escaping (CoreConfig) -> MapboxNavigationProvider = { MapboxNavigationProvider(coreConfig: $0) }) {
    self.makeProvider = makeProvider
    self.cache = ProviderCache(log: { NSLog("\(providerStoreLogTag) MapboxProviderStore: \($0)") })
  }

  func acquire(configKey: String, coreConfig: CoreConfig) -> MapboxNavigationProvider {
    cache.acquire(configKey: configKey) { [makeProvider] _ in makeProvider(coreConfig) }
  }

  func release(configKey: String) {
    cache.release(configKey: configKey)
  }
}
