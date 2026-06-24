package com.margelo.nitro.jlsdigital.reactnativemapboxnavigation

import com.mapbox.navigation.base.formatter.UnitType
import java.util.Locale

// ── LocaleUnits ───────────────────────────────────────
// Pure locale resolution + metric/imperial unit derivation extracted from
// `HybridReactNativeMapboxNavigation` (the inline `resolveLocale` /
// `unitTypeFor`). Operates on a plain optional `String` language code and a
// `Locale` so it is unit-testable without the Nitro runtime; the only SDK
// touch is the value enum `UnitType`, which is a plain JVM enum.
//
// Mirrors iOS `LocaleUnits` (resolveLocale + usesMetric).
object LocaleUnits {

  /**
   * Resolve the consumer's optional `language` prop into a [Locale].
   * A non-blank tag is honoured verbatim (with `_` normalised to `-` for
   * BCP-47); otherwise we fall back to the device's default locale (matching
   * the previous inline behaviour exactly).
   */
  fun resolveLocale(language: String?): Locale {
    val tag = language?.takeIf { it.isNotBlank() } ?: return Locale.getDefault()
    return Locale.forLanguageTag(tag.replace('_', '-'))
  }

  /**
   * Whether the given locale uses imperial or metric units. Mirrors the
   * country-code derivation the god class applied inline: the imperial
   * holdouts (US, Liberia, Myanmar) get [UnitType.IMPERIAL], everything else
   * is [UnitType.METRIC].
   */
  fun unitTypeFor(locale: Locale): UnitType = when (locale.country.uppercase()) {
    "US", "LR", "MM" -> UnitType.IMPERIAL
    else -> UnitType.METRIC
  }
}
