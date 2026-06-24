import Foundation

// ── LocaleUnits ───────────────────────────────────────
// Pure locale resolution + metric/imperial unit derivation extracted from
// `HybridReactNativeMapboxNavigation.ensureMapboxNavigation`. Operates on a
// plain optional `String` language code so it is unit-testable without the
// Nitro runtime.
//
// The SDK ultimately derives the spoken/displayed measurement system from
// the `CoreConfig.locale`; `usesMetric(for:)` exposes that same decision as
// a pure function so it can be asserted in tests and mirrored on Android.
enum LocaleUnits {

  /// Resolve the consumer's optional `language` prop into a `Locale`.
  /// A non-empty BCP-47 code is honoured verbatim; otherwise we fall back to
  /// the device's nationalised current locale (matching the previous inline
  /// behaviour exactly).
  static func resolveLocale(language: String?) -> Locale {
    if let code = language, !code.isEmpty {
      return Locale(identifier: code)
    }
    return Locale(identifier: nationalizedCurrentIdentifier())
  }

  /// Pure-Foundation replica of `MapboxNavigationCore`'s
  /// `Locale.nationalizedCurrent` (language from the app's preferred
  /// localization, region from the current locale). Inlined so this unit
  /// stays SDK-free and host-testable instead of importing the SDK for one
  /// extension.
  static func nationalizedCurrentIdentifier() -> String {
    let first = Bundle.main.preferredLocalizations.first ?? Locale.current.identifier
    let parts = first.components(separatedBy: "-")
    if parts.count > 1 { return first }
    if let countryCode = (Locale.current as NSLocale).object(forKey: .countryCode) as? String {
      return "\(parts.first ?? first)-\(countryCode)"
    }
    return first
  }

  /// Whether the given locale uses the metric measurement system. This is
  /// the derivation Mapbox applies internally from the locale; exposed here
  /// as a pure, testable seam. Defaults to metric when the platform can't
  /// determine the system (the global-majority default).
  static func usesMetric(for locale: Locale) -> Bool {
    if #available(iOS 16, macOS 13, *) {
      switch locale.measurementSystem {
      case .us, .uk:
        return false
      default:
        return true
      }
    } else {
      return locale.usesMetricSystem
    }
  }
}
