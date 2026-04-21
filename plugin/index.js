const fs = require('fs');
const path = require('path');
const {
  withInfoPlist,
  withStringsXml,
  withDangerousMod,
  AndroidConfig,
} = require('@expo/config-plugins');

const PLUGIN_NAME = '@jls-digital/react-native-mapbox-navigation';
const ANDROID_STRING_RESOURCE_NAME = 'mapbox_access_token';
const PODFILE_HOOK_CALL =
  'react_native_mapbox_navigation_post_install(installer)';
const PODFILE_HOOK_MARKER =
  '# [react-native-mapbox-navigation] embed-transitive-spm-frameworks';

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

// Inject a call to `react_native_mapbox_navigation_post_install(installer)`
// into the generated Podfile's post_install block so the library's helper
// (loaded by the podspec) runs during `pod install` and embeds Mapbox's
// transitive SPM frameworks into the app bundle. Bare RN consumers add
// the same line by hand (README); this keeps Expo CNG consumers zero-config.
const withMapboxPodfilePostInstallCall = (config) => {
  return withDangerousMod(config, [
    'ios',
    (cfg) => {
      const podfilePath = path.join(
        cfg.modRequest.platformProjectRoot,
        'Podfile'
      );
      if (!fs.existsSync(podfilePath)) return cfg;
      const original = fs.readFileSync(podfilePath, 'utf8');
      if (original.includes(PODFILE_HOOK_MARKER)) return cfg;

      const reactNativePostInstallRegex =
        /(react_native_post_install\([\s\S]*?\n\s*\))/m;
      if (!reactNativePostInstallRegex.test(original)) return cfg;

      const updated = original.replace(
        reactNativePostInstallRegex,
        (match) =>
          `${match}\n    ${PODFILE_HOOK_MARKER}\n    ${PODFILE_HOOK_CALL}`
      );
      fs.writeFileSync(podfilePath, updated);
      return cfg;
    },
  ]);
};

const withMapboxNavigation = (config, props) => {
  // Resolve once up front so the prebuild fails loudly on missing token
  // before Expo starts mutating platform files.
  resolveAccessToken(props);
  config = withMapboxAccessTokenIOS(config, props);
  config = withMapboxAccessTokenAndroid(config, props);
  config = withMapboxPodfilePostInstallCall(config);
  return config;
};

module.exports = withMapboxNavigation;
