package ch.jls.reactnative.mapboxnavigation

import android.content.pm.PackageManager
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.annotations.ReactProp
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point

class MapboxNavigationViewManager(private var reactContext: ReactApplicationContext) :
  ViewGroupManager<NavigationFrameLayout>() {
  private var accessToken: String? = null
  private var mapboxNavigationFragment: MapboxNavigationFragment? = null

  /**
   * Navigation destination point
   */
  private var destination: Point? = null

  /**
   * Navigation origin point
   */
  private var origin: Point? = null

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
   * Whether the voice should be muted
   */
  private var isVoiceInstructionsMuted = false

  companion object {
    private const val REACT_CLASS = "MapboxNavigation"
    private const val COMMAND_CREATE = 1
  }

  init {
    reactContext.runOnUiQueueThread {
      try {
        val app = reactContext.packageManager.getApplicationInfo(
          reactContext.packageName,
          PackageManager.GET_META_DATA
        )
        val bundle = app.metaData
        val accessToken = bundle.getString("MAPBOX_ACCESS_TOKEN")
        this.accessToken = accessToken
        if (accessToken != null) {
          MapboxOptions.accessToken = accessToken
        }
      } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
      }
    }
  }

  override fun getName() = REACT_CLASS

  override fun createViewInstance(reactContext: ThemedReactContext): NavigationFrameLayout {
    return NavigationFrameLayout(reactContext)
  }

  override fun onDropViewInstance(view: NavigationFrameLayout) {
    super.onDropViewInstance(view)

    Log.d("MapboxNavigation", "Dropping view instance")
    if (reactContext.currentActivity == null) {
      Log.d("MapboxNavigation", "Activity is already null, no need to remove fragment")
      return
    }
    val activity = reactContext.currentActivity as FragmentActivity
    activity.supportFragmentManager
      .beginTransaction()
      .remove(this.mapboxNavigationFragment!!)
      .commit()
    this.mapboxNavigationFragment = null
    Log.d("MapboxNavigation", "Fragment removed")
  }

  override fun getCommandsMap(): Map<String, Int> {
    return mapOf("create" to COMMAND_CREATE)
  }

  override fun receiveCommand(root: NavigationFrameLayout, commandId: String?, args: ReadableArray?) {
    super.receiveCommand(root, commandId, args)
    val reactNativeViewId = requireNotNull(args).getInt(0)
    Log.d("MapboxNavigation", "Received command: $commandId with viewId: $reactNativeViewId")

    when (requireNotNull(commandId).toInt()) {
      COMMAND_CREATE -> createFragment(root, reactNativeViewId)
    }
  }

  /**
   * Called after the view has been set up and measured.
   * On new architecture (Fabric), UIManager.dispatchViewManagerCommand doesn't work,
   * so we create the fragment here as a fallback.
   */
  override fun onAfterUpdateTransaction(view: NavigationFrameLayout) {
    super.onAfterUpdateTransaction(view)
    if (this.mapboxNavigationFragment == null) {
      Log.d("MapboxNavigation", "onAfterUpdateTransaction: creating fragment for view ${view.id}")
      createFragmentInView(view)
    }
  }

  private fun createFragmentInView(root: NavigationFrameLayout) {
    setupLayout(root)

    val fragment = MapboxNavigationFragment(this.reactContext)
    this.mapboxNavigationFragment = fragment

    fragment.setOrigin(this.origin)
    fragment.setDestination(this.destination)
    fragment.resetWaypoints()
    this.waypoints.forEach { fragment.addWaypoint(it) }
    fragment.setShouldSimulateRoute(this.shouldSimulateRoute)
    fragment.setShouldShowEndOfRouteFeedback(this.shouldShowEndOfRouteFeedback)
    fragment.setMute(this.isVoiceInstructionsMuted)

    val activity = reactContext.currentActivity
    if (activity !is FragmentActivity) {
      Log.e("MapboxNavigation", "currentActivity is not a FragmentActivity, cannot create navigation fragment")
      return
    }

    // Fabric-managed views aren't in the standard Android view hierarchy,
    // so FragmentManager.replace(containerId, ...) fails with "No view found for id".
    // Instead, add the fragment headlessly, then reparent its view into our FrameLayout.
    val tag = "mapbox_nav_${root.id}"

    try {
      activity.supportFragmentManager
        .beginTransaction()
        .add(fragment, tag)
        .commitNow()

      // After commitNow, the fragment's view is created. Add it to our container.
      fragment.view?.let { fragmentView ->
        root.addView(fragmentView)
      }

      Log.d("MapboxNavigation", "Fragment created: $fragment")
    } catch (e: Exception) {
      Log.e("MapboxNavigation", "Failed to create navigation fragment", e)
    }
  }

  private fun createFragment(root: NavigationFrameLayout, reactNativeViewId: Int) {
    val parentView = root.findViewById<ViewGroup>(reactNativeViewId)
    setupLayout(parentView)

    this.mapboxNavigationFragment = MapboxNavigationFragment(this.reactContext)

    this.mapboxNavigationFragment!!.setOrigin(this.origin)
    this.mapboxNavigationFragment!!.setDestination(this.destination)
    this.mapboxNavigationFragment!!.resetWaypoints()
    this.waypoints.forEach { this.mapboxNavigationFragment!!.addWaypoint(it) }
    this.mapboxNavigationFragment!!.setShouldSimulateRoute(this.shouldSimulateRoute)
    this.mapboxNavigationFragment!!.setShouldShowEndOfRouteFeedback(this.shouldShowEndOfRouteFeedback)
    this.mapboxNavigationFragment!!.setMute(this.isVoiceInstructionsMuted)

    val activity = reactContext.currentActivity as FragmentActivity
    activity.supportFragmentManager
      .beginTransaction()
      .replace(reactNativeViewId, this.mapboxNavigationFragment!!, reactNativeViewId.toString())
      .commit()
    Log.d("MapboxNavigation", "Fragment created: ${this.mapboxNavigationFragment}")
  }

  private fun setupLayout(parentView: View) {
    Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
      override fun doFrame(frameTimeNanos: Long) {
        layoutChildView(parentView)
        parentView.viewTreeObserver.dispatchOnGlobalLayout()
        Choreographer.getInstance().postFrameCallback(this)
      }
    })
  }

  private fun layoutChildView(parentView: View) {
    val fragmentView = this.mapboxNavigationFragment?.view ?: return

    val parentWidth = parentView.width
    val parentHeight = parentView.height

    // When using this fragment approach, this is the only way to ensure that the ConstraintLayout
    // used at the root of navigation_view.xml is properly sized.
    fragmentView.measure(
      View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(parentHeight, View.MeasureSpec.EXACTLY)
    )
    fragmentView.layout(0, 0, parentWidth, parentHeight)
  }

  @ReactProp(name = "destination")
  fun setDestination(view: NavigationFrameLayout, destination: ReadableArray?) {
    if (destination == null) {
      this.destination = null
      this.mapboxNavigationFragment?.setDestination(null)
      return
    }
    this.destination = Point.fromLngLat(destination!!.getDouble(0), destination!!.getDouble(1))
    this.mapboxNavigationFragment?.setDestination(this.destination)
  }

  @ReactProp(name = "origin")
  fun setOrigin(view: NavigationFrameLayout, origin: ReadableArray?) {
    if (origin == null) {
      this.origin = null
      this.mapboxNavigationFragment?.setOrigin(null)
      return
    }
    this.origin = Point.fromLngLat(origin!!.getDouble(0), origin!!.getDouble(1))
    this.mapboxNavigationFragment?.setOrigin(this.origin)
  }

  @ReactProp(name = "waypoints")
  fun setWaypoints(view: NavigationFrameLayout, waypoints: ReadableArray?) {
    this.mapboxNavigationFragment?.resetWaypoints()
    if (waypoints == null) {
      this.waypoints = ArrayList()
      return
    }
    for (i in 0 until waypoints.size()) {
      val entry = waypoints.getArray(i)
      if (entry == null) {
        continue
      }
      val point = Point.fromLngLat(entry.getDouble(0), entry.getDouble(1))
      this.waypoints += point
      this.mapboxNavigationFragment?.addWaypoint(point)
    }
  }

  @ReactProp(name = "shouldSimulateRoute")
  fun setShouldSimulateRoute(view: NavigationFrameLayout, shouldSimulateRoute: Boolean) {
    this.shouldSimulateRoute = shouldSimulateRoute
    this.mapboxNavigationFragment?.setShouldSimulateRoute(shouldSimulateRoute)
  }

  @ReactProp(name = "shouldShowEndOfRouteFeedback")
  fun setShowsEndOfRouteFeedback(view: NavigationFrameLayout, shouldShowEndOfRouteFeedback: Boolean) {
    this.shouldShowEndOfRouteFeedback = shouldShowEndOfRouteFeedback
    this.mapboxNavigationFragment?.setShouldShowEndOfRouteFeedback(shouldShowEndOfRouteFeedback)
  }

  @ReactProp(name = "mute")
  fun setMute(view: NavigationFrameLayout, mute: Boolean) {
    this.isVoiceInstructionsMuted = mute
    this.mapboxNavigationFragment?.setMute(mute)
  }
}
