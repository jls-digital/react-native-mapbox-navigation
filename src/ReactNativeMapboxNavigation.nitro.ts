import type {
  HybridView,
  HybridViewMethods,
  HybridViewProps,
} from 'react-native-nitro-modules';

export interface ReactNativeMapboxNavigationProps extends HybridViewProps {
  color: string;
}
export interface ReactNativeMapboxNavigationMethods extends HybridViewMethods {}

export type ReactNativeMapboxNavigation = HybridView<
  ReactNativeMapboxNavigationProps,
  ReactNativeMapboxNavigationMethods
>;
