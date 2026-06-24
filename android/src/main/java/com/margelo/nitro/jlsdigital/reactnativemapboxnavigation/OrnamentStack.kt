package com.margelo.nitro.jlsdigital.reactnativemapboxnavigation

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
