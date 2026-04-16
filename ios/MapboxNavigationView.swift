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
  @objc var mute: Bool = false

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
    clipsToBounds = false
  }

  required init?(coder aDecoder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }

  override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
    guard let navView = navViewController?.view else {
      return super.hitTest(point, with: event)
    }

    let result = super.hitTest(point, with: event)
    if let result = result, result !== self {
      return result
    }

    let convertedPoint = convert(point, to: navView)
    if navView.point(inside: convertedPoint, with: event) {
      return navView.hitTest(convertedPoint, with: event) ?? navView
    }
    return nil
  }

  override func reactSubviews() -> [UIView]! {
    return []
  }

  override func insertReactSubview(_ subview: UIView!, at atIndex: Int) {
  }

  override func removeReactSubview(_ subview: UIView!) {
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
    if let vc = navViewController {
      vc.willMove(toParent: nil)
      vc.view.removeFromSuperview()
      vc.removeFromParent()
      navViewController = nil
    }
    embedded = false
    embedding = false
  }

  /// Fix UILayoutContainerView having isUserInteractionEnabled=false,
  /// which blocks all touches from reaching this view.
  private func fixParentInteraction() {
    var view: UIView? = self.superview
    while let v = view {
      if !v.isUserInteractionEnabled {
        v.isUserInteractionEnabled = true
      }
      view = v.superview
    }
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
      DispatchQueue.main.async {
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

            navigationService.router.reroutesProactively = false

            let navigationOptions = NavigationOptions(navigationService: navigationService)
            let vc = NavigationViewController(for: response, routeIndex: 0, routeOptions: options, navigationOptions: navigationOptions)

            vc.showsEndOfRouteFeedback = false
            StatusView.appearance().isHidden = false

            NavigationSettings.shared.voiceMuted = strongSelf.mute;

            vc.delegate = strongSelf

            parentVC.addChild(vc)
            strongSelf.addSubview(vc.view)
            vc.view.frame = strongSelf.bounds
            vc.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            vc.didMove(toParent: parentVC)
            strongSelf.navViewController = vc
            strongSelf.fixParentInteraction()
            // Re-apply after push transition animation settles
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { [weak self] in
              self?.fixParentInteraction()
            }

            if let muteButton = vc.floatingButtons?[1] {
              muteButton.addTarget(self, action: #selector(self?.toggleMute(sender:)), for: .touchUpInside)
            }
        }

        strongSelf.embedding = false
        strongSelf.embedded = true
      }
    }
  }

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
