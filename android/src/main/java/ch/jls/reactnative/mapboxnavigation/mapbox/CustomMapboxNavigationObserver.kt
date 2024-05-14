package ch.jls.reactnative.mapboxnavigation.mapbox

import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider

class CustomMapboxNavigationObserver(navigationLocationProvider: NavigationLocationProvider) : MapboxNavigationObserver {
  private val locationObserver = CustomLocationObserver(navigationLocationProvider)

  override fun onAttached(mapboxNavigation: MapboxNavigation) {
    mapboxNavigation.registerLocationObserver(locationObserver)
  }

  override fun onDetached(mapboxNavigation: MapboxNavigation) {
    mapboxNavigation.unregisterLocationObserver(locationObserver)
  }
}
