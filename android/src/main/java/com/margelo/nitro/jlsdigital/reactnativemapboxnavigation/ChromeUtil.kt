package com.margelo.nitro.jlsdigital.reactnativemapboxnavigation

import android.content.res.Resources
import android.graphics.Typeface
import android.util.TypedValue

/** Resolve the host app's `fontFamily` prop to a `Typeface`, falling back
 *  to the system default if the requested family can't be loaded. */
internal fun resolveTypefaceSafe(family: String?, style: Int): Typeface {
  if (family.isNullOrBlank()) return Typeface.create(Typeface.DEFAULT, style)
  return try {
    Typeface.create(family, style)
  } catch (t: Throwable) {
    Typeface.create(Typeface.DEFAULT, style)
  }
}

internal fun Resources.dp(n: Int): Int =
  TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP, n.toFloat(), displayMetrics
  ).toInt()

internal fun Resources.dpf(n: Float): Float =
  TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, n, displayMetrics)
