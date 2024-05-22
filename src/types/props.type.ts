import type { Coordinates, NativeCoordinates } from './coordinates.type';
import type {
  OnErrorEvent,
  OnLocationChangeEvent,
  OnMuteChangeEvent,
  OnRouteProgressChangeEvent,
} from './events.type';

interface CommonProps {
  /**
   * If set to true, Mapbox will simulate navigation along the route.
   * Note that if set, simulationOrigin must also be provided.
   */
  shouldSimulateRoute?: boolean;
  /**
   * Callback that is called when the user's location changes.
   */
  onLocationChange?: (event: OnLocationChangeEvent) => void;
  /**
   * Callback that is called when the route progress changes
   */
  onRouteProgressChange?: (event: OnRouteProgressChangeEvent) => void;
  /**
   * Callback that is called when an error occurs.
   */
  onError?: (event: OnErrorEvent) => void;
  /**
   * Callback that is called when the user cancels navigation.
   */
  onCancelNavigation?: () => void;
  /**
   * Callback that is called when the user arrives at the destination.
   */
  onArrive?: () => void;
  /**
   * Callback that is called when the user mutes or unmutes the voice guidance
   * via native mapbox buttons.
   */
  onMuteChange?: (event: OnMuteChangeEvent) => void;
  /**
   * If set to true, voice guidance will be muted on load.
   */
  mute?: boolean;
}

export interface MapboxNavigationProps extends CommonProps {
  /**
   * Destination coordinates.
   */
  destination: Coordinates;
  /**
   * Origin coordinates for simulation.
   */
  simulationOrigin?: Coordinates;
  /**
   * Array of waypoints to visit along the route.
   */
  waypoints?: Array<Coordinates>;
}

export interface NativeNavigationProps extends CommonProps {
  destination: NativeCoordinates;
  simulationOrigin?: NativeCoordinates;
  waypoints: Array<NativeCoordinates>;
}
