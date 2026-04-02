# CLAUDE.md

## What is this repo?

`@jls-digital/react-native-mapbox-navigation` — a React Native library wrapping Mapbox Navigation SDK v3.20.x for turn-by-turn driving navigation. New Architecture only (Fabric + Codegen).

## Repo Structure

```
src/                        # TypeScript source + Codegen specs
ios/                        # Swift native module
android/                    # Kotlin native module
example/                    # Dev / demo app
docs/
├── spec/SPEC.md            # Full product & API specification
└── technical/ARCHITECTURE.md  # Architecture & implementation notes
```

## Documentation

- **[Product Spec](docs/spec/SPEC.md)** — requirements, component API, error codes, supported languages
- **[Architecture](docs/technical/ARCHITECTURE.md)** — native bridge design, SDK integration, repo layout

## Key Commands

```sh
yarn install          # install dependencies
yarn build            # build the library
yarn lint             # run ESLint
yarn typecheck        # run TypeScript type checking
yarn example start    # start the example app Metro bundler
```
