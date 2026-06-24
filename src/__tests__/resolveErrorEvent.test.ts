/// <reference types="jest" />
import {
  FALLBACK_ERROR_CODE,
  KNOWN_ERROR_CODES,
  resolveErrorEvent,
} from '../resolveErrorEvent';

describe('resolveErrorEvent', () => {
  let warnSpy: jest.SpyInstance;

  beforeEach(() => {
    warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
  });

  afterEach(() => {
    warnSpy.mockRestore();
    (globalThis as { __DEV__?: boolean }).__DEV__ = true;
  });

  it('passes every recognized code through unchanged, with no warning', () => {
    for (const code of KNOWN_ERROR_CODES) {
      const result = resolveErrorEvent(code, 'some message');
      expect(result).toEqual({ code, message: 'some message' });
    }
    expect(warnSpy).not.toHaveBeenCalled();
  });

  // Parity link: these are exactly the codes the native RouteErrorClassifier /
  // RouteErrorMessageClassifier can emit (see the Kotlin + Swift parity tables).
  // The JS layer must recognize every one of them so they're never remapped to
  // the fallback.
  it('recognizes all codes the native classifiers emit', () => {
    const nativeEmitted = [
      'NETWORK_ERROR',
      'SDK_INIT_FAILED',
      'INVALID_COORDINATES',
      'ROUTE_CALCULATION_FAILED',
    ];
    for (const code of nativeEmitted) {
      expect(resolveErrorEvent(code, 'm')).toEqual({ code, message: 'm' });
    }
  });

  it('remaps an unrecognized code to the fallback and preserves the raw code', () => {
    const result = resolveErrorEvent('SOME_FUTURE_CODE', 'boom');
    expect(result).toEqual({
      code: FALLBACK_ERROR_CODE,
      message: '[SOME_FUTURE_CODE] boom',
    });
  });

  it('warns once (in __DEV__) for an unrecognized code', () => {
    resolveErrorEvent('SOME_FUTURE_CODE', 'boom');
    expect(warnSpy).toHaveBeenCalledTimes(1);
    expect(warnSpy.mock.calls[0]![0]).toContain('SOME_FUTURE_CODE');
    expect(warnSpy.mock.calls[0]![0]).toContain(FALLBACK_ERROR_CODE);
  });

  it('does not warn when __DEV__ is false', () => {
    (globalThis as { __DEV__?: boolean }).__DEV__ = false;
    const result = resolveErrorEvent('SOME_FUTURE_CODE', 'boom');
    expect(result.code).toBe(FALLBACK_ERROR_CODE);
    expect(warnSpy).not.toHaveBeenCalled();
  });

  it('uses SDK_INIT_FAILED as the fallback code', () => {
    expect(FALLBACK_ERROR_CODE).toBe('SDK_INIT_FAILED');
  });
});
