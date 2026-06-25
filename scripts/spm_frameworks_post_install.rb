# frozen_string_literal: true
#
# Podfile post-install helper that embeds Mapbox's transitive SPM
# frameworks into the consuming app's bundle.
#
# Why this is needed
# ------------------
# React Native's `spm_dependency` helper (RN ≥ 0.75) links SPM products
# against the library's Pod target, but CocoaPods' Copy Frameworks script
# only iterates pod-authored frameworks. Mapbox Navigation v3 ships as
# five dynamic frameworks (MapboxCommon, MapboxCoreMaps,
# MapboxNavigationNative, Turf, MapboxMaps) that bleed through as
# `@rpath/` load commands on our pod's binary but never land in the app
# bundle's Frameworks/ directory — so the app crashes at launch with
# "Library not loaded: @rpath/Turf.framework/Turf".
#
# This helper patches the generated Copy Frameworks script with
# `install_framework` calls for each transitive Mapbox framework,
# reusing CocoaPods' own install pipeline.
#
# Usage
# -----
# The library's podspec `load`s this file, so the helper is in scope by
# the time `pod install` runs the Podfile's post_install block. Call it
# from your Podfile:
#
#   post_install do |installer|
#     react_native_post_install(installer, ...) # existing RN call
#     react_native_mapbox_navigation_post_install(installer)
#   end
#
# Expo (CNG) consumers get this wired automatically by the library's
# config plugin; bare RN consumers add the one line manually.

require 'pathname'

# `MapboxNavigationCore`, `MapboxNavigationUIKit`, and `MapboxDirections`
# get statically absorbed into the consuming pod's dylib and don't need
# separate embedding. The ones below are the genuinely-dynamic transitives.
REACT_NATIVE_MAPBOX_NAVIGATION_TRANSITIVE_FRAMEWORKS = %w[
  Turf
  MapboxCommon
  MapboxCoreMaps
  MapboxNavigationNative
  MapboxMaps
].freeze

REACT_NATIVE_MAPBOX_NAVIGATION_MARKER =
  '# [react-native-mapbox-navigation] embed-transitive-spm-frameworks'

# Append `install_framework` calls for Mapbox transitive SPM frameworks
# to every Copy Frameworks script under the Pods sandbox. Idempotent —
# repeated invocations do not duplicate the injection.
def react_native_mapbox_navigation_post_install(installer)
  sandbox_root = Pathname(installer.sandbox.root)
  glob = sandbox_root.join(
    'Target Support Files', 'Pods-*', 'Pods-*-frameworks.sh'
  )

  patched = 0
  Dir.glob(glob).each do |script_path|
    content = File.read(script_path)
    next if content.include?(REACT_NATIVE_MAPBOX_NAVIGATION_MARKER)

    # NOTE: do NOT gate these on `${CONFIGURATION}` being "Debug"/"Release".
    # Apps with custom multi-environment build configurations (e.g.
    # "LOCAL.Debug", "PRODUCTION.Release") never match those names, so the
    # frameworks silently fail to embed and the app crashes at launch with
    # "Library not loaded: @rpath/MapboxCommon.framework". The per-framework
    # `[ -d ... ]` existence check already makes these safe for any config.
    snippet = [
      '',
      REACT_NATIVE_MAPBOX_NAVIGATION_MARKER,
      '# Install Mapbox transitive SPM frameworks that CocoaPods does not',
      '# iterate on its own. Missing frameworks are silently skipped so this',
      '# is safe to run on builds that link only a subset.',
      *REACT_NATIVE_MAPBOX_NAVIGATION_TRANSITIVE_FRAMEWORKS.map { |framework|
        "[ -d \"${BUILT_PRODUCTS_DIR}/#{framework}.framework\" ] && install_framework \"${BUILT_PRODUCTS_DIR}/#{framework}.framework\""
      },
      '',
    ].join("\n")

    # Inject at the OUTERMOST parallel-code-sign guard — the one whose
    # body is a bare `wait`. This is outside the `install_framework()`
    # function definition (which contains another `${COCOAPODS_PARALLEL_
    # CODE_SIGN}` check that we must not match). Matching at end-of-file
    # (`\z`) keeps us anchored past both Debug and Release blocks.
    outer_guard_regex = /^if \[ "\$\{COCOAPODS_PARALLEL_CODE_SIGN\}" == "true" \]; then\n  wait\nfi\s*\z/m
    new_content =
      if content =~ outer_guard_regex
        content.sub(outer_guard_regex) { |tail| "#{snippet}\n#{tail}" }
      else
        content + "\n" + snippet + "\n"
      end

    File.write(script_path, new_content)
    patched += 1
  end

  Pod::UI.puts(
    "[ReactNativeMapboxNavigation] Patched #{patched} Copy Frameworks " \
    "script#{patched == 1 ? '' : 's'} to embed transitive SPM frameworks."
  ) if defined?(Pod::UI)
end
