package com.margelo.nitro.jlsdigital.reactnativemapboxnavigation

import android.view.View
import com.facebook.proguard.annotations.DoNotStrip
import com.facebook.react.uimanager.ThemedReactContext

@DoNotStrip
class HybridReactNativeMapboxNavigation(val context: ThemedReactContext) : HybridReactNativeMapboxNavigationSpec() {

  override val view: View = View(context)

  // ── Props ────────────────────────────────────────────

  override var origin: Coordinates = Coordinates(0.0, 0.0)
  override var destination: Coordinates = Coordinates(0.0, 0.0)
  override var waypoints: Array<Waypoint>? = null
  override var language: String? = null
  override var shouldSimulateRoute: Boolean? = null
  override var simulationSpeedMultiplier: Double? = null
  override var mute: Boolean? = null
  override var colorScheme: String? = null
  override var fontFamily: String? = null

  // ── Callbacks ────────────────────────────────────────

  override var onArrive: ((destination: Coordinates) -> Unit)? = null
  override var onError: ((code: String, message: String) -> Unit)? = null
  override var onCancelNavigation: (() -> Unit)? = null
  override var onNavigationEnd: (() -> Unit)? = null
  override var onMuteChange: ((isMuted: Boolean) -> Unit)? = null
  override var onRouteProgressChange: ((progress: RouteProgress) -> Unit)? = null
  override var onLocationChange: ((latitude: Double, longitude: Double) -> Unit)? = null
  override var onReroute: (() -> Unit)? = null

  // ── Methods ──────────────────────────────────────────

  override fun recenterCamera() {
    // TODO: implement with Mapbox SDK
  }

  override fun showRouteOverview() {
    // TODO: implement with Mapbox SDK
  }
}
