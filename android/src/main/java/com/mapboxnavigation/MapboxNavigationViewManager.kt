package com.mapboxnavigation

import android.content.pm.PackageManager
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import com.mapbox.geojson.Point

class MapboxNavigationViewManager(private var mCallerContext: ReactApplicationContext) :
  SimpleViewManager<MapboxNavigationView>() {
  private var accessToken: String? = null

  init {
    mCallerContext.runOnUiQueueThread {
      try {
        val app = mCallerContext.packageManager.getApplicationInfo(
          mCallerContext.packageName,
          PackageManager.GET_META_DATA
        )
        val bundle = app.metaData
        val accessToken = bundle.getString("MAPBOX_ACCESS_TOKEN")
        this.accessToken = accessToken
//        ResourceOptionsManager.getDefault(mCallerContext, accessToken).update {
//          tileStoreUsageMode(TileStoreUsageMode.READ_ONLY)
//        }
      } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
      }
    }
  }

  override fun getName() = "MapboxNavigationView"

  override fun createViewInstance(reactContext: ThemedReactContext): MapboxNavigationView {
    return MapboxNavigationView(reactContext, this.accessToken)
  }

  @ReactProp(name = "origin")
  fun setOrigin(view: MapboxNavigationView, origin: ReadableArray?) {
    if (origin == null) {
      view.setOrigin(null)
      return
    }
    view.setOrigin(Point.fromLngLat(origin.getDouble(0), origin.getDouble(1)))
  }

  @ReactProp(name = "destination")
  fun setDestination(view: MapboxNavigationView, destination: ReadableArray?) {
    if (destination == null) {
      view.setDestination(null)
      return
    }
    view.setOrigin(Point.fromLngLat(destination.getDouble(0), destination.getDouble(1)))
  }

  @ReactProp(name = "waypoints")
  fun setWaypoints(view: MapboxNavigationView, waypoints: ReadableArray?) {
    view.resetWaypoints()
    if (waypoints == null) {
      return
    }
    for (i in 0 until waypoints.size()) {
      val entry = waypoints.getArray(i)
      val point = Point.fromLngLat(entry.getDouble(0), entry.getDouble(1))
      view.addWaypoint(point)
    }
  }

  @ReactProp(name = "shouldSimulateRoute")
  fun setShouldSimulateRoute(view: MapboxNavigationView, shouldSimulateRoute: Boolean) {
    view.setShouldSimulateRoute(shouldSimulateRoute)
  }

  @ReactProp(name = "shouldShowEndOfRouteFeedback")
  fun setShowsEndOfRouteFeedback(view: MapboxNavigationView, shouldShowEndOfRouteFeedback: Boolean) {
    view.setShouldShowEndOfRouteFeedback(shouldShowEndOfRouteFeedback)
  }
}
