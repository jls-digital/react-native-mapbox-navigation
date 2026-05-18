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

// The Mapbox Navigation SDK is hosted on a private Maven repository that
// requires basic auth with a downloads token. Inject the repo into the
// app's project-level build.gradle so both the library and its transitive
// Mapbox dependencies resolve. The token is read at build time from the
// MAPBOX_DOWNLOADS_TOKEN Gradle property or environment variable.
const ANDROID_MAPBOX_REPO_MARKER =
  '// [react-native-mapbox-navigation] mapbox-maven-repo';
const ANDROID_MAPBOX_REPO_SNIPPET = `
  maven {
    url 'https://api.mapbox.com/downloads/v2/releases/maven'
    authentication { basic(BasicAuthentication) }
    credentials {
      username = 'mapbox'
      def mbxDl = project.findProperty('MAPBOX_DOWNLOADS_TOKEN') ?: System.getenv('MAPBOX_DOWNLOADS_TOKEN')
      if (mbxDl == null) {
        throw new GradleException(
          '[react-native-mapbox-navigation] MAPBOX_DOWNLOADS_TOKEN not found. ' +
          'Set it in ~/.gradle/gradle.properties or the environment.'
        )
      }
      password = mbxDl
    }
  }`;

const withMapboxMavenRepoAndroid = (config) => {
  return withDangerousMod(config, [
    'android',
    (cfg) => {
      const buildGradlePath = path.join(
        cfg.modRequest.platformProjectRoot,
        'build.gradle'
      );
      if (!fs.existsSync(buildGradlePath)) return cfg;
      const original = fs.readFileSync(buildGradlePath, 'utf8');
      if (original.includes(ANDROID_MAPBOX_REPO_MARKER)) return cfg;

      // Insert into the first allprojects { repositories { ... } } block.
      const regex = /(allprojects\s*\{\s*repositories\s*\{)/;
      if (!regex.test(original)) return cfg;
      const updated = original.replace(
        regex,
        `$1\n    ${ANDROID_MAPBOX_REPO_MARKER}${ANDROID_MAPBOX_REPO_SNIPPET}\n`
      );
      fs.writeFileSync(buildGradlePath, updated);
      return cfg;
    },
  ]);
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
  config = withMapboxMavenRepoAndroid(config);
  config = withMapboxPodfilePostInstallCall(config);
  return config;
};

module.exports = withMapboxNavigation;
