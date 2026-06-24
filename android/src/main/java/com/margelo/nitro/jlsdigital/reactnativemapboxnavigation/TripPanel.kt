package com.margelo.nitro.jlsdigital.reactnativemapboxnavigation

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateValue
import java.util.Locale

/**
 * Bottom dock — iOS-style:
 *   left column = duration headline (green) + distance remaining (gray);
 *   right column = arrival time + round × close button.
 *
 * The × button calls `onEndClicked`. The outer HybridView decides whether
 * to invoke `onCancelNavigation` (mid-route) or `onNavigationEnd`
 * (post-arrival) based on its `hasArrivedAtDestination` flag. The
 * button keeps `contentDescription = "End Navigation"` so existing
 * Maestro flows (`tapOn: 'End Navigation'`) still match.
 */
internal class TripPanel(
  context: Context,
  private val onEndClicked: () -> Unit
) : LinearLayout(context) {
  private val durationText: TextView
  private val distanceText: TextView
  private val arrivalTimeText: TextView
  private val closeButton: ImageButton
  private val bgDrawable: GradientDrawable
  private var baseBottomPad: Int
  private var palette: ChromePalette = ChromePalette.LIGHT

  init {
    orientation = HORIZONTAL
    bgDrawable = GradientDrawable().apply {
      setColor(Color.WHITE)
      val r = resources.dpf(16f)
      cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
    }
    background = bgDrawable

    val dp = { n: Int -> resources.dp(n) }
    baseBottomPad = dp(20)
    setPadding(dp(24), dp(16), dp(16), baseBottomPad)

    val leftCol = LinearLayout(context).apply {
      orientation = VERTICAL
      gravity = Gravity.CENTER_VERTICAL
      layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
    }
    durationText = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
      setTypeface(null, Typeface.BOLD)
      text = "—"
      isSingleLine = true
      ellipsize = TextUtils.TruncateAt.END
      includeFontPadding = false
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
      // Intentionally NOT a live region — duration ticks every second
      // and would spam TalkBack. User swipes to it on demand.
    }
    distanceText = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
      text = "—"
      isSingleLine = true
      ellipsize = TextUtils.TruncateAt.END
      includeFontPadding = false
    }
    leftCol.addView(durationText, LayoutParams(
      LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
    ))
    leftCol.addView(distanceText, LayoutParams(
      LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(2) })

    val rightCol = LinearLayout(context).apply {
      orientation = HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    arrivalTimeText = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
      setTypeface(null, Typeface.BOLD)
      text = "—"
      isSingleLine = true
      includeFontPadding = false
      // Without a minWidth the leftCol's weight=1 measurement starves
      // the right column and "2:45 pm" collapses to "2:".
      minWidth = (90f * resources.displayMetrics.density).toInt()
    }
    // 48dp = Android accessibility minimum touch target.
    val closeSize = dp(48)
    closeButton = ImageButton(context).apply {
      contentDescription = "End Navigation"
      setImageResource(R.drawable.ic_nav_close)
      scaleType = ImageView.ScaleType.CENTER_INSIDE
      background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor("#E5E7EB"))
      }
      setOnClickListener { onEndClicked() }
    }
    rightCol.addView(arrivalTimeText, LayoutParams(
      LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
    ))
    rightCol.addView(closeButton, LayoutParams(closeSize, closeSize).apply {
      marginStart = dp(12)
    })

    addView(leftCol)
    addView(rightCol, LayoutParams(
      LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
    ))
  }

  fun applyPalette(p: ChromePalette) {
    palette = p
    bgDrawable.setColor(p.dockBg)
    durationText.setTextColor(p.durationText)
    distanceText.setTextColor(p.dockSecondaryText)
    arrivalTimeText.setTextColor(p.dockPrimaryText)
    ImageViewCompat.setImageTintList(
      closeButton, ColorStateList.valueOf(p.closeIconTint)
    )
    (closeButton.background as? GradientDrawable)?.setColor(
      if (p == ChromePalette.DARK) Color.parseColor("#374151")
      else Color.parseColor("#E5E7EB")
    )
  }

  fun applyBottomInset(bottomPx: Int) {
    setPadding(paddingLeft, paddingTop, paddingRight, baseBottomPad + bottomPx)
  }

  fun applyFontFamily(family: String?) {
    val bold = resolveTypefaceSafe(family, Typeface.BOLD)
    val normal = resolveTypefaceSafe(family, Typeface.NORMAL)
    durationText.typeface = bold
    distanceText.typeface = normal
    arrivalTimeText.typeface = bold
  }

  fun update(progress: RouteProgress, tripUpdate: TripProgressUpdateValue?) {
    if (tripUpdate != null) {
      val f = tripUpdate.formatter
      // .toString() strips the SpannableString's AbsoluteSizeSpan(px)
      // that would otherwise blow up the digit glyphs past the TextView
      // height and clip them against the dock's top edge.
      durationText.text = f.getTimeRemaining(tripUpdate.currentLegTimeRemaining).toString()
      distanceText.text = f.getDistanceRemaining(tripUpdate.distanceRemaining).toString()
      arrivalTimeText.text = f.getEstimatedTimeToArrival(tripUpdate.estimatedTimeToArrival).toString()
    } else {
      durationText.text = formatDuration(progress.durationRemaining)
      distanceText.text = formatDistance(progress.distanceRemaining.toDouble())
      arrivalTimeText.text = ""
    }
  }

  fun showArrived() {
    durationText.setTextColor(palette.arrivedText)
    durationText.text = "You have arrived"
    distanceText.text = ""
    arrivalTimeText.text = ""
  }

  // Hand-rolled metric fallbacks used only when the SDK has not yet handed us
  // a TripProgressUpdateValue (whose .formatter already does locale + unit).
  // Routed through [NavFormatting] (shared with ManeuverBanner + the god
  // class); pin an explicit Locale so the "km" decimal separator is
  // deterministic rather than the implicit device default.
  private fun formatDistance(meters: Double): String =
    NavFormatting.formatDistanceMeters(meters, Locale.getDefault())

  private fun formatDuration(secs: Double): String =
    NavFormatting.formatDurationSeconds(secs)
}
