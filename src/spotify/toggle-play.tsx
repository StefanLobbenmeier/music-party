import {
   useSpotifyPlayer} from "react-spotify-web-playback-sdk";

export function TogglePlay() {
  const player = useSpotifyPlayer();

    if (player === null) return <div>No Player Found</div>;

  return <button onClick={() => {
      player.activateElement();
      player.togglePlay();
  }}>Pause / Play</button>;
}