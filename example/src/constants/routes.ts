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

export interface RoutePreset {
  label: string;
  origin: Coordinates;
  destination: Coordinates;
  waypoints?: Waypoint[];
}

export const ROUTE_PRESETS: RoutePreset[] = [
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
];
