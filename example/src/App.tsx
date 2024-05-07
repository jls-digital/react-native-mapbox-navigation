import * as React from 'react';
import type { Coordinates } from 'react-native-mapbox-navigation';
import { MapboxNavigationView } from 'react-native-mapbox-navigation';

export default function App() {
  const origin: Coordinates = {
    latitude: 47.102,
    longitude: 8.03,
  };
  const destination: Coordinates = {
    latitude: 47.392,
    longitude: 8.39,
  };
  return <MapboxNavigationView origin={origin} destination={destination} />;
}
