package ch.jls.reactnative.mapboxnavigation

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnDetach
import androidx.fragment.app.Fragment
import ch.jls.reactnative.mapboxnavigation.databinding.NavigationViewBinding
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Expected
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.TimeFormat
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.internal.extensions.inferDeviceLocale
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.trip.model.RouteLegProgress
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.progress.api.MapboxTripProgressApi
import com.mapbox.navigation.tripdata.progress.model.DistanceRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.EstimatedTimeToArrivalFormatter
import com.mapbox.navigation.tripdata.progress.model.PercentDistanceTraveledFormatter
import com.mapbox.navigation.tripdata.progress.model.TimeRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateFormatter
import com.mapbox.navigation.ui.base.util.MapboxNavigationConsumer
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.components.tripprogress.view.MapboxTripProgressView
import com.mapbox.navigation.ui.maps.NavigationStyles
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.camera.transition.NavigationCameraTransitionOptions
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.voice.api.MapboxSpeechApi
import com.mapbox.navigation.voice.api.MapboxVoiceInstructionsPlayer
import com.mapbox.navigation.voice.model.SpeechAnnouncement
import com.mapbox.navigation.voice.model.SpeechError
import com.mapbox.navigation.voice.model.SpeechValue
import com.mapbox.navigation.voice.model.SpeechVolume
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
@SuppressLint("ViewConstructor")
class MapboxNavigationFragment(
  private val context: ReactApplicationContext
) : Fragment() {


  private companion object {
    private const val BUTTON_ANIMATION_DURATION = 1500L
  }

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
   * Debug observer that makes sure the replayer has always an up-to-date information to generate mock updates.
   */
  private lateinit var replayProgressObserver: ReplayProgressObserver

  /**
   * Debug object that converts a route into events that can be replayed to navigate a route.
   */
  private val replayRouteMapper = ReplayRouteMapper()

  /**
   * Bindings to the example layout.
   */
  private lateinit var binding: NavigationViewBinding

  /**
   * Used to execute camera transitions based on the data generated by the [viewportDataSource].
   * This includes transitions from route overview to route following and continuously updating the camera as the location changes.
   */
  private lateinit var navigationCamera: NavigationCamera

  /**
   * Produces the camera frames based on the location and routing data for the [navigationCamera] to execute.
   */
  private lateinit var viewportDataSource: MapboxNavigationViewportDataSource

  /*
   * Below are generated camera padding values to ensure that the route fits well on screen while
   * other elements are overlaid on top of the map (including instruction view, buttons, etc.)
   */
  private val pixelDensity = Resources.getSystem().displayMetrics.density
  private val overviewPadding: EdgeInsets by lazy {
    EdgeInsets(
      140.0 * pixelDensity, 40.0 * pixelDensity, 120.0 * pixelDensity, 40.0 * pixelDensity
    )
  }
  private val landscapeOverviewPadding: EdgeInsets by lazy {
    EdgeInsets(
      30.0 * pixelDensity, 380.0 * pixelDensity, 110.0 * pixelDensity, 20.0 * pixelDensity
    )
  }
  private val followingPadding: EdgeInsets by lazy {
    EdgeInsets(
      180.0 * pixelDensity, 40.0 * pixelDensity, 150.0 * pixelDensity, 40.0 * pixelDensity
    )
  }
  private val landscapeFollowingPadding: EdgeInsets by lazy {
    EdgeInsets(
      30.0 * pixelDensity, 380.0 * pixelDensity, 110.0 * pixelDensity, 40.0 * pixelDensity
    )
  }

  /**
   * Generates updates for the [MapboxManeuverView] to display the upcoming maneuver instructions
   * and remaining distance to the maneuver point.
   */
  private lateinit var maneuverApi: MapboxManeuverApi

  /**
   * Generates updates for the [MapboxTripProgressView] that include remaining time and distance to the destination.
   */
  private lateinit var tripProgressApi: MapboxTripProgressApi

  /**
   * Generates updates for the [routeLineView] with the geometries and properties of the routes that should be drawn on the map.
   */
  private lateinit var routeLineApi: MapboxRouteLineApi

  /**
   * Draws route lines on the map based on the data from the [routeLineApi]
   */
  private lateinit var routeLineView: MapboxRouteLineView

  /**
   * Generates updates for the [routeArrowView] with the geometries and properties of maneuver arrows that should be drawn on the map.
   */
  private val routeArrowApi: MapboxRouteArrowApi = MapboxRouteArrowApi()

  /**
   * Draws maneuver arrows on the map based on the data [routeArrowApi].
   */
  private lateinit var routeArrowView: MapboxRouteArrowView

  /**
   * Stores and updates the state of whether the voice instructions should be played as they come or muted.
   */
  private var isVoiceInstructionsMuted = false
    set(value) {
      field = value
      if (value) {
        if (this::binding.isInitialized) {
          binding.soundButton.muteAndExtend(BUTTON_ANIMATION_DURATION)
        }
        if (this::voiceInstructionsPlayer.isInitialized) {
          voiceInstructionsPlayer.volume(SpeechVolume(0f))
        }
      } else {
        if (this::binding.isInitialized) {
          binding.soundButton.unmuteAndExtend(BUTTON_ANIMATION_DURATION)
        }
        if (this::voiceInstructionsPlayer.isInitialized) {
          voiceInstructionsPlayer.volume(SpeechVolume(1f))
        }
      }
    }

  /**
   * Extracts message that should be communicated to the driver about the upcoming maneuver.
   * When possible, downloads a synthesized audio file that can be played back to the driver.
   */
  private lateinit var speechApi: MapboxSpeechApi

  /**
   * Plays the synthesized audio files with upcoming maneuver instructions
   * or uses an on-device Text-To-Speech engine to communicate the message to the driver.
   * NOTE: do not use lazy initialization for this class since it takes some time to initialize
   * the system services required for on-device speech synthesis. With lazy initialization
   * there is a high risk that said services will not be available when the first instruction
   * has to be played. [MapboxVoiceInstructionsPlayer] should be instantiated in
   * `Activity#onCreate`.
   */
  private lateinit var voiceInstructionsPlayer: MapboxVoiceInstructionsPlayer

  /**
   * Observes when a new voice instruction should be played.
   */
  private val voiceInstructionsObserver = VoiceInstructionsObserver { voiceInstructions ->
    speechApi.generate(voiceInstructions, speechCallback)
  }

  /**
   * Based on whether the synthesized audio file is available, the callback plays the file
   * or uses the fall back which is played back using the on-device Text-To-Speech engine.
   */
  private val speechCallback =
    MapboxNavigationConsumer<Expected<SpeechError, SpeechValue>> { expected ->
      expected.fold({ error ->
        Log.d("MBNavFragment", "Speech callback error: ${error.errorMessage}")
        // play the instruction via fallback text-to-speech engine
        voiceInstructionsPlayer.play(
          error.fallback, voiceInstructionsPlayerCallback
        )
      }, { value ->
        Log.d("MBNavFragment", "Speech callback value: ${value.announcement}")
        // play the sound file from the external generator
        voiceInstructionsPlayer.play(
          value.announcement, voiceInstructionsPlayerCallback
        )
      })
    }

  /**
   * When a synthesized audio file was downloaded, this callback cleans up the disk after it was played.
   */
  private val voiceInstructionsPlayerCallback =
    MapboxNavigationConsumer<SpeechAnnouncement> { value ->
      // remove already consumed file to free-up space
      speechApi.clean(value)
    }

  /**
   * [NavigationLocationProvider] is a utility class that helps to provide location updates generated by the Navigation SDK
   * to the Maps SDK in order to update the user location indicator on the map.
   */
  private val navigationLocationProvider = NavigationLocationProvider()

  /**
   * Gets notified with location updates.
   *
   * Exposes raw updates coming directly from the location services
   * and the updates enhanced by the Navigation SDK (cleaned up and matched to the road).
   */
  private val locationObserver = object : LocationObserver {
    var firstLocationUpdateReceived = false

    override fun onNewRawLocation(rawLocation: Location) {
      // not handled
    }

    override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
      Log.v(
        "MBNavFragment",
        "onNewLocationMatcherResult: ${locationMatcherResult.enhancedLocation.latitude}, ${locationMatcherResult.enhancedLocation.longitude}"
      )
      val enhancedLocation = locationMatcherResult.enhancedLocation
      // update location puck's position on the map
      navigationLocationProvider.changePosition(
        location = enhancedLocation,
        keyPoints = locationMatcherResult.keyPoints,
      )

      // update camera position to account for new location
      viewportDataSource.onLocationChanged(enhancedLocation)
      viewportDataSource.evaluate()

      // if this is the first location update the activity has received,
      // it's best to immediately move the camera to the current user location
      if (!firstLocationUpdateReceived) {
        Log.d("MBNavFragment", "First location update received, initializing navigation")
        firstLocationUpdateReceived = true
        navigationCamera.requestNavigationCameraToFollowing(
          stateTransitionOptions = NavigationCameraTransitionOptions.Builder()
            .maxDuration(0) // instant transition
            .build()
          )
        this@MapboxNavigationFragment.findRoute()
      }
    }
  }

  /**
   * Gets notified with progress along the currently active route.
   */
  private val routeProgressObserver = RouteProgressObserver { routeProgress ->
    // update the camera position to account for the progressed fragment of the route
    viewportDataSource.onRouteProgressChanged(routeProgress)
    viewportDataSource.evaluate()

    // draw the upcoming maneuver arrow on the map
    val style = binding.mapView.mapboxMap.style
    if (style != null) {
      val maneuverArrowResult = routeArrowApi.addUpcomingManeuverArrow(routeProgress)
      routeArrowView.renderManeuverUpdate(style, maneuverArrowResult)
    }

    // update top banner with maneuver instructions
    val maneuvers = maneuverApi.getManeuvers(routeProgress)
    maneuvers.fold({ error ->
      sendErrorToReact(error.errorMessage)
    }, {
      binding.maneuverView.visibility = View.VISIBLE
      binding.maneuverView.renderManeuvers(maneuvers)
    })

    val params: WritableMap = Arguments.createMap()
    params.putDouble("distanceTraveled", routeProgress.distanceTraveled.toDouble())
    params.putDouble("durationRemaining", routeProgress.durationRemaining)
    params.putDouble("fractionTraveled", routeProgress.fractionTraveled.toDouble())
    params.putDouble("distanceRemaining", routeProgress.distanceRemaining.toDouble())
    sendEventToReactNative("onRouteProgressChange", params)

    val tripProgress = tripProgressApi.getTripProgress(routeProgress)
    val timeRemainingString = tripProgressApi.formatter.getTimeRemaining(tripProgress.totalTimeRemaining)
    val distanceRemainingString = tripProgressApi.formatter.getDistanceRemaining(tripProgress.distanceRemaining)
    val arrivalTimeString = tripProgressApi.formatter.getEstimatedTimeToArrival(tripProgress.estimatedTimeToArrival)
    binding.timeRemainingText.text = timeRemainingString
    binding.distanceRemainingText.text = distanceRemainingString
    binding.arrivalTimeText.text = arrivalTimeString
  }

  /**
   * Gets notified when the user arrives at the final destination.
   */
  private val arrivalObserver: ArrivalObserver = object : ArrivalObserver {
    override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
      val parameters = Arguments.createMap()
      parameters.putString("onArrive", "")
      sendEventToReactNative("onArrive", parameters)
    }

    override fun onNextRouteLegStart(routeLegProgress: RouteLegProgress) {
      // Do something when the user starts a new leg
    }

    override fun onWaypointArrival(routeProgress: RouteProgress) {
      // Do something when the user arrives at a waypoint
    }
  }

  /**
   * Gets notified whenever the tracked routes change.
   *
   * A change can mean:
   * - routes get changed with [MapboxNavigation.setNavigationRoutes]
   * - routes annotations get refreshed (for example, congestion annotation that indicate the live traffic along the route)
   * - driver got off route and a reroute was executed
   */
  private val routesObserver = RoutesObserver { routeUpdateResult ->
    if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
      // generate route geometries asynchronously and render them
      routeLineApi.setNavigationRoutes(
        routeUpdateResult.navigationRoutes
      ) { value ->
        binding.mapView.mapboxMap.style?.apply {
          routeLineView.renderRouteDrawData(this, value)
        }
      }

      // update the camera position to account for the new route
      viewportDataSource.onRouteChanged(routeUpdateResult.navigationRoutes.first())
      viewportDataSource.evaluate()
    } else {
      // remove the route line and route arrow from the map
      val style = binding.mapView.mapboxMap.style
      if (style != null) {
        routeLineApi.clearRouteLine { value ->
          routeLineView.renderClearRouteLineValue(
            style, value
          )
        }
        routeArrowView.render(style, routeArrowApi.clearArrows())
      }

      // remove the route reference from camera position evaluations
      viewportDataSource.clearRouteData()
      viewportDataSource.evaluate()
    }
  }

  private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
    onResumedObserver = object : MapboxNavigationObserver {
      @SuppressLint("MissingPermission")
      override fun onAttached(mapboxNavigation: MapboxNavigation) {
        Log.d("MBNavFragment", "mapboxNavigation - onAttached")
        mapboxNavigation.registerRoutesObserver(routesObserver)
        mapboxNavigation.registerLocationObserver(locationObserver)
        mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.registerVoiceInstructionsObserver(voiceInstructionsObserver)
        mapboxNavigation.registerArrivalObserver(arrivalObserver)

        replayProgressObserver = ReplayProgressObserver(mapboxNavigation.mapboxReplayer)
        mapboxNavigation.registerRouteProgressObserver(replayProgressObserver)

        // Start the trip session to being receiving location updates in free drive
        // and later when a route is set also receiving route progress updates.
        // In case of `startReplayTripSession`,
        // location events are emitted by the `MapboxReplayer`
        if (this@MapboxNavigationFragment.shouldSimulateRoute) {
          Log.d("MBNavFragment", "mapboxNavigation - starting replay trip session")
          mapboxNavigation.startReplayTripSession()
        } else {
          Log.d("MBNavFragment", "mapboxNavigation - starting trip session")
          mapboxNavigation.startTripSession()
        }
      }

      override fun onDetached(mapboxNavigation: MapboxNavigation) {
        Log.d("MBNavFragment", "mapboxNavigation - onDetached")
        mapboxNavigation.unregisterRoutesObserver(routesObserver)
        mapboxNavigation.unregisterLocationObserver(locationObserver)
        mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.unregisterRouteProgressObserver(replayProgressObserver)
        mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
        mapboxNavigation.unregisterArrivalObserver(arrivalObserver)
        mapboxNavigation.mapboxReplayer.finish()
      }
    }
  )

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View {
    super.onCreateView(inflater, container, savedInstanceState)
    Log.d("MBNavFragment", "onCreateView")
    binding = NavigationViewBinding.inflate(layoutInflater)
    Log.d("MBNavFragment", "Binding: $binding")
    return binding.root
  }

  @SuppressLint("MissingPermission")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    this.view?.doOnDetach { Log.d("MBNavFragment", "View is detaching") }
    Log.d("MBNavFragment", "onViewCreated")

    // initialize Navigation Camera
    viewportDataSource = MapboxNavigationViewportDataSource(binding.mapView.mapboxMap)
    navigationCamera = NavigationCamera(
      binding.mapView.mapboxMap, binding.mapView.camera, viewportDataSource
    )
    // set the animations lifecycle listener to ensure the NavigationCamera stops
    // automatically following the user location when the map is interacted with
    binding.mapView.camera.addCameraAnimationsLifecycleListener(
      NavigationBasicGesturesHandler(navigationCamera)
    )
    navigationCamera.registerNavigationCameraStateChangeObserver { navigationCameraState ->
      // shows/hide the recenter button depending on the camera state
      when (navigationCameraState) {
        NavigationCameraState.TRANSITION_TO_FOLLOWING, NavigationCameraState.FOLLOWING -> binding.recenter.visibility =
          View.INVISIBLE

        NavigationCameraState.TRANSITION_TO_OVERVIEW, NavigationCameraState.OVERVIEW, NavigationCameraState.IDLE -> binding.recenter.visibility =
          View.VISIBLE
      }
    }
    // set the padding values depending on screen orientation and visible view layout
    if (this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      viewportDataSource.overviewPadding = landscapeOverviewPadding
    } else {
      viewportDataSource.overviewPadding = overviewPadding
    }
    if (this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      viewportDataSource.followingPadding = landscapeFollowingPadding
    } else {
      viewportDataSource.followingPadding = followingPadding
    }

    // make sure to use the same DistanceFormatterOptions across different features
    val distanceFormatterOptions = DistanceFormatterOptions.Builder(this.context).build()

    // initialize maneuver api that feeds the data to the top banner maneuver view
    maneuverApi = MapboxManeuverApi(
      MapboxDistanceFormatter(distanceFormatterOptions)
    )

    // initialize bottom progress view
    tripProgressApi = MapboxTripProgressApi(
      TripProgressUpdateFormatter.Builder(this.context).distanceRemainingFormatter(
        DistanceRemainingFormatter(distanceFormatterOptions)
      ).timeRemainingFormatter(
        TimeRemainingFormatter(this.context)
      ).percentRouteTraveledFormatter(
        PercentDistanceTraveledFormatter()
      ).estimatedTimeToArrivalFormatter(
        EstimatedTimeToArrivalFormatter(this.context, TimeFormat.NONE_SPECIFIED)
      ).build()
    )

    // initialize voice instructions api and the voice instruction player
    speechApi = MapboxSpeechApi(
      this.context, this.context.inferDeviceLocale().toString()
    )
    voiceInstructionsPlayer = MapboxVoiceInstructionsPlayer(
      this.context, this.context.inferDeviceLocale().toString()
    )
    if (this.isVoiceInstructionsMuted) {
      voiceInstructionsPlayer.volume(SpeechVolume(0f))
    } else {
      voiceInstructionsPlayer.volume(SpeechVolume(1f))
    }

    // initialize route line, the routeLineBelowLayerId is specified to place
    // the route line below road labels layer on the map
    // the value of this option will depend on the style that you are using
    // and under which layer the route line should be placed on the map layers stack
    val mapboxRouteLineViewOptions = MapboxRouteLineViewOptions.Builder(this.context)
      .routeLineBelowLayerId("road-label-navigation").build()

    routeLineApi = MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build())
    routeLineView = MapboxRouteLineView(mapboxRouteLineViewOptions)

    // initialize maneuver arrow view to draw arrows on the map
    val routeArrowOptions = RouteArrowOptions.Builder(this.context).build()
    routeArrowView = MapboxRouteArrowView(routeArrowOptions)

    // load map style
    binding.mapView.mapboxMap.loadStyle(NavigationStyles.NAVIGATION_DAY_STYLE) {
      // Ensure that the route line related layers are present before the route arrow
      routeLineView.initializeLayers(it)
    }

    // initialize view interactions
    binding.stop.setOnClickListener {
      val params = Arguments.createMap()
      params.putString("onCancelNavigation", "Navigation Closed")
      sendEventToReactNative("onCancelNavigation", params)
    }
    binding.recenter.setOnClickListener {
      navigationCamera.requestNavigationCameraToFollowing()
      binding.routeOverview.showTextAndExtend(BUTTON_ANIMATION_DURATION)
    }
    binding.routeOverview.setOnClickListener {
      navigationCamera.requestNavigationCameraToOverview()
      binding.recenter.showTextAndExtend(BUTTON_ANIMATION_DURATION)
    }
    binding.soundButton.setOnClickListener {
      // mute/unmute voice instructions
      isVoiceInstructionsMuted = !isVoiceInstructionsMuted

      val parameters = Arguments.createMap()
      parameters.putBoolean("isMuted", isVoiceInstructionsMuted)
      sendEventToReactNative("onMuteChange", parameters)
    }
    // Set initial state of the sound button
    if (this.isVoiceInstructionsMuted) {
      binding.soundButton.muteAndExtend(BUTTON_ANIMATION_DURATION)
    } else {
      binding.soundButton.unmuteAndExtend(BUTTON_ANIMATION_DURATION)
    }

    this.initNavigation()
  }

  override fun onStop() {
    super.onStop()
    Log.d("MBNavFragment", "onStop")
  }

  override fun onResume() {
    super.onResume()
    Log.d("MBNavFragment", "onResume")
  }

  override fun onDestroyView() {
    super.onDestroyView()
    Log.d("MBNavFragment", "onDestroyView")
    this.clearRouteAndStopNavigation()
  }

  override fun onPause() {
    super.onPause()
    Log.d("MBNavFragment", "onPause")
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.d("MBNavFragment", "onDestroy")
    maneuverApi.cancel()
    routeLineApi.cancel()
    routeLineView.cancel()
    speechApi.cancel()
    voiceInstructionsPlayer.shutdown()
  }

  private fun initNavigation() {
    Log.d("MBNavFragment", "initNavigation")
    MapboxNavigationApp.setup(
      NavigationOptions.Builder(this.context).build()
    )

    // initialize location puck
    binding.mapView.location.apply {
      setLocationProvider(navigationLocationProvider)
      this.locationPuck = LocationPuck2D(
        bearingImage = ImageHolder.Companion.from(
          com.mapbox.navigation.R.drawable.mapbox_navigation_puck_icon
        )
      )
      puckBearingEnabled = true
      enabled = true
    }

    if (this.shouldSimulateRoute) {
      setupSimulationOrigin()
    }
  }

  private fun setupSimulationOrigin() {
    Log.d("MBNavFragment", "setupSimulationOrigin")
    if (this.origin == null) {
      Log.e("MBNavFragment", "origin is required when shouldSimulateRoute is true")
      return
    }
    with(mapboxNavigation.mapboxReplayer) {
      stop()
      clearEvents()
      resetWaypoints()
      play()
      pushEvents(
        listOf(
          ReplayRouteMapper.mapToUpdateLocation(
            Date().time.toDouble(),
            Point.fromLngLat(
              this@MapboxNavigationFragment.origin!!.longitude(),
              this@MapboxNavigationFragment.origin!!.latitude()
            )
          )
        )
      )
      playFirstLocation()
    }
  }

  private fun findRoute() {
    Log.d("MBNavFragment", "findRoute, lastLocation: ${navigationLocationProvider.lastLocation}")
    val originLocation = navigationLocationProvider.lastLocation ?: return
    val originPoint = Point.fromLngLat(originLocation.longitude, originLocation.latitude)
    if (this.destination == null) {
      sendErrorToReact("Destination is required")
      return
    }
    val route: List<Point> = listOf(originPoint) + waypoints + this.destination!!
    Log.d("MBNavFragment", "$route")
    // execute a route request
    // it's recommended to use the
    // applyDefaultNavigationOptions and applyLanguageAndVoiceUnitOptions
    // that make sure the route request is optimized
    // to allow for support of all of the Navigation SDK features
    mapboxNavigation.requestRoutes(
      RouteOptions.builder()
        .applyDefaultNavigationOptions()
        .applyLanguageAndVoiceUnitOptions(this.context)
        .coordinatesList(route)
        .waypointIndicesList(listOf(0, route.size - 1))
        .profile(DirectionsCriteria.PROFILE_DRIVING)
        .enableRefresh(false)
        .steps(true)
        .build(),
      object : NavigationRouterCallback {
        override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
          Log.e(
            "MBNavFragment",
            "findRoute: onCanceled, routeOptions: $routeOptions, routerOrigin: $routerOrigin"
          )
        }

        override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
          Log.e(
            "MBNavFragment",
            "findRoute: onFailure, reasons: $reasons, routeOptions: $routeOptions"
          )
        }

        override fun onRoutesReady(
          routes: List<NavigationRoute>,
          routerOrigin: String
        ) {
          Log.d("MBNavFragment", "onRoutesReady")
          setRouteAndStartNavigation(routes)
        }
      }
    )
  }

  private fun setRouteAndStartNavigation(routes: List<NavigationRoute>) {
    // set routes, where the first route in the list is the primary route that
    // will be used for active guidance
    mapboxNavigation.setNavigationRoutes(routes)

    // show UI elements
    binding.soundButton.visibility = View.VISIBLE
    binding.routeOverview.visibility = View.VISIBLE
    binding.tripProgressCard.visibility = View.VISIBLE

    // move the camera to overview when new route is available
    navigationCamera.requestNavigationCameraToFollowing(
      stateTransitionOptions = NavigationCameraTransitionOptions.Builder()
        .maxDuration(0) // instant transition
        .build()
    )

    if (this.shouldSimulateRoute) {
      // start simulation
      startSimulation(routes.first().directionsRoute)
    }
  }

  private fun clearRouteAndStopNavigation() {
    // clear
    mapboxNavigation.setNavigationRoutes(listOf())

    // stop simulation
    stopSimulation()

    // hide UI elements
    binding.soundButton.visibility = View.INVISIBLE
    binding.maneuverView.visibility = View.INVISIBLE
    binding.routeOverview.visibility = View.INVISIBLE
    binding.tripProgressCard.visibility = View.INVISIBLE
  }

  private fun startSimulation(route: DirectionsRoute) {
    mapboxNavigation.mapboxReplayer.stop()
    mapboxNavigation.mapboxReplayer.clearEvents()
    val replayData = replayRouteMapper.mapDirectionsRouteGeometry(route)
    mapboxNavigation.mapboxReplayer.pushEvents(replayData)
    mapboxNavigation.mapboxReplayer.seekTo(replayData[0])
    mapboxNavigation.mapboxReplayer.play()
  }

  private fun stopSimulation() {
    mapboxNavigation.mapboxReplayer.stop()
    mapboxNavigation.mapboxReplayer.clearEvents()
  }

  // Setters for react native driven properties

  fun setDestination(destination: Point?) {
    this.destination = destination
    if (destination == null) {
      Log.w("MBNavFragment", "destination set to null")
      return
    }
    Log.d(
      "MapboxNavigation",
      "destination set to ${destination.latitude()}, ${destination.longitude()}"
    )
  }

  fun setOrigin(origin: Point?) {
    this.origin = origin
    if (origin == null) {
      Log.d("MBNavFragment", "simulationOrigin set to null")
      return
    }
    Log.d(
      "MapboxNavigation",
      "simulationOrigin set to ${origin.latitude()}, ${origin.longitude()}"
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
    this.isVoiceInstructionsMuted = mute
  }

  private fun sendErrorToReact(error: String?) {
    val eventParameters = Arguments.createMap()
    eventParameters.putString("error", error)
    if (error != null) {
      Log.e("MBNavFragment", error)
    }
    this.sendEventToReactNative("onError", eventParameters)
  }

  private fun sendEventToReactNative(eventName: String, parameters: WritableMap?) {
    context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(eventName, parameters)
  }

}
