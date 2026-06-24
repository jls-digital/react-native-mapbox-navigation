// swift-tools-version: 5.9
import PackageDescription

// ── Off-device unit-test package ──────────────────────
// Compiles the Foundation-only pure units extracted from
// `ReactNativeMapboxNavigation.swift` and runs them via `swift test` on the
// macOS host — no simulator, no Nitro runtime, no UIKit, and crucially NO
// Mapbox SDK dependency (so it's fast and CI-robust, with no binary
// frameworks to fetch).
//
// The pure source files live one level up in `ios/` (where the pod compiles
// them). SwiftPM forbids a target referencing sources outside the package
// root, so `PureUnits/` holds symlinks to the real files — no duplication.
//
// Deliberately excluded units: `RouteErrorClassifier` (imports
// MapboxDirections — its message logic is split into the Foundation-only
// `RouteErrorMessageClassifier`, which IS tested here) and
// `MapboxProviderStore` (imports MapboxNavigationCore, no macOS slice — its
// pure refcount logic lives in `ProviderStoreState`, tested here).
let package = Package(
  name: "ReactNativeMapboxNavigationTests",
  platforms: [.macOS(.v12)],
  targets: [
    .target(name: "PureUnits", path: "PureUnits"),
    .testTarget(name: "Tests", dependencies: ["PureUnits"], path: "Tests"),
  ]
)
