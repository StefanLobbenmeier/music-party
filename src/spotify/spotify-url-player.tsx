import { useEffect } from "react";
import { useParams } from "react-router-dom";
import { getSpotifySdk } from "../lib/spotify-client.ts";
import { ScanNextQrCodeButton } from "./scan-next-qr-code-button.tsx";
import { TogglePlay } from "./toggle-play.tsx";

export function SpotifyUrlPlayer(props: { deviceId: string }) {
  const { songId } = useParams();
  const deviceId = props.deviceId;

  useEffect(() => {
    if (songId && deviceId) {
      getSpotifySdk().player.startResumePlayback(deviceId, undefined, [songId]);
    }
  }, [songId, deviceId]);

  useEffect(() => {
    if (songId) {
      const trackId = songId.split(":")[2];
      getSpotifySdk()
        .tracks.get(trackId)
        .then((track) => console.log(track));
    }
  }, [songId]);

  return (
    <>
      <div>Song id is {songId}</div>
      <div>Device id is {deviceId}</div>
      <TogglePlay />
      <ScanNextQrCodeButton />
    </>
  );
}

// https://open.spotify.com/track/3RIYlQ20buPfanKbSyGh98?si=1addc55fe7874b94
// spotify:track:5T7FLSnK2cQLfsP1hODPiC
// https://localhost:5173/play/spotify/spotify:track:5T7FLSnK2cQLfsP1hODPiC
