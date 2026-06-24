package com.margelo.nitro.jlsdigital.reactnativemapboxnavigation

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.LifecycleOwner
import com.facebook.proguard.annotations.DoNotStrip
import com.facebook.react.uimanager.ThemedReactContext
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.Style
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.directions.session.RoutesExtra
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.replay.route.ReplayRouteOptions
import com.mapbox.navigation.core.replay.route.ReplayRouteSession
import com.mapbox.navigation.core.replay.route.ReplayRouteSessionOptions
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.TripSessionState
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.maneuver.api.MapboxTurnIconsApi
import com.mapbox.navigation.tripdata.maneuver.model.ManeuverOptions
import com.mapbox.navigation.tripdata.maneuver.model.TurnIconResources
import com.mapbox.navigation.tripdata.progress.api.MapboxTripProgressApi
import com.mapbox.navigation.tripdata.progress.model.DistanceRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.EstimatedTimeOfArrivalFormatter
import com.mapbox.navigation.base.TimeFormat
import java.util.Locale
import com.mapbox.navigation.tripdata.progress.model.PercentDistanceTraveledFormatter
import com.mapbox.navigation.tripdata.progress.model.TimeRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateFormatter
import com.mapbox.navigation.tripdata.speedlimit.api.MapboxSpeedInfoApi
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.voice.api.MapboxAudioGuidance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "RNMapboxNav"

/**
 * Android implementation of the Mapbox Navigation HybridView.
 *
 * Mapbox Navigation Android SDK v3 removed v2's drop-in `NavigationView`,
 * so this composes a custom navigation UI from the SDK's building blocks
 * plus thin chrome of our own:
 *  - `MapView` renders the basemap
 *  - `MapboxRouteLineApi` / `MapboxRouteLineView` draw the route
 *  - `NavigationCamera` + `MapboxNavigationViewportDataSource` follow the puck
 *  - `MapboxAudioGuidance` handles voice + mute
 *  - [ManeuverBanner] shows the upcoming maneuver
 *  - [TripPanel] shows ETA + remaining distance + close button
 *  - [OrnamentStack] floats the mute toggle on the right edge
 *  - [com.mapbox.navigation.ui.components.maps.camera.view.MapboxRecenterButton]
 *    sits bottom-start, shown only when the camera is not following
 *  - SDK MapboxSpeedInfoView floats the posted-speed badge
 *
 * Mirrors the iOS implementation (`ios/ReactNativeMapboxNavigation.swift`)
 * for event semantics: arrival notification, cancel vs end, progress /
 * location throttling, reroute detection, error-code mapping.
 */
