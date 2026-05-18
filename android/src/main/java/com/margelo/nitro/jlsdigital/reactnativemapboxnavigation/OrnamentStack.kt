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
 * Vertical stack of floating round map ornaments on the right edge —
 * recenter on top, mute below. Both are 48dp (Android min touch
 * target). The mute icon swaps via `setMuted` driven by the
 * `MapboxAudioGuidance` state flow on the outer HybridView.
 */
internal class OrnamentStack(
  context: Context,
  private val onRecenter: () -> Unit,
  private val onMute: () -> Unit
) : LinearLayout(context) {
  private val recenterButton: ImageButton
  private val muteButton: ImageButton
  private val recenterBg: GradientDrawable
  private val muteBg: GradientDrawable

  init {
    orientation = VERTICAL
    val size = resources.dp(48)
    recenterBg = GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(Color.WHITE)
    }
    recenterButton = ImageButton(context).apply {
      background = recenterBg
      setImageResource(R.drawable.ic_nav_recenter)
      scaleType = ImageView.ScaleType.CENTER_INSIDE
      contentDescription = "Recenter"
      setOnClickListener { onRecenter() }
    }
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
    addView(recenterButton, LayoutParams(size, size))
    addView(muteButton, LayoutParams(size, size).apply { topMargin = resources.dp(8) })
  }

  fun applyPalette(p: ChromePalette) {
    recenterBg.setColor(p.ornamentBg)
    muteBg.setColor(p.ornamentBg)
    val tint = ColorStateList.valueOf(p.ornamentIconTint)
    ImageViewCompat.setImageTintList(recenterButton, tint)
    ImageViewCompat.setImageTintList(muteButton, tint)
  }

  fun setMuted(muted: Boolean) {
    muteButton.setImageResource(
      if (muted) R.drawable.ic_nav_volume_off else R.drawable.ic_nav_volume_on
    )
    muteButton.contentDescription = if (muted) "Unmute" else "Mute"
  }
}
