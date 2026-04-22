import type { ViewProps } from 'react-native';

// ── Shared types ─────────────────────────────────────────────

/** A WGS84 geographic coordinate pair. */
export interface Coordinates {
  /** Latitude in decimal degrees. Valid range: -90 to 90. */
  latitude: number;
  /** Longitude in decimal degrees. Valid range: -180 to 180. */
  longitude: number;
}

/** An intermediate point between origin and destination. */
export interface Waypoint {
  /** Coordinate the route should pass through. */
  coordinate: Coordinates;
  /**
   * When `true`, the waypoint shapes the route without creating a leg
   * boundary or triggering an arrival announcement. When `false` or
   * omitted, it is a full stop waypoint.
   *
   * @default false
   */
  isSilent?: boolean;
}

// ── Language ─────────────────────────────────────────────────

/**
 * Instruction language supported by the Mapbox Directions API.
 *
 * Some values use Mapbox Voice API cloud synthesis; others fall back
 * to the platform's native TTS engine (iOS `AVSpeechSynthesizer`,
 * Android `TextToSpeech`). See SPEC §8 for the per-language tier.
 */
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

/**
 * Machine-readable error code emitted via {@link MapboxNavigationProps.onError}.
 * See SPEC §7 for the meaning of each code.
 */
export type MapboxNavigationErrorCode =
  | 'ROUTE_CALCULATION_FAILED'
  | 'GPS_UNAVAILABLE'
  | 'GPS_PERMISSION_DENIED'
  | 'NETWORK_ERROR'
  | 'INVALID_COORDINATES'
  | 'SDK_INIT_FAILED';

// ── Event types ──────────────────────────────────────────────

/** Payload for {@link MapboxNavigationProps.onArrive}. */
export interface ArriveEvent {
  nativeEvent: {
    /** The final destination the user arrived at. */
    destination: Coordinates;
  };
}

/** Payload for {@link MapboxNavigationProps.onError}. */
export interface ErrorEvent {
  nativeEvent: {
    /** Machine-readable error code. */
    code: MapboxNavigationErrorCode;
    /** Human-readable error message, suitable for logging. */
    message: string;
  };
}

/** Payload for {@link MapboxNavigationProps.onMuteChange}. */
export interface MuteChangeEvent {
  nativeEvent: {
    /** Current mute state after the change. */
    isMuted: boolean;
  };
}

/** Payload for {@link MapboxNavigationProps.onRouteProgressChange}. */
export interface RouteProgressEvent {
  nativeEvent: {
    /** Distance traveled along the active route, in meters. */
    distanceTraveled: number;
    /** Distance remaining to the final destination, in meters. */
    distanceRemaining: number;
    /** Estimated time remaining to the final destination, in seconds. */
    durationRemaining: number;
    /** Fraction of the route traveled, `0.0`–`1.0`. */
    fractionTraveled: number;
  };
}

/** Payload for {@link MapboxNavigationProps.onLocationChange}. */
export interface LocationEvent {
  nativeEvent: {
    /** Current map-matched latitude in decimal degrees. */
    latitude: number;
    /** Current map-matched longitude in decimal degrees. */
    longitude: number;
  };
}

// ── Component props ──────────────────────────────────────────

/**
 * Props for the `<MapboxNavigation />` component.
 *
 * The component renders a full-screen, self-contained turn-by-turn
 * navigation experience. See SPEC §5 for the full API contract.
 */
export interface MapboxNavigationProps extends ViewProps {
  // ── Route ──────────────────────────────────────────────

  /** Start point of the route. */
  origin: Coordinates;
  /** Final destination of the route. */
  destination: Coordinates;
  /**
   * Optional intermediate waypoints, visited in order between `origin`
   * and `destination`. The Mapbox Directions API is all-or-nothing:
   * if any waypoint cannot be reached, the whole route request fails
   * with `ROUTE_CALCULATION_FAILED`.
   */
  waypoints?: Waypoint[];

  // ── Configuration ──────────────────────────────────────

