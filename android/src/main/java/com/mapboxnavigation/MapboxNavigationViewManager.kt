package com.mapboxnavigation

import android.util.Log
import android.view.View
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

class MapboxNavigationViewManager : SimpleViewManager<View>() {
  override fun getName() = "MapboxNavigationView"

  override fun createViewInstance(reactContext: ThemedReactContext): View {
    return View(reactContext)
  }

  @ReactProp(name = "origin")
  fun setOrigin(view: View, origin: ReadableArray) {
    Log.d("MapboxNavigationView", "Origin set to ${origin.getDouble(0)}, ${origin.getDouble(1)}")
  }
}
