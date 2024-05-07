package com.mapboxnavigation

import android.util.Log
import com.facebook.react.bridge.ReadableArray

class MapboxNavigationView {
  fun setOrigin(origin: ReadableArray) {
    Log.d("MapboxNavigationView", "origin set to ${origin.getDouble(0)}, ${origin.getDouble(1)}")
  }
}
