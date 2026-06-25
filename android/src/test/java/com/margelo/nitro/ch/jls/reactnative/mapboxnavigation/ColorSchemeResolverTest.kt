package com.margelo.nitro.ch.jls.reactnative.mapboxnavigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// ── ColorSchemeResolver ───────────────────────────────────────────────────
// Android-specific pure unit (no iOS mirror): the explicit prop wins; "auto"
// and null defer to the systemDark passthrough.
class ColorSchemeResolverTest {

  @Test
  fun explicitDarkIsAlwaysDark() {
    assertTrue(ColorSchemeResolver.resolveDark("dark", systemDark = false))
    assertTrue(ColorSchemeResolver.resolveDark("dark", systemDark = true))
  }

  @Test
  fun explicitLightIsAlwaysLight() {
    assertFalse(ColorSchemeResolver.resolveDark("light", systemDark = false))
    assertFalse(ColorSchemeResolver.resolveDark("light", systemDark = true))
  }

  @Test
  fun autoPassesThroughSystemDark() {
    assertTrue(ColorSchemeResolver.resolveDark("auto", systemDark = true))
    assertFalse(ColorSchemeResolver.resolveDark("auto", systemDark = false))
  }

  @Test
  fun nullPassesThroughSystemDark() {
    assertTrue(ColorSchemeResolver.resolveDark(null, systemDark = true))
    assertFalse(ColorSchemeResolver.resolveDark(null, systemDark = false))
  }
}
