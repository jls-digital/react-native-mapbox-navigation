import type { Coordinates, NativeCoordinates } from '../types/coordinates.type';

export const mapToNativeCoordinates = (
  coordinates: Coordinates
): NativeCoordinates => {
  return [coordinates.longitude, coordinates.latitude];
};
