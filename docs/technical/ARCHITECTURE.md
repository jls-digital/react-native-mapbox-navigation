# Architecture

> Status: Draft — to be expanded as implementation progresses

## Overview

`react-native-mapbox-navigation` is a React Native Fabric native component that wraps the Mapbox Navigation SDK v3.20.x on both iOS and Android.

## Repo Structure

```
src/                        # TypeScript source + Codegen specs
ios/                        # Swift native module (+ ObjC++ interop)
android/                    # Kotlin native module
example/                    # Development / demo app
docs/
├── spec/                   # Product & API specification
│   └── SPEC.md
└── technical/              # Architecture & implementation docs
    └── ARCHITECTURE.md     (this file)
```

## Native Bridge

- **Bridge layer:** Fabric Codegen (`codegenNativeComponent` + `codegenNativeCommands`)
- **iOS:** Swift view wrapping `NavigationViewController`, exposed via ObjC++ Fabric view manager
- **Android:** Kotlin view wrapping Mapbox `NavigationView`, exposed via Fabric `SimpleViewManager`

## SDK Integration

| Platform | SDK | Distribution |
|----------|-----|-------------|
| iOS | `MapboxNavigationUIKit` + `MapboxNavigationCore` | SPM (via `spm_dependency` in podspec) |
| Android | `com.mapbox.navigationcore:android:3.20.x` | Maven / Gradle |

See [SPEC.md](../spec/SPEC.md) §6 and §9 for the full architecture diagram and SDK details.
