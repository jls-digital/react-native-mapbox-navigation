package com.mapboxnavigation

import android.annotation.SuppressLint
import android.util.Log
import android.widget.FrameLayout
import com.facebook.react.uimanager.ThemedReactContext
import com.mapbox.geojson.Point

@SuppressLint("ViewConstructor")
class MapboxNavigationView(
  private val context: ThemedReactContext,
  private val accessToken: String?
) :
  FrameLayout(context.baseContext) {

  /**
   * Navigation origin point
   */
  private var origin: Point? = null

  /**
   * Navigation destination point
   */
  private var destination: Point? = null

  /**
   * List of waypoints for the navigation to follow
   */
  private var waypoints: List<Point> = ArrayList()

  /**
   * Whether navigation should be simulated
   */
  private var shouldSimulateRoute = false

  /**
   * Whether feedback should be shown by mapbox when navigation is finished
   */
  private var shouldShowEndOfRouteFeedback = false

  fun setOrigin(origin: Point?) {
    this.origin = origin
    if (origin == null) {
      Log.w("MapboxNavigation", "origin set to null")
      return
    }
    Log.v("MapboxNavigation", "origin set to ${origin.latitude()}, ${origin.longitude()}")
  }

  fun setDestination(destination: Point?) {
    this.destination = destination
    if (destination == null) {
      Log.w("MapboxNavigation", "destination set to null")
      return
    }
    Log.v("MapboxNavigation", "destination set to ${destination.latitude()}, ${destination.longitude()}")
  }

  fun addWaypoint(waypoint: Point) {
    this.waypoints += waypoint
  }

  fun resetWaypoints() {
    this.waypoints = ArrayList()
  }

  fun setShouldSimulateRoute(shouldSimulateRoute: Boolean) {
    this.shouldSimulateRoute = shouldSimulateRoute
  }

  fun setShouldShowEndOfRouteFeedback(shouldShowEndOfRouteFeedback: Boolean) {
    this.shouldShowEndOfRouteFeedback = shouldShowEndOfRouteFeedback
  }

  fun setMute(mute: Boolean) {
//    this.isVoiceInstructionsMuted = mute
  }

  fun setShouldRerouteProactively(shouldRerouteProactively: Boolean) {
//    this.shouldRerouteProactively = shouldRerouteProactively
  }
}
