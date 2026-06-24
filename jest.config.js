/**
 * Jest config for the library's off-device TypeScript unit tests.
 *
 * Uses the `react-native` preset so @testing-library/react-native can render
 * the component to a host tree (the preset wires up the Metro/babel transform,
 * `__DEV__`, and the act environment). The component's native imports
 * (`react-native-nitro-modules`, the Nitro host view) are still mocked per-test
 * — none of the real native runtime is needed.
 *
 * @type {import('jest').Config}
 */
module.exports = {
  preset: 'react-native',
  // Don't depend on watchman — it isn't present in CI and the file crawl is
  // tiny here anyway.
  watchman: false,
  roots: ['<rootDir>/src'],
  testMatch: ['**/__tests__/**/*.test.{ts,tsx}'],
};
