package com.margelo.nitro.jlsdigital.reactnativemapboxnavigation

import com.mapbox.navigation.base.formatter.UnitType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

// ── LocaleUnits ───────────────────────────────────────────────────────────
// Mirrors iOS PureUnitsTests.testLocaleUnits (resolveLocale verbatim,
// empty/null fallback, US→imperial vs DE→metric).
class LocaleUnitsTest {

  @Test
  fun resolveLocaleHonoursTagVerbatim() {
    // "de-DE" is honoured verbatim; toLanguageTag() is the JVM analog of the
    // iOS `Locale.identifier` round-trip asserted in the mirror test.
    assertEquals("de-DE", LocaleUnits.resolveLocale("de-DE").toLanguageTag())
  }

  @Test
  fun emptyStringFallsBackIdenticallyToNull() {
    // Empty/blank string falls back to the device default, identical to null.
    assertEquals(
      LocaleUnits.resolveLocale(null),
      LocaleUnits.resolveLocale("")
    )
    assertEquals(Locale.getDefault(), LocaleUnits.resolveLocale(""))
    assertEquals(Locale.getDefault(), LocaleUnits.resolveLocale(null))
  }

  @Test
  fun underscoreSeparatorIsNormalisedToHyphen() {
    // The god class normalised `de_DE` → `de-DE` before parsing; keep that.
    assertEquals("de-DE", LocaleUnits.resolveLocale("de_DE").toLanguageTag())
  }

  @Test
  fun unitTypeForUsIsImperialAndDeIsMetric() {
    assertEquals(UnitType.IMPERIAL, LocaleUnits.unitTypeFor(Locale.forLanguageTag("en-US")))
    assertEquals(UnitType.METRIC, LocaleUnits.unitTypeFor(Locale.forLanguageTag("de-DE")))
    // Other imperial holdouts the inline logic kept.
    assertEquals(UnitType.IMPERIAL, LocaleUnits.unitTypeFor(Locale.forLanguageTag("en-LR")))
    assertEquals(UnitType.IMPERIAL, LocaleUnits.unitTypeFor(Locale.forLanguageTag("my-MM")))
    assertEquals(UnitType.METRIC, LocaleUnits.unitTypeFor(Locale.forLanguageTag("en-GB")))
  }
}
