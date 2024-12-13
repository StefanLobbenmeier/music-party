import "./StartPage.css";
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
  const { data, error, isLoading } = useSWR(props.spotifyToken, () => {
    return getSpotifySdk().player.getAvailableDevices();
  });

  if (error) return <div>Failed to load devices: {error.toString()}</div>;
  if (isLoading || !data) return <div>Loading devices...</div>;

  const deviceOptions = data.devices.map((device) => (
    <option key={device.id} value={device.id ?? "no-device-id"}>
      {device.name}
    </option>
  ));

  return (
    <select
      value={props.deviceId ?? undefined}
      onChange={(e) => props.setDeviceId(e.target.value)}
    >
      {deviceOptions}
    </select>
  );
}

export default StartPage;
