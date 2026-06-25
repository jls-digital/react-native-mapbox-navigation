"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
var _coordinates = require("./coordinates.type");
Object.keys(_coordinates).forEach(function (key) {
  if (key === "default" || key === "__esModule") return;
  if (key in exports && exports[key] === _coordinates[key]) return;
  Object.defineProperty(exports, key, {
    enumerable: true,
    get: function () {
      return _coordinates[key];
    }
  });
});
var _events = require("./events.type");
Object.keys(_events).forEach(function (key) {
  if (key === "default" || key === "__esModule") return;
  if (key in exports && exports[key] === _events[key]) return;
  Object.defineProperty(exports, key, {
    enumerable: true,
    get: function () {
      return _events[key];
    }
  });
});
var _props = require("./props.type");
Object.keys(_props).forEach(function (key) {
  if (key === "default" || key === "__esModule") return;
  if (key in exports && exports[key] === _props[key]) return;
  Object.defineProperty(exports, key, {
    enumerable: true,
    get: function () {
      return _props[key];
    }
  });
});
//# sourceMappingURL=index.js.map