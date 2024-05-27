import {
  findNodeHandle,
  NativeEventEmitter,
  NativeModules,
  Platform,
  requireNativeComponent,
  StyleSheet,
  UIManager,
  type ViewProps,
} from 'react-native';
import type {
  MapboxNavigationProps,
  NativeNavigationProps,
  OnErrorEvent,
  OnLocationChangeEvent,
  OnMuteChangeEvent,
  OnRouteProgressChangeEvent,
} from './types';
import React, { type FunctionComponent, useEffect, useRef } from 'react';
import { mapToNativeCoordinates } from './utils/coordinates-mapper.util';

const LINKING_ERROR =
  `The package 'react-native-mapbox-navigation' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const ComponentName = 'MapboxNavigation';

const NativeMapboxNavigation =
  UIManager.getViewManagerConfig(ComponentName) != null
    ? requireNativeComponent<NativeNavigationProps & ViewProps>(ComponentName)
    : () => {
        throw new Error(LINKING_ERROR);
      };

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
});

const createFragment = (viewId: number) =>
  UIManager.dispatchViewManagerCommand(
    viewId,
    // we are calling the 'create' command
    UIManager.getViewManagerConfig(ComponentName).Commands.create!.toString(),
    [viewId]
  );

export const MapboxNavigation: FunctionComponent<MapboxNavigationProps> = (
  props
) => {
  const ref = useRef(null);

  const nativeProps: NativeNavigationProps = {
    ...props,
    destination: mapToNativeCoordinates(props.destination),
    origin: props.origin ? mapToNativeCoordinates(props.origin) : undefined,
    waypoints: props.waypoints
      ? props.waypoints.map(mapToNativeCoordinates)
      : [],
  };

  useEffect(() => {
    if (Platform.OS === 'android') {
      const eventEmitter = new NativeEventEmitter(
        NativeModules.MapboxNavigation
      );
      const onLocationChangeListener = eventEmitter.addListener(
        'onLocationChange',
        (event) => {
          const mappedEvent: OnLocationChangeEvent = {
            nativeEvent: {
              latitude: event.latitude,
              longitude: event.longitude,
            },
          };
          props.onLocationChange?.(mappedEvent);
        }
      );
      const onRouteProgressChangeListener = eventEmitter.addListener(
        'onRouteProgressChange',
        (event) => {
          const mappedEvent: OnRouteProgressChangeEvent = {
            nativeEvent: {
              distanceTraveled: event.distanceTraveled,
              durationRemaining: event.durationRemaining,
              fractionTraveled: event.fractionTraveled,
              distanceRemaining: event.distanceRemaining,
            },
          };
          props.onRouteProgressChange?.(mappedEvent);
        }
      );
      const onErrorListener = eventEmitter.addListener('onError', (event) => {
        const mappedEvent: OnErrorEvent = {
          nativeEvent: {
            message: event.message,
          },
        };
        props.onError?.(mappedEvent);
      });
      const onCancelNavigationListener = eventEmitter.addListener(
        'onCancelNavigation',
        () => {
          props.onCancelNavigation?.();
        }
      );
      const onArriveListener = eventEmitter.addListener('onArrive', () => {
        props.onArrive?.();
      });
      const onMuteChangeListener = eventEmitter.addListener(
        'onMuteChange',
        (event) => {
          const mappedEvent: OnMuteChangeEvent = {
            nativeEvent: {
              isMuted: event.isMuted,
            },
          };
          props.onMuteChange?.(mappedEvent);
        }
      );

      const viewId = findNodeHandle(ref.current);
      if (viewId) {
        createFragment(viewId);
      }

      // Removes the listener once unmounted
      return () => {
        onLocationChangeListener.remove();
        onRouteProgressChangeListener.remove();
        onErrorListener.remove();
        onCancelNavigationListener.remove();
        onArriveListener.remove();
        onMuteChangeListener.remove();
      };
    } else {
      return () => {};
    }
  }, [props]);

  return (
    <NativeMapboxNavigation
      style={styles.container}
      {...nativeProps}
      ref={ref}
    />
  );
};

export * from './types';
