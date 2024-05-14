package ch.jls.reactnative.mapboxnavigation.mapbox

import android.util.Log
import com.mapbox.common.location.Location
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider

class CustomLocationObserver(private val navigationLocationProvider: NavigationLocationProvider) :
  LocationObserver {

  override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
    Log.v(
      "MapboxNavigation",
      "onNewLocationMatcherResult ${locationMatcherResult.enhancedLocation}"
    )
    navigationLocationProvider.changePosition(
      location = locationMatcherResult.enhancedLocation,
      keyPoints = locationMatcherResult.keyPoints,
    )
  }

  override fun onNewRawLocation(rawLocation: Location) {
    Log.v("MapboxNavigation", "onNewRawLocation $rawLocation")
  }
}