  /**
   * Instruction language for voice + text guidance.
   *
   * @default 'en'
   */
  language?: MapboxLanguage;
  /**
   * When `true`, simulates driving along the calculated route instead
   * of using real GPS. Intended for development and E2E testing.
   * Read at mount only — runtime changes require remounting.
   *
   * @default false
   */
  shouldSimulateRoute?: boolean;
  /**
   * Scales simulated driving speed when `shouldSimulateRoute` is `true`.
   * Values greater than `1` run faster than real time; values less than
   * `1` run slower. Has no effect on live GPS routes.
   *
   * @default 1
   */
  simulationSpeedMultiplier?: number;
  /**
   * Initial mute state of voice guidance. The user may override this at
   * any time via the on-screen mute button; changes are reported via
   * {@link onMuteChange}.
   *
   * @default false
   */
  mute?: boolean;
  /**
   * Force a light or dark navigation style, or let the Mapbox SDK pick
   * based on sun position and tunnel detection.
   *
   * @default 'auto'
   */
  colorScheme?: 'light' | 'dark' | 'auto';
  /**
   * Font family applied to all text rendered inside the navigation UI
   * (maneuver banner, trip progress, button labels, speed limit). Must
   * resolve to a font installed in the host app (iOS: PostScript name
   * registered via `UIAppFonts`; Android: family bundled via
   * `res/font` or `assets/fonts`). Omit to use the SDK default.
   */
  fontFamily?: string;

  // ── Events ─────────────────────────────────────────────

  /**
   * Fires once when the user physically arrives at the final
   * destination. Use this to update local application state (e.g.
   * marking a delivery as completed).
   *
   * The Mapbox navigation UI stays mounted after this fires so the
   * user sees the native end-of-trip screen (with the built-in
   * "End Navigation" button). Do **not** unmount the component
   * here if you want that visual feedback — use
   * {@link onNavigationEnd} to react to the user dismissing that UI.
   */
  onArrive?: (event: ArriveEvent) => void;
  /**
   * Fires when the library encounters a recoverable or fatal error.
   * The payload includes a machine-readable code and a human-readable
   * message. See SPEC §7 for the error taxonomy.
   */
  onError?: (event: ErrorEvent) => void;
  /**
   * Fires when the user taps the on-screen stop/exit button to abort
   * navigation **before** arrival. The native session is torn down
   * immediately after; the consumer is expected to unmount the
   * component (typically by navigating away).
   */
  onCancelNavigation?: () => void;
  /**
   * Fires when the Mapbox SDK dismisses the navigation UI after a
   * completed trip — i.e. the user reached the destination and then
   * tapped the native "End Navigation" button on the arrival screen.
   * Never fires for pre-arrival cancellation (use
   * {@link onCancelNavigation} for that).
   *
   * Consumers that want the user to see the native arrival UI should
   * defer navigating back / unmounting until this fires, rather than
   * reacting to {@link onArrive}.
   */
  onNavigationEnd?: () => void;
  /**
   * Fires when voice guidance is muted or unmuted — either by the
   * {@link mute} prop or by the user tapping the on-screen mute button.
   */
  onMuteChange?: (event: MuteChangeEvent) => void;
  /**
   * Fires on route progress updates (distance, duration, fraction
   * traveled). Throttled natively to roughly one event per second.
   */
  onRouteProgressChange?: (event: RouteProgressEvent) => void;
  /**
   * Fires when the user's map-matched location changes. De-duplicated
   * natively so stationary users don't produce a flood of events.
   */
  onLocationChange?: (event: LocationEvent) => void;
  /**
   * Fires when the Mapbox SDK recalculates the route because the user
   * deviated from the current path.
   */
  onReroute?: () => void;
}

// ── Ref handle ───────────────────────────────────────────────

/**
 * Imperative handle exposed via `ref` on `<MapboxNavigation />`.
 * All methods are no-ops when called before the native view is ready.
 */
export interface MapboxNavigationRef {
  /** Return the map camera to following the user's position and heading. */
  recenterCamera: () => void;
  /** Zoom the camera out to show the entire remaining route. */
  showRouteOverview: () => void;
}
