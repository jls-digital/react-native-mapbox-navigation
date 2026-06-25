const path = require('path');
const { getConfig } = require('react-native-builder-bob/babel-config');
const pkg = require('../package.json');

const root = path.resolve(__dirname, '..');

module.exports = function (api) {
  api.cache(true);

  const config = getConfig(
    {
      presets: ['babel-preset-expo'],
    },
    { root, pkg }
  );

  // Expo SDK 56 / Metro computes the transform cache key by loading this Babel
  // config WITHOUT a filename (@expo/metro-config `getCacheKey`). bob's
  // monorepo override uses a string `include` path to apply its preset to the
  // linked library source, and Babel throws "Configuration contains
  // string/RegExp pattern, but no filename was passed to Babel" when it can't
  // evaluate that pattern filename-less — which crashes the transformer for
  // every file ("Cannot read properties of undefined (reading 'transformFile')").
  // Convert the string matcher to a function matcher: it returns false at
  // cache-key time (no filename) and still matches the library source at
  // transform time (filename present), preserving bob's behaviour.
  config.overrides = (config.overrides ?? []).map((override) =>
    typeof override.include === 'string'
      ? {
          ...override,
          include: (filename) =>
            typeof filename === 'string' &&
            filename.startsWith(override.include),
        }
      : override
  );

  return config;
};
