package com.margelo.nitro.ch.jls.reactnative.mapboxnavigation

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.widget.ImageViewCompat

/**
 * Vertical stack of floating round map ornaments on the right edge.
 * Currently holds only the mute toggle — the recenter affordance is the
 * SDK's `MapboxRecenterButton`, mounted bottom-start and shown only when
 * the camera is not following the puck. The mute icon swaps via
 * `setMuted` driven by the `MapboxAudioGuidance` state flow on the outer
 * HybridView.
 */
internal class OrnamentStack(
  context: Context,
  private val onMute: () -> Unit
) : LinearLayout(context) {
  private val muteButton: ImageButton
  private val muteBg: GradientDrawable

  init {
    orientation = VERTICAL
    val size = resources.dp(48)
    // TODO: The Mapbox ui-components AAR ships MapboxSoundButton for exactly
    // this mute toggle and is the preferred SDK component per CLAUDE.md. We
    // keep this custom ImageButton for now because MapboxSoundButton can't
    // cleanly back the existing contract without device verification:
    //  - it's a ConstraintLayout whose icon lives in an internal AppCompatImageView,
    //    so our palette tint (ImageViewCompat on the button) and background tint
    //    (ornamentBg/ornamentIconTint) don't map onto it directly;
    //  - its mute()/unmute() toggle internal state (and return Boolean) rather
    //    than offering a plain idempotent setMuted(Boolean) setter, and it
    //    doesn't swap the "Mute"/"Unmute" contentDescription our Maestro flows
    //    rely on;
    //  - like other ui-components widgets it must be XML-inflated with its
    //    MapboxStyleSound style (Trap 1) or it measures 0×0.
    // Migrating means re-validating the icon swap + palette tint + onMute click
    // on a device, so defer until that can be tested.
    muteBg = GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(Color.WHITE)
    }
    muteButton = ImageButton(context).apply {
      background = muteBg
      setImageResource(R.drawable.ic_nav_volume_on)
      scaleType = ImageView.ScaleType.CENTER_INSIDE
      contentDescription = "Mute"
      setOnClickListener { onMute() }
    }
    addView(muteButton, LayoutParams(size, size))
  }

  fun applyPalette(p: ChromePalette) {
    muteBg.setColor(p.ornamentBg)
    ImageViewCompat.setImageTintList(muteButton, ColorStateList.valueOf(p.ornamentIconTint))
  }

  fun setMuted(muted: Boolean) {
    muteButton.setImageResource(
      if (muted) R.drawable.ic_nav_volume_off else R.drawable.ic_nav_volume_on
    )
    muteButton.contentDescription = if (muted) "Unmute" else "Mute"
  }
}
