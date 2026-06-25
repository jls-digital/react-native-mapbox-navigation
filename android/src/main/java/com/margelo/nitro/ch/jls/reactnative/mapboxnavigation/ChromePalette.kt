package com.margelo.nitro.ch.jls.reactnative.mapboxnavigation

import android.graphics.Color

/**
 * Color palette pushed to every chrome view (banner, dock, speed-limit,
 * ornaments) so SPEC §B13 (colorScheme: light / dark / auto) doesn't
 * rely on hardcoded colors. The iOS SDK does this implicitly via
 * StandardDayStyle / StandardNightStyle on the drop-in
 * NavigationViewController; on Android there's no drop-in, so the
 * outer HybridView calls `applyPalette(palette)` on each chrome view
 * whenever colorScheme changes or the chrome first mounts.
 */
internal data class ChromePalette(
  val bannerBg: Int, val bannerPrimaryText: Int, val bannerSecondaryText: Int,
  val bannerDistanceText: Int, val bannerDivider: Int,
  val dockBg: Int, val dockPrimaryText: Int, val dockSecondaryText: Int,
  val durationText: Int, val arrivedText: Int, val closeIconTint: Int,
  val ornamentBg: Int, val ornamentIconTint: Int
) {
  companion object {
    val LIGHT = ChromePalette(
      bannerBg = Color.parseColor("#FFFFFF"),
      bannerPrimaryText = Color.parseColor("#111827"),
      bannerSecondaryText = Color.parseColor("#6B7280"),
      bannerDistanceText = Color.parseColor("#111827"),
      bannerDivider = Color.parseColor("#E5E7EB"),
      dockBg = Color.parseColor("#FFFFFF"),
      dockPrimaryText = Color.parseColor("#111827"),
      dockSecondaryText = Color.parseColor("#6B7280"),
      durationText = Color.parseColor("#059669"),
      arrivedText = Color.parseColor("#059669"),
      closeIconTint = Color.parseColor("#6B7280"),
      ornamentBg = Color.parseColor("#FFFFFF"),
      ornamentIconTint = Color.parseColor("#374151")
    )
    val DARK = ChromePalette(
      bannerBg = Color.parseColor("#1F2937"),
      bannerPrimaryText = Color.parseColor("#F9FAFB"),
      bannerSecondaryText = Color.parseColor("#9CA3AF"),
      bannerDistanceText = Color.parseColor("#F9FAFB"),
      bannerDivider = Color.parseColor("#374151"),
      dockBg = Color.parseColor("#1F2937"),
      dockPrimaryText = Color.parseColor("#F9FAFB"),
      dockSecondaryText = Color.parseColor("#9CA3AF"),
      durationText = Color.parseColor("#34D399"),
      arrivedText = Color.parseColor("#34D399"),
      closeIconTint = Color.parseColor("#9CA3AF"),
      ornamentBg = Color.parseColor("#374151"),
      ornamentIconTint = Color.parseColor("#F9FAFB")
    )
  }
}
