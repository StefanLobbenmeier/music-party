import {
   useSpotifyPlayer} from "react-spotify-web-playback-sdk";

export function TogglePlay() {
  const player = useSpotifyPlayer();

    if (player === null) return <div>No Player Found</div>;

  return <button onClick={async () => {
      await player.activateElement();
      await player.togglePlay();
  }}>Pause / Play</button>;
}