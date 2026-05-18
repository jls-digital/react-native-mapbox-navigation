---
name: nitro-android-rebuild
description: How the example app's Android APK actually picks up Kotlin changes made in this Nitro Module library, and how to verify a rebuild really happened. Trigger when editing any `.kt`/`.swift` in `android/src` or `ios/`, or when "my native change isn't showing on the device" / "the dock/banner/etc. is still the same after I edited Kotlin".
---

## Always use the yarn scripts to build & run

For the standard edit → test loop, use the package.json scripts — **never** invoke gradle / xcodebuild / metro directly:

| Action | Command |
|---|---|
| Run example on Android emulator (rebuilds the library + APK) | `yarn example android` |
| Run example on iOS sim | `yarn example ios` |
| Start Metro for the example | `yarn example start` |
| Rebuild the library JS (`src` → `lib`) + regenerate nitrogen | `yarn prepare` |
| Regenerate Nitro native bindings only | `yarn nitrogen` |
| TypeScript + lint | `yarn typecheck && yarn lint` |

**Why not `./gradlew :app:installDebug` directly?** It builds and installs an APK but skips the JS bundle wiring and Metro hooks the RN/Expo scripts set up — the app can launch against stale JS or fail at runtime even though the native dex is correct. It also doesn't run pre-build hooks (Nitrogen invocation, etc.) that `yarn example android` triggers. Direct gradle is only acceptable for a fast type-check probe (`./gradlew :jls-digital_react-native-mapbox-navigation:compileDebugKotlin`) where no install is needed.

**Do not manually restart the app** after `yarn example android` (no `adb shell am force-stop` + `monkey` dance to "make sure the new dex is running"). The yarn script already restarts the app process on install. Chaining a manual restart is redundant noise.

The diagnostic recipes below (probe markers, dex grep, cache-poisoning escalation) still apply — they just live downstream of `yarn example android`, not as a replacement for it.

# How `@jls-digital/react-native-mapbox-navigation` ↔ example app linking actually works on Android

This repo is a **Nitro Module** (Margelo `react-native-nitro-modules`) developed at the root, with the example app in a yarn workspace at `example/`. The standard "edit `.kt` → `yarn example android` → see the change" loop has subtle traps. This skill captures the real mechanism + the diagnostic commands that have proven reliable in this repo.

## The linking chain

There is **no** `node_modules/@jls-digital/react-native-mapbox-navigation` symlink in the example. Yarn 4 + the root package being the library means the library is NOT a normal node dep of the example. The wiring instead goes:

1. **`example/react-native.config.js`** manually points autolinking at the repo root:
   ```js
   module.exports = {
     dependencies: {
       '@jls-digital/react-native-mapbox-navigation': { root: path.join(__dirname, '..') },
     },
   };
   ```
2. **`example/android/settings.gradle`** invokes `expoAutolinking.rnConfigCommand` which calls `@react-native-community/cli config` and writes `example/android/build/generated/autolinking/autolinking.json`. That JSON has `sourceDir = <repo>/android`.
3. **The React Native gradle plugin** (`com.facebook.react.settings`) reads the JSON and registers the library as an in-tree gradle subproject — `:jls-digital_react-native-mapbox-navigation` whose `projectDir` is the library's `android/` dir (not a published AAR).
4. **`apply from: '../nitrogen/generated/android/jlsdigital_reactnativemapboxnavigation+autolinking.gradle'`** inside the library's own `android/build.gradle` adds the nitrogen-generated Kotlin to the library's source set.
5. **`nitro.json`** at the repo root declares `implementationClassName: HybridReactNativeMapboxNavigation` and the android namespace. `yarn nitrogen` regenerates the nitrogen sources whenever the `.nitro.ts` spec changes.

So when the example builds, Gradle compiles the library's Kotlin in-place and packages it into the app's dex — there's no intermediate AAR install/publish step.

## What this means for "edit Kotlin → rebuild"

- **Pure native edit (`.kt`/`.swift`)** → run `yarn android` in `example/` (or `yarn example android` from root). **Do NOT** run `yarn prepare` — that only rebuilds `lib/` from `src/` and is irrelevant for native code.
- **Nitro spec edit (`src/**/*.nitro.ts`)** → run `yarn nitrogen` first, then `yarn example android`. Nitrogen regenerates `nitrogen/generated/android/kotlin/**` which the library compiles in. iOS analog: rerun `pod install` in `example/ios` after nitrogen.
- **Public TS API edit (`src/index.ts`)** → run `yarn prepare` so the consumed `lib/module/` is current; then `yarn example android` (which restarts Metro and picks up the new JS).
- **`app.json` plugin / podspec changes** → `cd example && npx expo prebuild --clean` then `yarn android`.

## The trap: "BUILD SUCCESSFUL in 7s — 362 up-to-date" can be a lie

Symptoms:
- `stat -f "%Sm %N"` shows the `.kt` source is **newer** than the compiled `.class` in `android/build/tmp/kotlin-classes/debug/.../*.class`.
- Or the APK in `example/android/app/build/outputs/apk/debug/app-debug.apk` does not contain a string you just added in Kotlin.

