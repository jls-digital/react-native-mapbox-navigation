export type OnLocationChangeEvent = {
  nativeEvent?: {
    latitude: number;
    longitude: number;
  };
};

export type OnRouteProgressChangeEvent = {
  nativeEvent?: {
    distanceTraveled: number;
    durationRemaining: number;
    fractionTraveled: number;
    distanceRemaining: number;
  };
};

export type OnErrorEvent = {
  nativeEvent?: {
    message?: string;
  };
};

export type OnMuteChangeEvent = {
  nativeEvent?: {
    isMuted: boolean;
  };
};
