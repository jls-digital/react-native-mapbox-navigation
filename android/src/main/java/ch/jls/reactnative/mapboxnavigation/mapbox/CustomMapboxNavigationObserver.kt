package ch.jls.reactnative.mapboxnavigation.mapbox

import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver

class CustomMapboxNavigationObserver : MapboxNavigationObserver {
  private val locationObserver = CustomLocationObserver()

  override fun onAttached(mapboxNavigation: MapboxNavigation) {
    mapboxNavigation.registerLocationObserver(locationObserver)
  }

  override fun onDetached(mapboxNavigation: MapboxNavigation) {
    mapboxNavigation.unregisterLocationObserver(locationObserver)
  }
}
