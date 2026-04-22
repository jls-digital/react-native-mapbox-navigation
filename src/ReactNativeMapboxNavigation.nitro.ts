import type {
  HybridView,
  HybridViewMethods,
  HybridViewProps,
} from 'react-native-nitro-modules';

// ── Shared types (become native structs) ─────────────────────

/** WGS84 geographic coordinate pair. */
export interface Coordinates {
  /** Latitude in decimal degrees. Valid range: -90 to 90. */
  latitude: number;
  /** Longitude in decimal degrees. Valid range: -180 to 180. */
  longitude: number;
}

/** Intermediate point along the route. */
export interface Waypoint {
  /** Coordinate the route should pass through. */
  coordinate: Coordinates;
  /**
   * When `true`, shapes the route without creating a leg boundary or
   * arrival announcement.
   */
  isSilent?: boolean;
}

/** Snapshot of progress along the active route. */
export interface RouteProgress {
  /** Distance traveled along the active route, in meters. */
  distanceTraveled: number;
  /** Distance remaining to the final destination, in meters. */
  distanceRemaining: number;
  /** Estimated time remaining to the final destination, in seconds. */
  durationRemaining: number;
  /** Fraction of the route traveled, `0.0`–`1.0`. */
  fractionTraveled: number;
}

// ── Props ────────────────────────────────────────────────────

/**
 * Native-bridge props for the Mapbox navigation HybridView. The
 * public JS-facing API lives in `types.ts`
 * (`MapboxNavigationProps`) and wraps these values into richer
 * React-style event payloads.
 */
export interface ReactNativeMapboxNavigationProps extends HybridViewProps {
  // Route
  /** Start point of the route. */
  origin: Coordinates;
  /** Final destination of the route. */
  destination: Coordinates;
  /** Optional intermediate waypoints in visit order. */
  waypoints?: Waypoint[];

  // Configuration
  /** Instruction language code (see `MapboxLanguage` in `types.ts`). */
  language?: string;
  /** Simulate driving along the route instead of using real GPS. */
  shouldSimulateRoute?: boolean;
  /** Simulation speed multiplier; only honored when simulating. */
  simulationSpeedMultiplier?: number;
  /** Initial voice mute state. */
  mute?: boolean;
  /** `'light' | 'dark' | 'auto'`. */
  colorScheme?: string;
  /** Font family override for navigation UI text. */
  fontFamily?: string;

  // Events
  /** Fires once on arrival at the final destination. Does not dismiss the UI. */
  onArrive?: (destination: Coordinates) => void;
  /** Fires on recoverable/fatal errors with a code and message. */
  onError?: (code: string, message: string) => void;
  /** Fires when the user cancels navigation before arrival. */
  onCancelNavigation?: () => void;
  /**
   * Fires when the SDK dismisses the nav UI after a completed trip
   * (user tapped the native "End Navigation" button post-arrival).
   */
  onNavigationEnd?: () => void;
  /** Fires when voice guidance is muted or unmuted. */
  onMuteChange?: (isMuted: boolean) => void;
  /** Fires on route progress updates, throttled natively to ~1/s. */
  onRouteProgressChange?: (progress: RouteProgress) => void;
  /** Fires when the map-matched user location changes. */
  onLocationChange?: (latitude: number, longitude: number) => void;
  /** Fires when the SDK recalculates the route after a deviation. */
  onReroute?: () => void;
}

// ── Imperative methods ───────────────────────────────────────

/** Imperative commands exposed on the HybridView ref. */
export interface ReactNativeMapboxNavigationMethods extends HybridViewMethods {
  /** Return the camera to following the user's position and heading. */
  recenterCamera(): void;
  /** Zoom out to show the entire remaining route. */
  showRouteOverview(): void;
}

// ── HybridView type ─────────────────────────────────────────

export type ReactNativeMapboxNavigation = HybridView<
  ReactNativeMapboxNavigationProps,
  ReactNativeMapboxNavigationMethods
>;
