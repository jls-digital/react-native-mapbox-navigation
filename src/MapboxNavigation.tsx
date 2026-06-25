import { forwardRef, useImperativeHandle, useMemo, useRef } from 'react';
import { StyleSheet } from 'react-native';
import { callback } from 'react-native-nitro-modules';
import { ReactNativeMapboxNavigationView } from './ReactNativeMapboxNavigationView.native';
import type {
  ReactNativeMapboxNavigation,
  RouteProgress,
  Coordinates,
} from './ReactNativeMapboxNavigation.nitro';
import type { MapboxNavigationProps, MapboxNavigationRef } from './types';
import { resolveErrorEvent } from './resolveErrorEvent';

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
    simulationSpeedMultiplier,
    mute,
    colorScheme,
    fontFamily,
    onArrive,
    onError,
    onCancelNavigation,
    onNavigationEnd,
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
            onError({ nativeEvent: resolveErrorEvent(code, message) });
          })
        : undefined,
    [onError]
  );

  const wrappedOnCancelNavigation = useMemo(
    () => (onCancelNavigation ? callback(onCancelNavigation) : undefined),
    [onCancelNavigation]
  );

  const wrappedOnNavigationEnd = useMemo(
    () => (onNavigationEnd ? callback(onNavigationEnd) : undefined),
    [onNavigationEnd]
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
      // This is a full-screen, self-contained navigation experience, so it
      // must fill its parent by default. A Nitro HybridView has no intrinsic
      // size — without a flex/size the RN layout collapses it to height 0 and
      // the (still-running) nav session renders into a zero-height view. The
      // consumer's own `style` is merged last so it can still override.
      style={[styles.fill, viewProps.style]}
      hybridRef={wrappedHybridRef}
      origin={origin}
      destination={destination}
      // Coalesce the primitive props to their documented defaults (see
      // `MapboxNavigationProps` in types.ts). Nitro's HybridView prop
      // converters reject `null`, so a prop that transitions
      // defined→undefined (e.g. `mute={someBoolOrUndefined}`) would throw
      // "Value is null, expected a boolean". Always handing the native side
      // a concrete value keeps the contract null-free.
      language={language ?? 'en'}
      shouldSimulateRoute={shouldSimulateRoute ?? false}
      simulationSpeedMultiplier={simulationSpeedMultiplier ?? 1}
      mute={mute ?? false}
      colorScheme={colorScheme ?? 'auto'}
      // No documented default; omit when absent so the SDK font is used.
      // `waypoints` defaults to no intermediate stops (a direct route).
      waypoints={waypoints ?? []}
      {...(fontFamily !== undefined && { fontFamily })}
      onArrive={wrappedOnArrive}
      onError={wrappedOnError}
      onCancelNavigation={wrappedOnCancelNavigation}
      onNavigationEnd={wrappedOnNavigationEnd}
      onMuteChange={wrappedOnMuteChange}
      onRouteProgressChange={wrappedOnRouteProgressChange}
      onLocationChange={wrappedOnLocationChange}
      onReroute={wrappedOnReroute}
    />
  );
});

const styles = StyleSheet.create({
  fill: { flex: 1 },
});
