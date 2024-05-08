import type { Coordinates, NativeCoordinates } from './coordinates.type';
import type {
  OnErrorEvent,
  OnLocationChangeEvent,
  OnMuteChangeEvent,
  OnRouteProgressChangeEvent,
} from './events.type';

interface CommonProps {
  shouldSimulateRoute?: boolean;
  onLocationChange?: (event: OnLocationChangeEvent) => void;
  onRouteProgressChange?: (event: OnRouteProgressChangeEvent) => void;
  onError?: (event: OnErrorEvent) => void;
  onCancelNavigation?: () => void;
  onArrive?: () => void;
  onMuteChange?: (event: OnMuteChangeEvent) => void;
  shouldShowEndOfRouteFeedback?: boolean;
  hideStatusView?: boolean;
  mute?: boolean;
  shouldRerouteProactively?: boolean;
}

export interface MapboxNavigationProps extends CommonProps {
  origin: Coordinates;
  destination: Coordinates;
  waypoints?: Array<Coordinates>;
}

export interface NativeNavigationProps extends CommonProps {
  origin: NativeCoordinates;
  destination: NativeCoordinates;
  waypoints: Array<NativeCoordinates>;
}
