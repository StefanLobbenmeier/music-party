import type { Track } from "@spotify/web-api-ts-sdk";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { getSpotifySdk } from "../lib/spotify-client.ts";
import { PlayerControls } from "./player-controls.tsx";
import { ScanNextQrCodeButton } from "./scan-next-qr-code-button.tsx";
import "./spotify-url-player.css";
import { TogglePlay } from "./toggle-play.tsx";

const backgroundColours = [
    // pink, blue, green, purple, yellow
    "#E91E63",
    "#2196F3",
    "#4CAF50",
    "#9C27B0",
    "#9a9a1b",
];

export function SpotifyUrlPlayer(props: { deviceId: string }) {
  const { songId } = useParams();
  const deviceId = props.deviceId;
  const [track, setTrack] = useState<Track | null>(null);
  const [backgroundColour, setBackgroundColour] = useState<string>("");

  useEffect(() => {
    if (songId && deviceId) {
      getSpotifySdk().player.startResumePlayback(deviceId, undefined, [songId]);
    }
  }, [songId, deviceId]);

  useEffect(() => {
    if (songId) {
      const trackId = songId.split(":")[2];
      getSpotifySdk().tracks.get(trackId).then(setTrack);
    }
  }, [songId]);

  useEffect(() => {
      const randomBackgroundColour = backgroundColours[Math.floor(Math.random() * backgroundColours.length)];
      setBackgroundColour(randomBackgroundColour);
  }, [songId]);

  return (
    <div style={{ backgroundColor: backgroundColour }}>
      {track && (
        <details>
          <summary>Track Information (SPOILERS!)</summary>
          <ul className="track-information-list">
            <li>Name: {track.name}</li>
            <li>Name: {track.artists.map((it) => it.name).join(", ")}</li>
            <li>Album Name: {track.album.name}</li>
            <li>
              Album Release Date:{" "}
              {new Date(track.album.release_date).getUTCFullYear()}
            </li>
          </ul>
        </details>
      )}
      <TogglePlay />
      <ScanNextQrCodeButton />
      <PlayerControls />
    </div>
  );
}

// https://open.spotify.com/track/3RIYlQ20buPfanKbSyGh98?si=1addc55fe7874b94
// spotify:track:5T7FLSnK2cQLfsP1hODPiC
// https://localhost:5173/play/spotify/spotify:track:5T7FLSnK2cQLfsP1hODPiC
