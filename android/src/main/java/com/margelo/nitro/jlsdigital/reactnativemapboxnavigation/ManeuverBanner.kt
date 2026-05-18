package com.margelo.nitro.jlsdigital.reactnativemapboxnavigation

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.ImageViewCompat
import com.mapbox.navigation.base.internal.maneuver.ManeuverTurnIcon
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.tripdata.maneuver.model.LaneIndicator
import com.mapbox.navigation.tripdata.maneuver.model.Maneuver

/**
 * Top maneuver banner.
 *
 * Layout: left column = SDK turn-arrow icon + distance-to-maneuver
 * underneath; right column = primary instruction (street name) +
 * optional secondary text; thin lane-arrow row below the primary row.
 *
 * The turn-arrow drawables ship in Mapbox's `tripdata` AAR (e.g.
 * `R.drawable.mapbox_ic_turn_left`). Their path fill references
 * `?attr/maneuverTurnIconColor`, which is only defined inside the SDK's
 * `MapboxStyleTurnIconManeuver` style — our library doesn't inherit it,
 * so we wrap the context in a `ContextThemeWrapper` keyed to that style
 * before inflating, and additionally force a runtime tint via
 * `DrawableCompat` to match the current chrome palette.
 */
internal class ManeuverBanner(context: Context) : LinearLayout(context) {
  private val arrowIcon: ImageView
  private val instructionText: TextView
  private val distanceText: TextView
  private val secondaryText: TextView
  private val laneRow: LinearLayout
  private val divider: View
  private var palette: ChromePalette = ChromePalette.LIGHT
  private var distanceFormatter: MapboxDistanceFormatter? = null

