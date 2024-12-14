import "./StartPage.css";
import { usePlayerDevice } from "react-spotify-web-playback-sdk";
import useSWR from "swr";
import { getSpotifySdk, startLoginToSpotify } from "../lib/spotify-client.ts";
import { SpotifyLogoutButton } from "../spotify/spotify-logout-button.tsx";
import type { SpotifyState } from "../spotify/spotify-state.ts";
import type { DeviceId } from "../spotify/spotify-state.ts";

function StartPage(props: { spotifyState: SpotifyState }) {
  const spotifyToken = props.spotifyState.spotifyToken;
  return (
    <>
      <div className="card">
        <LoginOrUserInfo spotifyToken={spotifyToken} />
        <SpotifyLogoutButton />
        {spotifyToken != null ? (
          <DeviceSelection
            spotifyToken={spotifyToken}
            deviceId={props.spotifyState.deviceId}
            setDeviceId={props.spotifyState.setDeviceId}
          />
        ) : (
          <></>
        )}
      </div>
    </>
  );
}

function LoginOrUserInfo(props: {
  spotifyToken: string | null;
}) {
  if (props.spotifyToken != null) {
    return <div>"You are now logged in."</div>;
  }
  return (
    <button type="button" onClick={startLoginToSpotify}>
      Login to Spotify
    </button>
  );
}

function DeviceSelection(props: {
  spotifyToken: string;
  deviceId: DeviceId | null;
  setDeviceId: (device: DeviceId) => void;
}) {
  const browserDevice = usePlayerDevice();

  const { data, error, isLoading } = useSWR(
    props.spotifyToken + browserDevice?.status,
    () => {
      return getSpotifySdk().player.getAvailableDevices();
    },
  );

  if (error) return <div>Failed to load devices: {error.toString()}</div>;
  if (isLoading || !data) return <div>Loading devices...</div>;

  const deviceOptions = data.devices.map((device) => (
    <option key={device.id} value={device.id ?? "no-device-id"}>
      {device.name}
      {browserDevice?.device_id === device.id ? " (This browser tab)" : ""}
    </option>
  ));

  return (
    <>
      <div>
        Browser device is {browserDevice?.status} and id is{" "}
        {browserDevice?.device_id}
      </div>
      <select
        value={props.deviceId ?? undefined}
        onChange={(e) => props.setDeviceId(e.target.value)}
      >
        {deviceOptions}
      </select>
    </>
  );
}

export default StartPage;
