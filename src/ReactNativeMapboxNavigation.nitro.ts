import type {
  HybridView,
  HybridViewMethods,
  HybridViewProps,
} from 'react-native-nitro-modules';

// ── Shared types (become native structs) ─────────────────────

export interface Coordinates {
  latitude: number;
  longitude: number;
}

export interface Waypoint {
  coordinate: Coordinates;
  isSilent?: boolean;
}

export interface RouteProgress {
  distanceTraveled: number;
  distanceRemaining: number;
  durationRemaining: number;
  fractionTraveled: number;
}

// ── Props ────────────────────────────────────────────────────

export interface ReactNativeMapboxNavigationProps extends HybridViewProps {
  // Route
  origin: Coordinates;
  destination: Coordinates;
  waypoints?: Waypoint[];

  // Configuration
  language?: string;
  shouldSimulateRoute?: boolean;
  simulationSpeedMultiplier?: number;
  mute?: boolean;
  colorScheme?: string;
  fontFamily?: string;

  // Events
  onArrive?: (destination: Coordinates) => void;
  onError?: (code: string, message: string) => void;
  onCancelNavigation?: () => void;
  onMuteChange?: (isMuted: boolean) => void;
  onRouteProgressChange?: (progress: RouteProgress) => void;
  onLocationChange?: (latitude: number, longitude: number) => void;
  onReroute?: () => void;
}

// ── Imperative methods ───────────────────────────────────────

export interface ReactNativeMapboxNavigationMethods extends HybridViewMethods {
  recenterCamera(): void;
  showRouteOverview(): void;
}

// ── HybridView type ─────────────────────────────────────────

export type ReactNativeMapboxNavigation = HybridView<
  ReactNativeMapboxNavigationProps,
  ReactNativeMapboxNavigationMethods
>;
