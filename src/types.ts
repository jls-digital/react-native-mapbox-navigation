import type { ViewProps } from 'react-native';

// ── Shared types ─────────────────────────────────────────────

export interface Coordinates {
  latitude: number;
  longitude: number;
}

export interface Waypoint {
  coordinate: Coordinates;
  isSilent?: boolean;
}

// ── Language ─────────────────────────────────────────────────

export type MapboxLanguage =
  | 'ar'
  | 'ca'
  | 'cs'
  | 'da'
  | 'de'
  | 'en'
  | 'es'
  | 'fi'
  | 'fr'
  | 'he'
  | 'hu'
  | 'id'
  | 'it'
  | 'ja'
  | 'ko'
  | 'nb'
  | 'nl'
  | 'pl'
  | 'pt-BR'
  | 'pt-PT'
  | 'ro'
  | 'ru'
  | 'sl'
  | 'sv'
  | 'tr'
  | 'uk'
  | 'vi'
  | 'zh-Hans';

// ── Error codes ──────────────────────────────────────────────

export type MapboxNavigationErrorCode =
  | 'ROUTE_CALCULATION_FAILED'
  | 'GPS_UNAVAILABLE'
  | 'GPS_PERMISSION_DENIED'
  | 'NETWORK_ERROR'
  | 'INVALID_COORDINATES'
  | 'SDK_INIT_FAILED';

// ── Event types ──────────────────────────────────────────────

export interface ArriveEvent {
  nativeEvent: {
    destination: Coordinates;
  };
}

export interface ErrorEvent {
  nativeEvent: {
    code: MapboxNavigationErrorCode;
    message: string;
  };
}

export interface MuteChangeEvent {
  nativeEvent: {
    isMuted: boolean;
  };
}

export interface RouteProgressEvent {
  nativeEvent: {
    distanceTraveled: number;
    distanceRemaining: number;
    durationRemaining: number;
    fractionTraveled: number;
  };
}

export interface LocationEvent {
  nativeEvent: {
    latitude: number;
    longitude: number;
  };
}

// ── Component props ──────────────────────────────────────────

export interface MapboxNavigationProps extends ViewProps {
  // Route
  origin: Coordinates;
  destination: Coordinates;
  waypoints?: Waypoint[];

  // Configuration
  language?: MapboxLanguage;
  shouldSimulateRoute?: boolean;
  simulationSpeedMultiplier?: number;
  mute?: boolean;
  colorScheme?: 'light' | 'dark' | 'auto';
  fontFamily?: string;

  // Events
  onArrive?: (event: ArriveEvent) => void;
  onError?: (event: ErrorEvent) => void;
  onCancelNavigation?: () => void;
  onMuteChange?: (event: MuteChangeEvent) => void;
  onRouteProgressChange?: (event: RouteProgressEvent) => void;
  onLocationChange?: (event: LocationEvent) => void;
  onReroute?: () => void;
}

// ── Ref handle ───────────────────────────────────────────────

export interface MapboxNavigationRef {
  recenterCamera: () => void;
  showRouteOverview: () => void;
}
