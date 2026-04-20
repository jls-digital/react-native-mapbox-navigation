require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "ReactNativeMapboxNavigation"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => "https://github.com/jls-digital/react-native-mapbox-navigation.git", :tag => "#{s.version}" }

  s.source_files = [
    "ios/**/*.{swift}",
    "ios/**/*.{m,mm}",
    "cpp/**/*.{hpp,cpp}",
  ]

  s.dependency 'React-jsi'
  s.dependency 'React-callinvoker'

  load 'nitrogen/generated/ios/ReactNativeMapboxNavigation+autolinking.rb'
  add_nitrogen_files(s)

  install_modules_dependencies(s)

  # Mapbox Navigation SDK v3 — SPM-only (CocoaPods support dropped in v3).
  # Pinned to 3.20.x to avoid surprises from minor bumps.
  # Requires the consuming app's Podfile to use `use_frameworks! :linkage => :dynamic`.
  # See docs/technical/MAPBOX_SDK_RESEARCH.md §0 for details.
  spm_dependency(s,
    url: 'https://github.com/mapbox/mapbox-navigation-ios',
    requirement: { kind: 'upToNextMinorVersion', minimumVersion: '3.20.0' },
    products: ['MapboxNavigationCore', 'MapboxNavigationUIKit']
  )
end
