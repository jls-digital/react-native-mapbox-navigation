package ch.jls.reactnative.mapboxnavigation

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Custom FrameLayout that claims touch events for the native navigation fragment.
 *
 * React Native's touch system (and react-native-gesture-handler) can intercept
 * touch events before they reach embedded native fragments. This layout calls
 * [requestDisallowInterceptTouchEvent] on its parent when a touch begins,
 * ensuring the navigation map's gesture recognizers (pan, pinch, etc.) receive
 * all touch events directly.
 */
class NavigationFrameLayout(context: Context) : FrameLayout(context) {

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    if (ev.action == MotionEvent.ACTION_DOWN) {
      parent?.requestDisallowInterceptTouchEvent(true)
    }
    return false
  }
}
