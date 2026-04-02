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
