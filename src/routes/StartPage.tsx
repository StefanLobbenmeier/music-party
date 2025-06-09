import "./StartPage.css";
import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  usePlayerDevice,
  useSpotifyPlayer,
} from "react-spotify-web-playback-sdk";
import useSWR from "swr";
import { getSpotifySdk, startLoginToSpotify } from "../lib/spotify-client.ts";
import type { SpotifyState } from "../spotify/spotify-state.ts";
import type { DeviceId } from "../spotify/spotify-state.ts";

function StartPage(props: { spotifyState: SpotifyState }) {
  const spotifyToken = props.spotifyState.spotifyToken;
  return (
    <>
      <div className="card">
        <LoginOrUserInfo spotifyToken={spotifyToken} />
        {spotifyToken != null ? (
          <>
            <DeviceSelection
              spotifyToken={spotifyToken}
              deviceId={props.spotifyState.deviceId}
              setDeviceId={props.spotifyState.setDeviceId}
            />
            <StartGameButton deviceId={props.spotifyState.deviceId} />
          </>
        ) : null}
      </div>
    </>
  );
}

function LoginOrUserInfo(props: {
  spotifyToken: string | null;
}) {
  const spotifyToken = props.spotifyToken;
  return (
    <>
      <h2>Step 1: Login to Spotify</h2>
      {spotifyToken !== null ? (
        <div>Login Successful</div>
      ) : (
        <button type="button" onClick={startLoginToSpotify}>
          Login to Spotify
        </button>
      )}
    </>
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

  useEffect(() => {
    const firstId = data?.devices[0]?.id;
    if (props.deviceId == null && firstId != null) {
      props.setDeviceId(firstId);
    }
  }, [data, props]);

  if (error) return <div>Failed to load devices: {error.toString()}</div>;
  if (isLoading || !data) return <div>Loading devices...</div>;

  const deviceOptions = data.devices.map((device) => (
    <option key={device.id} value={device.id ?? "no-device-id"}>
      {device.name}
      {browserDevice?.device_id === device.id ? " (This browser tab)" : ""}
    </option>
  ));

  const browserDeviceNotReadyWarning =
    browserDevice?.status !== "ready" ? (
      <div>
        Spotify Web Player did not initialize, try refreshing the page if you
        want to listen in the browser.
      </div>
    ) : (
      <></>
    );

  return (
    <>
      <h2>Step 2: Select target device</h2>
      <select
        value={props.deviceId ?? undefined}
        onChange={(e) => props.setDeviceId(e.target.value)}
      >
        {deviceOptions}
      </select>
      {browserDeviceNotReadyWarning}
    </>
  );
}

function StartGameButton(props: {
  deviceId: DeviceId | null;
}) {
  const navigate = useNavigate();
  const player = useSpotifyPlayer();
  const device = usePlayerDevice();

  const startGame = () => {
    if (device?.device_id === props.deviceId) {
      player?.activateElement();
    }
    navigate("/qr-scanner");
  };
  return (
    <>
      <h2>Step 3: Start the game</h2>
      <button type="button" onClick={startGame}>
        Scan QR Code
      </button>
    </>
  );
}

export default StartPage;
