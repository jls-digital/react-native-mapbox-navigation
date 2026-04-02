import { useState } from 'react';
import {
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { MapboxLanguage } from '@jls-digital/react-native-mapbox-navigation';
import { ROUTE_PRESETS, type RoutePreset } from '../constants/routes';
import type { RootStackParamList } from '../App';

type Props = NativeStackScreenProps<RootStackParamList, 'Home'>;

const LANGUAGES: { label: string; value: MapboxLanguage }[] = [
  { label: 'English', value: 'en' },
  { label: 'German', value: 'de' },
  { label: 'French', value: 'fr' },
  { label: 'Italian', value: 'it' },
];

const COLOR_SCHEMES = ['auto', 'light', 'dark'] as const;

export function HomeScreen({ navigation }: Props) {
  const [selectedPreset, setSelectedPreset] = useState<RoutePreset>(
    ROUTE_PRESETS[0]!
  );
  const [simulateRoute, setSimulateRoute] = useState(true);
  const [mute, setMute] = useState(false);
  const [language, setLanguage] = useState<MapboxLanguage>('en');
  const [colorScheme, setColorScheme] = useState<'auto' | 'light' | 'dark'>(
    'auto'
  );

  const handleStartNavigation = () => {
    navigation.navigate('Navigation', {
      origin: selectedPreset.origin,
      destination: selectedPreset.destination,
      waypoints: selectedPreset.waypoints,
      simulateRoute,
      mute,
      language,
      colorScheme,
    });
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.sectionTitle}>Route</Text>
      {ROUTE_PRESETS.map((preset, index) => (
        <TouchableOpacity
          key={preset.label}
          testID={`home.preset.${index}`}
          accessibilityRole="button"
          style={[
            styles.presetButton,
            selectedPreset === preset && styles.presetButtonSelected,
          ]}
          onPress={() => setSelectedPreset(preset)}
        >
          <Text
            style={[
              styles.presetText,
              selectedPreset === preset && styles.presetTextSelected,
            ]}
          >
            {preset.label}
          </Text>
        </TouchableOpacity>
      ))}

      <Text style={styles.sectionTitle}>Settings</Text>

      <View style={styles.settingRow}>
        <Text style={styles.settingLabel}>Simulate Route</Text>
        <Switch
          testID="home.toggle.simulation"
          accessibilityRole="switch"
          value={simulateRoute}
          onValueChange={setSimulateRoute}
        />
      </View>

      <View style={styles.settingRow}>
        <Text style={styles.settingLabel}>Mute</Text>
        <Switch
          testID="home.toggle.mute"
          accessibilityRole="switch"
          value={mute}
          onValueChange={setMute}
        />
      </View>

      <Text style={styles.settingSubtitle}>Language</Text>
      <View style={styles.chipRow}>
        {LANGUAGES.map((lang) => (
          <TouchableOpacity
            key={lang.value}
            testID={`home.language.${lang.value}`}
            accessibilityRole="button"
            style={[
              styles.chip,
              language === lang.value && styles.chipSelected,
            ]}
            onPress={() => setLanguage(lang.value)}
          >
            <Text
              style={[
                styles.chipText,
                language === lang.value && styles.chipTextSelected,
              ]}
            >
              {lang.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      <Text style={styles.settingSubtitle}>Color Scheme</Text>
      <View style={styles.chipRow}>
        {COLOR_SCHEMES.map((scheme) => (
          <TouchableOpacity
            key={scheme}
            testID={`home.colorScheme.${scheme}`}
            accessibilityRole="button"
            style={[styles.chip, colorScheme === scheme && styles.chipSelected]}
            onPress={() => setColorScheme(scheme)}
          >
            <Text
              style={[
                styles.chipText,
                colorScheme === scheme && styles.chipTextSelected,
              ]}
            >
              {scheme}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      <TouchableOpacity
        testID="home.startNavigation"
        accessibilityRole="button"
        style={styles.startButton}
        onPress={handleStartNavigation}
      >
        <Text style={styles.startButtonText}>Start Navigation</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  content: {
    padding: 16,
    paddingBottom: 40,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: '700',
    marginTop: 24,
    marginBottom: 12,
  },
  presetButton: {
    padding: 14,
    borderRadius: 8,
    backgroundColor: '#f0f0f0',
    marginBottom: 8,
  },
  presetButtonSelected: {
    backgroundColor: '#007AFF',
  },
  presetText: {
    fontSize: 16,
    color: '#333',
  },
  presetTextSelected: {
    color: '#fff',
    fontWeight: '600',
  },
  settingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
  },
  settingLabel: {
    fontSize: 16,
  },
  settingSubtitle: {
    fontSize: 16,
    fontWeight: '500',
    marginTop: 16,
    marginBottom: 8,
  },
  chipRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: '#f0f0f0',
  },
  chipSelected: {
    backgroundColor: '#007AFF',
  },
  chipText: {
    fontSize: 14,
    color: '#333',
  },
  chipTextSelected: {
    color: '#fff',
    fontWeight: '600',
  },
  startButton: {
    backgroundColor: '#34C759',
    padding: 16,
    borderRadius: 12,
    alignItems: 'center',
    marginTop: 32,
  },
  startButtonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '700',
  },
});
