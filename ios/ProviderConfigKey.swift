import Foundation

// ── ProviderConfigKey ─────────────────────────────────
// Pure derivation of the cache key under which the shared
// `MapboxNavigationProvider` is acquired from `MapboxProviderStore`.
// Extracted from `HybridReactNativeMapboxNavigation.ensureMapboxNavigation`
// so the key format is unit-testable (and locked against accidental drift —
// remounts of the same logical config MUST produce the same key, otherwise
// open→close→open rebuilds a checked singleton and trips
// `checkInstanceIsUnique()`).
//
// The key distinguishes only the inputs that REQUIRE a different provider:
// locale and simulation mode. Origin is intentionally NOT part of the key
// (see the store / `ensureMapboxNavigation` for why).
enum ProviderConfigKey {

  /// Build the provider config key. Stable across calls for identical
  /// inputs — `Locale.identifier` is deterministic for a given locale.
  static func providerConfigKey(simulated: Bool, locale: Locale) -> String {
    "sim=\(simulated)|locale=\(locale.identifier)"
  }
}
