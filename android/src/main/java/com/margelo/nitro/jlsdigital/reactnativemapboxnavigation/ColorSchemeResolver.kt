package com.margelo.nitro.jlsdigital.reactnativemapboxnavigation

// ── ColorSchemeResolver ───────────────────────────────
// The pure part of `HybridReactNativeMapboxNavigation.resolveDark`: map the
// `colorScheme` prop ("dark"/"light"/"auto"/null) to a dark/light verdict.
// The god class passes its framework-derived `isSystemInDarkMode()` for the
// `systemDark` arg, keeping the `Configuration.uiMode` read on its side so
// this unit stays Android-framework-free and host-testable.
object ColorSchemeResolver {

  /**
   *  - "dark"  → always dark
   *  - "light" → always light
   *  - anything else ("auto", null) → follow the system ([systemDark])
   */
  fun resolveDark(colorScheme: String?, systemDark: Boolean): Boolean = when (colorScheme) {
    "dark" -> true
    "light" -> false
    else -> systemDark
  }
}
