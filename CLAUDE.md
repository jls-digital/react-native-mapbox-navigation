# CLAUDE.md

## What is this repo?

`@jls-digital/react-native-mapbox-navigation` — a React Native library wrapping Mapbox Navigation SDK v3.20.x for turn-by-turn driving navigation. New Architecture only (Fabric + Nitro Modules).

## Repo Structure

```
src/                        # TypeScript source + Nitro spec
ios/                        # Swift native module
android/                    # Kotlin native module
nitrogen/                   # Auto-generated Nitro bindings (do not edit)
example/                    # Dev / demo app (Expo)
docs/
├── spec/SPEC.md            # Full product & API specification
├── technical/ARCHITECTURE.md  # Architecture & implementation notes
└── test-cases/TEST_CASES.md   # Natural-language E2E test cases
```

## Documentation

- **[Product Spec](docs/spec/SPEC.md)** — requirements, component API, error codes, supported languages
- **[Architecture](docs/technical/ARCHITECTURE.md)** — native bridge design, SDK integration, repo layout
- **[Mapbox SDK Research](docs/technical/MAPBOX_SDK_RESEARCH.md)** — per-requirement iOS / Android API map with code snippets, doc links, and open questions
- **[Test Cases](docs/test-cases/TEST_CASES.md)** — Maestro E2E test case descriptions

## Key Commands

```sh
yarn install          # install dependencies
yarn prepare          # build the library (bob build + nitrogen)
yarn nitrogen         # regenerate Nitro native bindings from .nitro.ts spec
yarn lint             # run ESLint
yarn typecheck        # run TypeScript type checking
yarn example start    # start the example app Metro bundler
yarn example ios      # build & run example on iOS simulator
yarn example android  # build & run example on Android emulator
```

## Verification workflow

After making changes, always:

1. `yarn typecheck` — verify TypeScript compiles
2. `yarn lint` — verify no lint errors
3. `yarn prepare` — verify the library builds (module + types + nitrogen)
4. Build & run the example app on a simulator to verify changes visually
5. Use Maestro / mobile-interact MCP to interact with the running app and verify UI flows