  init {
    orientation = VERTICAL
    val dp = { n: Int -> resources.dp(n) }
    setPadding(dp(20), dp(16), dp(20), dp(12))

    val primaryRow = LinearLayout(context).apply {
      orientation = HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }

    val leftCol = LinearLayout(context).apply {
      orientation = VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      minimumWidth = dp(72)
    }
    // Scale the icon with system font scale so users on very-large-text
    // settings get a correspondingly larger arrow (clamped at 1.6×).
    val fontScale = resources.configuration.fontScale.coerceIn(1f, 1.6f)
    val arrowPx = (56f * resources.displayMetrics.density * fontScale).toInt()
    arrowIcon = ImageView(context).apply {
      scaleType = ImageView.ScaleType.FIT_CENTER
      layoutParams = LayoutParams(arrowPx, arrowPx)
      // The verbal instructionText already conveys the maneuver — don't
      // duplicate to TalkBack via the icon.
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    distanceText = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
      setTypeface(null, Typeface.BOLD)
      text = "—"
      gravity = Gravity.CENTER
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    leftCol.addView(arrowIcon)
    leftCol.addView(distanceText, LayoutParams(
      LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(2) })

    val rightCol = LinearLayout(context).apply {
      orientation = VERTICAL
      gravity = Gravity.CENTER_VERTICAL
      layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        .apply { marginStart = dp(16) }
    }
    instructionText = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
      setTypeface(null, Typeface.BOLD)
      text = "Calculating route…"
      maxLines = 2
      ellipsize = TextUtils.TruncateAt.END
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
      accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    secondaryText = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
      visibility = View.GONE
    }
    rightCol.addView(instructionText)
    rightCol.addView(secondaryText, LayoutParams(
      LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(2) })

    primaryRow.addView(leftCol)
    primaryRow.addView(rightCol)
    addView(primaryRow, LayoutParams(
      LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
    ))

    laneRow = LinearLayout(context).apply {
      orientation = HORIZONTAL
      visibility = View.GONE
      // Lane glyphs are visual-only; instructionText covers lane info verbally.
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    addView(laneRow, LayoutParams(
      LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(8); marginStart = dp(72 + 16) })

    divider = View(context)
    addView(divider, LayoutParams(LayoutParams.MATCH_PARENT, dp(1))
      .apply { topMargin = dp(12) })

    applyPalette(palette)
  }

  fun applyPalette(p: ChromePalette) {
    palette = p
    setBackgroundColor(p.bannerBg)
    instructionText.setTextColor(p.bannerPrimaryText)
    secondaryText.setTextColor(p.bannerSecondaryText)
    distanceText.setTextColor(p.bannerDistanceText)
    ImageViewCompat.setImageTintList(
      arrowIcon, ColorStateList.valueOf(p.bannerPrimaryText)
    )
    divider.setBackgroundColor(p.bannerDivider)
    for (i in 0 until laneRow.childCount) {
      val child = laneRow.getChildAt(i) as? TextView ?: continue
      val active = child.tag as? Boolean ?: false
      child.setTextColor(if (active) p.laneActive else p.laneInactive)
    }
  }

  fun setDistanceFormatter(f: MapboxDistanceFormatter) {
    distanceFormatter = f
  }

  fun applyFontFamily(family: String?) {
    val bold = resolveTypefaceSafe(family, Typeface.BOLD)
    val normal = resolveTypefaceSafe(family, Typeface.NORMAL)
    instructionText.typeface = bold
    distanceText.typeface = bold
    secondaryText.typeface = normal
  }

  fun update(
    progress: RouteProgress,
    maneuver: Maneuver?,
    turnIcon: ManeuverTurnIcon?
  ) {
    val primary = maneuver?.primary
    val instr = primary?.text?.takeIf { it.isNotBlank() }
      ?: progress.currentLegProgress?.currentStepProgress?.step?.maneuver()?.instruction()
        ?.takeIf { it.isNotBlank() }
      ?: "Continue"
    val stepDistanceRemaining = maneuver?.stepDistance?.distanceRemaining?.toDouble()
      ?: progress.currentLegProgress?.currentStepProgress?.distanceRemaining?.toDouble()
      ?: progress.distanceRemaining.toDouble()
    instructionText.text = instr
    distanceText.text = formatDistance(stepDistanceRemaining)
    applyTurnIcon(turnIcon)

    val secondary = maneuver?.secondary?.text?.takeIf { it.isNotBlank() }
    if (secondary != null) {
      secondaryText.text = secondary
      secondaryText.visibility = View.VISIBLE
    } else {
      secondaryText.visibility = View.GONE
    }

    renderLaneGuidance(maneuver?.laneGuidance?.allLanes)
  }

  fun showArrived() {
    instructionText.text = "You have arrived"
    distanceText.text = ""
    setTintedIcon(com.mapbox.navigation.tripdata.R.drawable.mapbox_ic_arrive)
    arrowIcon.rotation = 0f
    arrowIcon.scaleX = 1f
  }

  private fun applyTurnIcon(turn: ManeuverTurnIcon?) {
    val iconRes = turn?.icon
      ?: com.mapbox.navigation.tripdata.R.drawable.mapbox_ic_turn_straight
    setTintedIcon(iconRes)
    arrowIcon.rotation = turn?.degree ?: 0f
    arrowIcon.scaleX = if (turn?.shouldFlipIcon == true) -1f else 1f
  }

  private val themedIconContext by lazy {
    ContextThemeWrapper(
      context,
      com.mapbox.navigation.ui.components.R.style.MapboxStyleTurnIconManeuver
    )
  }

  private fun setTintedIcon(@DrawableRes resId: Int) {
    val raw = AppCompatResources.getDrawable(themedIconContext, resId) ?: return
    val wrapped = DrawableCompat.wrap(raw.mutate())
    DrawableCompat.setTint(wrapped, palette.bannerPrimaryText)
    arrowIcon.setImageDrawable(wrapped)
  }

  private fun renderLaneGuidance(lanes: List<LaneIndicator>?) {
    laneRow.removeAllViews()
    if (lanes.isNullOrEmpty()) {
      laneRow.visibility = View.GONE
      return
    }
    val endMarginPx = resources.dp(8)
    lanes.forEach { lane ->
      val active = lane.isActive
      val glyph = laneGlyph(lane.activeDirection ?: lane.directions.firstOrNull())
      val tv = TextView(context).apply {
        text = glyph
        setTextColor(if (active) palette.laneActive else palette.laneInactive)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        tag = active
      }
      laneRow.addView(tv, LayoutParams(
        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
      ).apply { marginEnd = endMarginPx })
    }
    laneRow.visibility = View.VISIBLE
  }

  private fun laneGlyph(direction: String?): String = when (direction) {
    "left" -> "↰"
    "slight left" -> "↖"
    "sharp left" -> "⬉"
    "right" -> "↱"
    "slight right" -> "↗"
    "sharp right" -> "⬈"
    "straight" -> "↑"
    "uturn" -> "↩"
    else -> "↑"
  }

  private fun formatDistance(meters: Double): String {
    // Tiered countdown granularity: coarse far away, finer close up.
    // The SDK formatter only supports a single rounding increment, so we
    // pre-round here and then run through the formatter for locale + unit.
    val rounded = when {
      meters >= 1000.0 -> meters
      meters >= 500.0 -> Math.round(meters / 100.0) * 100.0
      meters >= 100.0 -> Math.round(meters / 50.0) * 50.0
      else -> Math.round(meters / 25.0) * 25.0
    }
    distanceFormatter?.let { return it.formatDistance(rounded).toString() }
    if (rounded < 1000) return "${rounded.toInt()} m"
    return String.format("%.1f km", rounded / 1000.0)
  }
}
