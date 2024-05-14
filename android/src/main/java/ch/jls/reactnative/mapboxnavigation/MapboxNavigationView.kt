package ch.jls.reactnative.mapboxnavigation

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ch.jls.reactnative.mapboxnavigation.databinding.NavigationViewBinding
import ch.jls.reactnative.mapboxnavigation.mapbox.CustomMapboxNavigationObserver
import com.facebook.react.bridge.Arguments
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.mapbox.geojson.Point
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.viewport
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider

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

  /**
   * Binding for the view
   */
  private var viewBinding: NavigationViewBinding =
    NavigationViewBinding.inflate(LayoutInflater.from(context), this, true)

  /**
   * Mapbox Maps entry point obtained from the [MapView].
   * You need to get a new reference to this object whenever the [MapView] is recreated.
   */
  private lateinit var mapboxMap: MapboxMap

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    onCreate()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    val mapboxNavigation = MapboxNavigationApp.current()
    mapboxNavigation?.stopTripSession()
  }

  fun onCreate() {
    if (this.accessToken == null) {
      sendErrorToReact("Mapbox access token is not set")
      return
    }

    if (origin == null || destination == null) {
      sendErrorToReact("Origin and destination are required")
    }
    Log.i("MapboxNavigation", "onCreate")
    Log.i("MapboxNavigation", "binding: ${this.viewBinding}")

//    mapboxMap = viewBinding.mapView.mapboxMap
    if (!MapboxNavigationApp.isSetup()) {
      MapboxNavigationApp.setup {
        NavigationOptions.Builder(this.context)
          .build()
      }
    }
    MapboxNavigationApp.attach(this.context.currentActivity as AppCompatActivity)
    val navigationLocationProvider = NavigationLocationProvider()
    MapboxNavigationApp.registerObserver(CustomMapboxNavigationObserver(navigationLocationProvider))
    val mapboxNavigation = MapboxNavigationApp.current()
    if (ActivityCompat.checkSelfPermission(
        this.context,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
        this.context,
        Manifest.permission.ACCESS_COARSE_LOCATION
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      // TODO: Consider calling
      //    ActivityCompat#requestPermissions
      // here to request the missing permissions, and then overriding
      //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
      //                                          int[] grantResults)
      // to handle the case where the user grants the permission. See the documentation
      // for ActivityCompat#requestPermissions for more details.
      return
    }
    mapboxNavigation?.startTripSession()
    this.viewBinding.mapView.location.apply {
      this.locationPuck = LocationPuck2D(
        topImage = ImageHolder.Companion.from(com.mapbox.navigation.ui.maps.R.drawable.mapbox_navigation_puck_icon),
        bearingImage = ImageHolder.from(com.mapbox.navigation.ui.maps.R.drawable.mapbox_navigation_puck_icon),
        shadowImage = ImageHolder.Companion.from(com.mapbox.navigation.ui.maps.R.drawable.mapbox_navigation_puck_icon)
      )
      setLocationProvider(navigationLocationProvider)
      puckBearingEnabled = true
      enabled = true
    }
  }

  // Setters for react native driven properties

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
    Log.v(
      "MapboxNavigation",
      "destination set to ${destination.latitude()}, ${destination.longitude()}"
    )
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

  private fun sendErrorToReact(error: String?) {
    val event = Arguments.createMap()
    event.putString("error", error)
    if (error != null) {
      Log.e("MapboxNavigation", error)
    }
//    context
//      .getJSModule(RCTEventEmitter::class.java)
//      .receiveEvent(id, "onError", event)
  }
}
