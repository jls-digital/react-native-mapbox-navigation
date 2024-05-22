import MapboxCoreNavigation
import MapboxNavigation
import MapboxDirections

// // adapted from https://pspdfkit.com/blog/2017/native-view-controllers-and-react-native/ and https://github.com/mslabenyak/react-native-mapbox-navigation/blob/master/ios/Mapbox/MapboxNavigationView.swift
extension UIView {
  var parentViewController: UIViewController? {
    var parentResponder: UIResponder? = self
    while parentResponder != nil {
      parentResponder = parentResponder!.next
      if let viewController = parentResponder as? UIViewController {
        return viewController
      }
    }
    return nil
  }
}

class MapboxNavigationView: UIView {
  weak var navViewController: NavigationViewController?
  
  var embedded: Bool
  var embedding: Bool
  
  @objc var origin: NSArray = [] {
    didSet { setNeedsLayout() }
  }
  
  @objc var destination: NSArray = [] {
    didSet { setNeedsLayout() }
  }

  @objc var waypoints: NSArray = [] {
    didSet { setNeedsLayout() }
  }
  
  @objc var shouldSimulateRoute: Bool = false
  @objc var showsEndOfRouteFeedback: Bool = false
  @objc var hideStatusView: Bool = false
  @objc var mute: Bool = false
  @objc var shouldRerouteProactively: Bool = true
  
  @objc var onLocationChange: RCTDirectEventBlock?
  @objc var onRouteProgressChange: RCTDirectEventBlock?
  @objc var onError: RCTDirectEventBlock?
  @objc var onCancelNavigation: RCTDirectEventBlock?
  @objc var onArrive: RCTDirectEventBlock?
  @objc var onMuteChange: RCTDirectEventBlock?
  
  override init(frame: CGRect) {
    self.embedded = false
    self.embedding = false
    super.init(frame: frame)
  }
  
  required init?(coder aDecoder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }
  
  override func layoutSubviews() {
    super.layoutSubviews()
    
    if (navViewController == nil && !embedding && !embedded) {
      embed()
    } else {
      navViewController?.view.frame = bounds
    }
  }
  
  override func removeFromSuperview() {
    super.removeFromSuperview()
    // cleanup and teardown any existing resources
    self.navViewController?.removeFromParent()
  }
  
  @objc private func toggleMute(sender: UIButton) {
    onMuteChange?(["isMuted": sender.isSelected]);
  }
  
  private func embed() {
    guard origin.count == 2 && destination.count == 2 else { return }
    
    embedding = true

    let route = createRoute()
    let options = NavigationRouteOptions(waypoints: route, profileIdentifier: .automobile)

    Directions.shared.calculate(options) { [weak self] (_, result) in
      guard let strongSelf = self, let parentVC = strongSelf.parentViewController else {
        return
      }
      
      switch result {
        case .failure(let error):
          strongSelf.onError!(["message": error.localizedDescription])
        case .success(let response):
          guard self != nil else {
            return
          }
          
          let navigationService = MapboxNavigationService(
            routeResponse: response,
            routeIndex: 0,
            routeOptions: options,
            routingProvider: Directions.shared,
            credentials: NavigationSettings.shared.directions.credentials,
            locationSource: nil,
            eventsManagerType: nil,
            simulating: strongSelf.shouldSimulateRoute ? .always : .never,
            routerType: nil
          )
        
          navigationService.router.reroutesProactively = strongSelf.shouldRerouteProactively
          
          let navigationOptions = NavigationOptions(navigationService: navigationService)
          let vc = NavigationViewController(for: response, routeIndex: 0, routeOptions: options, navigationOptions: navigationOptions)

          vc.showsEndOfRouteFeedback = strongSelf.showsEndOfRouteFeedback
          StatusView.appearance().isHidden = strongSelf.hideStatusView

          NavigationSettings.shared.voiceMuted = strongSelf.mute;
          
          vc.delegate = strongSelf
        
          parentVC.addChild(vc)
          strongSelf.addSubview(vc.view)
          vc.view.frame = strongSelf.bounds
          vc.didMove(toParent: parentVC)
          strongSelf.navViewController = vc
        
          if let muteButton = vc.floatingButtons?[1] {
            muteButton.addTarget(self, action: #selector(self?.toggleMute(sender:)), for: .touchUpInside)
          }
      }
      
      strongSelf.embedding = false
      strongSelf.embedded = true
      

    }
  }
  
  // MARK: Helper Functions
  
  // MARK: - Route Creation
  /**
   * Creates a route out of the origin, destination and potential waypoints which lay between them
   */
  private func createRoute() -> Array<Waypoint> {
    let originWaypoint = createWaypoint(from: origin)
    let destinationWaypoint = createWaypoint(from: destination)
    
    originWaypoint.separatesLegs = false
    let additionalWaypoints = waypoints.map { coordinate -> Waypoint in
      let waypoint = createWaypoint(from: coordinate as! NSArray)
      waypoint.separatesLegs = false
      return waypoint
    }
      
    return [originWaypoint] + additionalWaypoints + [destinationWaypoint]
  }
  
  /**
   * Creates a waypoint out of an array of coordinates.
   * The coordinates are expected to be in the format [longitude, latitude]
   */
  private func createWaypoint(from coordinate: NSArray) -> Waypoint {
    Waypoint(coordinate: CLLocationCoordinate2D(latitude: coordinate[1] as! CLLocationDegrees, longitude: coordinate[0] as! CLLocationDegrees))
  }
}

extension MapboxNavigationView: NavigationViewControllerDelegate {
  func navigationViewController(_ navigationViewController: NavigationViewController, didUpdate progress: RouteProgress, with location: CLLocation, rawLocation: CLLocation) {
    onLocationChange?(["longitude": location.coordinate.longitude, "latitude": location.coordinate.latitude])
    onRouteProgressChange?(["distanceTraveled": progress.distanceTraveled,
                            "durationRemaining": progress.durationRemaining,
                            "fractionTraveled": progress.fractionTraveled,
                            "distanceRemaining": progress.distanceRemaining])
  }
  
  func navigationViewControllerDidDismiss(_ navigationViewController: NavigationViewController, byCanceling canceled: Bool) {
    if (!canceled) {
      return;
    }
    onCancelNavigation?(["message": ""]);
  }
  
  func navigationViewController(_ navigationViewController: NavigationViewController, didArriveAt waypoint: Waypoint) -> Bool {
    onArrive?(["message": ""]);
    return true;
  }
}
