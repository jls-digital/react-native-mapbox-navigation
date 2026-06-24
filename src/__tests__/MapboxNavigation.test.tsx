/// <reference types="jest" />
import { createRef, type RefObject } from 'react';
import { act, render, screen } from '@testing-library/react-native';
import { MapboxNavigation } from '../MapboxNavigation';
import type {
  Coordinates,
  MapboxNavigationProps,
  MapboxNavigationRef,
} from '../types';

// `callback` from Nitro just marks a JS function as native-callable; for these
// off-device tests it is an identity wrapper, so the props handed to the native
// view ARE the functions the component built.
jest.mock('react-native-nitro-modules', () => ({
  callback: (fn: unknown) => fn,
}));
// Replace the real Nitro host component (which needs the native runtime) with a
// plain host View carrying a testID, so RNTL can locate it and we can read the
// wrapped props the component passed down.
jest.mock('../ReactNativeMapboxNavigationView.native', () => {
  const React = require('react');
  const { View } = require('react-native');
  return {
    ReactNativeMapboxNavigationView: (props: Record<string, unknown>) =>
      React.createElement(View, { testID: 'nativeView', ...props }),
  };
});

/** The wrapped props the component passes down — only the parts we exercise. */
interface ViewHandlers {
  hybridRef: (view: {
    recenterCamera: () => void;
    showRouteOverview: () => void;
  }) => void;
  onArrive?: (destination: Coordinates) => void;
  onError?: (code: string, message: string) => void;
  onMuteChange?: (isMuted: boolean) => void;
  onRouteProgressChange?: (progress: unknown) => void;
  onLocationChange?: (latitude: number, longitude: number) => void;
  onCancelNavigation?: () => void;
  onNavigationEnd?: () => void;
  onReroute?: () => void;
}

const ORIGIN: Coordinates = { latitude: 47.37, longitude: 8.54 };
const DESTINATION: Coordinates = { latitude: 46.95, longitude: 7.45 };

function renderWith(
  props: Partial<MapboxNavigationProps>,
  ref?: RefObject<MapboxNavigationRef | null>
): ViewHandlers {
  render(
    <MapboxNavigation
      ref={ref}
      origin={ORIGIN}
      destination={DESTINATION}
      {...props}
    />
  );
  return screen.getByTestId('nativeView').props as unknown as ViewHandlers;
}

describe('MapboxNavigation event wrappers', () => {
  let warnSpy: jest.SpyInstance;

  beforeEach(() => {
    warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
  });
  afterEach(() => warnSpy.mockRestore());

  it('wraps onArrive into a { nativeEvent: { destination } } event', () => {
    const onArrive = jest.fn();
    renderWith({ onArrive }).onArrive!(DESTINATION);
    expect(onArrive).toHaveBeenCalledWith({
      nativeEvent: { destination: DESTINATION },
    });
  });

  it('passes a recognized error code straight through', () => {
    const onError = jest.fn();
    renderWith({ onError }).onError!('NETWORK_ERROR', 'offline');
    expect(onError).toHaveBeenCalledWith({
      nativeEvent: { code: 'NETWORK_ERROR', message: 'offline' },
    });
  });

  it('remaps an unknown error code to the fallback (component wiring)', () => {
    const onError = jest.fn();
    renderWith({ onError }).onError!('FUTURE_CODE', 'weird');
    expect(onError).toHaveBeenCalledWith({
      nativeEvent: { code: 'SDK_INIT_FAILED', message: '[FUTURE_CODE] weird' },
    });
    expect(warnSpy).toHaveBeenCalledTimes(1);
  });

  it('wraps onMuteChange into a { nativeEvent: { isMuted } } event', () => {
    const onMuteChange = jest.fn();
    renderWith({ onMuteChange }).onMuteChange!(true);
    expect(onMuteChange).toHaveBeenCalledWith({
      nativeEvent: { isMuted: true },
    });
  });

  it('forwards the route progress payload under nativeEvent', () => {
    const onRouteProgressChange = jest.fn();
    const progress = {
      distanceTraveled: 10,
      distanceRemaining: 90,
      durationRemaining: 60,
      fractionTraveled: 0.1,
    };
    renderWith({ onRouteProgressChange }).onRouteProgressChange!(progress);
    expect(onRouteProgressChange).toHaveBeenCalledWith({
      nativeEvent: progress,
    });
  });

  it('wraps onLocationChange into a { nativeEvent: { latitude, longitude } } event', () => {
    const onLocationChange = jest.fn();
    renderWith({ onLocationChange }).onLocationChange!(1.5, 2.5);
    expect(onLocationChange).toHaveBeenCalledWith({
      nativeEvent: { latitude: 1.5, longitude: 2.5 },
    });
  });

  it('passes through handlers that need no payload mapping', () => {
    const onCancelNavigation = jest.fn();
    const onNavigationEnd = jest.fn();
    const onReroute = jest.fn();
    const view = renderWith({
      onCancelNavigation,
      onNavigationEnd,
      onReroute,
    });
    expect(view.onCancelNavigation).toBe(onCancelNavigation);
    expect(view.onNavigationEnd).toBe(onNavigationEnd);
    expect(view.onReroute).toBe(onReroute);
  });

  it('omits a wrapped handler when the corresponding prop is absent', () => {
    const view = renderWith({});
    expect(view.onArrive).toBeUndefined();
    expect(view.onError).toBeUndefined();
    expect(view.onMuteChange).toBeUndefined();
  });

  it('exposes recenterCamera / showRouteOverview that forward to the native view', () => {
    const ref = createRef<MapboxNavigationRef>();
    const view = renderWith({}, ref);
    const nativeView = {
      recenterCamera: jest.fn(),
      showRouteOverview: jest.fn(),
    };
    act(() => view.hybridRef(nativeView));

    ref.current!.recenterCamera();
    ref.current!.showRouteOverview();
    expect(nativeView.recenterCamera).toHaveBeenCalledTimes(1);
    expect(nativeView.showRouteOverview).toHaveBeenCalledTimes(1);
  });
});
