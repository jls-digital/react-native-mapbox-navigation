import { getHostComponent } from 'react-native-nitro-modules';
const ReactNativeMapboxNavigationConfig = require('../nitrogen/generated/shared/json/ReactNativeMapboxNavigationConfig.json');
import type {
  ReactNativeMapboxNavigationMethods,
  ReactNativeMapboxNavigationProps,
} from './ReactNativeMapboxNavigation.nitro';

export const ReactNativeMapboxNavigationView = getHostComponent<
  ReactNativeMapboxNavigationProps,
  ReactNativeMapboxNavigationMethods
>('ReactNativeMapboxNavigation', () => ReactNativeMapboxNavigationConfig);
