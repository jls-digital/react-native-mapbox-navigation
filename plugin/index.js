const {
  withInfoPlist,
  withStringsXml,
  AndroidConfig,
} = require('@expo/config-plugins');

const PLUGIN_NAME = '@jls-digital/react-native-mapbox-navigation';
const ANDROID_STRING_RESOURCE_NAME = 'mapbox_access_token';

function resolveAccessToken(props) {
  const fromConfig = props && props.accessToken;
  const fromEnv = process.env.MAPBOX_ACCESS_TOKEN;
  const token = fromConfig || fromEnv;
  if (!token) {
    throw new Error(
      `[${PLUGIN_NAME}] Missing Mapbox access token. Pass { accessToken: 'pk...' } in the plugin config, or set MAPBOX_ACCESS_TOKEN in the environment. See the library README for setup instructions.`
    );
  }
  return token;
}

const withMapboxAccessTokenIOS = (config, props) => {
  return withInfoPlist(config, (cfg) => {
    cfg.modResults.MBXAccessToken = resolveAccessToken(props);
    return cfg;
  });
};

const withMapboxAccessTokenAndroid = (config, props) => {
  return withStringsXml(config, (cfg) => {
    cfg.modResults = AndroidConfig.Strings.setStringItem(
      [
        {
          $: { name: ANDROID_STRING_RESOURCE_NAME, translatable: 'false' },
          _: resolveAccessToken(props),
        },
      ],
      cfg.modResults
    );
    return cfg;
  });
};

const withMapboxNavigation = (config, props) => {
  // Resolve once up front so the prebuild fails loudly on missing token
  // before Expo starts mutating platform files.
  resolveAccessToken(props);
  config = withMapboxAccessTokenIOS(config, props);
  config = withMapboxAccessTokenAndroid(config, props);
  return config;
};

module.exports = withMapboxNavigation;
