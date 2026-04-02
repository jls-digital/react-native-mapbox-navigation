import { forwardRef, useImperativeHandle, useMemo, useRef } from 'react';
import { callback } from 'react-native-nitro-modules';
import { ReactNativeMapboxNavigationView } from './ReactNativeMapboxNavigationView.native';
import type {
  ReactNativeMapboxNavigation,
  RouteProgress,
  Coordinates,
} from './ReactNativeMapboxNavigation.nitro';
import type {
  MapboxNavigationErrorCode,
  MapboxNavigationProps,
  MapboxNavigationRef,
} from './types';

export const MapboxNavigation = forwardRef<
  MapboxNavigationRef,
  MapboxNavigationProps
>(function MapboxNavigation(
  {
    origin,
    destination,
    waypoints,
    language,
    shouldSimulateRoute,
    mute,
    colorScheme,
    onArrive,
    onError,
    onCancelNavigation,
    onMuteChange,
    onRouteProgressChange,
    onLocationChange,
    onReroute,
    ...viewProps
  },
  ref
) {
  const nativeRef = useRef<ReactNativeMapboxNavigation | null>(null);

  useImperativeHandle(ref, () => ({
    recenterCamera: () => nativeRef.current?.recenterCamera(),
    showRouteOverview: () => nativeRef.current?.showRouteOverview(),
  }));

  const wrappedHybridRef = useMemo(
    () =>
      callback((nativeView: ReactNativeMapboxNavigation) => {
        nativeRef.current = nativeView;
      }),
    []
  );

  const wrappedOnArrive = useMemo(
    () =>
      onArrive
        ? callback((dest: Coordinates) => {
            onArrive({ nativeEvent: { destination: dest } });
          })
        : undefined,
    [onArrive]
  );

  const wrappedOnError = useMemo(
    () =>
      onError
        ? callback((code: string, message: string) => {
            onError({
              nativeEvent: {
                code: code as MapboxNavigationErrorCode,
                message,
              },
            });
          })
        : undefined,
    [onError]
  );

  const wrappedOnCancelNavigation = useMemo(
    () => (onCancelNavigation ? callback(onCancelNavigation) : undefined),
    [onCancelNavigation]
  );

  const wrappedOnMuteChange = useMemo(
    () =>
      onMuteChange
        ? callback((isMuted: boolean) => {
            onMuteChange({ nativeEvent: { isMuted } });
          })
        : undefined,
    [onMuteChange]
  );

  const wrappedOnRouteProgressChange = useMemo(
    () =>
      onRouteProgressChange
        ? callback((progress: RouteProgress) => {
            onRouteProgressChange({ nativeEvent: progress });
          })
        : undefined,
    [onRouteProgressChange]
  );

  const wrappedOnLocationChange = useMemo(
    () =>
      onLocationChange
        ? callback((latitude: number, longitude: number) => {
            onLocationChange({ nativeEvent: { latitude, longitude } });
          })
        : undefined,
    [onLocationChange]
  );

  const wrappedOnReroute = useMemo(
    () => (onReroute ? callback(onReroute) : undefined),
    [onReroute]
  );

  return (
    <ReactNativeMapboxNavigationView
      {...viewProps}
      hybridRef={wrappedHybridRef}
      origin={origin}
      destination={destination}
      waypoints={waypoints}
      language={language}
      shouldSimulateRoute={shouldSimulateRoute}
      mute={mute}
      colorScheme={colorScheme}
      onArrive={wrappedOnArrive}
      onError={wrappedOnError}
      onCancelNavigation={wrappedOnCancelNavigation}
      onMuteChange={wrappedOnMuteChange}
      onRouteProgressChange={wrappedOnRouteProgressChange}
      onLocationChange={wrappedOnLocationChange}
      onReroute={wrappedOnReroute}
    />
  );
});
