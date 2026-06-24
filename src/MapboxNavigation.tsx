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

/**
 * Set of error codes recognized by this version of the library. Declared as a
 * typed tuple so adding/removing a {@link MapboxNavigationErrorCode} member is a
 * compile error here until this list is updated, keeping native and JS in sync.
 */
const KNOWN_ERROR_CODES: readonly MapboxNavigationErrorCode[] = [
  'ROUTE_CALCULATION_FAILED',
  'GPS_UNAVAILABLE',
  'GPS_PERMISSION_DENIED',
  'NETWORK_ERROR',
  'INVALID_COORDINATES',
  'SDK_INIT_FAILED',
];
const KNOWN_ERROR_CODE_SET = new Set<string>(KNOWN_ERROR_CODES);

/**
 * Fallback code used when the native layer emits a code this JS version does
 * not recognize (e.g. a newer SDK code shipped by only one platform). We never
 * silently cast an unknown string to {@link MapboxNavigationErrorCode}: an
 * unrecognized code would let consumer `switch` statements that rely on
 * exhaustiveness fall through with no signal. Instead the raw code is preserved
 * in the human-readable `message` and surfaced via a dev-only warning.
 */
const FALLBACK_ERROR_CODE: MapboxNavigationErrorCode = 'SDK_INIT_FAILED';

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
            const isKnown = KNOWN_ERROR_CODE_SET.has(code);
            // Narrow only after the runtime check — never blind-cast an
            // arbitrary native string to the enum (see FALLBACK_ERROR_CODE).
            const resolvedCode = isKnown
              ? (code as MapboxNavigationErrorCode)
              : FALLBACK_ERROR_CODE;
            // Preserve the raw native code so consumers can still see it even
            // though the typed `code` field is forced to a known member.
            const resolvedMessage = isKnown ? message : `[${code}] ${message}`;
            if (!isKnown && __DEV__) {
              console.warn(
                `MapboxNavigation: received unrecognized native error code "${code}"; ` +
                  `reporting it as "${FALLBACK_ERROR_CODE}". ` +
                  'The raw code is preserved in the error message.'
              );
            }
            onError({
              nativeEvent: {
                code: resolvedCode,
                message: resolvedMessage,
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
      hybridRef={wrappedHybridRef}
      origin={origin}
      destination={destination}
      waypoints={waypoints}
      language={language}
      shouldSimulateRoute={shouldSimulateRoute}
      simulationSpeedMultiplier={simulationSpeedMultiplier}
      mute={mute}
      colorScheme={colorScheme}
      fontFamily={fontFamily}
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
