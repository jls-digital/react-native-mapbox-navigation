import { Platform, requireNativeComponent, UIManager } from 'react-native';
import type { MapboxNavigationProps, NativeNavigationProps } from './types';
import type { FunctionComponent } from 'react';
import { mapToNativeCoordinates } from './utils/coordinates-mapper.util';
import React from 'react';

const LINKING_ERROR =
  `The package 'react-native-mapbox-navigation' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const ComponentName = 'MapboxNavigation';

const NativeMapboxNavigation =
  UIManager.getViewManagerConfig(ComponentName) != null
    ? requireNativeComponent<NativeNavigationProps>(ComponentName)
    : () => {
        throw new Error(LINKING_ERROR);
      };

export const MapboxNavigation: FunctionComponent<MapboxNavigationProps> = (
  props
) => {
  const nativeProps: NativeNavigationProps = {
    ...props,
    destination: mapToNativeCoordinates(props.destination),
    origin: props.origin ? mapToNativeCoordinates(props.origin) : undefined,
    waypoints: props.waypoints
      ? props.waypoints.map(mapToNativeCoordinates)
      : [],
  };

  return <NativeMapboxNavigation {...nativeProps} />;
};

export * from './types';