Most common causes (in order):
1. **Stale `example/android/build/generated/autolinking/autolinking.json`** pointing at an old library path after a directory move or yarn install rebuild.
2. **Gradle configuration cache** in `example/android/.gradle/configuration-cache/` pinning a stale subproject pointer.
3. **Kotlin daemon stuck** with an old classpath.
4. **`example/react-native.config.js` was edited but the autolinking JSON didn't regenerate** (Gradle hashes the file via the RN plugin; an unchanged-mtime edit can miss it).
5. **Hot Reload running off Metro's stale bundle while native code WAS rebuilt** — the dex you want IS in the APK but the running process still shows old JS state.

## The smoking-gun probe (use this every time you suspect cache poisoning)

Add a unique log marker string inside a Kotlin file in the library:

```kotlin
private val nitroProbeMarker = "NITRO_PROBE_<TODAY>_<TICKET>"
```

Rebuild, then grep the produced APK across all dex files (Nitro splits library code into `classes{2,3,4,5}.dex`):

```sh
APK=example/android/app/build/outputs/apk/debug/app-debug.apk
for d in classes.dex classes2.dex classes3.dex classes4.dex classes5.dex; do
  unzip -p "$APK" "$d" 2>/dev/null | strings | grep -q NITRO_PROBE_<TODAY> && echo "found in $d"
done
```

- **Marker present →** native rebuild is working; the bug is in your code (or in the JS/Metro layer or device-side caching).
- **Marker absent →** cache poisoning. Apply the recipe below.

## Cache-poisoning recipe (escalating)

```sh
# 1) Force-rebuild the library subproject only
cd example/android && ./gradlew :jls-digital_react-native-mapbox-navigation:clean :app:assembleDebug

# 2) Drop both build dirs + autolinking JSON
rm -rf example/android/build example/android/app/build android/build \
       example/android/build/generated/autolinking
cd example && yarn android

# 3) Drop Gradle local caches
cd example/android && ./gradlew --stop
rm -rf example/android/.gradle android/.gradle
cd example && yarn android

# 4) Nuclear — drop user-level Gradle build cache too
rm -rf ~/.gradle/caches/build-cache-* ~/.gradle/caches/transforms-*
```

## Verify a real rebuild without an emulator

```sh
# A) Source vs class mtime
SRC=android/src/main/java/com/margelo/nitro/jlsdigital/reactnativemapboxnavigation/ReactNativeMapboxNavigation.kt
CLS=android/build/tmp/kotlin-classes/debug/com/margelo/nitro/jlsdigital/reactnativemapboxnavigation/HybridReactNativeMapboxNavigation.class
stat -f "%Sm %N" "$SRC" "$CLS"

# B) Confirm gradle still includes the library subproject
cd example/android && ./gradlew projects | grep mapbox
# Expect: "Project ':jls-digital_react-native-mapbox-navigation'"

# C) Ask gradle why compileDebugKotlin is up-to-date
cd example/android && ./gradlew :jls-digital_react-native-mapbox-navigation:compileDebugKotlin --info 2>&1 | grep -E "UP-TO-DATE|Skipping|out of date|Build cache"

# D) APK size & inclusion sanity
ls -la example/android/app/build/outputs/apk/debug/app-debug.apk
unzip -l example/android/app/build/outputs/apk/debug/app-debug.apk | grep dex
```

## iOS analog (short version)

- Library Swift is at `ios/ReactNativeMapboxNavigation.swift`. The example Podfile references the library via `:path => "../.."`.
- Pure `.swift` edits → `yarn example ios` (or build in Xcode) — Pods compile in-place from the linked path.
- Nitrogen spec changes → `yarn nitrogen` then `cd example/ios && pod install` then `yarn example ios`.
- Smoking-gun probe: same idea, grep the `.app` bundle: `strings example/ios/build/.../ReactNativeMapboxNavigationExample.app/ReactNativeMapboxNavigationExample | grep NITRO_PROBE`.

## Why "yarn prepare" is NOT the answer for native edits

`yarn prepare` runs `bob build + yarn nitrogen`. It outputs to `lib/` (consumed by JS) and refreshes `nitrogen/generated/`. **The example's gradle subproject reads `android/src/main/java` directly, not `lib/`.** Running `yarn prepare` after a pure `.kt` edit does nothing useful for the APK; people often run it as a "ritual" and assume that's what got the change in — it isn't; the next `yarn example android` was.

## Tested workflow that always works in this repo

```sh
# from repo root, after editing android/src/.../*.kt
cd example && yarn android
# verify the change made it (one-time, when uncertain):
APK=$PWD/android/app/build/outputs/apk/debug/app-debug.apk
unzip -p "$APK" classes3.dex | strings | grep <yourMarker>
```

Anything more than this is only needed when you've also touched the nitro spec, the public JS API, or the app config plugin.
