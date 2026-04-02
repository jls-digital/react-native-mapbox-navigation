import { useRef, useState } from 'react';
import {
  Alert,
  FlatList,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import {
  MapboxNavigation,
  type MapboxNavigationRef,
} from '@jls-digital/react-native-mapbox-navigation';
import type { RootStackParamList } from '../App';

type Props = NativeStackScreenProps<RootStackParamList, 'Navigation'>;

interface LogEntry {
  id: string;
  timestamp: string;
  message: string;
}

let logIdCounter = 0;

export function NavigationScreen({ navigation, route }: Props) {
  const {
    origin,
    destination,
    waypoints,
    simulateRoute,
    mute,
    language,
    colorScheme,
  } = route.params;
  const navRef = useRef<MapboxNavigationRef>(null);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [showDebug, setShowDebug] = useState(false);

  const addLog = (message: string) => {
    const entry: LogEntry = {
      id: String(++logIdCounter),
      timestamp: new Date().toLocaleTimeString(),
      message,
    };
    setLogs((prev) => [entry, ...prev].slice(0, 100));
  };

  return (
    <View style={styles.container}>
      <MapboxNavigation
        testID="navigation.mapboxNavigation"
        ref={navRef}
        style={styles.navigation}
        origin={origin}
        destination={destination}
        waypoints={waypoints}
        shouldSimulateRoute={simulateRoute}
        mute={mute}
        language={language}
        colorScheme={colorScheme}
        onArrive={(e) => {
          addLog(
            `Arrived at ${e.nativeEvent.destination.latitude.toFixed(4)}, ${e.nativeEvent.destination.longitude.toFixed(4)}`
          );
          Alert.alert('Arrived', 'You have reached your destination.', [
            { text: 'OK', onPress: () => navigation.goBack() },
          ]);
        }}
        onError={(e) => {
          addLog(`Error [${e.nativeEvent.code}]: ${e.nativeEvent.message}`);
          Alert.alert(
            'Navigation Error',
            `${e.nativeEvent.code}\n${e.nativeEvent.message}`
          );
        }}
        onCancelNavigation={() => {
          addLog('Navigation cancelled');
          navigation.goBack();
        }}
        onMuteChange={(e) => {
          addLog(`Mute: ${e.nativeEvent.isMuted}`);
        }}
        onRouteProgressChange={(e) => {
          const { distanceRemaining, durationRemaining, fractionTraveled } =
            e.nativeEvent;
          addLog(
            `Progress: ${(fractionTraveled * 100).toFixed(1)}% | ${(distanceRemaining / 1000).toFixed(1)}km | ${Math.ceil(durationRemaining / 60)}min`
          );
        }}
        onLocationChange={(e) => {
          addLog(
            `Location: ${e.nativeEvent.latitude.toFixed(4)}, ${e.nativeEvent.longitude.toFixed(4)}`
          );
        }}
        onReroute={() => {
          addLog('Rerouting...');
        }}
      />

      <View style={styles.controls}>
        <TouchableOpacity
          testID="navigation.button.recenter"
          accessibilityRole="button"
          style={styles.controlButton}
          onPress={() => navRef.current?.recenterCamera()}
        >
          <Text style={styles.controlButtonText}>Recenter</Text>
        </TouchableOpacity>

        <TouchableOpacity
          testID="navigation.button.overview"
          accessibilityRole="button"
          style={styles.controlButton}
          onPress={() => navRef.current?.showRouteOverview()}
        >
          <Text style={styles.controlButtonText}>Overview</Text>
        </TouchableOpacity>

        <TouchableOpacity
          testID="navigation.button.debug"
          accessibilityRole="button"
          style={[
            styles.controlButton,
            showDebug && styles.controlButtonActive,
          ]}
          onPress={() => setShowDebug((prev) => !prev)}
        >
          <Text style={styles.controlButtonText}>Debug</Text>
        </TouchableOpacity>
      </View>

      {showDebug && (
        <View testID="navigation.debugConsole" style={styles.debugConsole}>
          <FlatList
            data={logs}
            keyExtractor={(item) => item.id}
            renderItem={({ item }) => (
              <Text style={styles.logEntry}>
                <Text style={styles.logTimestamp}>{item.timestamp}</Text>{' '}
                {item.message}
              </Text>
            )}
          />
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  navigation: {
    flex: 1,
  },
  controls: {
    position: 'absolute',
    bottom: 40,
    left: 16,
    right: 16,
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 8,
  },
  controlButton: {
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 20,
  },
  controlButtonActive: {
    backgroundColor: '#007AFF',
  },
  controlButtonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
  },
  debugConsole: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: '40%',
    backgroundColor: 'rgba(0, 0, 0, 0.85)',
    padding: 8,
  },
  logEntry: {
    color: '#0f0',
    fontFamily: 'monospace',
    fontSize: 11,
    lineHeight: 16,
  },
  logTimestamp: {
    color: '#888',
  },
});
