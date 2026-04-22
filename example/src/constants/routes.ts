import type {
  Coordinates,
  Waypoint,
} from '@jls-digital/react-native-mapbox-navigation';

export const CITIES: Record<string, Coordinates> = {
  zurich: { latitude: 47.3769, longitude: 8.5417 },
  bern: { latitude: 46.9481, longitude: 7.4474 },
  lucerne: { latitude: 47.0502, longitude: 8.3093 },
  basel: { latitude: 47.5596, longitude: 7.5886 },
  geneva: { latitude: 46.2044, longitude: 6.1432 },
};

// ~150m within central Zurich (with one stop waypoint) — completes in
// well under a minute with the default iOS simulator speed (no public
// speed multiplier on iOS v3). Intended for Maestro smoke tests that
// need to observe arrival without waiting for a full city-to-city drive.
const ZURICH_SHORT_ORIGIN: Coordinates = {
  latitude: 47.37441233663865,
  longitude: 8.535740016744242,
};
const ZURICH_SHORT_WAYPOINT: Coordinates = {
  latitude: 47.37374509815699,
  longitude: 8.534102519548396,
};
const ZURICH_SHORT_DESTINATION: Coordinates = {
  latitude: 47.37335312999324,
  longitude: 8.534220636808888,
};

export interface RoutePreset {
  label: string;
  origin: Coordinates;
  destination: Coordinates;
  waypoints?: Waypoint[];
}

export const ROUTE_PRESETS: RoutePreset[] = [
  {
    label: 'Zurich short (~150m, for tests)',
    origin: ZURICH_SHORT_ORIGIN,
    destination: ZURICH_SHORT_DESTINATION,
    waypoints: [{ coordinate: ZURICH_SHORT_WAYPOINT }],
  },
  {
    label: 'Zurich → Bern',
    origin: CITIES.zurich!,
    destination: CITIES.bern!,
  },
  {
    label: 'Zurich → Lucerne',
    origin: CITIES.zurich!,
    destination: CITIES.lucerne!,
  },
  {
    label: 'Zurich → Bern via Lucerne',
    origin: CITIES.zurich!,
    destination: CITIES.bern!,
    waypoints: [{ coordinate: CITIES.lucerne! }],
  },
  {
    label: 'Zurich → Bern (silent waypoint)',
    origin: CITIES.zurich!,
    destination: CITIES.bern!,
    waypoints: [{ coordinate: CITIES.lucerne!, isSilent: true }],
  },
  {
    // TC-7.3 — out-of-range lat/lon triggers INVALID_COORDINATES.
    label: 'Invalid coordinates (out of range)',
    origin: { latitude: 999, longitude: 999 },
    destination: CITIES.bern!,
  },
  {
    // TC-7.4 — middle of the Atlantic; Directions API returns no routes,
    // classified as ROUTE_CALCULATION_FAILED.
    label: 'Unroutable (ocean destination)',
    origin: CITIES.zurich!,
    destination: { latitude: 30, longitude: -40 },
  },
];
