export type DeviceId = string | "THIS_BROWSER";

export interface SpotifyState {
  spotifyToken: string | null;
  setSpotifyToken: (token: string) => void;

  deviceId: DeviceId | null;
  setDeviceId: (device: DeviceId) => void;
}
