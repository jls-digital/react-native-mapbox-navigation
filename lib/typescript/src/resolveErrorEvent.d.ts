import type { ErrorEvent, MapboxNavigationErrorCode } from './types.js';
/**
 * Set of error codes recognized by this version of the library. Declared as a
 * typed tuple so adding/removing a {@link MapboxNavigationErrorCode} member is a
 * compile error here until this list is updated, keeping native and JS in sync.
 */
export declare const KNOWN_ERROR_CODES: readonly MapboxNavigationErrorCode[];
/**
 * Fallback code used when the native layer emits a code this JS version does
 * not recognize (e.g. a newer SDK code shipped by only one platform). We never
 * silently cast an unknown string to {@link MapboxNavigationErrorCode}: an
 * unrecognized code would let consumer `switch` statements that rely on
 * exhaustiveness fall through with no signal. Instead the raw code is preserved
 * in the human-readable `message` and surfaced via a dev-only warning.
 */
export declare const FALLBACK_ERROR_CODE: MapboxNavigationErrorCode;
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
export declare function resolveErrorEvent(code: string, message: string): ErrorEvent['nativeEvent'];
//# sourceMappingURL=resolveErrorEvent.d.ts.map