"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
var _exportNames = {
  MapboxNavigation: true
};
exports.MapboxNavigation = void 0;
var _reactNative = require("react-native");
var _coordinatesMapper = require("./utils/coordinates-mapper.util");
var _react = _interopRequireDefault(require("react"));
var _types = require("./types");
Object.keys(_types).forEach(function (key) {
  if (key === "default" || key === "__esModule") return;
  if (Object.prototype.hasOwnProperty.call(_exportNames, key)) return;
  if (key in exports && exports[key] === _types[key]) return;
  Object.defineProperty(exports, key, {
    enumerable: true,
    get: function () {
      return _types[key];
    }
  });
});
function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }
const LINKING_ERROR = `The package 'react-native-mapbox-navigation' doesn't seem to be linked. Make sure: \n\n` + _reactNative.Platform.select({
  ios: "- You have run 'pod install'\n",
  default: ''
}) + '- You rebuilt the app after installing the package\n' + '- You are not using Expo Go\n';
const ComponentName = 'MapboxNavigation';
const NativeMapboxNavigation = _reactNative.UIManager.getViewManagerConfig(ComponentName) != null ? (0, _reactNative.requireNativeComponent)(ComponentName) : () => {
  throw new Error(LINKING_ERROR);
};
const MapboxNavigation = props => {
  const nativeProps = {
    ...props,
    destination: (0, _coordinatesMapper.mapToNativeCoordinates)(props.destination),
    origin: props.origin ? (0, _coordinatesMapper.mapToNativeCoordinates)(props.origin) : undefined,
    waypoints: props.waypoints ? props.waypoints.map(_coordinatesMapper.mapToNativeCoordinates) : []
  };
  return /*#__PURE__*/_react.default.createElement(NativeMapboxNavigation, nativeProps);
};
exports.MapboxNavigation = MapboxNavigation;
//# sourceMappingURL=index.js.map