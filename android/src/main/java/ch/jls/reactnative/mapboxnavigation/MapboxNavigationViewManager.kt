package ch.jls.reactnative.mapboxnavigation

import android.content.pm.PackageManager
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.annotations.ReactProp
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point

class MapboxNavigationViewManager(private var reactContext: ReactApplicationContext) :
  ViewGroupManager<FrameLayout>() {
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

  override fun createViewInstance(reactContext: ThemedReactContext): FrameLayout {
    return FrameLayout(reactContext)
  }

  override fun getCommandsMap(): Map<String, Int> {
    return mapOf("create" to COMMAND_CREATE)
  }

  override fun receiveCommand(root: FrameLayout, commandId: String?, args: ReadableArray?) {
    super.receiveCommand(root, commandId, args)
    val reactNativeViewId = requireNotNull(args).getInt(0)
    Log.d("MapboxNavigation", "Received command: $commandId with viewId: $reactNativeViewId")

    when (requireNotNull(commandId).toInt()) {
      COMMAND_CREATE -> createFragment(root, reactNativeViewId)
    }
  }

  private fun createFragment(root: FrameLayout, reactNativeViewId: Int) {
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
  fun setDestination(view: FrameLayout, destination: ReadableArray?) {
    if (destination == null) {
      this.destination = null
      this.mapboxNavigationFragment?.setDestination(null)
      return
    }
    this.destination = Point.fromLngLat(destination.getDouble(0), destination.getDouble(1))
    this.mapboxNavigationFragment?.setDestination(this.destination)
  }

  @ReactProp(name = "origin")
  fun setOrigin(view: FrameLayout, origin: ReadableArray?) {
    if (origin == null) {
      this.origin = null
      this.mapboxNavigationFragment?.setOrigin(null)
      return
    }
    this.origin = Point.fromLngLat(origin.getDouble(0), origin.getDouble(1))
    this.mapboxNavigationFragment?.setOrigin(this.origin)
  }

  @ReactProp(name = "waypoints")
  fun setWaypoints(view: FrameLayout, waypoints: ReadableArray?) {
    this.mapboxNavigationFragment?.resetWaypoints()
    if (waypoints == null) {
      this.waypoints = ArrayList()
      return
    }
    for (i in 0 until waypoints.size()) {
      val entry = waypoints.getArray(i)
      val point = Point.fromLngLat(entry.getDouble(0), entry.getDouble(1))
      this.waypoints += point
      this.mapboxNavigationFragment?.addWaypoint(point)
    }
  }

  @ReactProp(name = "shouldSimulateRoute")
  fun setShouldSimulateRoute(view: FrameLayout, shouldSimulateRoute: Boolean) {
    this.shouldSimulateRoute = shouldSimulateRoute
    this.mapboxNavigationFragment?.setShouldSimulateRoute(shouldSimulateRoute)
  }

  @ReactProp(name = "shouldShowEndOfRouteFeedback")
  fun setShowsEndOfRouteFeedback(view: FrameLayout, shouldShowEndOfRouteFeedback: Boolean) {
    this.shouldShowEndOfRouteFeedback = shouldShowEndOfRouteFeedback
    this.mapboxNavigationFragment?.setShouldShowEndOfRouteFeedback(shouldShowEndOfRouteFeedback)
  }

  @ReactProp(name = "mute")
  fun setMute(view: FrameLayout, mute: Boolean) {
    this.isVoiceInstructionsMuted = mute
    this.mapboxNavigationFragment?.setMute(mute)
  }
}
