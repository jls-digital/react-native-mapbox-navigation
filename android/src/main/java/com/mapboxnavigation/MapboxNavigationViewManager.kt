package com.mapboxnavigation

import android.content.pm.PackageManager
import android.util.Log
import android.view.View
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

class MapboxNavigationViewManager(private var mCallerContext: ReactApplicationContext) : SimpleViewManager<View>() {
  private var accessToken: String? = null

  init {
    mCallerContext.runOnUiQueueThread {
      try {
        val app = mCallerContext.packageManager.getApplicationInfo(mCallerContext.packageName, PackageManager.GET_META_DATA)
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

  override fun createViewInstance(reactContext: ThemedReactContext): View {
    return View(reactContext)
  }

  @ReactProp(name = "origin")
  fun setOrigin(view: View, origin: ReadableArray) {
    Log.d("MapboxNavigationView", "Origin set to ${origin.getDouble(0)}, ${origin.getDouble(1)}")
  }
}
