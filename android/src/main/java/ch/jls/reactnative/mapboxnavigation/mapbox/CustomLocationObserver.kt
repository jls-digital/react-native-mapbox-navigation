package ch.jls.reactnative.mapboxnavigation.mapbox

import android.util.Log
import com.mapbox.common.location.Location
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver

class CustomLocationObserver : LocationObserver {
  override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
    Log.v("MapboxNavigation", "onNewLocationMatcherResult ${locationMatcherResult.enhancedLocation}")
  }

  override fun onNewRawLocation(rawLocation: Location) {
    Log.v("MapboxNavigation", "onNewRawLocation $rawLocation")
  }
}
