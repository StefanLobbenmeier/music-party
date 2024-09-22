import {
   useSpotifyPlayer} from "react-spotify-web-playback-sdk";

export function TogglePlay() {
  const player = useSpotifyPlayer();

  if (player === null) return null;

  return <button onClick={player.togglePlay}>Pause / Play</button>;
};