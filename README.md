# @jls-digital/react-native-mapbox-navigation

React Native wrapper around Mapbox Navigation SDKs for turn-by-turn driving navigation.

New Architecture only (Fabric + Codegen) · Mapbox Navigation SDK v3.20.x

## Documentation

- **[Product Spec](docs/spec/SPEC.md)** — requirements, component API, error codes, supported languages
- **[Architecture](docs/technical/ARCHITECTURE.md)** — native bridge design, SDK integration, repo layout

## Installation


```sh
npm install @jls-digital/react-native-mapbox-navigation react-native-nitro-modules

> `react-native-nitro-modules` is required as this library relies on [Nitro Modules](https://nitro.margelo.com/).
```


## Mapbox tokens

Using this library requires **two** separate Mapbox tokens. They serve different purposes and live in different places. Get both from your [Mapbox account](https://account.mapbox.com/).

### 1. Public access token — runtime (`pk.*`)

Used by the SDK at runtime to request routes, tiles, and voice instructions. It ships inside the host app's binary.

**Expo (CNG) consumers — use the bundled config plugin:**

```json
{
  "expo": {
    "plugins": [
      ["@jls-digital/react-native-mapbox-navigation", { "accessToken": "pk.your_public_token" }]
    ]
  }
}
```

Omit `accessToken` to fall back to `process.env.MAPBOX_ACCESS_TOKEN` at prebuild time (recommended for repos — keep the token in a local `.env` that's gitignored). The plugin writes the token into `Info.plist` (`MBXAccessToken`) and `res/values/mapbox_access_token.xml` each prebuild.

**Bare React Native consumers — set the values directly:**

- iOS — add `MBXAccessToken` to `Info.plist`:

  ```xml
  <key>MBXAccessToken</key>
  <string>pk.your_public_token</string>
  ```

- Android — add a string resource at `app/src/main/res/values/mapbox_access_token.xml`:

  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <resources>
    <string name="mapbox_access_token" translatable="false">pk.your_public_token</string>
  </resources>
  ```

The library never reads this token from code — both the iOS and Android SDKs resolve it from the platform's resource system.

### 2. Secret download token — build time (`sk.*`, scope: `DOWNLOADS:READ`)

Used at build time to authenticate against Mapbox's private SPM registry (iOS) and Maven repository (Android) when fetching the Navigation SDK binaries. It must **never** be committed to the repo, placed in `.env`, or embedded in the app binary — it is a build credential only.

Both the iOS and Android build configurations look for the token in a **single env var**, `MAPBOX_DOWNLOADS_TOKEN`, with a user-level fallback. This means the same setup works for local machines and CI runners.

**Local development (user-level, set once):**

- iOS (SPM) — add to `~/.netrc`:

  ```
  machine api.mapbox.com
    login mapbox
    password sk.your_secret_token
  ```

- Android (Gradle) — add to `~/.gradle/gradle.properties`:

  ```properties
  MAPBOX_DOWNLOADS_TOKEN=sk.your_secret_token
  ```

  The library's `build.gradle` resolves the token in priority order: `System.getenv("MAPBOX_DOWNLOADS_TOKEN")` → `project.findProperty("MAPBOX_DOWNLOADS_TOKEN")`.

**CI (expose as a pipeline secret, read from the environment):**

Define `MAPBOX_DOWNLOADS_TOKEN` as a masked secret in your CI provider. At build time:

- Android picks it up automatically from the environment (no extra step).
- iOS needs a `.netrc` file; generate it at job start from the env var:

  ```sh
  cat > ~/.netrc <<EOF
  machine api.mapbox.com
    login mapbox
    password $MAPBOX_DOWNLOADS_TOKEN
  EOF
  chmod 600 ~/.netrc
  ```

  Keep this step inside the job's scratch home — do not persist the `.netrc` to a cache or artifact.

### Example app setup

The `example/` app consumes the library (and its config plugin) the same way an Expo consumer would — so it dogfoods the plugin path:

1. Copy the template: `cp example/.env.example example/.env`
2. Fill in `MAPBOX_ACCESS_TOKEN` (public) in `example/.env`.
3. Make sure your user-level `.netrc` and `gradle.properties` carry the secret download token (see above) — these are outside the repo and shared across all Mapbox projects on your machine.
4. Run `yarn example prebuild` and then `yarn example ios` / `yarn example android`. The plugin picks up `MAPBOX_ACCESS_TOKEN` from `example/.env` and injects it into the regenerated native files.

`example/.env` is gitignored (see root `.gitignore`); only `example/.env.example` is tracked.


## Usage


```js
import { ReactNativeMapboxNavigationView } from "@jls-digital/react-native-mapbox-navigation";

// ...

<ReactNativeMapboxNavigationView color="tomato" />
```


## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