@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
@DoNotStrip
class HybridReactNativeMapboxNavigation(
  val context: ThemedReactContext
) : HybridReactNativeMapboxNavigationSpec() {

  private val mainHandler = Handler(Looper.getMainLooper())

  // SPEC §T9: teardown signal is `View.onDetachedFromWindow`, but RN's
  // ScrollView may briefly detach/re-attach the same view in one layout
  // pass — defer 250 ms so a same-frame re-attach can cancel.
  private val deferredTeardown = Runnable {
    if (container.windowToken == null && !isShuttingDown) detachNavigationUI()
  }
  private inner class HostContainer : LinearLayout(context) {
    private var hasAttachedOnce = false
    override fun onAttachedToWindow() {
      super.onAttachedToWindow()
      hasAttachedOnce = true
      mainHandler.removeCallbacks(deferredTeardown)
    }
    override fun onDetachedFromWindow() {
      super.onDetachedFromWindow()
      if (!hasAttachedOnce) return
      mainHandler.removeCallbacks(deferredTeardown)
      mainHandler.postDelayed(deferredTeardown, 250)
    }
  }
  private val container: LinearLayout = HostContainer().apply {
    orientation = LinearLayout.VERTICAL
    layoutParams = ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
  }
  override val view: View = container

  private var mapView: MapView? = null
  private var mapFrame: FrameLayout? = null
  private var maneuverBanner: ManeuverBanner? = null
  private var tripPanel: TripPanel? = null
  private var ornamentStack: OrnamentStack? = null
  private var recenterButton: com.mapbox.navigation.ui.components.maps.camera.view.MapboxRecenterButton? = null
  private var speedLimitView: com.mapbox.navigation.ui.components.speedlimit.view.MapboxSpeedInfoView? = null
  private var currentPalette: ChromePalette = ChromePalette.LIGHT

  private val navigationLocationProvider = NavigationLocationProvider()
  private var viewportDataSource: MapboxNavigationViewportDataSource? = null
  private var navigationCamera: NavigationCamera? = null
  private var routeLineApi: MapboxRouteLineApi? = null
  private var routeLineView: MapboxRouteLineView? = null

  private var maneuverApi: MapboxManeuverApi? = null
  private var turnIconsApi: MapboxTurnIconsApi? = null
  private var tripProgressApi: MapboxTripProgressApi? = null
  private var speedInfoApi: MapboxSpeedInfoApi? = null
  private var distanceFormatterOptions: DistanceFormatterOptions? = null

  private var hasScheduledSessionStart = false
  private var isShuttingDown = false
  private var hasArrivedAtDestination = false
  private var firstLocationReceived = false
  private var lastLat: Double? = null
  private var lastLon: Double? = null
  private val locationEpsilonMeters = 0.5

  // SPEC §T10: throttle JS callbacks to ~1 Hz. Native still consumes every
  // tick so the map/camera stay smooth.
  private val jsEventMinIntervalMs = 1000L
  private var lastProgressEmitMs: Long = 0L
  private var lastLocationEmitMs: Long = 0L

  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var muteJob: Job? = null
  private var lastKnownMuted: Boolean = false
  private var loadedStyle: Style? = null
  private var pendingRoutes: List<NavigationRoute>? = null
  private var replayRouteSession: ReplayRouteSession? = null

  // ── Props ────────────────────────────────────────────

  override var origin: Coordinates = Coordinates(0.0, 0.0)
  override var destination: Coordinates = Coordinates(0.0, 0.0)
  override var waypoints: Array<Waypoint>? = null
  override var language: String? = null
  override var shouldSimulateRoute: Boolean? = null
  override var simulationSpeedMultiplier: Double? = null

  override var mute: Boolean? = null
    set(value) {
      val old = field
      field = value
      if (old != value && value != null) applyMute(value)
    }

  override var colorScheme: String? = null
    set(value) {
      val old = field
      field = value
      if (old != value) applyColorScheme()
    }

  override var fontFamily: String? = null
    set(value) {
      val old = field
      field = value
      if (old != value) {
        maneuverBanner?.applyFontFamily(value)
        tripPanel?.applyFontFamily(value)
      }
    }

  // ── Callbacks ────────────────────────────────────────

  override var onArrive: ((destination: Coordinates) -> Unit)? = null
  override var onError: ((code: String, message: String) -> Unit)? = null
  override var onCancelNavigation: (() -> Unit)? = null
  override var onNavigationEnd: (() -> Unit)? = null
  override var onMuteChange: ((isMuted: Boolean) -> Unit)? = null
  override var onRouteProgressChange: ((progress: RouteProgress) -> Unit)? = null
  override var onLocationChange: ((latitude: Double, longitude: Double) -> Unit)? = null
  override var onReroute: (() -> Unit)? = null

  // ── Lifecycle ────────────────────────────────────────

  override fun afterUpdate() {
    if (hasScheduledSessionStart || isShuttingDown) return
    hasScheduledSessionStart = true
    startSessionIfReady()
  }

  override fun onDropView() = detachNavigationUI()

  private fun detachNavigationUI() {
    if (isShuttingDown) return
    isShuttingDown = true
    mainHandler.removeCallbacks(deferredTeardown)
    muteJob?.cancel()
    muteJob = null
    try {
      MapboxNavigationApp.current()?.let { nav ->
        nav.unregisterRouteProgressObserver(routeProgressObserver)
        nav.unregisterLocationObserver(locationObserver)
        nav.unregisterArrivalObserver(arrivalObserver)
        nav.unregisterRoutesObserver(routesObserver)
        nav.setNavigationRoutes(emptyList())
        nav.stopTripSession()
      }
    } catch (t: Throwable) {
      Log.w(TAG, "detach cleanup threw: ${t.message}")
    }
    MapboxNavigationApp.unregisterObserver(mapboxNavigationObserver)
    replayRouteSession?.let {
      try { MapboxNavigationApp.unregisterObserver(it) } catch (_: Throwable) {}
    }
    replayRouteSession = null
    try { maneuverApi?.cancel() } catch (_: Throwable) {}
    maneuverApi = null
    turnIconsApi = null
    tripProgressApi = null
    routeLineApi?.cancel()
    routeLineView?.cancel()
    container.removeAllViews()
    mapView = null
    mapFrame = null
    maneuverBanner = null
    tripPanel = null
    ornamentStack = null
    recenterButton = null
    speedLimitView = null
    speedInfoApi = null
    distanceFormatterOptions = null
    scope.cancel()
    // SPEC §T9: release the SDK singleton so re-mount can call setup()
    // again without tripping `checkInstanceIsUnique`.
    try { MapboxNavigationApp.disable() } catch (t: Throwable) {
      Log.w(TAG, "MapboxNavigationApp.disable() threw: ${t.message}")
    }
  }

  // ── Imperative methods ──────────────────────────────

  override fun recenterCamera() {
    navigationCamera?.requestNavigationCameraToFollowing()
  }

  override fun showRouteOverview() {
    navigationCamera?.requestNavigationCameraToOverview()
  }

  // ── Session start ────────────────────────────────────

  private fun startSessionIfReady() {
    if (!ensureCoordinatesValid()) return
    if (!ensureLocationAvailable()) return
    if (!ensureLocationPermission()) return

    ensureMapboxSetup()
    ensureTripDataApis()
    mountUi()
    MapboxNavigationApp.registerObserver(mapboxNavigationObserver)
    if (shouldSimulateRoute == true) {
      val session = buildReplayRouteSession()
      replayRouteSession = session
      MapboxNavigationApp.registerObserver(session)
    }
  }

  private fun buildReplayRouteSession(): ReplayRouteSession {
    // SPEC §B7: scale the SDK's default replay max speed (30 m/s) by the
    // JS-side multiplier prop.
    val multiplier = simulationSpeedMultiplier?.takeIf { it > 0 } ?: 1.0
    val replayOpts = ReplayRouteOptions.Builder()
      .maxSpeedMps(30.0 * multiplier)
      .build()
    val sessionOpts = ReplayRouteSessionOptions.Builder()
      .replayRouteOptions(replayOpts)
      .build()
    return ReplayRouteSession().apply { setOptions(sessionOpts) }
  }

  private fun resolveLocale(): Locale {
    val tag = language?.takeIf { it.isNotBlank() } ?: return Locale.getDefault()
    return Locale.forLanguageTag(tag.replace('_', '-'))
  }

  private fun unitTypeFor(locale: Locale): UnitType = when (locale.country.uppercase()) {
    "US", "LR", "MM" -> UnitType.IMPERIAL
    else -> UnitType.METRIC
  }

  private fun ensureTripDataApis() {
    if (maneuverApi != null) return
    val locale = resolveLocale()
    val dfOpts = DistanceFormatterOptions.Builder(context)
      .locale(locale)
      .unitType(unitTypeFor(locale))
      .build()
    distanceFormatterOptions = dfOpts
    val distanceFormatter = MapboxDistanceFormatter(dfOpts)
    maneuverApi = MapboxManeuverApi(distanceFormatter, ManeuverOptions.Builder().build())
    maneuverBanner?.setDistanceFormatter(distanceFormatter)
    turnIconsApi = MapboxTurnIconsApi(TurnIconResources.Builder().build())
    val tripProgressFormatter = TripProgressUpdateFormatter.Builder(context)
      .distanceRemainingFormatter(DistanceRemainingFormatter(dfOpts))
      .timeRemainingFormatter(TimeRemainingFormatter(context))
      .percentRouteTraveledFormatter(PercentDistanceTraveledFormatter())
      .estimatedTimeOfArrivalFormatter(
        EstimatedTimeOfArrivalFormatter(context, TimeFormat.NONE_SPECIFIED)
      )
      .build()
    tripProgressApi = MapboxTripProgressApi(tripProgressFormatter)
    speedInfoApi = MapboxSpeedInfoApi()
  }

  private fun ensureMapboxSetup() {
    if (!MapboxNavigationApp.isSetup()) {
      MapboxNavigationApp.setup(
        NavigationOptions.Builder(context.applicationContext as Application).build()
      )
    }
    attachApplicationLifecycle()
  }

  private fun attachApplicationLifecycle() {
    // attachAllActivities() alone doesn't retro-trigger onAttached for an
    // already-RESUMED ReactActivity; bind directly to the current one too.
    (context.currentActivity as? LifecycleOwner)?.let {
      try { MapboxNavigationApp.attach(it) } catch (t: Throwable) {
        Log.w(TAG, "attach(activity) threw: ${t.message}")
      }
    }
    (context.applicationContext as? Application)?.let {
      try { MapboxNavigationApp.attachAllActivities(it) } catch (t: Throwable) {
        Log.w(TAG, "attachAllActivities threw: ${t.message}")
      }
    }
  }

  // ── Chrome mount ─────────────────────────────────────

  private fun mountUi() {
    if (mapView != null) return

    val banner = ManeuverBanner(context)
    container.addView(banner, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    ))
    maneuverBanner = banner
    // ensureTripDataApis() runs before mountUi(); the formatter is already
    // built. Wire it in so the banner uses the SDK rounding/locale.
    maneuverApi?.let {
      distanceFormatterOptions?.let { opts ->
        banner.setDistanceFormatter(MapboxDistanceFormatter(opts))
      }
    }

    val frame = FrameLayout(context).apply {
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
      )
    }
    val mv = MapView(context).apply {
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    }
    // Default Mapbox plugins overlap our chrome: the scale bar lives
    // top-left where our speed-limit badge mounts, and the compass sits
    // top-right under the ornament stack. iOS hides both — match it.
    mv.scalebar.enabled = false
    mv.compass.enabled = false
    frame.addView(mv)
    container.addView(frame)
    mapView = mv
    mapFrame = frame
    mountMapOverlays(frame)

    val initialStyle = when (colorScheme) {
      "dark" -> Style.DARK
      "light" -> Style.STANDARD
      else -> if (isSystemInDarkMode()) Style.DARK else Style.STANDARD
    }
    // Seed the camera so we don't flash the [0,0] world view while the
    // NavigationCamera transition warms up.
    mv.mapboxMap.setCamera(
      CameraOptions.Builder()
        .center(Point.fromLngLat(origin.longitude, origin.latitude))
        .zoom(15.5).pitch(0.0).bearing(0.0)
        .build()
    )
    mv.mapboxMap.loadStyle(initialStyle, Style.OnStyleLoaded { style ->
      loadedStyle = style
      mv.location.apply {
        locationPuck = createDefault2DPuck(withBearing = true)
        puckBearingEnabled = true
        setLocationProvider(navigationLocationProvider)
        enabled = true
      }
      mv.location.addOnIndicatorPositionChangedListener(indicatorPositionListener)

      val vds = MapboxNavigationViewportDataSource(mv.mapboxMap).apply {
        overviewPadding = edgeInsetsFor(topDp = 96, bottomDp = 160, sideDp = 40)
        followingPadding = edgeInsetsFor(topDp = 180, bottomDp = 220, sideDp = 40)
      }
      viewportDataSource = vds
      val cam = NavigationCamera(mv.mapboxMap, mv.camera, vds)
      navigationCamera = cam
      // NavigationCamera has no built-in gesture handling — a user pan
      // doesn't transition it to IDLE on its own, so the viewport data
      // source keeps yanking the camera back on every location tick.
      // Hook the map's gesture plugin and drop to IDLE on move-begin.
      mv.gestures.addOnMoveListener(object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) {
          navigationCamera?.requestNavigationCameraToIdle()
        }
        override fun onMove(detector: MoveGestureDetector): Boolean = false
        override fun onMoveEnd(detector: MoveGestureDetector) = Unit
      })
      // Show the Resume/Recenter pill when the camera is not following the
      // puck (user panned, or overview engaged); hide it while following.
      cam.registerNavigationCameraStateChangeObserver { state ->
        val rb = recenterButton ?: return@registerNavigationCameraStateChangeObserver
        // Use INVISIBLE rather than GONE for the hidden state: GONE removes
        // the view from layout, and React Native swallows the requestLayout
        // that setVisibility(VISIBLE) emits — so the view never gets a fresh
        // measure pass and stays at 0×0. INVISIBLE keeps it laid out (the
        // pill occupies a small fixed area off the map's visible content)
        // so toggling visibility is a pure invalidate, no layout needed.
        val hidden = hasArrivedAtDestination ||
          state == com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState.FOLLOWING ||
          state == com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState.TRANSITION_TO_FOLLOWING
        rb.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
      }

      val lineApiOpts = MapboxRouteLineApiOptions.Builder()
        .vanishingRouteLineEnabled(true)
        .build()
      // Standard style uses slot-based imports rather than the legacy
      // `road-label-navigation` anchor — pin the route line to the SDK's
      // "top" slot so it renders above the basemap road network.
      val lineViewOpts = MapboxRouteLineViewOptions.Builder(context)
        .slotName("top")
        .build()
      routeLineApi = MapboxRouteLineApi(lineApiOpts)
      routeLineView = MapboxRouteLineView(lineViewOpts).apply { initializeLayers(style) }
      pendingRoutes?.let { drawRouteLine(it); pendingRoutes = null }
    })

    val panel = TripPanel(context) {
      if (hasArrivedAtDestination) onNavigationEnd?.invoke()
      else onCancelNavigation?.invoke()
      detachNavigationUI()
    }
    container.addView(panel, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    ))
    tripPanel = panel

    fontFamily?.let {
      banner.applyFontFamily(it)
      panel.applyFontFamily(it)
    }
    pushPalette()
    applySystemBarInsets()
  }

  private fun mountMapOverlays(parent: FrameLayout) {
    val dp = { n: Int -> context.resources.dp(n) }

    val ornaments = OrnamentStack(
      context,
      onMute = {
        val audio = MapboxAudioGuidance.getRegisteredInstance()
        if (lastKnownMuted) audio.unmute() else audio.mute()
      }
    )
    parent.addView(ornaments, FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.WRAP_CONTENT,
      FrameLayout.LayoutParams.WRAP_CONTENT
    ).apply {
      gravity = Gravity.TOP or Gravity.END
      topMargin = dp(16)
      rightMargin = dp(16)
    })
    ornamentStack = ornaments

    // The SDK component renders MUTCD (US) or Vienna (EU) style based on
    // the SpeedLimitSign in each SpeedInfoValue. Wrap in the Mapbox theme
    // so the XML layout's `?attr/...` references resolve.
    val themedCtx = androidx.appcompat.view.ContextThemeWrapper(
      context, com.mapbox.navigation.ui.components.R.style.MapboxStyleSpeedLimit
    )
    val speed = com.mapbox.navigation.ui.components.speedlimit.view.MapboxSpeedInfoView(themedCtx)
    speed.applyOptions(
      com.mapbox.navigation.ui.components.speedlimit.model.MapboxSpeedInfoOptions.Builder()
        .showUnit(true)
        .showLegend(false)
        .showSpeedWhenUnavailable(false)
        .build()
    )
    // SDK view's <merge> layout has both inner ConstraintLayouts as GONE
    // until render() flips one. With WRAP_CONTENT the outer FrameLayout
    // measures as 0x0 on first attach and never recovers when a child
    // becomes VISIBLE. Force fixed dimensions sized for the MUTCD/Vienna
    // sign content (64dp inner posted layout + padding).
    parent.addView(speed, FrameLayout.LayoutParams(dp(76), dp(96)).apply {
      gravity = Gravity.TOP or Gravity.START
      topMargin = dp(16)
      leftMargin = dp(16)
    })
    speedLimitView = speed

    // Native Mapbox recenter pill — shown only when NavigationCamera is not
    // following (user panned or overview engaged). Inflated from XML so
    // the canonical `style="@style/MapboxStyleRecenterButton"` attribute
    // is honored, including the base `android:minHeight` / padding /
    // elevation from the extendable-button parent style. Code-only
    // construction goes through the single-arg ctor which skips the
    // SDK's `initAttributes` and produces a 0×0 view.
    val recenter = android.view.LayoutInflater.from(context).inflate(
      R.layout.mb_recenter_button, parent, false
    ) as com.mapbox.navigation.ui.components.maps.camera.view.MapboxRecenterButton
    recenter.setOnClickListener { recenterCamera() }
    parent.addView(recenter)
    recenterButton = recenter
  }

  private fun edgeInsetsFor(topDp: Int, bottomDp: Int, sideDp: Int): EdgeInsets {
    val d = context.resources.displayMetrics.density.toDouble()
    return EdgeInsets(topDp * d, sideDp * d, bottomDp * d, sideDp * d)
  }

  private fun applySystemBarInsets() {
    // RN consumes WindowInsets at its RootView, so neither
    // `setOnApplyWindowInsetsListener` nor `getRootWindowInsets` on our
    // container returns real values. The activity's decor view always
    // has the actual insets — query those directly.
    val activity = context.currentActivity ?: return
    val rootInsets = ViewCompat.getRootWindowInsets(activity.window.decorView) ?: return
    val sb = rootInsets.getInsets(WindowInsetsCompat.Type.systemBars())
    container.setPadding(0, sb.top, 0, 0)
    tripPanel?.applyBottomInset(sb.bottom)
  }

  // ── Precondition checks ─────────────────────────────

  private fun ensureLocationPermission(): Boolean {
    val granted = ContextCompat.checkSelfPermission(
      context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED
    if (granted) return true
    onError?.invoke(
      "GPS_PERMISSION_DENIED",
      "Location permission not granted. The app needs location permission to navigate."
    )
    return false
  }

  private fun ensureLocationAvailable(): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    val enabled = lm != null && (
      lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
      lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    )
    if (enabled) return true
    onError?.invoke(
      "GPS_UNAVAILABLE",
      "Location services are disabled on this device. Enable Location Services in Settings."
    )
    return false
  }

  private fun ensureCoordinatesValid(): Boolean {
    fun isValid(c: Coordinates): Boolean =
      c.latitude in -90.0..90.0 &&
      c.longitude in -180.0..180.0 &&
      !(c.latitude == 0.0 && c.longitude == 0.0)
    if (!isValid(origin) || !isValid(destination)) {
      onError?.invoke(
        "INVALID_COORDINATES",
        "Origin or destination is outside the valid lat/lon range or is the default (0, 0)."
      )
      return false
    }
    waypoints?.forEachIndexed { i, wp ->
      if (!isValid(wp.coordinate)) {
        onError?.invoke("INVALID_COORDINATES", "Waypoint #${i + 1} coordinate is invalid.")
        return false
      }
    }
    return true
  }

  // ── MapboxNavigation observer ───────────────────────

  private val mapboxNavigationObserver = object : MapboxNavigationObserver {
    override fun onAttached(mapboxNavigation: MapboxNavigation) {
      mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
      mapboxNavigation.registerLocationObserver(locationObserver)
      mapboxNavigation.registerArrivalObserver(arrivalObserver)
      mapboxNavigation.registerRoutesObserver(routesObserver)

      val audio = MapboxAudioGuidance.getRegisteredInstance()
      val initialMuted = mute == true
      lastKnownMuted = initialMuted
      // MapboxAudioGuidance restores its persisted mute state from a
      // DataStore inside its own onAttached, which runs AFTER ours and
      // overwrites any synchronous mute()/unmute() we'd call here. Drive
      // the desired state through the stateFlow collector instead: once
      // the SDK emits its restored state, reconcile it against the prop.
      var initialReconciled = false
      muteJob = scope.launch {
        audio.stateFlow().collect { state ->
          if (isShuttingDown) return@collect
          if (!initialReconciled) {
            initialReconciled = true
            if (state.isMuted != initialMuted) {
              if (initialMuted) audio.mute() else audio.unmute()
              return@collect
            }
          }
          ornamentStack?.setMuted(state.isMuted)
          if (state.isMuted != lastKnownMuted) {
            lastKnownMuted = state.isMuted
            onMuteChange?.invoke(state.isMuted)
          }
        }
      }

      requestRoute(mapboxNavigation)
      // Don't call applyColorScheme() here — it triggers a second
      // loadStyle that would wipe the LocationComponent + route-line
      // slot bindings established in mountUi.
      pushPalette()
    }

    override fun onDetached(mapboxNavigation: MapboxNavigation) {
      mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
      mapboxNavigation.unregisterLocationObserver(locationObserver)
      mapboxNavigation.unregisterArrivalObserver(arrivalObserver)
      mapboxNavigation.unregisterRoutesObserver(routesObserver)
    }
  }

  // ── Route request ────────────────────────────────────

  private fun requestRoute(mapboxNavigation: MapboxNavigation) {
    mapboxNavigation.requestRoutes(buildRouteOptions(), object : NavigationRouterCallback {
      override fun onRoutesReady(
        routes: List<NavigationRoute>,
        @Suppress("DEPRECATION") routerOrigin: String
      ) {
        if (isShuttingDown) return
        if (shouldSimulateRoute == true) {
          if (mapboxNavigation.getTripSessionState() != TripSessionState.STARTED) {
            mapboxNavigation.startReplayTripSession()
          }
        } else {
          mapboxNavigation.startTripSession()
        }
        mapboxNavigation.setNavigationRoutes(routes)
        if (loadedStyle != null) drawRouteLine(routes) else pendingRoutes = routes
        viewportDataSource?.onRouteChanged(routes.first())
        viewportDataSource?.evaluate()
        navigationCamera?.requestNavigationCameraToFollowing()
      }

      override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
        if (isShuttingDown) return
        val reason = reasons.firstOrNull()
        onError?.invoke(
          classifyRouterFailure(reason),
          reason?.message ?: "Route calculation failed."
        )
      }

      override fun onCanceled(
        routeOptions: RouteOptions,
        @Suppress("DEPRECATION") routerOrigin: String
      ) = Unit
    })
  }

  private fun buildRouteOptions(): RouteOptions {
    val points = mutableListOf<Point>()
    points.add(Point.fromLngLat(origin.longitude, origin.latitude))
    waypoints?.forEach { wp ->
      points.add(Point.fromLngLat(wp.coordinate.longitude, wp.coordinate.latitude))
    }
    points.add(Point.fromLngLat(destination.longitude, destination.latitude))

    val stopIndices = mutableListOf(0)
    waypoints?.forEachIndexed { i, wp ->
      if (wp.isSilent != true) stopIndices.add(i + 1)
    }
    stopIndices.add(points.size - 1)

    val builder = RouteOptions.builder()
      .applyDefaultNavigationOptions()
      .applyLanguageAndVoiceUnitOptions(context)
      .coordinatesList(points)
      .waypointIndicesList(stopIndices)
    language?.takeIf { it.isNotEmpty() }?.let { builder.language(it) }
    return builder.build()
  }

  private fun drawRouteLine(routes: List<NavigationRoute>) {
    val style = loadedStyle ?: return
    routeLineApi?.setNavigationRoutes(routes) { expected ->
      routeLineView?.renderRouteDrawData(style, expected)
    }
  }

  private val indicatorPositionListener = OnIndicatorPositionChangedListener { point ->
    val style = loadedStyle ?: return@OnIndicatorPositionChangedListener
    val api = routeLineApi ?: return@OnIndicatorPositionChangedListener
    val view = routeLineView ?: return@OnIndicatorPositionChangedListener
    view.renderRouteLineUpdate(style, api.updateTraveledRouteLine(point))
  }

  private fun classifyRouterFailure(reason: RouterFailure?): String {
    val msg = reason?.message.orEmpty().lowercase()
    return when {
      msg.contains("network") || msg.contains("timeout") || msg.contains("connection") -> "NETWORK_ERROR"
      msg.contains("auth") || msg.contains("401") || msg.contains("403") -> "SDK_INIT_FAILED"
      msg.contains("input") || msg.contains("invalid") -> "INVALID_COORDINATES"
      else -> "ROUTE_CALCULATION_FAILED"
    }
  }

  // ── Observers ────────────────────────────────────────

  private val routeProgressObserver = RouteProgressObserver { progress ->
    if (isShuttingDown) return@RouteProgressObserver
    // After arrival, stop feeding the viewport data source so NavigationCamera
    // settles instead of fighting our easeTo / re-framing every tick.
    if (!hasArrivedAtDestination) {
      viewportDataSource?.onRouteProgressChanged(progress)
      viewportDataSource?.evaluate()
    }
    routeLineApi?.updateWithRouteProgress(progress) { expected ->
      loadedStyle?.let { routeLineView?.renderRouteLineUpdate(it, expected) }
    }
    if (!hasArrivedAtDestination) {
      val first = maneuverApi?.getManeuvers(progress)?.value?.firstOrNull()
      val turnIcon = first?.primary?.let { p ->
        turnIconsApi?.generateTurnIcon(p.type, p.degrees?.toFloat(), p.modifier, p.drivingSide)?.value
      }
      maneuverBanner?.update(progress, first, turnIcon)
      tripPanel?.update(progress, tripProgressApi?.getTripProgress(progress))
    }

    if (hasArrivedAtDestination) return@RouteProgressObserver
    val now = SystemClock.elapsedRealtime()
    if (now - lastProgressEmitMs < jsEventMinIntervalMs) return@RouteProgressObserver
    lastProgressEmitMs = now
    onRouteProgressChange?.invoke(RouteProgress(
      distanceTraveled = progress.distanceTraveled.toDouble(),
      distanceRemaining = progress.distanceRemaining.toDouble(),
      durationRemaining = progress.durationRemaining,
      fractionTraveled = progress.fractionTraveled.toDouble()
    ))
  }

  private val locationObserver = object : LocationObserver {
    override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) = Unit
    override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
      if (isShuttingDown) return
      val dfo = distanceFormatterOptions
      if (dfo != null) {
        val si = speedInfoApi?.updatePostedAndCurrentSpeed(locationMatcherResult, dfo)
        val slv = speedLimitView
        if (si != null && slv != null) {
          slv.render(si)
          // The SDK overlays the current speed in red below the posted
          // limit when over-speeding. iOS doesn't, so hide both to match.
          slv.speedInfoCurrentSpeedVienna.visibility = android.view.View.GONE
          slv.speedInfoCurrentSpeedMutcd.visibility = android.view.View.GONE
          // The SDK's render() flips the active MUTCD/Vienna child to
          // VISIBLE, but inside RN's view tree the requestLayout from
          // that visibility change does not propagate back through our
          // host, so the active layout stays at 0x0. Force a synchronous
          // measure+layout matching the parent-assigned dimensions.
          val w = slv.width
          val h = slv.height
          if (w > 0 && h > 0) {
            slv.measure(
              android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
              android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY)
            )
            slv.layout(slv.left, slv.top, slv.left + w, slv.top + h)
          }
        }
      }
      val loc = locationMatcherResult.enhancedLocation
      navigationLocationProvider.changePosition(
        location = loc,
        keyPoints = locationMatcherResult.keyPoints
      )
      // Freeze camera work after arrival: any further evaluate() here races
      // NavigationCamera's natural post-arrival framing and produces a
      // visible oscillation between top-down and angled following.
      if (hasArrivedAtDestination) return
      viewportDataSource?.onLocationChanged(loc)
      viewportDataSource?.evaluate()
      if (!firstLocationReceived) {
        firstLocationReceived = true
        navigationCamera?.requestNavigationCameraToFollowing()
        // One-shot belt-and-braces: on first activation NavigationCamera
        // occasionally stays at the seed zoom. Ease into a sensible
        // following pose just once — after this the user may pan freely
        // and the Resume pill takes over re-engaging following.
        mapView?.camera?.easeTo(
          CameraOptions.Builder()
            .center(Point.fromLngLat(loc.longitude, loc.latitude))
            .zoom(16.5).pitch(45.0)
            .bearing(loc.bearing?.toDouble() ?: 0.0)
            .build(),
          MapAnimationOptions.mapAnimationOptions { duration(400) }
        )
      }

      val lat = loc.latitude
      val lon = loc.longitude
      val prevLat = lastLat
      val prevLon = lastLon
      if (prevLat != null && prevLon != null &&
        haversine(prevLat, prevLon, lat, lon) < locationEpsilonMeters) return
      val now = SystemClock.elapsedRealtime()
      if (now - lastLocationEmitMs < jsEventMinIntervalMs) return
      lastLocationEmitMs = now
      lastLat = lat
      lastLon = lon
      onLocationChange?.invoke(lat, lon)
    }
  }

  private val arrivalObserver = object : ArrivalObserver {
    override fun onWaypointArrival(
      routeProgress: com.mapbox.navigation.base.trip.model.RouteProgress
    ) = Unit
    override fun onNextRouteLegStart(
      routeLegProgress: com.mapbox.navigation.base.trip.model.RouteLegProgress
    ) = Unit
    override fun onFinalDestinationArrival(
      routeProgress: com.mapbox.navigation.base.trip.model.RouteProgress
    ) {
      if (isShuttingDown || hasArrivedAtDestination) return
      hasArrivedAtDestination = true
      val totalDistance = routeProgress.navigationRoute.directionsRoute.distance() ?: 0.0
      onRouteProgressChange?.invoke(RouteProgress(
        distanceTraveled = totalDistance,
        distanceRemaining = 0.0,
        durationRemaining = 0.0,
        fractionTraveled = 1.0
      ))
      onArrive?.invoke(Coordinates(destination.latitude, destination.longitude))
      maneuverBanner?.showArrived()
      tripPanel?.showArrived()
    }
  }

  private val routesObserver = RoutesObserver { result ->
    if (isShuttingDown) return@RoutesObserver
    if (result.reason == RoutesExtra.ROUTES_UPDATE_REASON_REROUTE) onReroute?.invoke()
  }

  // ── Mute / colorScheme ──────────────────────────────

  private fun applyMute(value: Boolean) {
    if (!MapboxNavigationApp.isSetup()) return
    val audio = MapboxAudioGuidance.getRegisteredInstance()
    if (value) audio.mute() else audio.unmute()
  }

  private fun applyColorScheme() {
    val mode = when (colorScheme) {
      "light" -> AppCompatDelegate.MODE_NIGHT_NO
      "dark" -> AppCompatDelegate.MODE_NIGHT_YES
      else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
    AppCompatDelegate.setDefaultNightMode(mode)
    pushPalette()
    val style = if (resolveDark()) Style.DARK else Style.STANDARD
    mapView?.mapboxMap?.loadStyle(style)
  }

  private fun resolveDark(): Boolean = when (colorScheme) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkMode()
  }

  private fun isSystemInDarkMode(): Boolean {
    val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return night == Configuration.UI_MODE_NIGHT_YES
  }

  private fun resolvePalette(): ChromePalette =
    if (resolveDark()) ChromePalette.DARK else ChromePalette.LIGHT

  private fun pushPalette() {
    currentPalette = resolvePalette()
    // The library renders fullscreen / edge-to-edge: the container's top
    // padding sits under the status bar and its background shows through the
    // trip panel's rounded top corners + bottom inset. Tint it with the
    // chrome surface color (bannerBg == dockBg in both palettes) so those
    // exposed strips match the banner/dock instead of flashing the white RN
    // root. The weight-1 map frame covers the middle, so this only paints
    // those edges.
    container.setBackgroundColor(currentPalette.dockBg)
    maneuverBanner?.applyPalette(currentPalette)
    tripPanel?.applyPalette(currentPalette)
    // MapboxSpeedInfoView handles its own styling via the SDK theme.
    ornamentStack?.applyPalette(currentPalette)
    applyStatusBarAppearance()
  }

  /**
   * Match the system status-bar icon tint to the scheme: dark icons on the
   * light surface, light icons on the dark surface. Without this the icons
   * keep their default (dark) appearance and disappear against the dark
   * status-bar band painted by the container background in dark mode.
   */
  private fun applyStatusBarAppearance() {
    val window = context.currentActivity?.window ?: return
    WindowInsetsControllerCompat(window, window.decorView)
      .isAppearanceLightStatusBars = !resolveDark()
  }

  // ── Helpers ──────────────────────────────────────────

  private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2).let { it * it } +
      Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
      Math.sin(dLon / 2).let { it * it }
    return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  }
}
