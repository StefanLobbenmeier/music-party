import { useEffect } from "react";
import { useParams } from "react-router-dom";
import { getSpotifySdk } from "../lib/spotify-client.ts";
import { TogglePlay } from "./toggle-play.tsx";

export function SpotifyUrlPlayer(props: { deviceId: string }) {
  const { songId } = useParams();
  const deviceId = props.deviceId;

  useEffect(() => {
    if (songId && deviceId) {
      getSpotifySdk().player.startResumePlayback(deviceId, undefined, [songId]);
    }
  }, [songId, deviceId]);

  return (
    <>
      <div>Song id is {songId}</div>
      <div>Device id is {deviceId}</div>
      <TogglePlay />
    </>
  );
}

// https://open.spotify.com/track/3RIYlQ20buPfanKbSyGh98?si=1addc55fe7874b94
// spotify:track:5T7FLSnK2cQLfsP1hODPiC
// https://localhost:5173/play/spotify/spotify:track:5T7FLSnK2cQLfsP1hODPiC
