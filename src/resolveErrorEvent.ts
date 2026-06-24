import type { ErrorEvent, MapboxNavigationErrorCode } from './types';

/**
 * Set of error codes recognized by this version of the library. Declared as a
 * typed tuple so adding/removing a {@link MapboxNavigationErrorCode} member is a
 * compile error here until this list is updated, keeping native and JS in sync.
 */
export const KNOWN_ERROR_CODES: readonly MapboxNavigationErrorCode[] = [
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
export const FALLBACK_ERROR_CODE: MapboxNavigationErrorCode = 'SDK_INIT_FAILED';

/**
 * Validate and normalize a `(code, message)` pair emitted by the native layer
 * into the typed payload delivered to {@link MapboxNavigationProps.onError}.
 *
 * - A recognized code passes through unchanged.
 * - An unrecognized code is reported as {@link FALLBACK_ERROR_CODE}, with the
 *   raw code preserved (prefixed) in the message and a `__DEV__`-only warning.
 *
 * Pure and side-effect-free apart from the dev warning, so it is unit-tested
 * directly without rendering the component.
 */
export function resolveErrorEvent(
  code: string,
  message: string
): ErrorEvent['nativeEvent'] {
  if (KNOWN_ERROR_CODE_SET.has(code)) {
    // Narrow only after the runtime check — never blind-cast an arbitrary
    // native string to the enum (see FALLBACK_ERROR_CODE).
    return { code: code as MapboxNavigationErrorCode, message };
  }
  if (__DEV__) {
    console.warn(
      `MapboxNavigation: received unrecognized native error code "${code}"; ` +
        `reporting it as "${FALLBACK_ERROR_CODE}". ` +
        'The raw code is preserved in the error message.'
    );
  }
  // Preserve the raw native code so consumers can still see it even though the
  // typed `code` field is forced to a known member.
  return { code: FALLBACK_ERROR_CODE, message: `[${code}] ${message}` };
}
