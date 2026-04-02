import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import type {
  Coordinates,
  MapboxLanguage,
  Waypoint,
} from '@jls-digital/react-native-mapbox-navigation';
import { HomeScreen } from './screens/HomeScreen';
import { NavigationScreen } from './screens/NavigationScreen';

export type RootStackParamList = {
  Home: undefined;
  Navigation: {
    origin: Coordinates;
    destination: Coordinates;
    waypoints?: Waypoint[];
    simulateRoute: boolean;
    mute: boolean;
    language: MapboxLanguage;
    colorScheme: 'light' | 'dark' | 'auto';
  };
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function App() {
  return (
    <NavigationContainer>
      <Stack.Navigator>
        <Stack.Screen
          name="Home"
          component={HomeScreen}
          options={{ title: 'Mapbox Navigation Demo' }}
        />
        <Stack.Screen
          name="Navigation"
          component={NavigationScreen}
          options={{ headerShown: false }}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
