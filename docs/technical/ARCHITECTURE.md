# Architecture

> Status: Draft — to be expanded as implementation progresses

## Overview

`react-native-mapbox-navigation` is a React Native Fabric native component that wraps the Mapbox Navigation SDK v3.20.x on both iOS and Android.

## Repo Structure

```
src/                        # TypeScript source + Nitro spec
ios/                        # Swift native module
android/                    # Kotlin native module
nitrogen/                   # Auto-generated Nitro bindings (do not edit)
example/                    # Development / demo app
docs/
├── spec/                   # Product & API specification
│   └── SPEC.md
└── technical/              # Architecture & implementation docs
    └── ARCHITECTURE.md     (this file)
```

## Native Bridge

- **Bridge layer:** Nitro Modules (`react-native-nitro-modules`) — `.nitro.ts` spec defines the typed bridge, `nitrogen` CLI generates native bindings
- **iOS:** Swift view implementing `HybridReactNativeMapboxNavigationSpec`, wrapping `NavigationViewController`
- **Android:** Kotlin view implementing `HybridReactNativeMapboxNavigationSpec`, wrapping Mapbox `NavigationView`

## SDK Integration

| Platform | SDK | Distribution |
|----------|-----|-------------|
| iOS | `MapboxNavigationUIKit` + `MapboxNavigationCore` | SPM (via `spm_dependency` in podspec) |
| Android | `com.mapbox.navigationcore:android:3.20.x` | Maven / Gradle |

See [SPEC.md](../spec/SPEC.md) §6 and §9 for the full architecture diagram and SDK details.
